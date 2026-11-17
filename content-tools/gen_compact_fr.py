#!/usr/bin/env python3
"""Generate and judge compact French learner entries from the existing packs.

    python3 content-tools/gen_compact_fr.py [--limit 200] [--shard 0/1]

The frequency rank comes from fr/*.json, definitions/examples from
fr/monolingual/v1, and morphology/synonyms/examples from fr/v2. State is
checkpointed in content-tools/out/compact_fr_pilot.sqlite; reruns resume it.
"""
import argparse
import json
import random
import re
import sqlite3
import subprocess
import tempfile
import time
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DICT = ROOT.parent / "zabankhan-dict"
OUT = ROOT / "content-tools/out"
JSONL = OUT / "compact_fr_pilot.jsonl"
DB_PATH = OUT / "compact_fr_pilot.sqlite"
REPORT = OUT / "compact_fr_pilot_report.txt"
PROMPT_VERSION = "compact-fr-v1"
PERSIAN = re.compile(r"[\u0600-\u06ff\u0750-\u077f\u08a0-\u08ff]")
ENGLISH_WORDS = set("the and with without from into onto to of in at by is are was were be been being this that these those which who whom whose it its they their them can could should would will may might used use called someone something one another such any".split())

ENTRY_SCHEMA = {
    "type": "object", "additionalProperties": False,
    "required": ["senses", "syn", "ant"],
    "properties": {
        "senses": {"type": "array", "maxItems": 3, "items": {"type": "object", "additionalProperties": False,
            "required": ["def", "ex"], "properties": {"def": {"type": "string"}, "ex": {"type": "string"}}}},
        "syn": {"type": "array", "maxItems": 3, "items": {"type": "string"}},
        "ant": {"type": "array", "maxItems": 3, "items": {"type": "string"}},
    },
}
JUDGE_SCHEMA = {"type": "object", "additionalProperties": False, "required": ["items"], "properties": {
    "items": {"type": "array", "items": {"type": "object", "additionalProperties": False,
        "required": ["id", "verdict"], "properties": {"id": {"type": "string"},
        "verdict": {"type": "string", "enum": ["ok", "wrong"]}}}}}}

PROMPT = """Write one compact French learner dictionary entry using only the supplied French definitions and source examples. Never add a sense or synonym. Keep definitions simple and at most 15 whitespace-separated words. Select at most 3 definitions. For each selected definition, use an example only if it comes from the supplied examples; keep it at most 10 words. If no suitable example exists, use an empty string. Synonyms must be copied from the supplied synonyms; antonyms must be copied from supplied antonyms. Do not translate or add Persian or English. The metadata is context only; do not repeat it. Return this one JSON object only, with exactly these keys: senses (objects with def and ex), syn, ant.\n\n"""
JUDGE_PROMPT = """Judge whether each compact French entry is faithful to its supplied French source definitions. A definition may simplify or summarize a source definition but must not add a meaning. Examples must be copied from the supplied source example text (possibly a contiguous excerpt). Metadata and synonyms are checked separately. Return JSON only: {\"items\":[{\"id\":\"...\",\"verdict\":\"ok|wrong\"}]} for every id.\n\n"""


def dumps(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def normalize(word):
    word = unicodedata.normalize("NFD", " ".join(word.replace("’", "'").split()).lower())
    return "".join(c for c in word if unicodedata.category(c) != "Mn")


def shard_key(word, width=2):
    token = normalize(word).split()[0] if normalize(word) else ""
    return "".join(c if "a" <= c <= "z" else "_" for c in token[:width]).ljust(width, "_")


def lookup_shard(word, index):
    width = 2
    key = shard_key(word, width)
    while key in index.get("split", {}):
        width += 1
        key = shard_key(word, width)
    return key


def load_ranked(limit):
    entries = {}
    for path in (DICT / "fr").glob("*.json"):
        if path.name == "index.json":
            continue
        for item in json.loads(path.read_text(encoding="utf-8")):
            if not isinstance(item, dict) or not isinstance(item.get("rank"), int):
                continue
            key = (item.get("word", ""), item.get("pos", ""))
            if key[0] and (key not in entries or item["rank"] < entries[key]["rank"]):
                entries[key] = item
    return sorted(entries.values(), key=lambda x: (x["rank"], x["word"], x["pos"]))[:limit]


def find_mono(word, pos, key):
    path = DICT / "fr/monolingual/v1" / f"{key}.json"
    if not path.exists():
        return None
    data = json.loads(path.read_text(encoding="utf-8"))
    return next((x for x in data.get("entries", []) if x.get("word") == word and x.get("pos") == pos), None)


def find_v2(word, pos, key, index):
    shard = lookup_shard(word, index)
    path = DICT / "fr/v2" / f"{shard}.json"
    if not path.exists():
        return None
    data = json.loads(path.read_text(encoding="utf-8"))
    for row in data.get("entries", []):
        if len(row) >= 11 and row[1] == word and row[2] == pos:
            return row
    return None


def source_for(entry, index):
    word, pos = entry["word"], entry["pos"]
    mono_key = shard_key(word)
    mono = find_mono(word, pos, mono_key) or {}
    v2 = find_v2(word, pos, mono_key, index)
    ipa = v2[4] if v2 else entry.get("ipa", "")
    gender = v2[5] if v2 else mono.get("gender", "")
    forms = v2[8] if v2 and isinstance(v2[8], dict) else {}
    definitions = []
    for sense in mono.get("senses", []):
        definition = sense.get("definition", "").strip()
        if definition:
            definitions.append({"definition": definition, "example": sense.get("example", "").strip()})
    synonyms = []
    examples = []
    if v2:
        for sense in v2[7]:
            if isinstance(sense, list) and len(sense) >= 5:
                synonyms.extend(x for x in sense[3] if isinstance(x, str))
                examples.extend(x[0] for x in sense[4] if isinstance(x, list) and x and isinstance(x[0], str))
    source = {"word": word, "ipa": ipa or "", "pos": pos or "", "gender": gender or "",
              "forms": forms, "definitions": definitions, "examples": list(dict.fromkeys(x for x in examples if x)),
              "synonyms": list(dict.fromkeys(synonyms)), "antonyms": []}
    return source


def ensure_db(db):
    db.execute("PRAGMA busy_timeout=60000")
    if db.execute("PRAGMA journal_mode").fetchone()[0] != "wal":
        db.execute("PRAGMA journal_mode=WAL")
    db.execute("""CREATE TABLE IF NOT EXISTS compact_entry(
        id TEXT PRIMARY KEY, rank INTEGER NOT NULL, word TEXT NOT NULL, pos TEXT NOT NULL,
        source TEXT NOT NULL, result TEXT, status TEXT NOT NULL, judge TEXT, error TEXT,
        prompt_version TEXT NOT NULL, updated REAL NOT NULL)""")
    db.commit()


def ask_codex(prompt, schema, usage_file):
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as temp:
        schema_path = Path(temp) / "schema.json"
        answer_path = Path(temp) / "answer.json"
        schema_path.write_text(json.dumps(schema), encoding="utf-8")
        res = subprocess.run(["codex", "exec", "-m", "gpt-6-luna", "-c", "model_reasoning_effort=low",
            "-s", "read-only", "--ephemeral", "--skip-git-repo-check", "-C", temp,
            "--output-schema", str(schema_path), "-o", str(answer_path), "--json", prompt],
            capture_output=True, text=True, timeout=900, stdin=subprocess.DEVNULL)
        usage = []
        for line in res.stdout.splitlines():
            try:
                event = json.loads(line)
                if "usage" in event:
                    usage.append(event["usage"])
            except ValueError:
                continue
        with open(usage_file, "a", encoding="utf-8") as f:
            f.write(dumps({"t": time.time(), "rc": res.returncode, "usage": usage,
                           "err": res.stderr[-400:] if res.returncode else ""}) + "\n")
        if res.returncode:
            raise RuntimeError(res.stderr[-400:])
        return json.loads(answer_path.read_text(encoding="utf-8"))


def has_english(text):
    words = re.findall(r"\w+", text.lower())
    return any(w in ENGLISH_WORDS for w in words)


def valid_result(result, source):
    if not isinstance(result, dict) or set(result) != set(ENTRY_SCHEMA["required"]):
        return False, "schema keys"
    if not isinstance(result["senses"], list) or len(result["senses"]) > 3:
        return False, "sense count"
    source_examples = [x["example"] for x in source["definitions"] if x.get("example")]
    source_examples += [x for x in source.get("examples", []) if x]
    if not source["definitions"] and result["senses"]:
        return False, "no source definitions"
    for sense in result["senses"]:
        if not isinstance(sense, dict) or set(sense) != {"def", "ex"}:
            return False, "sense schema"
        definition, example = sense["def"], sense["ex"]
        if not isinstance(definition, str) or not isinstance(example, str):
            return False, "sense types"
        if len(definition.split()) > 15 or len(example.split()) > 10:
            return False, "text length"
        if PERSIAN.search(definition + " " + example) or has_english(definition + " " + example):
            return False, "non-French text"
        if example and not any(example in text for text in source_examples):
            return False, "example not sourced"
    for key, allowed in (("syn", source["synonyms"]), ("ant", source["antonyms"])):
        values = result.get(key)
        if not isinstance(values, list) or len(values) > 3 or any(not isinstance(x, str) or x not in allowed for x in values):
            return False, f"{key} not sourced"
    return True, ""


def generate(db, entries, index, shard):
    for ordinal, entry in enumerate(entries):
        if ordinal % shard[1] != shard[0]:
            continue
        source = source_for(entry, index)
        identity = f"{entry['word']}\t{entry['pos']}"
        old = db.execute("SELECT status,prompt_version FROM compact_entry WHERE id=?", (identity,)).fetchone()
        if old and old[0] in ("generated", "passed") and old[1] == PROMPT_VERSION:
            continue
        input_data = {"metadata": {k: source[k] for k in ("word", "ipa", "pos", "gender", "forms")},
                      "definitions": source["definitions"], "examples": source["examples"],
                      "synonyms": source["synonyms"], "antonyms": source["antonyms"]}
        payload = PROMPT + dumps(input_data)
        result, error = None, ""
        for attempt in range(2):
            try:
                result = ask_codex(payload, ENTRY_SCHEMA, OUT / f"codex_usage.{shard[0]}.jsonl")
                ok, error = valid_result(result, source)
                if ok:
                    # Metadata is copied from the source, never from the model.
                    result = {**{k: source[k] for k in ("word", "ipa", "pos", "gender", "forms")}, **result}
                    break
            except Exception as exc:
                error = str(exc)
            print(f"{entry['word']} [{attempt + 1}/2]: {error}", flush=True)
        status = "generated" if result is not None and not error else "failed"
        db.execute("INSERT INTO compact_entry VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET source=excluded.source,result=excluded.result,status=excluded.status,judge=NULL,error=excluded.error,updated=excluded.updated",
                   (identity, entry["rank"], entry["word"], entry["pos"], dumps(source), dumps(result) if status == "generated" else None,
                    status, None, error, PROMPT_VERSION, time.time()))
        db.commit()
        print(f"generated {ordinal + 1}/{len(entries)} {entry['word']} ({status})", flush=True)


def judge(db, shard):
    rows = db.execute("SELECT id,source,result FROM compact_entry WHERE status='generated' AND prompt_version=? ORDER BY rank", (PROMPT_VERSION,)).fetchall()
    pending = [r for i, r in enumerate(rows) if i % shard[1] == shard[0]]
    for start in range(0, len(pending), 10):
        chunk = pending[start:start + 10]
        items = [{"id": row[0], "source_definitions": json.loads(row[1])["definitions"],
                  "entry": json.loads(row[2])["senses"]} for row in chunk]
        verdicts = {}
        for attempt in range(2):
            try:
                answer = ask_codex(JUDGE_PROMPT + "\n".join(dumps(i) for i in items), JUDGE_SCHEMA,
                                   OUT / f"codex_usage.judge{shard[0]}.jsonl")
                verdicts = {x["id"]: x["verdict"] for x in answer.get("items", []) if isinstance(x, dict)}
                if all(i["id"] in verdicts for i in items):
                    break
            except Exception as exc:
                print(f"judge batch {start} [{attempt + 1}/2]: {exc}", flush=True)
        for row in chunk:
            verdict = verdicts.get(row[0], "wrong")
            db.execute("UPDATE compact_entry SET status=?,judge=?,updated=? WHERE id=?",
                       ("passed" if verdict == "ok" else "failed", verdict, time.time(), row[0]))
        db.commit()
        print(f"judged {min(start + len(chunk), len(pending))}/{len(pending)}", flush=True)


def write_report(db, limit):
    rows = db.execute("SELECT id,word,pos,result,status,rank FROM compact_entry ORDER BY rank").fetchall()
    successful = [json.loads(r[3]) for r in rows if r[4] == "passed" and r[3]]
    JSONL.write_text("".join(dumps(x) + "\n" for x in successful), encoding="utf-8")
    failed = [r for r in rows if r[4] == "failed"]
    rng = random.Random(42)
    samples = successful[:]
    rng.shuffle(samples)
    sample_text = []
    for item in samples[:20]:
        sample_text.append(f"{item['word']} [{item['pos']}] {item['ipa']} {item['gender']}\n  " +
                           "\n  ".join(s['def'] + (" — " + s['ex'] if s['ex'] else "") for s in item['senses']) +
                           ("\n  syn: " + ", ".join(item['syn']) if item['syn'] else ""))
    lines = [f"Compact French pilot (requested {limit})", f"count: {len(rows)}", f"successful: {len(successful)}",
             f"failed count: {len(failed)}", "", "20 random samples:", *sample_text]
    if failed:
        lines.extend(["", "Failed entries:", *[f"{r[1]} [{r[2]}] — {r[4]}" for r in failed[:30]]])
    report = "\n".join(lines) + "\n"
    REPORT.write_text(report, encoding="utf-8")
    print(report, end="")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=200)
    parser.add_argument("--shard", default="0/1", help="i/n to split independent headword ranges")
    args = parser.parse_args()
    shard = tuple(map(int, args.shard.split("/")))
    if len(shard) != 2 or shard[1] < 1 or not 0 <= shard[0] < shard[1]:
        parser.error("--shard must be i/n where 0 <= i < n")
    OUT.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(DB_PATH, timeout=120)
    ensure_db(db)
    entries = load_ranked(args.limit)
    index = json.loads((DICT / "fr/v2/index.json").read_text(encoding="utf-8"))
    generate(db, entries, index, shard)
    judge(db, shard)
    write_report(db, args.limit)


if __name__ == "__main__":
    main()
