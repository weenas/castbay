---
layout: ../layouts/Doc.astro
lang: en
page: compatibility
title: 'Compatibility – CastBay'
description: 'Which TVs, phones and apps CastBay has been tested with, and how well each works.'
heading: 'Compatibility'
intro: 'What CastBay has been tested with so far. Tried it on something not listed, or found something that doesn''t work? <a href="/feedback">Tell us</a>.'
---

## TVs and car displays

| Device | Android | Status |
| --- | --- | --- |
| Sony BRAVIA XR-55X90L (4K) | 12 | ✅ Tested: H.265 mirroring in 4K, everything else |
| TCL TV | 9 | ✅ Tested |
| BYD car display, DiLink 5.0 (touch screen) | 12 | ✅ Tested: mirroring, music; steering-wheel buttons to be confirmed; force-stopped when switched off, so open CastBay after starting the car |

CastBay needs an Android device on Android 8.0 or later. Other brands of TVs, TV boxes, car displays and tablets should work, with a remote or a touch screen; 4K H.265 mirroring needs a TV with a hardware HEVC decoder (the home screen says what it offers).

## Phones and computers

| Device | System | Status |
| --- | --- | --- |
| iPhone 17 Pro Max | iOS 26 | ✅ Tested: mirroring, music, video, PIN pairing |
| Samsung Galaxy (Android phone) | Android 16 | ✅ Tested: DLNA from NetEase Cloud Music |
| iPad | iPadOS | ❔ Not yet confirmed; uses the same AirPlay as an iPhone |
| MacBook: screen mirroring | macOS | ✅ Tested: mirroring in 1080p and 4K; for latency see the [FAQ](/faq) |
| Mac: the Music app | macOS | ❌ Not supported: it uses an encryption the open-source AirPlay library can't handle |

## Apps

| App | How | Status |
| --- | --- | --- |
| Apple Music | AirPlay | ✅ Lyrics in sync; pausing and resuming pick up where they left off |
| NetEase Cloud Music (iPhone) | AirPlay | ✅ Works; after a pause from the TV, what the TV has plays out for a couple of seconds |
| NetEase Cloud Music (Android) | DLNA | ⚠️ Plays, with titles and cover; the phone's play state isn't always in step |
| YouTube (iPhone) | AirPlay | ✅ Up to 4K on 4K TVs; the TV must be able to reach YouTube itself |
| YouTube (Android) | — | ❌ Only casts with Google Cast, which CastBay can't receive |
| iQiyi | AirPlay, screen mirroring | ✅ Works |
| Bilibili | DLNA | ⚠️ Works, up to 720p (Bilibili's limit for other devices) |
| QQ Music | DLNA | ❔ Not yet confirmed |
| Any app | Screen mirroring | ✅ Whatever is on the phone's screen |

## Not supported

- **Google Cast (Chromecast)**: only Google-certified devices can receive it.
- **Miracast and Android's own screen mirroring**: they need system access an app doesn't get.
