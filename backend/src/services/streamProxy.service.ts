import { Request, Response } from 'express';
import https from 'https';
import http from 'http';
import { URL } from 'url';

export class StreamProxyService {
  private static DEFAULT_USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36';

  /**
   * Proxies media video stream with HTTP 206 Partial Content and Range headers
   */
  public static handleProxyStream(req: Request, res: Response): void {
    let targetUrl = '';

    // Safely extract from raw req.url to avoid Express parameter splitting & double-decoding
    const urlParamIdx = req.url.indexOf('url=');
    if (urlParamIdx !== -1) {
      const raw = req.url.substring(urlParamIdx + 4);
      try {
        targetUrl = decodeURIComponent(raw);
      } catch {
        targetUrl = raw;
      }
    } else if (req.query.url) {
      targetUrl = String(req.query.url);
    }

    if (!targetUrl || targetUrl.trim().length === 0) {
      res.status(400).json({ error: 'Missing target url parameter' });
      return;
    }

    targetUrl = targetUrl.trim();
    if (!targetUrl.startsWith('http://') && !targetUrl.startsWith('https://')) {
      targetUrl = 'https://' + targetUrl.replace(/^\/+/, '');
    }

    this.pipeStream(targetUrl, req, res, 0);
  }

  private static pipeStream(currentUrl: string, req: Request, res: Response, redirectCount: number = 0): void {
    if (redirectCount > 5) {
      if (!res.headersSent) {
        res.status(508).json({ error: 'Too many redirects from media server' });
      }
      return;
    }

    try {
      const parsedUrl = new URL(currentUrl);
      const isTwolekedeOrMovviez = parsedUrl.hostname.includes('movviez.com') || parsedUrl.hostname.includes('twolekede.com');
      const isMunoOrBunny = parsedUrl.hostname.includes('b-cdn.net') || parsedUrl.hostname.includes('munowatch.com') || parsedUrl.hostname.includes('munotek.com');

      const headers: Record<string, string> = {
        'User-Agent': this.DEFAULT_USER_AGENT,
        'Accept': '*/*'
      };

      if (isTwolekedeOrMovviez) {
        headers['Referer'] = 'https://twolekede.com/';
        headers['Origin'] = 'https://twolekede.com';
      } else if (isMunoOrBunny) {
        headers['Referer'] = 'https://munowatch.com/';
        headers['Origin'] = 'https://munowatch.com';
      }

      if (req.headers.range) {
        headers['Range'] = req.headers.range as string;
      }

      const client = parsedUrl.protocol === 'https:' ? https : http;

      const upstreamReq = client.request(
        parsedUrl,
        {
          method: 'GET',
          headers: headers,
          rejectUnauthorized: false
        },
        (upstreamRes) => {
          // If 301 / 302 / 307 redirect, resolve location against current parsedUrl
          if (
            (upstreamRes.statusCode === 301 || upstreamRes.statusCode === 302 || upstreamRes.statusCode === 307 || upstreamRes.statusCode === 308) &&
            upstreamRes.headers.location
          ) {
            const nextLocation = new URL(upstreamRes.headers.location, parsedUrl).href;
            upstreamRes.destroy();
            this.pipeStream(nextLocation, req, res, redirectCount + 1);
            return;
          }

          const responseStatusCode = upstreamRes.statusCode || 200;

          // Forward essential media streaming headers
          res.status(responseStatusCode);
          res.setHeader('Access-Control-Allow-Origin', '*');
          res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
          res.setHeader('Access-Control-Allow-Headers', 'Range, Accept, Origin, Content-Type');
          res.setHeader('Accept-Ranges', 'bytes');

          if (upstreamRes.headers['content-range']) {
            res.setHeader('Content-Range', upstreamRes.headers['content-range']);
          }
          if (upstreamRes.headers['content-length']) {
            res.setHeader('Content-Length', upstreamRes.headers['content-length']);
          }
          if (upstreamRes.headers['content-type']) {
            res.setHeader('Content-Type', upstreamRes.headers['content-type']);
          } else {
            res.setHeader('Content-Type', currentUrl.includes('.m3u8') ? 'application/x-mpegURL' : 'video/mp4');
          }

          upstreamRes.pipe(res);
        }
      );

      upstreamReq.on('error', (err) => {
        if (!res.headersSent) {
          res.status(502).json({ error: 'Upstream media connection error', message: err.message });
        }
      });

      req.on('close', () => {
        upstreamReq.destroy();
      });

      upstreamReq.end();
    } catch (e: any) {
      if (!res.headersSent) {
        res.status(500).json({ error: 'Stream proxy pipeline failed', message: e.message });
      }
    }
  }

  private static urlMatchesTitle(streamUrl: string, expectedTitle?: string): boolean {
    if (!expectedTitle || expectedTitle.trim().length < 2) return true;
    try {
      const cleanUrl = decodeURIComponent(streamUrl).toLowerCase();
      const words = expectedTitle
        .toLowerCase()
        .replace(/\[.*?\]|\(.*?\)/g, '')
        .split(/[^a-z0-9]+/)
        .filter(w => w.length >= 3 && !['part', 'the', 'movie', 'full', 'vj', 'series', 'season'].includes(w));

      if (words.length === 0) return true;
      return words.some(w => cleanUrl.includes(w));
    } catch {
      return true;
    }
  }

  /**
   * Resolves direct media streaming link for any movie or series title on demand
   */
  public static async resolveMediaStream(id: string, title?: string): Promise<string | null> {
    const isExplicitTwolekede = id.startsWith('tk_') || (!id.startsWith('myvj_') && /^\d+$/.test(id));
    const cleanId = id.replace(/^(myvj_|tk_)/, '');

    // 1. If it's a numeric Twolekede ID (and NOT an UgaWatch row_id)
    if (isExplicitTwolekede && /^\d+$/.test(cleanId)) {
      const resolved = await this.resolveTwolekedeStreamById(cleanId);
      if (resolved && this.urlMatchesTitle(resolved, title)) return resolved;
    }

    // 2. Search Twolekede by clean title if title is provided
    if (title && title.trim().length >= 2) {
      const cleanTitle = title
        .replace(/\[.*?\]|\(.*?\)/g, ' ')
        .replace(/\b(?:vj\s+[a-z0-9_.-]+|by\s+vj.*)\b/gi, ' ')
        .replace(/\s+/g, ' ')
        .trim();

      const candidateQueries = [
        cleanTitle.substring(0, 30),
        cleanTitle.split(/[:–-]/)[0].trim()
      ].filter(q => q.length >= 3);

      for (const q of candidateQueries) {
        try {
          const resp = await fetch(`https://twolekede.com/search.php?q=${encodeURIComponent(q)}`, {
            headers: {
              'Referer': 'https://twolekede.com/',
              'User-Agent': this.DEFAULT_USER_AGENT
            },
            signal: AbortSignal.timeout(6000)
          });

          if (resp.ok) {
            const html = await resp.text();

            // Match each content-item card and verify title
            const cardRegex = /<a href="(?:moviedetails|preview)\.php\?id=(\d+)"[^>]*class="content-item"[\s\S]*?<img[^>]+alt="([^"]*)"[\s\S]*?<h3 class="content-title">([^<]*)<\/h3>/g;
            let m: RegExpExecArray | null;

            while ((m = cardRegex.exec(html)) !== null) {
              const matchedId = m[1];
              const cardTitle = (m[3] || m[2] || '').trim();

              // Check if titles match
              const normCard = cardTitle.toLowerCase().replace(/[^a-z0-9]/g, '');
              const normSearch = cleanTitle.toLowerCase().replace(/[^a-z0-9]/g, '');

              if (normCard.includes(normSearch) || normSearch.includes(normCard) || normCard === normSearch) {
                const stream = await this.resolveTwolekedeStreamById(matchedId);
                if (stream && this.urlMatchesTitle(stream, title)) return stream;
              }
            }

            // Fallback: check any href if no cardRegex matched
            const anyMatch = html.match(/href="(?:moviedetails|preview)\.php\?id=(\d+)"/);
            if (anyMatch && anyMatch[1]) {
              const stream = await this.resolveTwolekedeStreamById(anyMatch[1]);
              if (stream && this.urlMatchesTitle(stream, title)) return stream;
            }
          }
        } catch {}
      }
    }

    // 3. Fallback: Query UgaWatch API for BunnyCDN video stream (e.g. Indian movies / MK Kisuule / exclusive uploads)
    const ugaStream = await this.resolveUgaWatchStream(cleanId, title);
    if (ugaStream) return ugaStream;

    return null;
  }

  /**
   * Resolves BunnyCDN MP4 stream from UgaWatch API v3 for titles not indexed on Twolekede
   */
  private static async resolveUgaWatchStream(id: string, title?: string): Promise<string | null> {
    const cleanId = id.replace(/^(myvj_|tk_)/, '');
    const endpoints = [
      'https://ugawatch.com/api-v3/mobile/get-movie-details-min.php',
      'https://myvj.net/api-v3/mobile/get-movie-details-min.php'
    ];

    const attempts: URLSearchParams[] = [];

    if (/^\d+$/.test(cleanId)) {
      const p = new URLSearchParams();
      p.append('source', 'next_js');
      p.append('movie_id', cleanId);
      p.append('id', cleanId);
      attempts.push(p);
    }

    if (title && title.trim().length >= 2) {
      const cleanSlug = title
        .toLowerCase()
        .replace(/\[.*?\]|\(.*?\)/g, '')
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-|-$/g, '');

      if (cleanSlug.length >= 2) {
        const p = new URLSearchParams();
        p.append('source', 'next_js');
        p.append('movie_slug', cleanSlug);
        attempts.push(p);
      }
    }

    for (const url of endpoints) {
      for (const body of attempts) {
        try {
          const resp = await fetch(url, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/x-www-form-urlencoded',
              'Referer': 'https://ugawatch.com/',
              'User-Agent': this.DEFAULT_USER_AGENT
            },
            body: body.toString(),
            signal: AbortSignal.timeout(6000)
          });

          if (resp.ok) {
            const data: any = await resp.json();
            const movie = data.movie || data.data;
            if (movie) {
              const file = movie.bunny_file_name || movie.trailer || movie.video;
              if (file && typeof file === 'string' && file.trim().length > 3) {
                const cleanFile = file.replace(/\\/g, '').trim();
                if (cleanFile.startsWith('http')) return cleanFile;
                return `https://trailer256.b-cdn.net/${cleanFile}`;
              }
            }
          }
        } catch {}
      }
    }

    return null;
  }

  private static async resolveTwolekedeStreamById(id: string): Promise<string | null> {
    // Attempt 1: GET preview.php (fastest and most reliable direct 1080p stream source)
    try {
      const resp = await fetch(`https://twolekede.com/preview.php?id=${id}`, {
        headers: {
          'Referer': 'https://twolekede.com/',
          'User-Agent': this.DEFAULT_USER_AGENT
        },
        signal: AbortSignal.timeout(6000)
      });

      if (resp.ok) {
        const html = await resp.text();
        const srcMatch = html.match(/<source\s+[^>]*src=["']([^"']+)["']/i) ||
                         html.match(/https?:\/\/(?:cdn|dl)\.movviez\.com\/secure\.php\?[^\s"'<>]+/);
        if (srcMatch) {
          const rawUrl = (srcMatch[1] || srcMatch[0]).replace(/&amp;/g, '&');
          if (rawUrl && !rawUrl.includes('index.php')) {
            return rawUrl;
          }
        }
      }
    } catch {}

    // Attempt 2: POST download.php
    try {
      const body = new URLSearchParams({ submit: 'Download', download: 'Download' });
      const resp = await fetch(`https://twolekede.com/download.php?id=${id}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'Referer': `https://twolekede.com/download.php?id=${id}`,
          'User-Agent': this.DEFAULT_USER_AGENT
        },
        body: body.toString(),
        redirect: 'manual',
        signal: AbortSignal.timeout(6000)
      });

      const loc = resp.headers.get('location');
      if (loc && (loc.includes('secure.php') || loc.includes('.mp4') || loc.includes('movviez.com')) && !loc.includes('index.php')) {
        return loc;
      }
    } catch {}

    return null;
  }
}
