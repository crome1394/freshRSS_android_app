# Changelog

## 0.7.3

- **Play / Listen** opens an **Open with** chooser and prefers installed media apps (YouTube, Spotify, SoundCloud, Vimeo, Twitch, NewPipe when present)
- Declares Android package visibility so those apps appear in the chooser (not only the browser)
- Unit tests for media-host → package mapping

## 0.7.2

- Home-screen widget: unread count + last updated
- Widget refreshes on mark-read, category mark-read, and pull-to-refresh
- Downloadable APK naming: `FreshRSS_Personal_Client-<version>.apk`

## 0.7.1 and earlier

- Personal FreshRSS client (Fever + Google Reader APIs)
- Unread / all / read / starred scopes; video & audio filters; date filters
- Star, mark read, share, browser open; offline snapshot; theme & layout options
- Optional Tailscale shortcut; first-run setup wizard
