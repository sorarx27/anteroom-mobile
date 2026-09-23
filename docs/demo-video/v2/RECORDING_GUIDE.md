# Recording guide: Anteroom demo v2

About 45 minutes on an iPhone with the TestFlight build. You record 10 short
screen clips and 14 short voice lines. `edit.py` does the editing.

Watch `anteroom-demo-v2-animatic.mp4` first. Every yellow **STAND-IN** tag
in it is a clip you replace, and the robot voice is the timing you read
against.

Why v2 exists: the Devpost rules want the app shown *on its target device*,
and iOS is the only platform where purchases work. v1 was mostly Android
footage and never showed a purchase. v2 is all iPhone, and its centrepiece is
a real StoreKit purchase unlocking Pro.

## Before you start (5 min)

1. **Update Anteroom in TestFlight** to the newest build.
2. **Focus → Do Not Disturb on.** A notification in a clip ruins it.
3. Charge to 100% if you can, since the battery icon is in every shot. Use
   Wi-Fi.
4. Add **Screen Recording** to Control Center (Settings → Control Center).
   Microphone off.
5. **Save the two attached sample pages to Photos**: `sample_page.jpg`, a
   referral, and `sample_page_illegible.jpg`, a prescription with the
   Warfarin dose blotted out on purpose. Both show a fictional TEST PATIENT.
6. **Create a fresh account in the app.** Use a made-up name such as
   *Alex Demo* and an email you control. **Do not use `test1@anteroom.dev`**:
   buying Pro on that account would unlock it for every judge, and they would
   never see the paywall. No real names or health details anywhere on screen.

TestFlight purchases run in Apple's sandbox and are **never charged**. When
the Apple sheet appears, it says so.

## The clips

Record each clip separately: start recording, wait one second, do the action
slowly, hold still for a second, stop. Slower than feels natural is right.
Anything too long gets trimmed; anything too short freezes on its last frame.

Record them **in this order**. Everything free has to be filmed before you
buy Pro.

| Clip | Length | What to film |
| --- | --- | --- |
| **G2** | 7 s | Dashboard → start a new brief → **choose from library** → pick both sample pages → wait until both thumbnails finish uploading. |
| **G3** | 6 s | On the review screen, the detected document type is shown. Tap a different type, then tap back to the correct one. |
| **G4** | 5 s+ | Tap **Generate brief**. Keep recording through the wait (about 15 s) until the brief appears. Only the first 5 s are used, labelled "model time trimmed". |
| **G5** | 8 s | On the finished brief, scroll so **"Flagged for clinical review — Warfarin dose unreadable on page 2"** sits in the middle of the screen. Start recording and **don't touch the screen** for 8 s. This is the key shot. |
| **G6** | 6 s | Scroll slowly down to the **medication list** and stop where Warfarin shows with **no dose** under it. |
| **G7** | 5 s | Tap the flagged item, or the page thumbnail, so the original photographed page opens. |
| **G8** | 6 s | Tap **Download PDF (watermarked)** and let the watermarked PDF show. |
| **G1** | 5 s | Back on the dashboard with the new brief in the list. Hold still. |
| **G9** | 10 s | **The purchase.** Tap **Upgrade** → paywall → **Monthly** → **Continue** → confirm in the Apple sandbox sheet → paywall closes, Pro unlocks. Hold 2 s on the unlocked screen. If the Apple sheet records as black, carry on. |
| **G10** | 6 s | Open the brief → tap **Español** → **Download PDF**. It should come out in Spanish with no watermark. |

AirDrop the clips to a Mac, or share them from Photos to Google Drive. Name
them `G1.mov` … `G10.mov`; any `.mov` or `.mp4` works.

## Three screenshots for Devpost

Devpost wants one screenshot at exactly **1179×2556 px, no device frame**.
The screenshots in `docs/assets/` are from before the pipeline went live:
they show canned sample data and a paywall that promised QR codes. Don't use
them.

Take these with side button + volume up while you record:

1. The brief with the **Warfarin flag** visible (during G5).
2. The **paywall** (during G9, before you buy).
3. The **dashboard showing PRO** (after G9).

iPhone 15, 15 Pro, 16 and 16e screenshots are natively 1179×2556, so send
them as they are. From any other model, run this on the Mac:

```bash
ffmpeg -i IMG_1234.PNG -vf "scale=1179:-1:flags=lanczos,crop=1179:2556:0:0" devpost-1.png
```

## The voice lines

Record each line as its own file with **Voice Memos**, in a quiet room with
the phone about 20 cm away. Read calmly, a little slower than conversation.
Each line has to fit its time or the end gets cut. Name the files `01` to
`14`, matching the numbers below.

| # | Max | Line |
| --- | --- | --- |
| 01 | 4.7 s | In Spain the appointment is ten minutes and the paperwork is a folder. |
| 02 | 3.7 s | Some of it is unreadable. A guessed dose is worse than none. |
| 03 | 4.7 s | Anteroom turns that folder into one structured brief. |
| 04 | 6.7 s | Pick the pages from your library, or photograph them. They upload to your account. |
| 05 | 5.7 s | Gemini Flash types each document. One tap overrides it. |
| 06 | 4.7 s | Gemini 2.5 Pro reads both pages, in the EU. |
| 07 | 7.7 s | It found seven medications. *(beat)* And it refused to guess the eighth. |
| 08 | 5.7 s | Every other drug has its dose, verbatim. Warfarin has none. |
| 09 | 4.7 s | Tap the flag, and the original page is right there. |
| 10 | 5.7 s | Export a PDF for the clinic. On the free tier, the server watermarks it. |
| 11 | 9.7 s | Anteroom Pro runs on RevenueCat. Buy it, and the app unlocks the moment the entitlement lands. |
| 12 | 5.7 s | Pro removes the watermark and renders the brief in Spanish. |
| 13 | 4.7 s | The server decides. The app cannot unlock itself. |
| 14 | 7.7 s | One Kotlin codebase. Live on iOS today. Try it on TestFlight. |

Line 07 is the payoff. Slow down and leave the pause. Don't ad-lib: the
burned-in captions show these exact words.

## Assembling (whoever has the Mac)

```bash
cd docs/demo-video/v2
# clips/G1.mov … clips/G10.mov   and   clips/vo/01.m4a … 14.m4a
python3 edit.py
```

Output: `build/anteroom-demo-v2.mp4`, 1920×1080, 86 s. The script lists any
clip still missing. If the magnified Warfarin callout in G5 shows the wrong
strip of the screen, change `callout=0.52` on the `flag` shot in `edit.py`
(0 is the top of the screen, 1 the bottom) and run it again.

Then upload to **YouTube as Unlisted or Public**. Devpost needs a YouTube or
Vimeo link, and the video must be under 2 minutes.
