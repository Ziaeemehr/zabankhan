#!/usr/bin/env python3
"""Judge generated sense translations, or export a blind human-judge sample."""
import argparse
import csv
import json
import random
import sqlite3
import sys
import urllib.request
from pathlib import Path
from gen_persian_meanings import ask_codex, ask_local, SCHEMA, generate

JUDGE_SCHEMA={"type":"object","additionalProperties":False,"required":["items"],"properties":{"items":{"type":"array","items":{"type":"object","additionalProperties":False,"required":["id","verdict"],"properties":{"id":{"type":"string"},"verdict":{"type":"string","enum":["ok","acceptable","wrong"]}}}}}}
PROMPT="Judge whether each Persian equivalent correctly expresses the supplied English sense with the given part of speech. Return only ok, acceptable, or wrong. JSON: {\"items\":[{\"id\":...,\"verdict\":...}]}\n\n"

def judge(db, backend, url="http://127.0.0.1:8080", shard=(0,1)):
    rows=db.execute("SELECT s.entry_id,s.sense,e.word,e.pos,json_extract(e.en,'$['||s.sense||']'),s.fa FROM sense_fa s JOIN entry e ON e.id=s.entry_id WHERE s.status='generated' AND s.entry_id % ? = ?",(shard[1],shard[0])).fetchall()
    inputs=[{"id":f"{r[0]}:{r[1]}","word":r[2],"pos":r[3],"en":r[4],"fa":r[5]} for r in rows]
    for start in range(0,len(inputs),60):
        chunk=inputs[start:start+60]; prompt=PROMPT+"\n".join(json.dumps(x,ensure_ascii=False) for x in chunk)
        if backend=="codex": result=ask_codex(prompt,JUDGE_SCHEMA,f"content-tools/out/codex_usage.judge{shard[0]}.jsonl")
        else:
            req=urllib.request.Request(url.rstrip("/")+"/v1/chat/completions",data=json.dumps({"model":"gemma4","temperature":0,"response_format":{"type":"json_object"},"messages":[{"role":"user","content":prompt}]}).encode(),headers={"Content-Type":"application/json"})
            with urllib.request.urlopen(req,timeout=600) as r: c=json.load(r)["choices"][0]["message"]["content"]; result=json.loads(c[c.find("{"):c.rfind("}")+1])
        verdicts={x.get("id"):x.get("verdict") for x in result.get("items",[]) if isinstance(x,dict)}
        for item in chunk:
            eid,si=map(int,item["id"].split(":")); verdict=verdicts.get(item["id"],"wrong")
            status="passed" if verdict in ("ok","acceptable") else "retry"
            db.execute('UPDATE sense_fa SET "check"=?,status=? WHERE entry_id=? AND sense=?',(verdict,status,eid,si))
        db.commit()
        for eid in {int(x["id"].split(":")[0]) for x in chunk}:
            fa=[r[0] for r in db.execute("SELECT fa FROM sense_fa WHERE entry_id=? AND status='passed' AND confidence='high'",(eid,)) if r[0]]
            if fa: db.execute("UPDATE entry SET fa=? WHERE id=?",("، ".join(dict.fromkeys(fa)),eid))
        db.commit()

def export_sample(db, count, dest):
    rows=db.execute("SELECT s.entry_id,s.sense,e.word,e.pos,json_extract(e.en,'$['||s.sense||']'),s.fa FROM sense_fa s JOIN entry e ON e.id=s.entry_id WHERE s.fa IS NOT NULL").fetchall()
    random.Random(42).shuffle(rows)
    with open(dest,"w",encoding="utf-8",newline="") as f:
        w=csv.writer(f,delimiter="\t"); w.writerow(["blind_id","word","pos","english_gloss","fa"])
        for n,r in enumerate(rows[:count]): w.writerow([f"s{n+1}",*r[2:]])

def main():
    p=argparse.ArgumentParser(); p.add_argument("db"); p.add_argument("--backend",choices=["local","codex"],default="local"); p.add_argument("--url",default="http://127.0.0.1:8080"); p.add_argument("--sample",type=int); p.add_argument("--export"); p.add_argument("--shard",default="0/1",help="i/n: run n judges in parallel, i=0..n-1"); a=p.parse_args()
    shard=tuple(map(int,a.shard.split("/"))); db=sqlite3.connect(a.db,timeout=120)
    if db.execute("PRAGMA journal_mode").fetchone()[0]!="wal": db.execute("PRAGMA journal_mode=WAL")
    if a.sample is not None:
        if not a.export: p.error("--sample requires --export")
        export_sample(db,a.sample,a.export)
    else:
        judge(db,a.backend,a.url,shard)
        # Wrong rows retry once using the same generator; second wrong remains uncertain.
        retried=db.execute("SELECT entry_id,sense FROM sense_fa WHERE status='retry' AND entry_id % ? = ?",(shard[1],shard[0])).fetchall()
        if retried:
            db.executemany("UPDATE sense_fa SET status='retry' WHERE entry_id=? AND sense=?",retried); db.commit()
            generate(db,a.backend,a.url,limit=2**31-1,only_status="retry",shard=shard)
            # The retry output is generated, so judge only those rows one more time.
            judge(db,a.backend,a.url,shard)
            db.execute("UPDATE sense_fa SET status='uncertain' WHERE status='retry' AND entry_id % ? = ?",(shard[1],shard[0])); db.commit()
if __name__=="__main__": main()
