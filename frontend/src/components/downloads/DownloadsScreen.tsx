import React, { useState, useEffect, useCallback } from 'react';
import { 
  Download, 
  HardDrive, 
  Play, 
  Trash2, 
  CheckCircle2, 
  AlertCircle, 
  X, 
  Film, 
  Tv, 
  RefreshCw, 
  FileDown, 
  ArrowRight,
  Database,
  Radio
} from 'lucide-react';
import { offlineStorage, OfflineMediaItem, ActiveDownload } from '../../services/offlineStorage.service';

interface DownloadsScreenProps {
  onPlayOffline: (item: OfflineMediaItem) => void;
  onNavigate: (route: string) => void;
}

export const DownloadsScreen: React.FC<DownloadsScreenProps> = ({
  onPlayOffline,
  onNavigate
}) => {
  const [offlineList, setOfflineList] = useState<OfflineMediaItem[]>([]);
  const [activeDownloads, setActiveDownloads] = useState<ActiveDownload[]>([]);
  const [storage, setStorage] = useState<{ usedBytes: number; quotaBytes: number; percent: number }>({
    usedBytes: 0,
    quotaBytes: 10 * 1024 * 1024 * 1024,
    percent: 0
  });
  const [filterTab, setFilterTab] = useState<'all' | 'movies' | 'series'>('all');
  const [loading, setLoading] = useState<boolean>(true);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const showToast = useCallback((msg: string) => {
    setToastMessage(msg);
    setTimeout(() => {
      setToastMessage(prev => (prev === msg ? null : prev));
    }, 3500);
  }, []);

  const refreshOfflineData = useCallback(async () => {
    try {
      const [items, storageData] = await Promise.all([
        offlineStorage.getAllOfflineMedia(),
        offlineStorage.getStorageEstimate()
      ]);
      setOfflineList(items);
      setStorage(storageData);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshOfflineData();

    // Subscribe to active downloads state
    const unsubscribe = offlineStorage.subscribeActiveDownloads((downloads) => {
      setActiveDownloads([...downloads]);
      // If a download just finished, refresh offline media items
      if (downloads.some(d => d.status === 'completed')) {
        refreshOfflineData();
      }
    });

    return () => unsubscribe();
  }, [refreshOfflineData]);

  // Handle Export / Save to Device
  const handleExportToFile = (item: OfflineMediaItem) => {
    const cleanTitle = item.title.replace(/[<>:"/\\|?*]+/g, '').trim();
    const cleanVj = (item.vj || '').replace(/[<>:"/\\|?*]+/g, '').trim();
    const vjPart = cleanVj ? ` (${cleanVj})` : '';
    
    let filename = '';
    if (item.type === 'series' && item.seasonNumber && item.episodeNumber) {
      const sNum = String(item.seasonNumber).padStart(2, '0');
      const eNum = String(item.episodeNumber).padStart(2, '0');
      filename = `${cleanTitle} - S${sNum}E${eNum}${vjPart} - Bolt.mp4`;
    } else {
      filename = `${cleanTitle}${vjPart} - Bolt.mp4`;
    }

    offlineStorage.exportBlobToFile(item.blob, filename);
    showToast(`Saved "${filename}" to your device Downloads!`);
  };

  // Handle Delete Offline Item
  const handleDeleteItem = async (item: OfflineMediaItem) => {
    const confirmed = window.confirm(
      `Remove "${item.title}${item.episodeNumber ? ` - Ep ${item.episodeNumber}` : ''}" from offline storage? This will free up ${offlineStorage.formatBytes(item.sizeBytes)}.`
    );
    if (!confirmed) return;

    try {
      await offlineStorage.deleteOfflineMedia(item.id);
      showToast(`Removed from offline storage.`);
      await refreshOfflineData();
    } catch {
      alert('Failed to delete item from storage.');
    }
  };

  // Filter items
  const filteredItems = offlineList.filter(item => {
    if (filterTab === 'movies') return item.type === 'movie';
    if (filterTab === 'series') return item.type === 'series';
    return true;
  });

  const totalDownloadedBytes = offlineList.reduce((acc, curr) => acc + (curr.sizeBytes || 0), 0);

  return (
    <div className="min-h-screen bg-[#010318] text-white pt-2 sm:pt-4 pb-28 px-4 sm:px-8 max-w-7xl mx-auto">
      {/* Toast Notification Banner */}
      {toastMessage && (
        <div className="fixed top-16 right-4 z-50 flex items-center gap-2 px-4 py-3 bg-[#10B981] text-black font-bold rounded-xl shadow-2xl animate-in fade-in slide-in-from-top-4 duration-200">
          <CheckCircle2 className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm">{toastMessage}</span>
        </div>
      )}

      {/* Screen Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-[#132845]/60">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="p-2.5 rounded-xl bg-[#10B981]/15 border border-[#10B981]/40 text-[#10B981]">
              <Download className="w-6 h-6" />
            </div>
            <div>
              <h1 className="text-2xl sm:text-3xl font-black tracking-tight text-white flex items-center gap-2">
                Downloads & Offline Library
              </h1>
              <p className="text-xs sm:text-sm text-[#94A3B8] mt-0.5">
                Watch full-length translated movies & series offline anytime, or export directly to your device storage.
              </p>
            </div>
          </div>
        </div>

        <button
          onClick={refreshOfflineData}
          className="self-start md:self-auto flex items-center gap-2 px-3.5 py-2 rounded-lg bg-white/5 border border-white/10 hover:bg-white/10 text-xs font-semibold text-[#94A3B8] hover:text-white transition-all"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>Refresh</span>
        </button>
      </div>

      {/* Storage Quota Gauge Widget */}
      <div className="mt-6 p-4 sm:p-5 rounded-2xl bg-gradient-to-r from-[#0d1627] to-[#081220] border border-[#1b3457] shadow-xl">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3">
          <div className="flex items-center gap-2.5">
            <Database className="w-5 h-5 text-[#10B981]" />
            <div>
              <div className="text-sm font-bold text-white flex items-center gap-2">
                Local Device Storage Usage
                <span className="px-2 py-0.5 text-[10px] font-black uppercase rounded bg-[#10B981]/20 text-[#10B981]">
                  IndexedDB
                </span>
              </div>
              <div className="text-xs text-[#94A3B8]">
                Munowatch Offline Media: <span className="font-semibold text-white">{offlineStorage.formatBytes(totalDownloadedBytes)}</span> ({offlineList.length} {offlineList.length === 1 ? 'title' : 'titles'} saved)
              </div>
            </div>
          </div>

          <div className="text-xs text-[#94A3B8] sm:text-right">
            <span>Browser Quota: </span>
            <span className="font-bold text-white">{offlineStorage.formatBytes(storage.usedBytes)}</span>
            <span> / {offlineStorage.formatBytes(storage.quotaBytes)}</span>
            <span className="ml-1 text-[11px] font-mono text-[#10B981]">({storage.percent}% used)</span>
          </div>
        </div>

        {/* Progress bar */}
        <div className="w-full h-2.5 rounded-full bg-[#132845] overflow-hidden p-0.5">
          <div 
            className="h-full rounded-full bg-gradient-to-r from-[#10B981] to-emerald-400 transition-all duration-500 shadow-sm shadow-[#10B981]/50"
            style={{ width: `${Math.max(2, Math.min(100, storage.percent))}%` }}
          />
        </div>
      </div>

      {/* Active Downloads In-Flight Section */}
      {activeDownloads.length > 0 && (
        <div className="mt-8">
          <div className="flex items-center gap-2 mb-3">
            <Radio className="w-4 h-4 text-[#10B981] animate-pulse" />
            <h2 className="text-lg font-bold text-white">Downloading Now ({activeDownloads.length})</h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
            {activeDownloads.map((dl) => (
              <div
                key={dl.id}
                className="flex items-center gap-3.5 p-3.5 rounded-xl bg-[#091526] border border-[#1b3457] shadow-lg relative overflow-hidden"
              >
                {/* Background progress tint */}
                <div 
                  className="absolute left-0 top-0 bottom-0 bg-[#10B981]/10 pointer-events-none transition-all duration-300"
                  style={{ width: `${dl.progress}%` }}
                />

                {/* Poster Thumbnail */}
                <div className="w-14 h-20 rounded-lg overflow-hidden bg-[#132845] flex-shrink-0 relative shadow">
                  {dl.poster ? (
                    <img 
                      src={dl.poster} 
                      alt={dl.title} 
                      className="w-full h-full object-cover" 
                      loading="lazy" 
                    />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center text-[#94A3B8]">
                      <Film className="w-6 h-6" />
                    </div>
                  )}
                  {dl.vj && (
                    <span className="absolute bottom-1 left-1 right-1 text-center bg-black/85 text-[#10B981] text-[8px] font-black rounded px-1 py-0.5 uppercase truncate">
                      {dl.vj}
                    </span>
                  )}
                </div>

                {/* Progress details */}
                <div className="flex-1 min-w-0 pr-2">
                  <div className="flex items-center justify-between gap-2">
                    <h3 className="font-bold text-sm text-white truncate">{dl.title}</h3>
                    <span className="text-xs font-mono font-bold text-[#10B981]">
                      {dl.progress}%
                    </span>
                  </div>

                  {dl.seasonNumber && dl.episodeNumber && (
                    <div className="text-[11px] text-amber-400 font-medium">
                      Season {dl.seasonNumber} • Episode {dl.episodeNumber}
                    </div>
                  )}

                  {/* Progress Bar */}
                  <div className="w-full h-1.5 rounded-full bg-[#132845] overflow-hidden my-2">
                    <div
                      className="h-full rounded-full bg-[#10B981] transition-all duration-300"
                      style={{ width: `${dl.progress}%` }}
                    />
                  </div>

                  <div className="flex items-center justify-between text-[11px] text-[#94A3B8]">
                    <span>
                      {offlineStorage.formatBytes(dl.loadedBytes)}
                      {dl.totalBytes > 0 && ` / ${offlineStorage.formatBytes(dl.totalBytes)}`}
                    </span>
                    <span className="font-mono text-emerald-400">
                      {offlineStorage.formatSpeed(dl.speedBps)}
                    </span>
                  </div>

                  {dl.status === 'error' && (
                    <div className="flex items-center gap-1 text-[11px] text-red-400 mt-1">
                      <AlertCircle className="w-3.5 h-3.5" />
                      <span className="truncate">{dl.error || 'Download failed'}</span>
                    </div>
                  )}
                </div>

                {/* Cancel Button */}
                <button
                  onClick={() => offlineStorage.cancelDownload(dl.id)}
                  className="p-2 rounded-lg bg-white/5 hover:bg-red-500/20 text-[#94A3B8] hover:text-red-400 transition-all flex-shrink-0"
                  title="Cancel download"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Offline Media Content Hub */}
      <div className="mt-8">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4">
          <div className="flex items-center gap-2">
            <h2 className="text-xl font-bold text-white">
              Saved Movies & Series ({filteredItems.length})
            </h2>
          </div>

          {/* Filter Tabs */}
          {offlineList.length > 0 && (
            <div className="flex items-center gap-1 p-1 rounded-xl bg-[#0d1627] border border-[#1b3457] self-start sm:self-auto">
              {(['all', 'movies', 'series'] as const).map(tab => (
                <button
                  key={tab}
                  onClick={() => setFilterTab(tab)}
                  className={`px-3 py-1 rounded-lg text-xs font-semibold capitalize transition-all ${
                    filterTab === tab
                      ? 'bg-[#10B981] text-black font-extrabold shadow'
                      : 'text-[#94A3B8] hover:text-white'
                  }`}
                >
                  {tab}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Empty State */}
        {filteredItems.length === 0 && !loading && (
          <div className="text-center py-16 px-4 rounded-2xl bg-gradient-to-b from-[#091526]/50 to-transparent border border-[#1b3457]/40 my-6">
            <div className="w-16 h-16 rounded-2xl bg-[#10B981]/10 border border-[#10B981]/30 flex items-center justify-center mx-auto mb-4 text-[#10B981]">
              <HardDrive className="w-8 h-8" />
            </div>
            <h3 className="text-lg font-bold text-white">No Offline Titles Saved Yet</h3>
            <p className="text-xs sm:text-sm text-[#94A3B8] max-w-md mx-auto mt-1 mb-6">
              Download your favorite Ugandan translated movies and series episodes to watch uninterrupted anywhere, even with zero internet data.
            </p>
            <div className="flex flex-wrap items-center justify-center gap-3">
              <button
                onClick={() => onNavigate('movies')}
                className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-[#10B981] text-black font-bold text-xs hover:bg-[#10B981]/90 shadow-lg shadow-[#10B981]/20 transition-all"
              >
                <Film className="w-4 h-4" />
                <span>Explore Movies</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </button>
              <button
                onClick={() => onNavigate('series')}
                className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 hover:bg-white/10 text-white font-semibold text-xs transition-all"
              >
                <Tv className="w-4 h-4" />
                <span>Browse Series</span>
              </button>
            </div>
          </div>
        )}

        {/* Media Grid */}
        {filteredItems.length > 0 && (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
            {filteredItems.map((item) => (
              <div
                key={item.id}
                className="group flex flex-col rounded-xl overflow-hidden bg-[#091526] border border-[#1b3457] hover:border-[#10B981]/60 shadow-lg transition-all"
              >
                {/* Poster Cover */}
                <div className="relative aspect-[2/3] w-full bg-[#132845] overflow-hidden">
                  {item.poster ? (
                    <img
                      src={item.poster}
                      alt={item.title}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                      loading="lazy"
                    />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center text-[#94A3B8]">
                      {item.type === 'series' ? <Tv className="w-10 h-10" /> : <Film className="w-10 h-10" />}
                    </div>
                  )}

                  {/* VJ Tag */}
                  {item.vj && (
                    <div className="absolute top-2 left-2 z-10">
                      <span className="px-2 py-0.5 rounded bg-black/85 backdrop-blur-sm text-[10px] font-black uppercase text-[#10B981] border border-[#10B981]/40">
                        {item.vj}
                      </span>
                    </div>
                  )}

                  {/* Offline Ready Badge */}
                  <div className="absolute top-2 right-2 z-10">
                    <span className="flex items-center gap-1 px-1.5 py-0.5 rounded bg-[#10B981] text-black text-[9px] font-black uppercase shadow">
                      <CheckCircle2 className="w-3 h-3" />
                      <span>Ready</span>
                    </span>
                  </div>

                  {/* Play Overlay */}
                  <div 
                    onClick={() => onPlayOffline(item)}
                    className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity cursor-pointer"
                  >
                    <div className="w-12 h-12 rounded-full bg-[#10B981] text-black flex items-center justify-center shadow-2xl transform group-hover:scale-110 transition-transform">
                      <Play className="w-6 h-6 fill-black ml-0.5" />
                    </div>
                  </div>
                </div>

                {/* Content Meta */}
                <div className="p-3 flex-1 flex flex-col justify-between">
                  <div>
                    <h3 className="font-bold text-xs sm:text-sm text-white line-clamp-1 group-hover:text-[#10B981] transition-colors" title={item.title}>
                      {item.title}
                    </h3>

                    {item.type === 'series' && item.seasonNumber && item.episodeNumber && (
                      <div className="text-[11px] font-semibold text-amber-400 mt-0.5">
                        Season {item.seasonNumber} • Episode {item.episodeNumber}
                        {item.episodeTitle && <span className="text-[#94A3B8] font-normal truncate block">{item.episodeTitle}</span>}
                      </div>
                    )}

                    <div className="flex items-center gap-2 text-[10px] text-[#94A3B8] mt-1">
                      <span className="font-medium">{item.year || '2024'}</span>
                      <span>•</span>
                      <span className="text-[#10B981] font-semibold">{offlineStorage.formatBytes(item.sizeBytes)}</span>
                    </div>
                  </div>

                  {/* Action Buttons */}
                  <div className="flex items-center gap-1.5 mt-3 pt-2.5 border-t border-white/5">
                    {/* Instant Offline Play */}
                    <button
                      onClick={() => onPlayOffline(item)}
                      className="flex-1 flex items-center justify-center gap-1 py-1.5 px-2 rounded-lg bg-[#10B981] hover:bg-[#10B981]/90 text-black font-black text-[11px] transition-all shadow-sm shadow-[#10B981]/20 active:scale-95"
                      title="Play movie offline"
                    >
                      <Play className="w-3 h-3 fill-black" />
                      <span>Watch</span>
                    </button>

                    {/* Export to Local Disk (.mp4) */}
                    <button
                      onClick={() => handleExportToFile(item)}
                      className="p-1.5 rounded-lg bg-white/5 hover:bg-white/15 text-[#94A3B8] hover:text-white transition-all active:scale-95"
                      title="Save MP4 directly to your device storage"
                    >
                      <FileDown className="w-3.5 h-3.5" />
                    </button>

                    {/* Delete from Storage */}
                    <button
                      onClick={() => handleDeleteItem(item)}
                      className="p-1.5 rounded-lg bg-white/5 hover:bg-red-500/20 text-[#94A3B8] hover:text-red-400 transition-all active:scale-95"
                      title="Delete from offline storage"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
