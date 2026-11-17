#!/usr/bin/env python3
"""Build compact French->French learner entries (fr/learner/v1) with Codex.

    python3 content-tools/gen_fr_learner.py --limit 200 --shard 0/1   # generate + judge
    python3 content-tools/gen_fr_learner.py --report                   # summary + 20 samples
    python3 content-tools/gen_fr_learner.py --export                   # write shards to ../zabankhan-dict/fr/learner/v1

Sources: frequency rank and forms from ../zabankhan-dict/fr (v2), every sense,
example, synonym, antonym and derived word from the French Wiktionnaire extract
(../zabankhan-dict-src/frwiktionary-fr.jsonl.gz, CC BY-SA). The model shortens
definitions, picks examples verbatim from the source, and proposes synonyms,
antonyms and phrases; a second Codex pass judges every entry. State lives in
content-tools/out/fr_learner.sqlite; reruns resume and retry failed rows.
"""
import argparse
import gzip
import hashlib
import json
import random
import re
import sqlite3
import subprocess
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_compact_fr import DICT, OUT, PERSIAN, dumps, has_english, shard_key  # noqa: E402
from merge_french_definitions import POS  # noqa: E402

WIKT = DICT.parent / "zabankhan-dict-src/frwiktionary-fr.jsonl.gz"
DB_PATH = OUT / "fr_learner.sqlite"
EXPORT = DICT / "fr/learner/v1"
VERSION = "fr-learner-v1"
BATCH = 10
JUDGE_BATCH = 24
MAX_ATTEMPTS = 3


def load_ranked(limit):
    """Word list from the v2 pack the app ships; entries are [rank, word, pos, ...]."""
    entries = {}
    for path in (DICT / "fr/v2").glob("*.json"):
        if path.name == "index.json":
            continue
        for e in json.loads(path.read_text(encoding="utf-8"))["entries"]:
            if isinstance(e, list) and isinstance(e[0], int) and e[1]:
                key = (e[1], e[2])
                if key not in entries or e[0] < entries[key]["rank"]:
                    entries[key] = {"rank": e[0], "word": e[1], "pos": e[2]}
    return sorted(entries.values(), key=lambda x: (x["rank"], x["word"], x["pos"]))[:limit]

STR = {"type": "string"}
LIST = {"type": "array", "maxItems": 4, "items": STR}
def obj(**props):
    return {"type": "object", "additionalProperties": False, "required": list(props), "properties": props}

ENTRY = obj(
    id=STR,
    senses={"type": "array", "maxItems": 3, "items": obj(src={"type": "integer"}, d=STR, ex=STR, reg=STR, syn=LIST, ant=LIST)},
    more={"type": "array", "maxItems": 5, "items": obj(src={"type": "integer"}, d=STR)},
    phrases={"type": "array", "maxItems": 5, "items": obj(p=STR, d=STR)},
    pattern=STR, note=STR, family=LIST)
GEN_SCHEMA = obj(items={"type": "array", "items": ENTRY})
JUDGE_SCHEMA = obj(items={"type": "array", "items": obj(
    id=STR, verdict={"type": "string", "enum": ["ok", "wrong"]}, reason=STR)})

GEN_PROMPT = """You write entries for a compact French-French learner dictionary (learners A2-B2). Each input item has an id, the headword, its part of speech, and ALL its source senses (numbered "i", with French definition, register tags and source examples), plus source synonyms, antonyms and derived words.

If an item has "previous_problem", an earlier answer was rejected for that reason: avoid it.\nFor every input item return one object with the same id:
- senses: the 1-3 most common, most useful senses, most common first. "src" is the source sense number. "d" is a plain French definition, at most 15 words, simpler than the source but with the same meaning; never add a meaning. "ex" is one example copied character-for-character from that sense's source examples (you may cut it to a contiguous excerpt of at most 12 words); use "" if none fits. "reg" is a short register label only if the source tags say so (fam., soutenu, vieilli, littéraire, péjoratif, vulgaire), else "". "syn" and "ant": up to 3 common single words or short expressions that are synonyms/antonyms IN THIS SENSE; prefer the supplied ones; you may add well-known ones; use [] if none is truly correct.
- more: up to 5 other source senses that a learner may meet, each with src and a definition of at most 10 words. Skip rare, technical, regional and dated senses.
- phrases: up to 5 frequent fixed expressions or collocations containing the headword, each with "p" (the phrase) and "d" (French meaning, at most 10 words). Prefer the supplied derived words that are multi-word phrases. Only real, common phrases.
- pattern: the key grammar pattern if useful (e.g. "penser à qqch", "fort en + matière"), else "".
- note: one short French learner note (common mistake, false friend for English speakers, homophone, irregular plural) only if truly useful, else "".
- family: up to 4 common words of the same family, single words, taken from the supplied derived/related words.
Write only French. No English, no Persian. Return JSON only.

"""

JUDGE_PROMPT = """You check entries of a compact French-French learner dictionary for A2-B2 learners. For each item compare "entry" with "source". Mark "wrong" if ANY of these is true: a definition adds or changes a meaning versus its source sense "src"; an example is not taken from the source examples of that sense (a contiguous excerpt of a source example is fine); a synonym or antonym is not correct for that sense; a phrase is not a real common French phrase or its definition is wrong; the pattern or note is false; any text is not French. Judge only real errors a learner would be misled by. Short definitions deliberately omit nonessential botanical, technical, historical and encyclopedic details; do not reject these omissions unless they change the meaning or confuse it with another sense. Selecting only some source senses is intended: do not reject an entry for omitting other senses. Minor style choices are "ok". Otherwise "ok". Give a short reason identifying the actual misleading claim when wrong, else "". Return JSON only with every id.

"""


def build_index(db):
    """Load usable Wiktionnaire entries for the ranked (word, pos) pairs into SQLite once."""
    if db.execute("SELECT count(*) FROM wikt").fetchone()[0]:
        return
    print("indexing Wiktionnaire extract...", flush=True)
    rows = {}
    with gzip.open(WIKT, "rt", encoding="utf-8") as f:
        for line in f:
            d = json.loads(line)
            pos = POS.get(d.get("pos", ""))
            if not pos or d.get("lang_code") != "fr":
                continue
            senses = []
            for s in d.get("senses", []):
                if s.get("form_of") or s.get("alt_of") or not s.get("glosses"):
                    continue
                examples = [e["text"] for e in s.get("examples", []) if e.get("text") and len(e["text"].split()) <= 30][:4]
                senses.append({"def": s["glosses"][-1], "tags": s.get("tags", []) + s.get("raw_tags", []), "ex": examples})
            if not senses:
                continue
            words = lambda k: [x["word"] for x in d.get(k, []) if x.get("word")]
            key = (d["word"], pos)
            old = rows.get(key)
            item = {"senses": senses, "syn": words("synonyms"), "ant": words("antonyms"),
                    "derived": words("derived") + words("related")}
            if old:  # several etymologies: merge
                for k in item:
                    old[k] += item[k]
            else:
                rows[key] = item
    db.executemany("INSERT INTO wikt VALUES(?,?,?)", ((w, p, dumps(v)) for (w, p), v in rows.items()))
    db.commit()


def source_for(db, word, pos):
    row = db.execute("SELECT data FROM wikt WHERE word=? AND pos=?", (word, pos)).fetchone()
    if not row:
        return None
    d = json.loads(row[0])
    senses = [{"i": i, **s} for i, s in enumerate(d["senses"][:15])]
    return {"word": word, "pos": pos, "senses": senses, "syn": list(dict.fromkeys(d["syn"]))[:20],
            "ant": list(dict.fromkeys(d["ant"]))[:20], "derived": list(dict.fromkeys(d["derived"]))[:40]}


def ask_codex(prompt, schema, log):
    with tempfile.TemporaryDirectory() as temp:
        schema_path, answer = Path(temp) / "schema.json", Path(temp) / "answer.json"
        schema_path.write_text(json.dumps(schema), encoding="utf-8")
        res = subprocess.run(["codex", "exec", "-m", "gpt-6-luna", "-c", "model_reasoning_effort=low",
                              "-s", "read-only", "--ephemeral", "--skip-git-repo-check", "-C", temp,
                              "--output-schema", str(schema_path), "-o", str(answer), prompt],
                             capture_output=True, text=True, timeout=900, stdin=subprocess.DEVNULL)
        if res.returncode:
            with open(log, "a", encoding="utf-8") as f:
                f.write(dumps({"t": time.time(), "err": res.stderr[-400:]}) + "\n")
            raise RuntimeError(res.stderr[-200:])
        return json.loads(answer.read_text(encoding="utf-8"))


def french(text):
    return not PERSIAN.search(text) and not has_english(text)


def repair(entry, source):
    """Drop the small unsourced bits the model adds instead of rejecting the whole entry."""
    by_i = {s["i"]: s for s in source["senses"]}
    word = source["word"].lower()
    for s in entry["senses"]:
        if s["src"] in by_i and s["ex"] and not any(s["ex"] in e for e in by_i[s["src"]]["ex"]):
            s["ex"] = ""
        s["syn"] = [w for w in s["syn"] if w.lower() != word]
        s["ant"] = [w for w in s["ant"] if w.lower() != word]
    entry["family"] = [w for w in entry["family"] if " " not in w and w in source["derived"]]
    entry["more"] = [m for m in entry["more"] if m["src"] in by_i and len(m["d"].split()) <= 12]
    return entry


def check(entry, source):
    """Return an error string, or "" when the entry respects the hard rules."""
    by_i = {s["i"]: s for s in source["senses"]}
    if not entry["senses"]:
        return "no senses"
    for s in entry["senses"]:
        if s["src"] not in by_i:
            return f"bad src {s['src']}"
        if len(s["d"].split()) > 15 or not s["d"].strip():
            return "definition length"
        if s["ex"] and not any(s["ex"] in e for e in by_i[s["src"]]["ex"]):
            return "example not copied from its sense"
        if any(w.lower() == source["word"].lower() for w in s["syn"] + s["ant"]):
            return "headword in syn/ant"
    for m in entry["more"]:
        if m["src"] not in by_i or len(m["d"].split()) > 12:
            return "bad more"
    for p in entry["phrases"]:
        if len(p["d"].split()) > 12:
            return "phrase definition length"
    texts = [s["d"] for s in entry["senses"]] + [m["d"] for m in entry["more"]] + [p["d"] for p in entry["phrases"]] + [entry["note"]]
    if not all(french(t) for t in texts):
        return "non-French text"
    if any(" " in w or w not in source["derived"] for w in entry["family"]):
        return "family not sourced"
    return ""


def ensure_db(db):
    db.execute("PRAGMA busy_timeout=60000")
    db.execute("PRAGMA journal_mode=WAL")
    db.execute("CREATE TABLE IF NOT EXISTS wikt(word TEXT, pos TEXT, data TEXT, PRIMARY KEY(word,pos))")
    db.execute("""CREATE TABLE IF NOT EXISTS entry(id TEXT PRIMARY KEY, rank INTEGER, word TEXT, pos TEXT,
        result TEXT, status TEXT, reason TEXT, attempts INTEGER DEFAULT 0, version TEXT, updated REAL)""")
    db.commit()


def save(db, ident, rank, word, pos, result, status, reason):
    db.execute("""INSERT INTO entry VALUES(?,?,?,?,?,?,?,1,?,?) ON CONFLICT(id) DO UPDATE SET
        result=excluded.result,status=excluded.status,reason=excluded.reason,attempts=attempts+1,
        version=excluded.version,updated=excluded.updated""",
               (ident, rank, word, pos, dumps(result) if result else None, status, reason, VERSION, time.time()))


def owns(ident, shard):
    # Stable across processes, filtering, retries and changes to the ranked list.
    return int.from_bytes(hashlib.sha256(ident.encode("utf-8")).digest()[:8], "big") % shard[1] == shard[0]


def generate(db, ranked, shard):
    todo = []
    for e in ranked:
        ident = f"{e['word']}\t{e['pos']}"
        if not owns(ident, shard):
            continue
        old = db.execute("SELECT status,attempts,version,reason FROM entry WHERE id=?", (ident,)).fetchone()
        if old and old[2] == VERSION and (old[0] in ("generated", "passed", "nosource", "skipped") or old[1] >= MAX_ATTEMPTS):
            continue
        source = source_for(db, e["word"], e["pos"])
        if not source:
            save(db, ident, e["rank"], e["word"], e["pos"], None, "nosource", "")
            continue
        if old and old[0] == "failed" and old[3]:
            source = {**source, "previous_problem": old[3]}  # retry fixes what the judge or checks rejected
        todo.append((ident, e, source))
    db.commit()
    log = OUT / f"fr_learner_errors.{shard[0]}.jsonl"
    for start in range(0, len(todo), BATCH):
        chunk = todo[start:start + BATCH]
        items = [{"id": ident, **src} for ident, _, src in chunk]
        try:
            answer = {x["id"]: x for x in ask_codex(GEN_PROMPT + "\n".join(map(dumps, items)), GEN_SCHEMA, log)["items"]}
        except Exception as exc:
            answer, err = {}, str(exc)
        for ident, e, src in chunk:
            result = answer.get(ident)
            if result and not result["senses"]:  # model judged every source sense obsolete or unusable
                save(db, ident, e["rank"], e["word"], e["pos"], None, "skipped", "no usable senses")
                continue
            reason = check(repair(result, src), src) if result else (err if not answer else "missing in answer")
            save(db, ident, e["rank"], e["word"], e["pos"], result if not reason else None,
                 "generated" if not reason else "failed", reason)
        db.commit()
        print(f"shard {shard[0]}: generated {min(start + BATCH, len(todo))}/{len(todo)}", flush=True)


def judge(db, shard):
    rows = db.execute("SELECT id,word,pos,result FROM entry WHERE status='generated' AND version=? ORDER BY rank", (VERSION,)).fetchall()
    rows = [r for r in rows if owns(r[0], shard)]
    log = OUT / f"fr_learner_errors.judge{shard[0]}.jsonl"
    for start in range(0, len(rows), JUDGE_BATCH):
        chunk = rows[start:start + JUDGE_BATCH]
        items = [{"id": r[0], "source": source_for(db, r[1], r[2]), "entry": json.loads(r[3])} for r in chunk]
        try:
            verdicts = {x["id"]: x for x in ask_codex(JUDGE_PROMPT + "\n".join(map(dumps, items)), JUDGE_SCHEMA, log)["items"]}
        except Exception as exc:
            print(f"judge batch failed: {exc}", flush=True)
            continue  # stays 'generated'; the next run judges it again
        for r in chunk:
            v = verdicts.get(r[0])
            if v:
                db.execute("UPDATE entry SET status=?,reason=?,updated=? WHERE id=? AND status='generated' AND result=? AND version=?",
                           ("passed" if v["verdict"] == "ok" else "failed", v["reason"], time.time(), r[0], r[3], VERSION))
        db.commit()
        print(f"shard {shard[0]}: judged {min(start + JUDGE_BATCH, len(rows))}/{len(rows)}", flush=True)


def to_export(word, pos, r):
    """Compact app format; empty fields are dropped to keep shards small."""
    senses = [{k: v for k, v in s.items() if k != "src" and v} for s in r["senses"]]
    out = {"word": word, "pos": pos, "senses": senses, "more": [m["d"] for m in r["more"]],
           "phrases": r["phrases"], "pattern": r["pattern"], "note": r["note"], "family": r["family"]}
    return {k: v for k, v in out.items() if v}


def export(db):
    shards = {}
    for word, pos, result in db.execute("SELECT word,pos,result FROM entry WHERE status='passed' AND result IS NOT NULL ORDER BY rank"):
        shards.setdefault(shard_key(word), []).append(to_export(word, pos, json.loads(result)))
    EXPORT.mkdir(parents=True, exist_ok=True)
    for key, entries in shards.items():
        (EXPORT / f"{key}.json").write_text(dumps({"entries": entries}), encoding="utf-8")
    # version changes on every export so installed apps drop stale cached shards
    (EXPORT / "index.json").write_text(dumps({"lang": "fr", "format": 1, "version": int(time.time()),
                                              "entries": sum(map(len, shards.values())), "license": "CC BY-SA 4.0 (Wiktionnaire)",
                                              "shards": sorted(shards)}), encoding="utf-8")
    print(f"exported {sum(map(len, shards.values()))} entries in {len(shards)} shards to {EXPORT}")


def report(db):
    counts = dict(db.execute("SELECT status,count(*) FROM entry GROUP BY status").fetchall())
    print("status:", counts)
    for reason, n in db.execute("SELECT substr(reason,1,60),count(*) FROM entry WHERE status='failed' GROUP BY 1 ORDER BY 2 DESC LIMIT 12"):
        print(f"  {n:5} {reason}")
    rows = db.execute("SELECT word,pos,result FROM entry WHERE status='passed'").fetchall()
    for word, pos, result in random.Random(42).sample(rows, min(20, len(rows))):
        print("\n" + json.dumps(to_export(word, pos, json.loads(result)), ensure_ascii=False, indent=1))


def selftest():
    src = {"word": "fort", "senses": [{"i": 0, "def": "Costaud.", "tags": [], "ex": ["Il est fort comme un bœuf."]}],
           "derived": ["force", "point fort"]}
    good = {"senses": [{"src": 0, "d": "Qui a beaucoup de force.", "ex": "fort comme un bœuf", "reg": "", "syn": ["robuste"], "ant": ["faible"]}],
            "more": [], "phrases": [], "pattern": "", "note": "", "family": ["force"]}
    assert check(good, src) == ""
    assert check({**good, "family": ["point fort"]}, src) == "family not sourced"
    bad = json.loads(dumps(good)); bad["senses"][0]["ex"] = "Il est très fort."
    assert check(bad, src) == "example not copied from its sense"
    bad["senses"][0].update(ex="", d="Strong and powerful with the body")
    assert check(bad, src) == "non-French text"
    fixable = json.loads(dumps(good))
    fixable["senses"][0].update(ex="Il est très fort.", syn=["robuste", "Fort"])
    fixable["family"] = ["point fort", "force", "inventé"]
    fixed = repair(fixable, src)
    assert check(fixed, src) == "" and fixed["senses"][0]["ex"] == "" and fixed["senses"][0]["syn"] == ["robuste"]
    assert fixed["family"] == ["force"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=200)
    ap.add_argument("--shard", default="0/1")
    ap.add_argument("--report", action="store_true")
    ap.add_argument("--export", action="store_true")
    ap.add_argument("--index-only", action="store_true")
    args = ap.parse_args()
    selftest()
    OUT.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(DB_PATH, timeout=120)
    ensure_db(db)
    if args.report:
        return report(db)
    if args.export:
        return export(db)
    build_index(db)
    if args.index_only:
        return
    shard = tuple(map(int, args.shard.split("/")))
    generate(db, load_ranked(args.limit), shard)
    judge(db, shard)


if __name__ == "__main__":
    main()
