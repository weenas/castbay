---
layout: ../layouts/Doc.astro
lang: en
toc: true
page: guide
title: 'User guide – CastBay'
description: 'How to use CastBay: installing and updating it, casting from iPhone, iPad, Mac and Android, the remote, touch screens and the quick menu, and every setting.'
heading: 'User guide'
intro: 'Everything about using CastBay, from installing it to each setting. For problems, see the <a href="/faq">FAQ</a>; for how it works, see <a href="/tech">How it works</a>.'
---

## What you need

- An Android device on Android 8.0 or later: a TV, a TV box, a car display or a tablet, worked with a remote or a touch screen.
- The TV and your phone on the same network, not a guest network. The TV can be wired and the phone on Wi-Fi, as long as both go through the same router.

## Install and update

1. Get the latest CastBay.apk from **castbay.weenas.com/apk** (the website's Download APK button), or any version from the [changelog](/changelog). Then put it on the TV in whichever way suits it:
   - **The TV's browser**: type `castbay.weenas.com/apk` in its address bar; the APK downloads, then open it.
   - **A USB drive**: copy the APK onto it, plug it into the TV, and open the APK with the TV's file manager (ES File Explorer, for example).
   - **From your phone**: apps such as Dangbei Assistant or Shafa Butler (当贝助手, 沙发管家) send an APK from the phone to the TV over the home network and install it.
2. The first time, the TV may ask you to allow installing apps from unknown sources; allow it.
3. Open CastBay from the TV's apps. It starts receiving right away, and is ready whenever the TV turns on from then on.

**Updates**: CastBay looks for a newer version once a day; to look now, press **Check for updates** on About. When a newer version is out, a red dot appears on the home screen's About button. Open About and press **Download and install**: CastBay downloads the new version, checks it, and opens Android's installer, where you confirm. The first time, the TV asks you to allow CastBay to install apps; allow it. Your settings are kept. (Or scan the QR code on About with your phone to download it yourself.)

## The home screen

- **Left**: the CastBay mark and the status. While it says "Waiting for a connection…", phones can find the TV. Below are Settings, Help and About.
- **The panel on the right**: the name phones see, the network and IP address, what mirroring it offers (e.g. H.265 · 2160p), whether DLNA is on, the casting verification, and whether a second device is refused or takes over.
- **Quitting**: press Back twice on the home screen. CastBay then stops receiving until you open it again.

## Casting from an iPhone or iPad

### Screen mirroring

1. Swipe down from the top-right corner to open Control Center and tap Screen Mirroring.
2. Choose CastBay's name (by default "CastBay" plus the TV's name; you can change it in Settings).
3. Your screen and sound appear on the TV. To stop, tap Stop Mirroring in Control Center, or press Back twice on the TV.

### Video from an app

In YouTube, iQiyi and other apps, tap the AirPlay icon (a rectangle with a triangle) on the player and choose CastBay. The TV plays the video straight from the internet, so you can lock the phone or use other apps.

### Music

In Apple Music, NetEase Cloud Music and other apps, tap the AirPlay icon there or on the music card in Control Center, and choose CastBay. Music starts about two seconds later: that keeps the TV in step with the phone, which is also what keeps lyrics in sync (see [How it works](/tech)).

## Casting from a Mac

Click Control Center in the menu bar → Screen Mirroring, and choose CastBay. Sending music from the Mac's Music app isn't supported.

## Casting from an Android phone

Use an app's own cast button (DLNA): in Bilibili, iQiyi, NetEase Cloud Music, QQ Music and similar apps, tap the cast button on the player (usually a TV icon) and choose CastBay. DLNA casting must be on in Settings (it is by default). These apps' buttons work from iPhones too.

## The first time a device connects

If casting verification is on in Settings, a new device casting for the first time:

- **Confirm**: is turned away while the TV asks "… wants to cast to this TV". Choose Allow, then cast again from the device; Block keeps it from casting.
- **PIN**: enters the four-digit PIN the TV shows in the box that appears on the phone, once; the device is remembered after that. OK or Back puts the PIN away, and the device gets a new one when it tries again.
- **Password**: enters the password from Settings. The phone remembers it until the password changes.

## While playing: remote and touch screen

- **Video cast from an app**: OK or Play pauses and resumes; Left and Right skip back and ahead 10 seconds. On a touch screen, tap it and pause or skip in the menu.
- **Music**: Left and Right move between the buttons, OK presses one; on a touch screen, tap them. For AirPlay music they are previous, play/pause and next, carried out by the phone; for DLNA music, back 10 seconds, play/pause and ahead 10 seconds. The remote's own play/pause key works too.
- **Screen mirroring**: the phone controls the picture; on the TV or car you only open the menu and stop the cast.
- **Down or Menu**: opens the quick menu along the bottom of the screen; Left and Right choose an option, OK changes it, Back or Down closes the menu. On a touch screen (a car's, for example), tap the screen to open or close it.
- **Media keys** (a remote's, or a car's steering-wheel buttons): play/pause, next and previous control the music, carried out by the phone.
- **Back twice**: stops the cast and goes back to the home screen.
- **Home**: goes to the TV's home screen; the cast keeps playing in the background.
- **Volume**: set it on the phone; the TV shows a volume bar in the corner for a moment.

## The quick menu

Press Down or Menu while playing, or tap a touch screen. It closes by itself after five seconds untouched. What it offers depends on what's playing:

- **Pause** and **Back** / **Ahead** 10 seconds (videos cast from apps), with where the video is.

- **Playback stats**: codec, resolution, frame rate, bitrate and more; while mirroring, also the phone's frame rate and network (see [How it works](/tech)).
- **Picture** (video and mirroring): Fit (all of it, perhaps with black bars), Fill (the whole screen, perhaps cropping the edges) or Stretch (the whole screen, perhaps distorted).
- **Lyrics** (music): on or off.
- **Audio** and **Subtitles** (videos cast from apps, when there is a choice): another language, or subtitles off.

## The music screen

The blurred album cover fills the background; in front are the cover, title, artist, album and progress, with the playback buttons below. With lyrics on, they scroll along with the song on the right, or it says there are none. The top left shows which device is casting.

## Settings

Choose Settings on the home screen, and move between the four tabs at the top with Left and Right. Text fields, such as the device name, open the keyboard only when you press OK.

### Connection

Changing these restarts the receiver; a device that was casting connects again.

- **Device name**: the name phones see; "CastBay" by default.
- **Add the TV's name after it**: adds the TV's model or name after it, so several TVs are told apart. The full name phones see is shown below.
- **DLNA casting (apps' cast button)**: whether apps' own cast buttons can cast here.
- **Casting verification**: Not required, Confirm, PIN or Password; see "The first time a device connects" above. Password needs at least 4 digits.
- **When another device casts**: while one device is casting, another is refused, or takes over (the current cast ends).
- **Devices that have cast**: each can be allowed or blocked; blocked devices can't cast. Remove all (press twice) makes every device new again.

### Screen mirroring

Only for mirroring a phone's screen, not apps' video and music. Changing these restarts the receiver.

- **Maximum Resolution**: Auto (2160p on a 4K TV with H.265, otherwise 1080p), 720p or 1080p.
- **Maximum Frame Rate**: Auto (60 fps) or 30 fps. On a weak network, 30 can be smoother.
- **Codec**: Auto (H.265 when the TV decodes it in hardware) or H.264 only. If H.265 mirroring looks wrong, try H.264 only.

### Playback

These apply at once, and can also be changed from the quick menu while casting.

- **Show playback stats**.
- **Show lyrics for music**: looks the song's title and artist up on lrclib.net; off by default.
- **Picture**: Fit, Fill or Stretch.

### General

- **Language**: System (the TV's), 中文 or English.
- **Check for Updates**: looks for a newer version once a day (on this website, or GitHub).
- **Restore Defaults**: press twice to put every setting back as it was when CastBay was installed.

## Help and About

Help lists how to cast in each way and what to do when something doesn't work, right on the TV. About shows the version, the website, privacy policy and source code, and a QR code that opens the website on your phone; when a newer version is out, it shows that version, and the QR code opens the download page instead.
