#!/usr/bin/env python3
"""Extract English Wiktionary Persian translations from a kaikki JSONL gzip dump."""
import gzip
import json
import sys

def extract(src, dest):
    result={}
    with gzip.open(src,"rt",encoding="utf-8") as stream:
        for line in stream:
            entry=json.loads(line)
            word=str(entry.get("word","")).strip().lower()
            if not word: continue
            for trans in entry.get("translations") or []:
                if trans.get("lang_code")!="fa": continue
                value=str(trans.get("word","")).strip()
                if not value: continue
                sense=str(trans.get("sense","") or trans.get("sense_gloss","") or "").strip()
                item={"fa":value,"sense":sense}
                bucket=result.setdefault(word,[])
                if item not in bucket: bucket.append(item)
    with open(dest,"w",encoding="utf-8") as out: json.dump(result,out,ensure_ascii=False)

if __name__=="__main__": extract(sys.argv[1],sys.argv[2])
