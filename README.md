# Patches Applied to Launcher2

## Build
- Removed legacy `<!--add-resource.../-->` syntax from `strings.xml` (AAPT2-incompatible)
- Removed Git merge conflict markers in `dimens.xml` and `strings.xml`
- Moved source code from `app/src/main/src/` to `app/src/main/java/`
- Fixed `--` inside XML comments in `AndroidManifest.xml` (2 occurrences)
- Renamed `style name="Theme"` → `LauncherTheme` (framework resource collision)
- Renamed `attr name="title"` → `favoriteTitle` (collision with `android:attr/title`)
- Configured lint as non-blocking (`abortOnError = false`)
- Excluded duplicate `kotlin-stdlib-jdk7`/`jdk8` dependencies

## Crash / black screen in app drawer
- Rewrote `LauncherViewPropertyAnimator.end()` to apply final values and correctly fire `onAnimationEnd()`

## Package visibility
- Added `<queries>` to manifest for `ACTION_MAIN`/`CATEGORY_LAUNCHER`

## Home screen layout
- `workspace_screen.xml`: `wrap_content` → `match_parent`
- Cell gaps changed to `-1dp` (enables auto-fill)
- `cell_count_x`/`cell_count_y` temporarily set to 6×12
- Implemented **adaptive** cell count in `CellLayout.java` (new `useAdaptiveCellCount` attribute)

## Icon pack
- Added `IconPackMap.java` + patch to `IconCache.java`
- 20 icons bundled into `res/drawable-nodpi/`

## Wallpaper picker
- Replaced deprecated `Gallery` widget with `HorizontalScrollView` + `LinearLayout`
- Fixed navigation bar overlap (`setDecorFitsSystemWindows`, `fitsSystemWindows`)
- Added photo picker support (with fallback if system cropper fails)
- Added support for installed live wallpapers (+ `<queries>` for `WallpaperService`)

## Performance
- Throttled `setWallpaperOffsets()` calls during home screen scrolling

## App uninstall
- Removed `DOWNLOADED_FLAG`/`FLAG_SYSTEM` heuristic hiding the uninstall target
- Added `REQUEST_DELETE_PACKAGES` permission (root cause of missing confirmation dialog)

Btw, these are formatted in topics, if you want a full guide-through, take a look in the docs folder

# For the future:
- lockable apps
- add ability to add more sections (like the ones already on the top, "APPS" and "WIDGET", for better organization) 
- fix wallpaper picker
- fix widgets not displaying properly

I'm also open for suggestions*

*Suggestions will be evaluated, as the project aims to keep a 1:1 "legacy" experience while adding the convenience of more recent launchers

