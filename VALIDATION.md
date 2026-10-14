# Validation — 2026-10-14 UTC

## Verified
- Java 21 Gradle installDist build with NewPipeExtractor v0.26.5.
- API v3 lists all five services with actual library filters and feeds.
- Live local-backend searches: YouTube 16 items; media.ccc.de 30; Bandcamp 15; SoundCloud 10; PeerTube 12.
- PeerTube extraction returned 15 video and 3 audio options. SoundCloud extraction failed for the sampled track; Bandcamp playback verification did not finish because a network approval was cancelled. These are not claimed as working playback.
- Live media.ccc.de extraction: 4 combined video streams and 2 audio streams; an actual media range request returned HTTP 206, video/webm, and 256 bytes.
- YouTube public playlist search returned 9 lists; playlist extraction returned 100 items; channel extraction returned 30 videos and 3 tabs.
- API health/services 200; restricted YouTube streams and comments 503 without an upstream request; invalid source URL/tab/filter 400; invalid media token 410.
- Frontend JavaScript syntax checks.
- HappyDOM: prior home/search/save/playlists/history/dark mode behavior; 5-source selector; queue; no requests to blocked YouTube stream/comment routes; timestamp notes and escaping; A–B loop; focus mode; sleep-timer pause; malformed backup rejection; cleanup on navigation; source switching and invalid links.

## Limits
- No supported browser QA capability was available. DOM checks do not prove visual rendering or actual browser media playback.
- The YouTube Render IP verification block remains. Official embedded playback is implemented, but not verified in a real browser here.
- Search/extraction can change upstream and differ between the test environment and Render. Public content availability, CORS, allowed codecs, source outages, and owner embedding restrictions still apply.
- Native browser captions use source-provided WebVTT only. Adaptive video-only/audio merging and DASH playback are not implemented.

## Publication
The frontend and backend sources must be deployed together for API v3 features. The UI capability-checks the live backend and keeps v2-compatible search/local tools until the new backend is live.
