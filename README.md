# MK PITWALL

Offline-capable F1 26 setup book for your phone or tablet. Save setups per track and condition, compare any two side by side, and look up what to change for a handling problem.

**Tabs:** Setup (editor), Book (saved library, backup/restore), VS (compare), Fix (symptom guide).

## Run it

Plain HTML/CSS/JS, no build step.

- Quick look: open `index.html` (the offline/install bits need hosting, below).
- Local server: `python3 -m http.server 8000` in this folder, then open `http://localhost:8000`.

## Host it on GitHub Pages

1. Push these files to the root of a repo's `main` branch.
2. Repo **Settings > Pages**, source `main` / root.
3. Open the Pages URL on your phone and use **Add to Home Screen** (iOS Safari) or **Install app** (Chrome on Android).
4. https://necero71.github.io/MK-Pitwall/
If you edit any file, bump `CACHE` in `sw.js` (`mkpit-v1` to `mkpit-v2`) so installed copies update.

## Notes

- Setups are saved in the browser on that device only. Use **Book > Export JSON** for backups, and Import to restore or move to another device.
- No min/max limits are enforced. Starter values are a neutral baseline, so enter what your car's setup screen shows.
- The Fix tab holds general rules of thumb, not F1 26 measurements.
- Logic lives in `core.js` and is independent of the UI.

## Android app (Capacitor)

The same web files are also packaged as a real Android app. The web version and the GitHub Pages site are unchanged.

- App id: `io.github.necero71.mkpitwall`. This can't be changed after people install it, so settle it before the first release.
- `npm install`, then `npm run sync` copies the web files into `android/`. `npm run open` opens Android Studio.
- The service worker is skipped inside the app (files are bundled, so it isn't needed).
- Data is stored inside the app, separate from the web/PWA/Store copies. To move setups across, use **Book > Export JSON** in the old copy and **Import** in the app. In the app, Export opens the Android share sheet.

### One-time signing setup

Every update has to be signed with the **same key** as the first release, or Android refuses to install it over the old one. Back the keystore up somewhere safe (password manager plus an offline copy). Lose it and nobody can update.

```bash
keytool -genkeypair -v -keystore release.keystore -alias mkkey -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore    # paste this output into the first secret below
```

Repo **Settings > Secrets and variables > Actions**, add:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | output of the `base64` command |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | `mkkey` (or whatever you used) |
| `ANDROID_KEY_PASSWORD` | key password |

One keystore can sign both MK apps. Never commit it (`*.keystore` is git-ignored).

### Releasing and updating

```bash
git tag v1.0.0 && git push origin v1.0.0
```

GitHub Actions builds a signed APK and attaches it to a GitHub Release. Every new tag (`v1.0.1`, `v1.1.0`...) is an update. The version code is derived from the tag (`1.2.3` becomes `10203`), so tags must go up.

- **Install:** download the APK from the Releases page and allow installs from unknown sources.
- **Auto-updates on the phone:** add this repo's URL to [Obtainium](https://github.com/ImranR98/Obtainium) and it will pick up new releases.
- **Test build:** Actions tab, *Android APK*, *Run workflow*. With no secrets set it makes a debug APK (artifact only, never published). Debug and release builds are signed differently, so uninstall one before installing the other.
