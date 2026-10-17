# TikStik privacy policy

Last updated: 2026-10-16

TikStik is an Android app that turns the stickers you see on TikTok into WhatsApp sticker packs. It is free, open source and developed by redrixDev. This policy explains what the app does with data, in plain words.

## What TikStik reads on your phone

- **TikTok's image cache.** When you tap Import, TikStik reads the image cache of the TikTok app on your phone to find the stickers you have seen or saved. Other images in that cache (for example profile pictures) are looked at only in memory, to tell them apart from stickers, and are discarded. On Android 11 and newer this happens through Android's wireless debugging, which TikStik turns on only while importing and turns off afterwards. On Android 8 to 10 it uses the storage permission.
- **Your stickers and packs.** The stickers you choose to keep, the packs you build from them and the copies prepared for WhatsApp are stored in the app's private storage on your phone.

Nothing is sent to TikStik's developer or to any server operated by the developer. There are none.

## What leaves your phone

- **Your Google Drive backup (optional).** If you sign in with Google, TikStik saves your packs and their stickers to a private folder of your own Google Drive (`appDataFolder`) that only TikStik can read, so you can get them back on a new phone. TikStik asks only for the `drive.appdata` permission and never sees the rest of your Drive. You can turn this off by signing out; the backup stays in your Drive until you delete it from your Google account's settings.
- **WhatsApp.** When you add a pack to WhatsApp or WhatsApp Business, those apps read the pack from TikStik through the standard sticker interface, on the phone.
- **Update check.** At most once a day, TikStik asks GitHub whether a newer version has been published, so it can offer to download it. The request carries no data about you; as with any website, GitHub sees the IP address. You can turn this off in Settings.

That is all. TikStik has no ads, no analytics, no crash reporting and no identifiers.

## Permissions

- Network access: to talk to your own phone over wireless debugging, to Google Drive if you sign in, and to GitHub for the update check.
- Install unknown apps (Android 8 and newer): only when you tap Update, to hand the downloaded APK to Android's installer, which asks you before installing anything.
- Notifications: to ask you for the pairing code during setup.
- Foreground service: only while pairing, so Android keeps TikStik running while you type the code in that notification.
- Change secure system settings (Android 11 and newer): apps cannot ask for it. TikStik grants it to itself once, over wireless debugging, when you connect it, and uses it only to turn wireless debugging on while importing and off afterwards.
- Storage (Android 8 to 10 only): to read TikTok's cache.
- Vibration: for the small haptic touches in the interface.
- Start after a restart and keep the processor awake (added by Android's WorkManager library): so the Drive backup and the preparation of WhatsApp copies can finish with the screen off and carry on after the phone restarts.

Android's libraries also add an internal permission that only TikStik itself can hold.

## Children

TikStik is not directed at children under 13. TikTok itself requires users to be 13 or older.

## Changes and contact

Changes to this policy are published in this repository with the date above. Questions: redrixdev@gmail.com.
