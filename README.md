# PadhakuTube

YouTube-style video discovery and viewing interface with a NewPipeExtractor Java API.

## Features
- Live video search with pagination, channel pages, related videos and read-only YouTube comments.
- Direct combined-stream playback with quality/speed controls and a byte-range media proxy. Embedded YouTube playback remains an explicit fallback.
- Home discovery by topic, mobile layout, light/dark appearance, full watch pages, theater mode and share links.
- Browser-local watch history, watch later, liked videos, subscriptions and custom playlists. These actions do not modify a YouTube account.

## Deploy
The frontend is served by Sites from `dist/`. The Java service is deployed separately using `backend/Dockerfile` or the provided Render Blueprint.

```sh
cd backend
docker build -t padhakutube-api .
docker run --rm -p 8080:8080 \
  -e ALLOWED_ORIGIN=https://padhakutube-watch.bushyball.chatgpt.site \
  -e JAVA_OPTS='-Xms64m -Xmx320m' padhakutube-api
```

Set the deployed HTTPS API origin in `dist/config.js` for all visitors, or configure it under Settings for a single browser. ALLOWED_ORIGIN must match the frontend origin exactly. `PORT` defaults to 8080 and is read from the hosting environment.

Render: free plan, Singapore region, Docker context `backend`, Dockerfile `backend/Dockerfile`, health check `/api/health`. A free service may sleep when idle; the client allows a longer initial connection timeout. An always-running paid tier is not enabled automatically.

## API
- `/api/health`: engine, version, capabilities.
- `/api/search?q=`: video search, returns opaque continuation cursor.
- `/api/more?cursor=`: pagination; cursors expire after ten minutes.
- `/api/stream?id=`: video metadata, related videos, combined streams.
- `/api/comments?id=`: read-only comments.
- `/api/channel?url=`: channel metadata and videos, restricted to YouTube channel URLs.
- `/api/media?token=`: byte-range video proxy for server-issued opaque tokens. It accepts no arbitrary URL and validates Google Video hosts on every redirect. Tokens expire after fifteen minutes.

All routes support the configured frontend CORS origin. There are bounded extraction/media concurrency limits, a bounded request queue, and short in-memory caches. Apply deployment-level rate limits before broad public use. CORS is not authentication.

## Limits
No uploads, YouTube account sign-in, posting comments, account-level likes/subscriptions, adaptive audio/video merging, Shorts feed, or recommendation algorithm are implemented. The homepage uses a discovery search, not a personalized YouTube feed. Only browser-compatible combined streams are selected for direct playback. Upstream restrictions, datacenter blocking, expiring media URLs and extractor changes can affect playback. Embedded playback can include ads and may be disabled by the uploader. No restriction or CAPTCHA bypass is implemented.

When no backend is configured, the frontend clearly identifies its curated collection. It never represents local filtering as live search.

See VALIDATION.md for actual test outcomes and deployment limitations.

## License
GPL-3.0-or-later. NewPipeExtractor is GPL-3.0-or-later, copyright its contributors. This project is not affiliated with YouTube or Team NewPipe. Full source is available from the website's source download. Include corresponding source and license notices when redistributing builds.
