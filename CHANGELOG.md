# Changelog

## 0.3.18
- Replaced the subtitle under the King Post title with: "One video. Three platforms."
- No functional changes

## 0.3.17
- Removed manual URI grants and the broad pre-grant to every compatible share app; sharing now uses Android's temporary read-access flag and `ClipData`
- Limited persisted access for selected videos to read-only
- Losslessly converted the in-app header and social icons to WebP, reducing packaged UI artwork by about 31 KB with identical pixels
- Losslessly recompressed the Play Store PNG, reducing the source project by about 160 KB without changing the image
- Confirmed that the only manifest permission remains `POST_NOTIFICATIONS`, which is required for the Instagram retry notification
- No launcher, adaptive, installer, APK/file-manager, or notification icons were changed

## 0.3.16
- Replaced the notification icon with a bold crown-only silhouette for clearer recognition at Android notification size
- Removed the older crown-and-arrow notification bitmap variants so Android always uses the new scalable crown icon
- No launcher, adaptive, installer, or APK/file-manager icons were changed

## 0.3.15
- Replaced the notification icon with a simpler crown-over-arrow silhouette designed specifically for tiny Android notification space
- Removed the face-like interior cutouts and rounded base from the previous notification icon
- Only the notification icon was changed; launcher, adaptive, installer, and file-manager icons were left alone

## 0.3.14
- Reworked the official King Post notification icon into a bolder, denser crown-plus-arrow silhouette for better visibility in Android notifications
- Kept the same notification icon resource name so no notification code changes were needed
- No launcher, adaptive, installer, or file-manager icons were changed

## 0.3.13
- Added an official King Post monochrome notification icon using the crown-plus-arrow mark
- Updated the Instagram retry notification to use the new King Post notification icon for the status icon and notification actions
- No launcher, adaptive, installer, or file-manager icons were changed

## 0.3.12
- Added a low-priority Instagram retry notification with **Try Again** and **Done** actions
- Tapping **Try Again** recopies the caption, regrants temporary video read access, and resends the selected video to Instagram
- The retry notification stays available until **Done** is tapped, the selected video is cleared, or a different video is chosen
- Requests notification permission only when Instagram sharing is first used on Android 13 or newer
- Instagram sharing still works when notification permission is denied
- The retry message warns users to retry before beginning edits

## 0.3.11
- Added a **Clear Video** button beside **Choose Video**
- Clearing a video forgets its saved URI, releases King Post's persisted file permission, clears its thumbnail and filename, and disables posting controls
- Deletes King Post's temporary TikTok sharing copy while leaving the original video and caption untouched
- Cancels a pending TikTok handoff if the selected video is cleared or replaced

## 0.3.10
- Restored the missing `java.util.Collections` import in `MainActivity.java`
- Fixed the `cannot find symbol: Collections` compile error
- No feature or behavior changes
