#!/usr/bin/env python3
"""Watch a TestFlight build through Apple's Beta App Review.

    python3 tools/monitor_beta_review.py            # poll until it settles
    python3 tools/monitor_beta_review.py --once     # one check, then exit
    python3 tools/monitor_beta_review.py --build 2  # a specific build number

Beta App Review has no webhook and App Store Connect sends mail only on some
transitions, so the alternative is reloading a browser tab for hours. This
polls the App Store Connect API and says something the moment the state moves.

Exit code is 0 while things are fine, 1 if the build was rejected, so it can
gate a script.

Credentials
-----------
The private key is read from `~/.appstoreconnect/private_keys/AuthKey_<id>.p8`
and is never read from, or written to, this repository. The key id and issuer
id are identifiers rather than secrets -- they are useless without the key, in
the same way `TEAM_ID` in `Config.xcconfig` is -- but both can still be
overridden through the environment:

    ANTEROOM_ASC_KEY_ID, ANTEROOM_ASC_ISSUER_ID, ANTEROOM_ASC_KEY_PATH
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

KEY_ID = os.environ.get("ANTEROOM_ASC_KEY_ID", "2KBJBJA8GV")
ISSUER_ID = os.environ.get("ANTEROOM_ASC_ISSUER_ID", "14ce111d-36f7-4419-abda-22672f874c7b")
KEY_PATH = os.environ.get(
    "ANTEROOM_ASC_KEY_PATH",
    os.path.expanduser(f"~/.appstoreconnect/private_keys/AuthKey_{KEY_ID}.p8"),
)
BUNDLE_ID = os.environ.get("ANTEROOM_BUNDLE_ID", "com.zayedmd.anteroom")
API = "https://api.appstoreconnect.apple.com/v1"

# Nothing further will happen on its own once the build reaches one of these.
TERMINAL = {"APPROVED", "REJECTED"}

# Processing has to finish before Beta Review can even start, so a build with
# no review submission yet is usually just still being processed.
PROCESSING_DONE = {"PROCESSED", "VALID", "READY_FOR_BETA_TESTING", "BETA_TESTING"}


def token() -> str:
    """A short-lived ES256 JWT. Apple rejects anything over 20 minutes."""
    try:
        import jwt  # PyJWT
    except ImportError:
        sys.exit(
            "PyJWT is required: python3 -m pip install pyjwt cryptography\n"
            "(cryptography supplies the ES256 backend; PyJWT alone will not sign.)"
        )
    try:
        secret = open(KEY_PATH).read()
    except OSError as e:
        sys.exit(f"Cannot read the App Store Connect key at {KEY_PATH}: {e}")

    now = int(time.time())
    return jwt.encode(
        {"iss": ISSUER_ID, "iat": now, "exp": now + 15 * 60, "aud": "appstoreconnect-v1"},
        secret,
        algorithm="ES256",
        headers={"kid": KEY_ID, "typ": "JWT"},
    )


def api(path: str, params: dict | None = None) -> dict:
    url = f"{API}/{path}"
    if params:
        url += "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"Authorization": "Bearer " + token()})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        detail = body
        try:
            errors = json.loads(body).get("errors", [])
            if errors:
                detail = "; ".join(
                    f"{x.get('title')}: {x.get('detail')}" for x in errors
                )
        except Exception:
            pass
        sys.exit(f"App Store Connect returned {e.code}\n  {detail[:400]}")


def app_id() -> str:
    data = api("apps", {"filter[bundleId]": BUNDLE_ID, "limit": 1})
    items = data.get("data") or []
    if not items:
        sys.exit(f"No app found for bundle id {BUNDLE_ID}. Check the key's app access.")
    return items[0]["id"]


def latest_build(app: str, wanted: str | None) -> dict | None:
    """One build, plus the two related records that carry the states."""
    params = {
        "filter[app]": app,
        "sort": "-version",
        "limit": 20,
        "include": "buildBetaDetail,betaAppReviewSubmission",
        # The two relationship names have to be listed in `fields[builds]`
        # alongside the attributes. A sparse fieldset that names only
        # attributes makes Apple omit the `relationships` object altogether,
        # and then `include` returns the records while nothing points at them
        # — which reads exactly like "this build has no review submission",
        # the one answer this script must never get wrong.
        "fields[builds]": (
            "version,uploadedDate,processingState,expired,"
            "buildBetaDetail,betaAppReviewSubmission"
        ),
        "fields[buildBetaDetails]": "externalBuildState,internalBuildState",
        "fields[betaAppReviewSubmissions]": "betaReviewState,submittedDate",
    }
    data = api("builds", params)
    included = {(i["type"], i["id"]): i for i in data.get("included", [])}

    for build in data.get("data") or []:
        if wanted and build["attributes"].get("version") != wanted:
            continue

        def related(name, kind):
            ref = ((build.get("relationships") or {}).get(name) or {}).get("data")
            return included.get((kind, ref["id"]), {}) if ref else {}

        detail = related("buildBetaDetail", "buildBetaDetails").get("attributes", {})
        review = related("betaAppReviewSubmission", "betaAppReviewSubmissions").get(
            "attributes", {}
        )
        return {
            "version": build["attributes"].get("version"),
            "uploaded": build["attributes"].get("uploadedDate"),
            "processing": build["attributes"].get("processingState"),
            "expired": build["attributes"].get("expired"),
            "external": detail.get("externalBuildState"),
            "internal": detail.get("internalBuildState"),
            "review": review.get("betaReviewState"),
            "submitted": review.get("submittedDate"),
        }
    return None


def notify(title: str, message: str) -> None:
    """Terminal bell plus a macOS notification, so an unwatched run still lands."""
    sys.stdout.write("\a")
    sys.stdout.flush()
    if sys.platform == "darwin":
        script = (
            f'display notification {json.dumps(message)} with title {json.dumps(title)}'
        )
        subprocess.run(["osascript", "-e", script], capture_output=True, check=False)


def line(b: dict) -> str:
    return (
        f"build {b['version']}  processing={b['processing']}  "
        f"external={b['external']}  review={b['review'] or '—'}"
    )


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--build", help="build number, e.g. 2 (default: newest)")
    ap.add_argument("--once", action="store_true", help="check once and exit")
    ap.add_argument("--interval", type=int, default=300, help="seconds between polls")
    args = ap.parse_args()

    app = app_id()
    print(f"app {app}  bundle {BUNDLE_ID}  polling every {args.interval}s\n")

    previous = None
    while True:
        build = latest_build(app, args.build)
        if build is None:
            print(f"No build {args.build} found yet.")
            if args.once:
                return 0
            time.sleep(args.interval)
            continue

        stamp = time.strftime("%H:%M:%S")
        state = (build["processing"], build["external"], build["review"])

        if state != previous:
            print(f"{stamp}  {line(build)}")
            if previous is not None:
                notify("Anteroom TestFlight", line(build))
        else:
            print(f"{stamp}  no change", end="\r", flush=True)
        previous = state

        review = build["review"]
        if review in TERMINAL:
            print()
            if review == "APPROVED":
                notify("Anteroom approved", f"Build {build['version']} passed Beta App Review.")
                print(f"Build {build['version']} is APPROVED — external testers can install it.")
                return 0
            notify("Anteroom rejected", f"Build {build['version']} was rejected.")
            print(
                f"Build {build['version']} was REJECTED. The reason is in App Store "
                "Connect under TestFlight → the build → Test Information."
            )
            return 1

        if args.once:
            if review is None and build["processing"] not in PROCESSING_DONE:
                print("  (still processing; Beta Review cannot start until it finishes)")
            return 0

        time.sleep(args.interval)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\nstopped")
        sys.exit(0)
