# Build APKs from your phone (one-time setup)

GitHub builds the APK automatically every time you push code.
One manual move is needed first (the API cannot write the workflows folder).

## One-time move (2 minutes, phone browser)

1. On **github.com** (not the app), open `SETUP-build-workflow.yml` in this repo.
2. Tap the **pencil** (Edit this file).
3. In the **filename box** at the top, change the name to exactly:
   `.github/workflows/build.yml`
   (this *moves* the file into the special folder — content stays the same)
4. Tap **Commit changes**.

That's it. The first build starts automatically.

## Everyday use

- Edit any file under `app/` on your phone → **Commit** → GitHub builds the APK (~5–10 min).
- Download: repo → **Actions** tab → tap the green run → **Artifacts** → download the APK → install.
- The APK is signed with the same key as before, so it installs as an **update** (no uninstall needed).
- Bump the version in `build.sh` (`VER_CODE`, `VER_NAME`, `APK_NAME`) before building a new release.
