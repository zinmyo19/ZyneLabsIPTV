# ZyneLabs IPTV

A native Android IPTV player with an OTT-style interface. Plays M3U playlists
and Xtream Codes accounts through ExoPlayer (Media3) — direct stream
passthrough, no transcoding, no accounts or playlists bundled in the app.

## Features

- **Live TV / Movies / Series** browsing with tabs and an OTT-style home
- **M3U + Xtream Codes** account support (URL, local file, Xtream login)
- **Media Library** — movie files inside M3U playlists are auto-detected and
  browsed as genre folders, like a local media collection
- **Smart categories** — genre and country chips computed from group titles
- **Player**: ExoPlayer with Fit / Fill / Zoom, swipe brightness & volume,
  subtitle picker, stream-quality badge (SD / HD / FHD / 4K), adjustable
  buffer size, in-player channel browser, Picture-in-Picture
- **EPG** now/next where the provider supplies it
- Favorites, recently watched, per-account settings, JSON backup/restore

## Building

No Android Studio / Gradle required — a plain shell pipeline
(`aapt2 → javac → d8 → zipalign → apksigner`).

Prerequisites:

- JDK 17
- Android SDK with `build-tools;34.0.0` and `platforms;android-34`
  (expected at `~/android-sdk`, or edit `SDK=` in `build.sh`)
- Python 3 (for the helper scripts)

```bash
# 1. download the Media3 / AndroidX dependency closure into libs/
python3 resolve_deps.py

# 2. build the APK (output: ZyneLabsIPTV-<version>.apk)
bash build.sh
```

The APK is signed with a debug key and installs as an update over previous
builds. App data (accounts, favorites) is preserved on update.

## Project layout

```
app/src/main/
  AndroidManifest.xml
  java/com/zynelabs/iptv/
    data/   Channel, ChannelRepo, M3uParser, XtreamClient, Cats, Store, ...
    ui/     SetupActivity, HomeActivity, SectionsActivity, PlayerActivity,
            MediaLibraryActivity, EpgActivity, SettingsActivity, ...
  res/      values, drawable
build.sh          manual build pipeline
gen_lib_r.py      generates library R.java from the merged R.txt
resolve_deps.py   downloads AAR/JAR deps from Maven repositories
```

## Notes

- Playlists you import are copied into the app's private storage; nothing is
  uploaded anywhere.
- Buffering is almost always the provider's server or the network route —
  the player exposes an adjustable buffer (15–120 s) to ride out short stalls,
  but no player can fix a source slower than the stream bitrate.
- Built for personal use with your own playlists/subscriptions.
