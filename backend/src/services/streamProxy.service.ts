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
   * Validates that stream URL contains substantive keywords of expected title
   */
  public static urlMatchesTitle(streamUrl: string, expectedTitle?: string): boolean {
    if (!expectedTitle || expectedTitle.trim().length < 2) return true;
    try {
      const cleanUrl = decodeURIComponent(streamUrl).toLowerCase();
      const words = expectedTitle
        .toLowerCase()
        .replace(/\[.*?\]|\(.*?\)/g, '')
        .replace(/\b(?:by\s+vj|vj)\s+[a-z0-9_.-]*.*$/gi, ' ')
        .split(/[^a-z0-9]+/)
        .map(w => w.endsWith('s') && w.length > 3 ? w.slice(0, -1) : w)
        .filter(w => w.length >= 3 && !['part', 'the', 'movie', 'full', 'vj', 'series', 'season', 'and'].includes(w));

      if (words.length === 0) return true;
      return words.some(w => cleanUrl.includes(w));
    } catch {
      return true;
    }
  }

  /**
   * Generates prioritized search query candidates for Twolekede matching
   */
  public static generateCandidateQueries(title: string): string[] {
    let clean = title
      .replace(/\[.*?\]|\(.*?\)/g, ' ')
      .replace(/\b(?:by\s+vj|vj)\s+[a-z0-9_.-]*.*$/gi, ' ')
      .replace(/\b(?:19\d{2}|20\d{2})\b/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();

    const queries: string[] = [];
    if (clean.length >= 2) queries.push(clean);

    // Substitute 'and' <-> '&'
    if (/\band\b/i.test(clean)) {
      queries.push(clean.replace(/\band\b/gi, '&').replace(/\s+/g, ' ').trim());
    }
    if (clean.includes('&')) {
      queries.push(clean.replace(/&/g, 'and').replace(/\s+/g, ' ').trim());
    }

    // Plural 's' fix (e.g. wolverines -> wolverine)
    const words = clean.split(' ');
    if (words.length > 0 && words[words.length - 1].endsWith('s') && words[words.length - 1].length > 3) {
      const sing = [...words.slice(0, -1), words[words.length - 1].slice(0, -1)].join(' ');
      queries.push(sing);
      if (/\band\b/i.test(sing)) {
        queries.push(sing.replace(/\band\b/gi, '&').replace(/\s+/g, ' ').trim());
      }
    }

    // Primary substantive keywords (e.g. "Deadpool", "Wolverine", "Furiosa")
    const stopWords = new Set(["the", "with", "from", "part", "this", "that", "movie", "full", "translated", "luganda", "saga", "and"]);
    const substantive = clean
      .split(' ')
      .map(w => w.replace(/[^a-zA-Z0-9]/g, ''))
      .filter(w => w.length >= 4 && !stopWords.has(w.toLowerCase()));

    for (const sub of substantive.slice(0, 2)) {
      if (sub && !queries.includes(sub)) {
        queries.push(sub);
      }
    }

    // Prefix before colon or dash
    const pre = clean.split(/[:–-]/)[0].trim();
    if (pre.length >= 3 && !queries.includes(pre)) {
      queries.push(pre);
    }

    return queries.filter(q => q.length >= 2);
  }

  /**
   * Robust comparison verifying that two titles represent the same movie
   */
  public static titlesMatch(searchTitle: string, candidateTitle: string): boolean {
    const unescapeHtml = (str: string) => str.replace(/&amp;/g, '&').replace(/&#38;/g, '&').replace(/&quot;/g, '"');
    const stopWords = new Set(["the", "with", "from", "part", "this", "that", "movie", "full", "translated", "luganda", "saga", "and"]);

    const clean1 = unescapeHtml(searchTitle).toLowerCase().replace(/[^a-z0-9]/g, ' ');
    const clean2 = unescapeHtml(candidateTitle).toLowerCase().replace(/[^a-z0-9]/g, ' ');

    const tokens1 = clean1.split(/\s+/).map(w => w.endsWith('s') && w.length > 3 ? w.slice(0, -1) : w).filter(w => w.length >= 3 && !stopWords.has(w));
    const tokens2 = clean2.split(/\s+/).map(w => w.endsWith('s') && w.length > 3 ? w.slice(0, -1) : w).filter(w => w.length >= 3 && !stopWords.has(w));

    if (tokens1.length === 0 || tokens2.length === 0) return false;

    const set1 = new Set(tokens1);
    const set2 = new Set(tokens2);

    const isSubset1 = tokens1.every(t => set2.has(t));
    const isSubset2 = tokens2.every(t => set1.has(t));
    if (isSubset1 || isSubset2) return true;

    let overlap = 0;
    for (const t of tokens1) {
      if (set2.has(t)) overlap++;
    }
    return overlap >= 2;
  }

  /**
   * Resolves direct media streaming link for any movie or series title on demand
   */
  public static async resolveMediaStream(id: string, title?: string): Promise<string | null> {
    const isExplicitTwolekede = id.startsWith('tk_') || (!id.startsWith('myvj_') && /^\d+$/.test(id));
    const cleanId = id.replace(/^(myvj_|tk_)/, '');

    // 1. If it's a numeric Twolekede ID, resolve and verify title match
    if (isExplicitTwolekede && /^\d+$/.test(cleanId)) {
      const resolved = await this.resolveTwolekedeStreamById(cleanId);
      if (resolved && this.urlMatchesTitle(resolved, title)) {
        return resolved;
      }
      // If direct ID returned mismatched movie, fall through to search by title
    }

    // 2. Search Twolekede using smart candidate query variations
    if (title && title.trim().length >= 2) {
      const candidateQueries = this.generateCandidateQueries(title);

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

            // Match all content-items and unescape HTML titles
            const cardRegex = /<a href="(?:moviedetails|preview)\.php\?id=(\d+)"[^>]*>[\s\S]*?<img[^>]+alt="([^"]*)"[\s\S]*?<h3 class="content-title">([^<]*)<\/h3>/g;
            let m: RegExpExecArray | null;

            while ((m = cardRegex.exec(html)) !== null) {
              const matchedId = m[1];
              const cardTitle = (m[3] || m[2] || '').trim();

              if (this.titlesMatch(title, cardTitle)) {
                const stream = await this.resolveTwolekedeStreamById(matchedId);
                if (stream && this.urlMatchesTitle(stream, title)) {
                  return stream;
                }
              }
            }

            // Fallback pattern matching
            const altCardRegex = /<a href="(?:moviedetails|preview)\.php\?id=(\d+)"[^>]*alt="([^"]*)"/g;
            let altM: RegExpExecArray | null;
            while ((altM = altCardRegex.exec(html)) !== null) {
              const matchedId = altM[1];
              const cardTitle = (altM[2] || '').trim();
              if (this.titlesMatch(title, cardTitle)) {
                const stream = await this.resolveTwolekedeStreamById(matchedId);
                if (stream && this.urlMatchesTitle(stream, title)) {
                  return stream;
                }
              }
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
        .replace(/\b(?:by\s+vj|vj)\s+[a-z0-9_.-]*.*$/gi, '')
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '');

      if (cleanSlug.length >= 2) {
        const p1 = new URLSearchParams();
        p1.append('source', 'next_js');
        p1.append('movie_slug', cleanSlug);
        attempts.push(p1);

        const p2 = new URLSearchParams();
        p2.append('source', 'next_js');
        p2.append('title', title.trim());
        attempts.push(p2);
      }
    }

    for (const endpoint of endpoints) {
      for (const params of attempts) {
        try {
          const resp = await fetch(endpoint, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/x-www-form-urlencoded',
              'User-Agent': this.DEFAULT_USER_AGENT,
              'Referer': 'https://ugawatch.com/'
            },
            body: params.toString(),
            signal: AbortSignal.timeout(5000)
          });

          if (resp.ok) {
            const data = await resp.json();
            if (data && (data.status === 0 || data.status === '0') && (data.movie || data.data)) {
              const movie = data.movie || data.data;
              const file = movie.bunny_file_name || movie.trailer || movie.video;
              if (file && typeof file === 'string' && file.trim().length > 3) {
                const cleanFile = file.replace(/\\/g, '').trim();
                const stream = cleanFile.startsWith('http') ? cleanFile : `https://trailer256.b-cdn.net/${cleanFile}`;
                if (this.urlMatchesTitle(stream, title)) {
                  return stream;
                }
              }
            }
          }
        } catch {}
      }
    }

    return null;
  }

  public static async resolveTwolekedeStreamById(id: string): Promise<string | null> {
    // Attempt 1: GET preview.php (fastest direct stream source)
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
