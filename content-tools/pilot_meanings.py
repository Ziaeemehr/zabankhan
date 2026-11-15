#!/usr/bin/env python3
"""Phase-1 pilot: sense-level Persian/English/French meanings from several models, plus back-translation.

    python3 content-tools/pilot_meanings.py sample DICT.sqlite OUT_DIR          # writes OUT_DIR/sample.jsonl
    python3 content-tools/pilot_meanings.py run {luna,gemma,qwen} OUT_DIR       # writes OUT_DIR/<backend>.jsonl
    python3 content-tools/pilot_meanings.py backtrans {luna,gemma,qwen} OUT_DIR # writes OUT_DIR/<backend>.bt.jsonl
    python3 content-tools/pilot_meanings.py table OUT_DIR                       # writes OUT_DIR/eval.tsv (blind, shuffled)

luna runs through `codex exec` (Codex subscription, read-only, ephemeral); token usage goes to OUT_DIR/usage.jsonl.
gemma needs `llm-on` (llama-server :8080); qwen needs ollama (:11434). Resumable: finished ids are skipped.
"""
import json
import random
import re
import sqlite3
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

SEED = 42
BATCH = 25
TRICKY = ["prendre", "tenir", "passer", "faire", "bien", "fin", "avocat", "livre", "son", "pièce",
          "glace", "louer", "toucher", "compte", "point", "temps", "mode", "poste", "voler", "tour"]
PERSIAN = re.compile(r"[؀-ۿ]")

PROMPT = """You are a French lexicographer writing a learner's dictionary for Persian speakers.
Each input line is ONE sense of a French headword: id, word, pos (part of speech), gender, en (the English gloss of this sense), example (may be empty).
For each sense give:
- fa: 1 to 3 natural Persian equivalents of THIS sense only, most common first, separated by "، ". Verbs as Persian infinitives (e.g. گرفتن). No articles, transliteration, Latin letters or notes.
- en_short: a short English equivalent (1-4 words) of this sense.
- fr_simple: a simple French definition of this sense for a B1 learner, max 15 words.
- confidence: "high" or "low". Use "low" if the gloss is ambiguous or you are unsure.
If you cannot answer a field reliably, use null. Never invent a sense that the gloss does not support.
Answer with JSON only: {"items": [{"id": ..., "fa": ..., "en_short": ..., "fr_simple": ..., "confidence": ...}, ...]} covering every id.

"""
BT_PROMPT = """Translate each Persian dictionary line to a short English gloss (1-5 words). Do not guess the source word.
Answer with JSON only: {"items": [{"id": ..., "en": ...}, ...]} covering every id.

"""
NS = {"type": ["string", "null"]}
SCHEMA = {"type": "object", "additionalProperties": False, "required": ["items"], "properties": {"items": {"type": "array", "items": {
    "type": "object", "additionalProperties": False, "required": ["id", "fa", "en_short", "fr_simple", "confidence"],
    "properties": {"id": {"type": "string"}, "fa": NS, "en_short": NS, "fr_simple": NS, "confidence": {"type": "string", "enum": ["high", "low"]}}}}}}
BT_SCHEMA = {"type": "object", "additionalProperties": False, "required": ["items"], "properties": {"items": {"type": "array", "items": {
    "type": "object", "additionalProperties": False, "required": ["id", "en"], "properties": {"id": {"type": "string"}, "en": NS}}}}}


def read_jsonl(p):
    return [json.loads(l) for l in Path(p).read_text().splitlines() if l.strip()] if Path(p).exists() else []


def sample(db_path, out):
    db = sqlite3.connect(db_path)
    rows = db.execute("SELECT id, word, pos, gender, en, example FROM entry WHERE rank < 3000 ORDER BY id").fetchall()
    tricky = [r for r in rows if r[1] in TRICKY]
    rest = [r for r in rows if r[1] not in TRICKY]
    random.Random(SEED).shuffle(rest)
    senses = []
    for r in tricky + rest:
        ex = r[5].split("\t")[0] if r[5] else ""
        for i, en in enumerate(json.loads(r[4])):
            senses.append({"id": f"{r[0]}:{i}", "word": r[1], "pos": r[2], "gender": r[3], "en": en, "example": ex if i == 0 else ""})
        if len(senses) >= 100:
            break
    Path(out, "sample.jsonl").write_text("".join(json.dumps(s, ensure_ascii=False) + "\n" for s in senses[:100]))
    print(f"{len(senses[:100])} senses")


def ask_codex(prompt, schema, out):
    with tempfile.TemporaryDirectory() as tmp:  # empty cwd: no AGENTS.md/CLAUDE.md context
        Path(tmp, "schema.json").write_text(json.dumps(schema))
        res = subprocess.run(["codex", "exec", "-m", "gpt-6-luna", "-c", "model_reasoning_effort=low", "-s", "read-only",
                              "--ephemeral", "--skip-git-repo-check", "-C", tmp, "--output-schema", f"{tmp}/schema.json",
                              "-o", f"{tmp}/answer.json", "--json", prompt], capture_output=True, text=True, timeout=900, stdin=subprocess.DEVNULL)
        usage = [json.loads(l)["usage"] for l in res.stdout.splitlines() if l.startswith("{") and '"usage"' in l]
        with open(Path(out, "usage.jsonl"), "a") as f:
            f.write(json.dumps({"t": time.time(), "rc": res.returncode, "usage": usage, "err": res.stderr[-400:] if res.returncode else ""}) + "\n")
        if res.returncode:
            raise RuntimeError(res.stderr[-400:])
        return json.loads(Path(tmp, "answer.json").read_text())


def ask_local(url, model, prompt):
    req = urllib.request.Request(url, data=json.dumps({"model": model, "temperature": 0.2, "response_format": {"type": "json_object"},
                                                       "messages": [{"role": "user", "content": prompt}]}).encode(),
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=900) as r:
        content = json.load(r)["choices"][0]["message"]["content"]
    return json.loads(content[content.find("{"):content.rfind("}") + 1])


def ask(backend, prompt, schema, out):
    if backend == "luna":
        return ask_codex(prompt, schema, out)
    if backend == "gemma":
        return ask_local("http://127.0.0.1:8080/v1/chat/completions", "gemma4", prompt)
    return ask_local("http://127.0.0.1:11434/v1/chat/completions", "qwen3:4b", "/no_think\n" + prompt)


def process(backend, out, items, prompt, schema, dest, keep):
    done = {r["id"] for r in read_jsonl(dest)}
    todo = [i for i in items if i["id"] not in done]
    for k in range(0, len(todo), BATCH):
        chunk = todo[k:k + BATCH]
        start = time.time()
        for attempt in range(3):
            try:
                answer = ask(backend, prompt + "\n".join(json.dumps(i, ensure_ascii=False) for i in chunk), schema, out)
                break
            except Exception as e:  # malformed JSON, timeout or quota: retry, then leave the batch for the next run
                print(f"{backend} batch {k}: {e}", file=sys.stderr, flush=True)
                answer = {}
        ids = {i["id"] for i in chunk}
        got = [r for r in answer.get("items", []) if isinstance(r, dict) and r.get("id") in ids and keep(r)]
        with open(dest, "a") as f:
            f.writelines(json.dumps(r, ensure_ascii=False) + "\n" for r in got)
        print(f"{backend}: {k + len(chunk)}/{len(todo)} got={len(got)} {time.time() - start:.0f}s", flush=True)


def valid_fa(v):
    return v is None or (isinstance(v, str) and PERSIAN.search(v) and not re.search(r"[A-Za-z]", v) and len(v) < 120)


def run(backend, out):
    process(backend, out, read_jsonl(Path(out, "sample.jsonl")), PROMPT, SCHEMA, Path(out, f"{backend}.jsonl"),
            lambda r: valid_fa(r.get("fa")))


def backtrans(backend, out):
    items = [{"id": r["id"], "fa": r["fa"]} for r in read_jsonl(Path(out, f"{backend}.jsonl")) if r.get("fa")]
    process("luna", out, items, BT_PROMPT, BT_SCHEMA, Path(out, f"{backend}.bt.jsonl"), lambda r: True)


def table(out):
    senses = read_jsonl(Path(out, "sample.jsonl"))
    rows = []
    for b in ["luna", "gemma", "qwen"]:
        res = {r["id"]: r for r in read_jsonl(Path(out, f"{b}.jsonl"))}
        bt = {r["id"]: r.get("en") or "" for r in read_jsonl(Path(out, f"{b}.bt.jsonl"))}
        for s in senses:
            r = res.get(s["id"], {})
            rows.append([b, s["id"], s["word"], s["pos"], s["en"], r.get("fa") or "", r.get("en_short") or "",
                         r.get("fr_simple") or "", r.get("confidence") or "", bt.get(s["id"], "")])
    random.Random(SEED).shuffle(rows)
    key = {f"x{n}": r[0] for n, r in enumerate(rows)}  # blind label -> backend, kept apart from the table
    Path(out, "eval.key.json").write_text(json.dumps(key))
    head = ["blind", "id", "word", "pos", "source_en", "fa", "en_short", "fr_simple", "confidence", "back_translation"]
    Path(out, "eval.tsv").write_text("\t".join(head) + "\n" + "".join(
        "\t".join([f"x{n}"] + [c.replace("\t", " ") for c in r[1:]]) + "\n" for n, r in enumerate(rows)))
    print(f"{len(rows)} rows")


if __name__ == "__main__":
    cmd, *args = sys.argv[1:]
    {"sample": sample, "run": run, "backtrans": backtrans, "table": table}[cmd](*args)
