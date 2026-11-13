# Box art and metadata sources

**Status:** research for your decision. Two no-account sources are built in 0.2.0; the rest wait for you to choose and create accounts.

## What's built now (no accounts needed)

| Order | Source | How it matches | Notes |
|---|---|---|---|
| 1 | **Your art folder** | Image file named exactly like the ROM (`Game (USA).png` for `Game (USA).nds`); an image inside a folder named after the system wins | Works with media you already have, e.g. ES-DE's `downloaded_media` |
| 2 | **libretro thumbnails** (`thumbnails.libretro.com`) | Exact No-Intro / Redump name per system collection; `&*/:`<>?\|"` become `_`; a second try drops `[tags]`, `(Rev n)`, `(vX.Y)`, `(Disc n)` | Free, no key. Your files already use these names. Doesn't cover Switch or Steam; Wii U and Xbox 360 coverage is unconfirmed until your report shows it |

Art is cached on the device. Misses are retried after a week, or immediately with **System → Box art → Fetch missing art now**. The System report shows the coverage per system.

## Services that need an account or key

| Service | What you need | Matching | Art it offers | Fit for Wii-S |
|---|---|---|---|---|
| **ScreenScraper** | Free user account, plus developer credentials for the app, issued to registered members on request ([RomM docs](https://docs.romm.app/5.1.0/getting-started/metadata-providers/)) | ROM hash (CRC/MD5/SHA1) and name | Box front/back/3D, screenshots, logos (wheels), videos, marquees | Best retro coverage and hash matching. Request limits are per user and modest; it's community-run |
| **SteamGridDB** | Free account, API key from Profile → Preferences → API ([guide](https://github.com/sonic2kk/steamtinkerlaunch/wiki/SteamGridDB)) | Name search or Steam app id | Grids (covers), heroes (wide banners), logos, icons; community-made, consistent styles | Best for **GameNative / Steam** (look up by app id) and for stylised, matching art across systems |
| **IGDB** (Twitch) | Twitch developer app: client id + secret ([RomM key guide](https://docs.romm.app/3.9.0/Getting-Started/Generate-API-Keys/)) | Name search | Covers, artworks, screenshots | Strong for modern systems (Switch, Wii U, 360). Commercial terms are unclear ([Twitch forum](https://discuss.dev.twitch.com/t/commercial-use-of-igdb-api/23567)) |
| **Hasheous** | None (public, open source, AGPL) | ROM hash against No-Intro / Redump / TOSEC DATs, then IGDB data through its proxy ([project](https://awesome.ecosyste.ms/projects/github.com%2Fgaseous-project%2Fhasheous)) | IGDB covers | Hash matching without your own Twitch keys. Endpoint details still need checking before I build it |
| **TheGamesDB** | API key requested on their forum; monthly request allowance ([wrapper notes](https://www.nuget.org/packages/TheGamesDBApiWrapper)) | Name search | Box front/back, fanart, banners, clear logos | Decent coverage; key process is manual |
| **RetroAchievements** | Free account, web API key ([API docs](https://api-docs.retroachievements.org/v1/get-game-extended.html)) | Needs the game's RA id (RA's own hashing) | Box art, title screen, in-game shot, icon | Worth adding later with achievements; not a primary art source |
| **MobyGames** | API key from your profile; free tier is non-commercial only ([API docs](https://mobygames.com/info/api)) | Name search | Covers, screenshots | Fine for personal use; slower rate limits |
| **LaunchBox Games DB** | None (daily `Metadata.zip`); no official API ([forum](https://forums.launchbox-app.com/topic/52709-launchbox-db-api/)) | Exact name in the XML | Metadata; image access isn't clearly public | Metadata only, large download; skip for art |

## Recommendation for your choice

1. **SteamGridDB** next: one free key covers GameNative/Steam (which libretro can't) and gives consistent, stylised art if you want every tile to look alike.
2. **ScreenScraper** for the deepest retro coverage, hash matching and extra media (logos, 3D boxes, videos) the design could use later. It needs developer credentials. I can draft the request; it has to come from you as a registered member.
3. **IGDB or Hasheous** for Switch, Wii U and Xbox 360 gaps, if your report still shows them missing after 1 and 2.

Keys are entered on the device (System → Box art) and stored only there, never in the repository or the theme file.

**Decision needed:** which of these to add, and in what order relative to your folder and libretro.
