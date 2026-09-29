---
layout: ../layouts/Doc.astro
lang: en
page: privacy
title: 'Privacy Policy – CastBay'
description: 'CastBay''s privacy policy: no account, no analytics, no ads. Lyrics lookups and the daily update check are optional.'
heading: 'Privacy Policy'
intro: 'Effective: September 30, 2026'
---

CastBay is an open-source casting receiver app for Android TV. It needs no account and contains no ads, analytics or tracking code.

## What we don't collect

CastBay does not collect, store or upload personal information, and does not send your usage to us or anyone else. We have no server to receive such data.

## What you cast

- AirPlay mirroring and music travel only between your phone and TV, on your local network.
- When an app casts a video or song (AirPlay video or DLNA), the TV fetches it directly from the address that app provides, just as if you watched it on the TV. The service providing the content is responsible for its data handling.
- CastBay announces its device name on your local network so your phone can find it.

## Lyrics (optional)

"Show lyrics" is off by default. When you turn it on, CastBay sends the song's title, artist, album and length to [lrclib.net](https://lrclib.net) to look up its lyrics. Nothing that identifies you is included, and that site's own policy applies to it. You can turn this off at any time in Settings or the quick menu while playing.

## Update check

Once a day, CastBay asks this website (castbay.weenas.com/latest.json), or GitHub (api.github.com) if the website can't be reached, for the number of the latest CastBay version, so it can tell you when a newer one is out. The request contains nothing about you or your TV; the server sees your network's address, as for any web page (the website is hosted by Cloudflare; GitHub's own policy applies to GitHub). You can turn this off in Settings, under General.

Only when you choose **Download and install** on the About screen does CastBay download the new version, from this website or GitHub, check that it is exactly the released file (its SHA-256), and open Android's installer, where you confirm the update. Nothing is downloaded or installed on its own.

## Settings and data on the TV

Your settings (such as the device name and casting password) stay on the TV and are never uploaded, as does the list of devices that have cast to it (their names and AirPlay device IDs, whether each is allowed, and the pairing keys of devices paired with a PIN). You can remove the devices in Settings; uninstalling CastBay deletes all of it.

## Permissions

- Network: to receive casts and play what apps cast.
- Location, requested only when you choose "Show Wi-Fi name": Android only lets apps with this permission read the Wi-Fi name. CastBay uses it to show the network's name on the home screen and never reads or uses your location.
- Start at boot and foreground service: so CastBay is ready when the TV turns on and keeps playing in the background.

## Children's privacy

CastBay collects no personal information from anyone, including children.

## Changes and contact

If this policy changes, we will update this page and its effective date. For questions, please open an issue on [GitHub](https://github.com/weenas/castbay/issues).
