---
layout: ../layouts/Doc.astro
lang: en
toc: true
page: tech
title: 'How CastBay works – CastBay'
description: 'How CastBay works: the three ways to cast, how your phone finds the TV, why AirPlay music is timed, reading the mirroring stats, and who can cast.'
heading: 'How CastBay works'
intro: 'What happens between your phone and the TV, and why CastBay behaves the way it does.'
---

## Three ways to cast, and what travels where

- **Screen mirroring** (Control Center → Screen Mirroring): the phone records its own screen, compresses it as video (H.264, or H.265 for 4K) and streams it over your Wi-Fi; the TV decodes it in hardware. Everything you see comes from the phone, so the phone has to stay on and nearby.
- **Casting from an app with AirPlay** (the AirPlay icon in YouTube, iQiyi, Apple Music…): for video, the phone only tells the TV *where* the video is. The TV fetches it straight from the internet, so you can lock the phone or use other apps. It also means the TV must reach the video itself: if your phone needs a proxy or VPN for YouTube, the TV does too. Music is different: the phone streams it to the TV in lossless ALAC.
- **An app's own cast button (DLNA)**, as in Bilibili, iQiyi, NetEase Cloud Music or QQ Music: also hands the TV an address to play from. It works from iPhones and Android phones alike. How good the picture gets is up to the app: Bilibili, for example, keeps 1080p for its own TV app and offers other devices 720p; screen mirroring has no such limit.

## How your phone finds the TV

CastBay announces itself on your local network the way an Apple TV does (Bonjour, also called mDNS), and your phone lists every receiver it hears. That only works when both are on the same network and allowed to see each other: guest networks, and routers with "AP isolation" turned on, keep devices apart. If the TV doesn't show up, that is almost always why.

## Why music starts about two seconds late, and why that's a good thing

An iPhone doesn't send music "as it plays". It sends each piece about two seconds ahead, stamped with the moment it should be heard, and keeps the TV's clock in step with its own. CastBay plays every piece at exactly that moment, the way an Apple TV or HomePod does. That short wait buys a lot:

- **Lyrics stay in sync**, because the phone's idea of "now" and what you hear on the TV are the same moment.
- **Pausing and resuming pick up where they left off**, with nothing skipped or repeated. (Playing music the instant it arrived would put the TV two seconds ahead of the phone, and every resume would replay those two seconds.)
- Wi-Fi hiccups are absorbed by the two seconds in hand, instead of being heard as stutters.

Apps pause differently, and CastBay follows each, as an Apple TV does. Apple Music stops the TV at once. NetEase Cloud Music just stops sending, so what the TV already has plays out for a couple of seconds; when you resume, it carries on without a gap.

AirPlay music is lossless (Apple's ALAC). Android has no ALAC decoder, so CastBay includes Apple's open-source one.

## Mirroring quality, and reading the stats

On a 4K TV with a hardware H.265 decoder, CastBay offers the phone H.265 up to 4K at up to 60 frames per second; otherwise H.264 up to 1080p. The phone chooses what it actually sends within that.

Turn on **Show stats** (Settings → Playback, or the quick menu while playing) to see what's going on. While mirroring, the phone also reports on itself:

- **Video**: what the TV receives and decodes (codec, resolution, frame rate, bitrate) and frames it dropped.
- **Sender**: frames the phone sends against its target (e.g. 60/60), how many its screen drew (fewer while nothing moves, which is normal), and frames it dropped.
- **Network**: round trip time, packet loss, and the bandwidth used against what the phone thinks the link can carry.

So a stutter can be traced: packet loss or a bandwidth close to its limit points to Wi-Fi; the phone sending fewer frames than its target points to the phone being busy; the TV dropping frames points to the TV.

## Who can cast to your TV

Settings → Connection → Casting verification offers four choices:

- **Not required**: anyone on your network can cast.
- **Confirm**: the first time a device casts, the TV asks whether to allow it. The device is turned away while the TV asks, so once you allow it, cast again. (The TV can't hold the phone waiting: the AirPlay core serves every connection in turn, and waiting would freeze whatever is already playing.)
- **PIN**: as on an Apple TV, a new device enters a four-digit PIN shown on the TV, once. The two then trust each other's keys, so it connects straight away after that, and a device that never entered the PIN can't pretend it did.
- **Password**: every device enters the same password.

Whichever you choose, every device that has cast is listed there, and any of them can be blocked. Removing them all makes every device new again; if any had paired with a PIN, the TV also takes a new identity, so phones pair again instead of failing to connect.

## The remote and the connection

- **Controlling the phone's music from the TV remote.** When a music app plays over AirPlay, the TV's play/pause, previous and next go back to the phone, over AirPlay's remote-control channel (DACP), and the phone carries them out, just as with an Apple TV remote. That channel has play, pause and track commands but no "jump to a time", which is why AirPlay music has no skip-10-seconds buttons: seek on the phone instead. (DLNA music plays on the TV itself, so it can skip exactly.)
- **When a second device casts.** Settings → Connection → "When another device casts" decides: *Refuse it* (the default) keeps the current cast and turns the newcomer away; *Let it take over* ends the current cast and shows the new device's, name and all.
- **Why a cast ends by itself.** While mirroring or playing music, the phone sends a heartbeat every two seconds, even when paused. If none arrives for five seconds (the phone left the Wi-Fi, ran out of battery, or its app was closed), the TV takes the cast as over and goes back to its home screen rather than freezing on the last picture. Videos cast from apps play on the TV by themselves, so they don't need one.

## What isn't supported, and why

- **Google Cast (Chromecast)**: only Google-certified devices can receive it.
- **Miracast and Android's built-in screen mirroring**: they need access to the TV's system that an app doesn't get. From Android phones, use an app's cast button (DLNA).
- **The Mac's Music app**: it protects its stream with a kind of FairPlay encryption that the open-source AirPlay implementation can't handle. Screen mirroring from a Mac works.

## Built on open source

CastBay is open source under GPL-3.0. It builds on [UxPlay](https://github.com/FDH2/UxPlay) for the AirPlay protocol, [libplist](https://github.com/libimobiledevice/libplist), [Apple's ALAC decoder](https://github.com/macosforge/alac) and [Media3 ExoPlayer](https://developer.android.com/media/media3), with Android's hardware video decoding. The full source is on [GitHub](https://github.com/weenas/castbay).
