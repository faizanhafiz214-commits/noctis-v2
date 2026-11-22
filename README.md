# NOCTIS v2 — preview build, not a production release

**System Developed by Hafiz Faizan Murtaza**

This package contains a Node/SQLite server, mobile-first web interface, and a native Android WebView shell. The APK is a **debug-signed preview**; it is not security-audited, production-signed, deployed or tested on a physical phone. It is not an OS-level Find My Device replacement. Use only for devices whose owners have consented. Node 22.13+ is required.

## What changed

- Dark vector map: MapLibre GL JS and the OpenFreeMap dark style, using OpenStreetMap/OpenMapTiles data. **No API key** is required. An Internet connection and working WebGL are required; availability and third-party terms still apply. The tiles provider receives the device IP and the map area being viewed. Attribution remains on the map.
- Cyan/crimson glass interface with CSS 3D phone visualizer, motion-reduced alternatives, live telemetry status, radar pin, six-digit one-use pairing code and requested developer credit.
- A dual-oscillator distorted sweep siren and a slow red emergency pulse (disabled with reduced-motion settings). A stop button is always available in the foreground app.
- Android shell with a configurable trusted HTTPS server, links to controller and target modes, geolocation permission prompts and an **opt-in** setting that temporarily raises Android media volume while the alarm runs, then attempts to restore it. It does not bypass OS sound restrictions.

## Install and connect the preview APK

Install the separately supplied `NOCTIS-v2-preview-debug.apk` on Android 8.0+ after allowing installation from the chosen source. Android may warn that a debug-signed APK is not a production release. Open it and choose **SERVER**. Enter the origin of your own trusted, reachable HTTPS NOCTIS v2 server (for example `https://your-domain.example`, without a path), then choose Connect. No server URL is preconfigured: a backend has not been deployed by this package. The app downloads and executes the interface from the configured server; never point it at an untrusted server. Enable the optional maximum media-volume setting only after considering the loudness risk.

The controller interface is `/`, target is `/device`. Use the mode link in the web interface to switch. Create an account, generate a six-digit code, enter it in target mode on another device, and explicitly enable location and audio on that target. Keep the target app in the foreground. The QR code opens the companion page; it does not contain the pairing code or scan with the camera.

## Local developer use

```
npm install
export JWT_SECRET="$(openssl rand -hex 32)"
npm start
```

Open http://localhost:3000 on the same computer. The Android app accepts HTTPS only, so localhost is not sufficient to connect a phone. For remote access configure your own HTTPS reverse proxy and set `BASE_URL` to its public origin. The backend binds to `127.0.0.1` by default; when deployed on a platform that supplies TLS at an internal ingress, follow its networking instructions and set `HOST=0.0.0.0` only inside that protected hosting environment. `BASE_URL`, WebSocket origin and dashboard origin must match. Do not expose unencrypted backend HTTP directly to the public. Never use the sample secret.

The Android source is under `android/` and can be opened in Android Studio with JDK 17 and Android SDK 35. `app/build.gradle` defines the app. A local debug build was compiled with Android Gradle Plugin 8.9.2 / Gradle 8.11.1; production distribution requires a separately managed release signing key, a release build, real device tests, privacy disclosures and security review. Do not use the debug signing key for production.

Run `npm test` for server integration tests. Tests use temporary data. Browser testing exercised six-digit pairing, mock GPS, message, siren command and simulated wipe. No physical Android, audio-level or 60 FPS performance testing was performed.

## Important limitations and security

- The APK is a thin **WebView shell**, not a standalone backend. A live, secure server is still required; no cloud deployment or public URL is included. The target must remain open and connected: there is no push service or reliable background tracking, alarm, GPS or command queue.
- Android/browser APIs do not guarantee sound during silent mode, Do Not Disturb, backgrounding or screen lock. Maximum volume is optional and applies only to the media stream; it can be limited by the OS, device or audio route. No guarantee of reaching a particular loudness. The red flashing UI could affect photosensitive users; it is slow and respects reduced-motion, but use cautiously.
- Messages are displayed inside the app, not on the OS lock screen. Simulated wipe only removes this app's local pairing data and revokes the server pairing; it does not wipe the phone, photos, or other apps. It requires the target to be online. The server revokes upon command dispatch, and does not confirm execution.
- The backend uses scrypt password hashes, JWT expiry/signature checks, per-device random tokens stored hashed, ownership checks, one-use expiring codes and basic in-memory IP throttling. Six-digit codes have limited entropy and the in-memory per-IP limiter is **not sufficient for a public production service**; distributed abuse protection and hardened pairing are required. Email is not verified; there is no MFA, account recovery, strong session revocation or audit log. Tokens in web storage can be exposed by XSS. The native WebView exposes an opt-in audio-volume bridge to the configured server's pages, another reason to trust and secure the server.
- The SQLite database contains sensitive user and location data and has no encryption at rest. Many free web hosting plans use ephemeral filesystems; restarts/deployments can permanently erase accounts, pairings, and telemetry. Use an appropriate persistent, backed-up, access-controlled database before any real deployment. Do not present this as production-ready without a security assessment and a suitable authenticated hosting architecture.
- Location may be stale; use the displayed **last location** timestamp. Battery API availability depends on Android WebView/browser support. Hosting, DNS, a stable URL, server credentials, backend operating costs, privacy policy and release signing are not included.

## Third-party materials

MapLibre GL JS is distributed under BSD-3-Clause; its license is available in `node_modules/maplibre-gl/LICENSE.txt` after installation. The supplied web bundle uses OpenFreeMap's style and tiles, with OpenStreetMap and OpenMapTiles attribution shown in the map. Review provider terms and usage policies before production use.

The Git repository stores the Gradle wrapper as base64 text and restores its jar on `npm install`; the same install step generates the MapLibre web assets from the pinned dependency. The GitHub copy is still the SQLite **preview**, not a Render-ready persistent backend. Do not deploy it as-is to a free web service: account and pairing data would be lost on spin-down.
