# Publishing Bhakti Clock on Google Play – step by step

Everything that can be prepared in advance is ready: the app targets Android 16 (required for new
apps), the icon, feature graphic, listing text and privacy policy are in this folder, and the
GitHub build makes the signed bundle that Google Play needs.

The steps below are the ones only you can do, because they need your Google account, payment
and identity.

---

## Step 1 – Turn on the signed Play bundle (5 minutes)

You received two private files: `bhakticlock-upload.jks` (your upload key) and
`BhaktiClock-signing-key-KEEP-PRIVATE.txt`. Save both somewhere safe and private
(for example a private Google Drive folder). Never put them in this public repository.

1. Open GitHub → **BhaktiClock** repo → **Settings** → **Secrets and variables** → **Actions**.
2. Click **New repository secret** twice and add the two secrets exactly as written in the text file:
   `KEYSTORE_PASSWORD` and `KEYSTORE_BASE64`.
3. Open the **Actions** tab → **Build APK** → **Run workflow**.
4. When it finishes, download **BhaktiClock-play-bundle** from the bottom of the run and unzip it.
   You get `app-release.aab`. This is the file you upload to Google Play.

## Step 2 – Create your Google Play developer account

1. Go to <https://play.google.com/console/signup> and choose a **Personal** account.
2. Pay the one-time registration fee and complete Google's identity verification.
   Google may take a few days to verify you.

## Step 3 – Create the app

Play Console → **Create app**:
- App name: **Bhakti Clock: Bhajan Scheduler**
- Default language: English
- App or game: **App** · Free or paid: **Free**
- Accept the declarations.

## Step 4 – Fill in "App content" (Policy → App content)

| Section | What to choose |
|---|---|
| Privacy policy | `https://github.com/karthikvenkat1988/BhaktiClock/blob/main/PRIVACY.md` |
| App access | All functionality is available without special access |
| Ads | No, my app does not contain ads |
| Content rating | Fill the questionnaire: category **Utility / Productivity / Other**, answer **No** to everything. Rated for everyone. |
| Target audience | 18 and over (simplest; avoids extra rules for children's apps) |
| Data safety | **No** data collected, **no** data shared. |
| Government app / Financial / Health | No |
| **Foreground service** | Type: **Media playback**. Describe it like this: "At the time the user scheduled, the app plays the audio the user chose (for example a morning prayer) through their speaker. If interrupted, the user's scheduled prayer music stops mid-way." Upload a short screen recording: open the app, tap **Play now** on a schedule, and show the music playing with the Now Playing notification. |
| **Full-screen intent** | Choose **Alarm** – the app wakes the phone at a user-set time to play music, like an alarm clock. |

If Play asks about **exact alarms**, explain that it is an alarm-style app: the user sets a time and
music must start on that exact minute.

## Step 5 – Store listing (Grow → Store presence → Main store listing)

Copy the text from `listing.md`, then upload:
- App icon: `play_store_icon_512.png`
- Feature graphic: `feature_graphic_1024x500.png`
- **Phone screenshots (at least 2)**: on the phone, open Bhakti Clock and take screenshots of
  the home screen with a schedule or two, and of the editor (tap a schedule). Press Power + Volume Down.

## Step 6 – Closed test with 12 testers for 14 days (required for new personal accounts)

Google won't let a new personal account publish publicly until the app has had a closed test with
**at least 12 testers who stay opted in for 14 days in a row**.

1. Play Console → **Test and release → Testing → Closed testing** → create a track.
2. **Testers**: add a list of at least 12 Gmail addresses (family and friends are fine; 15–20 is
   safer in case someone drops out).
3. **Create release** → upload `app-release.aab` → accept **Play App Signing** when asked → save → roll out.
4. Send testers the **opt-in link** shown on the Testers tab. Each tester must open it, tap
   **Become a tester**, then install the app from Play.
5. Ask everyone to keep the app installed and open it now and then for the full 14 days.

## Step 7 – Apply for production

After 14 days the Dashboard shows **Apply for production**. Answer the short questions about your
test (what testers did, what feedback you got), submit, and once approved, create a
**Production** release with the same bundle. Google's review usually takes a few days.

## Later: updating the app

Every update needs a higher version number. Ask Claude to bump `versionCode` in
`app/build.gradle`, then download the new bundle from GitHub and upload it as a new release.

## Note: the app's ID changed

To publish, the app ID is now `com.karthikvenkat.bhakticlock`. If you installed an earlier test
version, uninstall the old **Bhakti Clock** first, then install the new one. You'll need to set
up your schedules again once.
