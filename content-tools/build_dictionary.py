#!/usr/bin/env python3
"""Build the offline French dictionary pack (SQLite) for Zabankhan.

Sources (both CC BY-SA 4.0, credit them in the app):
  - English Wiktionary via kaikki.org: https://kaikki.org/dictionary/French/kaikki.org-dictionary-French.jsonl.gz
  - Lexique 3.83 (frequencies): http://www.lexique.org/databases/Lexique383/Lexique383.zip

Step 1 (this script) writes entries with IPA, gender, English glosses, one example and verb forms:
    python3 content-tools/build_dictionary.py KAIKKI.jsonl.gz Lexique383.tsv OUT.sqlite
Step 2: content-tools/gen_persian_meanings.py fills the Persian meanings with a local model.
Step 2a: content-tools/merge_french_definitions.py adds sourced French-to-French definitions.
The dictionary packer publishes compact shards from the resulting database.
"""
import csv
import gzip
import json
import sqlite3
import sys
from collections import defaultdict

UNRANKED = 1_000_000        # rank for headwords Lexique does not list (rare words, multiword expressions)
MAX_GLOSSES = 5
MAX_GLOSS_CHARS = 200
# Map kaikki parts of speech to the app's labels (see ui/reader/PartOfSpeech.kt).
POS = {"noun": "noun", "verb": "verb", "adj": "adjective", "adv": "adverb", "pron": "pronoun", "prep": "preposition",
       "conj": "conjunction", "intj": "interjection", "det": "article", "article": "article", "num": "numeral",
       "phrase": "phrase", "prep_phrase": "phrase", "particle": "particle", "prefix": "prefix", "suffix": "suffix",
       "abbrev": "abbreviation"}
# Verb forms kept for the conjugation table: (mood/tense tags) -> label.
TENSES = [({"indicative", "present"}, "present"), ({"indicative", "imperfect"}, "imperfect"),
          ({"indicative", "future"}, "future"), ({"conditional"}, "conditional"),
          ({"subjunctive", "present"}, "subjunctive"), ({"past", "participle"}, "past_participle"),
          ({"present", "participle"}, "present_participle")]
PERSONS = [({"first-person", "singular"}, 0), ({"second-person", "singular"}, 1), ({"third-person", "singular"}, 2),
           ({"first-person", "plural"}, 3), ({"second-person", "plural"}, 4), ({"third-person", "plural"}, 5)]


def clean(text):
    return " ".join(str(text).replace("\t", " ").split())


def lemma_frequencies(path):
    freq = defaultdict(float)
    with open(path, encoding="utf-8") as f:
        for row in csv.DictReader(f, delimiter="\t"):
            if row["islem"] != "1":
                continue
            try:
                freq[row["lemme"]] = max(freq[row["lemme"]], float(row["freqlemfilms2"] or 0) + float(row["freqlemlivres"] or 0))
            except ValueError:
                pass
    return freq


def gender(entry):
    for t in entry.get("head_templates") or []:
        g = (t.get("args") or {}).get("1") or (t.get("args") or {}).get("g")
        if g in ("m", "f", "mf", "m-p", "f-p"):
            return g.split("-")[0]
    return ""


def verb_forms(entry):
    table = {}
    for form in entry.get("forms") or []:
        tags = set(form.get("tags") or [])
        word = clean(form.get("form", ""))
        if not word or "multiword-construction" in tags or "table-tags" in tags or "inflection-template" in tags:
            continue
        for tense_tags, label in TENSES:
            if not tense_tags <= tags:
                continue
            if label.endswith("participle"):
                table.setdefault(label, word)
            else:
                for person_tags, index in PERSONS:
                    if person_tags <= tags:
                        table.setdefault(label, [""] * 6)
                        if not table[label][index]:
                            table[label][index] = word
            break
    return table


def main():
    kaikki, lexique, out = sys.argv[1:4]
    freq = lemma_frequencies(lexique)
    rank = {w: i for i, w in enumerate(sorted(freq, key=freq.get, reverse=True))}
    entries = {}
    with gzip.open(kaikki, "rt", encoding="utf-8") as f:
        for line in f:
            e = json.loads(line)
            word = clean(e.get("word", "")).replace("’", "'")
            if not word or e.get("pos") not in POS:
                continue
            senses = [s for s in e.get("senses") or [] if not (s.get("form_of") or s.get("alt_of") or
                      {"form-of", "alt-of"} & set(s.get("tags") or []))]
            glosses = []
            example = ""
            for s in senses:
                for g in s.get("glosses") or []:
                    g = clean(g)[:MAX_GLOSS_CHARS]
                    if g and g not in glosses and len(glosses) < MAX_GLOSSES:
                        glosses.append(g)
                for ex in s.get("examples") or []:
                    if not example and ex.get("text") and ex.get("english"):
                        example = clean(ex["text"]) + "\t" + clean(ex["english"])
            if not glosses:
                continue
            key = (word, POS[e["pos"]])
            ipa = next((clean(s["ipa"]) for s in e.get("sounds") or [] if s.get("ipa")), "")
            prev = entries.get(key)
            if prev:  # several etymologies: merge glosses
                prev["en"] += [g for g in glosses if g not in prev["en"]][:max(0, MAX_GLOSSES - len(prev["en"]))]
                continue
            entries[key] = {"ipa": ipa, "gender": gender(e) if POS[e["pos"]] == "noun" else "", "en": glosses,
                            "example": example, "forms": verb_forms(e) if e["pos"] == "verb" else {}}
    db = sqlite3.connect(out)
    db.executescript("""
        DROP TABLE IF EXISTS entry; DROP TABLE IF EXISTS meta;
        CREATE TABLE entry(id INTEGER PRIMARY KEY, word TEXT NOT NULL, pos TEXT NOT NULL, rank INTEGER NOT NULL,
            ipa TEXT NOT NULL, gender TEXT NOT NULL, en TEXT NOT NULL, example TEXT NOT NULL, forms TEXT NOT NULL,
            fa TEXT NOT NULL DEFAULT '', fr TEXT NOT NULL DEFAULT '');
        CREATE INDEX entry_word ON entry(word);
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
    """)
    rows = sorted(entries.items(), key=lambda kv: (rank.get(kv[0][0], UNRANKED), kv[0][0]))
    db.executemany("INSERT INTO entry(word,pos,rank,ipa,gender,en,example,forms) VALUES(?,?,?,?,?,?,?,?)",
                   [(w, p, rank.get(w, UNRANKED), v["ipa"], v["gender"], json.dumps(v["en"], ensure_ascii=False), v["example"],
                     json.dumps(v["forms"], ensure_ascii=False) if v["forms"] else "") for (w, p), v in rows])
    db.executemany("INSERT INTO meta VALUES(?,?)", [("lang", "fr"), ("format", "1"),
                   ("sources", "English Wiktionary via kaikki.org (CC BY-SA 4.0); Lexique 3.83 (CC BY-SA 4.0)")])
    db.commit()
    print(f"{len(rows)} entries for {len({w for w, _ in entries})} lemmas -> {out}")


if __name__ == "__main__":
    main()
