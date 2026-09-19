#!/usr/bin/env python3
"""Turn on the public TestFlight link for the external tester group.

    python3 tools/enable_public_testflight.py --dry-run   # show what would change
    python3 tools/enable_public_testflight.py             # enable it
    python3 tools/enable_public_testflight.py --limit 500 # cap the tester count

A public link is a URL anyone can use to install the beta, so this refuses to
run until a build has actually passed Beta App Review. Publishing a link that
sends people to a build Apple has not approved is worse than not having one:
they get an error page, and the link is already out of your hands.

Authentication comes from monitor_beta_review, which reads the key from
~/.appstoreconnect/private_keys and never from this repository.
"""

from __future__ import annotations

import argparse
import sys
import time

sys.path.insert(0, __file__.rsplit("/", 1)[0])
import monitor_beta_review as asc  # noqa: E402


def external_group(app: str) -> dict:
    data = asc.api("betaGroups", {"filter[app]": app, "limit": 50})
    groups = [g for g in data.get("data", []) if not g["attributes"].get("isInternalGroup")]
    if not groups:
        sys.exit("No external beta group exists. Create one in App Store Connect first.")
    if len(groups) > 1:
        names = ", ".join(f"{g['attributes'].get('name')} ({g['id']})" for g in groups)
        sys.exit(f"More than one external group; pass --group.\n  {names}")
    return groups[0]


def approved_build(app: str) -> dict | None:
    """The newest build that Beta App Review has actually approved."""
    for version in (None,):  # newest first; latest_build already sorts
        build = asc.latest_build(app, version)
        if build and build.get("review") == "APPROVED":
            return build
    return None


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--group", help="beta group id (default: the only external one)")
    ap.add_argument("--limit", type=int, help="cap the number of testers via the link")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--force", action="store_true",
                    help="enable even though no build is approved yet")
    args = ap.parse_args()

    app = asc.app_id()
    group = (
        {"id": args.group, "attributes": {}}
        if args.group
        else external_group(app)
    )
    gid = group["id"]
    attrs = group.get("attributes", {})

    build = asc.latest_build(app, None)
    state = (build or {}).get("review")
    print(f"app {app}  group {attrs.get('name') or gid}")
    print(f"newest build {(build or {}).get('version')}  beta review: {state or '—'}")

    if attrs.get("publicLinkEnabled") and attrs.get("publicLink"):
        print(f"\nAlready enabled: {attrs['publicLink']}")
        return 0

    if state != "APPROVED" and not args.force:
        print(
            f"\nRefusing: the newest build is {state or 'not submitted'}, not APPROVED.\n"
            "A public link to an unapproved build shows testers an error page.\n"
            "Re-run once monitor_beta_review.py reports APPROVED, or pass --force."
        )
        return 1

    changes: dict = {"publicLinkEnabled": True}
    if args.limit:
        changes["publicLinkLimitEnabled"] = True
        changes["publicLinkLimit"] = args.limit

    if args.dry_run:
        print(f"\nWould PATCH /v1/betaGroups/{gid} with {changes}")
        return 0

    asc.api(
        f"betaGroups/{gid}",
        method="PATCH",
        body={"data": {"type": "betaGroups", "id": gid, "attributes": changes}},
    )

    # Apple populates `publicLink` a moment after the flag flips.
    for _ in range(10):
        time.sleep(2)
        fresh = asc.api(f"betaGroups/{gid}").get("data", {}).get("attributes", {})
        link = fresh.get("publicLink")
        if link:
            print(f"\nPublic TestFlight link: {link}")
            print("Add it to README.md and docs/DEVPOST_SUBMISSION.md.")
            return 0

    print("\nEnabled, but the link has not appeared yet. Re-run in a minute.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
