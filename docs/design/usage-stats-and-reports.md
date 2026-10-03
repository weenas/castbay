# Usage statistics and problem reports

Status: problem reports (manual upload) are built; automatic crash reports and usage
statistics are planned and not in the app yet. The privacy policy describes only what the
released app does; each part's section is added to it when that part ships.

## Principles

- Nothing is sent unless the person chooses to. Both switches below are off by default, and
  CastBay never asks to turn them on outside Settings.
- Nothing that identifies the person or what they watched: no account, no device name, no
  sender names, no titles or links, no Wi-Fi name, no location, no hardware addresses.
- What is sent is described on the privacy page, field by field, and visible in this
  repository (the app's code and `web/worker/index.js`, which receives it).
- Our own Cloudflare Worker on castbay.weenas.com receives it; no third-party analytics SDK.

## Settings → General → Help improve CastBay

| Switch | Default | What it does |
| --- | --- | --- |
| Send anonymous usage statistics | Off | Once a day, a summary of the day (below) |
| Send error reports automatically | Off | After a crash or "not responding", a problem report at the next start |

Turning a switch off stops sending at once. "Delete my data" asks the website to delete what it
holds for this installation's ID.

## 1. Problem reports (built)

About → Diagnostics → Upload log, confirmed on a screen that says what is sent. Contents,
scrubbing, storage (R2, 90 days, no public access) and the report ID are as in the privacy
policy's "Problem reports" section and `app/.../util/LogReport.kt`.

## 2. Automatic error reports (planned)

- Detected at the next start from Android's record of the last exit (ApplicationExitInfo,
  Android 11+: crash, native crash, not responding), or from CastBay's own record of an
  uncaught exception (all versions).
- With the switch on: uploaded as a problem report marked "crash", including the stack trace.
- With it off: the home screen shows a quiet note, "CastBay stopped unexpectedly last time.
  Send a report?", with Send and Dismiss; nothing is sent without Send.

## 3. Anonymous usage statistics (planned)

### Sent (once a day, one small JSON request)

| Field | Example | Why |
| --- | --- | --- |
| Installation ID | random UUID | Count devices without knowing whose; made when the switch is turned on, deleted when it is turned off |
| App version | 1.1.0 | Which versions are in use |
| Android version | 12 (API 32) | Which versions to support |
| Device maker and model | Xiaomi MiTV4 | Which devices to test |
| Screen class and kind | 1080p, TV / car / tablet, touch | Layout decisions |
| System language | zh | Translations |
| Casts that day, by protocol and kind | AirPlay mirroring 3, DLNA video 1 | What people use |
| Cast length, in bands | under 1 min / 1–10 min / over 10 min | Not exact times |
| Mirroring resolution and codec | 1080p H.264, hardware decoder | Decoder support |
| Failures, by kind | connection failed 1, decoder error 0 | Where it breaks |
| Settings in use | verification: PIN; lyrics on | Which options matter |

### Never sent

Device or sender names, titles, artists, links, IP addresses (the Worker does not store the
request's address), Wi-Fi names, MAC addresses, location, exact times of casts, anything
typed (passwords, PINs).

### Storage

Cloudflare D1 (SQL) on the same Worker: one row per installation per day; rows older than
a year deleted. Read only by the developer (`wrangler d1 execute`).
