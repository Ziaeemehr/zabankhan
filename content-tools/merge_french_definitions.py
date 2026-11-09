#!/usr/bin/env python3
"""Merge French Wiktionnaire definitions into the offline French dictionary.

Input is a Wiktextract JSONL extract from the French-language Wiktionnaire,
available through kaikki.org/frwiktionary/rawdata.html. Definitions are kept
in French and are never translated from the English glosses.

    python3 content-tools/merge_french_definitions.py DICT.sqlite FRWIKTIONARY.jsonl.gz

The source is CC BY-SA; retain this provenance and the source link when
publishing generated dictionary shards.
"""
import argparse
import gzip
import json
import sqlite3
import unicodedata
from collections import defaultdict

POS = {
    "noun": "noun", "verb": "verb", "adj": "adjective", "adjective": "adjective",
    "adv": "adverb", "adverb": "adverb", "pron": "pronoun", "pronoun": "pronoun",
    "prep": "preposition", "preposition": "preposition", "conj": "conjunction",
    "conjunction": "conjunction", "intj": "interjection", "interjection": "interjection",
    "det": "article", "article": "article", "num": "numeral", "numeral": "numeral",
    "phrase": "phrase", "prep_phrase": "phrase", "particle": "particle",
    "prefix": "prefix", "suffix": "suffix", "abbrev": "abbreviation",
    "abbreviation": "abbreviation",
}
MAX_DEFINITIONS = 6
MAX_DEFINITION_CHARS = 240


def clean(text):
    return " ".join(str(text).replace("\t", " ").split())


def key(text):
    text = unicodedata.normalize("NFD", clean(text).lower().replace("’", "'"))
    return "".join(char for char in text if unicodedata.category(char) != "Mn")


def source_entries(path):
    opener = gzip.open if path.endswith(".gz") else open
    grouped = defaultdict(list)
    with opener(path, "rt", encoding="utf-8") as source:
        for line in source:
            entry = json.loads(line)
            if entry.get("lang_code") != "fr" or entry.get("pos") not in POS:
                continue
            word = clean(entry.get("word", "")).replace("’", "'")
            if not word:
                continue
            for sense in entry.get("senses") or []:
                tags = set(sense.get("tags") or [])
                if sense.get("form_of") or sense.get("alt_of") or {"form-of", "alt-of"} & tags:
                    continue
                for gloss in sense.get("glosses") or []:
                    definition = clean(gloss)
                    if definition and len(definition) <= MAX_DEFINITION_CHARS and not definition.endswith(":") and definition not in grouped[(key(word), POS[entry["pos"]])]:
                        values = grouped[(key(word), POS[entry["pos"]])]
                        if len(values) < MAX_DEFINITIONS:
                            values.append(definition)
    return grouped


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("database")
    parser.add_argument("wiktionnaire_jsonl")
    args = parser.parse_args()
    definitions = source_entries(args.wiktionnaire_jsonl)
    db = sqlite3.connect(args.database)
    columns = {row[1] for row in db.execute("PRAGMA table_info(entry)")}
    if "fr" not in columns:
        db.execute("ALTER TABLE entry ADD COLUMN fr TEXT NOT NULL DEFAULT ''")
    rows = db.execute("SELECT id, word, pos FROM entry").fetchall()
    updates = []
    for entry_id, word, pos in rows:
        values = definitions.get((key(word), pos), [])
        if values:
            updates.append((json.dumps(values, ensure_ascii=False), entry_id))
    db.executemany("UPDATE entry SET fr = ? WHERE id = ?", updates)
    db.execute("INSERT OR REPLACE INTO meta(key, value) VALUES(?, ?)",
               ("french_definition_source", "French Wiktionnaire via Kaikki Wiktextract (CC BY-SA); https://fr.wiktionary.org/wiki/"))
    db.commit()
    print(f"Added French definitions to {len(updates)} of {len(rows)} entries")


if __name__ == "__main__":
    main()
