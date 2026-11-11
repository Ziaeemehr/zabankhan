#!/usr/bin/env python3
"""Build a compact French Wiktionnaire pack: python3 tools/build_monolingual_pack.py FR.jsonl OUT_DIR"""
import argparse
import gzip
import html
import json
import re
import unicodedata
from collections import defaultdict
from pathlib import Path

POS = {"noun", "verb", "adj", "adjective", "adv", "adverb", "pron", "pronoun", "prep", "preposition", "conj", "conjunction", "intj", "interjection", "det", "article", "num", "numeral", "phrase", "prep_phrase", "particle", "prefix", "suffix", "abbrev", "abbreviation"}
POS_LABEL = {"adj": "adjective", "adv": "adverb", "pron": "pronoun", "prep": "preposition", "conj": "conjunction", "intj": "interjection", "det": "article", "num": "numeral", "prep_phrase": "phrase", "abbrev": "abbreviation"}
META_TAGS = {"form-of", "alt-of", "table-tags", "no-gloss", "placeholder", "unrecognized-form"}
MAX_SENSES, MAX_CHARS, MAX_EXAMPLE_CHARS = 5, 240, 180


def plain(value):
    value = html.unescape(str(value or ""))
    value = re.sub(r"<[^>]+>", " ", value)
    value = re.sub(r"\[\[(?:[^]|]*\|)?([^]]+)\]\]", r"\1", value)
    value = re.sub(r"\[([^ ]+) ([^]]+)\]", r"\2", value)
    value = re.sub(r"'''?([^']+)'''?", r"\1", value)
    value = re.sub(r"\{\{[^{}]*\}\}", " ", value)
    return " ".join(value.replace("\u00a0", " ").split()).strip(" ;,–—")


def norm(word):
    text = word.lower().replace("’", "'").translate(str.maketrans({"œ": "oe", "æ": "ae", "ø": "o", "ł": "l", "þ": "th"}))
    text = unicodedata.normalize("NFD", text)
    return "".join(c for c in text if unicodedata.category(c) != "Mn")


def shard_key(word):
    first = norm(word).split(" ", 1)[0][:2]
    return "".join(c if "a" <= c <= "z" else "_" for c in first).ljust(2, "_")


def labels(entry):
    out = []
    for tag in entry:
        text = plain(tag).replace("_", " ")
        if text and text not in out:
            out.append(text)
    return out[:5]


def extract(path):
    grouped = {}
    opener = gzip.open if str(path).endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as source:
        for line in source:
            row = json.loads(line)
            pos = row.get("pos", "")
            if row.get("lang_code") != "fr" or pos not in POS:
                continue
            word = plain(row.get("word", "")).replace("’", "'")
            if not word:
                continue
            key = (norm(word), POS_LABEL.get(pos, pos))
            item = grouped.setdefault(key, {"word": word, "pos": POS_LABEL.get(pos, pos), "gender": "", "senses": []})
            for template in row.get("head_templates") or []:
                args = template.get("args") or {}
                gender = args.get("1") or args.get("g")
                if gender in {"m", "f", "mf", "m-p", "f-p"}:
                    item["gender"] = gender.split("-")[0]
                    break
            for sense in row.get("senses") or []:
                tags = labels(sense.get("tags") or [])
                if sense.get("form_of") or sense.get("alt_of") or META_TAGS.intersection(sense.get("tags") or []):
                    continue
                definition = next((plain(g) for g in sense.get("glosses") or [] if plain(g)), "")
                if not definition or len(definition) > MAX_CHARS or definition.endswith(":"):
                    continue
                example = ""
                for ex in sense.get("examples") or []:
                    example = plain(ex.get("text", ""))
                    if example and len(example) <= MAX_EXAMPLE_CHARS:
                        break
                    example = ""
                record = {"definition": definition, "tags": tags, "example": example}
                if record not in item["senses"] and len(item["senses"]) < MAX_SENSES:
                    item["senses"].append(record)
    return grouped


def build(source, output):
    entries = extract(source)
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    shards = defaultdict(list)
    for (word, _), entry in sorted(entries.items()):
        if entry["senses"]:
            shard = shard_key(entry["word"])
            shards[shard].append(entry)
    for shard, rows in shards.items():
        (output / f"{shard}.json").write_text(json.dumps({"entries": rows}, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    index = {"lang": "fr", "format": 1, "version": 1, "entries": sum(map(len, shards.values())), "shards": sorted(shards),
             "sources": "French Wiktionnaire via Kaikki Wiktextract (CC BY-SA 4.0)",
             "license": "https://creativecommons.org/licenses/by-sa/4.0/"}
    (output / "index.json").write_text(json.dumps(index, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    size = sum(p.stat().st_size for p in output.glob("*.json"))
    print(f"{index['entries']} entries, {len(shards)} shards, {size:,} bytes JSON ({size / 1024 / 1024:.1f} MiB); gzip estimate {size * 0.28 / 1024 / 1024:.1f} MiB")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("jsonl", help="French Wiktionary extraction JSONL(.gz) from kaikki.org")
    parser.add_argument("output", help="directory for index.json and two-character JSON shards")
    args = parser.parse_args()
    build(args.jsonl, args.output)
