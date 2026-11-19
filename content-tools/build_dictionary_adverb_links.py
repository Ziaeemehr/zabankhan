#!/usr/bin/env python3
"""Export French Wiktionary derivations whose two headwords exist in the v2 dictionary.

Usage: python3 content-tools/build_dictionary_adverb_links.py WIKTIONARY.jsonl.gz DICTIONARY.sqlite
Write stdout to composeResources/files/fr/lexicon/dictionary_adverb_links.tsv.
"""

import argparse
import gzip
import json
import sqlite3
import unicodedata
from pathlib import Path

from build_dictionary import POS


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("wiktionary", type=Path)
    parser.add_argument("dictionary", type=Path)
    args = parser.parse_args()

    with sqlite3.connect(args.dictionary.resolve().as_uri() + "?mode=ro", uri=True) as db:
        entries = set(db.execute("SELECT word, pos FROM entry"))

    def headword(value):
        return unicodedata.normalize("NFC", value.replace("’", "'"))

    links = set()
    with gzip.open(args.wiktionary, "rt", encoding="utf-8") as source:
        for line in source:
            record = json.loads(line)
            pos = POS.get(record.get("pos"))
            if record.get("lang_code") != "fr" or pos not in ("noun", "adjective", "verb"):
                continue
            word = headword(record["word"])
            if (word, pos) not in entries:
                continue
            for derivative in record.get("derived", []):
                adverb = headword(derivative["word"])
                if adverb != word and not any(c.isspace() for c in adverb) and (adverb, "adverb") in entries:
                    links.add((word, pos, adverb))

    assert ("rapide", "adjective", "rapidement") in links
    print("# French Wiktionary contributors (CC BY-SA 4.0), via Kaikki. Source pages: https://fr.wiktionary.org/wiki/{word}")
    print("# Columns: base headword, part of speech, derived adverb")
    for row in sorted(links):
        print("\t".join(row))


if __name__ == "__main__":
    main()
