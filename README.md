# NOCTIS v3 — Android and web preview

**System Developed by Hafiz Faizan Murtaza**

This is a consent-based test application, **not a production or system-level Find My Device replacement**. The APK is debug-signed, has not had a security audit, and v3 has not been tested on a physical Android phone. Do not depend on it to recover a lost or stolen phone.

## Android

Android 8+; first launch asks for a local four-digit PIN and confirmation. The PIN lock hides the app on this device and on return from the background; it is not biometric and does not protect the separate web dashboard or encrypt all local data. A verifier is protected by Android Keystore and repeated failed attempts cause a short lockout. A four-digit PIN provides limited security. If it is forgotten, app data must be cleared and the device re-paired. The app restricts screenshots.

The interface is bundled in the APK and served under the configured HTTPS origin; it can appear while the server wakes, with a connection pill. A cached dashboard is available only when an existing WebView session has both a token and cached device state. Cached device statuses are marked offline; location may be stale. Startup time under one second is not guaranteed or measured on physical hardware. Map tiles and all live operations still require network and the backend.

Bonto test origin `https://noctis-faizan-v2.bonto.page` is preconfigured. Change it through SERVER. Install only servers you trust: the APK sends credentials and device data to that origin. To enable target alerts, pair and use the target-mode buttons to enable location/sound and Android notification permission. Commands require an active device connection; notifications are local to the app and are not push notifications. They may be delayed or absent if Android stops/backgrounds the app, network drops, or the backend sleeps. Notifications omit the message text to reduce lock-screen exposure. System settings control notification and sound behavior; silent mode and Do Not Disturb cannot be guaranteed to be overridden.

The prior v2 debug APK and this build may have different signing keys. Android will refuse an in-place update if signatures differ; uninstalling the old app clears its local data and pairing, so re-pairing will be necessary. Back up any needed information before uninstalling. A proper release needs a securely managed signing key.

## Maps and privacy

OpenFreeMap/MapLibre provides key-free in-app streets; light/dark map follows the selected theme. The Google Maps navigation and satellite buttons open Google Maps externally. The official interactive Google Maps Embed API requires a Google Cloud API key and billing-enabled project; no key is bundled. An on-demand reverse address lookup sends coordinates to OpenStreetMap Nominatim and returns an **approximate** nearest mapped address, not a guaranteed exact address. Its public usage policy and limits apply. OpenFreeMap receives IP and map-area information. Attribution is shown on the map.

## Backend

Node 22.13+ required. `npm ci` generates MapLibre and image assets and prepares the Android shell's local UI. `npm test` exercises the server. For local use, set a random `JWT_SECRET` of at least 32 bytes and run `npm start`. For HTTPS behind a trusted proxy set `HOST=0.0.0.0`, `BASE_URL` to the exact HTTPS origin, and a strong random `JWT_SECRET`. For temporary test SQLite set `ALLOW_EPHEMERAL_SQLITE=true`; data may be lost on sleep, restart or redeploy. The free Bonto plan sleeps and has a monthly runtime cap. PostgreSQL is supported via `DATABASE_URL` but needs independent deployment and backup.

The public backend is not production-hardened: there is no email verification, MFA, account recovery or comprehensive revocation/audit, per-IP throttling is basic, six-digit codes have limited entropy, location data at rest is not end-to-end encrypted and backups are not assured. A security review, reliable infrastructure, stronger authentication, privacy disclosures, release signing and physical-device tests are required before real use.

## Build

Run `npm ci`, then open `android/` in Android Studio with JDK 17 and Android SDK 35, or run `cd android && ./gradlew assembleDebug`. The web assets and Gradle wrapper binary are reconstructed by the npm postinstall step. Debug signing is for testing only. `PGLITE_TEST=1 npm test` exercises the PostgreSQL-compatible query path. Use only on devices whose owners consent.
