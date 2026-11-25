import unittest

from validate_catalog import content_hash, validate_catalog


def entry():
    return {
        "id": "fr.magazine.example",
        "language": "fr",
        "type": "magazine",
        "audience": "children",
        "illustrated": True,
        "series": "Example",
        "levels": ["A1"],
        "descriptions": {"fa": "مجلهٔ تصویری"},
        "provenance": "Publisher catalog",
        "rights": "unknown",
        "generation": "llm",
        "status": "draft",
    }


class CatalogTest(unittest.TestCase):
    def validate(self, *entries, release=False):
        validate_catalog({"schemaVersion": 1, "entries": list(entries)}, release)

    def test_offline_draft(self):
        self.validate(entry())

    def test_duplicate_and_missing_persian_rejected(self):
        item = entry()
        with self.assertRaises(ValueError):
            self.validate(item, item)
        item["descriptions"] = {"en": "Example"}
        with self.assertRaises(ValueError):
            self.validate(item)

    def test_unknown_rights_cannot_download(self):
        with self.assertRaises(ValueError):
            self.validate(dict(entry(), downloadUrl="https://example.org/file.pdf"))

    def test_release_requires_three_current_reviews_and_owner_approval(self):
        item = dict(entry(), status="approved", rights="link_only",
                    officialUrl="https://example.org/magazine", rightsEvidence="Publisher listing",
                    verifiedOn="2026-11-25")
        revision = content_hash(item)
        item["ownerApproval"] = {"reviewer": "owner", "contentHash": revision, "verdict": "pass"}
        item["reviews"] = [
            {"role": role, "reviewer": reviewer, "version": "1", "rubric": "1",
             "findings": "Evidence checked", "verdict": "pass", "contentHash": revision}
            for role, reviewer in zip(("blind", "adversarial", "pedagogical"), ("a", "b", "c"))
        ]
        self.validate(item, release=True)
        missing = dict(item, reviews=item["reviews"][:2])
        with self.assertRaises(ValueError):
            self.validate(missing, release=True)
        with self.assertRaises(ValueError):
            self.validate(dict(item, series="Changed"), release=True)
        with self.assertRaises(ValueError):
            self.validate(dict(item, status="draft"), release=True)

        with self.assertRaises(ValueError):
            self.validate(dict(item, reviews=[
                dict(review, reviewer="same") for review in item["reviews"]
            ]), release=True)
        with self.assertRaises(ValueError):
            self.validate(dict(item, ownerApproval=None), release=True)
