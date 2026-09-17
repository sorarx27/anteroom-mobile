"""Anteroom Cloud Functions entrypoint.

Firebase only reads this module, so every deployed function must be imported
here. Keep it thin: logic lives in `anteroom/`.

`initialize_app()` must run before importing anything that touches
`firestore.client()` at module scope, hence the import ordering and the noqa.
The storage bucket is passed explicitly because this project uses the newer
`.firebasestorage.app` domain while the Admin SDK still defaults to the legacy
`<project>.appspot.com`, which does not exist here.
"""

from __future__ import annotations

from firebase_admin import initialize_app
from firebase_functions import https_fn, options

from anteroom.config import REGION, STORAGE_BUCKET

initialize_app(options={"storageBucket": STORAGE_BUCKET})


@https_fn.on_call(
    region=REGION,
    memory=options.MemoryOption.MB_256,
    timeout_sec=30,
    max_instances=2,
)
def ping(req: https_fn.CallableRequest) -> dict:
    """Deployment canary.

    Exists to prove the whole chain — Python runtime, dependency install,
    region, auth context, and GitLive's callable serialization — before any
    real logic depends on it. Delete once generateBrief is live.
    """
    return {
        "ok": True,
        "region": REGION,
        "authenticated": req.auth is not None,
        "uid": req.auth.uid if req.auth else None,
    }
