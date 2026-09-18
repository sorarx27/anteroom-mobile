# Pipeline operations

What runs on the server, what it costs, and the levers you have when
something goes wrong. Everything here is live in `anteroom-d2e72`,
`europe-west1`.

## The five functions

| Function | Model | Timeout | What it owns |
| --- | --- | --- | --- |
| `classifyDocType` | `gemini-2.5-flash` | 90s | `detected_doc_type`, `detected_confidence` |
| `generateBrief` | `gemini-2.5-pro` | 540s | `content`, `status`, `generated_at`, `source_language` |
| `translateBrief` | `gemini-2.5-pro` | 180s | `content_translations.{lang}`, `available_languages` |
| `renderBriefPdf` | none | 120s | nothing — returns bytes |
| `revenuecatWebhook` | none | 30s | `users/{uid}/billing/entitlement` |

Clients cannot write any of those fields. `firestore.rules` rejects them, and
the Admin SDK inside these functions is the only thing that may — which is
what makes "nothing invented" a property of the system rather than a promise
about the UI.

## Why Gemini 2.5 and not 3.x

`gemini-3.1-pro-preview` and `gemini-3.5-flash` are real Vertex publisher
models, but only on the `global` endpoint. `europe-west1` and `europe-west4`
both top out at 2.5:

```
curl -H "Authorization: Bearer $(gcloud auth print-access-token)" \
     -H "x-goog-user-project: anteroom-d2e72" \
     "https://europe-west1-aiplatform.googleapis.com/v1beta1/publishers/google/models?pageSize=300"
```

`global` routes the request to whichever region Google picks. These are
photographs of people's medical paperwork, so the residency is worth more
than the extra capability. To change your mind, edit `VERTEX_LOCATION` and
the three model constants in `functions/anteroom/config.py`.

There is no API key and no service-account JSON. Functions and Vertex are in
the same project, so the runtime service account
(`458778941992-compute@developer.gserviceaccount.com`) authenticates with
Application Default Credentials. It needs exactly one role:

```
gcloud projects add-iam-policy-binding anteroom-d2e72 \
  --member="serviceAccount:458778941992-compute@developer.gserviceaccount.com" \
  --role="roles/aiplatform.user"
```

## Levers

**Kill switch.** Set `config/runtime.ai_enabled` to `false` in Firestore and
every model call starts returning `UNAVAILABLE` within seconds. No deploy. A
missing document means enabled, so the healthy state needs no setup. This is
the thing to reach for if spend runs away or a model starts misbehaving mid-
demo.

**Daily quota.** `DAILY_MODEL_CALLS_PER_USER` in `config.py`, counted at
`users/{uid}/usage/{YYYY-MM-DD}.model_calls`. Charged transactionally *before*
the model call, so two devices on one account cannot both slip through. The
user can read their own counter; nobody can write it.

**Page cap.** `MAX_PAGES = 6`, enforced in three places that must agree:
`config.py`, `StoragePaths.MAX_PAGES`, and the `photos.size() <= 6` rule in
`firestore.rules`.

## Cost

A two-page brief measured on the real pipeline: ~6k input tokens (images
dominate — Gemini bills them in 768px tiles, which is why pages are
downscaled to 1536px long edge on both the client and the server) plus
reasoning and output. That is roughly **$0.02 per brief** end to end,
including classification. A thousand briefs is about $20 against the
$999.90 GenAI trial credit on billing account `01C037-D624BF-1F8009`.

`classifyDocType` runs Flash with `thinking_budget=0` — sorting a page into
four buckets does not need reasoning tokens, and they are the dominant cost
on Flash.

## RevenueCat webhook

Deployed at:

```
https://europe-west1-anteroom-d2e72.cloudfunctions.net/revenuecatWebhook
```

In the RevenueCat dashboard, under **Integrations → Webhooks**, set that as
the URL and paste the shared secret as the **Authorization header value**.
The secret lives in Secret Manager as `REVENUECAT_WEBHOOK_SECRET` and is never
in this repo; read it with

```
gcloud secrets versions access latest --secret=REVENUECAT_WEBHOOK_SECRET
```

and rotate it with `firebase functions:secrets:set REVENUECAT_WEBHOOK_SECRET`
followed by a redeploy of the function.

`app_user_id` must be the Firebase uid. It already is: `AnteroomApp` calls
`revenueCatService.initialize(key, user.user_id)`.

Two event types are handled by *not* acting on them. `CANCELLATION` means
auto-renew was switched off, not that access ended — the user keeps Pro until
`expires_at`. `TRANSFER` carries `transferred_from`/`transferred_to` rather
than one `app_user_id`, so there is no single uid to write and it is skipped
rather than guessed at.

## Verifying

Three suites, all runnable against the live project:

```
python3 tools/verify_rules.py                      # 20 checks, security rules
python3 tools/verify_pipeline.py                   # 38 checks, end to end
functions/venv/bin/python functions/tests/test_pipeline.py   # 25 checks, offline
```

`verify_pipeline.py` needs `REVENUECAT_WEBHOOK_SECRET` in the environment for
the entitlement half; without it those checks are skipped rather than failed.

It uploads `tools/sample_page_illegible.jpg`, a prescription whose Warfarin
dose has been blurred and blotted, and asserts the dose is **absent** from
`medications` and **present** in `flagged_items`. That is the safety claim
tested rather than asserted: a model that guesses a plausible dose passes any
test that only checks the readable rows.

## Gotcha: the Android emulator loses DNS

It presents as Firestore `UNAVAILABLE` / `UnknownHostException` and looks like
a backend fault. It isn't. macOS leaves `/etc/resolv.conf` without
nameservers, so the emulator's DNS proxy has no upstream, and some networks
block outbound DNS to `8.8.8.8`. Boot with the resolver the host actually
uses:

```
scutil --dns | grep nameserver          # find the host's resolver
emulator -avd <name> -dns-server <that address>
```
