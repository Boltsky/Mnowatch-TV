import { MediaItem, CatalogRail, PlaybackServer } from '../types';
import { RICH_CATALOG, VJ_LIST, GENRE_LIST } from '../providers/richCatalog.data';

export class CatalogService {
  private static catalog: MediaItem[] = [...RICH_CATALOG];

  public static getHomeRails(baseUrl: string): CatalogRail[] {
    const attachProxyUrls = (items: MediaItem[]): MediaItem[] => {
      return items.map(item => this.enrichWithServers(item, baseUrl));
    };

    return [
      {
        id: "rail_trending",
        title: "🔥 Trending Ugandan Translated Movies",
        items: attachProxyUrls(this.catalog.filter(i => !i.isSeries).slice(0, 10))
      },
      {
        id: "rail_junior",
        title: "🎬 VJ Junior Exclusive Translations",
        vj: "VJ Junior",
        items: attachProxyUrls(this.catalog.filter(i => i.vj.toLowerCase().includes('junior')))
      },
      {
        id: "rail_emmy",
        title: "💥 VJ Emmy Cinematic Hits",
        vj: "VJ Emmy",
        items: attachProxyUrls(this.catalog.filter(i => i.vj.toLowerCase().includes('emmy')))
      },
      {
        id: "rail_series",
        title: "📺 Ugandan Translated TV Series (Multi-Season)",
        items: attachProxyUrls(this.catalog.filter(i => i.isSeries))
      },
      {
        id: "rail_jingo",
        title: "⚡ VJ Jingo Action Vault",
        vj: "VJ Jingo",
        items: attachProxyUrls(this.catalog.filter(i => i.vj.toLowerCase().includes('jingo')))
      },
      {
        id: "rail_icep",
        title: "❄️ VJ Ice P & VJ Ulio Premieres",
        items: attachProxyUrls(this.catalog.filter(i => i.vj.toLowerCase().includes('ice') || i.vj.toLowerCase().includes('ulio')))
      },
      {
        id: "rail_action",
        title: "🥊 High-Octane Action & Crime",
        genre: "Action",
        items: attachProxyUrls(this.catalog.filter(i => i.genre.toLowerCase().includes('action')))
      },
      {
        id: "rail_scifi",
        title: "🚀 Sci-Fi, Fantasy & Alien Worlds",
        genre: "Sci-Fi",
        items: attachProxyUrls(this.catalog.filter(i => i.genre.toLowerCase().includes('sci-fi') || i.genre.toLowerCase().includes('fantasy')))
      }
    ];
  }

  public static getVjs() {
    return VJ_LIST;
  }

  public static getGenres() {
    return GENRE_LIST;
  }

  public static getByVj(vjName: string, baseUrl: string): MediaItem[] {
    const clean = vjName.toLowerCase().replace('vj', '').trim();
    const matches = this.catalog.filter(i => i.vj.toLowerCase().includes(clean));
    return matches.map(m => this.enrichWithServers(m, baseUrl));
  }

  public static getByGenre(genre: string, baseUrl: string): MediaItem[] {
    const clean = genre.toLowerCase().trim();
    const matches = this.catalog.filter(i => i.genre.toLowerCase().includes(clean));
    return matches.map(m => this.enrichWithServers(m, baseUrl));
  }

  public static getAllMovies(baseUrl: string): MediaItem[] {
    return this.catalog.filter(i => !i.isSeries).map(m => this.enrichWithServers(m, baseUrl));
  }

  public static getAllSeries(baseUrl: string): MediaItem[] {
    return this.catalog.filter(i => i.isSeries).map(m => this.enrichWithServers(m, baseUrl));
  }

  public static getItemById(id: string, baseUrl: string): MediaItem | null {
    const item = this.catalog.find(i => i.id === id);
    if (!item) return null;
    return this.enrichWithServers(item, baseUrl);
  }

  public static search(query: string, baseUrl: string): MediaItem[] {
    if (!query || query.trim().length === 0) return [];
    const q = query.toLowerCase().trim();
    const results = this.catalog.filter(i =>
      i.title.toLowerCase().includes(q) ||
      i.vj.toLowerCase().includes(q) ||
      i.genre.toLowerCase().includes(q) ||
      i.description.toLowerCase().includes(q)
    );
    return results.map(r => this.enrichWithServers(r, baseUrl));
  }

  private static enrichWithServers(item: MediaItem, baseUrl: string): MediaItem {
    const proxyStreamUrl = `${baseUrl}/api/v1/stream/proxy?url=${encodeURIComponent(item.streamUrl)}`;
    
    const servers: PlaybackServer[] = [
      {
        id: "server-direct",
        name: "Server 1 (High-Speed Direct)",
        quality: "1080p Full HD",
        streamUrl: item.streamUrl,
        isProxy: false
      },
      {
        id: "server-proxy",
        name: "Server 2 (Munowatch Web Streamer - CORS Safe)",
        quality: "1080p FHD",
        streamUrl: proxyStreamUrl,
        isProxy: true
      }
    ];

    if (item.servers && item.servers.length > 0) {
      item.servers.forEach(s => {
        if (!servers.some(existing => existing.id === s.id)) {
          servers.push({
            ...s,
            streamUrl: s.isProxy ? `${baseUrl}/api/v1/stream/proxy?url=${encodeURIComponent(s.streamUrl)}` : s.streamUrl
          });
        }
      });
    }

    return {
      ...item,
      streamUrl: proxyStreamUrl, // Default to proxy for instant browser playback without CORS error
      servers: servers
    };
  }
}
