# App stores

The store listing (descriptions, icon, screenshots, changelogs) is in `fastlane/metadata/android/`, which F-Droid reads from this repository.

## F-Droid

F-Droid builds CastBay itself from a tagged commit, with `fdroid-com.weenas.castbay.yml` (this directory) as its build metadata, and signs it with F-Droid's key. Apps from F-Droid and from GitHub/the website therefore have different signatures: one can't update the other (uninstall first).

What the metadata does:

- `submodules: true` fetches UxPlay, libplist, ALAC and OpenSSL (F-Droid's checkout also pulls OpenSSL's own submodules).
- `init` deletes what the build doesn't use and F-Droid's scanner would flag: libplist's fuzzing test cases, OpenSSL's test data and OpenSSL's own submodules (fuzzing corpora, other projects). Deleting them before the scan, rather than with `scandelete`, keeps the log short: `scandelete` logs every file, and OpenSSL's corpora alone overflowed GitLab's 4 MB job log.
- `ndk` pins the NDK the app uses; `sudo` installs perl and make for `tools/build-openssl.sh`, which Gradle runs.
- `gradleprops: castbay.selfUpdate=false` builds without the in-app updater and update check: F-Droid updates the app.
- `commit` is the release's full commit hash (F-Droid's rule, not the tag). `UpdateCheckMode: Tags` and `AutoUpdateMode: Version` let F-Droid pick up each new `vX.Y.Z` tag.

To submit (once, from a GitLab account): fork https://gitlab.com/fdroid/fdroiddata (the fork must be public, so its group must be public too), add the file as `metadata/com.weenas.castbay.yml`, and open a merge request; F-Droid's CI builds it and reviewers reply in the merge request. Check it first with fdroidserver: `fdroid rewritemeta com.weenas.castbay` (formatting), `fdroid lint com.weenas.castbay`, and `fdroid build -v -l com.weenas.castbay` where a build environment is available.

## IzzyOnDroid (declined)

Requested in October 2026 (https://codeberg.org/IzzyOnDroid/repodata/issues/663) and declined: IzzyOnDroid doesn't accept apps developed with substantial LLM assistance, which CastBay is. The maintainer noted that `REQUEST_INSTALL_PACKAGES` (the in-app updater, which bypasses their scans) could also have been a blocker; a build with `castbay.selfUpdate=false` has neither the updater nor that permission. Their courtesy scan of the 1.3.2 APK found no offending libraries or signing blocks.
