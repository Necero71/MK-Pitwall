# Releasing

This repo ships two Android apps. Each has its own version line and its own tags, so you can follow either one on its own.

| App | Package id | Tag | Release title | APK |
|---|---|---|---|---|
| MK Pitwall | `io.github.necero71.mkpitwall` | `pitwall-vX.Y.Z` | MK Pitwall X.Y.Z | `mk-pitwall-vX.Y.Z.apk` |
| MK Telemetry | `com.mavrokinetic.telemetry` | `telemetry-vX.Y.Z` | MK Telemetry X.Y.Z | `mk-telemetry-vX.Y.Z.apk` |

## Version numbers

[Semantic versioning](https://semver.org): `MAJOR.MINOR.PATCH`.

- **PATCH** (`0.3.0` → `0.3.1`): bug fixes only, nothing looks or works differently on purpose.
- **MINOR** (`0.3.1` → `0.4.0`): new features or visible changes (a new game, a new layout, a restyle).
- **MAJOR** (`0.x` → `1.0.0`): the first version you'd call finished, then anything that breaks saved data or settings.

The Android version code comes from the tag: `MAJOR × 10000 + MINOR × 100 + PATCH` (`1.2.3` → `10203`). Android only installs an update with a higher code, so tags must always go up, and MINOR and PATCH stay below 100.

## Cutting a release

```bash
git checkout main && git pull
git tag telemetry-v0.3.0          # or pitwall-v1.0.0
git push origin telemetry-v0.3.0
```

GitHub Actions builds a signed APK and publishes the GitHub Release, with notes listing the changes since that app's previous tag. Without the signing secrets, no release is published (see the README's signing setup).

Pushes and pull requests build debug APKs only (Actions tab > the run > Artifacts). Debug and release builds are signed differently, so uninstall one before installing the other.

## History

| App | Version | Notes |
|---|---|---|
| MK Telemetry | 0.3.0 (next) | First signed release: Mavro Kinetic restyle, new package id `com.mavrokinetic.telemetry` |
| MK Telemetry | 0.2.1 | Phone layout fixes (debug builds only) |
| MK Telemetry | 0.2.0 | Phone, landscape and tablet layouts (debug builds only, as Console Telemetry) |
| MK Telemetry | 0.1.0 | First build (debug only, as Console Telemetry) |

The package id changed in 0.3.0, so it installs as a new app next to any old Console Telemetry debug build. Uninstall the old one.
