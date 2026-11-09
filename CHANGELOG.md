# Changelog

All notable changes to Foldman. Versions follow [semantic versioning](https://semver.org); downloads are on the [releases page](https://github.com/deveshrai/Foldman/releases).

## [1.9.7] Ferric Player, 2026-11-06

### Changed
- **The stack's well tapes grow another 10 percent.** The cassette plate takes a larger share of the stack's height, funded by the receiver and the CD player, and both well cassettes grow from about 207 dp to about 228 dp wide into the room below them.

## [1.9.6] Ferric Player, 2026-11-06

### Changed
- **Expand leaves the landscape body.** The expand cap and the door alone view it opened are gone: the landscape body already is the door forward view, and the extra step broke when the phone rotated to portrait. The portrait deck's and the stack's expanded tape are unchanged.

## [1.9.5] Ferric Player, 2026-11-06

### Changed
- **Expand in the landscape body opens the door, not the loose tape.** The expand cap now fills the screen with the player's door alone: the cassette large behind the door glass, the print row along the door's bottom edge, and only REW, PLAY or PAUSE and FF as piano keys under it, with a contract cap in the door's corner to come back. The portrait deck's expanded tape is unchanged.

## [1.9.4] Ferric Player, 2026-11-06

### Changed
- **The landscape body takes the mockup's shape, full screen.** The piano keys return to a row along the bottom edge, the pod's keys become the mockup's rectangular pod keys in two rows with their state lines, the track readout and seek hairline move to a thin bezel strip at the bottom edge, the cassette is seated so its handwritten title sits fully inside the door glass, and the body hides the system bars while it is shown.

## [1.9.3] Ferric Player, 2026-11-06

### Changed
- **The landscape door shows the tape the way a real door does.** The cassette behind the door glass is now rendered wider than the window, so the glass crops it to the label and the reels: the artwork, the handwritten title and the tape packs fill the window, and the shell's bottom half stays hidden behind the door.

## [1.9.2] Ferric Player, 2026-11-06

### Changed
- **The landscape cassette stands taller.** The five transport keys moved from the body's bottom edge into the side pod, and the door took the freed height: the tape now fills the door at a real cassette's proportions, centered in the space beside the pod, roughly half again as large as before on a cover screen.

## [1.9.1] Ferric Player, 2026-11-04

### Fixed
- **The landscape cassette fills its door.** The tape now takes the full height of the door area at a real cassette's proportions, with the door fitted around it, instead of sitting small in the middle of a wide dark panel.

## [1.9.0] Ferric Player, 2026-11-04

### Added
- **A personal player body for the landscape deck.** Sideways, the deck now fills the whole screen as a slim player in the style of a 90s portable: the cassette behind its door window with the body print under the glass, source lamps, volume, EQ, shuffle, repeat, source, settings and sleep on a side pod, a counter and pilot lamp up top, and five piano keys seated along the bottom edge.
- **Keys with travel and a tick.** The piano keys sink as they are pressed, with a short haptic tick, on the landscape body and everywhere the shared keys serve.
- **Hold REW or FF to wind.** On a source that supports seeking, holding a wind key steps through the song, ten seconds at a time at first and up to thirty the longer the hold; letting go stops the wind. A plain tap still latches the mechanical wind, and on a source that cannot seek a hold does nothing at all.

## [0.1.2] — 2026-10-21

### Changed
- **Foldman's settings no longer go into Android backups or device transfers.** They include your Apple Music user token, which now stays on the phone. When you move to a new phone, sign in to Apple Music again there.

## [0.1.1] — 2026-10-21

### Fixed
- **Apple Music showed the next song instead of the one playing.** MusicKit lists only the songs *after* the current one in its queue, and Foldman read the playing song's position from that list. The title, artist, cassette label and cover now come from the song that's actually playing, and the queue and Up next start from it.
- **"Track 0 of N"** for Apple Music now shows the right position and count, including songs already played.

### Changed
- Apple Music playlists always play in order: Foldman explicitly turns shuffle off when it loads one, and turns it off again if Apple's player ever reports it on.

## [0.1.0] — 2026-10-21

The first release.

### Added
- **Three postures.** On the cover screen, an edge-to-edge upright portable player with the tape on end behind a window and the keys down a silver side strip. Unfolded, the same player laid down as a deck between a library panel and a now-playing panel. Half-open on a table (Flex mode), the deck on the top half and the library on the bottom.
- **The mechanism.** Latching PLAY, PAUSE, STOP·EJECT, FF and REW keys and AMS skip buttons. Reels turn at tape speed ÷ radius with spin-up and coast-down. A mechanical tape counter, VU needles that follow the real audio on local files, an OPERATE LED, synthesised key clicks and haptics, and CLICK / HAPTIC switches.
- **Local folders.** Media3 playback in a background service with media controls. Album art comes from embedded art or a cover image in the folder, and each sub-folder is listed as an album.
- **Apple Music via MusicKit** (optional AARs). Sign-in, library playlist browsing through the Apple Music API, and in-app streaming in a separate, crash-isolated process that resumes the song after a crash.
- **YT Music and Apple Music via media sessions.** Transport, artwork and queue, using notification access.
- Adaptive launcher icon with a themed (monochrome) layer.

[0.1.2]: https://github.com/deveshrai/Foldman/releases/tag/v0.1.2
[0.1.1]: https://github.com/deveshrai/Foldman/releases/tag/v0.1.1
[0.1.0]: https://github.com/deveshrai/Foldman/releases/tag/v0.1.0
