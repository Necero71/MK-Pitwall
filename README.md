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
