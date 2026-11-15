#!/usr/bin/env python3
"""Small offline checks for sense-level meaning generation."""
import json
import sqlite3
from gen_persian_meanings import generate, matching_translations, sense_items, valid_fa, ensure_schema

def run():
    db=sqlite3.connect(":memory:")
    db.execute("CREATE TABLE entry(id INTEGER PRIMARY KEY,word,pos,rank,gender,en,example,fa)")
    db.execute("INSERT INTO entry VALUES(1,'prendre','verb',1,'','[\"to take\",\"to have\"]','','')")
    ensure_schema(db)
    items=sense_items(db.execute("SELECT id,word,pos,gender,en,example FROM entry").fetchall())
    assert [x["id"] for x in items]==["1:0","1:1"]
    assert items[1]["en"]=="to have"
    assert valid_fa("گرفتن") and not valid_fa("take گرفتن") and not valid_fa(None)
    suggestions=matching_translations("to take",{"take":[{"fa":"گرفتن","sense":"seize"},{"fa":"بردن","sense":"carry"}]})
    assert suggestions==[{"fa":"گرفتن","sense":"seize"},{"fa":"بردن","sense":"carry"}]
    # Insert one passed row; a fake backend must never be called for it.
    db.execute("INSERT INTO sense_fa VALUES(1,0,'گرفتن',NULL,'high','fake','v1','now','ok','passed')")
    db.commit()
    def fake(*_): raise AssertionError("passed row should be resumed/skipped")
    import gen_persian_meanings as gen
    old=gen.ask_local; gen.ask_local=fake
    try:
        assert generate(db,backend="local",prompt_version="v1")==0
    finally: gen.ask_local=old
    assert db.execute("SELECT fa,status FROM sense_fa WHERE entry_id=1 AND sense=0").fetchone()==("گرفتن","passed")
    assert db.execute("SELECT status FROM sense_fa WHERE entry_id=1 AND sense=1").fetchone()==("uncertain",)
    print("test_meanings: 4 checks passed")

if __name__=="__main__": run()
