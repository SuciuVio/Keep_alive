# Keep-Alive Android App - Down Server Alerts Design

## Context

The Android Keep-Alive app monitors a persisted list of URLs by running a foreground service that sends HTTP GET requests every 30 seconds while monitoring is active. The app shows live ping logs and keeps the service alive in the background.

This design adds down-server alerts without changing the no-login requirement or the manual start/stop behavior.

## Alert Rule

The app alerts only when monitoring is active. If the foreground ping service is stopped, no ping checks run and no down-server alerts are produced.

For each URL, `PingService` keeps an in-memory consecutive failure counter:

- A successful ping resets that URL counter to `0`.
- A failed ping increments that URL counter by `1`.
- When a URL reaches `3` consecutive failures, the app sends a separate Android notification warning that the server may be down.

## Anti-Spam Behavior

Each URL also tracks whether a down alert has already been sent for the current failure episode.

After the app sends an alert for a URL, it does not send another alert for that same URL on every later failed ping. The alert can be sent again only after that URL has at least one successful ping, which resets the episode, and then fails 3 consecutive times again.

## Notification Behavior

The existing foreground service notification remains ongoing and low-importance.

Down-server alerts use a separate notification flow:

- Title: `Server posibil down`
- Text: `<URL> - 3 ping-uri consecutive au esuat`
- Tap action: opens `MainActivity`
- Notification ID: stable per URL, so alerts for different URLs do not overwrite each other accidentally
- Alerts are emitted only from `PingService` while it is running

On Android 13+, alerts require the existing `POST_NOTIFICATIONS` runtime permission. If the permission is not granted, the failure still appears in the in-app log, but Android will not show the alert notification.

## Data Flow

1. `PingService` reads the current URL list from `UrlRepository` every 30 seconds.
2. For each URL, it performs an HTTP GET.
3. It broadcasts every success/failure log line to `MainActivity`.
4. It updates the per-URL failure counter.
5. When a URL reaches 3 consecutive failures and has not already alerted in this failure episode, it asks `NotificationHelper` to show a down-server notification.
6. On success, it resets the failure counter and alert flag for that URL.

## Scope

This design does not add login, cloud sync, automatic boot startup, or user-configurable thresholds. The threshold is fixed at 3 consecutive failures for the first version.

## Verification

Build verification should include:

- `gradlew assembleDebug` succeeds.
- A URL that fails once or twice only appears in the log and does not trigger a down alert.
- A URL that fails 3 times consecutively triggers one alert.
- Continued failures after the alert do not create repeated alerts.
- A later success resets the episode, allowing a future 3-failure sequence to alert again.
- Stopping the service stops ping checks and prevents new alerts.
