# Focuslot — RuStore release package (RU)

## App identity

- Name: **Focuslot**
- Package: `space.xproject.focuslot`
- Version: **1.0.0**
- Version code: **1**
- Category: Productivity / Тайм-менеджмент
- Price for 1.0: Free
- Support: `tumensev@xproject.space`
- Website: `https://xproject.space`
- Privacy policy: `https://xproject.space/focuslot/privacy`

## Short description

Минималистичный таймер фокус-сессий для работы и повседневных дел.

## Full description

**Focuslot** помогает делить работу, учёбу, чтение, домашние дела и другие занятия на понятные
временные слоты.

Создавайте свои активности, задавайте для каждой длительность фокуса и пауз и запускайте таймер
одним нажатием. Во время работы Focuslot не перегружает экран лишними элементами, а после завершения
слота предлагает перейти к перерыву, продолжить работу или добавить +5, +10 либо +15 минут.

Что есть в Focuslot:

- отдельные настройки времени для каждой активности;
- короткие и длинные паузы;
- свободный таймер для занятий без заранее заданной длительности;
- управление из уведомления;
- вибрация и звуковые сигналы;
- поддержка сценария с Samsung Galaxy Fit2 через системные уведомления;
- статистика по дням, неделям и месяцам;
- сравнение с предыдущим периодом;
- статистика по отдельным активностям;
- резервное копирование проектов, истории и настроек;
- светлая и тёмная темы;
- русский и английский языки.

Focuslot работает **офлайн**, не требует регистрации и не содержит рекламы или сторонней аналитики.

Focuslot создан на базе открытого проекта Pomodoro Tracker by drklo (MIT License) и существенно
переработан. Уведомление об исходном проекте и текст лицензии доступны внутри приложения.

## What's new — 1.0.0

Первый публичный выпуск Focuslot.

Таймер фокус-сессий и свободный таймер, пользовательские активности, паузы и продление слотов,
статистика, управление уведомлениями, резервное копирование и перенос данных из тестовой версии.

## Screenshot plan

Recommended portrait screenshots (phone, Russian UI):

1. **Main timer — running**
   - activity name visible;
   - clean countdown screen;
   - no debug/test labels.

2. **Choose activity**
   - several realistic activities with icons;
   - demonstrates that Focuslot is not tied to one fixed Pomodoro preset.

3. **Statistics — month**
   - month aggregation selected;
   - visible comparison with previous month;
   - at least several bars/data points.

4. **Statistics — activities**
   - per-activity bars;
   - one activity expanded to show its details.

5. **Settings / backup**
   - vibration options or backup/transfer block;
   - shows practical configuration without making settings look crowded.

Optional sixth screenshot:
- phase-end notification with +5/+10/+15 controls.

## Permissions / store declaration notes

### POST_NOTIFICATIONS
Purpose: show the running timer, phase-end alerts and timer controls.

### VIBRATE
Purpose: user-configurable vibration when a focus or break phase ends.

### FOREGROUND_SERVICE / FOREGROUND_SERVICE_SPECIAL_USE
Purpose: keep a user-started timer accurate and visible while Focuslot is in the background or the
screen is locked.

### REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
Purpose: optional user-initiated request to exclude Focuslot from aggressive battery optimization
that can interrupt long timers. Focuslot also works without granting this request, but background
reliability can depend on device firmware.

## Data declaration draft

Current Android build:

- no account;
- no INTERNET permission;
- no advertising SDK;
- no third-party analytics SDK;
- app data is stored locally;
- Focuslot itself does not transmit projects, history or settings to the developer;
- backup export is initiated by the user through Android's document picker;
- if the user chooses a cloud-storage provider in the system picker, that provider handles the file
  under its own terms.

Before submission, verify the final manifest and dependency tree again and make the RuStore
declaration match the actual signed 1.0.0 binary.

## Beta → store migration

The beta build and store build use different package names and can be installed side-by-side.

1. Beta: Settings → Переход на Focuslot 1.0 → Подготовить перенос.
2. Save `focuslot-transfer.json`.
3. Install Focuslot from RuStore / signed release APK.
4. Store build: Settings → Переход на Focuslot 1.0 → Перенести данные из тестовой версии.
5. Choose `focuslot-transfer.json`.
6. Verify activities, statistics and settings.
7. Only after verification, remove the beta build if desired.

## Release checklist

- [ ] Final launcher artwork integrated.
- [ ] Privacy page is live at the URL used by the app.
- [ ] Release keystore created and backed up in at least two safe places.
- [ ] GitHub release-signing secrets configured.
- [ ] Signed release workflow succeeds.
- [ ] APK signature fingerprint recorded.
- [ ] Fresh install test.
- [ ] Beta → store migration test.
- [ ] Timer start / pause / resume / stop.
- [ ] +5 / +10 / +15.
- [ ] Phase-end work and break flows.
- [ ] Fit2 notifications checked.
- [ ] Statistics day / week / month.
- [ ] Activity detail tap.
- [ ] Backup export + restore.
- [ ] RU and EN smoke test.
- [ ] Light / dark theme.
- [ ] Final screenshots captured from the signed build.
- [ ] RuStore metadata and data/permissions declarations completed.
