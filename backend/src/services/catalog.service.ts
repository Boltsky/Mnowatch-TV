import { MediaItem, CatalogRail, PlaybackServer, Episode, Season } from '../types';
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

          // Reject trailer-only promotional placeholders (unpaid entries with only 3MB clips on trailer256)
          if (obj.bunny_file_name && obj.trailer && obj.bunny_file_name === obj.trailer && obj.is_free !== 1 && !obj.video) {
            continue;
          }

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
  public static async fetchTwolekedeByVj(vjName: string, page: number = 1): Promise<MediaItem[]> {
    const mappedVj = this.mapVjToTwolekedeName(vjName);
    const cacheKey = `twolekede:vj:${mappedVj.toLowerCase()}:${page}`;
    const cached = this.getFromCache<MediaItem[]>(cacheKey);
    if (cached) return cached;

    try {
      const url = page <= 1
        ? `https://twolekede.com/categories.php?name=${encodeURIComponent(mappedVj)}`
        : `https://twolekede.com/categories.php?name=${encodeURIComponent(mappedVj)}&page=${page}`;

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

      // Resilient card extraction supporting multiple card layouts
      const cardRegex = /<a\s+[^>]*href=["'](?:moviedetails|preview)\.php\?id=(\d+)["'][^>]*>[\s\S]*?<img[^>]+src=["']([^"']+)["'][\s\S]*?<h3>([^<]+)<\/h3>(?:[\s\S]*?<p>([^<]*)<\/p>)?(?:[\s\S]*?<span class="category">([^<]*)<\/span>)?/g;
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
   * Fetches authentic series translated by a specific VJ from Twolekede categories.php
   */
  public static async fetchTwolekedeSeriesByVj(vjName: string, page: number = 1): Promise<MediaItem[]> {
    const mappedVj = this.mapVjToTwolekedeName(vjName);
    const cacheKey = `twolekede:series:vj:${mappedVj.toLowerCase()}:${page}`;
    const cached = this.getFromCache<MediaItem[]>(cacheKey);
    if (cached) return cached;

    try {
      const url = page <= 1
        ? `https://twolekede.com/categories.php?name=${encodeURIComponent(mappedVj)}`
        : `https://twolekede.com/categories.php?name=${encodeURIComponent(mappedVj)}&page=${page}`;

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

      // Extract all series links: seriesdetails.php?title=...&category=...
      const seriesRegex = /<a\s+[^>]*href=["'](seriesdetails\.php\?[^"']+)["'][^>]*>[\s\S]*?<img[^>]+src=["']([^"']+)["'][\s\S]*?<h3>([^<]+)<\/h3>(?:[\s\S]*?<p>([^<]*)<\/p>)?/gi;
      let match: RegExpExecArray | null;

      const seen = new Set<string>();
      while ((match = seriesRegex.exec(html)) !== null) {
        const href = match[1];
        let poster = match[2];
        const title = StreamProxyService.unescapeHtml(match[3]).trim();
        const year = match[4]?.trim() || '2024';

        const normTitle = title.toLowerCase().replace(/[^a-z0-9]/g, '');
        if (seen.has(normTitle)) continue;
        seen.add(normTitle);

        if (!poster.startsWith('http')) {
          poster = `https://twolekede.com/${poster.replace(/^\/+/, '')}`;
        }

        const titleSlugMatch = href.match(/title=([^&"']+)/i);
        const catSlugMatch = href.match(/category=([^&"']+)/i);
        const titleSlug = titleSlugMatch ? titleSlugMatch[1] : encodeURIComponent(title);
        const catSlug = catSlugMatch ? catSlugMatch[1] : encodeURIComponent(mappedVj);

        items.push({
          id: `tk_series_${titleSlug}_${catSlug}`,
          title: title,
          type: 'series',
          vj: mappedVj.toLowerCase().includes('vj') ? mappedVj : `VJ ${mappedVj}`,
          genre: 'Ugandan Translated Series',
          year: year,
          rating: '8.9',
          duration: 'Multi-Season',
          description: `${title} complete series translated into Luganda by ${mappedVj}. Stream all seasons on Munowatch TV.`,
          posterUrl: poster,
          backdropUrl: poster,
          streamUrl: `https://twolekede.com/${href}`,
          isSeries: true
        });
      }

      if (items.length > 0) {
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

      // Extract grid-item / content-item links (matches both movies with numeric IDs and series with title/category slugs)
      const itemRegex = /<div class="grid-item">\s*<a href="(?:moviedetails|seriesdetails|preview)\.php\?(?:id=(\d+)|title=([^&"]+)&(?:amp;)?category=([^&"]+))"[^>]*>[\s\S]*?<img[^>]+src="([^"]+)"[^>]*>[\s\S]*?<h3>([^<]+)<\/h3>(?:\s*<span class="category">([^<]*)<\/span>)?/g;
      let match: RegExpExecArray | null;

      while ((match = itemRegex.exec(html)) !== null) {
        const id = match[1];
        const seriesTitleSlug = match[2];
        const seriesCatSlug = match[3];
        let poster = match[4];
        const rawTitle = match[5].trim();
        const cardVj = match[6]?.trim() || (seriesCatSlug ? decodeURIComponent(seriesCatSlug).replace(/\+/g, ' ') : 'VJ Junior');

        const title = StreamProxyService.unescapeHtml(rawTitle);
        const resolvedId = id
          ? `tk_${id}`
          : `tk_series_${seriesTitleSlug || encodeURIComponent(title)}_${seriesCatSlug || encodeURIComponent(cardVj)}`;

        let vj = cardVj;
        if (!vj.toLowerCase().includes('vj')) vj = `VJ ${vj}`;

        if (!poster.startsWith('http')) {
          poster = `https://twolekede.com/${poster.replace(/^\/+/, '')}`;
        }

        items.push({
          id: resolvedId,
          title: title,
          type: isSeries ? 'series' : 'movie',
          vj: vj,
          genre: isSeries ? 'Ugandan Translated Series' : 'Action / Adventure',
          year: '2024',
          rating: '8.8',
          duration: isSeries ? 'TV Series' : '2h 10m',
          description: `${title} translated into Luganda by ${vj}. Full HD 1080p release available on Munowatch TV.`,
          posterUrl: poster,
          backdropUrl: poster,
          streamUrl: id ? `https://twolekede.com/preview.php?id=${id}` : '',
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
      // Priority 1: Direct Twolekede VJ catalog (1,156+ verified full movies for Junior, 444+ for Emmy, etc.)
      const twolekedeVjMovies = await this.fetchTwolekedeByVj(options.vj, page);
      let allVjItems = twolekedeVjMovies;
      if (allVjItems.length === 0 && page === 1) {
        allVjItems = await this.fetchUgaWatchCatalog(1, options);
      }

      totalEstimated = 1200;
      combined = allVjItems;

    } else if (options.genre) {
      // Priority 2: Direct Twolekede genre catalog
      const twolekedeGenreMovies = await this.fetchTwolekedeByGenre(options.genre, page);
      combined = twolekedeGenreMovies.length > 0
        ? twolekedeGenreMovies
        : await this.fetchUgaWatchCatalog(1, options);
      totalEstimated = 4000;

    } else {
      // Priority 3: General movies catalog across Twolekede (178 pages of authentic full-length movies)
      const liveTwolekede = await this.fetchTwolekedeCatalog(false, page);
      combined = liveTwolekede.length > 0
        ? liveTwolekede
        : await this.fetchUgaWatchCatalog(1, options);
      totalEstimated = 4000;
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
   * High-level getSeries with dynamic pagination and strict VJ isolation
   */
  public static async getSeries(options: QueryOptions, baseUrl: string): Promise<{ items: MediaItem[]; count: number; page: number }> {
    const page = options.page || 1;
    let combined: MediaItem[] = [];

    if (options.vj) {
      const mappedVj = this.mapVjToTwolekedeName(options.vj);
      const cleanTargetVj = options.vj.toLowerCase().replace('vj-', '').replace('vj', '').replace('.', '').trim();

      // 1. Fetch series from Twolekede categories.php for this specific VJ (e.g. 120+ series for VJ Junior, 121+ for VJ ICEP)
      const vjSeries = await this.fetchTwolekedeSeriesByVj(mappedVj, page);

      if (vjSeries.length > 0) {
        combined = vjSeries;
      } else {
        // Fallback to checking series.php if specific category was empty
        const liveCatalogSeries = await this.fetchTwolekedeCatalog(true, page);
        const matchedFromCatalog = liveCatalogSeries.filter(i => {
          const cVj = (i.vj || '').toLowerCase().replace('vj-', '').replace('vj', '').replace('.', '').trim();
          return cVj.includes(cleanTargetVj) || cleanTargetVj.includes(cVj);
        });
        combined = matchedFromCatalog;
      }

      // Fallback to rich catalog filtered strictly by VJ if upstream returned empty
      if (combined.length === 0) {
        combined = RICH_CATALOG.filter(i => {
          if (!i.isSeries) return false;
          const cVj = (i.vj || '').toLowerCase().replace('vj-', '').replace('vj', '').replace('.', '').trim();
          return cVj.includes(cleanTargetVj) || cleanTargetVj.includes(cVj);
        });
      }

      // Strictly ensure NO other VJs can leak into this VJ's series listing
      combined = combined.filter(i => {
        const cVj = (i.vj || '').toLowerCase().replace('vj-', '').replace('vj', '').replace('.', '').trim();
        return cVj.includes(cleanTargetVj) || cleanTargetVj.includes(cVj);
      });
    } else {
      let liveTwolekede = await this.fetchTwolekedeCatalog(true, page);
      combined = liveTwolekede.length > 0 ? liveTwolekede : await this.fetchUgaWatchCatalog(2, options);

      if (combined.length === 0) {
        combined = [...RICH_CATALOG.filter(i => i.isSeries)];
      }
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

    // Concurrently fetch verified full-length releases across Twolekede
    const [twolekedeLatest, twolekedeJunior, twolekedeEmmy, seriesList] = await Promise.all([
      this.fetchTwolekedeCatalog(false, 1),
      this.fetchTwolekedeByVj('VJ Junior'),
      this.fetchTwolekedeByVj('VJ Emmy'),
      this.fetchTwolekedeCatalog(true, 1)
    ]);

    const attachProxy = (items: MediaItem[]) => items.map(i => this.enrichWithServers(i, baseUrl));

    const rails: CatalogRail[] = [
      {
        id: "rail_trending",
        title: "🔥 Newly Uploaded Translated Movies",
        items: attachProxy(twolekedeLatest.length > 0 ? twolekedeLatest.slice(0, 16) : RICH_CATALOG.filter(i => !i.isSeries).slice(0, 8))
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
   * Resilient multi-source search across Munowatch VIP catalog, Twolekede movies & series,
   * UgaWatch / MyVJ, and VJ categories
   */
  public static async search(query: string, baseUrl: string): Promise<MediaItem[]> {
    if (!query || query.trim().length === 0) return [];
    const q = query.trim();
    const cacheKey = `search:${q.toLowerCase()}`;
    const cached = this.getFromCache<MediaItem[]>(cacheKey);
    if (cached) return cached;

    const results: MediaItem[] = [];
    const qTokens = q.toLowerCase().split(/\s+/).filter(t => t.length >= 2);
    const munoSession = 'PHPSESSID=926cdd498cd8113008318bd9c6696ff5';

    // 1-4. Run Munowatch, Twolekede Movies, Twolekede Series, and UgaWatch searches in parallel for instant speed
    const searchTasks = [
      // Task 1: Munowatch catalog directly
      (async (): Promise<MediaItem[]> => {
        try {
          const resp = await fetch(`https://munowatch.com/search?q=${encodeURIComponent(q)}`, {
            headers: {
              'Cookie': munoSession,
              'Referer': 'https://munowatch.com/',
              'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
            },
            signal: AbortSignal.timeout(6000)
          });
          if (!resp.ok) return [];
          const html = await resp.text();
          const items: MediaItem[] = [];
          const anchorRegex = /<a\s+[^>]*href="(\/twolekede\?v=[^"]+)"[^>]*>([\s\S]*?)<\/a>/gi;
          let am: RegExpExecArray | null;
          while ((am = anchorRegex.exec(html)) !== null) {
            const href = am[1].replace(/&amp;/g, '&');
            const inner = am[2];
            const idMatch = inner.match(/id=["'](\d+)["']/);
            const imgMatch = inner.match(/<img[^>]+src=["']([^"']+)["'][^>]*alt=["']([^"']*)["']|<img[^>]+alt=["']([^"']*)["'][^>]*src=["']([^"']+)["']/i);
            const vjMatch = inner.match(/<p[^>]*>([^<]+)<\/p>/i);

            const mid = idMatch ? idMatch[1] : `${items.length + 1000}`;
            const poster = imgMatch ? (imgMatch[1] || imgMatch[4] || '') : '';
            const rawTitle = imgMatch ? (imgMatch[2] || imgMatch[3] || '') : '';
            const title = StreamProxyService.unescapeHtml(rawTitle).trim();
            let vj = vjMatch ? vjMatch[1].trim() : 'VJ Junior';
            if (!vj.toLowerCase().includes('vj')) vj = `VJ ${vj}`;

            if (title) {
              items.push({
                id: `muno_${mid}`,
                title: title,
                type: href.toLowerCase().includes('series') ? 'series' : 'movie',
                vj: vj,
                genre: 'Action / Adventure',
                year: '2024',
                rating: '8.8',
                duration: '2h 05m',
                description: `${title} translated into Luganda by ${vj}. Available in 1080p Full HD on Munowatch TV.`,
                posterUrl: poster,
                backdropUrl: poster,
                streamUrl: `https://munowatch.com${href}`,
                isSeries: href.toLowerCase().includes('series')
              });
            }
          }
          return items;
        } catch {
          return [];
        }
      })(),

      // Task 2: Twolekede Movies
      (async (): Promise<MediaItem[]> => {
        try {
          const resp = await fetch(`https://twolekede.com/search.php?q=${encodeURIComponent(q)}`, {
            headers: {
              'Referer': 'https://twolekede.com/',
              'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
            },
            signal: AbortSignal.timeout(6000)
          });
          if (!resp.ok) return [];
          const html = await resp.text();
          const items: MediaItem[] = [];
          const tkRegex = /<a href="(?:moviedetails|preview)\.php\?id=(\d+)"[^>]*>[\s\S]*?(?:<span class="category-badge">([^<]*)<\/span>)?[\s\S]*?<img[^>]+src="([^"]+)"[\s\S]*?<h3 class="content-title">([^<]*)<\/h3>/g;
          let m: RegExpExecArray | null;
          while ((m = tkRegex.exec(html)) !== null) {
            const id = m[1];
            let vj = m[2]?.trim() || 'VJ Junior';
            let poster = m[3];
            const rawTitle = m[4].trim();
            const title = StreamProxyService.unescapeHtml(rawTitle);
            if (!vj.toLowerCase().includes('vj')) vj = `VJ ${vj}`;
            if (!poster.startsWith('http')) poster = `https://twolekede.com/${poster.replace(/^\/+/, '')}`;

            items.push({
              id: `tk_${id}`,
              title: title,
              type: 'movie',
              vj: vj,
              genre: 'Action / Adventure',
              year: '2024',
              rating: '8.8',
              duration: '2h 05m',
              description: `${title} translated into Luganda by ${vj}. Available in 1080p FHD on Munowatch TV.`,
              posterUrl: poster,
              backdropUrl: poster,
              streamUrl: `https://twolekede.com/preview.php?id=${id}`,
              isSeries: false
            });
          }
          return items;
        } catch {
          return [];
        }
      })(),

      // Task 3: Twolekede Series
      (async (): Promise<MediaItem[]> => {
        try {
          const resp = await fetch(`https://twolekede.com/search.php?type=series&q=${encodeURIComponent(q)}`, {
            headers: {
              'Referer': 'https://twolekede.com/',
              'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
            },
            signal: AbortSignal.timeout(6000)
          });
          if (!resp.ok) return [];
          const html = await resp.text();
          const items: MediaItem[] = [];
          const sRegex = /<a href="(?:seriesdetails|preview)\.php\?id=(\d+)"[^>]*>[\s\S]*?(?:<span class="category-badge">([^<]*)<\/span>)?[\s\S]*?<img[^>]+src="([^"]+)"[\s\S]*?<h3 class="content-title">([^<]*)<\/h3>/g;
          let sm: RegExpExecArray | null;
          while ((sm = sRegex.exec(html)) !== null) {
            const id = sm[1];
            let vj = sm[2]?.trim() || 'VJ Junior';
            let poster = sm[3];
            const rawTitle = sm[4].trim();
            const title = StreamProxyService.unescapeHtml(rawTitle);
            if (!vj.toLowerCase().includes('vj')) vj = `VJ ${vj}`;
            if (!poster.startsWith('http')) poster = `https://twolekede.com/${poster.replace(/^\/+/, '')}`;

            items.push({
              id: `tk_${id}`,
              title: title,
              type: 'series',
              vj: vj,
              genre: 'Series',
              year: '2024',
              rating: '8.9',
              duration: 'Complete Series',
              description: `${title} complete series translated into Luganda by ${vj}. Stream all seasons on Munowatch TV.`,
              posterUrl: poster,
              backdropUrl: poster,
              streamUrl: `https://twolekede.com/preview.php?id=${id}`,
              isSeries: true
            });
          }
          return items;
        } catch {
          return [];
        }
      })(),

      // Task 4: UgaWatch API v3
      (async (): Promise<MediaItem[]> => {
        try {
          const resp = await fetch('https://ugawatch.com/api-v3/mobile/search.php', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/x-www-form-urlencoded',
              'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
              'Referer': 'https://ugawatch.com/'
            },
            body: new URLSearchParams({ source: 'next_js', search_term: q }).toString(),
            signal: AbortSignal.timeout(6000)
          });
          if (!resp.ok) return [];
          const text = await resp.text();
          if (!text || text.startsWith('<')) return [];
          const json = JSON.parse(text);
          const items: MediaItem[] = [];
          const rawItems = [
            ...(json.matches_title_movies || []),
            ...(json.matches_title_series || []),
            ...(json.matches_vj_movies || []),
            ...(json.starring_in_movies || []),
            ...(json.medias || []),
            ...(json.items || []),
            ...(json.list || [])
          ];
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

            items.push({
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
          return items;
        } catch {
          return [];
        }
      })()
    ];

    const settled = await Promise.allSettled(searchTasks);
    for (const res of settled) {
      if (res.status === 'fulfilled' && Array.isArray(res.value)) {
        results.push(...res.value);
      }
    }

    // 5. VJ Category search expansion
    const lowerQ = q.toLowerCase();
    const matchedVj = this.getVjs().find(v => lowerQ.includes(v.name.toLowerCase()) || v.name.toLowerCase().includes(lowerQ));
    if (matchedVj) {
      try {
        const vjItems = await this.fetchTwolekedeByVj(matchedVj.name);
        results.push(...vjItems.slice(0, 30));
      } catch {}
    }

    // 6. Genre search expansion
    const matchedGenre = this.getGenres().find(g => lowerQ.includes(g.toLowerCase()));
    if (matchedGenre) {
      try {
        const genreItems = await this.fetchTwolekedeByGenre(matchedGenre, 1);
        results.push(...genreItems.slice(0, 30));
      } catch {}
    }

    // 7. Match against cached catalog & RICH_CATALOG
    const local = RICH_CATALOG.filter(i =>
      i.title.toLowerCase().includes(lowerQ) ||
      i.vj.toLowerCase().includes(lowerQ) ||
      i.genre.toLowerCase().includes(lowerQ) ||
      (qTokens.length > 0 && qTokens.some(tok => i.title.toLowerCase().includes(tok)))
    );
    results.push(...local);

    // Deduplicate preserving unique movie + VJ combinations
    const seen = new Set<string>();
    const deduplicated: MediaItem[] = [];
    for (const item of results) {
      const normTitle = item.title.toLowerCase().replace(/[^a-z0-9]/g, '');
      const normVj = (item.vj || '').toLowerCase().replace(/[^a-z0-9]/g, '');
      const uniqueKey = `${normTitle}_${normVj}`;
      if (!seen.has(uniqueKey)) {
        seen.add(uniqueKey);
        deduplicated.push(this.enrichWithServers(item, baseUrl));
      }
    }

    this.setInCache(cacheKey, deduplicated, 10 * 60 * 1000);
    return deduplicated;
  }

  public static enrichWithServers(item: MediaItem, baseUrl: string): MediaItem {
    const rawStream = item.streamUrl || '';
    const isTrailer = StreamProxyService.isTrailerOrPromoUrl(rawStream, item.title);
    const validStream = isTrailer ? '' : rawStream;
    const proxyStreamUrl = validStream ? `${baseUrl}/api/v1/stream/proxy?url=${encodeURIComponent(validStream)}` : '';

    const servers: PlaybackServer[] = validStream ? [
      {
        id: "server-cdn",
        name: "Server 1 (High-Speed Direct)",
        quality: "1080p FHD",
        streamUrl: validStream,
        isProxy: false
      },
      {
        id: "server-proxy",
        name: "Server 2 (Munowatch Web Streamer - CORS Safe)",
        quality: "1080p Full HD",
        streamUrl: proxyStreamUrl,
        isProxy: true
      }
    ] : [];

    return {
      ...item,
      streamUrl: validStream ? (proxyStreamUrl || validStream) : '',
      isStreamAvailable: !isTrailer && validStream.length > 0,
      servers: servers
    };
  }

  /**
   * Fetches all seasons and episodes for a Twolekede series
   */
  public static async fetchTwolekedeSeriesSeasonsAndEpisodes(
    seriesTitle: string,
    seriesVj: string,
    baseUrl: string
  ): Promise<{ seasons: Season[]; posterUrl: string; backdropUrl: string; description: string; year: string } | null> {
    const unescape = (s: string) => s.replace(/&amp;/g, '&').replace(/&#38;/g, '&').replace(/&#039;/g, "'").replace(/&quot;/g, '"');
    const sdUrl = `https://twolekede.com/seriesdetails.php?title=${encodeURIComponent(seriesTitle)}&category=${encodeURIComponent(seriesVj)}`;

    try {
      const resp = await fetch(sdUrl, {
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
          'Referer': 'https://twolekede.com/'
        },
        signal: AbortSignal.timeout(8000)
      });

      if (!resp.ok) return null;
      const html = await resp.text();

      // Poster
      const posterMatch = html.match(/<img[^>]+class="series-poster"[^>]+src="([^"]*)"/) ||
                          html.match(/<img[^>]+src="([^"]*)"[^>]+class="series-poster"/) ||
                          html.match(/<img[^>]+src="([^"]*)"[^>]+class="series-thumbnail"/) ||
                          html.match(/<img[^>]+src="([^"]*)"[^>]+class="movie-poster"/);
      let posterUrl = posterMatch ? posterMatch[1].trim() : '';
      if (posterUrl && !posterUrl.startsWith('http')) {
        posterUrl = `https://twolekede.com/${posterUrl.replace(/^\/+/, '')}`;
      }

      // Description
      const descMatch = html.match(/<div class="description">\s*([\s\S]*?)\s*<\/div>/);
      const description = descMatch ? unescape(descMatch[1]).trim() : '';

      // Year
      const yearMatch = html.match(/<span class="year">([^<]*)<\/span>/);
      const year = yearMatch ? yearMatch[1].trim() : '2024';

      // Discover all seasons
      const seasonRegex = /href="episodes\.php\?title=[^"&]+&amp;category=[^"&]+&amp;season=(\d+)"|href="episodes\.php\?title=[^"&]+&category=[^"&]+&season=(\d+)"/g;
      let sm: RegExpExecArray | null;
      const seasonNums = new Set<number>();
      while ((sm = seasonRegex.exec(html)) !== null) {
        const num = parseInt(sm[1] || sm[2]);
        if (num > 0) seasonNums.add(num);
      }

      if (seasonNums.size === 0) {
        const sMatches = html.match(/Season\s*(\d+)/gi);
        if (sMatches) {
          sMatches.forEach(s => {
            const n = parseInt(s.replace(/\D/g, ''));
            if (n > 0) seasonNums.add(n);
          });
        }
      }

      if (seasonNums.size === 0) seasonNums.add(1);
      const sortedSeasons = Array.from(seasonNums).sort((a, b) => a - b);

      // Fetch episodes for all discovered seasons in parallel
      const seasons: Season[] = await Promise.all(
        sortedSeasons.map(async (sNum) => {
          const epUrl = `https://twolekede.com/episodes.php?title=${encodeURIComponent(seriesTitle)}&category=${encodeURIComponent(seriesVj)}&season=${sNum}`;
          try {
            const epResp = await fetch(epUrl, {
              headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
                'Referer': 'https://twolekede.com/'
              },
              signal: AbortSignal.timeout(8000)
            });

            if (!epResp.ok) return { seasonNumber: sNum, episodes: [] };
            const epHtml = await epResp.text();
            const episodes: Episode[] = [];

            // Pattern 1: .episode-info block
            const simpleRegex = /<div class="episode-info">[\s\S]*?<h3>([^<]+)<\/h3>[\s\S]*?<div class="episode-title">([^<]*)<\/div>[\s\S]*?<a href="preview\.php\?id=(\d+)"/g;
            let em: RegExpExecArray | null;
            while ((em = simpleRegex.exec(epHtml)) !== null) {
              const epHeader = em[1].trim();
              const epSub = em[2].trim();
              const epId = em[3];
              const num = parseInt(epHeader.replace(/\D/g, '')) || (episodes.length + 1);
              const epStream = `https://twolekede.com/preview.php?id=${epId}`;
              const epProxy = `${baseUrl}/api/v1/stream/proxy?url=${encodeURIComponent(epStream)}`;

              episodes.push({
                id: `ep_${epId}`,
                episodeNumber: num,
                title: epHeader + (epSub && epSub !== seriesTitle ? ` - ${epSub}` : ''),
                duration: '45m',
                streamUrl: epProxy,
                thumbnailUrl: posterUrl
              });
            }

            // Pattern 2: direct preview.php fallback
            if (episodes.length === 0) {
              const fbRegex = /<a href="preview\.php\?id=(\d+)"[^>]*>[\s\S]*?<h3>([^<]+)<\/h3>/g;
              while ((em = fbRegex.exec(epHtml)) !== null) {
                const epId = em[1];
                const epTitle = em[2].trim();
                if (epTitle.toLowerCase().includes('links')) continue;
                const num = parseInt(epTitle.replace(/\D/g, '')) || (episodes.length + 1);
                const epStream = `https://twolekede.com/preview.php?id=${epId}`;
                const epProxy = `${baseUrl}/api/v1/stream/proxy?url=${encodeURIComponent(epStream)}`;

                episodes.push({
                  id: `ep_${epId}`,
                  episodeNumber: num,
                  title: epTitle,
                  duration: '45m',
                  streamUrl: epProxy,
                  thumbnailUrl: posterUrl
                });
              }
            }

            return {
              seasonNumber: sNum,
              episodes
            };
          } catch {
            return { seasonNumber: sNum, episodes: [] };
          }
        })
      );

      return {
        seasons: seasons.filter(s => s.episodes.length > 0),
        posterUrl,
        backdropUrl: posterUrl,
        description,
        year
      };
    } catch {
      return null;
    }
  }

  /**
   * Fetches rich genuine details for an individual movie or series
   */
  public static async getMovieDetails(id: string, title?: string, baseUrl: string = ''): Promise<MediaItem | null> {
    const cacheKey = `details:${id}:${title || ''}`;
    const cached = this.getFromCache<MediaItem>(cacheKey);
    if (cached) return cached;

    const cleanId = id.replace(/^(myvj_|tk_)/, '');

    // 0. If it's a Twolekede series ID (tk_series_Title_Category)
    if (id.startsWith('tk_series_')) {
      const parts = id.replace('tk_series_', '').split('_');
      const seriesTitleSlug = parts[0] || '';
      const seriesCatSlug = parts[1] || '';
      const seriesTitle = decodeURIComponent(seriesTitleSlug).replace(/\+/g, ' ');
      const seriesVj = decodeURIComponent(seriesCatSlug).replace(/\+/g, ' ');

      const seriesData = await this.fetchTwolekedeSeriesSeasonsAndEpisodes(seriesTitle, seriesVj, baseUrl);
      if (seriesData && seriesData.seasons.length > 0) {
        const firstEp = seriesData.seasons[0]?.episodes?.[0];
        const firstStream = firstEp?.streamUrl || '';
        const totalEps = seriesData.seasons.reduce((acc, s) => acc + s.episodes.length, 0);

        const item: MediaItem = {
          id,
          title: seriesTitle,
          type: 'series',
          vj: seriesVj.toLowerCase().includes('vj') ? seriesVj : `VJ ${seriesVj}`,
          genre: 'Ugandan Translated Series',
          year: seriesData.year || '2024',
          rating: '8.9',
          duration: `${seriesData.seasons.length} Seasons • ${totalEps} Episodes`,
          description: seriesData.description || `${seriesTitle} multi-season translated series by ${seriesVj} on Munowatch TV.`,
          posterUrl: seriesData.posterUrl,
          backdropUrl: seriesData.backdropUrl,
          streamUrl: firstStream,
          isSeries: true,
          isStreamAvailable: true,
          seasons: seriesData.seasons,
          servers: firstStream ? [
            { id: 's2', name: 'Server 2 (Munowatch Web Streamer - CORS Safe)', quality: '1080p Full HD', streamUrl: firstStream, isProxy: true },
            { id: 's1', name: 'Server 1 (High-Speed Direct)', quality: '1080p FHD', streamUrl: firstStream, isProxy: false }
          ] : []
        };

        this.setInCache(cacheKey, item, 60 * 60 * 1000);
        return item;
      }
    }

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

    // 2. Prioritize Searching Twolekede by Title BEFORE UgaWatch Mapping
    // This guarantees full-length Ugandan translated stream is resolved and eliminates trailer fallbacks
    let twolekedeFoundItem: MediaItem | null = null;
    const searchCandidateTitle = title || '';
    if (searchCandidateTitle.trim().length >= 2) {
      const candidateQueries = StreamProxyService.generateCandidateQueries(searchCandidateTitle);
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
              if (StreamProxyService.titlesMatch(searchCandidateTitle, cardTitle)) {
                twolekedeFoundItem = await this.getMovieDetails(`tk_${matchedId}`, searchCandidateTitle, baseUrl);
                if (twolekedeFoundItem) break;
              }
            }

            if (!twolekedeFoundItem) {
              const altCardRegex = /<a href="(?:moviedetails|preview)\.php\?id=(\d+)"[^>]*alt="([^"]*)"/g;
              let altM: RegExpExecArray | null;
              while ((altM = altCardRegex.exec(html)) !== null) {
                const matchedId = altM[1];
                const cardTitle = (altM[2] || '').trim();
                if (StreamProxyService.titlesMatch(searchCandidateTitle, cardTitle)) {
                  twolekedeFoundItem = await this.getMovieDetails(`tk_${matchedId}`, searchCandidateTitle, baseUrl);
                  if (twolekedeFoundItem) break;
                }
              }
            }

            if (twolekedeFoundItem) break;
          }
        } catch {}
      }
    }

    // 3. If it's an UgaWatch / MyVJ item (movie or series)
    if (id.startsWith('myvj_') || /^\d+$/.test(cleanId)) {
      try {
        const bodyParams = new URLSearchParams({
          source: 'next_js',
          id: cleanId,
          movie_id: cleanId,
          serie_id: cleanId
        });

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
              const itemTitle = m.title || title || 'Movie';

              // If Twolekede had not been searched yet (e.g. title was missing from request), search using m.title
              if (!twolekedeFoundItem && itemTitle && itemTitle.trim().length >= 2) {
                const candidateQueries = StreamProxyService.generateCandidateQueries(itemTitle);
                for (const q of candidateQueries) {
                  try {
                    const sResp = await fetch(`https://twolekede.com/search.php?q=${encodeURIComponent(q)}`, {
                      headers: {
                        'Referer': 'https://twolekede.com/',
                        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
                      },
                      signal: AbortSignal.timeout(5000)
                    });
                    if (sResp.ok) {
                      const sHtml = await sResp.text();
                      const cRegex = /<a href="(?:moviedetails|preview)\.php\?id=(\d+)"[^>]*>[\s\S]*?<img[^>]+alt="([^"]*)"[\s\S]*?<h3 class="content-title">([^<]*)<\/h3>/g;
                      let sm: RegExpExecArray | null;
                      while ((sm = cRegex.exec(sHtml)) !== null) {
                        const sMatchedId = sm[1];
                        const sCardTitle = (sm[3] || sm[2] || '').trim();
                        if (StreamProxyService.titlesMatch(itemTitle, sCardTitle)) {
                          twolekedeFoundItem = await this.getMovieDetails(`tk_${sMatchedId}`, itemTitle, baseUrl);
                          if (twolekedeFoundItem) break;
                        }
                      }
                      if (twolekedeFoundItem) break;
                    }
                  } catch {}
                }
              }

              // Filter out trailers and intro clips completely
              const rawFile = m.bunny_file_name || (isSeriesItem ? data.episodes[0]?.bunny_file_name : null) || m.video || '';
              const cleanFile = typeof rawFile === 'string' ? rawFile.replace(/\\/g, '').trim() : '';

              // Direct stream resolution: prioritize Twolekede stream if found
              let directStream = '';
              let hasVerifiedFullStream = false;

              if (twolekedeFoundItem && twolekedeFoundItem.streamUrl && !StreamProxyService.isTrailerOrPromoUrl(twolekedeFoundItem.streamUrl, title)) {
                directStream = twolekedeFoundItem.streamUrl;
                hasVerifiedFullStream = true;
              } else if (cleanFile.startsWith('http') && !StreamProxyService.isTrailerOrPromoUrl(cleanFile, title)) {
                directStream = cleanFile;
                hasVerifiedFullStream = true;
              }

              const proxyUrl = directStream ? `${baseUrl}/api/v1/stream/proxy?url=${encodeURIComponent(directStream)}` : '';

              const munoImg = m.img_port_muno_file_name || '';
              const tmdbPoster = m.tmdb_poster_path || '';
              let posterUrl = '';
              if (tmdbPoster && tmdbPoster !== 'null') {
                posterUrl = `https://image.tmdb.org/t/p/w500${tmdbPoster.startsWith('/') ? tmdbPoster : `/${tmdbPoster}`}`;
              } else if (munoImg && munoImg !== 'null') {
                posterUrl = munoImg.startsWith('http') ? munoImg : `https://res-watch.b-cdn.net/1/${munoImg}`;
              } else if (twolekedeFoundItem?.posterUrl) {
                posterUrl = twolekedeFoundItem.posterUrl;
              }

              const tmdbBackdrop = m.tmdb_backdrop_path || '';
              let backdropUrl = posterUrl;
              if (tmdbBackdrop && tmdbBackdrop !== 'null') {
                backdropUrl = `https://image.tmdb.org/t/p/original${tmdbBackdrop.startsWith('/') ? tmdbBackdrop : `/${tmdbBackdrop}`}`;
              }

              let vj = m.vj && m.vj !== '-' && m.vj !== 'null' ? m.vj : (twolekedeFoundItem?.vj || 'VJ Junior');
              if (!vj.toLowerCase().includes('vj')) vj = `VJ ${vj}`;

              // Map episodes for series (sanitize trailers)
              let seasons: any[] | undefined = undefined;
              if (isSeriesItem) {
                const seasonsMap = new Map<number, any[]>();
                for (const ep of data.episodes) {
                  const sNum = ep.season || 1;
                  const epFile = ep.bunny_file_name || ep.video || '';
                  const cleanEpFile = typeof epFile === 'string' ? epFile.replace(/\\/g, '').trim() : '';
                  const epStream = (cleanEpFile.startsWith('http') && !StreamProxyService.isTrailerOrPromoUrl(cleanEpFile, ep.title || title))
                    ? cleanEpFile
                    : (cleanEpFile ? `https://munotek.b-cdn.net/${cleanEpFile}` : '');
                  const epProxy = epStream ? `${baseUrl}/api/v1/stream/proxy?url=${encodeURIComponent(epStream)}` : '';

                  const episodeObj = {
                    id: `ep_${ep.id || ep.episode_number}`,
                    episodeNumber: ep.episode_number || 1,
                    title: ep.title || `Episode ${ep.episode_number}`,
                    duration: ep.duration ? `${ep.duration}m` : '45m',
                    streamUrl: epProxy || epStream,
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

              // If it's a series and seasons is still empty, query Twolekede series details to populate all seasons and episodes
              if ((isSeriesItem || m.type === 'series' || m.serie_id) && (!seasons || seasons.length === 0)) {
                const twolekedeSeries = await this.fetchTwolekedeSeriesSeasonsAndEpisodes(itemTitle, vj, baseUrl);
                if (twolekedeSeries && twolekedeSeries.seasons.length > 0) {
                  seasons = twolekedeSeries.seasons;
                }
              }

              const item: MediaItem = {
                id: `myvj_${cleanId}`,
                title: itemTitle,
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
                isStreamAvailable: hasVerifiedFullStream,
                seasons: seasons,
                servers: hasVerifiedFullStream ? [
                  { id: 's2', name: 'Server 2 (Munowatch Web Streamer - CORS Safe)', quality: '1080p Full HD', streamUrl: proxyUrl, isProxy: true },
                  { id: 's1', name: 'Server 1 (High-Speed Direct)', quality: '1080p FHD', streamUrl: directStream, isProxy: false }
                ] : []
              };

              this.setInCache(cacheKey, item, 60 * 60 * 1000);
              return item;
            }
          }
        }
      } catch {}
    }

    // 4. If Twolekede found item and UgaWatch had no details, return Twolekede item directly
    if (twolekedeFoundItem) {
      this.setInCache(cacheKey, twolekedeFoundItem, 60 * 60 * 1000);
      return twolekedeFoundItem;
    }

    // 5. Fallback to rich catalog entry if matched
    const local = RICH_CATALOG.find(i => i.id === id || (title && i.title.toLowerCase().includes(title.toLowerCase())));
    if (local) return this.enrichWithServers(local, baseUrl);

    return null;
  }
}
