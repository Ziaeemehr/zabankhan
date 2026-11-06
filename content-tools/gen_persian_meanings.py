#!/usr/bin/env python3
"""Fill Persian meanings in the offline dictionary with a local OpenAI-compatible model.

Start the model first (Gemma 4 on llama-server: `llm-on`), then:
    python3 content-tools/gen_persian_meanings.py DICT.sqlite [--url http://127.0.0.1:8080] [--limit 20000]
Resumable: only rows whose `fa` is empty are sent, most frequent first. Stop with Ctrl-C at any time.
"""
import argparse
import json
import re
import sqlite3
import sys
import time
import urllib.request

BATCH = 20
PERSIAN = re.compile(r"[؀-ۿ]")
PROMPT = """You are a French-Persian lexicographer writing a learner's dictionary.
For each French headword below give its Persian meanings as a short dictionary line:
- 1 to 4 Persian equivalents, most common sense first, separated by "، ".
- Verbs as Persian infinitives (e.g. رفتن). Nouns and adjectives without articles. No transliteration, no French, no notes.
- Follow the part of speech and the English glosses; they are the senses to translate.
Answer with JSON only: {"<id>": "<Persian line>", ...} with every id below.

"""


def ask(url, items):
    body = PROMPT + "\n".join(json.dumps(i, ensure_ascii=False) for i in items)
    req = urllib.request.Request(url.rstrip("/") + "/v1/chat/completions", data=json.dumps({
        "model": "gemma4", "temperature": 0.2, "response_format": {"type": "json_object"},
        "messages": [{"role": "user", "content": body}]}).encode(), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=600) as r:
        content = json.load(r)["choices"][0]["message"]["content"]
    content = content[content.find("{"):content.rfind("}") + 1]
    return json.loads(content)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("db"); p.add_argument("--url", default="http://127.0.0.1:8080"); p.add_argument("--limit", type=int, default=20000)
    a = p.parse_args()
    db = sqlite3.connect(a.db)
    rows = db.execute("SELECT id, word, pos, gender, en FROM entry WHERE fa = '' AND rank < ? ORDER BY rank", (a.limit,)).fetchall()
    print(f"{len(rows)} entries to translate", flush=True)
    done, start = 0, time.time()
    for i in range(0, len(rows), BATCH):
        chunk = rows[i:i + BATCH]
        items = [{"id": str(r[0]), "word": r[1], "pos": r[2], **({"gender": r[3]} if r[3] else {}), "en": json.loads(r[4])[:3]} for r in chunk]
        for attempt in range(3):
            try:
                answer = ask(a.url, items)
                break
            except Exception as e:  # malformed JSON or a timeout: retry, then skip this batch
                print(f"batch {i}: {e}", file=sys.stderr, flush=True)
                answer = {}
        good = [(v.strip(), int(k)) for k, v in answer.items() if k.isdigit() and isinstance(v, str) and PERSIAN.search(v)
                and not re.search(r"[A-Za-z]", v) and len(v) < 120]
        db.executemany("UPDATE entry SET fa = ? WHERE id = ?", good)
        db.commit()
        done += len(good)
        rate = done / max(1, time.time() - start)
        print(f"{i + len(chunk)}/{len(rows)} done={done} {rate:.1f}/s", flush=True)


if __name__ == "__main__":
    main()
