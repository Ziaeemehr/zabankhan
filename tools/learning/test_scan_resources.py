import tempfile
import unittest
from pathlib import Path

from scan_resources import scan


class ScanTest(unittest.TestCase):
    def test_attachment_and_duplicate(self):
        attachment = """
        <div class="message default clearfix" id="message42">
          <div class="body">
            <div class="pull_right date details" title="2026-10-01"></div>
            <div class="media_wrap"><a class="media media_file" href="files/draft.pdf">
              <div class="title">Café <span>Junior</span>.PDF</div>
              <div class="status">1 MB</div>
            </a></div>
          </div>
        </div>
        """
        with tempfile.TemporaryDirectory() as directory:
            paths = [Path(directory) / name for name in ("messages.html", "messages2.html")]
            for path in paths:
                path.write_text(attachment, encoding="utf-8")
            self.assertEqual(scan(paths), [{
                "message_id": 42, "filename": "Café Junior.PDF",
                "date": "2026-10-01", "size": "1 MB",
            }])


if __name__ == "__main__":
    unittest.main()
