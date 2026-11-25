import tempfile
import unittest
from pathlib import Path
from scan_resources import scan

class ScanTest(unittest.TestCase):
    def test_attachment_and_duplicate(self):
        html = '<div class="message default" id="message42"><div class="body"><div class="date" title="8 November 2026"></div><div class="media_file"><div class="body"><div class="title">Example.pdf</div><div class="status">15 MB</div></div></div></div></div>'
        with tempfile.TemporaryDirectory() as d:
            paths = [Path(d)/"messages7.html", Path(d)/"messages8.html"]
            for p in paths:
                p.write_text(html, encoding="utf-8")
            self.assertEqual(scan(paths), [{"message_id":42,"filename":"Example.pdf","date":"8 November 2026","size":"15 MB"}])

if __name__ == "__main__":
    unittest.main()
