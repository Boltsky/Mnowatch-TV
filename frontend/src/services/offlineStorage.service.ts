import { MediaItem, Episode } from '../types';
import { api } from './api';

export interface OfflineMediaItem {
  id: string; // Unique identifier: item.id for movies, or `${item.id}_s${season}_e${episode}` for series episodes
  mediaId: string;
  title: string;
  vj?: string;
  poster?: string;
  year?: string;
  genres?: string[];
  type: 'movie' | 'series';
  seasonNumber?: number;
  episodeNumber?: number;
  episodeTitle?: string;
  sizeBytes: number;
  downloadedAt: number;
  blob: Blob;
  duration?: string;
}

export interface ActiveDownload {
  id: string;
  mediaId: string;
  title: string;
  vj?: string;
  poster?: string;
  type: 'movie' | 'series';
  seasonNumber?: number;
  episodeNumber?: number;
  episodeTitle?: string;
  progress: number; // 0 - 100
  loadedBytes: number;
  totalBytes: number;
  speedBps: number;
  status: 'downloading' | 'completed' | 'error' | 'cancelled';
  error?: string;
  abortController: AbortController;
  startTime: number;
}

const DB_NAME = 'MunowatchOfflineDB';
const DB_VERSION = 1;
const STORE_NAME = 'offline_media';

let dbPromise: Promise<IDBDatabase> | null = null;

function getDB(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;

  dbPromise = new Promise((resolve, reject) => {
    if (typeof window === 'undefined' || !window.indexedDB) {
      reject(new Error('IndexedDB is not supported in this browser.'));
      return;
    }

    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = (event: IDBVersionChangeEvent) => {
      const db = (event.target as IDBOpenDBRequest).result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, { keyPath: 'id' });
        store.createIndex('mediaId', 'mediaId', { unique: false });
        store.createIndex('type', 'type', { unique: false });
        store.createIndex('downloadedAt', 'downloadedAt', { unique: false });
      }
    };

    request.onsuccess = () => {
      resolve(request.result);
    };

    request.onerror = () => {
      dbPromise = null;
      reject(request.error || new Error('Failed to open offline database'));
    };
  });

  return dbPromise;
}

// Active downloads state & subscription hub
const activeDownloads = new Map<string, ActiveDownload>();
type DownloadListener = (downloads: ActiveDownload[]) => void;
const listeners = new Set<DownloadListener>();

function notifyListeners() {
  const list = Array.from(activeDownloads.values());
  listeners.forEach(cb => {
    try {
      cb(list);
    } catch {}
  });
}

export const offlineStorage = {
  subscribeActiveDownloads(cb: DownloadListener): () => void {
    listeners.add(cb);
    cb(Array.from(activeDownloads.values()));
    return () => listeners.delete(cb);
  },

  getActiveDownloads(): ActiveDownload[] {
    return Array.from(activeDownloads.values());
  },

  async getAllOfflineMedia(): Promise<OfflineMediaItem[]> {
    try {
      const db = await getDB();
      return new Promise((resolve, reject) => {
        const tx = db.transaction(STORE_NAME, 'readonly');
        const store = tx.objectStore(STORE_NAME);
        const req = store.getAll();
        req.onsuccess = () => {
          const items: OfflineMediaItem[] = req.result || [];
          items.sort((a, b) => b.downloadedAt - a.downloadedAt);
          resolve(items);
        };
        req.onerror = () => reject(req.error);
      });
    } catch (e) {
      console.warn('Failed to get offline media:', e);
      return [];
    }
  },

  async getOfflineMedia(id: string): Promise<OfflineMediaItem | undefined> {
    try {
      const db = await getDB();
      return new Promise((resolve, reject) => {
        const tx = db.transaction(STORE_NAME, 'readonly');
        const store = tx.objectStore(STORE_NAME);
        const req = store.get(id);
        req.onsuccess = () => resolve(req.result || undefined);
        req.onerror = () => reject(req.error);
      });
    } catch {
      return undefined;
    }
  },

  async isMediaDownloaded(id: string): Promise<boolean> {
    const item = await this.getOfflineMedia(id);
    return !!item;
  },

  async saveOfflineMedia(item: OfflineMediaItem): Promise<void> {
    const db = await getDB();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readwrite');
      const store = tx.objectStore(STORE_NAME);
      const req = store.put(item);
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  },

  async deleteOfflineMedia(id: string): Promise<void> {
    const db = await getDB();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readwrite');
      const store = tx.objectStore(STORE_NAME);
      const req = store.delete(id);
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  },

  async getStorageEstimate(): Promise<{ usedBytes: number; quotaBytes: number; percent: number }> {
    try {
      if (typeof navigator !== 'undefined' && navigator.storage && navigator.storage.estimate) {
        const estimate = await navigator.storage.estimate();
        const used = estimate.usage || 0;
        const quota = estimate.quota || (10 * 1024 * 1024 * 1024); // Fallback 10GB
        const percent = quota > 0 ? Math.round((used / quota) * 100) : 0;
        return { usedBytes: used, quotaBytes: quota, percent };
      }
    } catch {}
    return { usedBytes: 0, quotaBytes: 10 * 1024 * 1024 * 1024, percent: 0 };
  },

  /**
   * Starts downloading full media stream into browser storage with real-time progress & speed
   */
  async startOfflineDownload(params: {
    item: MediaItem;
    episode?: Episode;
    streamUrl?: string;
  }): Promise<void> {
    const { item, episode, streamUrl: initialUrl } = params;
    const downloadId = episode
      ? `${item.id}_s${episode.seasonNumber || 1}_e${episode.episodeNumber}`
      : item.id;

    if (activeDownloads.has(downloadId)) {
      console.warn('Download already in progress for:', downloadId);
      return;
    }

    const alreadyStored = await this.isMediaDownloaded(downloadId);
    if (alreadyStored) {
      alert(`"${item.title}${episode ? ` - Episode ${episode.episodeNumber}` : ''}" is already saved for offline viewing!`);
      return;
    }

    const abortController = new AbortController();
    const activeItem: ActiveDownload = {
      id: downloadId,
      mediaId: item.id,
      title: item.title,
      vj: item.vj,
      poster: item.posterUrl || item.backdropUrl,
      type: item.type === 'series' ? 'series' : 'movie',
      seasonNumber: episode?.seasonNumber,
      episodeNumber: episode?.episodeNumber,
      episodeTitle: episode?.title,
      progress: 0,
      loadedBytes: 0,
      totalBytes: 0,
      speedBps: 0,
      status: 'downloading',
      abortController,
      startTime: Date.now()
    };

    activeDownloads.set(downloadId, activeItem);
    notifyListeners();

    try {
      // 1. Resolve media stream URL if not provided directly
      let directUrl = initialUrl || '';
      if (directUrl.includes('/stream/proxy') || directUrl.includes('preview.php')) {
        directUrl = '';
      }
      if (!directUrl) {
        const targetId = episode?.id || item.id;
        const res = await api.resolveStream(targetId, item.title, item.vj);
        if (res.directUrl && !res.directUrl.includes('preview.php')) {
          directUrl = res.directUrl;
        } else if (res.streamUrl) {
          directUrl = res.streamUrl;
        }
      }

      if (!directUrl) {
        throw new Error('Could not resolve playable media stream for download.');
      }

      // Always route through high-speed CORS safe proxy to guarantee chunked streaming & total content-length
      const fetchUrl = directUrl.includes('/stream/proxy?url=') ? directUrl : api.getProxyStreamUrl(directUrl);

      const response = await fetch(fetchUrl, {
        signal: abortController.signal,
        headers: {
          'Accept': '*/*'
        }
      });

      if (!response.ok) {
        throw new Error(`Media server responded with HTTP ${response.status}`);
      }

      const contentLengthHeader = response.headers.get('content-length');
      let totalBytes = contentLengthHeader ? parseInt(contentLengthHeader, 10) : 0;
      if (!totalBytes) {
        const contentRangeHeader = response.headers.get('content-range');
        if (contentRangeHeader) {
          const m = contentRangeHeader.match(/\/(\d+)/);
          if (m) totalBytes = parseInt(m[1], 10);
        }
      }
      activeItem.totalBytes = totalBytes;

      if (!response.body) {
        throw new Error('ReadableStream not supported by response body.');
      }

      const reader = response.body.getReader();
      const chunks: Uint8Array[] = [];
      let loadedBytes = 0;
      let lastNotifyTime = Date.now();
      let lastLoadedForSpeed = 0;
      let speedCalculationTime = Date.now();

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        if (value) {
          chunks.push(value);
          loadedBytes += value.byteLength;
          activeItem.loadedBytes = loadedBytes;

          const now = Date.now();
          // Calculate download speed every 500ms
          if (now - speedCalculationTime >= 500) {
            const timeDiffSec = (now - speedCalculationTime) / 1000;
            const bytesDiff = loadedBytes - lastLoadedForSpeed;
            activeItem.speedBps = Math.round(bytesDiff / timeDiffSec);
            speedCalculationTime = now;
            lastLoadedForSpeed = loadedBytes;
          }

          if (totalBytes > 0) {
            activeItem.progress = Math.min(99, Math.round((loadedBytes / totalBytes) * 100));
          } else {
            // Indeterminate progress representation
            activeItem.progress = Math.min(95, Math.round((loadedBytes / (100 * 1024 * 1024)) * 50));
          }

          // Throttle UI listener updates to every 150ms to keep UI 60fps
          if (now - lastNotifyTime > 150) {
            lastNotifyTime = now;
            notifyListeners();
          }
        }
      }

      // 2. Wrap chunks into binary MP4 Blob
      const mediaBlob = new Blob(chunks as any, { type: 'video/mp4' });

      // 3. Persist in IndexedDB
      const offlineRecord: OfflineMediaItem = {
        id: downloadId,
        mediaId: item.id,
        title: item.title,
        vj: item.vj,
        poster: item.posterUrl || item.backdropUrl,
        year: item.year,
        genres: item.genre ? item.genre.split(/[\/,]/).map(g => g.trim()).filter(Boolean) : undefined,
        type: item.type === 'series' ? 'series' : 'movie',
        seasonNumber: episode?.seasonNumber,
        episodeNumber: episode?.episodeNumber,
        episodeTitle: episode?.title,
        sizeBytes: mediaBlob.size,
        downloadedAt: Date.now(),
        blob: mediaBlob,
        duration: item.duration
      };

      await this.saveOfflineMedia(offlineRecord);

      activeItem.progress = 100;
      activeItem.status = 'completed';
      notifyListeners();

      // Clean up active downloads list after completion toast window
      setTimeout(() => {
        activeDownloads.delete(downloadId);
        notifyListeners();
      }, 2500);

    } catch (err: any) {
      if (err.name === 'AbortError') {
        activeItem.status = 'cancelled';
        activeItem.error = 'Download cancelled by user';
      } else {
        activeItem.status = 'error';
        activeItem.error = err.message || 'Download failed';
      }
      notifyListeners();

      setTimeout(() => {
        activeDownloads.delete(downloadId);
        notifyListeners();
      }, 4000);
    }
  },

  cancelDownload(id: string): void {
    const active = activeDownloads.get(id);
    if (active) {
      active.abortController.abort();
      active.status = 'cancelled';
      notifyListeners();
      setTimeout(() => {
        activeDownloads.delete(id);
        notifyListeners();
      }, 1000);
    }
  },

  exportBlobToFile(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(url), 10000);
  },

  createPlaybackUrl(blob: Blob): string {
    return URL.createObjectURL(blob);
  },

  formatBytes(bytes: number, decimals: number = 1): string {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${sizes[i]}`;
  },

  formatSpeed(speedBps: number): string {
    if (!speedBps || speedBps <= 0) return '0 KB/s';
    if (speedBps > 1024 * 1024) {
      return `${(speedBps / (1024 * 1024)).toFixed(1)} MB/s`;
    }
    return `${Math.round(speedBps / 1024)} KB/s`;
  }
};
