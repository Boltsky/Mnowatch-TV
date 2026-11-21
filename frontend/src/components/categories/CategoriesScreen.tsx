import React, { useState, useMemo, useEffect, useCallback, useRef } from 'react';
import { MediaItem, VjItem } from '../../types';
import { TvMediaCard } from '../common/TvMediaCard';
import { Clapperboard, Layers, Loader2, ArrowDownCircle, ChevronLeft, ChevronRight } from 'lucide-react';
import { api, isTrailerOrIntroUrl } from '../../services/api';

const isTrailerOnly = (item: MediaItem) => {
  if (!item.streamUrl) return false;
  return isTrailerOrIntroUrl(item.streamUrl, item.title) && !item.id.startsWith('tk_') && !item.streamUrl.includes('preview.php') && !item.streamUrl.includes('twolekede');
};

interface CategoriesScreenProps {
  movies: MediaItem[];
  vjs: VjItem[];
  onItemClick: (item: MediaItem) => void;
  initialVj?: string;
}

export const CategoriesScreen: React.FC<CategoriesScreenProps> = ({
  movies,
  vjs,
  onItemClick,
  initialVj
}) => {
  const [activeTab, setActiveTab] = useState<'VJ' | 'GENRE'>('VJ');
  const [selectedVj, setSelectedVj] = useState<string>(initialVj || vjs[0]?.name || 'VJ Junior');
  const [selectedGenre, setSelectedGenre] = useState<string>('Action');
  const [liveItems, setLiveItems] = useState<MediaItem[]>([]);
  const [page, setPage] = useState<number>(1);
  const [loading, setLoading] = useState<boolean>(false);
  const [loadingMore, setLoadingMore] = useState<boolean>(false);
  const [hasMore, setHasMore] = useState<boolean>(true);

  const [totalTitles, setTotalTitles] = useState<number>(0);
  const reqSeqRef = useRef<number>(0);
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  const scrollHorizontally = (offset: number) => {
    if (scrollContainerRef.current) {
      scrollContainerRef.current.scrollBy({ left: offset, behavior: 'smooth' });
    }
  };

  const genres = [
    'Action', 'Sci-Fi', 'Adventure', 'Thriller', 'Horror', 'Crime', 'Comedy', 'Drama', 'Animation', 'Korean', 'Indian Movie', 'Kinigeria'
  ];

  const fetchCategoryPageOne = useCallback(async () => {
    const currentSeq = ++reqSeqRef.current;
    setLoading(true);
    setPage(1);
    try {
      if (activeTab === 'VJ') {
        const res = await api.getMovies({ page: 1, perPage: 30, vj: selectedVj });
        if (reqSeqRef.current !== currentSeq) return;
        const cleanItems = (res.items || []).filter(i => !isTrailerOnly(i));
        if (cleanItems.length > 0) {
          setLiveItems(cleanItems);
          setTotalTitles(res.total || cleanItems.length);
          setHasMore(res.hasMore ?? (cleanItems.length >= 25));
        } else {
          setLiveItems([]);
          setHasMore(false);
        }
      } else {
        const res = await api.getMovies({ page: 1, perPage: 30, genre: selectedGenre });
        if (reqSeqRef.current !== currentSeq) return;
        const cleanItems = (res.items || []).filter(i => !isTrailerOnly(i));
        if (cleanItems.length > 0) {
          setLiveItems(cleanItems);
          setTotalTitles(res.total || cleanItems.length);
          setHasMore(res.hasMore ?? (cleanItems.length >= 15));
        } else {
          setLiveItems([]);
          setHasMore(false);
        }
      }
    } catch {
      // Keep existing
    } finally {
      if (reqSeqRef.current === currentSeq) {
        setLoading(false);
      }
    }
  }, [activeTab, selectedVj, selectedGenre]);

  useEffect(() => {
    fetchCategoryPageOne();
  }, [fetchCategoryPageOne]);

  const handleLoadMore = useCallback(async () => {
    if (loadingMore || !hasMore) return;
    setLoadingMore(true);
    const nextPage = page + 1;
    const currentSeq = reqSeqRef.current;
    try {
      const res = activeTab === 'VJ'
        ? await api.getMovies({ page: nextPage, perPage: 30, vj: selectedVj })
        : await api.getMovies({ page: nextPage, perPage: 30, genre: selectedGenre });

      if (reqSeqRef.current !== currentSeq) return;

      if (res.items && res.items.length > 0) {
        const cleanItems = res.items.filter(i => !isTrailerOnly(i));
        setLiveItems(prev => {
          const ids = new Set(prev.map(p => p.id));
          return [...prev, ...cleanItems.filter(i => !ids.has(i.id))];
        });
        setPage(nextPage);
        if (res.total) setTotalTitles(res.total);
        setHasMore(res.hasMore ?? (cleanItems.length >= 15));
      } else {
        setHasMore(false);
      }
    } catch {
      setHasMore(false);
    } finally {
      setLoadingMore(false);
    }
  }, [loadingMore, hasMore, page, activeTab, selectedVj, selectedGenre]);

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

  const displayItems = useMemo(() => {
    if (liveItems.length > 0) return liveItems;
    if (activeTab === 'VJ') {
      const cleanVj = selectedVj.toLowerCase().replace('vj', '').trim();
      return movies.filter(m => m.vj.toLowerCase().includes(cleanVj));
    } else {
      const cleanGenre = selectedGenre.toLowerCase().trim();
      return movies.filter(m => m.genre.toLowerCase().includes(cleanGenre));
    }
  }, [liveItems, movies, activeTab, selectedVj, selectedGenre]);

  return (
    <div className="p-4 sm:p-8 md:p-10 w-full max-w-[2400px] mx-auto space-y-6 animate-fadeIn">
      {/* Category Type Tab Switcher matching Android CategoriesScreen */}
      <div className="flex items-center justify-between border-b border-[#132845] pb-4">
        <div className="flex items-center gap-3">
          <button
            onClick={() => setActiveTab('VJ')}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition-all ${
              activeTab === 'VJ'
                ? 'bg-[#10B981] text-black shadow-lg shadow-[#10B981]/30'
                : 'bg-[#0A192F] text-[#94A3B8] hover:text-white border border-[#1E293B]'
            }`}
          >
            <Clapperboard className="w-4 h-4" />
            <span>Ugandan VJs</span>
          </button>

          <button
            onClick={() => setActiveTab('GENRE')}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition-all ${
              activeTab === 'GENRE'
                ? 'bg-[#10B981] text-black shadow-lg shadow-[#10B981]/30'
                : 'bg-[#0A192F] text-[#94A3B8] hover:text-white border border-[#1E293B]'
            }`}
          >
            <Layers className="w-4 h-4" />
            <span>Genres & Themes</span>
          </button>
        </div>

        <span className="text-xs font-bold text-[#94A3B8]">
          Showing <strong className="text-white">{displayItems.length}</strong>{totalTitles > displayItems.length ? ` of ${totalTitles}` : ''} Titles
        </span>
      </div>

      {/* Categories Horizontal Pills Row with PC scroll controls */}
      <div className="relative flex items-center gap-1.5 w-full">
        {/* PC Left Scroll Arrow Button */}
        <button
          type="button"
          onClick={() => scrollHorizontally(-350)}
          className="hidden md:flex items-center justify-center w-8 h-8 rounded-full bg-[#0A192F] hover:bg-[#10B981]/20 text-[#94A3B8] hover:text-[#10B981] border border-[#1E293B] shadow-md flex-shrink-0 transition-all active:scale-95 z-10"
          title="Scroll Left (or use mouse wheel)"
        >
          <ChevronLeft className="w-4 h-4" />
        </button>

        {/* Scrollable Pills Container with visible PC scrollbar and mouse-wheel support */}
        <div
          ref={scrollContainerRef}
          onWheel={(e) => {
            if (e.deltaY !== 0 && scrollContainerRef.current) {
              scrollContainerRef.current.scrollLeft += e.deltaY;
            }
          }}
          className="flex items-center gap-2.5 overflow-x-auto vj-horizontal-scrollbar pb-2 pt-1 flex-1 scroll-smooth"
        >
          {activeTab === 'VJ' ? (
            vjs.map(v => (
              <button
                key={v.id}
                onClick={() => setSelectedVj(v.name)}
                className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold whitespace-nowrap transition-all border flex-shrink-0 ${
                  selectedVj === v.name
                    ? 'bg-[#10B981] text-black border-[#10B981] shadow-lg shadow-[#10B981]/25 scale-105'
                    : 'bg-[#0A192F] text-[#94A3B8] hover:text-white border-[#1E293B]'
                }`}
              >
                <span>{v.icon}</span>
                <span>{v.name}</span>
              </button>
            ))
          ) : (
            genres.map(g => (
              <button
                key={g}
                onClick={() => setSelectedGenre(g)}
                className={`px-4 py-2.5 rounded-xl text-xs font-bold whitespace-nowrap transition-all border flex-shrink-0 ${
                  selectedGenre === g
                    ? 'bg-[#10B981] text-black border-[#10B981] shadow-lg shadow-[#10B981]/25 scale-105'
                    : 'bg-[#0A192F] text-[#94A3B8] hover:text-white border-[#1E293B]'
                }`}
              >
                {g}
              </button>
            ))
          )}
        </div>

        {/* PC Right Scroll Arrow Button */}
        <button
          type="button"
          onClick={() => scrollHorizontally(350)}
          className="hidden md:flex items-center justify-center w-8 h-8 rounded-full bg-[#0A192F] hover:bg-[#10B981]/20 text-[#94A3B8] hover:text-[#10B981] border border-[#1E293B] shadow-md flex-shrink-0 transition-all active:scale-95 z-10"
          title="Scroll Right (or use mouse wheel)"
        >
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>

      {/* Grid of movies matching Android Categories grid */}
      {loading && displayItems.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 space-y-2">
          <Loader2 className="w-8 h-8 text-[#10B981] animate-spin" />
          <p className="text-xs text-[#94A3B8]">Querying live index for {activeTab === 'VJ' ? selectedVj : selectedGenre}...</p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 xl:grid-cols-7 2xl:grid-cols-8 3xl:grid-cols-10 gap-3 sm:gap-4 pt-2">
            {displayItems.map(item => (
              <TvMediaCard
                key={item.id}
                item={item}
                onClick={() => onItemClick(item)}
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
                  <span>Loading more {activeTab === 'VJ' ? selectedVj : selectedGenre}...</span>
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
};
