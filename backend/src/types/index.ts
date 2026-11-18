export interface MediaItem {
  id: string;
  title: string;
  type: 'movie' | 'series';
  vj: string;
  genre: string;
  year?: string;
  rating?: string;
  duration?: string;
  description: string;
  posterUrl: string;
  backdropUrl: string;
  streamUrl: string;
  isSeries: boolean;
  isStreamAvailable?: boolean;
  seasons?: Season[];
  servers?: PlaybackServer[];
}

export interface Season {
  seasonNumber: number;
  episodes: Episode[];
}

export interface Episode {
  id?: string;
  episodeNumber: number;
  title: string;
  duration?: string;
  streamUrl: string;
  thumbnailUrl?: string;
}

export interface PlaybackServer {
  id: string;
  name: string;
  quality: string;
  streamUrl: string;
  isProxy: boolean;
}

export interface CatalogRail {
  id: string;
  title: string;
  vj?: string;
  genre?: string;
  items: MediaItem[];
}
