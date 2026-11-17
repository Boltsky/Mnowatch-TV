import { MediaItem, CatalogRail, PlaybackServer } from '../types';
import { RICH_CATALOG, VJ_LIST, GENRE_LIST } from '../providers/richCatalog.data';

interface QueryOptions {
  page?: number;
  perPage?: number;
  year?: string;
  vj?: string;
  genre?: string;
  sort?: string;
}

export class CatalogService {
  private static cache = new Map<string, { data: any; expiresAt: number }>();
  private static CACHE_TTL_MS = 20 * 60 * 1000; // 20 minutes

  private static getFromCache<T>(key: string): T | null {
    const entry = this.cache.get(key);
    if (!entry) return null;
    if (Date.now() > entry.expiresAt) {
      this.cache.delete(key);
      return null;
    }
    return entry.data as T;
  }

  private static setInCache(key: string, data: any, ttlMs: number = this.CACHE_TTL_MS): void {
    // Keep cache under 500 items
    if (this.cache.size > 500) {
      const oldestKey = this.cache.keys().next().value;
      if (oldestKey) this.cache.delete(oldestKey);
    }
    this.cache.set(key, { data, expiresAt: Date.now() + ttlMs });
  }

  /**
   * Fetches live filtered movies and series from UgaWatch / MyVJ API v3
   */
  public static async fetchUgaWatchCatalog(
    mediaType: number = 1,
    options: QueryOptions = {}
  ): Promise<MediaItem[]> {
    const page = options.page || 1;
    const perPage = options.perPage || 24;
    const cacheKey = `ugawatch:${mediaType}:${page}:${perPage}:${options.year || ''}:${options.vj || ''}:${options.genre || ''}`;

    const cached = this.getFromCache<MediaItem[]>(cacheKey);
    if (cached) return cached;

    const urls = [
      'https://ugawatch.com/api-v3/mobile/get-filtered.php',
      'https://myvj.net/api-v3/mobile/get-filtered.php'
    ];

    const bodyParams = new URLSearchParams();
    bodyParams.append('source', 'next_js');
    bodyParams.append('media_type', mediaType.toString());
    bodyParams.append('page', page.toString());
    bodyParams.append('per_page', perPage.toString());

    if (options.year && options.year !== 'all') {
      bodyParams.append('release_year', options.year.trim());
      bodyParams.append('year', options.year.trim());
    }

    if (options.genre) {
      const g = options.genre.toLowerCase().trim().replace(/\s+/g, '-');
      bodyParams.append('category_id', g);
    }

    if (options.vj) {
      let v = options.vj.toLowerCase().trim();
      if (!v.startsWith('vj-')) {
        v = 'vj-' + v.replace(/^vj\s*/i, '').replace(/[^a-z0-9]/g, '-');
      }
      bodyParams.append('vj_id', v);
    }

    for (const url of urls) {
      try {
        const resp = await fetch(url, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
            'Referer': 'https://ugawatch.com/'
          },
          body: bodyParams.toString(),
          signal: AbortSignal.timeout(8000)
        });

        if (!resp.ok) continue;
        const text = await resp.text();
        if (!text || text.startsWith('<')) continue;

        const json = JSON.parse(text);
        if (json.status !== 0 && json.status !== '0') continue;

        const rawList = json.medias || json.items || json.list || json.movies || [];
        if (!Array.isArray(rawList)) continue;

        const items: MediaItem[] = [];
        for (const obj of rawList) {
          const rowId = String(obj.row_id || obj.id || '').trim();
          const title = String(obj.title || '').trim();
          if (!rowId || !title) continue;

          const munoImg = obj.img_port_muno_file_name || '';
          const tmdbPoster = obj.tmdb_poster_path || '';
          let posterUrl = '';
          if (tmdbPoster && tmdbPoster !== 'null') {
            const cleanP = tmdbPoster.startsWith('/') ? tmdbPoster : `/${tmdbPoster}`;
            posterUrl = `https://image.tmdb.org/t/p/w500${cleanP}`;
          } else if (munoImg && munoImg !== 'null') {
            posterUrl = munoImg.startsWith('http') ? munoImg : `https://res-watch.b-cdn.net/1/${munoImg}`;
          }

          const tmdbBackdrop = obj.tmdb_backdrop_path || '';
          let backdropUrl = posterUrl;
          if (tmdbBackdrop && tmdbBackdrop !== 'null') {
            const cleanB = tmdbBackdrop.startsWith('/') ? tmdbBackdrop : `/${tmdbBackdrop}`;
            backdropUrl = `https://image.tmdb.org/t/p/original${cleanB}`;
          }

          let vj = obj.vj && obj.vj !== '-' && obj.vj !== 'null' ? obj.vj : 'VJ Junior';
          if (!vj.toLowerCase().includes('vj')) vj = `VJ ${vj}`;

          let year = '';
          if (options.year && options.year !== 'all') {
            year = options.year;
          } else {
            const relDate = String(obj.tmdb_release_date || obj.date_added || '');
            const match = relDate.match(/\b(19\d{2}|20\d{2})\b/);
            year = match ? match[1] : '2024';
          }

          const quality = obj.quality_str && obj.quality_str !== 'null' ? obj.quality_str : '1080p FHD';
          const genre = obj.categories && obj.categories !== '-' ? obj.categories : (options.genre || 'Action / Drama');
          const description = obj.about || obj.overview || obj.plot || `Luganda translated blockbuster brought to you by ${vj}.`;

          items.push({
            id: `myvj_${rowId}`,
            title: title,
            type: mediaType === 2 ? 'series' : 'movie',
            vj: vj,
            genre: genre,
            year: year,
            rating: '8.8',
            duration: mediaType === 2 ? 'Multi-Season' : '2h 05m',
            description: description,
            posterUrl: posterUrl,
            backdropUrl: backdropUrl,
            streamUrl: '', // Resolved on demand
            isSeries: mediaType === 2
          });
        }

        if (items.length > 0) {
          this.setInCache(cacheKey, items);
          return items;
        }
      } catch {
        // Continue to fallback
      }
    }

    return [];
  }

  /**
   * Scrapes Twolekede live catalog (movies.php / series.php)
   */
  public static async fetchTwolekedeCatalog(isSeries: boolean = false, page: number = 1): Promise<MediaItem[]> {
    const cacheKey = `twolekede:${isSeries}:${page}`;
    const cached = this.getFromCache<MediaItem[]>(cacheKey);
    if (cached) return cached;

    const targetUrl = isSeries
      ? `https://twolekede.com/series.php?page=${page}&sort=new&letter=`
      : `https://twolekede.com/movies.php?page=${page}&sort=new&letter=`;

    try {
      const resp = await fetch(targetUrl, {
        headers: {
          'Referer': 'https://twolekede.com/',
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36'
        },
        signal: AbortSignal.timeout(8000)
      });

      if (!resp.ok) return [];
      const html = await resp.text();
      const items: MediaItem[] = [];

      // Extract grid-item / content-item links
      const itemRegex = /<div class="grid-item">[\s\S]*?<a href="(moviedetails|seriesdetails)\.php\?id=(\d+)"[\s\S]*?<img src="([^"]+)"\s+alt="([^"]+)"[\s\S]*?<div class="item-info">[\s\S]*?<h3>([^<]+)<\/h3>[\s\S]*?<span class="category">([^<]+)<\/span>/g;
      let match: RegExpExecArray | null;

      while ((match = itemRegex.exec(html)) !== null) {
        const id = match[2];
        let poster = match[3];
        const title = match[5].trim();
        let vj = match[6].trim() || 'VJ Junior';
        if (!vj.toLowerCase().includes('vj')) vj = `VJ ${vj}`;

        if (!poster.startsWith('http')) {
          poster = `https://twolekede.com/${poster.replace(/^\/+/, '')}`;
        }

        items.push({
          id: `tk_${id}`,
          title: title,
          type: isSeries ? 'series' : 'movie',
          vj: vj,
          genre: 'Action / Adventure',
          year: '2024',
          rating: '8.7',
          duration: isSeries ? 'TV Series' : '2h 10m',
          description: `Luganda translation by ${vj} available on Twolekede.`,
          posterUrl: poster,
          backdropUrl: poster,
          streamUrl: '',
          isSeries: isSeries
        });
      }

      if (items.length > 0) {
        this.setInCache(cacheKey, items);
        return items;
      }
    } catch {
      // Ignore
    }

    return [];
  }

  /**
   * High-level getMovies with dynamic pagination and filtering
   */
  public static async getMovies(options: QueryOptions, baseUrl: string): Promise<{ items: MediaItem[]; count: number; page: number }> {
    const page = options.page || 1;
    const perPage = options.perPage || 24;

    // Concurrently fetch from UgaWatch API v3 and Twolekede
    let [liveUga, liveTwolekede] = await Promise.all([
      this.fetchUgaWatchCatalog(1, options),
      !options.year && !options.vj && !options.genre ? this.fetchTwolekedeCatalog(false, page) : Promise.resolve([])
    ]);

    let combined = [...liveUga, ...liveTwolekede];

    // Fallback to rich catalog if both live upstream servers are unreachable
    if (combined.length === 0) {
      combined = [...RICH_CATALOG.filter(i => !i.isSeries)];
      if (options.year && options.year !== 'all') {
        combined = combined.filter(i => i.year === options.year);
      }
      if (options.vj) {
        const cleanVj = options.vj.toLowerCase().replace('vj-', '').replace('vj', '').trim();
        combined = combined.filter(i => i.vj.toLowerCase().includes(cleanVj));
      }
      if (options.genre) {
        const cleanG = options.genre.toLowerCase().trim();
        combined = combined.filter(i => i.genre.toLowerCase().includes(cleanG));
      }
    }

    // Deduplicate by normalized title
    const seen = new Set<string>();
    const deduplicated: MediaItem[] = [];
    for (const item of combined) {
      const norm = item.title.toLowerCase().replace(/[^a-z0-9]/g, '');
      if (!seen.has(norm)) {
        seen.add(norm);
        deduplicated.push(this.enrichWithServers(item, baseUrl));
      }
    }

    // Apply sort if requested
    if (options.sort === 'OLD_MOVIES') {
      deduplicated.sort((a, b) => parseInt(a.year || '2024') - parseInt(b.year || '2024'));
    } else if (options.sort === 'RANDOM') {
      deduplicated.sort((a, b) => a.title.localeCompare(b.title));
    }

    return {
      items: deduplicated,
      count: deduplicated.length,
      page: page
    };
  }

  /**
   * High-level getSeries with dynamic pagination
   */
  public static async getSeries(options: QueryOptions, baseUrl: string): Promise<{ items: MediaItem[]; count: number; page: number }> {
    const page = options.page || 1;

    let [liveUga, liveTwolekede] = await Promise.all([
      this.fetchUgaWatchCatalog(2, options),
      this.fetchTwolekedeCatalog(true, page)
    ]);

    let combined = [...liveUga, ...liveTwolekede];

    if (combined.length === 0) {
      combined = [...RICH_CATALOG.filter(i => i.isSeries)];
    }

    const seen = new Set<string>();
    const deduplicated: MediaItem[] = [];
    for (const item of combined) {
      const norm = item.title.toLowerCase().replace(/[^a-z0-9]/g, '');
      if (!seen.has(norm)) {
        seen.add(norm);
        deduplicated.push(this.enrichWithServers(item, baseUrl));
      }
    }

    return {
      items: deduplicated,
      count: deduplicated.length,
      page: page
    };
  }

  /**
   * Home rails populated from live database
   */
  public static async getHomeRails(baseUrl: string): Promise<CatalogRail[]> {
    const cacheKey = 'home_rails';
    const cached = this.getFromCache<CatalogRail[]>(cacheKey);
    if (cached) return cached;

    // Fetch page 1 of latest uploads & series
    const [latestMovies, seriesList, vjJunior, vjEmmy] = await Promise.all([
      this.fetchUgaWatchCatalog(1, { page: 1, perPage: 18 }),
      this.fetchUgaWatchCatalog(2, { page: 1, perPage: 12 }),
      this.fetchUgaWatchCatalog(1, { page: 1, perPage: 12, vj: 'vj-junior' }),
      this.fetchUgaWatchCatalog(1, { page: 1, perPage: 12, vj: 'vj-emmy' })
    ]);

    const attachProxy = (items: MediaItem[]) => items.map(i => this.enrichWithServers(i, baseUrl));

    const rails: CatalogRail[] = [
      {
        id: "rail_trending",
        title: "🔥 Newly Uploaded Translated Movies",
        items: attachProxy(latestMovies.length > 0 ? latestMovies.slice(0, 12) : RICH_CATALOG.filter(i => !i.isSeries).slice(0, 8))
      },
      {
        id: "rail_junior",
        title: "🎬 VJ Junior Exclusive Translations",
        vj: "VJ Junior",
        items: attachProxy(vjJunior.length > 0 ? vjJunior : RICH_CATALOG.filter(i => i.vj.includes("Junior")))
      },
      {
        id: "rail_emmy",
        title: "💥 VJ Emmy Cinematic Hits",
        vj: "VJ Emmy",
        items: attachProxy(vjEmmy.length > 0 ? vjEmmy : RICH_CATALOG.filter(i => i.vj.includes("Emmy")))
      },
      {
        id: "rail_series",
        title: "📺 Ugandan Translated TV Series (Multi-Season)",
        items: attachProxy(seriesList.length > 0 ? seriesList : RICH_CATALOG.filter(i => i.isSeries))
      },
      {
        id: "rail_jingo",
        title: "⚡ VJ Jingo Action Vault",
        vj: "VJ Jingo",
        items: attachProxy(RICH_CATALOG.filter(i => i.vj.includes("Jingo")))
      },
      {
        id: "rail_action",
        title: "🥊 High-Octane Action & Thrillers",
        genre: "Action",
        items: attachProxy(latestMovies.length > 12 ? latestMovies.slice(12, 18) : RICH_CATALOG.filter(i => i.genre.includes("Action")))
      }
    ];

    this.setInCache(cacheKey, rails, 10 * 60 * 1000); // 10 min cache
    return rails;
  }

  public static getVjs() {
    return VJ_LIST;
  }

  public static getGenres() {
    return GENRE_LIST;
  }

  /**
   * Live search across UgaWatch and Twolekede
   */
  public static async search(query: string, baseUrl: string): Promise<MediaItem[]> {
    if (!query || query.trim().length === 0) return [];
    const q = query.trim();
    const cacheKey = `search:${q.toLowerCase()}`;
    const cached = this.getFromCache<MediaItem[]>(cacheKey);
    if (cached) return cached;

    const results: MediaItem[] = [];

    // 1. Search UgaWatch API v3
    try {
      const resp = await fetch('https://ugawatch.com/api-v3/mobile/search.php', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36',
          'Referer': 'https://ugawatch.com/'
        },
        body: new URLSearchParams({ source: 'next_js', search_term: q }).toString(),
        signal: AbortSignal.timeout(6000)
      });

      if (resp.ok) {
        const text = await resp.text();
        if (text && !text.startsWith('<')) {
          const json = JSON.parse(text);
          const rawItems = json.medias || json.items || json.list || [];
          for (const obj of rawItems) {
            const rowId = String(obj.row_id || obj.id || '');
            const title = String(obj.title || '');
            if (!rowId || !title) continue;

            const munoImg = obj.img_port_muno_file_name || '';
            const tmdbPoster = obj.tmdb_poster_path || '';
            let posterUrl = '';
            if (tmdbPoster && tmdbPoster !== 'null') {
              posterUrl = `https://image.tmdb.org/t/p/w500${tmdbPoster.startsWith('/') ? tmdbPoster : '/' + tmdbPoster}`;
            } else if (munoImg && munoImg !== 'null') {
              posterUrl = munoImg.startsWith('http') ? munoImg : `https://res-watch.b-cdn.net/1/${munoImg}`;
            }

            let vj = obj.vj && obj.vj !== '-' ? obj.vj : 'VJ Junior';
            if (!vj.toLowerCase().includes('vj')) vj = `VJ ${vj}`;

            results.push({
              id: `myvj_${rowId}`,
              title: title,
              type: obj.media_type === 2 ? 'series' : 'movie',
              vj: vj,
              genre: obj.categories || 'Translated',
              year: '2024',
              rating: '8.8',
              duration: '2h 00m',
              description: obj.about || obj.overview || `Luganda translation by ${vj}.`,
              posterUrl: posterUrl,
              backdropUrl: posterUrl,
              streamUrl: '',
              isSeries: obj.media_type === 2
            });
          }
        }
      }
    } catch {}

    // 2. Search Twolekede
    try {
      const resp = await fetch(`https://twolekede.com/search.php?q=${encodeURIComponent(q)}`, {
        headers: {
          'Referer': 'https://twolekede.com/',
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36'
        },
        signal: AbortSignal.timeout(6000)
      });

      if (resp.ok) {
        const html = await resp.text();
        const tkRegex = /<a href="(moviedetails|seriesdetails)\.php\?id=(\d+)"[^>]*>[\s\S]*?<img src="([^"]+)"[\s\S]*?<div class="content-title">([^<]+)<\/div>[\s\S]*?<span class="category-badge">([^<]+)<\/span>/g;
        let m: RegExpExecArray | null;
        while ((m = tkRegex.exec(html)) !== null) {
          const typeStr = m[1];
          const id = m[2];
          let poster = m[3];
          const title = m[4].trim();
          let vj = m[5].trim();
          if (!vj.toLowerCase().includes('vj')) vj = `VJ ${vj}`;
          if (!poster.startsWith('http')) poster = `https://twolekede.com/${poster.replace(/^\/+/, '')}`;

          results.push({
            id: `tk_${id}`,
            title: title,
            type: typeStr === 'seriesdetails' ? 'series' : 'movie',
            vj: vj,
            genre: 'Action / Adventure',
            year: '2024',
            rating: '8.8',
            duration: '2h 05m',
            description: `Luganda translation by ${vj}.`,
            posterUrl: poster,
            backdropUrl: poster,
            streamUrl: '',
            isSeries: typeStr === 'seriesdetails'
          });
        }
      }
    } catch {}

    // 3. Fallback to rich catalog search if results are empty
    if (results.length === 0) {
      const lowerQ = q.toLowerCase();
      const local = RICH_CATALOG.filter(i =>
        i.title.toLowerCase().includes(lowerQ) ||
        i.vj.toLowerCase().includes(lowerQ) ||
        i.genre.toLowerCase().includes(lowerQ)
      );
      results.push(...local);
    }

    // Deduplicate
    const seen = new Set<string>();
    const deduplicated: MediaItem[] = [];
    for (const item of results) {
      const norm = item.title.toLowerCase().replace(/[^a-z0-9]/g, '');
      if (!seen.has(norm)) {
        seen.add(norm);
        deduplicated.push(this.enrichWithServers(item, baseUrl));
      }
    }

    this.setInCache(cacheKey, deduplicated, 10 * 60 * 1000);
    return deduplicated;
  }

  public static enrichWithServers(item: MediaItem, baseUrl: string): MediaItem {
    const rawStream = item.streamUrl || '';
    const proxyStreamUrl = rawStream ? `${baseUrl}/api/v1/stream/proxy?url=${encodeURIComponent(rawStream)}` : '';

    const servers: PlaybackServer[] = [
      {
        id: "server-cdn",
        name: "Server 1 (BunnyCDN High-Speed 1080p)",
        quality: "1080p FHD",
        streamUrl: rawStream,
        isProxy: false
      },
      {
        id: "server-proxy",
        name: "Server 2 (Munowatch Web Streamer - CORS Safe)",
        quality: "1080p Full HD",
        streamUrl: proxyStreamUrl,
        isProxy: true
      }
    ];

    return {
      ...item,
      streamUrl: proxyStreamUrl || rawStream,
      servers: servers
    };
  }
}
