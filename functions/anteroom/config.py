"""Shared configuration for the Anteroom Cloud Functions."""

from __future__ import annotations

# Firestore for anteroom-d2e72 is `eur3` (European multi-region). Functions and
# Vertex AI both pin to europe-west1 so clinical data stays in the EU and every
# Firestore call inside a function is a local hop rather than a transatlantic
# one. A region mismatch between here and the client's
# `Firebase.functions(region = ...)` surfaces as a confusing NOT_FOUND, so this
# constant is the single source of truth for both sides.
REGION = "europe-west1"
VERTEX_LOCATION = "europe-west1"

PROJECT_ID = "anteroom-d2e72"
STORAGE_BUCKET = "anteroom-d2e72.firebasestorage.app"

# Pages sent to the extraction model. The old FastAPI build capped at 10; 6
# keeps p95 latency inside the client's callable timeout and halves the
# worst-case model spend, and no realistic referral packet in the demo is
# longer.
MAX_PAGES = 6

# Long edge, in pixels, that page images are downscaled to before going to the
# model. Gemini bills images in 768px tiles, so an A4 page at 1536px costs 4
# tiles instead of 24 — a 6x saving — and ~135 DPI across A4 stays comfortably
# legible for printed clinical text.
IMAGE_LONG_EDGE = 1536
