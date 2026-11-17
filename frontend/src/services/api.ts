import { MediaItem, CatalogRail, VjItem } from '../types';

const API_BASE = (import.meta as any).env?.VITE_API_URL || 'https://munowatch-api.onrender.com/api/v1';

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
    id: "vj_jun_1",
    title: "Deadpool & Wolverine (VJ Junior)",
    type: "movie",
    vj: "VJ Junior",
    genre: "Action / Comedy / Sci-Fi",
    year: "2024",
    rating: "8.8",
    duration: "2h 08m",
    description: "Wolverine is recovering from his injuries when he crosses paths with the loudmouth, Deadpool. They team up to defeat a common enemy in classic VJ Junior translated hype.",
    posterUrl: "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
    backdropUrl: "https://image.tmdb.org/t/p/original/yDHYTfA3R0jFYba16jBB1jv8v2C.jpg",
    streamUrl: "https://cdn.movviez.com/secure.php?file=Deadpool_And_Wolverine_2024_VJ_Junior.mp4",
    isSeries: false,
    servers: [
      { id: "s1", name: "Server 1 (BunnyCDN High-Speed)", quality: "1080p Full HD", streamUrl: "https://res-watch.b-cdn.net/1/Deadpool_Wolverine_Junior.mp4", isProxy: false },
      { id: "s2", name: "Server 2 (Twolekede Media)", quality: "720p HD", streamUrl: "https://twolekede.com/preview.php?id=8291", isProxy: false }
    ]
  },
  {
    id: "vj_jun_2",
    title: "Bad Boys: Ride or Die (VJ Junior)",
    type: "movie",
    vj: "VJ Junior",
    genre: "Action / Comedy / Crime",
    year: "2024",
    rating: "8.5",
    duration: "1h 55m",
    description: "Miami's favorite detectives Mike Lowrey and Marcus Burnett find themselves on the run after their late captain is framed in a cartel conspiracy.",
    posterUrl: "https://image.tmdb.org/t/p/w500/nP6RliHjxsz4irTKsxe8FRhKZYl.jpg",
    backdropUrl: "https://image.tmdb.org/t/p/original/ga4OLm4qLx69To6JnVsj24bswwF.jpg",
    streamUrl: "https://cdn.movviez.com/secure.php?file=Bad_Boys_Ride_Or_Die_2024_VJ_Junior.mp4",
    isSeries: false
  },
  {
    id: "vj_jun_3",
    title: "Furiosa: A Mad Max Saga (VJ Junior)",
    type: "movie",
    vj: "VJ Junior",
    genre: "Action / Sci-Fi / Adventure",
    year: "2024",
    rating: "8.7",
    duration: "2h 28m",
    description: "As the world falls, young Furiosa is snatched from the Green Place of Many Mothers into the hands of a great Biker Horde led by the Warlord Dementus.",
    posterUrl: "https://image.tmdb.org/t/p/w500/iADOJ8Zymht2JPMoy3R7xceZprc.jpg",
    backdropUrl: "https://image.tmdb.org/t/p/original/wNAhuOZ3Zf84jCI5TeTVxoQ1OT.jpg",
    streamUrl: "https://cdn.movviez.com/secure.php?file=Furiosa_A_Mad_Max_Saga_2024_VJ_Junior.mp4",
    isSeries: false
  },
  {
    id: "vj_emmy_1",
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
    streamUrl: "https://cdn.movviez.com/secure.php?file=Dune_Part_Two_2024_VJ_Emmy.mp4",
    isSeries: false
  },
  {
    id: "vj_jingo_1",
    title: "Monkey Man (VJ Jingo)",
    type: "movie",
    vj: "VJ Jingo",
    genre: "Action / Thriller / Crime",
    year: "2024",
    rating: "8.6",
    duration: "2h 01m",
    description: "Kid unleashes an explosive campaign of retribution against the corrupt leaders who murdered his mother and systematically victimize the powerless.",
    posterUrl: "https://image.tmdb.org/t/p/w500/4lhR49nmvFd17ZqT54p4U3b7j8p.jpg",
    backdropUrl: "https://image.tmdb.org/t/p/original/w4p8b0Z8bZgNq7gT6wF2t9xL3r.jpg",
    streamUrl: "https://cdn.movviez.com/secure.php?file=Monkey_Man_2024_VJ_Jingo.mp4",
    isSeries: false
  },
  {
    id: "vj_icep_1",
    title: "Alien: Romulus (VJ Ice P)",
    type: "movie",
    vj: "VJ Ice P",
    genre: "Sci-Fi / Horror / Thriller",
    year: "2024",
    rating: "8.7",
    duration: "1h 59m",
    description: "While scavenging the deep ends of a derelict space station, a group of young space colonists come face to face with the most terrifying life form.",
    posterUrl: "https://image.tmdb.org/t/p/w500/b33nnKl1GSFbao8l3Pgs4sd4vLJ.jpg",
    backdropUrl: "https://image.tmdb.org/t/p/original/9SSEUrSqhljBMzRe4aBTh17r0qC.jpg",
    streamUrl: "https://cdn.movviez.com/secure.php?file=Alien_Romulus_2024_VJ_Ice_P.mp4",
    isSeries: false
  },
  {
    id: "vj_series_shogun",
    title: "Shōgun (VJ Junior)",
    type: "series",
    vj: "VJ Junior",
    genre: "Drama / History / War",
    year: "2024",
    rating: "9.3",
    duration: "10 Episodes",
    description: "When a mysterious European ship is found marooned in a nearby fishing village, Lord Yoshii Toranaga discovers secrets that could tip the scales of power in feudal Japan.",
    posterUrl: "https://image.tmdb.org/t/p/w500/7O4iVfOMQmdCSxhOg1WnzG1AgYT.jpg",
    backdropUrl: "https://image.tmdb.org/t/p/original/5zmiBoMzeW1U70e2p5V2zO8x8.jpg",
    streamUrl: "https://cdn.movviez.com/secure.php?file=Shogun_S01E01_VJ_Junior.mp4",
    isSeries: true,
    seasons: [
      {
        seasonNumber: 1,
        episodes: [
          { episodeNumber: 1, title: "Chapter One: Anjin", duration: "1h 05m", streamUrl: "https://cdn.movviez.com/secure.php?file=Shogun_S01E01_VJ_Junior.mp4", thumbnailUrl: "https://image.tmdb.org/t/p/w500/7O4iVfOMQmdCSxhOg1WnzG1AgYT.jpg" },
          { episodeNumber: 2, title: "Chapter Two: Servants of Two Masters", duration: "58m", streamUrl: "https://cdn.movviez.com/secure.php?file=Shogun_S01E02_VJ_Junior.mp4", thumbnailUrl: "https://image.tmdb.org/t/p/w500/7O4iVfOMQmdCSxhOg1WnzG1AgYT.jpg" },
          { episodeNumber: 3, title: "Chapter Three: Tomorrow is Tomorrow", duration: "56m", streamUrl: "https://cdn.movviez.com/secure.php?file=Shogun_S01E03_VJ_Junior.mp4", thumbnailUrl: "https://image.tmdb.org/t/p/w500/7O4iVfOMQmdCSxhOg1WnzG1AgYT.jpg" }
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

  async getMovies(options: { page?: number; perPage?: number; year?: string; vj?: string; genre?: string; sort?: string } = {}): Promise<{ items: MediaItem[]; count: number; page: number }> {
    try {
      const params = new URLSearchParams();
      if (options.page) params.append('page', options.page.toString());
      if (options.perPage) params.append('perPage', options.perPage.toString());
      if (options.year) params.append('year', options.year);
      if (options.vj) params.append('vj', options.vj);
      if (options.genre) params.append('genre', options.genre);
      if (options.sort) params.append('sort', options.sort);

      const qs = params.toString() ? `?${params.toString()}` : '';
      const res = await fetch(`${API_BASE}/catalog/movies${qs}`, { signal: AbortSignal.timeout(6000) });
      if (res.ok) {
        const data = await res.json();
        return {
          items: data.items || [],
          count: data.count || data.items?.length || 0,
          page: data.page || options.page || 1
        };
      }
    } catch {}
    const filtered = FALLBACK_CATALOG.filter(i => !i.isSeries);
    return { items: filtered, count: filtered.length, page: 1 };
  },

  async getSeries(options: { page?: number; perPage?: number; vj?: string; genre?: string } = {}): Promise<{ items: MediaItem[]; count: number; page: number }> {
    try {
      const params = new URLSearchParams();
      if (options.page) params.append('page', options.page.toString());
      if (options.perPage) params.append('perPage', options.perPage.toString());
      if (options.vj) params.append('vj', options.vj);
      if (options.genre) params.append('genre', options.genre);

      const qs = params.toString() ? `?${params.toString()}` : '';
      const res = await fetch(`${API_BASE}/catalog/series${qs}`, { signal: AbortSignal.timeout(6000) });
      if (res.ok) {
        const data = await res.json();
        return {
          items: data.items || [],
          count: data.count || data.items?.length || 0,
          page: data.page || options.page || 1
        };
      }
    } catch {}
    const filtered = FALLBACK_CATALOG.filter(i => i.isSeries);
    return { items: filtered, count: filtered.length, page: 1 };
  },

  async resolveStream(id: string, title?: string): Promise<{ streamUrl: string; directUrl: string; servers?: any[] }> {
    try {
      const params = new URLSearchParams();
      params.append('id', id);
      if (title) params.append('title', title);

      const res = await fetch(`${API_BASE}/stream/resolve?${params.toString()}`, { signal: AbortSignal.timeout(7000) });
      if (res.ok) {
        const data = await res.json();
        if (data.success && data.streamUrl) {
          return { streamUrl: data.streamUrl, directUrl: data.directUrl, servers: data.servers };
        }
      }
    } catch {}
    return { streamUrl: '', directUrl: '' };
  },

  async search(query: string): Promise<MediaItem[]> {
    if (!query.trim()) return [];
    try {
      const res = await fetch(`${API_BASE}/catalog/search?q=${encodeURIComponent(query)}`, { signal: AbortSignal.timeout(3000) });
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
      const res = await fetch(`${API_BASE}/catalog/title/${id}`, { signal: AbortSignal.timeout(3000) });
      if (res.ok) {
        return await res.json();
      }
    } catch {}
    return FALLBACK_CATALOG.find(i => i.id === id) || null;
  },

  getProxyStreamUrl(streamUrl: string): string {
    return `${API_BASE}/stream/proxy?url=${encodeURIComponent(streamUrl)}`;
  }
};
