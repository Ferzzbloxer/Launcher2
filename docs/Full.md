# Technical Report — Patches Applied to Launcher2

This report documents, in chronological order, every change made to the Launcher2 source during this debugging session. Each section explains the original problem, its root cause, and the applied fix.

---

## 1. Build Fixes (getting CI to compile at all)

### 1.1 — Legacy AAPT1 syntax in `strings.xml`
**Problem:** the file contained `<!--add-resource type="string" name="default_folder_name" /-->`, a comment-directive used by the old AOSP Make/aapt1 build system to strip resources. Although technically valid XML, it isn't recognized by the modern resource compiler (Gradle's AAPT2/resource merger), which threw a `SAXParseException`.
**Fix:** line removed.

### 1.2 — Unresolved Git merge conflict markers
**Problem:** `dimens.xml` and `strings.xml` contained unresolved `<<<<<<< HEAD`, `=======`, `>>>>>>> 194358f` markers left over from a botched merge. `<<` is interpreted as the start of an XML tag, breaking the parser.
**Fix:** conflict blocks removed entirely (the "theirs" side was empty in both cases).

### 1.3 — Source code in the wrong directory
**Problem:** during manual folder reorganization (via `mv`), the entire `src/` directory ended up nested inside `app/src/main/`, resulting in `app/src/main/src/com/...` instead of `app/src/main/java/com/...`. Gradle only looks for source in `src/main/java` by default, so no `.java` files were being compiled at all.
**Fix:** contents moved from `app/src/main/src/com` to `app/src/main/java/com`.

### 1.4 — Literal `--` inside XML comments
**Problem:** `AndroidManifest.xml` had comments using `--` as an em dash (e.g. "only -- acceptable"). The XML spec forbids `--` inside comment content (only allowed in the closing `-->`).
**Fix:** two occurrences corrected, replacing `--` with `-` or rewording.

### 1.5 — Name collision: `style name="Theme"`
**Problem:** a local `<style name="Theme">` shared its exact name with a resource already defined by the Android framework itself (`android:style/Theme`). Modern AAPT2 rejects this with `IllegalStateException: Can not add resource ... to table` — a known failure mode when a local resource collides with a framework resource of the same type.
**Fix:** renamed to `LauncherTheme`, reference updated in `AndroidManifest.xml`.

### 1.6 — Name collision: `attr name="title"`
**Problem:** same class of collision, this time with `android:attr/title`. The attribute belonged to the `Favorite` declare-styleable (used to customize the default home screen layout).
**Fix:** renamed to `favoriteTitle`, with generated references (`R.styleable.Favorite_favoriteTitle`) updated in `LauncherProvider.java`.

### 1.7 — Lint blocking the build
**Problem:** the project accumulated ~220 lint findings (2012-era code running against modern APIs). Gradle aborts the build on lint errors by default.
**Fix:** added `lint { abortOnError = false; checkReleaseBuilds = false }` in `app/build.gradle.kts`. This doesn't fix the underlying findings, it just stops them from blocking the build — the lint report is still generated normally.

### 1.8 — Duplicate Kotlin stdlib classes
**Problem:** transitive dependencies (via AndroidX) pulled in both `kotlin-stdlib:1.8.22` and the older split `kotlin-stdlib-jdk7`/`kotlin-stdlib-jdk8:1.6.21`. Since Kotlin 1.8, those extensions are already merged into the main stdlib, making the separate modules redundant — and the duplication breaks the build.
**Fix:** globally excluded via `configurations.all { exclude(...) }` in `app/build.gradle.kts`.

---

## 2. Crash / black screen opening the app drawer

### 2.1 — Incomplete `LauncherViewPropertyAnimator.end()`
**Problem:** `end()` only called `cancel()` on the underlying animation. This has two serious side effects: (a) it leaves the view exactly wherever the animation happened to be interrupted (e.g. 40% faded, half-scaled) instead of jumping to its final value; (b) it fires `onAnimationCancel()` on listeners instead of `onAnimationEnd()` — so the app drawer's "I'm done opening, now become fully visible" completion logic never ran. Result: the drawer appeared stuck/black on open.
**Fix:** rewritten to detach the listener, cancel the underlying animation, manually apply the final target values (translation, scale, alpha, rotation) to the view, then fire `onAnimationEnd()` — reproducing what a real `end()` should do.

---

## 3. Package visibility (Android 11+)

### 3.1 — App drawer only showed stock apps
**Problem:** starting with Android 11, apps must explicitly declare which packages they're allowed to query via `PackageManager`. Without it, `queryIntentActivities()` returns a restricted, OEM-inconsistent set.
**Fix:** added a `<queries>` block in the manifest for `ACTION_MAIN`/`CATEGORY_LAUNCHER`, matching exactly what `LauncherModel.java` queries for.

---

## 4. Home screen layout and sizing

### 4.1 — Home screen grid rendered as a tiny centered square
**Problem:** `workspace_screen.xml` declared the per-page `CellLayout` as `wrap_content`. This makes `PagedView` measure the child in `AT_MOST` mode, and `CellLayout`, in that mode, ignores the available space and shrinks to a fixed `cellCount × cellSize` — dimensions from 2012 with no relation to the current screen.
**Fix:** changed to `match_parent`, forcing `EXACTLY` mode, which makes `CellLayout` actually use the real available space.

### 4.2 — Dead space around the icons
**Problem:** even after the fix above, the gap between cells (`workspace_width_gap`/`height_gap`) was hardcoded to `0dp`. The code already had logic to "stretch" the gap and fill leftover space, but it only activates when the value is negative (a sentinel).
**Fix:** values changed to `-1dp` in `values/dimens.xml` and `values-sw380dp-port/dimens.xml`, enabling the auto-stretch path.

### 4.3 — Grid still too small / didn't track `wm size`
**Problem:** even with auto-stretch, the "stretching" has a ceiling (`maxGap`, 16dp) — nowhere near enough to absorb the gap between a real modern screen and 2012-era cell dimensions. On top of that, `cellCountX`/`cellCountY` were fixed values from `config.xml`, read once at view construction time.
**Temporary fix:** `cell_count_x`/`cell_count_y` manually set to 6×12 (computed from the device's effective dp resolution).
**Final fix:** added true **adaptive** cell-count support to `CellLayout.java` — a new `useAdaptiveCellCount` attribute (enabled only on the home screen, not the hotseat) makes `onMeasure()` recompute `mCountX`/`mCountY` from the actual measured pixel size, mirroring the same `calculateCellCount` logic the app drawer already used. This makes the grid track `wm size` changes automatically, without depending on a fixed `config.xml` value.

---

## 5. Custom icon pack

Added support for an icon pack (24 app→icon mappings extracted from a KitKat-style icon pack APK):
- 20 PNGs copied into `res/drawable-nodpi/` with an `iconpack_` prefix.
- New `IconPackMap.java` class with a `HashMap<String, Integer>` mapping `ComponentName` (as `package/class`) to the corresponding drawable.
- `IconCache.java` modified in `cacheLocked()` — before loading an app's default icon via `info.getBadgedIcon()`, it checks whether a replacement exists in `IconPackMap`; if so, uses the custom drawable (still applying the work-profile badge if applicable).

---

## 6. Wallpaper picker

### 6.1 — Deprecated `Gallery` widget rendering broken
**Problem:** `wallpaper_chooser.xml` used `android.widget.Gallery`, deprecated since API 16 and known to render broken on modern Android.
**Fix:** replaced with `HorizontalScrollView` + `LinearLayout`; `WallpaperChooserDialogFragment.java` rewritten to manually inflate each thumbnail and track selection itself (no longer relying on `Gallery`'s `Adapter`/`AdapterView` machinery).

### 6.2 — Content overlapping the navigation bar
**Problem:** with `targetSdk = 36`, apps get edge-to-edge rendering by default (Android 15+). This screen was never designed for that.
**Fix:** `WallpaperChooser.java` calls `getWindow().setDecorFitsSystemWindows(true)` to opt this screen out of edge-to-edge. Also added `fitsSystemWindows="true"` to the layouts as a fallback, since this OEM (Vivo) has already shown inconsistent handling of newer APIs.

### 6.3 — Didn't recognize photos or live wallpapers
**Problem:** the screen only read the static `@array/wallpapers` array (which contained a single placeholder item, `default_wallpaper` — hence the thumbnail always applying the system default). There was no integration with the photo picker or with installed live wallpaper services.
**Fix:** added two new entry types to the gallery:
- **Photo:** launches `ACTION_GET_CONTENT`, then attempts `WallpaperManager.getCropAndSetWallpaperIntent()` (system crop flow); if that activity doesn't exist on the ROM, falls back to setting the image directly via `WallpaperManager.setStream()`.
- **Live Wallpapers:** queries `PackageManager.queryIntentServices()` for installed `WallpaperService` components, shows each one's own preview/icon, and on tap delegates to the system's own preview screen via `ACTION_CHANGE_LIVE_WALLPAPER`.
- Added a `<queries>` entry for `android.service.wallpaper.WallpaperService`, required under the same Android 11+ package-visibility rules.

---

## 7. Performance — lag while scrolling the home screen

**Problem:** `Workspace.onDraw()` calls `WallpaperManager.setWallpaperOffsets()` (a synchronous IPC call to the system) on every single draw frame, with no throttling — behavior inherited from an era when 60Hz was standard. On modern 90/120Hz displays, that's up to twice as many IPC calls per second, causing perceptible system-wide jank during fast scrolling.
**Fix:** added a minimum time interval between calls (`WALLPAPER_OFFSET_IPC_MIN_INTERVAL_MS = 12`), while always still sending the final settling value (so the wallpaper never visibly lags behind).

---

## 8. Uninstalling apps from the drawer

### 8.1 — Uninstall drop target became invisible
**Problem:** `DeleteDropTarget` hid the entire trash-can target (`View.GONE`) whenever the dragged app lacked the internal `DOWNLOADED_FLAG` — computed from `ApplicationInfo.FLAG_SYSTEM`. OEM ROMs like this one (Vivo) commonly mark pre-installed third-party apps as "system", causing this heuristic to silently fail for perfectly ordinary apps.
**Fix:** removed the logic that hid the target; it now always appears when dragging an app from the drawer (legitimate user-restriction checks were kept).

### 8.2 — Confirmation dialog didn't appear even with the target visible
**Problem (confirmed via logcat):** even though `ACTION_DELETE` correctly launched `com.android.packageinstaller/.UninstallerActivity`, the log showed: `UninstallRepository: Uid ... does not have android.permission.REQUEST_DELETE_PACKAGES`. Recent package installer versions require the calling app to declare this permission; without it, the activity simply exits without showing anything.
**Fix:** added `<uses-permission android:name="android.permission.REQUEST_DELETE_PACKAGES" />` to the manifest. It's a normal permission — no runtime prompt required.

---

## Summary of changed files

| File | Type of change |
|---|---|
| `strings.xml`, `dimens.xml` | Removed legacy syntax and merge conflicts |
| `AndroidManifest.xml` | Comment fixes, `<queries>` (×2), `REQUEST_DELETE_PACKAGES` permission |
| `styles.xml`, `attrs.xml` | Renamed colliding resources, new `useAdaptiveCellCount` attribute |
| `app/build.gradle.kts` | Lint config, duplicate dependency exclusion |
| `LauncherViewPropertyAnimator.java` | `end()` rewrite |
| `workspace_screen.xml` | `wrap_content` → `match_parent` |
| `CellLayout.java` | Adaptive cell count |
| `config.xml` | Temporary `cell_count_x/y` adjustment |
| `IconPackMap.java` (new), `IconCache.java` | Icon pack support |
| `WallpaperChooserDialogFragment.java`, `WallpaperChooser.java`, `wallpaper_chooser*.xml` | Full wallpaper picker rewrite |
| `Workspace.java` | Wallpaper offset IPC throttling |
| `DeleteDropTarget.java`, `Launcher.java` | Removed uninstall heuristic |
