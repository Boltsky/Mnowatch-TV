import { MediaItem, CatalogRail, PlaybackServer } from '../types';
import { RICH_CATALOG, VJ_LIST, GENRE_LIST } from '../providers/richCatalog.data';
import { StreamProxyService } from './streamProxy.service';

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

  private static mapVjToTwolekedeName(vjName: string): string {
    const clean = vjName.trim();
    const knownMap: Record<string, string> = {
      'vj ice p': 'VJ ICEP',
      'vj icep': 'VJ ICEP',
      'vj uncle t': 'Uncle T',
      'uncle t': 'Uncle T',
      'vj shao khan': 'Shao Khan',
      'shao khan': 'Shao Khan',
      'vj martin k': 'Martin K',
      'martin k': 'Martin K',
      'vj aaron': 'VJ Aron',
      'vj aron': 'VJ Aron',
      'vj fredy': 'VJ Freddie',
      'vj freddie': 'VJ Freddie',
      'vj ashim': 'VJ Hashim',
      'vj hashim': 'VJ Hashim',
      'vj dan de': 'VJ Dan Dee',
      'vj dan dee': 'VJ Dan Dee',
      'vj ivo': 'VJ IVO Kakorea',
      'vj ivo kakorea': 'VJ IVO Kakorea'
    };
    const lower = clean.toLowerCase();
    return knownMap[lower] || clean;
  }

  /**
   * Fetches the complete catalog for a specific VJ from Twolekede (e.g. 1,156+ movies for VJ Junior)
   */
  public static async fetchTwolekedeByVj(vjName: string): Promise<MediaItem[]> {
    const mappedVj = this.mapVjToTwolekedeName(vjName);
    const cacheKey = `twolekede:vj:${mappedVj.toLowerCase()}`;
    const cached = this.getFromCache<MediaItem[]>(cacheKey);
    if (cached) return cached;

    try {
      const url = `https://twolekede.com/categories.php?name=${encodeURIComponent(mappedVj)}`;
      const resp = await fetch(url, {
        headers: {
          'Referer': 'https://twolekede.com/',
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36'
        },
        signal: AbortSignal.timeout(10000)
      });

      if (!resp.ok) return [];
      const html = await resp.text();
      const items: MediaItem[] = [];

      // Resilient card extraction
      const cardRegex = /<div class="grid-item">[\s\S]*?<a href="(?:moviedetails|preview)\.php\?id=(\d+)"[\s\S]*?<img[^>]+src="([^"]+)"[\s\S]*?<h3>([^<]+)<\/h3>[\s\S]*?<p>([^<]*)<\/p>(?:[\s\S]*?<span class="category">([^<]*)<\/span>)?/g;
      let match: RegExpExecArray | null;

      while ((match = cardRegex.exec(html)) !== null) {
        const id = match[1];
        let poster = match[2];
        const title = match[3].trim();
        const year = match[4]?.trim() || '2024';
        const cardVj = match[5]?.trim() || mappedVj;

        if (!poster.startsWith('http')) {
          poster = `https://twolekede.com/${poster.replace(/^\/+/, '')}`;
        }

        items.push({
          id: `tk_${id}`,
          title: title,
          type: 'movie',
          vj: cardVj.toLowerCase().includes('vj') ? cardVj : `VJ ${cardVj}`,
          genre: 'Action / Drama',
          year: year,
          rating: '8.9',
          duration: '2h 05m',
          description: `${title} translated into Luganda by ${cardVj}. Available in 1080p Full HD on Munowatch.`,
          posterUrl: poster,
          backdropUrl: poster,
          streamUrl: '',
          isSeries: false
        });
      }

      if (items.length > 0) {
        // Cache full VJ catalog for 30 minutes
        this.setInCache(cacheKey, items, 30 * 60 * 1000);
        return items;
      }
    } catch {}

    return [];
  }

  /**
   * Fetches movies by genre from Twolekede (e.g. Action, Horror, Comedy, Indian Movie)
   */
  public static async fetchTwolekedeByGenre(genreName: string, page: number = 1): Promise<MediaItem[]> {
    const cacheKey = `twolekede:genre:${genreName.toLowerCase()}:${page}`;
    const cached = this.getFromCache<MediaItem[]>(cacheKey);
    if (cached) return cached;

    try {
      const encoded = encodeURIComponent(genreName);
      const url = page <= 1
        ? `https://twolekede.com/genres.php?genre=${encoded}`
        : `https://twolekede.com/genres.php?genre=${encoded}&page=${page}`;

      const resp = await fetch(url, {
        headers: {
          'Referer': 'https://twolekede.com/',
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36'
        },
        signal: AbortSignal.timeout(8000)
      });

      if (!resp.ok) return [];
      const html = await resp.text();
      const items: MediaItem[] = [];

      const cardRegex = /<div class="grid-item">[\s\S]*?<a href="(?:moviedetails|preview)\.php\?id=(\d+)"[\s\S]*?<img[^>]+src="([^"]+)"[\s\S]*?<h3>([^<]+)<\/h3>[\s\S]*?<p>([^<]*)<\/p>(?:[\s\S]*?<span class="category">([^<]*)<\/span>)?/g;
      let match: RegExpExecArray | null;

      while ((match = cardRegex.exec(html)) !== null) {
        const id = match[1];
        let poster = match[2];
        const title = match[3].trim();
        const year = match[4]?.trim() || '2024';
        const cardVj = match[5]?.trim() || 'VJ Junior';

        if (!poster.startsWith('http')) {
          poster = `https://twolekede.com/${poster.replace(/^\/+/, '')}`;
        }

        items.push({
          id: `tk_${id}`,
          title: title,
          type: 'movie',
          vj: cardVj.toLowerCase().includes('vj') ? cardVj : `VJ ${cardVj}`,
          genre: genreName,
          year: year,
          rating: '8.8',
          duration: '2h 10m',
          description: `${title} translated into Luganda by ${cardVj} in ${genreName}. Available in 1080p FHD.`,
          posterUrl: poster,
          backdropUrl: poster,
          streamUrl: '',
          isSeries: false
        });
      }

      if (items.length > 0) {
        this.setInCache(cacheKey, items);
        return items;
      }
    } catch {}

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
      const itemRegex = /<div class="grid-item">[\s\S]*?<a href="(?:moviedetails|seriesdetails|preview)\.php\?id=(\d+)"[\s\S]*?<img[^>]+src="([^"]+)"[\s\S]*?<div class="item-info">[\s\S]*?<h3>([^<]+)<\/h3>(?:[\s\S]*?<p>([^<]*)<\/p>)?(?:[\s\S]*?<span class="category">([^<]*)<\/span>)?/g;
      let match: RegExpExecArray | null;

      while ((match = itemRegex.exec(html)) !== null) {
        const id = match[1];
        let poster = match[2];
        const title = match[3].trim();
        const year = match[4]?.trim() || '2024';
        let vj = match[5]?.trim() || 'VJ Junior';
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
          year: year,
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
   * High-level getMovies with dynamic pagination and multi-source filtering
   */
  public static async getMovies(options: QueryOptions, baseUrl: string): Promise<{ items: MediaItem[]; count: number; total?: number; page: number; hasMore?: boolean }> {
    const page = options.page || 1;
    const perPage = options.perPage || 30;

    let combined: MediaItem[] = [];
    let totalEstimated: number = 0;

    if (options.vj) {
      // Priority 1: Direct Twolekede VJ catalog (1,156+ movies for Junior, 444+ for Emmy, etc.)
      const [twolekedeVjMovies, ugaVjMovies] = await Promise.all([
        this.fetchTwolekedeByVj(options.vj),
        this.fetchUgaWatchCatalog(1, options)
      ]);

      // Deduplicate
      const seen = new Set<string>();
      const allVjItems: MediaItem[] = [];

      for (const item of [...twolekedeVjMovies, ...ugaVjMovies]) {
        const norm = item.title.toLowerCase().replace(/[^a-z0-9]/g, '');
        if (!seen.has(norm)) {
          seen.add(norm);
          allVjItems.push(item);
        }
      }

      totalEstimated = allVjItems.length;
      const start = (page - 1) * perPage;
      combined = allVjItems.slice(start, start + perPage);

    } else if (options.genre) {
      // Priority 2: Direct Twolekede genre catalog
      const [twolekedeGenreMovies, ugaGenreMovies] = await Promise.all([
        this.fetchTwolekedeByGenre(options.genre, page),
        this.fetchUgaWatchCatalog(1, options)
      ]);

      const seen = new Set<string>();
      for (const item of [...twolekedeGenreMovies, ...ugaGenreMovies]) {
        const norm = item.title.toLowerCase().replace(/[^a-z0-9]/g, '');
        if (!seen.has(norm)) {
          seen.add(norm);
          combined.push(item);
        }
      }
      totalEstimated = combined.length * 10; // multiple pages available

    } else {
      // Priority 3: General movies catalog across Twolekede (178 pages) and UgaWatch (17,000+ titles)
      const [liveTwolekede, liveUga] = await Promise.all([
        this.fetchTwolekedeCatalog(false, page),
        this.fetchUgaWatchCatalog(1, options)
      ]);

      const seen = new Set<string>();
      for (const item of [...liveTwolekede, ...liveUga]) {
        const norm = item.title.toLowerCase().replace(/[^a-z0-9]/g, '');
        if (!seen.has(norm)) {
          seen.add(norm);
          combined.push(item);
        }
      }
      totalEstimated = 17000;
    }

    // Fallback to rich catalog if both live upstream servers are unreachable
    if (combined.length === 0 && page === 1) {
      let fb = [...RICH_CATALOG.filter(i => !i.isSeries)];
      if (options.year && options.year !== 'all') {
        fb = fb.filter(i => i.year === options.year);
      }
      if (options.vj) {
        const cleanVj = options.vj.toLowerCase().replace('vj-', '').replace('vj', '').trim();
        fb = fb.filter(i => i.vj.toLowerCase().includes(cleanVj));
      }
      if (options.genre) {
        const cleanG = options.genre.toLowerCase().trim();
        fb = fb.filter(i => i.genre.toLowerCase().includes(cleanG));
      }
      combined = fb;
      totalEstimated = fb.length;
    }

    // Apply sort if requested
    if (options.sort === 'OLD_MOVIES') {
      combined.sort((a, b) => parseInt(a.year || '2024') - parseInt(b.year || '2024'));
    } else if (options.sort === 'RANDOM') {
      combined.sort((a, b) => a.title.localeCompare(b.title));
    }

    const enrichedItems = combined.map(item => this.enrichWithServers(item, baseUrl));

    return {
      items: enrichedItems,
      count: enrichedItems.length,
      total: totalEstimated || enrichedItems.length,
      page: page,
      hasMore: totalEstimated > (page * perPage) || enrichedItems.length >= perPage
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

    // Concurrently fetch latest uploads & VJ specials across Twolekede and UgaWatch
    const [twolekedeLatest, twolekedeJunior, twolekedeEmmy, seriesList, ugaLatest] = await Promise.all([
      this.fetchTwolekedeCatalog(false, 1),
      this.fetchTwolekedeByVj('VJ Junior'),
      this.fetchTwolekedeByVj('VJ Emmy'),
      this.fetchTwolekedeCatalog(true, 1),
      this.fetchUgaWatchCatalog(1, { page: 1, perPage: 12 })
    ]);

    const attachProxy = (items: MediaItem[]) => items.map(i => this.enrichWithServers(i, baseUrl));

    // Combine trending
    const trendingCombined: MediaItem[] = [];
    const seen = new Set<string>();
    for (const item of [...twolekedeLatest, ...ugaLatest]) {
      const norm = item.title.toLowerCase().replace(/[^a-z0-9]/g, '');
      if (!seen.has(norm)) {
        seen.add(norm);
        trendingCombined.push(item);
      }
    }

    const rails: CatalogRail[] = [
      {
        id: "rail_trending",
        title: "🔥 Newly Uploaded Translated Movies",
        items: attachProxy(trendingCombined.length > 0 ? trendingCombined.slice(0, 16) : RICH_CATALOG.filter(i => !i.isSeries).slice(0, 8))
      },
      {
        id: "rail_junior",
        title: "🎬 VJ Junior Exclusive Translations",
        vj: "VJ Junior",
        items: attachProxy(twolekedeJunior.length > 0 ? twolekedeJunior.slice(0, 16) : RICH_CATALOG.filter(i => i.vj.includes("Junior")))
      },
      {
        id: "rail_emmy",
        title: "💥 VJ Emmy Cinematic Hits",
        vj: "VJ Emmy",
        items: attachProxy(twolekedeEmmy.length > 0 ? twolekedeEmmy.slice(0, 16) : RICH_CATALOG.filter(i => i.vj.includes("Emmy")))
      },
      {
        id: "rail_series",
        title: "📺 Ugandan Translated TV Series (Multi-Season)",
        items: attachProxy(seriesList.length > 0 ? seriesList.slice(0, 16) : RICH_CATALOG.filter(i => i.isSeries))
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
        items: attachProxy(twolekedeLatest.length > 10 ? twolekedeLatest.slice(10, 22) : RICH_CATALOG.filter(i => i.genre.includes("Action")))
      }
    ];

    this.setInCache(cacheKey, rails, 15 * 60 * 1000); // 15 min cache
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

  /**
   * Fetches rich genuine details for an individual movie or series
   */
  public static async getMovieDetails(id: string, title?: string, baseUrl: string = ''): Promise<MediaItem | null> {
    const cacheKey = `details:${id}:${title || ''}`;
    const cached = this.getFromCache<MediaItem>(cacheKey);
    if (cached) return cached;

    const cleanId = id.replace(/^(myvj_|tk_)/, '');

    // 1. If it's a Twolekede item (or numeric ID)
    if (id.startsWith('tk_') || (!id.startsWith('myvj_') && /^\d+$/.test(cleanId))) {
      try {
        const resp = await fetch(`https://twolekede.com/moviedetails.php?id=${cleanId}`, {
          headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
            'Referer': 'https://twolekede.com/'
          },
          signal: AbortSignal.timeout(6000)
        });

        if (resp.ok) {
          const html = await resp.text();
          const unescape = (s: string) => s.replace(/&amp;/g, '&').replace(/&#38;/g, '&').replace(/&quot;/g, '"');
          const titleMatch = (html.match(/<div class="details-section">\s*<h1>([^<]*)<\/h1>/) || html.match(/<h1[^>]*>([^<]*)<\/h1>/));
          const descMatch = html.match(/<div class="description">\s*([\s\S]*?)\s*<\/div>/) ||
                            html.match(/<div class="overview">\s*([\s\S]*?)\s*<\/div>/) ||
                            html.match(/<p class="description[^"]*">\s*([\s\S]*?)\s*<\/p>/);

          if (titleMatch) {
            const movieTitle = unescape(titleMatch[1]).trim();
            const desc = descMatch ? unescape(descMatch[1]).trim() : '';

            const yearMatch = html.match(/<span class="year">([^<]*)<\/span>/);
            const year = yearMatch ? yearMatch[1].trim() : '2024';

            const vjMatch = html.match(/<span class="category-tag">([^<]*)<\/span>/);
            let vj = vjMatch ? vjMatch[1].trim() : 'VJ Junior';
            if (!vj.toLowerCase().includes('vj')) vj = `VJ ${vj}`;

            const posterMatch = html.match(/<img[^>]+src="([^"]*)"[^>]+class="movie-poster"/) ||
                                html.match(/<img[^>]+class="movie-poster"[^>]+src="([^"]*)"/);
            let posterUrl = posterMatch ? posterMatch[1].trim() : '';
            if (posterUrl && !posterUrl.startsWith('http')) {
              posterUrl = `https://twolekede.com/${posterUrl.replace(/^\/+/, '')}`;
            }

            const streamUrl = `https://twolekede.com/preview.php?id=${cleanId}`;
            const proxyUrl = `${baseUrl}/api/v1/stream/proxy?url=${encodeURIComponent(streamUrl)}`;

            const item: MediaItem = {
              id: `tk_${cleanId}`,
              title: movieTitle,
              type: 'movie',
              vj: vj,
              genre: 'Action / Adventure',
              year: year,
              rating: '8.8',
              duration: '2h 05m',
              description: desc || `Luganda translation by ${vj} available on Munowatch TV.`,
              posterUrl: posterUrl,
              backdropUrl: posterUrl,
              streamUrl: streamUrl,
              isSeries: false,
              servers: [
                { id: 's2', name: 'Server 2 (Munowatch Web Streamer - CORS Safe)', quality: '1080p Full HD', streamUrl: proxyUrl, isProxy: true },
                { id: 's1', name: 'Server 1 (High-Speed Direct)', quality: '1080p FHD', streamUrl: streamUrl, isProxy: false }
              ]
            };

            this.setInCache(cacheKey, item, 60 * 60 * 1000);
            return item;
          }
        }
      } catch {}
    }

    // 2. If it's an UgaWatch / MyVJ item (movie or series)
    if (id.startsWith('myvj_') || /^\d+$/.test(cleanId)) {
      try {
        const bodyParams = new URLSearchParams({
          source: 'next_js',
          id: cleanId,
          movie_id: cleanId,
          serie_id: cleanId
        });

        // Try movie details first, then series details
        const endpoints = [
          'https://ugawatch.com/api-v3/mobile/get-movie-details-min.php',
          'https://ugawatch.com/api-v3/mobile/get-serie-details-min.php'
        ];

        for (const epUrl of endpoints) {
          const resp = await fetch(epUrl, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/x-www-form-urlencoded',
              'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
              'Referer': 'https://ugawatch.com/'
            },
            body: bodyParams.toString(),
            signal: AbortSignal.timeout(6000)
          });

          if (resp.ok) {
            const data = await resp.json();
            if (data && (data.status === 0 || data.status === '0') && (data.movie || data.serie || data.data)) {
              const m = data.movie || data.serie || data.data;
              const isSeriesItem = Array.isArray(data.episodes) && data.episodes.length > 0;
              const file = m.bunny_file_name || (isSeriesItem ? data.episodes[0]?.bunny_file_name : null) || m.trailer || m.video || '';
              const cleanFile = typeof file === 'string' ? file.replace(/\\/g, '').trim() : '';
              const directStream = cleanFile.startsWith('http') ? cleanFile : (cleanFile ? `https://trailer256.b-cdn.net/${cleanFile}` : '');
              const proxyUrl = directStream ? `${baseUrl}/api/v1/stream/proxy?url=${encodeURIComponent(directStream)}` : '';

              const munoImg = m.img_port_muno_file_name || '';
              const tmdbPoster = m.tmdb_poster_path || '';
              let posterUrl = '';
              if (tmdbPoster && tmdbPoster !== 'null') {
                posterUrl = `https://image.tmdb.org/t/p/w500${tmdbPoster.startsWith('/') ? tmdbPoster : `/${tmdbPoster}`}`;
              } else if (munoImg && munoImg !== 'null') {
                posterUrl = munoImg.startsWith('http') ? munoImg : `https://res-watch.b-cdn.net/1/${munoImg}`;
              }

              const tmdbBackdrop = m.tmdb_backdrop_path || '';
              let backdropUrl = posterUrl;
              if (tmdbBackdrop && tmdbBackdrop !== 'null') {
                backdropUrl = `https://image.tmdb.org/t/p/original${tmdbBackdrop.startsWith('/') ? tmdbBackdrop : `/${tmdbBackdrop}`}`;
              }

              let vj = m.vj && m.vj !== '-' && m.vj !== 'null' ? m.vj : 'VJ Junior';
              if (!vj.toLowerCase().includes('vj')) vj = `VJ ${vj}`;

              // Map episodes for series
              let seasons: any[] | undefined = undefined;
              if (isSeriesItem) {
                const seasonsMap = new Map<number, any[]>();
                for (const ep of data.episodes) {
                  const sNum = ep.season || 1;
                  const epFile = ep.bunny_file_name || ep.video || ep.trailer || '';
                  const epStream = epFile.startsWith('http') ? epFile : (epFile ? `https://trailer256.b-cdn.net/${epFile}` : '');

                  const episodeObj = {
                    id: `ep_${ep.id || ep.episode_number}`,
                    episodeNumber: ep.episode_number || 1,
                    title: ep.title || `Episode ${ep.episode_number}`,
                    duration: ep.duration ? `${ep.duration}m` : '45m',
                    streamUrl: epStream,
                    thumbnailUrl: backdropUrl
                  };

                  if (!seasonsMap.has(sNum)) seasonsMap.set(sNum, []);
                  seasonsMap.get(sNum)!.push(episodeObj);
                }

                seasons = Array.from(seasonsMap.entries()).map(([seasonNumber, episodes]) => ({
                  seasonNumber,
                  episodes
                }));
              }

              const item: MediaItem = {
                id: `myvj_${cleanId}`,
                title: m.title || title || 'Movie',
                type: isSeriesItem ? 'series' : 'movie',
                vj: vj,
                genre: m.categories || 'Action / Drama',
                year: m.release_year || '2024',
                rating: '8.8',
                duration: isSeriesItem ? `${data.episodes.length} Episodes` : '2h 05m',
                description: m.about || m.overview || `Luganda translated blockbuster brought to you by ${vj} on Munowatch TV.`,
                posterUrl: posterUrl,
                backdropUrl: backdropUrl,
                streamUrl: directStream,
                isSeries: isSeriesItem,
                seasons: seasons,
                servers: directStream ? [
                  { id: 's2', name: 'Server 2 (Munowatch Web Streamer - CORS Safe)', quality: '1080p Full HD', streamUrl: proxyUrl, isProxy: true },
                  { id: 's1', name: 'Server 1 (BunnyCDN High-Speed)', quality: '1080p FHD', streamUrl: directStream, isProxy: false }
                ] : []
              };

              this.setInCache(cacheKey, item, 60 * 60 * 1000);
              return item;
            }
          }
        }
      } catch {}
    }

    // 3. Fallback: Search Twolekede by title if candidate title is provided
    if (title && title.trim().length >= 2) {
      const candidateQueries = StreamProxyService.generateCandidateQueries(title);
      for (const q of candidateQueries) {
        try {
          const resp = await fetch(`https://twolekede.com/search.php?q=${encodeURIComponent(q)}`, {
            headers: {
              'Referer': 'https://twolekede.com/',
              'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
            },
            signal: AbortSignal.timeout(5000)
          });

          if (resp.ok) {
            const html = await resp.text();
            const cardRegex = /<a href="(?:moviedetails|preview)\.php\?id=(\d+)"[^>]*>[\s\S]*?<img[^>]+alt="([^"]*)"[\s\S]*?<h3 class="content-title">([^<]*)<\/h3>/g;
            let m: RegExpExecArray | null;

            while ((m = cardRegex.exec(html)) !== null) {
              const matchedId = m[1];
              const cardTitle = (m[3] || m[2] || '').trim();
              if (StreamProxyService.titlesMatch(title, cardTitle)) {
                return await this.getMovieDetails(`tk_${matchedId}`, title, baseUrl);
              }
            }
          }
        } catch {}
      }
    }

    // 4. Fallback to rich catalog entry if matched
    const local = RICH_CATALOG.find(i => i.id === id || (title && i.title.toLowerCase().includes(title.toLowerCase())));
    if (local) return this.enrichWithServers(local, baseUrl);

    return null;
  }
}
