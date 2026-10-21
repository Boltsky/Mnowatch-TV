# Munowatch Architecture & Communication Specification

## 1. Overview
Munowatch (`https://munowatch.com/`) is a video entertainment platform specializing in translated movies and TV shows localized by Ugandan VJs (Video Jockeys like Vj Junior, Vj Emmy, Vj Jingo, Ice P, etc.). This document records the empirical network structure, authentication protocol, endpoints, request formats, and streaming integration discovered during platform inspection.

## 2. Authentication Protocol & Session Architecture
* **Primary Credential**: PHP session identifier (`PHPSESSID`).
* **Transport**: HTTP `Cookie: PHPSESSID=<32-char hex string>`.
* **Validation Mechanism**:
  - Request: `GET https://munowatch.com/home` (or `/grid`, `/search`, `/list`) with `Cookie: PHPSESSID=<id>`.
  - Unauthenticated / Expired State: The server returns `HTTP 302 Found` with `Location: /login` and an expires header of `Thu, 19 Nov 1981 08:52:00 GMT`.
  - Authenticated State: Returns `HTTP 200 OK` containing the home dashboard HTML.
* **Security & Storage**:
  - Session IDs are stored using hardware-backed Android Keystore AES-GCM encryption in private app preferences.
  - Session IDs are never written to Logcat or exposed in debug tools.
  - When the session expires, the app triggers a re-authentication prompt without crashing or infinite loops.

## 3. Discovered Network Endpoints
All Munowatch service interactions are centralized in the `data/` layer.

| Endpoint | Method | Auth Required | Description / Response Format |
|---|---|---|---|
| `https://munowatch.com/browse` | GET | No | Returns public JSON with available categories: `{"vj": [{"id": Int, "name": String, "icon": String, "pipe": "vj_genre"}], "genre": [{"id": Int, "name": String, "icon": String, "pipe": "genre_vj"}]}`. |
| `https://munowatch.com/remaining-days` | GET | Yes | Returns plain numeric string representing active subscription days remaining (e.g. `28`). |
| `https://munowatch.com/grid?pipe_type={type}&pipe_id={id}&page_name={name}` | GET | Yes | Returns the full page grid container. `pipe_type`: `p` (preset like 4 = Latest Movies), `g` (genre like 5 = Series), `vj` (VJ catalog), `fr` (free movies). |
| `https://munowatch.com/grid-slot` | GET | Yes | Dynamic pagination endpoint used by infinite scroll. Query params: `pipe_type`, `pipe_id`, `vj_id` (optional), `genre_id` (optional), `last_fetch_id` (ID of last loaded card). Returns HTML fragment of cards. |
| `https://munowatch.com/search?q={query}` | GET | Yes | Live search endpoint returning HTML cards for query matching movies and series. |
| `https://munowatch.com/list?serie_id={id}` | GET | Yes | Loads series episodes/seasons HTML. Also `/list?page=saved` for watchlist. |
| `https://munowatch.com/logout` | GET | Yes | Clears the session on the server. |
| `https://munotek.com/` | GET | Media | Official media and CDN streaming host hosting media files and HLS/MP4 streams. |

## 4. Media & Playback Architecture
* **Streaming Engine**: AndroidX Media3 (ExoPlayer 1.5.1) with HLS, DASH, and Progressive MP4 support.
* **Headers & Referers**: Streams often require `Referer: https://munowatch.com/` and `User-Agent: Mozilla/5.0 ...` to prevent hotlinking. Configured via `OkHttpDataSource.Factory`.
* **Multi-Server Resiliency**: Playback repository abstracts servers (`Server 1`, `Server 2`, `Server 3`, `Auto-Select`). If a stream errors, user or auto-fallback switches instantly to an alternate provider.
* **Local Playback Progress**: Stored in a local Room database (`WatchHistoryEntity`), tracking content ID, title, position, duration, and last watched timestamp for the TV "Continue Watching" row.
