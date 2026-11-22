import { Request, Response } from 'express';
import https from 'https';
import http from 'http';
import { URL } from 'url';

export class StreamProxyService {
  private static DEFAULT_USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36';
  private static MUNO_SESSION_COOKIE = 'PHPSESSID=926cdd498cd8113008318bd9c6696ff5';

  /**
   * Resolves direct 1080p BunnyCDN video stream from Munowatch watch page via data-gumite base64 decoding
   */
  public static async resolveMunowatchWatchStream(watchUrlOrPath: string): Promise<string | null> {
    try {
      const fullUrl = watchUrlOrPath.startsWith('http')
        ? watchUrlOrPath
        : `https://munowatch.com${watchUrlOrPath.startsWith('/') ? '' : '/'}${watchUrlOrPath}`;

      const resp = await fetch(fullUrl, {
        headers: {
          'Cookie': this.MUNO_SESSION_COOKIE,
          'Referer': 'https://munowatch.com/',
          'User-Agent': this.DEFAULT_USER_AGENT
        },
        signal: AbortSignal.timeout(6000)
      });

      if (resp.ok) {
        const html = await resp.text();
        const gumiteMatch = html.match(/data-gumite=["']([^"']+)["']/i);
        if (gumiteMatch && gumiteMatch[1]) {
          try {
            const decoded = Buffer.from(gumiteMatch[1], 'base64').toString('utf-8').trim();
            const streamInDecoded = decoded.match(/https?:\/\/[^\s"'<>]+\.(?:mp4|m3u8)(?:\?[^\s"'<>]*)?/i) ||
                                    decoded.match(/https?:\/\/[a-zA-Z0-9_\-\.]+\.b-cdn\.net\/[^\s"'<>]+/i);
            const streamUrl = streamInDecoded ? streamInDecoded[0] : (decoded.startsWith('http') ? decoded : null);
            if (streamUrl && !this.isTrailerOrPromoUrl(streamUrl)) {
              return streamUrl;
            }
          } catch {}
        }
      }
    } catch {}
    return null;
  }

  /**
   * Universal detector rejecting any trailer, sample, promo, teaser, or intro video URLs or filenames.
   * Defeats percent-encoded obfuscation via iterative decoding, detects query-param trailer indicators,
   * enforces token boundaries on trailer/teaser keywords, and avoids false positives when expectedTitle
   * contains "trailer" (e.g. "Trailer Park Boys").
   */
  public static isTrailerOrPromoUrl(url: string | null | undefined, expectedTitle?: string): boolean {
    if (!url || typeof url !== 'string') return true;
    let lower = url.toLowerCase().trim();
    if (lower.length === 0) return true;

    // 1. Iteratively decode decodeURIComponent (up to 3 times or until string stabilizes)
    // to defeat percent-encoded obfuscation (%74%72%61%69%6c%65%72, %2ftrailer)
    let decoded = lower;
    for (let i = 0; i < 3; i++) {
      try {
        const next = decodeURIComponent(decoded);
        if (next === decoded) break;
        decoded = next;
      } catch {
        break;
      }
    }
    lower = decoded.toLowerCase().trim();

    // 2. Forbidden trailer / promo CDN pull zones or hostnames (unconditional)
    if (lower.includes('trailer256.b-cdn.net')) return true;
    if (lower.includes('intro_myvj')) return true;

    // 3. Query parameter trailer / teaser / promo indicators
    // Detect query parameters: [?&](?:type|clip|mode|file)=trailer, [?&]trailer\b, [?&]teaser\b
    if (
      /[?&](?:type|clip|mode|file)=(?:trailer|teaser|promo|sample)\b/i.test(lower) ||
      /[?&]trailer\b/i.test(lower) ||
      /[?&]teaser\b/i.test(lower) ||
      /[?&]promo\b/i.test(lower)
    ) {
      return true;
    }

    // 4. Context-aware title protection:
    // If expectedTitle contains "trailer" (e.g. "Trailer Park Boys", "The Trailer"),
    // do not falsely block the title name in the URL.
    let testStr = lower;
    if (expectedTitle && typeof expectedTitle === 'string' && expectedTitle.toLowerCase().includes('trailer')) {
      const coreTitle = expectedTitle
        .toLowerCase()
        .replace(/\b(?:seasons?\s*\d+|s\d+|episodes?\s*\d+|eps?\s*\d+|e\d+|parts?\s*\d+|pt\s*\d+)\b/gi, ' ')
        .replace(/[^a-z0-9]+/g, ' ')
        .trim();
      const words = coreTitle.split(/\s+/).filter(Boolean);
      if (words.length > 0) {
        const titlePattern = new RegExp(words.map(w => w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('[^a-z0-9]*'), 'gi');
        testStr = testStr.replace(titlePattern, 'TITLEREPLACED');
      }
      testStr = testStr.replace(/trailer[^a-z0-9]*park[^a-z0-9]*boys/gi, 'TITLEREPLACED');
      testStr = testStr.replace(/the[^a-z0-9]*trailer/gi, 'TITLEREPLACED');
    } else if (!expectedTitle) {
      // Even if expectedTitle is omitted, protect known legitimate titles containing "trailer"
      testStr = testStr.replace(/trailer[^a-z0-9]+park[^a-z0-9]+boys/gi, 'TITLEREPLACED');
      testStr = testStr.replace(/the[^a-z0-9]+trailer(?=[^a-z0-9]+(?:19\d\d|20\d\d|x264|720p|1080p|hd|vj|full|\.))/gi, 'TITLEREPLACED');
    }

    // 5. Hostname check for BunnyCDN trailer zone with trailer in name
    if (testStr.includes('trailer') && testStr.includes('b-cdn.net')) return true;

    // 6. Token boundary checks for trailer
    // Enforce token boundaries /(?:^|[._\/-])trailer(?:s)?(?:[._\/-]|\.mp4|\.m3u8|$)/i
    const trailerTokenRegex = /(?:^|[._\/-])trailer(?:s)?(?:[._\/-]|\.mp4|\.m3u8|$)/i;
    if (trailerTokenRegex.test(testStr)) {
      return true;
    }

    // 7. Teaser token boundary checks (_teaser, /teaser, teaser.mp4)
    const teaserTokenRegex = /(?:^|[._\/-])teaser(?:s)?(?:[._\/-]|\.mp4|\.m3u8|$)/i;
    if (teaserTokenRegex.test(testStr)) {
      return true;
    }

    // 8. Sample token boundary checks (_sample, /sample, sample.mp4)
    const sampleTokenRegex = /(?:^|[._\/-])sample(?:s)?(?:[._\/-]|\.mp4|\.m3u8|$)/i;
    if (sampleTokenRegex.test(testStr)) {
      return true;
    }

    // 9. Promo token boundary checks (_promo, /promo, promo.mp4)
    const promoTokenRegex = /(?:^|[._\/-])promo(?:s)?(?:[._\/-]|\.mp4|\.m3u8|$)/i;
    if (promoTokenRegex.test(testStr)) {
      return true;
    }

    return false;
  }

  /**
   * Proxies media video stream with HTTP 206 Partial Content and Range headers
   */
  public static async handleProxyStream(req: Request, res: Response): Promise<void> {
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

    // Auto-resolve Twolekede preview HTML pages to genuine media stream URLs
    if (targetUrl.includes('preview.php?id=') || targetUrl.includes('twolekede.com/preview')) {
      const match = targetUrl.match(/id=(\d+)/);
      if (match) {
        const direct = await this.resolveTwolekedeStreamById(match[1]);
        if (direct) {
          targetUrl = direct;
        }
      }
    }

    // Strict trailer rejection: never proxy trailers or promo clips
    const titleParam = req.query.title ? String(req.query.title) : undefined;
    if (this.isTrailerOrPromoUrl(targetUrl, titleParam)) {
      res.status(400).json({
        error: 'Trailer and promo playback blocked. Munowatch exclusively streams full-length Ugandan translated movies.'
      });
      return;
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

      // Munowatch BunnyCDN streams (/port/str/...) use Base64 encrypted tokens, resolved directly from matched title watch pages
      if (cleanUrl.includes('/port/str/') || cleanUrl.includes('.b-cdn.net/port/')) return true;

      // Generic stop words that should NEVER be used alone to match a video URL
      const stopWords = new Set([
        'part', 'the', 'movie', 'full', 'vj', 'series', 'season', 'and', 'with', 'from',
        'this', 'that', 'translated', 'luganda', 'twolekede', 'secure', 'php', 'file',
        'movies', 'x264', '1080p', '720p', '480p', 'mp4', 'm3u8', 'all', 'about', 'a', 'an',
        'love', 'story', 'nice', 'girl', 'girls', 'jr', 'junior', 'emmy', 'icep', 'jingo'
      ]);

      const substantiveWords = expectedTitle
        .toLowerCase()
        .replace(/\[.*?\]|\(.*?\)/g, ' ')
        .replace(/\b(?:by\s+vj|vj)\s+[a-z0-9_.-]*.*$/gi, ' ')
        .replace(/\b(?:19\d{2}|20\d{2})\b/g, ' ')
        .replace(/\b(?:jr|junior|icep|emmy|jingo|ulio|mark|nelly|kimuli)\b/g, ' ')
        .split(/[^a-z0-9]+/)
        .map(w => (w.endsWith('s') && w.length > 3 && !w.endsWith('ss')) ? w.slice(0, -1) : w)
        .filter(w => w.length >= 3 && !stopWords.has(w));

      if (substantiveWords.length === 0) return true;

      // Ensure that at least one distinct, substantive token appears in the video URL
      return substantiveWords.some(w => cleanUrl.includes(w));
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
    if (words.length > 0 && words[words.length - 1].endsWith('s') && words[words.length - 1].length > 3 && !words[words.length - 1].endsWith('ss')) {
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

  public static unescapeHtml(str: string): string {
    if (!str) return '';
    return str
      .replace(/&amp;/g, '&')
      .replace(/&#38;/g, '&')
      .replace(/&quot;/g, '"')
      .replace(/&#039;/g, "'")
      .replace(/&apos;/g, "'")
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>');
  }

  /**
   * Battle-tested comparison verifying that two titles represent the same movie
   * Ported from MunowatchHtmlParser.kt in Android TV to eliminate all mismatches
   */
  public static titlesMatch(a: string, b: string): boolean {
    const unescapeHtml = this.unescapeHtml;

    const cleanForMatching = (t: string): string => {
      let s = unescapeHtml(t).toLowerCase();
      s = s.replace(/\[.*?\]|\(.*?\)/g, ' ');
      s = s.replace(/\b(?:by\s+vj|vj)\s+[a-z0-9_.-]*.*$/gi, ' ');
      s = s.replace(/\b(?:19\d{2}|20\d{2})\b/g, ' ');
      s = s.replace(/\b(?:jr|junior|icep|emmy|jingo|ulio|mark|nelly|kimuli)\b/g, ' ');
      // Strip series season descriptors (e.g. "s01", "s1", "s02", "season 1", "season 2", etc.)
      s = s.replace(/\b(?:seasons?\s*\d+|s\d+)\b/gi, ' ');
      // Strip episode descriptors (e.g. "episode 1", "ep 1", "e01", "eps 1")
      s = s.replace(/\b(?:episodes?\s*\d+|eps?\s*\d+|e\d+)\b/gi, ' ');
      s = s.replace(/[:–—|]/g, ' ');
      // Standardize Roman numerals at word boundaries
      s = s.replace(/\biv\b/g, '4');
      s = s.replace(/\biii\b/g, '3');
      s = s.replace(/\bii\b/g, '2');
      s = s.replace(/\bi\b/g, '1');
      s = s.replace(/[^a-z0-9]+/g, ' ');

      const words = s.split(/\s+/).filter(Boolean).map(w => {
        if (w.endsWith('s') && w.length > 3 && !w.endsWith('ss')) {
          return w.slice(0, -1);
        }
        return w;
      });
      return words.join(' ').trim();
    };

    const ca = cleanForMatching(a);
    const cb = cleanForMatching(b);
    if (!ca || !cb) return false;
    if (ca === cb) return true;

    // Compressed match (handles spacing differences: spiderman vs spider man)
    if (ca.replace(/\s+/g, '') === cb.replace(/\s+/g, '')) return true;

    // Article-stripped match (e.g. "The Beast" vs "Beast")
    const noArtA = ca.replace(/^(?:the|a|an)\s+/, '');
    const noArtB = cb.replace(/^(?:the|a|an)\s+/, '');
    if (noArtA && (noArtA === noArtB || noArtA.replace(/\s+/g, '') === noArtB.replace(/\s+/g, ''))) {
      return true;
    }

    // Strict numeric check: if one title has sequel / part numbers and other differs, REJECT!
    const numRegex = /\b(?:pt\s*)?(\d+)\b/g;
    const numsA = Array.from(ca.matchAll(numRegex), m => m[1]);
    const numsB = Array.from(cb.matchAll(numRegex), m => m[1]);
    const isPart1Match = (numsA.length === 1 && numsA[0] === '1' && numsB.length === 0) ||
                         (numsB.length === 1 && numsB[0] === '1' && numsA.length === 0);
    if (!isPart1Match && numsA.join(',') !== numsB.join(',')) {
      return false;
    }

    const tokensA = ca.split(/\s+/).filter(Boolean);
    const tokensB = cb.split(/\s+/).filter(Boolean);
    const setA = new Set(tokensA);
    const setB = new Set(tokensB);

    if (tokensA.length === tokensB.length && tokensA.every(t => setB.has(t))) {
      return true;
    }

    const ignorableExtras = new Set([
      "the", "of", "and", "a", "an", "in", "on", "at", "for", "from", "to",
      "pt", "part", "1", "hd", "full", "movie", "translated", "luganda", "vj",
      "jr", "twolekede", "season", "seasons", "s01", "s02", "s03", "s04", "s05",
      "ep", "episode", "eps", "series"
    ]);

    let intersectionCount = 0;
    for (const t of setA) {
      if (setB.has(t)) intersectionCount++;
    }

    const minSize = Math.min(setA.size, setB.size);
    if (minSize >= 1 && intersectionCount === minSize) {
      // One is a strict subset: ONLY accept if EVERY extra token is ignorable!
      const union = new Set([...setA, ...setB]);
      let allExtrasIgnorable = true;
      for (const t of union) {
        if (!setA.has(t) || !setB.has(t)) {
          if (!ignorableExtras.has(t)) {
            allExtrasIgnorable = false;
            break;
          }
        }
      }
      if (allExtrasIgnorable) return true;
    }

    return false;
  }

  /**
   * Resolves direct media streaming link for any movie or series title on demand
   */
  public static async resolveBunnyCdnStream(title: string, vj?: string): Promise<string | null> {
    if (!title || title.trim().length < 2) return null;
    const cleanTitle = title
      .replace(/\[.*?\]|\(.*?\)/g, '')
      .replace(/\b(?:by\s+vj|vj)\s+[a-z0-9_.-]*.*$/gi, '')
      .replace(/\s+/g, ' ')
      .trim();

    const normTargetVj = vj ? vj.toLowerCase().replace(/^(?:vj\s*|vj-)/i, '').trim() : '';

    const candidateQueries = this.generateCandidateQueries(cleanTitle);
    for (const q of candidateQueries.slice(0, 3)) {
      try {
        const resp = await fetch(`https://munowatch.com/search?q=${encodeURIComponent(q)}`, {
          headers: {
            'Cookie': this.MUNO_SESSION_COOKIE,
            'Referer': 'https://munowatch.com/',
            'User-Agent': this.DEFAULT_USER_AGENT
          },
          signal: AbortSignal.timeout(6000)
        });

        if (resp.ok) {
          const html = await resp.text();
          const cardRegex = /<a[^>]+href="(\/twolekede\?v=[^"'\s]+)"[^>]*>([\s\S]*?)<\/a>/g;
          let cm: RegExpExecArray | null;
          while ((cm = cardRegex.exec(html)) !== null) {
            const watchHref = cm[1].replace(/&amp;/g, '&');
            const inner = cm[2];
            const imgM = inner.match(/<img[^>]+alt="([^"']+)"/i);
            const vjM = inner.match(/<p[^>]*>([^<]+)<\/p>/i);
            const cardTitle = imgM ? this.unescapeHtml(imgM[1]).trim() : '';
            const cardVj = vjM ? vjM[1].trim() : '';
            const normCardVj = cardVj.toLowerCase().replace(/^(?:vj\s*|vj-)/i, '').trim();

            if (normTargetVj && normCardVj && !normCardVj.includes(normTargetVj) && !normTargetVj.includes(normCardVj)) {
              continue;
            }

            if (this.titlesMatch(title, cardTitle)) {
              const bcdnStream = await this.resolveMunowatchWatchStream(watchHref);
              if (bcdnStream && !this.isTrailerOrPromoUrl(bcdnStream, title)) {
                return bcdnStream;
              }
            }
          }
        }
      } catch {}
    }
    return null;
  }

  public static async resolveMediaStream(id: string, title?: string, vj?: string): Promise<string | null> {
    const isExplicitTwolekede = id.startsWith('tk_') || (!id.startsWith('myvj_') && !id.startsWith('muno_') && /^\d+$/.test(id));
    const cleanId = id.replace(/^(myvj_|tk_|ep_|muno_)/, '');

    // 0. If ID is muno_ or watch path is provided, resolve direct Munowatch 1080p BunnyCDN stream
    if (id.startsWith('muno_')) {
      const direct = await this.resolveMunowatchWatchStream(`/twolekede?mid=${cleanId}`);
      if (direct && !this.isTrailerOrPromoUrl(direct, title)) {
        return direct;
      }
    }

    // 1. Direct explicit Twolekede ID resolution FIRST (Guarantees exact movie from poster/ID with zero hijacking)
    if (isExplicitTwolekede && /^\d+$/.test(cleanId)) {
      const resolved = await this.resolveTwolekedeStreamById(cleanId, title);
      if (resolved && !this.isTrailerOrPromoUrl(resolved, title) && this.urlMatchesTitle(resolved, title)) {
        return resolved;
      }
    }

    // 1.5. If cleanId itself is numeric and resolves to Twolekede preview, try it
    if (/^\d+$/.test(cleanId)) {
      const resolved = await this.resolveTwolekedeStreamById(cleanId, title);
      if (resolved && !this.isTrailerOrPromoUrl(resolved, title) && this.urlMatchesTitle(resolved, title)) {
        return resolved;
      }
    }

    // 2. Search Munowatch catalog directly by title (with strict VJ verification)
    if (title && title.trim().length >= 2) {
      const bcdnStream = await this.resolveBunnyCdnStream(title, vj);
      if (bcdnStream) return bcdnStream;
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
            const cardRegex = /<a href="(?:moviedetails|seriesdetails|preview)\.php\?id=(\d+)"[^>]*>[\s\S]*?<h3 class="content-title">([^<]*)<\/h3>/g;
            let m: RegExpExecArray | null;

            while ((m = cardRegex.exec(html)) !== null) {
              const matchedId = m[1];
              const cardTitle = this.unescapeHtml(m[2]).trim();

              if (this.titlesMatch(title, cardTitle)) {
                const stream = await this.resolveTwolekedeStreamById(matchedId, title);
                if (stream && !this.isTrailerOrPromoUrl(stream, title) && this.urlMatchesTitle(stream, title)) {
                  return stream;
                }
              }
            }

            // Fallback pattern matching
            const altCardRegex = /<a href="(?:moviedetails|preview)\.php\?id=(\d+)"[^>]*alt="([^"]*)"/g;
            let altM: RegExpExecArray | null;
            while ((altM = altCardRegex.exec(html)) !== null) {
              const matchedId = altM[1];
              const cardTitle = this.unescapeHtml(altM[2]).trim();
              if (this.titlesMatch(title, cardTitle)) {
                const stream = await this.resolveTwolekedeStreamById(matchedId, title);
                if (stream && !this.isTrailerOrPromoUrl(stream, title) && this.urlMatchesTitle(stream, title)) {
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
    if (ugaStream && !this.isTrailerOrPromoUrl(ugaStream, title)) {
      return ugaStream;
    }

    return null;
  }

  /**
   * Resolves BunnyCDN MP4 stream from UgaWatch API v3 for titles not indexed on Twolekede
   */
  private static async resolveUgaWatchStream(id: string, title?: string): Promise<string | null> {
    const cleanId = id.replace(/^(myvj_|tk_|ep_)/, '');
    const endpoints = [
      'https://ugawatch.com/api-v3/mobile/get-movie-details-min.php',
      'https://myvj.net/api-v3/mobile/get-movie-details-min.php',
      'https://ugawatch.com/api-v3/mobile/get-serie-details-min.php',
      'https://myvj.net/api-v3/mobile/get-serie-details-min.php'
    ];

    const attempts: URLSearchParams[] = [];

    if (/^\d+$/.test(cleanId)) {
      const p = new URLSearchParams();
      p.append('source', 'next_js');
      p.append('movie_id', cleanId);
      p.append('serie_id', cleanId);
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
        p1.append('serie_slug', cleanSlug);
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
            if (data && (data.status === 0 || data.status === '0')) {
              const movie = data.movie || data.serie || data.data || {};
              // Explicitly eliminate movie.trailer and ep.trailer
              const file = movie.bunny_file_name ||
                (Array.isArray(data.episodes) && data.episodes[0]?.bunny_file_name) ||
                movie.video;

              if (file && typeof file === 'string' && file.trim().length > 3) {
                const cleanFile = file.replace(/\\/g, '').trim();
                // Never allow trailers or construct trailer256.b-cdn.net
                if (this.isTrailerOrPromoUrl(cleanFile, title)) {
                  continue;
                }
                // Only accept genuine full http streams that are not trailers
                if (cleanFile.startsWith('http') && this.urlMatchesTitle(cleanFile, title)) {
                  return cleanFile;
                }
              }
            }
          }
        } catch {}
      }
    }

    return null;
  }

  public static async resolveTwolekedeStreamById(id: string, expectedTitle?: string): Promise<string | null> {
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
          if (rawUrl && !rawUrl.includes('index.php') && !this.isTrailerOrPromoUrl(rawUrl, expectedTitle)) {
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
      if (loc && (loc.includes('secure.php') || loc.includes('.mp4') || loc.includes('movviez.com')) && !loc.includes('index.php') && !this.isTrailerOrPromoUrl(loc, expectedTitle)) {
        return loc;
      }
    } catch {}

    return null;
  }

  /**
   * Discovers the closest available verified full Ugandan translated release when a sequel/variant is unavailable
   */
  public static async findAlternativeMovie(title: string): Promise<{ id: string; title: string } | null> {
    if (!title || title.trim().length < 2) return null;
    const clean = title.replace(/\[.*?\]|\(.*?\)/g, '').trim();
    // Try base title, e.g. "Jumanji: The Next Level" -> "Jumanji"
    const baseTitle = clean.split(/[:\-–—]/)[0].trim();
    if (baseTitle && baseTitle.length >= 3 && baseTitle.toLowerCase() !== clean.toLowerCase()) {
      try {
        const resp = await fetch(`https://twolekede.com/search.php?q=${encodeURIComponent(baseTitle)}`, {
          headers: {
            'Referer': 'https://twolekede.com/',
            'User-Agent': this.DEFAULT_USER_AGENT
          },
          signal: AbortSignal.timeout(5000)
        });
        if (resp.ok) {
          const html = await resp.text();
          const cardRegex = /<a href="(?:moviedetails|seriesdetails|preview)\.php\?id=(\d+)"[^>]*>[\s\S]*?<h3 class="content-title">([^<]*)<\/h3>/g;
          let m: RegExpExecArray | null;
          while ((m = cardRegex.exec(html)) !== null) {
            const matchedId = m[1];
            const cardTitle = this.unescapeHtml(m[2]).trim();
            if (cardTitle.toLowerCase().includes(baseTitle.toLowerCase())) {
              return { id: `tk_${matchedId}`, title: cardTitle };
            }
          }
        }
      } catch {}
    }
    return null;
  }
}
