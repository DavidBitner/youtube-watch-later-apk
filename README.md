# Watch Later

Android app that turns a YouTube playlist into an offline player.

## Features

- Paste any public or unlisted playlist link, switch between saved playlists from the title menu
- Shows the playlist in its original order with positions, thumbnails and durations
- Downloads on Wi-Fi only, 1080p max, VP9 preferred with H.264 fallback
- Default playback speed 1.5x, adjustable in the player and in settings
- Resume position, watched marks, optional delete after watching
- Optional storage limit in GB
- yt-dlp can be updated from settings without reinstalling the app
- Adapts to phones and tablets

## Build

Push to `main`. The `Build APK` workflow publishes `watch-later-apk` in the run's artifacts.

Every build is signed with the keystore in `app/keystore`, so a new APK installs over the old one and keeps downloads and settings.

## Install

Open the APK on the device. On Samsung, turn off Auto Blocker or allow installs from your browser or file manager first.

## Notes

- Playlists must be public or unlisted. Private playlists can't be read.
- If downloads fail, open Settings and tap Update yt-dlp, then sync again.
- Downloaded videos are stored in the app's own storage and removed when the app is uninstalled.
