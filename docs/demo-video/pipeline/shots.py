"""The edit list. Mirrors docs/demo-video/storyboard.xml shot for shot."""

SP = "/private/tmp/claude-501/-Users-ahmedzayed-Downloads-Revenue-cat/007fafd3-1d22-41df-828e-b880f8d82ca6/scratchpad"
FOOT = SP + "/footage"

# kind: "static" (full frame png) | "video" (screen recording behind overlay)
#       | "hold"  (video that freezes on its last frame to fill the duration)
SHOTS = [
    dict(id="problem-1", dur=5, kind="static", img="st_problem-1.png",
         cap="In Spain the appointment is ten minutes and the paperwork is a folder."),
    dict(id="problem-2", dur=4, kind="static", img="st_problem-2.png",
         cap="Some of it is unreadable. A guessed dose is worse than none."),

    dict(id="hero-1", dur=5, kind="hold", src=f"{FOOT}/take1_cfr.mp4", ss=0.5, take=2.8,
         eyebrow="Anteroom",
         head="The waiting room does the paperwork",
         sub="A real account on a real device, signed into the live project.",
         cap="Anteroom turns that folder into one structured brief before the visit starts."),

    dict(id="capture-1", dur=7, kind="video", src=f"{FOOT}/take1_cfr.mp4", ss=12.0,
         eyebrow="Step 1",
         head="Photograph the paperwork",
         sub="Or import it straight from the photo library.",
         cap="Photograph the paperwork, or pick it from the library."),

    dict(id="capture-2", dur=6, kind="video", src=f"{FOOT}/take1_cfr.mp4", ss=27.0,
         eyebrow="Step 1",
         head="Two pages, uploaded to your account",
         sub="Firebase Storage, scoped to you by security rules.",
         cap="They upload to your account, and you can see exactly what you captured."),

    dict(id="classify-1", dur=7, kind="video", src=f"{FOOT}/take1_cfr.mp4", ss=74.0,
         eyebrow="Step 2",
         head="Anteroom types the document",
         sub="Gemini 2.5 Flash. One tap to override it.",
         cap="Gemini Flash types the document. You can override it in one tap."),

    dict(id="generate-1", dur=5, kind="video", src=f"{FOOT}/take2_cfr.mp4", ss=3.0,
         eyebrow="Step 3",
         head="One structured brief",
         sub="Gemini 2.5 Pro reads both pages — in europe-west1.",
         cap="Generate. Gemini 2.5 Pro reads both pages, in the EU."),

    dict(id="generate-2", dur=5, kind="video", src=f"{FOOT}/take2_cfr.mp4", ss=19.0,
         eyebrow="Step 3", badge="~18 s of model time, trimmed",
         head="One structured brief",
         sub="A real model call, not a canned response.",
         cap="Eighteen seconds later, the brief is there."),

    dict(id="flag-1", dur=7, kind="video", src=f"{FOOT}/take2_cfr.mp4", ss=30.0,
         eyebrow="The part that matters",
         head="It flags what it cannot read",
         sub="Seven medications found. The eighth was not guessed.",
         cap="It found seven medications. And it refused to guess the eighth."),

    dict(id="flag-2", dur=6, kind="video", src=f"{FOOT}/take2_cfr.mp4", ss=45.0,
         eyebrow="The part that matters",
         head="No dose. Not a guessed dose.",
         sub="Every other drug carries its dose verbatim.",
         cap="Every other drug has its dose, verbatim. Warfarin has none, because the page did not."),

    dict(id="flag-3", dur=5, kind="video", src=f"{FOOT}/take3_cfr.mp4", ss=6.0,
         eyebrow="Provenance",
         head="Every line traces back to the page",
         sub="The original photograph is one tap away.",
         cap="And the original page is one tap away, so any line can be checked."),

    dict(id="export-1", dur=6, kind="video", src=f"{FOOT}/take3_cfr.mp4", ss=17.5,
         eyebrow="Step 4",
         head="Hand your doctor a PDF",
         sub="Rendered on the server. The free tier is watermarked.",
         cap="Export a PDF for the clinic. On the free tier the server watermarks it."),

    dict(id="export-2",  dur=5, kind="static", img="st_export-2.png",
         cap="Pro removes the watermark and renders it in Spanish. Doses are never translated."),
    dict(id="billing-1", dur=5, kind="static", img="st_billing-1.png",
         cap="RevenueCat's Kotlin Multiplatform SDK — one implementation, two stores."),
    dict(id="billing-2", dur=5, kind="static", img="st_billing-2.png",
         cap="A RevenueCat webhook writes the entitlement. The client cannot unlock itself."),
    dict(id="cta-1",     dur=8, kind="static", img="st_cta-1.png",
         cap="Android, iOS, desktop and web — from one Kotlin codebase."),
]

TOTAL = sum(s["dur"] for s in SHOTS)
