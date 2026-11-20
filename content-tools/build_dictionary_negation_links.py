#!/usr/bin/env python3
"""Find French negative-prefix pairs (compréhensible / incompréhensible) whose two headwords exist in the v2 dictionary.

Usage:
  python3 content-tools/build_dictionary_negation_links.py WIKTIONARY.jsonl.gz DICTIONARY.sqlite OUT_DIR

Writes two files to OUT_DIR:
  negation_confirmed.tsv   pairs that Wiktionary lists as antonyms (kept without review)
  negation_candidates.tsv  prefix-only pairs; filter them with review_negation_candidates.sh
"""

import argparse
import gzip
import json
import sqlite3
import unicodedata
from pathlib import Path

from build_dictionary import POS

POSES = ("noun", "adjective", "verb", "adverb")


def negatives(word):
    """Spellings of word with a French negative prefix."""
    out = {"in" + word, "dé" + word, "dés" + word, "mé" + word, "més" + word, "non-" + word, "a" + word}
    first = word[:1]
    if first in "bmp":
        out.add("im" + word)
    if first == "l":
        out.add("il" + word)
    if first == "r":
        out.add("ir" + word)
    if first in "aeéèioôuh":
        out |= {"in" + word, "an" + word}
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("wiktionary", type=Path)
    parser.add_argument("dictionary", type=Path)
    parser.add_argument("out_dir", type=Path)
    args = parser.parse_args()

    with sqlite3.connect(args.dictionary.resolve().as_uri() + "?mode=ro", uri=True) as db:
        entries = {(w, p) for w, p in db.execute("SELECT word, pos FROM entry") if p in POSES}

    def headword(value):
        return unicodedata.normalize("NFC", value.replace("’", "'"))

    antonyms = set()
    with gzip.open(args.wiktionary, "rt", encoding="utf-8") as source:
        for line in source:
            record = json.loads(line)
            pos = POS.get(record.get("pos"))
            if record.get("lang_code") != "fr" or pos not in POSES:
                continue
            word = headword(record["word"])
            for antonym in record.get("antonyms", []):
                other = headword(antonym.get("word", ""))
                antonyms.add((word, pos, other))
                antonyms.add((other, pos, word))

    confirmed, candidates = set(), set()
    for word, pos in entries:
        if len(word) < 3:
            continue
        for negative in negatives(word):
            if (negative, pos) in entries:
                (confirmed if (word, pos, negative) in antonyms else candidates).add((word, pos, negative))

    assert ("compréhensible", "adjective", "incompréhensible") in confirmed | candidates
    args.out_dir.mkdir(parents=True, exist_ok=True)
    for name, rows in (("negation_confirmed.tsv", confirmed), ("negation_candidates.tsv", candidates)):
        (args.out_dir / name).write_text("".join("\t".join(r) + "\n" for r in sorted(rows)), encoding="utf-8")
    print(f"confirmed={len(confirmed)} candidates={len(candidates)}")


if __name__ == "__main__":
    main()
