import React, { useState, useEffect, useCallback } from 'react';
import { MediaItem } from '../../types';
import { TvMediaCard } from '../common/TvMediaCard';
import { Tv, Loader2, ArrowDownCircle } from 'lucide-react';
import { api, isTrailerOrIntroUrl } from '../../services/api';

const isTrailerOnly = (item: MediaItem) => {
  if (!item.streamUrl) return false;
  return isTrailerOrIntroUrl(item.streamUrl, item.title) && !item.id.startsWith('tk_') && !item.streamUrl.includes('preview.php') && !item.streamUrl.includes('twolekede');
};

const filterByVj = (items: MediaItem[], vj: string | null): MediaItem[] => {
  if (!vj) return items;
  const cleanTarget = vj.toLowerCase().replace('vj-', '').replace('vj', '').replace('.', '').trim();
  return items.filter(item => {
    const cleanCard = (item.vj || '').toLowerCase().replace('vj-', '').replace('vj', '').replace('.', '').trim();
    return cleanCard.includes(cleanTarget) || cleanTarget.includes(cleanCard);
  });
};

interface SeriesScreenProps {
  series: MediaItem[];
  onSeriesClick: (item: MediaItem) => void;
}

const SERIES_VJS = [
  'VJ Junior', 'VJ Emmy', 'VJ Jingo', 'VJ Ice P', 'VJ Ulio', 'VJ Kriss Sweet', 'VJ HD'
];

export const SeriesScreen: React.FC<SeriesScreenProps> = ({ series: initialSeries, onSeriesClick }) => {
  const [seriesList, setSeriesList] = useState<MediaItem[]>(initialSeries || []);
  const [page, setPage] = useState<number>(1);
  const [loading, setLoading] = useState<boolean>(false);
  const [loadingMore, setLoadingMore] = useState<boolean>(false);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [selectedVj, setSelectedVj] = useState<string | null>(null);

  // Sync initial series if loaded asynchronously
  useEffect(() => {
    if (selectedVj === null && initialSeries && initialSeries.length > 0 && seriesList.length === 0) {
      setSeriesList(initialSeries);
    }
  }, [initialSeries, selectedVj, seriesList.length]);

  const handleSelectVj = (vj: string | null) => {
    setSelectedVj(vj);
    // Instant client-side filter to completely prevent cross-VJ flashing
    if (vj) {
      setSeriesList(prev => filterByVj(prev, vj));
    }
  };

  const fetchSeriesPageOne = useCallback(async () => {
    setLoading(true);
    setPage(1);
    try {
      const res = await api.getSeries({
        page: 1,
        perPage: 24,
        vj: selectedVj || undefined
      });
      const cleanItems = (res.items || []).filter(i => !isTrailerOnly(i));
      const filtered = filterByVj(cleanItems, selectedVj);
      setSeriesList(filtered);
      setHasMore(filtered.length >= 10);
    } catch {
      // Keep existing
    } finally {
      setLoading(false);
    }
  }, [selectedVj]);

  useEffect(() => {
    fetchSeriesPageOne();
  }, [fetchSeriesPageOne]);

  const handleLoadMore = async () => {
    if (loadingMore || !hasMore) return;
    setLoadingMore(true);
    const nextPage = page + 1;
    try {
      const res = await api.getSeries({
        page: nextPage,
        perPage: 24,
        vj: selectedVj || undefined
      });
      if (res.items && res.items.length > 0) {
        const cleanItems = res.items.filter(i => !isTrailerOnly(i));
        const filtered = filterByVj(cleanItems, selectedVj);
        setSeriesList(prev => {
          const ids = new Set(prev.map(p => p.id));
          return [...prev, ...filtered.filter(i => !ids.has(i.id))];
        });
        setPage(nextPage);
        setHasMore(filtered.length >= 10);
      } else {
        setHasMore(false);
      }
    } catch {
      setHasMore(false);
    } finally {
      setLoadingMore(false);
    }
  };

  return (
    <div className="p-4 sm:p-9 max-w-7xl mx-auto space-y-6 animate-fadeIn">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-[#132845] pb-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-[#10B981]/15 border border-[#10B981]/40 flex items-center justify-center">
            <Tv className="w-5 h-5 text-[#10B981]" />
          </div>
          <div>
            <h1 className="text-xl sm:text-2xl font-black text-white">Ugandan Translated Series</h1>
            <p className="text-xs text-[#94A3B8]">Full multi-season series with Veejay voiceovers</p>
          </div>
        </div>

        <span className="text-xs font-bold text-[#94A3B8]">
          <strong className="text-white">{seriesList.length}</strong> Series Loaded
        </span>
      </div>

      {/* VJ Filter Pills */}
      <div className="flex items-center gap-2 overflow-x-auto no-scrollbar py-1">
        <button
          onClick={() => handleSelectVj(null)}
          className={`px-3 py-1.5 rounded-lg text-xs font-bold whitespace-nowrap transition-all ${
            selectedVj === null
              ? 'bg-[#10B981] text-black shadow-md'
              : 'bg-[#0A192F] text-[#94A3B8] hover:text-white border border-[#1E293B]'
          }`}
        >
          All Series
        </button>
        {SERIES_VJS.map(vj => (
          <button
            key={vj}
            onClick={() => handleSelectVj(selectedVj === vj ? null : vj)}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold whitespace-nowrap transition-all ${
              selectedVj === vj
                ? 'bg-[#10B981] text-black shadow-md'
                : 'bg-[#0A192F] text-[#94A3B8] hover:text-white border border-[#1E293B]'
            }`}
          >
            {vj}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="flex flex-col items-center justify-center py-20 space-y-3">
          <Loader2 className="w-10 h-10 text-[#10B981] animate-spin" />
          <p className="text-sm font-semibold text-[#94A3B8]">Loading Ugandan translated series...</p>
        </div>
      ) : (
        <>
          {/* Grid */}
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3 sm:gap-4 pt-2">
            {seriesList.map((item) => (
              <TvMediaCard
                key={item.id}
                item={item}
                onClick={() => onSeriesClick(item)}
                cardWidth="w-full"
              />
            ))}
          </div>

          {hasMore && (
            <div className="flex justify-center pt-8 pb-4">
              <button
                onClick={handleLoadMore}
                disabled={loadingMore}
                className="flex items-center gap-2 px-6 py-3 bg-[#0A192F] hover:bg-[#132845] border border-[#10B981]/60 hover:border-[#10B981] text-white hover:text-[#34D399] font-bold text-sm rounded-xl shadow-lg transition-all active:scale-95 disabled:opacity-50"
              >
                {loadingMore ? (
                  <>
                    <Loader2 className="w-4 h-4 text-[#10B981] animate-spin" />
                    <span>Loading Next Series...</span>
                  </>
                ) : (
                  <>
                    <ArrowDownCircle className="w-4 h-4 text-[#10B981]" />
                    <span>Load More Series (Page {page + 1})</span>
                  </>
                )}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
};
