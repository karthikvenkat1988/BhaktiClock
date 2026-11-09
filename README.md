# Bhakti Clock

A small, self-contained Android app that plays the music you choose at set times,
every day, through whatever Bluetooth speaker the phone is connected to. No ads,
no account, no internet needed, no third-party libraries.

## What it does

- Any number of schedules, e.g. 6:00 AM Suprabhatam, 6:00 PM Vishnu Sahasranamam.
- Each schedule: time, days of the week, one or more audio files, volume, shuffle.
- Uses Android's alarm-clock timer, so it fires on the exact minute even when the phone is asleep.
- Waits up to 30 seconds for the Bluetooth speaker to reconnect before it starts.
- Sets the volume itself, so a phone left at low volume still plays properly.
- Re-arms all schedules after a reboot or a clock/time-zone change.
- A "Now playing" notification with a Stop button; "Play now" to test any schedule.

## Three ways to play

Each schedule can use one of:

- **Audio file**: plays MP3/M4A files from the phone. The most reliable choice.
- **Music app**: at the set time the app you choose (YouTube Music, Spotify, JioSaavn, ...) is opened
  and Play is pressed for you. Leave the app on the song or playlist you want.
- **Link**: opens a song or playlist link (for example YouTube Music: Share, Copy link) and presses Play.

For "Music app" and "Link", allow **Display over other apps** when the app asks (yellow banner), and set
the phone's screen lock to **None** or **Swipe**, otherwise Android keeps a locked phone from opening apps.
You can also set a "pause after N minutes" timer.

## Build it (pick one)

### Option A: GitHub, free, nothing to install

1. Create a free account at github.com and make a new **private** repository.
2. On the repository page, choose **Add file → Upload files** and drag in the
   *contents* of this folder (including the `.github` folder; on a Mac press
   Cmd+Shift+. in Finder to see hidden folders). Commit.
3. Open the **Actions** tab. A "Build APK" run starts automatically (about 3–5 minutes).
4. When it shows a green tick, open the run and download **BhaktiClock-apk** at the bottom.
   Unzip it to get `app-debug.apk`.

### Option B: Android Studio

Open this folder in Android Studio, let it sync, then **Build → Build APK(s)**.

## Install on the spare phone

1. Copy `app-debug.apk` to the phone (WhatsApp to yourself, Google Drive, or USB cable).
2. Tap it. Android will ask to allow installing from that app; allow it.
   (Play Protect may warn that the app is unknown. That's because you built it yourself; choose "Install anyway".)
3. Open **Bhakti Clock** and tap **Fix** on every yellow banner
   (alarms, battery optimisation, notifications).

## Set up a schedule

1. Copy your MP3s onto the phone (e.g. a `Music/Devotional` folder).
2. **+ Add schedule** → set 6:00 PM → choose the file → set volume → **Save**.
3. Tap **Play now** on the card to check it comes out of the speaker.

## Keeping it reliable

- Keep the phone on the charger. If it has a "protect battery" / 80% charge-limit option, turn it on.
- Pair the speaker once and keep it on. Many cheap speakers switch themselves off after
  10–30 minutes of silence; if yours does, use a speaker with an always-on setting, or put it
  on a smart plug.
- On Xiaomi/Redmi, Oppo, Vivo, Realme and Samsung phones, also look in the phone's own battery
  settings for "Autostart" / "Background activity" / "Never sleeping apps" and allow Bhakti Clock.
  These brands add extra app-killing on top of Android's.
- Keep the files on the phone. If you delete or move an MP3, choose it again in the schedule.
