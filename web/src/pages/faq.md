---
layout: ../layouts/Doc.astro
lang: en
page: faq
toc: true
faq: true
title: 'FAQ – CastBay'
description: 'CastBay FAQ: supported devices, installing, phones that don''t find the TV, casting and playback, privacy and security.'
heading: 'FAQ'
intro: 'Not answered here? See the <a href="/guide">user guide</a>, or <a href="/feedback">ask us</a>.'
---

## Getting started

### Which devices are supported?

Android devices on Android 6.0 or later: TVs, TV boxes, car displays and tablets, worked with a remote or a touch screen. Tested on Xiaomi (Android 6), TCL (Android 9) and Sony (Android 12) TVs, a Google TV Streamer and a BYD DiLink 5.0 car display (Android 12); see the [compatibility list](/compatibility).

### How do I install the APK on a TV?

Copy the APK to a USB drive, or open it with a file manager on the TV (ES File Explorer, for example). The first time, the TV may ask you to allow installing apps from unknown sources.

### Can I trust the APK I download?

Each release is built by GitHub from the tagged source, not on anyone's computer, and signed with CastBay's own key; the build checks the signature before publishing. The complete source of that exact version is attached to every release. Android also only installs an update over CastBay if it carries the same signature, so an altered copy can't replace the one you have.

## Connecting

### My phone doesn't find CastBay. What now?

Make sure the TV and phone are on the same Wi-Fi and CastBay is open, waiting for a connection. Some routers enable "AP isolation", which stops devices finding each other; turn it off in the router's settings. Turning the phone's Wi-Fi off and on can also help.

### Why can't YouTube on my Android phone find CastBay?

YouTube for Android only casts with Google Cast, which only Google-certified devices can receive. Use the YouTube app on the TV itself, or cast from an iPhone or iPad, whose YouTube app uses AirPlay. Apps with a DLNA cast button (Bilibili, iQiyi, NetEase Cloud Music and others) work from Android phones.

### Does it support Google Cast (Chromecast) or Miracast?

No. Only Google-certified devices can receive Google Cast, and Miracast needs system-level access an app doesn't get. CastBay supports AirPlay and DLNA.

### Can I send music from a Mac?

Mac screen mirroring works. Music from the Mac's Music app doesn't: it uses an encryption the open-source AirPlay library CastBay is built on can't handle.

## Casting and playback

### Why can't I drag the progress bar of AirPlay music?

Because AirPlay doesn't let the TV choose where music plays from. The phone plays the song itself and sends it to the TV a moment ahead, stamped with when each part is to be heard; the TV only plays what arrives. What the TV can send back is a short list of remote commands (play, pause, previous and next track), with no "go to a time" among them, which is also why an Apple TV's remote can't seek AirPlay music. So the bar shows where the song is, and you move it on the phone.

DLNA music is different: the TV fetches and plays the file itself, so its bar can be dragged, and so can the bar of any video cast from an app (AirPlay or DLNA).

### Why does music start about two seconds late?

To stay in step with the phone. An iPhone sends music about two seconds ahead, stamped with when each part should be heard, and CastBay plays it then, as an Apple TV does. That keeps lyrics in sync and lets pausing and resuming pick up where they left off. See [How it works](/tech).

### Screen mirroring lags a little. Is that normal?

Some delay is, as with an Apple TV: the sender has to capture and compress its screen before the TV can show it. Two things shorten it. First, switch the TV's picture mode to **Game**: TVs process the picture to make films look better, and on our Sony that alone took 160 ms. Second, if you mirror a Mac to a 4K TV, choose 1080p: a Mac encodes 1080p much faster than 4K. Set **Maximum Resolution** to 1080p in CastBay, or pick the resolution for CastBay in the Mac's System Settings → Displays while mirroring (the Mac remembers it). In our test (MacBook to a Sony 4K TV) mirroring lagged about 350 ms in the standard picture mode, 190 ms in Game mode, and under 100 ms in Game mode at 1080p. The stats overlay shows how much of it the TV's decoding takes (decode, usually 10–20 ms).

### A video won't play. Why?

Videos cast from apps are fetched by the TV itself. If your phone needs a proxy or VPN to open them (YouTube, for example), the TV needs one too. Screen mirroring isn't affected: the picture comes from the phone.

### Why is Bilibili limited to 720p?

Bilibili keeps 1080p and above for its own TV app; casting to other devices tops out at 720p. Use screen mirroring for 1080p.

### How do I stop a cast from the TV or car?

Press Back twice (the remote's Back key, or a car display's or tablet's Back button or gesture). Pressing Home keeps the cast playing in the background.

### Do I have to open CastBay each time I start the car?

On a BYD car display, yes. Switching the car off doesn't shut the display down: it goes to sleep and force-stops every third-party app (CastBay's Diagnostics shows "stop … due to quickboot"). Android doesn't let a force-stopped app hear about anything, or run anything in the background, until it is opened again, so CastBay can't come back by itself when the car starts. Put CastBay on the car's home screen or app bar and tap it once you're in; it then receives until the car is switched off.

On TVs, and on devices that don't force-stop apps, CastBay starts receiving by itself after booting or waking.

### Why did changing a setting stop my cast?

Connection and screen-mirroring settings (the device name, casting verification, resolution and so on) only take effect when the receiver restarts, so the device that was casting has to connect again. Playback settings, such as picture fit, stats and lyrics, apply at once without interrupting anything.

## Privacy and security

### Can I stop other people from casting to my TV?

Yes. In Settings → Connection → Casting verification, choose Confirm (the TV asks before a new device casts), PIN (a new device enters a PIN shown on the TV, once) or Password. Any device that has cast can be blocked there too.

### Does CastBay collect my data?

No. There's no account, analytics or ads. Only when you turn on lyrics does it send the song's title and artist to lrclib.net to look them up. See the [privacy policy](/privacy).
