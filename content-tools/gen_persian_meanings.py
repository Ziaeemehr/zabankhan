#!/usr/bin/env python3
"""Generate Persian equivalents per English sense in an offline dictionary."""
import argparse
import json
import re
import sqlite3
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

PERSIAN = re.compile(r"[\u0600-\u06ff]")
PROMPT_VERSION = "sense-fa-v1"
STOP = {"a","an","the","to","of","in","on","at","for","with","by","or","and","as","be","one","oneself","someone","something","sth","sb","etc"}
SCHEMA = {"type":"object","additionalProperties":False,"required":["items"],"properties":{"items":{"type":"array","items":{"type":"object","additionalProperties":False,"required":["id","fa","en_short","confidence"],"properties":{"id":{"type":"string"},"fa":{"type":["string","null"]},"en_short":{"type":["string","null"]},"confidence":{"type":"string","enum":["high","low"]}}}}}}
PROMPT = """Translate each English dictionary sense into 1-3 natural Persian equivalents, separated by Persian comma. Verbs must be infinitives. Use only the supplied sense. Prefer wiktionary_fa suggestions when they fit this sense; never copy a suggestion that does not fit. Return null and low confidence if uncertain. fa must contain Persian script, no Latin letters, and be under 120 characters. Return JSON: {\"items\":[{\"id\":...,\"fa\":...,\"en_short\":...,\"confidence\":\"high|low\"}]} for every id.\n\n"""

def valid_fa(v):
    return isinstance(v, str) and bool(PERSIAN.search(v)) and not re.search(r"[A-Za-z]", v) and len(v) < 120

def ensure_schema(db):
    db.execute("""CREATE TABLE IF NOT EXISTS sense_fa(entry_id INTEGER NOT NULL, sense INTEGER NOT NULL, fa TEXT, en_short TEXT,
        confidence TEXT, model TEXT, prompt_version TEXT, created TEXT, "check" TEXT, status TEXT NOT NULL,
        PRIMARY KEY(entry_id,sense))""")
    db.commit()

def sense_items(rows, translations=None):
    translations = translations or {}
    out=[]
    for row in rows:
        eid,word,pos,gender,en_json,example=row
        for i, gloss in enumerate(json.loads(en_json)):
            sugg=matching_translations(gloss, translations)
            out.append({"id":f"{eid}:{i}","word":word,"pos":pos,"gender":gender or "","en":gloss,
                        "example":(example.split("\t")[0] if example else "") if i == 0 else "", "wiktionary_fa":sugg})
    return out

def normalize(s):
    return re.sub(r"[\u200c\s]", "", s.replace("ي","ی").replace("ك","ک")).casefold()

def matching_translations(gloss, data):
    # Match the content words (including compound words) of the gloss against dump lemmas.
    words=[w for w in re.findall(r"[A-Za-z][A-Za-z'-]*", gloss.lower()) if w not in STOP]
    found=[]; seen=set()
    for word in words:
        for item in data.get(word, []):
            key=(normalize(item.get("fa", "")), item.get("sense", ""))
            if item.get("fa") and key not in seen:
                seen.add(key); found.append({"fa":item["fa"],"sense":item.get("sense", "")})
    return found

def ask_codex(prompt, schema, usage_path):
    with tempfile.TemporaryDirectory() as tmp:
        Path(tmp,"schema.json").write_text(json.dumps(schema))
        res=subprocess.run(["codex","exec","-m","gpt-6-luna","-c","model_reasoning_effort=low","-s","read-only","--ephemeral","--skip-git-repo-check","-C",tmp,"--output-schema",f"{tmp}/schema.json","-o",f"{tmp}/answer.json","--json",prompt],capture_output=True,text=True,timeout=900,stdin=subprocess.DEVNULL)
        usage=[]
        for line in res.stdout.splitlines():
            try:
                event=json.loads(line)
                if "usage" in event: usage.append(event["usage"])
            except ValueError: pass
        with open(usage_path,"a") as f: f.write(json.dumps({"t":time.time(),"rc":res.returncode,"usage":usage,"err":res.stderr[-400:] if res.returncode else ""})+"\n")
        if res.returncode: raise RuntimeError(res.stderr[-400:])
        return json.loads(Path(tmp,"answer.json").read_text())

def ask_local(url, items):
    body=PROMPT+"\n".join(json.dumps(x,ensure_ascii=False) for x in items)
    req=urllib.request.Request(url.rstrip("/")+"/v1/chat/completions",data=json.dumps({"model":"gemma4","temperature":0.2,"response_format":{"type":"json_object"},"messages":[{"role":"user","content":body}]}).encode(),headers={"Content-Type":"application/json"})
    with urllib.request.urlopen(req,timeout=600) as r: content=json.load(r)["choices"][0]["message"]["content"]
    return json.loads(content[content.find("{"):content.rfind("}")+1])

def generate(db, backend="local", url="http://127.0.0.1:8080", limit=20000, batch_size=None, prompt_version=PROMPT_VERSION, fa_translations=None, only_status=None, shard=(0,1)):
    ensure_schema(db)
    batch_size=batch_size or (60 if backend=="codex" else 20)
    data=json.loads(Path(fa_translations).read_text()) if fa_translations else {}
    rows=db.execute("SELECT id,word,pos,gender,en,example FROM entry WHERE rank < ? ORDER BY rank",(limit,)).fetchall()
    items=sense_items(rows,data); todo=[]
    for item in items:
        eid,si=map(int,item["id"].split(":"))
        old=db.execute("SELECT status,prompt_version FROM sense_fa WHERE entry_id=? AND sense=?",(eid,si)).fetchone()
        if old and old[0]=="passed" and old[1]==prompt_version: continue
        if only_status and (not old or old[0]!=only_status): continue
        if eid % shard[1] != shard[0]: continue  # --shard i/n: each process takes whole entries with id % n == i
        todo.append(item)
    print(f"{len(todo)} senses to generate",flush=True)
    for offset in range(0,len(todo),batch_size):
        chunk=todo[offset:offset+batch_size]; answer={}
        for attempt in range(3):
            try:
                payload=PROMPT+"\n".join(json.dumps(x,ensure_ascii=False) for x in chunk)
                result=ask_codex(payload,SCHEMA,f"content-tools/out/codex_usage.{shard[0]}.jsonl") if backend=="codex" else ask_local(url,chunk)
                answer={x.get("id"):x for x in result.get("items",[]) if isinstance(x,dict)}; break
            except Exception as e:
                print(f"batch {offset}: {e}",file=sys.stderr,flush=True)
                if backend=="codex" and "limit" in str(e).lower():
                    print("Quota/rate limit reached. Resume by rerunning the same command; completed senses are checkpointed.",file=sys.stderr,flush=True); return 2
        for item in chunk:
            eid,si=map(int,item["id"].split(":")); got=answer.get(item["id"],{})
            fa=got.get("fa"); conf=got.get("confidence","low")
            good=conf=="high" and valid_fa(fa)
            status="generated" if good else "uncertain"
            db.execute('INSERT INTO sense_fa VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT(entry_id,sense) DO UPDATE SET fa=excluded.fa,en_short=excluded.en_short,confidence=excluded.confidence,model=excluded.model,prompt_version=excluded.prompt_version,created=excluded.created,"check"=NULL,status=excluded.status',
                       (eid,si,fa if valid_fa(fa) else None,got.get("en_short"),conf,"gpt-6-luna" if backend=="codex" else "gemma4",prompt_version,time.strftime("%Y-%m-%dT%H:%M:%SZ",time.gmtime()),None,status))
        db.commit()
        # Only passed rows populate the app-facing legacy field; entries without any passed sense keep their old value.
        for row in {int(x["id"].split(":")[0]) for x in chunk}:
            row=(row,)
            vals=[x[0] for x in db.execute("SELECT fa FROM sense_fa WHERE entry_id=? AND status='passed' ORDER BY sense",(row[0],)) if x[0]]
            if vals: db.execute("UPDATE entry SET fa=? WHERE id=?",("، ".join(dict.fromkeys(vals)),row[0]))
        db.commit(); print(f"{offset+len(chunk)}/{len(todo)} checkpointed",flush=True)
    return 0

def main():
    p=argparse.ArgumentParser(); p.add_argument("db"); p.add_argument("--backend",choices=["codex","local"],default="local"); p.add_argument("--url",default="http://127.0.0.1:8080"); p.add_argument("--limit",type=int,default=20000); p.add_argument("--batch-size",type=int); p.add_argument("--prompt-version",default=PROMPT_VERSION); p.add_argument("--fa-translations"); p.add_argument("--shard",default="0/1",help="i/n: run n processes in parallel, i=0..n-1"); a=p.parse_args()
    shard=tuple(map(int,a.shard.split("/")))
    db=sqlite3.connect(a.db,timeout=120)
    # Switching to WAL needs an exclusive lock and fails when parallel shards start together; only switch if not yet WAL.
    if db.execute("PRAGMA journal_mode").fetchone()[0]!="wal": db.execute("PRAGMA journal_mode=WAL")
    rc=generate(db,a.backend,a.url,a.limit,a.batch_size,a.prompt_version,a.fa_translations,shard=shard); sys.exit(rc or 0)
if __name__=="__main__": main()
