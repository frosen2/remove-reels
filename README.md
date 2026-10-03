# Remove Reels

An Android app that hides the **Reels tab** in Instagram while it's turned on.
Reels that people send you in DMs still open normally.

## How it works

The app runs as an Android *Accessibility service*. While blocking is on:

- It covers the Reels button in Instagram's bottom tab bar with a blank patch that
  matches the bar, so the button is gone and taps on that spot do nothing.
- If you still land on the Reels tab (for example by swiping between tabs), it
  sends you straight back to Home.
- When you open a reel from a DM, Instagram shows it in a separate viewer without
  selecting the Reels tab, so that still works.

Turn blocking off (in the app or with the Quick Settings tile) and Instagram goes
back to normal right away. The app has no internet permission and never reads or
stores your messages.

## Install on your phone

1. On your phone, open this repo's **Releases** page (the newest one is at
   `https://github.com/frosen2/remove-reels/releases/latest`) and download
   `RemoveReels.apk`.
2. Open the downloaded file. If Android asks, allow your browser or Files app to
   *install unknown apps*, then tap **Install**.
   If Play Protect warns about an unrecognized app, tap **More details → Install anyway**.
3. Open **Remove Reels** and tap **Open Accessibility settings**. Find
   **Remove Reels** (it may be under *Downloaded apps* or *Installed apps*) and turn it on.
4. **If Android says "Restricted setting"** (common on Android 13+ for apps
   installed outside the Play Store):
   go back to Remove Reels → tap **Allow restricted settings (App info)** → tap
   the **⋮** menu in the top-right → **Allow restricted settings** → then repeat step 3.
5. Make sure the **Block Reels** switch is on, then open Instagram. The Reels tab is gone.

Optional: swipe down twice from the top of the screen, tap the pencil (edit) icon,
and drag the **Block Reels** tile into Quick Settings for one-tap on/off.

### Motorola tip

Some phones close background services to save battery. If the Reels tab ever comes
back while blocking is on, go to **Settings → Apps → Remove Reels → App battery usage**
and set it to **Unrestricted**.

## Building it yourself

Every push to GitHub builds the APK with GitHub Actions and publishes it as a release.
To build locally you need the Android SDK and JDK 17:

```
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/app-release.apk`.
