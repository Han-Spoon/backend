#!/usr/bin/env python3
"""Prepare a one-account-bound guardian code after a verified email reply.

This script does not send mail or verify a guardian. An operator must first send
the complete current legal documents and retain the guardian's explicit reply.
Run the emitted SQL in the production database through the normal audited path,
then provide the code to that guardian. Never commit or log the code or SQL.
"""

import argparse
import hashlib
import re
import secrets
import uuid
from datetime import datetime, timedelta, timezone


def sql_string(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--guardian-email", required=True)
    parser.add_argument("--evidence-reference", required=True, help="Secure case ID for the saved explicit reply")
    parser.add_argument("--document-version", required=True, help="Must equal the deployed legal bundle version")
    parser.add_argument("--verified-email-reply", action="store_true", required=True,
                        help="Confirm the full documents were emailed and the guardian explicitly replied")
    args = parser.parse_args()
    if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", args.guardian_email):
        parser.error("Invalid guardian email")
    if len(args.evidence_reference) > 255 or not args.evidence_reference.strip():
        parser.error("Evidence reference must be 1–255 characters")
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", args.document_version):
        parser.error("Document version must be YYYY-MM-DD")

    code = "hs-" + secrets.token_urlsafe(32)
    code_hash = hashlib.sha256(code.encode()).hexdigest()
    expiry = (datetime.now(timezone.utc) + timedelta(days=365)).isoformat()
    approval_id = str(uuid.uuid4())
    print("Give this code only to the verified guardian (shown once):", code)
    print("Apply this SQL through the audited database workflow:")
    print(
        "INSERT INTO guardian_approvals "
        "(id, code_hash, guardian_email, document_version, evidence_reference, expires_at) VALUES ("
        + ", ".join(map(sql_string, [approval_id, code_hash, args.guardian_email,
                                     args.document_version, args.evidence_reference, expiry])) + ");"
    )


if __name__ == "__main__":
    main()
