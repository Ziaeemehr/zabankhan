"""Extract attachment metadata from Telegram Desktop HTML exports locally."""
import argparse
import json
import re
import unicodedata
from pathlib import Path
from html.parser import HTMLParser

class ExportParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack = []
        self.current = None
        self.records = []
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        classes = a.get("class", "").split()
        mid = re.fullmatch(r"message(\d+)", a.get("id", ""))
        if tag == "div" and mid and "message" in classes:
            self.current = {"message_id": int(mid[1]), "filename": "", "date": "", "size": ""}
            self.stack = []
        if self.current is None or tag not in ("div", "a"):
            return
        field = None
        if "date" in classes:
            self.current["date"] = a.get("title", "")
        if "media_file" in classes:
            field = "file"
        elif any(scope == "file" for _, scope in self.stack):
            if "title" in classes:
                field = "filename"
            elif "status" in classes:
                field = "size"
        self.stack.append((tag, field))

    def handle_data(self, data):
        if self.current and self.stack and self.stack[-1][1] in ("filename", "size"):
            self.current[self.stack[-1][1]] += data

    def handle_endtag(self, tag):
        if self.current is not None and self.stack and self.stack[-1][0] == tag:
            self.stack.pop()
            if not self.stack:
                r = self.current
                r["filename"] = unicodedata.normalize("NFC", r["filename"].strip())
                r["size"] = r["size"].strip()
                if r["filename"].lower().endswith((".pdf", ".epub")):
                    self.records.append(r)
                self.current = None

def scan(paths):
    records = {}
    for path in paths:
        parser = ExportParser()
        parser.feed(path.read_text(encoding="utf-8"))
        parser.close()
        for record in parser.records:
            records[record["message_id"]] = record
    return sorted(records.values(), key=lambda r: r["message_id"], reverse=True)

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("directory", type=Path)
    p.add_argument("--output", required=True, type=Path)
    args = p.parse_args()
    paths = sorted(args.directory.glob("messages*.html"))
    if not paths:
        p.error("No messages*.html files found")
    rows = scan(paths)
    args.output.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"{len(rows)} attachment records; private discovery output, not a release catalog")

if __name__ == "__main__":
    main()
