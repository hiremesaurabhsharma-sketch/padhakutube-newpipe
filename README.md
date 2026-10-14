# PadhakuTube

A NewPipeExtractor-backed media browser with study tools. The frontend is hosted at https://padhakutube-watch.bushyball.chatgpt.site and the backend at https://padhakutube-newpipe.onrender.com.

## Features
- NewPipeExtractor v0.26.5: YouTube, SoundCloud, Bandcamp, PeerTube and media.ccc.de search, source-specific filters, suggestions, discovery feeds, public playlists, channels/tabs and pagination where supported by the source.
- Direct video/audio and HLS playback for supported unrestricted media sources; quality selection, playback speed, seek, volume, full screen and browser-supported PiP.
- Official YouTube IFrame API playback and controls. This server has encountered a YouTube login/bot-verification block, so direct YouTube extraction and comments are disabled and return a structured 503 without contacting YouTube. No cookies, alternate clients, IP rotation or verification bypass is implemented.
- Study notes with timestamps, note export, A–B segment repeat, focus mode, sleep timer and resume position.
- Play queue, reordering, remove/clear, opt-in automatic next item, local history/liked/watch-later/subscriptions/playlists.
- Versioned library backup/restore, NewPipe subscriptions JSON import/export.
- Local media files (never uploaded) and user-supplied HTTPS MP4/WebM/audio/HLS sources.
- Source-provided captions (WebVTT) and chapters when extraction succeeds. Original-source links remain available.

## Runtime
The buildless frontend is in `dist/`. Scripts are loaded in this order: HLS library, player adapter, config, base UI, extended features. The frontend capability-checks `/api/health`; v2 backends keep YouTube search and local study features, while v3 enables the five-source catalogue.

The Java 21 backend uses `backend/Dockerfile`. Build locally:

```sh
cd backend
gradle --no-daemon installDist
```

Docker:

```sh
docker build -t padhakutube-api backend
docker run --rm -p 8080:8080 -e ALLOWED_ORIGIN=https://padhakutube-watch.bushyball.chatgpt.site padhakutube-api
```

Render uses Docker context `backend`, Dockerfile `backend/Dockerfile`, free plan, Singapore, and health endpoint `/api/health`. `PORT` defaults to 8080. `JAVA_OPTS=-Xms64m -Xmx320m` is appropriate for the configured free instance. A free instance may sleep; the frontend allows a 65-second request timeout.

`PEERTUBE_ORIGIN` defaults to `https://framatube.org`. It is an administrator-configured HTTPS origin; arbitrary user-supplied PeerTube hosts are not accepted. Federated results hosted elsewhere may require opening the original source. `ALLOWED_ORIGIN` must match the frontend origin exactly. CORS is not authentication; add appropriate rate limits before broad public use.

## API v3
`service` accepts `youtube`, `soundcloud`, `bandcamp`, `peertube`, `mediaccc` (default `youtube`).
- `/api/health`: version/capabilities.
- `/api/services`: source names, filters, sorts, feeds and playback capabilities.
- `/api/search?q=&service=&filter=&sort=`
- `/api/suggest?q=&service=`
- `/api/kiosk?service=&id=`
- `/api/channel?service=&url=` and `/api/tab?token=`
- `/api/playlist?service=&url=`
- `/api/stream?service=&url=`: combined video, audio, captions, chapters, related media, HLS/DASH metadata.
- `/api/comments?service=&url=`: read-only where supported.
- `/api/more?cursor=`: server-issued pagination cursor, expires in ten minutes.
- `/api/media?token=`: retained legacy Google Video proxy; no new YouTube tokens are issued by v3.

Source URLs are validated against the selected service; pagination and tab tokens are generated on the server. Concurrency, queues and cache sizes are bounded. No Google cookies, passwords, or API keys are needed by this implementation.

## Limits
This is not the complete Android NewPipe app. It has no YouTube account login, upload, posting comments, YouTube account likes/subscriptions, Android background service, offline download manager, adaptive video/audio muxing, or personalized YouTube recommendation feed. DASH metadata is exposed but the frontend currently plays MP4/WebM/audio/HLS only. Caption availability depends on WebVTT format and source CORS. Quality choices reflect actual extracted streams, not promised 4K.

An Android background service cannot be reproduced by a browser tab; media and sleep timers may be suspended by the OS. PiP depends on browser support. Embedded playback can show ads and can be disabled by a video owner. Browser rendering and end-to-end playback require a real browser check; see VALIDATION.md for verified results.

## License
Project: GPL-3.0-or-later. NewPipeExtractor: GPL-3.0-or-later, its contributors. HLS.js: Apache-2.0; version and license are in `dist/vendor/`. The site includes corresponding project source as `dist/source.zip`. Not affiliated with YouTube or Team NewPipe.
