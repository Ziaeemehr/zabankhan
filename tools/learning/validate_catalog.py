"""Validate an offline learning resource catalog, optionally for release."""

import argparse
from datetime import date
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import urlsplit

LEVELS = {"A1", "A2", "B1", "B2", "C1", "C2"}
TYPES = {"magazine", "reader", "textbook", "audio", "video", "website"}
REVIEW_ROLES = {"blind", "adversarial", "pedagogical"}
AUDIENCES = {"children", "teenagers", "general"}


def content_hash(item):
    content = {key: value for key, value in item.items()
               if key not in {"reviews", "ownerApproval", "status"}}
    canonical = json.dumps(content, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def nonblank(value):
    return isinstance(value, str) and bool(value.strip())


def https_url(value):
    if not isinstance(value, str) or any(c.isspace() or c == "\\" for c in value):
        return False
    url = urlsplit(value)
    return url.scheme == "https" and bool(url.hostname) and url.username is None and url.password is None


def validate_catalog(catalog, release=False):
    require(isinstance(catalog, dict) and type(catalog.get("schemaVersion")) is int
            and catalog["schemaVersion"] == 1, "Unsupported catalog schema version")
    entries = catalog.get("entries")
    require(isinstance(entries, list), "entries must be an array")
    seen = set()
    for item in entries:
        require(isinstance(item, dict), "Each entry must be an object")
        item_id = item.get("id")
        require(isinstance(item_id, str) and re.fullmatch(r"[a-z0-9]+(?:[.-][a-z0-9]+)+", item_id)
                and item_id not in seen, f"Invalid or duplicate entry ID: {item_id}")
        seen.add(item_id)
        try:
            validate_entry(item, release)
        except ValueError as error:
            raise ValueError(f"{item_id}: {error}") from error


def validate_entry(item, release):
    language = item.get("language")
    require(isinstance(language, str) and re.fullmatch(r"[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*", language),
            "Invalid target language")
    require(item.get("type") in TYPES and item.get("audience") in AUDIENCES
            and type(item.get("illustrated")) is bool, "Invalid type, audience or illustration flag")
    for field in ("series", "provenance"):
        require(nonblank(item.get(field)), f"Missing {field}")
    levels = item.get("levels")
    require(isinstance(levels, list) and levels and all(isinstance(level, str) and level in LEVELS
            for level in levels) and len(set(levels)) == len(levels), "Invalid CEFR levels")
    descriptions = item.get("descriptions")
    require(isinstance(descriptions, dict) and "fa" in descriptions and all(
        re.fullmatch(r"[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*", locale) and nonblank(value)
        for locale, value in descriptions.items()), "Persian and any optional descriptions must be nonblank")
    rights = item.get("rights")
    require(rights in {"unknown", "link_only", "download_allowed"}, "Invalid rights status")
    require(item.get("generation") in {"original", "llm"}, "Missing generation method")
    require(item.get("status") in {"draft", "approved"}, "Invalid review status")
    for field in ("officialUrl", "downloadUrl"):
        if field in item:
            require(https_url(item[field]), f"Invalid HTTPS URL for {field}")
    if "downloadUrl" in item:
        require(rights == "download_allowed" and item.get("status") == "approved",
                "Downloads require approved, authorized rights")
    if release or item.get("status") == "approved":
        validate_approval(item)


def validate_approval(item):
    require(item.get("status") == "approved", "Draft entry cannot be released")
    require(item["rights"] != "unknown" and nonblank(item.get("rightsEvidence"))
            and "officialUrl" in item, "Verified rights evidence and official URL required")
    verified = item.get("verifiedOn")
    require(isinstance(verified, str) and re.fullmatch(r"\d{4}-\d{2}-\d{2}", verified),
            "Rights verification date required")
    date.fromisoformat(verified)
    digest = content_hash(item)
    owner = item.get("ownerApproval")
    require(isinstance(owner, dict) and nonblank(owner.get("reviewer"))
            and owner.get("verdict") == "pass" and owner.get("contentHash") == digest,
            "Current owner approval required")
    if item["generation"] == "llm":
        reviews = item.get("reviews")
        require(isinstance(reviews, list) and len(reviews) == 3, "Three independent reviews required")
        roles = set()
        reviewers = set()
        for review in reviews:
            require(isinstance(review, dict) and all(nonblank(review.get(field))
                    for field in ("role", "reviewer", "version", "rubric", "findings"))
                    and review.get("verdict") == "pass" and review.get("contentHash") == digest,
                    "Every review must pass on the current revision")
            roles.add(review["role"])
            reviewers.add(review["reviewer"])
        require(roles == REVIEW_ROLES and len(reviewers) == 3, "Three distinct review roles and reviewers required")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("catalog", type=Path)
    parser.add_argument("--release", action="store_true")
    args = parser.parse_args()
    try:
        catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
        validate_catalog(catalog, args.release)
    except (OSError, ValueError, TypeError) as error:
        parser.error(str(error))
    print(f"Validated {len(catalog['entries'])} resource entries")


if __name__ == "__main__":
    main()
