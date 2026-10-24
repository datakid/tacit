# Known limits

These come from Android itself. Tacit doesn't work around them with root, hacks or anti-features.

- **Settings**: Tacit finds Settings *screens*. It can't find individual toggles inside them; that search index needs system privileges. Vendor skins (Samsung, Xiaomi) expose fewer screens.
- **Files**: Android/data, Android/obb, other apps' private storage and cloud-only files can't be searched without root. Full search needs "All files access". Without it you can grant individual folders, but Android won't let you grant the storage root or the Download root that way.
- **Files, chosen folders**: scanning is slower than with All files access, because Android answers one query per folder.
- **Clipboard lock**: only available when the phone has a screen lock set.
- **Clipboard**: Android 10+ only lets the app on screen read the clipboard. Tacit saves what's on the clipboard each time you open it; copies made in between are missed.
- **Updates**: installing, updating or removing an app reaches Tacit as a burst of events. Tacit waits 400 ms, reloads once, and reloads only what changed. The new app can take about half a second to show up.
- **App shortcuts**: only available while Tacit is the default home app.
- **No internet**: no currency rates, no search suggestions, no update checks.
- **WhatsApp**: going straight to a chat needs WhatsApp's contact entry or a number with a country code (set a default country code in Settings).
- **First install over a beta**: the stable release is signed with the owner's key, so a phone with a beta (debug-signed) build has to uninstall it first. Settings are not carried over; export them from Settings before uninstalling.
- **Narrow screens**: contact buttons that don't leave the name 140 dp move into a More button. On a 360 dp phone with WhatsApp installed, Call stays and Message and WhatsApp go into More.
