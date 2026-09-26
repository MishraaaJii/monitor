# Time Ledger — App Usage Tracker & Blocker (Android)

A native Android app (Kotlin) that tracks how long you use every app on
your phone today, lets you set a daily time limit per app, and sends you
back to the Home screen the instant a limited app is opened after its
limit is reached. Limits reset automatically every day at midnight, or you
can reset one manually at any time. You can also turn the limiter off for
any app without losing the saved limit.

This is a real native app, not a website — that's required, because
monitoring other apps and closing them is only possible with Android's
`Usage Access` and `Accessibility Service` system permissions, which a web
page can never get.

## What you get in this folder

```
app/                      Android app source (Kotlin + XML layouts)
gradle/, gradlew*          Gradle wrapper (already generated — do not delete)
build.gradle.kts, settings.gradle.kts, gradle.properties
.github/workflows/build-apk.yml   Builds the APK for you in the cloud
```

## Step 1 — Get the code onto GitHub (no `git` command needed)

1. Go to github.com and click **New repository** (the "+" in the top right).
2. Name it anything, e.g. `time-ledger-app-blocker`. Keep it **Public** or
   **Private**, either works. Don't initialize it with a README (you're
   uploading your own files).
3. On the new repo's page, click **uploading an existing file**.
4. Drag the **entire contents of this folder** in (all the files and
   folders shown above — `app/`, `gradle/`, `gradlew`, `gradlew.bat`,
   `.github/`, the `.gitignore`, the `build.gradle.kts` files, etc.).
   Make sure the folder *structure* is preserved — most browsers preserve
   it automatically if you drag the whole unzipped folder in.
5. Commit the files (the green **Commit changes** button).

## Step 2 — Let GitHub build the APK for you

1. Open the **Actions** tab on your repository.
2. You should see a workflow run called **Build APK** already running
   (it starts automatically on every push). If you don't see it, click
   **Build APK** in the left sidebar, then **Run workflow**.
3. Wait for it to finish (a few minutes — the green checkmark).
4. Click into the finished run, scroll down to **Artifacts**, and download
   **time-ledger-app-blocker-debug**. It's a `.zip` containing one file:
   `app-debug.apk`.

If the run fails (red X), open it, expand the failed step, and paste me
the error — Android build tooling changes occasionally and I can patch the
Gradle config from the exact error message.

## Step 3 — Install it on your phone

1. Unzip the artifact you downloaded to get `app-debug.apk`, and get it
   onto your phone (email it to yourself, upload to Google Drive, or plug
   in via USB).
2. Tap the `.apk` file on your phone. Android will ask you to allow
   installing from this source the first time — allow it.
3. Open **Time Ledger**.

## Step 4 — Grant permissions (the app walks you through this)

The app cannot silently grant itself these — Android deliberately requires
a human to flip them on, as anti-malware protection. On first launch (and
any time via the **Permissions** link at the top of the app), you'll see:

- **Usage Access** — tap "Open Usage Access settings", find **Time
  Ledger** in the list, and turn it on.
- **Accessibility Service** — tap "Open Accessibility settings", find
  **Time Ledger**, and turn it on. This is what lets the app detect a
  limited app opening and send you home.
- **Background reliability (optional but recommended)** — exempts the app
  from battery optimization so some phones (Samsung, Xiaomi, etc.) don't
  kill the blocking service in the background.

Once granted, go back to the main screen — usage numbers and blocking
start working immediately, no restart needed.

## How it works

- **Tracking**: reads Android's own `UsageStatsManager` foreground-event
  log to compute exact time-in-foreground per app since midnight (or since
  a manual reset). Updates every 3 seconds while the app is open.
- **Limits**: tap any app in the list to set a daily limit in minutes, or
  to reset that app's counter for today, or to remove the limit entirely.
  The switch on each row turns the limiter on/off without deleting the
  saved limit number.
- **Blocking**: a background Accessibility Service notices the instant a
  limited app comes to the foreground (and also re-checks every 3 seconds
  while it stays open, in case the limit is crossed mid-session) and sends
  you to the Home screen if today's limit for it is already used up.
- **Daily reset**: automatic — "today's usage" is always computed from
  midnight onward, so every app's counter and any limit naturally clears
  itself at midnight with no scheduled job needed.
- **Manual reset**: tapping "Reset today's usage" on an app just moves its
  counting start-point to right now, so it reads 0 again immediately.
- **All data stays on your phone** — nothing is uploaded anywhere.

## Honest limitations

- Android has no public API for one app to force-kill another app's
  *process* outright. "Closing" a limited app means immediately returning
  you to the Home screen the moment it tries to come to the foreground —
  functionally the same result (you can't keep using it), and it's the
  same mechanism real screen-time/blocking apps use under the hood.
- Some phone brands (Samsung, Xiaomi, Huawei, OnePlus, etc.) apply extra
  background-process restrictions beyond stock Android. If blocking stops
  working after a while, check that battery optimization is off for the
  app (Step 4) and that the Accessibility Service still shows "On" in
  Android's Accessibility settings (some OEMs silently disable
  accessibility services after periods of inactivity).
- The list only shows apps that appear in the phone's app drawer (i.e.
  have a launcher icon) — background-only services aren't listed, since
  there'd be nothing meaningful to "open" and block.
