# Album

An Android app with one job: **open it, see your album.** No feed, no tabs, no chrome.

On launch it reads a saved MediaStore `bucket_id` and draws that album's photos in a
full-screen grid. First launch (or when you tap the gear) it shows an album picker.

## Requirements

- Android Studio (or a JDK 17+ and the Android SDK)
- `compileSdk` 37, `targetSdk` 36 (Android 16, matching One UI 8.5), `minSdk` 26
- AGP 9.4.1 / Gradle 9.7.1 / Compose BOM 2026.09.00

AGP 9 supplies Kotlin itself, so the only extra plugin applied is the Compose
compiler (`org.jetbrains.kotlin.plugin.compose`).

## Build and run

```bash
./gradlew installDebug        # build + install on a connected device
./gradlew assembleDebug       # APK only -> app/build/outputs/apk/debug/
```

`local.properties` points at the local Android SDK; regenerate it if you move the SDK.

## How it works

| Piece | File |
| --- | --- |
| Launch path, three-state routing | `AlbumApp.kt` |
| State holder, MediaStore observation | `AlbumViewModel.kt` |
| Album + photo queries | `data/MediaStoreRepository.kt` |
| Saved album (DataStore) | `data/AlbumPreferences.kt` |
| Permission model | `data/MediaPermissions.kt` |
| Album picker | `ui/AlbumPickerScreen.kt` |
| Home grid | `ui/AlbumGridScreen.kt` |
| Pager + pinch-to-zoom | `ui/PhotoViewerScreen.kt` |

Notable decisions:

- **No nav graph.** Three states held in one composable keeps the cold-start path
  "read pref → draw grid" with no intermediate frame.
- **Nothing is drawn until DataStore answers**, so returning users never see the
  picker flash before their album appears.
- **Albums are folded in Kotlin** rather than with a `GROUP BY`. MediaStore has no
  portable grouping across API levels, so the repository walks the (already sorted)
  cursor once and takes each bucket's newest image as its cover.
- **A `ContentObserver` feeds both queries**, so photos added or deleted elsewhere
  show up without a manual refresh.
- **Coil has no disk cache.** The originals are already local files; a disk cache
  would only duplicate them. The memory cache gets 30% of the heap instead.

## Changing album

- Gear button, top-right of the grid
- Long-press any thumbnail

Both open the picker; picking an album saves it and returns you to the grid. Back
leaves the picker only when an album is already saved.

## Permissions

| Platform | Permission |
| --- | --- |
| Android 14+ | `READ_MEDIA_IMAGES`, or `READ_MEDIA_VISUAL_USER_SELECTED` for partial access |
| Android 13 | `READ_MEDIA_IMAGES` |
| Android 12L and below | `READ_EXTERNAL_STORAGE` (capped via `maxSdkVersion`) |

Access is re-checked on every resume, since it can be revoked from Settings while
the app is backgrounded. Denial shows a rationale screen; a permanent denial swaps
the button for an app-settings shortcut. Under partial access the picker shows a
"Select more" action that reopens the system photo selection sheet.

## Gestures

- **Tap** a thumbnail: open the full-screen viewer
- **Swipe**: previous/next photo (disabled while zoomed, so panning doesn't turn pages)
- **Pinch**: zoom up to 6×, anchored on the pinch centroid; panning is clamped to the image
- **Double-tap**: toggle 2.5× on the tapped point
- **Single tap**: show/hide the close button and photo counter
- **Back**: leave the viewer
