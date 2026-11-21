import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { MediaItem, VjItem } from '../../types';
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
  if (!cleanTarget) return items;
  return items.filter(item => {
    const cleanCard = (item.vj || '').toLowerCase().replace('vj-', '').replace('vj', '').replace('.', '').trim();
    return cleanCard.includes(cleanTarget);
  });
};

interface SeriesScreenProps {
  series: MediaItem[];
  vjs?: VjItem[];
  onSeriesClick: (item: MediaItem) => void;
  initialVj?: string;
}

const SERIES_VJS = [
  'VJ Junior', 'VJ Emmy', 'VJ ICEP', 'VJ Jingo', 'VJ Ulio',
  'VJ Soul', 'VJ Banks', 'VJ Sammy', 'VJ Freddie', 'VJ Waza',
  'VJ Eddy', 'VJ Shield', 'VJ HD', 'MK Kisuule', 'Shao Khan'
];

export const SeriesScreen: React.FC<SeriesScreenProps> = ({ series: initialSeries, vjs, onSeriesClick, initialVj }) => {
  const [seriesList, setSeriesList] = useState<MediaItem[]>(initialSeries || []);
  const [page, setPage] = useState<number>(1);
  const [loading, setLoading] = useState<boolean>(false);
  const [loadingMore, setLoadingMore] = useState<boolean>(false);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [selectedVj, setSelectedVj] = useState<string | null>(initialVj || null);

  const availableVjs = useMemo(() => {
    if (vjs && vjs.length > 0) {
      return vjs.map(v => v.name);
    }
    return SERIES_VJS;
  }, [vjs]);

  // Request sequence counter to eliminate race conditions
  const reqSeqRef = useRef<number>(0);

  // Sync initial series if loaded asynchronously (only when no VJ filter is selected)
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
    const currentSeq = ++reqSeqRef.current;
    setLoading(true);
    setPage(1);
    try {
      const res = await api.getSeries({
        page: 1,
        perPage: 24,
        vj: selectedVj || undefined
      });
      if (reqSeqRef.current !== currentSeq) return; // Stale request discarded

      const cleanItems = (res.items || []).filter(i => !isTrailerOnly(i));
      const filtered = filterByVj(cleanItems, selectedVj);
      setSeriesList(filtered);
      setHasMore(filtered.length >= 10);
    } catch {
      if (reqSeqRef.current === currentSeq) {
        // Enforce strict VJ filter even on fallback
        setSeriesList(prev => filterByVj(prev, selectedVj));
      }
    } finally {
      if (reqSeqRef.current === currentSeq) {
        setLoading(false);
      }
    }
  }, [selectedVj]);

  useEffect(() => {
    fetchSeriesPageOne();
  }, [fetchSeriesPageOne]);

  const handleLoadMore = useCallback(async () => {
    if (loadingMore || !hasMore) return;
    setLoadingMore(true);
    const nextPage = page + 1;
    const currentSeq = reqSeqRef.current;
    try {
      const res = await api.getSeries({
        page: nextPage,
        perPage: 24,
        vj: selectedVj || undefined
      });
      if (reqSeqRef.current !== currentSeq) return;

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
  }, [loadingMore, hasMore, page, selectedVj]);

  // Automated Infinite Scroll Sentinel (zero manual clicks)
  const sentinelRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!hasMore || loadingMore || loading) return;
    const sentinel = sentinelRef.current;
    if (!sentinel) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          handleLoadMore();
        }
      },
      { rootMargin: '450px' }
    );

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMore, loadingMore, loading, handleLoadMore]);

  // Guarantee that displayed cards strictly match selected VJ with zero cross-contamination
  const displayedSeries = useMemo(() => {
    return filterByVj(seriesList, selectedVj);
  }, [seriesList, selectedVj]);

  return (
    <div className="p-4 sm:p-8 md:p-10 w-full max-w-[2400px] mx-auto space-y-6 animate-fadeIn">
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
          <strong className="text-white">{displayedSeries.length}</strong> Series Loaded
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
        {availableVjs.map(vj => (
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
          {/* Responsive Expanded Grid (up to 10 columns on ultrawide) */}
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 xl:grid-cols-7 2xl:grid-cols-8 3xl:grid-cols-10 gap-3 sm:gap-4 pt-2">
            {displayedSeries.map((item) => (
              <TvMediaCard
                key={item.id}
                item={item}
                onClick={() => onSeriesClick(item)}
                cardWidth="w-full"
              />
            ))}
          </div>

          {/* Automated infinite scroll sentinel */}
          {hasMore && (
            <div ref={sentinelRef} className="flex justify-center py-8">
              {loadingMore && (
                <div className="flex items-center gap-2 px-6 py-3 rounded-full bg-[#0A192F]/80 text-[#10B981] text-xs font-bold border border-[#10B981]/30">
                  <Loader2 className="w-4 h-4 animate-spin text-[#10B981]" />
                  <span>Loading more series...</span>
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
};
