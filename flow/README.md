# MK Flow, a TV launcher

Part of the Mavro Kinetic app lineup. Styled with the shared **MK Tactical** theme:
olive, coyote brown and tan on near-black, with chamfered (45° cut) cards and panels.

A light, animated home screen for Android TV 9 (built and tuned for the Mi TV Stick).
No libraries beyond the Android framework, so the release APK is about 75 KB.

## What you get

- **Home**: a big clock and the date, focusable pills (Search, All apps, Theme, TV settings,
  Flow settings) that unfold their label when focused, a **Favourites** row and an **Apps** row.
  The focused tile glides to a fixed spot while the row flows underneath. The focused app's
  banner fades in large behind everything, and the background slowly takes on its colour.
- **Themes**: Tactical (dark), Sand (light), or Auto (Sand 7 am–7 pm). Switching cross-fades the whole screen.
- **All apps flyout**: a Start-menu style panel that rises from the bottom over whatever is
  on screen, opened by the All apps tile or the Apps button. Press Apps again (or Back) to close it.
- **Settings flyout**: a notification-centre style panel that slides in from the right, with
  quick tiles (Theme, Remote helper, Wi-Fi, Home app, Screensaver, TV settings), a volume slider,
  and the remote-button settings.
- **Volume indicator**: a pill at the top of the screen with a 20-tick rev-counter bar, replacing the system one.
  Android TV sends volume keys straight to the system, so this needs **Remote helper** turned on.
- **Hold OK** (or press Menu) on any app: Open, Move, Add/Remove Favourite,
  Put on a remote button, App info, Uninstall. Move mode: ← → slides the tile, OK drops it.

## The remote

| Button | What Flow does |
| --- | --- |
| D-pad / OK | Navigate, open. Hold OK for options. |
| Home | Back to Flow; if you're already home, glides back to the first favourite. |
| Back | Closes menus and grids; on the home screen it returns to the first favourite. |
| Apps | Opens or closes All apps (configurable). |
| Volume | Left to the system, unchanged. |
| Voice | Left to Google Assistant by default; can be remapped. |
| **Netflix / Prime Video** | **Open any app you choose.** |

### Making Netflix and Prime Video open your own apps

1. Turn on **Remote helper**: *Flow settings → Remote helper → Turn it on*, then switch on
   **Flow remote helper** in Accessibility. Without it, the TV grabs these buttons before Flow sees them.
2. *Flow settings → Netflix button → Choose what it opens* and pick an app.
3. Flow asks you to **press the Netflix button once**, so it learns the code your remote sends.
   Do the same for Prime Video.

Shortcut: hold OK on any app → *Put on a remote button*.

If your TV hides the Accessibility menu, run this from a computer (note: it replaces any other
accessibility services you have enabled):

```bash
adb shell settings put secure enabled_accessibility_services com.mavrokinetic.flow/app.flowtv.launcher.remote.RemoteKeyService
```

```bash
adb shell settings put secure accessibility_enabled 1
```

## Install on the Mi TV Stick

1. On the stick: *Settings → Device Preferences → About*, click *Build* 7 times, then turn on
   *Developer options → USB debugging* (network debugging works on the stick).
2. From the computer:

```bash
adb connect <stick-ip>:5555
```

```bash
adb install -r dist\app-release.apk
```

3. Make Flow the home screen:

```bash
adb shell cmd package set-home-activity com.mavrokinetic.flow/app.flowtv.launcher.HomeActivity
```

If the stock launcher still comes back on Home, you can disable it (re-enable it any time
with `pm enable`):

```bash
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

## Building

Run `build.bat`. The APKs land in `dist\`. The release APK is signed with the debug key so it
can be sideloaded straight away.

## Code map

- `HomeActivity`: home screen, hero artwork, ambient colour, Favourites move mode
- `AllAppsActivity`, `SettingsActivity`: the app grid and settings
- `ui/`: `FlowRow` and `FlowScroll` (eased focus scrolling), `AppTile`, `Sheet` (themed menus), motion helpers
- `remote/`: `Remote` (button matching, teaching, actions), `RemoteKeyService` (accessibility key filter), `Teach`
- `Palette.kt`: MK Tactical (dark) and Sand (light) colours
- `ui/Chamfer.kt`: the chamfered plate drawable, outline and clipping frame
