# Security policy

CastBay listens on the local network (AirPlay on port 7000, DLNA over HTTP and SSDP) and keeps pairing keys and a casting password on the device, so security issues matter. Thank you for reporting them responsibly.

## Supported versions

Security fixes go into the latest release; please check that the issue exists there ([releases](https://github.com/weenas/castbay/releases)).

## Reporting a vulnerability

Please **don't open a public issue**. Report it privately through GitHub: [Report a vulnerability](https://github.com/weenas/castbay/security/advisories/new). Include what an attacker can do, the steps or a proof of concept, and the CastBay version and device.

You can expect an acknowledgement within a few days. Once fixed, the fix is released and the advisory published, crediting you unless you'd rather not be named. This is a volunteer project with no bug bounty.

## In scope

- The AirPlay and DLNA receivers and what they accept from the network (pairing, PIN and password checks, parsing, playback of URLs from senders).
- Data on the device (settings, pairing keys) and the in-app updater (download and checksum).
- The website's API (`castbay.weenas.com/api/*`): problem reports and usage statistics.

Out of scope: denial of service from a device that is already allowed to cast, and issues in third-party code that are already known upstream (please report those to the project concerned: UxPlay, libplist, Media3).
