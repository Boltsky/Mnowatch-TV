import { MediaItem, CatalogRail, VjItem } from '../types';

/**
 * Validates whether a URL or filename represents a promotional trailer, teaser, or intro clip
 */
export const isTrailerOrIntroUrl = (url?: string | null, expectedTitle?: string): boolean => {
  if (!url || typeof url !== 'string') return false;
  let lower = url.toLowerCase().trim();
  if (lower.length === 0) return false;

  // 1. Iteratively decode (up to 3 times)
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

  // 2. Forbidden CDN pull zones or hostnames
  if (lower.includes('trailer256.b-cdn.net')) return true;
  if (lower.includes('intro_myvj')) return true;

  // 3. Query parameter indicators
  if (
    /[?&](?:type|clip|mode|file)=(?:trailer|teaser|promo|sample)\b/i.test(lower) ||
    /[?&]trailer\b/i.test(lower) ||
    /[?&]teaser\b/i.test(lower) ||
    /[?&]promo\b/i.test(lower)
  ) {
    return true;
  }

  // 4. Context-aware title protection
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
    testStr = testStr.replace(/trailer[^a-z0-9]+park[^a-z0-9]+boys/gi, 'TITLEREPLACED');
    testStr = testStr.replace(/the[^a-z0-9]+trailer(?=[^a-z0-9]+(?:19\d\d|20\d\d|x264|720p|1080p|hd|vj|full|\.))/gi, 'TITLEREPLACED');
  }

  if (testStr.includes('trailer') && testStr.includes('b-cdn.net')) return true;

  // 5. Token boundaries
  const trailerTokenRegex = /(?:^|[._\/-])trailer(?:s)?(?:[._\/-]|\.mp4|\.m3u8|$)/i;
  if (trailerTokenRegex.test(testStr)) return true;

  const teaserTokenRegex = /(?:^|[._\/-])teaser(?:s)?(?:[._\/-]|\.mp4|\.m3u8|$)/i;
  if (teaserTokenRegex.test(testStr)) return true;

  const sampleTokenRegex = /(?:^|[._\/-])sample(?:s)?(?:[._\/-]|\.mp4|\.m3u8|$)/i;
  if (sampleTokenRegex.test(testStr)) return true;

  const promoTokenRegex = /(?:^|[._\/-])promo(?:s)?(?:[._\/-]|\.mp4|\.m3u8|$)/i;
  if (promoTokenRegex.test(testStr)) return true;

  return false;
};

const RAW_ENV = (import.meta as any).env?.VITE_API_URL || 'https://munowatch-api.onrender.com/api/v1';
const API_BASE = RAW_ENV.endsWith('/api/v1') ? RAW_ENV : `${RAW_ENV.replace(/\/+$/, '')}/api/v1`;

export const FALLBACK_VJS: VjItem[] = [
  { id: "vj_junior", name: "VJ Junior", icon: "🎬", count: 48 },
  { id: "vj_emmy", name: "VJ Emmy", icon: "🔥", count: 36 },
  { id: "vj_jingo", name: "VJ Jingo", icon: "⚡", count: 32 },
  { id: "vj_icep", name: "VJ Ice P", icon: "❄️", count: 28 },
  { id: "vj_ulio", name: "VJ Ulio", icon: "🎯", count: 24 },
  { id: "vj_mark", name: "VJ Mark", icon: "🌟", count: 20 },
  { id: "vj_nelly", name: "VJ Nelly", icon: "🎙️", count: 18 },
  { id: "vj_kimuli", name: "VJ Kimuli", icon: "👑", count: 16 }
];

export const FALLBACK_CATALOG: MediaItem[] = [
  {
    id: "tk_2667",
    title: "Deadpool & Wolverine",
    type: "movie",
    vj: "VJ ICEP",
    genre: "Action / Comedy / Sci-Fi",
    year: "2024",
    rating: "8.8",
    duration: "2h 08m",
    description: "A listless Wade Wilson toils away in civilian life with his days as the morally flexible mercenary, Deadpool, behind him. But when his homeworld faces an existential threat, Wade must reluctantly suit-up again with an even more reluctant Wolverine.",
    posterUrl: "https://twolekede.com/mv-admin/uploads/posters/1750261808_6852e03050f1b.webp",
    backdropUrl: "https://image.tmdb.org/t/p/original/yDHYTfA3R0jFYba16jBB1jv8v2C.jpg",
    streamUrl: "https://twolekede.com/preview.php?id=2667",
    isSeries: false,
    servers: [
      { id: "s2", name: "Server 2 (Munowatch Web Streamer - CORS Safe)", quality: "1080p Full HD", streamUrl: "https://twolekede.com/preview.php?id=2667", isProxy: true },
      { id: "s1", name: "Server 1 (High-Speed Direct)", quality: "1080p FHD", streamUrl: "https://twolekede.com/preview.php?id=2667", isProxy: false }
    ]
  },
  {
    id: "tk_12950",
    title: "Bad Boys: Ride or Die",
    type: "movie",
    vj: "VJ Junior",
    genre: "Action / Comedy / Crime",
    year: "2024",
    rating: "8.5",
    duration: "1h 55m",
    description: "After their late former Captain is framed, Lowrey and Burnett try to clear his name, only to end up on the run themselves in classic VJ Junior hype.",
    posterUrl: "https://twolekede.com/mv-admin/uploads/posters/1774293983_69c193df07154.jpg",
    backdropUrl: "https://image.tmdb.org/t/p/original/ga4OLm4qLx69To6JnVsj24bswwF.jpg",
    streamUrl: "https://twolekede.com/preview.php?id=12950",
    isSeries: false,
    servers: [
      { id: "s2", name: "Server 2 (Munowatch Web Streamer - CORS Safe)", quality: "1080p Full HD", streamUrl: "https://twolekede.com/preview.php?id=12950", isProxy: true },
      { id: "s1", name: "Server 1 (High-Speed Direct)", quality: "1080p FHD", streamUrl: "https://twolekede.com/preview.php?id=12950", isProxy: false }
    ]
  },
  {
    id: "tk_1421",
    title: "Furiosa: A Mad Max Saga Part 1",
    type: "movie",
    vj: "VJ ICEP",
    genre: "Action / Sci-Fi / Adventure",
    year: "2024",
    rating: "8.7",
    duration: "2h 28m",
    description: "After being snatched from the Green Place of Many Mothers, young Furiosa falls into the hands of a great Biker Horde led by the Warlord Dementus.",
    posterUrl: "https://twolekede.com/mv-admin/uploads/posters/1748196262_mad max.jpg",
    backdropUrl: "https://image.tmdb.org/t/p/original/wNAhuOZ3Zf84jCI5TeTVxoQ1OT.jpg",
    streamUrl: "https://twolekede.com/preview.php?id=1421",
    isSeries: false,
    servers: [
      { id: "s2", name: "Server 2 (Munowatch Web Streamer - CORS Safe)", quality: "1080p Full HD", streamUrl: "https://twolekede.com/preview.php?id=1421", isProxy: true },
      { id: "s1", name: "Server 1 (High-Speed Direct)", quality: "1080p FHD", streamUrl: "https://twolekede.com/preview.php?id=1421", isProxy: false }
    ]
  },
  {
    id: "tk_2668",
    title: "Dune: Part Two (VJ Emmy)",
    type: "movie",
    vj: "VJ Emmy",
    genre: "Sci-Fi / Adventure",
    year: "2024",
    rating: "9.1",
    duration: "2h 46m",
    description: "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.",
    posterUrl: "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
    backdropUrl: "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s520DRq.jpg",
    streamUrl: "https://twolekede.com/preview.php?id=2668",
    isSeries: false,
    servers: [
      { id: "s2", name: "Server 2 (Munowatch Web Streamer - CORS Safe)", quality: "1080p Full HD", streamUrl: "https://twolekede.com/preview.php?id=2668", isProxy: true },
      { id: "s1", name: "Server 1 (High-Speed Direct)", quality: "1080p FHD", streamUrl: "https://twolekede.com/preview.php?id=2668", isProxy: false }
    ]
  },
  {
    id: "tk_12952",
    title: "Road House (VJ Jingo)",
    type: "movie",
    vj: "VJ Jingo",
    genre: "Action / Thriller",
    year: "2024",
    rating: "8.1",
    duration: "2h 01m",
    description: "Ex-UFC fighter Dalton takes a job as a bouncer at a Florida Keys roadhouse, only to discover that this paradise is not all it seems.",
    posterUrl: "https://image.tmdb.org/t/p/w500/bXi6IQiCuHD00X977x9GNDurAoq.jpg",
    backdropUrl: "https://image.tmdb.org/t/p/original/oe7mWXYY9jkT4v47qLA8vD6sLw9.jpg",
    streamUrl: "https://twolekede.com/preview.php?id=12952",
    isSeries: false,
    servers: [
      { id: "s2", name: "Server 2 (Munowatch Web Streamer - CORS Safe)", quality: "1080p Full HD", streamUrl: "https://twolekede.com/preview.php?id=12952", isProxy: true },
      { id: "s1", name: "Server 1 (High-Speed Direct)", quality: "1080p FHD", streamUrl: "https://twolekede.com/preview.php?id=12952", isProxy: false }
    ]
  },
  {
    id: "tk_12955",
    title: "The Beekeeper (VJ Junior)",
    type: "movie",
    vj: "VJ Junior",
    genre: "Action / Thriller",
    year: "2024",
    rating: "8.6",
    duration: "1h 45m",
    description: "One man's brutal campaign for vengeance takes on national stakes after he is revealed to be a former operative of a powerful and clandestine organization known as 'Beekeepers'.",
    posterUrl: "https://image.tmdb.org/t/p/w500/A7EByudX0eOzlkQ2FIbogzyazm2.jpg",
    backdropUrl: "https://image.tmdb.org/t/p/original/4woSOUD0equAYzvwhWBHIJDCM88.jpg",
    streamUrl: "https://twolekede.com/preview.php?id=12955",
    isSeries: false,
    servers: [
      { id: "s2", name: "Server 2 (Munowatch Web Streamer - CORS Safe)", quality: "1080p Full HD", streamUrl: "https://twolekede.com/preview.php?id=12955", isProxy: true },
      { id: "s1", name: "Server 1 (High-Speed Direct)", quality: "1080p FHD", streamUrl: "https://twolekede.com/preview.php?id=12955", isProxy: false }
    ]
  },
  {
    id: "tk_series_hotd",
    title: "House of the Dragon (VJ Bonny / VJ Junior)",
    type: "series",
    vj: "VJ Junior",
    genre: "Fantasy / Drama / Action",
    year: "2024",
    rating: "9.3",
    duration: "3 Seasons",
    description: "The Targaryen civil war begins. Rhaenyra and Daemon clash with the Greens in a devastating clash of blood and dragons.",
    posterUrl: "https://image.tmdb.org/t/p/w500/1X4h40fcB4WWUmIBK0auT4z9vub.jpg",
    backdropUrl: "https://image.tmdb.org/t/p/original/etj5CuMuamfdIip3Q8G60Hg0Y.jpg",
    streamUrl: "https://twolekede.com/preview.php?id=125784",
    isSeries: true,
    seasons: [
      {
        seasonNumber: 1,
        episodes: [
          { episodeNumber: 1, title: "The Heirs of the Dragon", duration: "1h 06m", streamUrl: "https://twolekede.com/preview.php?id=125784", thumbnailUrl: "https://image.tmdb.org/t/p/w500/1X4h40fcB4WWUmIBK0auT4z9vub.jpg" },
          { episodeNumber: 2, title: "The Rogue Prince", duration: "54m", streamUrl: "https://twolekede.com/preview.php?id=125785", thumbnailUrl: "https://image.tmdb.org/t/p/w500/1X4h40fcB4WWUmIBK0auT4z9vub.jpg" },
          { episodeNumber: 3, title: "Second of His Name", duration: "1h 03m", streamUrl: "https://twolekede.com/preview.php?id=125786", thumbnailUrl: "https://image.tmdb.org/t/p/w500/1X4h40fcB4WWUmIBK0auT4z9vub.jpg" }
        ]
      }
    ]
  }
];

export const api = {
  async getHome(): Promise<{ rails: CatalogRail[]; featured: MediaItem | null }> {
    try {
      const res = await fetch(`${API_BASE}/catalog/home`, { signal: AbortSignal.timeout(4000) });
      if (res.ok) {
        return await res.json();
      }
    } catch {
      // Fallback seamlessly
    }

    return {
      rails: [
        { id: "rail_trending", title: "🔥 Trending Ugandan Translated Movies", items: FALLBACK_CATALOG.filter(i => !i.isSeries) },
        { id: "rail_junior", title: "🎬 VJ Junior Exclusive Translations", items: FALLBACK_CATALOG.filter(i => i.vj.includes("Junior")) },
        { id: "rail_emmy", title: "💥 VJ Emmy Cinematic Hits", items: FALLBACK_CATALOG.filter(i => i.vj.includes("Emmy")) },
        { id: "rail_series", title: "📺 Ugandan Translated TV Series", items: FALLBACK_CATALOG.filter(i => i.isSeries) }
      ],
      featured: FALLBACK_CATALOG[0]
    };
  },

  async getVjs(): Promise<VjItem[]> {
    try {
      const res = await fetch(`${API_BASE}/catalog/vjs`, { signal: AbortSignal.timeout(3000) });
      if (res.ok) {
        const data = await res.json();
        return data.vjs || FALLBACK_VJS;
      }
    } catch {}
    return FALLBACK_VJS;
  },

  async getMovies(options: { page?: number; perPage?: number; year?: string; vj?: string; genre?: string; sort?: string } = {}): Promise<{ items: MediaItem[]; count: number; total?: number; page: number; hasMore?: boolean }> {
    try {
      const params = new URLSearchParams();
      if (options.page) params.append('page', options.page.toString());
      if (options.perPage) params.append('perPage', options.perPage.toString());
      if (options.year) params.append('year', options.year);
      if (options.vj) params.append('vj', options.vj);
      if (options.genre) params.append('genre', options.genre);
      if (options.sort) params.append('sort', options.sort);

      const qs = params.toString() ? `?${params.toString()}` : '';
      const res = await fetch(`${API_BASE}/catalog/movies${qs}`, { signal: AbortSignal.timeout(12000) });
      if (res.ok) {
        const data = await res.json();
        return {
          items: data.items || [],
          count: data.count || data.items?.length || 0,
          total: data.total || data.count || data.items?.length || 0,
          hasMore: data.hasMore ?? (data.items?.length >= (options.perPage || 30)),
          page: data.page || options.page || 1
        };
      }
    } catch {}
    const filtered = FALLBACK_CATALOG.filter(i => !i.isSeries);
    return { items: filtered, count: filtered.length, total: filtered.length, page: 1, hasMore: false };
  },

  async getSeries(options: { page?: number; perPage?: number; vj?: string; genre?: string } = {}): Promise<{ items: MediaItem[]; count: number; total?: number; page: number; hasMore?: boolean }> {
    try {
      const params = new URLSearchParams();
      if (options.page) params.append('page', options.page.toString());
      if (options.perPage) params.append('perPage', options.perPage.toString());
      if (options.vj) params.append('vj', options.vj);
      if (options.genre) params.append('genre', options.genre);

      const qs = params.toString() ? `?${params.toString()}` : '';
      const res = await fetch(`${API_BASE}/catalog/series${qs}`, { signal: AbortSignal.timeout(12000) });
      if (res.ok) {
        const data = await res.json();
        return {
          items: data.items || [],
          count: data.count || data.items?.length || 0,
          total: data.total || data.count || data.items?.length || 0,
          hasMore: data.hasMore ?? (data.items?.length >= (options.perPage || 30)),
          page: data.page || options.page || 1
        };
      }
    } catch {}
    const filtered = FALLBACK_CATALOG.filter(i => i.isSeries);
    return { items: filtered, count: filtered.length, total: filtered.length, page: 1, hasMore: false };
  },
  async resolveStream(id: string, title?: string): Promise<{ streamUrl: string; directUrl: string; servers?: any[]; error?: string; unavailable?: boolean; alternative?: { id: string; title: string } }> {
    try {
      const params = new URLSearchParams();
      params.append('id', id);
      if (title) params.append('title', title);

      const res = await fetch(`${API_BASE}/stream/resolve?${params.toString()}`, { signal: AbortSignal.timeout(12000) });
      if (res.ok) {
        const data = await res.json();
        if (data.success && data.streamUrl && !isTrailerOrIntroUrl(data.streamUrl, title) && !isTrailerOrIntroUrl(data.directUrl, title)) {
          return { streamUrl: data.streamUrl, directUrl: data.directUrl, servers: data.servers };
        } else if (data.unavailable || !data.success) {
          return {
            streamUrl: '',
            directUrl: '',
            unavailable: true,
            error: data.error || 'Full Ugandan translated movie is currently unavailable. No trailer playback permitted.',
            alternative: data.alternative || undefined
          };
        }
      }
    } catch {}
    return { streamUrl: '', directUrl: '', unavailable: true, error: 'Full Ugandan translated stream connection failed.' };
  },

  async search(query: string): Promise<MediaItem[]> {
    if (!query.trim()) return [];
    try {
      const res = await fetch(`${API_BASE}/catalog/search?q=${encodeURIComponent(query)}`, { signal: AbortSignal.timeout(9000) });
      if (res.ok) {
        const data = await res.json();
        return data.items || [];
      }
    } catch {}
    const q = query.toLowerCase();
    return FALLBACK_CATALOG.filter(i => i.title.toLowerCase().includes(q) || i.vj.toLowerCase().includes(q) || i.genre.toLowerCase().includes(q));
  },

  async getTitle(id: string): Promise<MediaItem | null> {
    try {
      const res = await fetch(`${API_BASE}/catalog/title/${id}`, { signal: AbortSignal.timeout(6000) });
      if (res.ok) {
        return await res.json();
      }
    } catch {}
    return FALLBACK_CATALOG.find(i => i.id === id) || null;
  },

  async getMovieDetails(id: string, title?: string): Promise<MediaItem | null> {
    try {
      const qs = title ? `?title=${encodeURIComponent(title)}` : '';
      const res = await fetch(`${API_BASE}/catalog/details/${id}${qs}`, { signal: AbortSignal.timeout(12000) });
      if (res.ok) {
        return await res.json();
      }
    } catch {}
    return null;
  },

  getProxyStreamUrl(streamUrl: string): string {
    if (!streamUrl) return '';
    if (streamUrl.includes('/stream/proxy?url=')) return streamUrl;
    return `${API_BASE}/stream/proxy?url=${encodeURIComponent(streamUrl)}`;
  }
};
