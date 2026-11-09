# Ferric Player: User Guide and Known Issues

**Ferric Player, by Protech Prime Consulting**
Version covered: 1.9.7

Ferric Player is a skeuomorphic remote control for the music apps already on your phone. It puts YouTube Music and Apple Music behind one cassette deck face, and on foldables and tablets it becomes a full component stereo stack with two cassette wells, one for each library. It also plays local audio files from a folder you choose.

Read this before the comments section of your own brain starts writing tickets: Ferric Player streams nothing itself. It is a conductor, not an orchestra. The source apps do the playing; Ferric does the showing and the controlling.

## Your mileage may vary (read this part twice)

This app was built for, and tested on, exactly one device: a Pixel 10 Pro Fold. It uses only standard Android interfaces, and the design is size driven rather than tuned to that one phone, so it should behave on other hardware. "Should" is doing honest work in that sentence. Other foldables, other manufacturers' Android skins, and other versions of the source apps are untested combinations. Everything in the Known Issues section below is a place where your mileage may genuinely vary. There are no ads, no analytics, and no data collection. If something misbehaves, it is a compatibility quirk, not a business model.

## Requirements

- Android 11 or newer.
- For streaming sources: the official YouTube Music app and/or the official Apple Music app, installed and signed in with your own subscriptions. Ferric Player does not provide, replace, or unlock either service.
- For local playback: audio files in a folder you grant access to.
- One permission that matters: notification access. See the next section, because the install stands or falls on it.

## Installing

1. Download the APK from the Releases page.
2. Your browser will ask to allow installs from it. Allow, then open the APK and install.
3. Updates work the same way: download the newer APK and install it over the top. Your settings carry over. (Installing an older version over a newer one is not possible without uninstalling first, which wipes settings.)

## The one setup that matters: notification access

Ferric controls other apps' playback through their media sessions, and Android only lets an app see those sessions if it holds notification access. That is the entire reason for the grant. Ferric uses it to watch media session notifications only. It does not read your messages, email, or notification content for any other purpose, and nothing leaves your device.

Setup, in order:

1. Open Ferric Player and go to the source shelf (the TAPES or SOURCE control).
2. Choose YouTube Music or Apple Music and tap GRANT ACCESS. You will likely be told access is denied the first time. That is expected on Android 13 and later for sideloaded apps.
3. Open Settings, then Apps, then Ferric Player. Open the menu in the top right corner of the App info screen and tap **Allow restricted settings**. (On foldables, expand the App info pane to full screen first; the menu only appears there.)
4. Go back to Ferric and grant again. Android will open the Notification access list. Turn Ferric Player on.
5. Start playing something in YouTube Music or Apple Music. The deck should pick it up within a few seconds.

If the decks never see a source after this, see the battery section below before anything else.

## Battery managers: the number one cause of "it just stops working"

Many manufacturers kill background services aggressively, and Ferric's session watching is a background service. If control works while the app is open but dies later, your phone is putting Ferric to sleep.

- **Samsung (One UI):** Settings, Battery, Background usage limits, Never sleeping apps: add Ferric Player. Also set Ferric's battery usage to Unrestricted in its App info.
- **Pixel:** Settings, Apps, Ferric Player, App battery usage: Unrestricted.
- **Xiaomi, Oppo, OnePlus, Honor, others:** find the battery or autostart settings for the app and allow it to run unrestricted. Every maker names it differently; the intent is the same.
- Local file playback is unaffected by all of this. It is only the streaming remote features that depend on the listener staying alive.

## What the decks do

- **Phone, portrait:** a cassette deck with meters, a full cassette, and a Now Playing panel. Expand the cassette for a full screen tape.
- **Phone, landscape:** a full personal player in the style of the great 90s portables. A solid door with a smoked window fills most of the body, and behind the glass sits a cassette larger than the window itself, so what you see is the label band, the handwritten title, and both reels turning, the way a tape actually looks inside a real machine. The five piano keys run along the bottom edge and travel when pressed, with a light haptic tick if haptics are on in Settings. Hold FF or REW down to wind, where the playing app's session offers seeking. The control pod beside the door carries EQ, shuffle, and repeat, plus SOURCE, SETTINGS, and SLEEP, and the track readout and seek line ride in a thin strip along the bottom edge. The body runs full screen, with the phone's status and navigation bars hidden, the same as the other full screen views. There is no expand button in landscape: the body already is the full door view. The portrait deck and the stack keep their own expand.
- **Foldable, cover screen:** the portrait deck.
- **Foldable, half open (tabletop):** the deck rides above the hinge.
- **Foldable, unfolded, and tablets:** the component stack. A receiver, a dual cassette deck, a CD player, and an amplifier.
- **The dual wells:** the left well holds YouTube Music, the right well holds Apple Music. Each well keeps that library's last played track, label, artwork, and place. Tap the parked well and playback hands over to that library. A well showing a blank labeled tape means that app has not reported a track yet this session; tap it anyway, or open that app once and play something.
- **The CD player** wears the current album's artwork on its disc, and its progress line is where the stack shows track position.
- **The amplifier's slider bank is a display**, a graphic EQ curve set as sculpture, in the tradition of every receiver whose sliders were more about the look than the sound. The working equalizer is the EQ button, below.

## Controls and what they depend on

Ferric's rule is that a control only lights up when the current source actually offers the function. Features differ between YouTube Music and Apple Music, and between versions of those apps. If a control is dim, the source is not offering it. Nothing in the deck pretends otherwise.

- **Play, pause, next, previous:** always available for a live session.
- **Seek:** the progress bars (portrait panel, landscape line, CD player line) are real scrubbers when the session offers seeking. Tap to jump, drag to scrub. Where a session does not offer it, the bars are readouts only.
- **Shuffle and repeat:** real toggles, driven by the source's own state, shown where the source supports them.
- **Volume:** the stack's big knob and the sliders elsewhere control system media volume.
- **EQ button:** opens an equalizer app of your choosing on your phone (any system wide equalizer, for example Poweramp Equalizer). First tap asks you to choose; the deck remembers your pick. Long press the EQ button to choose a different one. Ferric cannot equalize another app's audio itself; no app can. Anyone who claims otherwise is showing you sliders that do nothing.
- **Sleep timer (stack receiver):** tap SLEEP to cycle 15, 30, 45, 60 minutes, off. The readout counts down, and the deck pauses itself at zero. Long press cancels.
- **Orientation lock:** in Settings (the gear), choose AUTO, PORTRAIT, or LANDSCAPE. AUTO is standard behavior.
- **Meters, spectrum, and LED ladders:** for local files these read the real audio. For streamed sources, Android shares no audio with other apps, so these displays are synthesized from the playback state. They are decoration there, honest decoration, and they claim nothing else.

## Bluetooth and lock screen

Ferric keeps its own media session as a bridge, so headset and earbud controls (play, pause, next, previous) reach the live deck no matter which library is playing. A side effect: your lock screen and system media panel will show Ferric Player as the controller, wearing the current track's title and artwork. That is the bridge doing its job.

## Known issues and issue points

Everything here is stated plainly because surprises are worse than fine print.

1. **A source app that has not been opened in a while may not show a session.** Open the app, play a track, and the deck will pick it up. Apple Music on Android is the most prone to dropping its session when idle; the well will hold its last tape in the meantime.
2. **Feature availability moves.** YouTube Music and Apple Music updates can add or remove the session features (seek, shuffle, repeat) that the deck mirrors. A control that worked last month can go dim after a source app update. That is the source's call, not a Ferric bug, and it usually comes back the same way it left.
3. **Battery managers are the top support issue** across all makers. See the battery section. If Ferric works in the morning and stops by afternoon, this is almost certainly why.
4. **Restricted settings on Android 13 and later.** If the notification access toggle is greyed out, you missed the Allow restricted settings step in the install section.
5. **Other foldables are untested.** The app reads hinge and posture through the standard AndroidX WindowManager interfaces used by all book style foldables, and falls back to window size where no hinge reports. Proportions on squarer unfolded screens (for example Samsung's) will differ from the Pixel Fold's, by design rather than by defect, but nobody on this project has seen them.
6. **Tabletop behavior depends on the hinge reporting a half opened state.** Most book style foldables do. If yours does not, you get the unfolded layouts instead, which is a cosmetic difference, not a failure.
7. **No automatic updates.** GitHub releases are manual. Check the Releases page when you feel like it. There is no nag; the deck respects you.
8. **Two phones, two setups.** Notification access and the battery exclusions are per device. A new phone means the five setup steps again.
9. **Work profiles and multiple user accounts are untested.** Sessions from apps inside a work profile may not be visible to a Ferric installed outside it, and the reverse.
10. **The tape counter is a counter of elapsed seconds**, shown modulo 1000, in the style of the mechanical counters it salutes. It does not count reel revolutions, because there are no reels. The reels, you will note, turn anyway, driven by honest tape physics from the playback position.
11. **Artwork quality belongs to the source.** If a label or disc looks soft, the source app supplied a small image. Ferric shows what it is given.
12. **Ferric is a remote.** If a source app itself cannot play (no connection, subscription lapsed, regional restriction), the deck cannot fix that. It will show you the stopped state faithfully.

## Troubleshooting quick table

- **A well is blank or a source never appears:** open the source app and play a track; then check notification access is still on; then check the battery exclusion.
- **Controls are dim:** the current source does not offer that feature for this session. Try the other source; compare.
- **Earbud or car controls stop answering:** make sure playback was last started or touched from Ferric so the bridge session is the active one; open Ferric once and press play.
- **The stack does not appear on a tablet:** the unfolded layout needs a window at least 600 dp wide in an upright posture. A small tablet in a narrow split screen window gets the phone decks instead.
- **Everything worked yesterday:** a source app updated, or a battery manager reasserted itself. Notification access first, battery exclusion second; those two cover the overwhelming majority of reports we would expect.

## Privacy

Ferric Player collects nothing, phones home nowhere, contains no advertising and no analytics, and needs no account. Notification access is used for media session control as described above. Your equalizer choice and settings are stored on your device only.

## Credits and trademarks

Ferric Player is built on the open source Foldman project (Apache License 2.0) and is released by Protech Prime Consulting. YouTube Music is a trademark of Google LLC. Apple Music is a trademark of Apple Inc. This project is not affiliated with, endorsed by, or sponsored by Google or Apple. The deck's design is an original work drawn in code; no manufacturer's industrial design, artwork, or brand marks are reproduced. Any resemblance to the great machines of the cassette era is a tribute, and is intended as one.

**Ferric Player, by Protech Prime Consulting.** Listen to the music and enjoy.
