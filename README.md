# Hermes Bridge

Android companion app that exposes a **loopback-only** HTTP API for a Hermes Agent (or another local process) running in Termux. It uses Android `AccessibilityService` for explicitly enabled UI automation and `NotificationListenerService` for notification access.

> **Security notice:** This app can read visible UI content and, when notification access is granted, notification content. Use it only on a device you control. Keep the bearer token private, review the allowlisted packages, and disable the services when they are not needed. The API binds to `127.0.0.1` and is not intended to be exposed to a LAN or the public internet.

## Build APK with GitHub Actions

1. Push the repository to GitHub.
2. Open **Actions** and run the **Android CI** workflow, or push to `main`.
3. Open the completed workflow run and download the `hermes-bridge-debug` artifact.
4. Extract `app-debug.apk` and install it on your Android device.

The workflow uses JDK 17 and Gradle 8.10.2. No signing key is required for the debug APK. A local Gradle installation can also build it with `gradle assembleDebug`.

## Enable on Android

1. Open Android Settings -> Apps -> Hermes Bridge -> (menu) -> **Allow restricted settings**, if Android blocks enabling Accessibility.
2. Open Hermes Bridge -> **Accessibility settings** -> enable the service.
3. Open **Notification access** and enable Hermes Bridge only if notification reading is required.
4. Tap **Start bridge**. Grant notification permission if Android requests it.
5. Tap **Copy API token** and keep the token private.
6. Review the allowed package list before using automation.

## API (from Termux)

Set the token copied from the app:

```sh
TOKEN='<paste-token-here>'
H="Authorization: Bearer $TOKEN"
B='http://127.0.0.1:8765'

curl -s -H "$H" "$B/status"
curl -s -H "$H" "$B/screen"
curl -s -H "$H" "$B/notifications"
curl -s -X POST -H "$H" -H 'Content-Type: application/json' \
  -d '{"text":"Send"}' "$B/click"
curl -s -X POST -H "$H" -H 'Content-Type: application/json' \
  -d '{"text":"สวัสดีครับ"}' "$B/type"
curl -s -X POST -H "$H" -H 'Content-Type: application/json' \
  -d '{"x":540,"y":1800}' "$B/tap"
curl -s -X POST -H "$H" -H 'Content-Type: application/json' \
  -d '{"x1":500,"y1":1500,"x2":500,"y2":500,"ms":300}' "$B/swipe"
curl -s -X POST -H "$H" -H 'Content-Type: application/json' \
  -d '{"action":"back"}' "$B/global"
curl -s -X POST -H "$H" "$B/stop"
curl -s -H "$H" "$B/log"
```

Available routes: `GET /status`, `GET /screen`, `GET /notifications`, `GET /log`; `POST /tap`, `/click`, `/type`, `/swipe`, `/global`, `/launch`, `/stop`.

## Safety controls

- Server binds to `127.0.0.1:8765` only.
- Every request requires a random bearer token generated and stored locally by the app.
- Screen interaction is restricted to packages in the app's allowlist; `/launch` also checks the allowlist.
- Password nodes are excluded from both text and content-description extraction; typing into password fields is blocked.
- Request bodies are limited to 64 KiB, concurrent clients are bounded, and actions are rate-limited.
- The kill switch blocks automation until manually resumed in the app; the foreground notification also provides kill/stop controls.

## Limitations

- This is an early development build. It has not yet been verified on a physical device.
- Android may restrict launching another app from a background service depending on OS version and device vendor.
- Accessibility and notification access are powerful permissions. Grant them only when needed.
- No per-contact approval, screenshot endpoint, automated test suite, or release-signing setup is included yet.
