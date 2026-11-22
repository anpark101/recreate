# Superyau online app

An **unofficial Android WebView wrapper** and an **installable web launcher** for
<https://superyau.iryonmann.workers.dev/>. Internet is required.

The original website remains on its own server. Its HTML, CSS, content, branding,
and layout are not rewritten. The launcher has an original, separate app icon.
This project is not affiliated with the website creator.

## Current status — important

- Android source and a GitHub Actions APK-build workflow are provided.
- **No APK has been compiled or device-tested in this workspace.** There is no
  Android SDK/JDK installed here, and outbound access prevents downloading the
  Android toolchain or connecting directly to the original website.
- The web launcher, manifest, icons, service worker, and local server pass the
  included local tests. Those are configuration/server checks, **not** end-to-end
  tests of the remote website.
- The web version embeds the original site. Its CSP or `X-Frame-Options` may
  prevent this; cross-origin storage, downloads, or browser APIs may also behave
  differently inside an iframe. This cannot be fixed from this repository.
  `/install.html` includes a direct link to the original site as a fallback.
- The Android wrapper loads the site as the top-level WebView page, not an
  iframe. Nevertheless, site-specific browser features and Cloudflare policies
  must be checked on a real device before calling it fully compatible.

## Android: create and download the APK

When these changes are pushed to the current branch, the **Build Android APK**
GitHub Actions workflow runs automatically for Android-related changes. No
secrets, paid service, or developer account are needed for this test build.

1. On GitHub, open this repository → **Actions → Build Android APK**.
2. Select the successful run for this branch.
3. Under **Artifacts**, download **Superyau-Android-APK** and extract its ZIP.
4. On Android, tap `app-debug.apk`. If prompted, allow installation from the
   browser or Files app that you used to open it.
5. Open **Superyau** from your home screen.

The workflow can also be run manually once GitHub recognizes it as a workflow
on the default branch. Nothing in this workspace has been pushed or deployed
as part of creating these files.

This is a **debug-signed, sideloadable test APK**, not a Play Store release.
GitHub's fresh runners may generate a different debug signing key on each run;
updates may require uninstalling the previous build, which removes its local
settings. For ongoing distribution, configure a persistent release signing key
in GitHub secrets; never commit a signing key.

### Build on a development computer

Install JDK 17, Gradle 8.11.1, and the Android SDK (platform 35 and build-tools
35.0.0). Set `ANDROID_HOME` or create an untracked `android/local.properties`.
Then run:

```sh
cd android
gradle assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

Or open `android/` in Android Studio and build the APK. The Gradle wrapper binary
is not included; the workflow installs a pinned Gradle version.

### Android behavior

- No native toolbar, advertisements, or injected website styling.
- JavaScript, first-party cookies, and DOM storage enabled for site controls and
  autosave. The data is separate from Chrome's existing saved data.
- System file picker for uploads, including multiple selections when requested.
- PNG/JPEG/WebP `data:` and site-origin `blob:` download handling via Android's
  Save dialog. A small export helper reads blobs in the existing page context;
  **no native JavaScript interface is exposed**. Exports are limited to about
  24 MB to bound memory usage. HTTP(S) downloads open in the browser.
- Back button navigates website history; local connection failure screen offers
  Retry and Open in browser. This screen is not part of the website design.
- No broad storage permissions, no cleartext HTTP in the WebView, no certificate
  bypass, no file URL access. External HTTPS/HTTP/mail/phone links use another
  app rather than exposing the wrapper to unrelated websites.

## Installable web launcher

```sh
npm start
# http://localhost:3000/              — full-window original site iframe
# http://localhost:3000/install.html  — install instructions and fallback link
npm test
```

No npm dependencies are required. The server binds to `0.0.0.0` and accepts the
preview host. On a phone, a hosted **HTTPS** URL is required for installation;
plain HTTP over a local network is not enough.

Deploy the contents of `web/` to any HTTPS static host. For GitHub Pages, enable
**Settings → Pages → Source: GitHub Actions**, then run **Deploy web launcher**
after the workflow is available on the default branch. Relative paths also
support repository-subpath hosting. This workflow publishes only `web/`.

On Android Chrome, open the hosted `/install.html`, tap **Install web app** when
available, or use **⋮ → Add to Home screen → Install**. Installation availability
and menu wording depend on Chrome. The launcher has its own origin; it cannot
add a manifest/service worker to the original website's origin.

Only the launcher shell is cached, to show a helpful connection message. The
remote website is never claimed to be offline. No analytics or backend are added.

## Before distributing: device checklist

- Launch on Android 8+ with a current Android System WebView and internet.
- Compare appearance with the original site in Chrome (including keyboard,
  status bar, portrait/landscape, and dark mode).
- Switch each website section and edit a message; confirm the preview updates.
- Upload one photo and multiple photos; cancel the picker and reopen it.
- Save a generated image, choose a folder, and inspect the saved PNG/JPEG/WebP.
- Change settings, close/reopen the app, and check the site's autosaved data.
- Test Back, external links, rotation, lost connection, and Retry.
- On the hosted web launcher, verify the site permits embedding, install it,
  and test uploads, image saving, and persistence there independently.

Remote-site changes or downtime will affect both wrappers. If the site blocks
WebView/iframe use or needs APIs unavailable there, its owner's cooperation may
be necessary; this project cannot guarantee every button works without testing.
