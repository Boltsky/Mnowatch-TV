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

  /**
   * Resolves direct media streaming link for any movie or series title on demand
   */
  public static async resolveMediaStream(id: string, title?: string): Promise<string | null> {
    const cleanId = id.replace(/^(myvj_|tk_)/, '');

    // 1. If it's a numeric Twolekede ID
    if (/^\d+$/.test(cleanId)) {
      const resolved = await this.resolveTwolekedeStreamById(cleanId);
      if (resolved) return resolved;
    }

    // 2. If title is provided, search Twolekede for matching movie / series
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
            signal: AbortSignal.timeout(5000)
          });

          if (resp.ok) {
            const html = await resp.text();
            const idMatch = html.match(/href="(?:moviedetails|preview)\.php\?id=(\d+)"/);
            if (idMatch && idMatch[1]) {
              const stream = await this.resolveTwolekedeStreamById(idMatch[1]);
              if (stream) return stream;
            }
          }
        } catch {}
      }
    }

    return null;
  }

  private static async resolveTwolekedeStreamById(id: string): Promise<string | null> {
    // Attempt A: POST download.php
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
      if (loc && (loc.includes('secure.php') || loc.includes('.mp4') || loc.includes('movviez.com'))) {
        return loc;
      }
    } catch {}

    // Attempt B: GET preview.php and extract source tag
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
        if (srcMatch && srcMatch[1]) {
          return srcMatch[1].replace(/&amp;/g, '&');
        } else if (srcMatch && srcMatch[0]) {
          return srcMatch[0].replace(/&amp;/g, '&');
        }
      }
    } catch {}

    return null;
  }
}
