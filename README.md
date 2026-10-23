# Focuslot

Focuslot is a minimalist Android focus timer for people who want to divide work and everyday tasks
into clear time slots without accounts, ads, cloud sync or unnecessary screens.

- Android 10+
- Kotlin + Jetpack Compose
- offline-first: no Internet permission
- Russian and English UI
- per-activity focus/break presets
- free timer for open-ended activities
- foreground timer with notification controls
- Galaxy Fit2 / Samsung notification workflow
- day/week/month statistics and per-activity breakdown
- backup/restore of activities, history and global settings

## Status

The application is being prepared for its first public release as **Focuslot 1.0.0**.
The store package is:

`space.xproject.focuslot`

A separate compatibility build is used during beta testing so existing tester data can be preserved.
The app contains a guided transfer flow: the beta build exports `focuslot-transfer.json`, and the
store build restores that file.

## Privacy

Focuslot stores its data locally on the device. The current Android application does not request
Internet access and does not include advertising or third-party analytics.

The privacy-policy source is in
[docs/FOCUSLOT_PRIVACY.md](docs/FOCUSLOT_PRIVACY.md). The public release links to:

https://xproject.space/focuslot/privacy

## Open-source attribution

Focuslot is based on the open-source
[Pomodoro Tracker](https://github.com/Dr-klo/pomodoro-tracker) project by **drklo** and contains
substantial portions of that codebase.

The original project is licensed under the MIT License. Its copyright notice and the MIT text are
preserved in [LICENSE](LICENSE) and are also available inside Focuslot under **Settings → About →
Open-source acknowledgements**.

Focuslot's product name, UI direction and subsequent modifications are maintained in this fork.

## Main features

### Timer

- circular focus timer with pause/resume and stop
- custom activities with their own focus and break durations
- short and long breaks
- +5 / +10 / +15 minute extension
- optional autostart
- configurable sound and vibration patterns
- always-on-display option
- logical end-of-day boundary
- battery-optimization guidance for reliable background operation

### Notifications and wearable use

The running timer is kept by an Android foreground service. The system chronometer renders the
countdown without the app reposting the notification every second. This avoids unnecessary
notification churn and is important for Samsung/Galaxy Fit2 mirroring.

Phase-end alerts provide actions for moving to the next phase, extending the current one or
stopping the activity.

### Reports

- focus time and completed focus sessions
- week journal
- daily-goal calendar
- day/week/month aggregation
- comparison with the previous matching period
- time by activity
- distribution chart
- tap-to-expand activity details
- actual-time statistics for the free timer

### Backup and migration

A Focuslot backup contains:

- activities and their presets
- the retained activity history
- sound/vibration/background settings
- theme and language
- end-of-day and autostart settings

Older backup files that predate settings backup remain supported.

For beta → store migration:

1. In the beta build, open **Settings → Move to Focuslot 1.0 → Prepare transfer**.
2. Save `focuslot-transfer.json`.
3. Install the store build (`space.xproject.focuslot`).
4. Open **Settings → Move to Focuslot 1.0 → Move data from the test version**.
5. Select the transfer file and confirm restore.

The two applications may coexist during the transfer because they use different Android package
names.

## Build

Requirements: JDK 21 and an Android SDK capable of compiling API 37.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:detekt
./gradlew :app:lintDebug
```

### Release signing

Release signing is read from `~/.pomodoro/keystore.properties` (or the project-root
`keystore.properties` for a local checkout):

```properties
storeFile=/absolute/path/to/focuslot-release.jks
storePassword=...
keyAlias=focuslot
keyPassword=...
```

The keystore itself and all passwords must stay outside the repository. The first public release
must keep the same signing key for all later updates and, ideally, for every Android store.

The release APK is built with:

```bash
./gradlew :app:assembleRelease
```

## Project structure

```
app/src/main/java/com/drklo/pomodoro/
├─ data/        Room, DataStore, repositories, backup
├─ timer/       timer engines, foreground services, effects
├─ ui/main/     main timer and activity selection
├─ ui/reports/  statistics and charts
├─ ui/settings/ settings and activity editor
└─ util/        locale and Android helpers
```

The Kotlin namespace is intentionally still `com.drklo.pomodoro`; changing it is not required for
Android distribution. The public application ID is `space.xproject.focuslot`.

## License

See [LICENSE](LICENSE) for the preserved MIT license and original copyright notice.
