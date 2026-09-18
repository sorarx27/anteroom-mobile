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

# ---------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------
# The old FastAPI build named `gemini-3.1-pro-preview` and `gemini-3.5-flash`.
# Those are real Vertex publisher models, but as of 2026-09-18 they are served
# only from the `global` endpoint: querying
# europe-west1-aiplatform.googleapis.com/v1beta1/publishers/google/models
# returns Gemini 2.5 and nothing newer, and europe-west4 is the same.
#
# Using `global` would route a photograph of someone's referral letter to
# whichever region Google picks, which is exactly the property we are trading
# a little model capability to keep. So: EU endpoint, Gemini 2.5.
#
# Switching is a two-line change -- set VERTEX_LOCATION to "global" and bump
# these -- if the residency requirement is ever relaxed.
EXTRACTION_MODEL = "gemini-2.5-pro"
CLASSIFIER_MODEL = "gemini-2.5-flash"
TRANSLATION_MODEL = "gemini-2.5-pro"

# Pages sent to the extraction model. The old FastAPI build capped at 10; 6
# keeps p95 latency inside the client's callable timeout and halves the
# worst-case model spend, and no realistic referral packet in the demo is
# longer.
MAX_PAGES = 6

# Long edge, in pixels, that page images are downscaled to before going to the
# model. Gemini bills images in 768px tiles, so an A4 page at 1536px costs 4
# tiles instead of 24 - a 6x saving - and ~135 DPI across A4 stays comfortably
# legible for printed clinical text.
IMAGE_LONG_EDGE = 1536

# JPEG quality used when re-encoding a downscaled page.
IMAGE_JPEG_QUALITY = 85

# ---------------------------------------------------------------------------
# Spend guardrails
# ---------------------------------------------------------------------------
# Model calls a single user may make per UTC day, counted before the call so a
# runaway client cannot spend the Vertex credit. `maxInstances` is a latency
# control, not a spend ceiling - this is the ceiling.
DAILY_MODEL_CALLS_PER_USER = 40

# Global off switch, read from Firestore `config/runtime.ai_enabled` on every
# model call. A missing document means enabled, so the normal state needs no
# setup; flipping the flag to false stops all spend within seconds without a
# deploy.
RUNTIME_CONFIG_DOC = "config/runtime"

# Entitlement written by the RevenueCat webhook and read before anything a
# paying user gets. Never written by a client - see firestore.rules.
ENTITLEMENT_DOC = "billing/entitlement"
ENTITLEMENT_ID = "anteroom_pro"

SUPPORTED_LANGUAGES = ("en", "es")
