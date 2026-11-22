import React, { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { MediaItem, VjItem } from '../../types';
import { TvMediaCard } from '../common/TvMediaCard';
import { Sparkles, Calendar, Shuffle, Clock, X, Loader2, ArrowDownCircle, Tv, ChevronLeft, ChevronRight, Film } from 'lucide-react';
import { api, isTrailerOrIntroUrl } from '../../services/api';

const isTrailerOnly = (item: MediaItem) => {
  if (!item.streamUrl) return false;
  return isTrailerOrIntroUrl(item.streamUrl, item.title) && !item.id.startsWith('tk_') && !item.streamUrl.includes('preview.php') && !item.streamUrl.includes('twolekede');
};

const filterByVj = (items: MediaItem[], vj: string | null): MediaItem[] => {
  if (!vj) return items;
  const cleanTarget = vj.toLowerCase().replace(/^(?:vj\s*|vj-)/i, '').replace(/[^a-z0-9]/g, '').trim();
  if (!cleanTarget) return items;
  return items.filter(item => {
    const cleanCard = (item.vj || '').toLowerCase().replace(/^(?:vj\s*|vj-)/i, '').replace(/[^a-z0-9]/g, '').trim();
    if (!cleanCard) return false;
    if ((cleanTarget.includes('kisuule') || cleanTarget.includes('kiwule')) && (cleanCard.includes('kisuule') || cleanCard.includes('kiwule'))) return true;
    if (cleanTarget.includes('icep') && (cleanCard.includes('icep') || cleanCard.includes('ice p'))) return true;
    return cleanCard.includes(cleanTarget) || cleanTarget.includes(cleanCard);
  });
};

interface MoviesScreenProps {
  movies: MediaItem[];
  vjs?: VjItem[];
  onMovieClick: (movie: MediaItem) => void;
  onNavigateToSeries?: (vj?: string) => void;
}

const ALL_YEARS = [
  '2026', '2025', '2024', '2023', '2022', '2021', '2020',
  '2019', '2018', '2017', '2016', '2015', '2014', '2013', '2012', '2011', '2010'
];

const POPULAR_VJS = [
  'VJ Junior', 'VJ Emmy', 'VJ ICEP', 'VJ Jingo', 'VJ Ulio',
  'VJ Soul', 'Uncle T', 'VJ Mosco', 'VJ Kevo', 'VJ Muba', 'VJ Shield',
  'Martin K', 'Heavy Q', 'VJ MK Kisuule', 'VJ Mark', 'VJ Tonny',
  'VJ Kriss Sweet', 'VJ HD', 'VJ Nelly', 'VJ Kimuli'
];

export const MoviesScreen: React.FC<MoviesScreenProps> = ({ movies: initialMovies, vjs, onMovieClick, onNavigateToSeries }) => {
  const [movieList, setMovieList] = useState<MediaItem[]>(initialMovies || []);
  const [page, setPage] = useState<number>(1);
  const [loading, setLoading] = useState<boolean>(false);
  const [loadingMore, setLoadingMore] = useState<boolean>(false);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [sortMode, setSortMode] = useState<'LATEST' | 'RANDOM' | 'OLD_MOVIES'>('LATEST');
  const [selectedYear, setSelectedYear] = useState<string | null>(null);
  const [selectedVj, setSelectedVj] = useState<string | null>(null);
  const [isYearDialogOpen, setIsYearDialogOpen] = useState<boolean>(false);

  // Horizontal scroll container ref & scroll helper for PC
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const scrollHorizontally = (offset: number) => {
    if (scrollContainerRef.current) {
      scrollContainerRef.current.scrollBy({ left: offset, behavior: 'smooth' });
    }
  };

  // Dynamically resolve full VJ list matching the Android app
  const availableVjs = useMemo(() => {
    if (vjs && vjs.length > 0) {
      return vjs.map(v => v.name);
    }
    return POPULAR_VJS;
  }, [vjs]);

  // Strictly enforce client-side VJ and year filtering while guaranteeing loaded movies display
  const displayedMovies = useMemo(() => {
    let items = movieList;
    if (selectedVj) {
      const filtered = filterByVj(items, selectedVj);
      items = filtered.length > 0 ? filtered : items;
    }
    if (selectedYear && selectedYear !== 'all') {
      const yr = selectedYear.trim();
      const yearFiltered = items.filter(i => (i.year || '').trim() === yr || (i.year || '').includes(yr));
      items = yearFiltered.length > 0 ? yearFiltered : items;
    }
    return items;
  }, [movieList, selectedVj, selectedYear]);

  // Fetch initial/filtered page 1
  const fetchPageOne = useCallback(async () => {
    setLoading(true);
    setPage(1);
    try {
      const res = await api.getMovies({
        page: 1,
        perPage: 24,
        year: selectedYear || undefined,
        vj: selectedVj || undefined,
        sort: sortMode
      });
      const cleanItems = (res.items || []).filter(i => !isTrailerOnly(i));
      setMovieList(cleanItems);
      setHasMore(cleanItems.length >= 12);
    } catch {
      // Keep initial movies on error
    } finally {
      setLoading(false);
    }
  }, [selectedYear, selectedVj, sortMode]);

  // Trigger page 1 fetch when filters change
  useEffect(() => {
    fetchPageOne();
  }, [fetchPageOne]);

  // Load More Next Page
  const handleLoadMore = useCallback(async () => {
    if (loadingMore || !hasMore) return;
    setLoadingMore(true);
    const nextPage = page + 1;
    try {
      const res = await api.getMovies({
        page: nextPage,
        perPage: 24,
        year: selectedYear || undefined,
        vj: selectedVj || undefined,
        sort: sortMode
      });

      if (res.items && res.items.length > 0) {
        const cleanItems = res.items.filter(i => !isTrailerOnly(i));
        setMovieList(prev => {
          const existingIds = new Set(prev.map(p => p.id));
          const newItems = cleanItems.filter(i => !existingIds.has(i.id));
          return [...prev, ...newItems];
        });
        setPage(nextPage);
        setHasMore(cleanItems.length >= 10);
      } else {
        setHasMore(false);
      }
    } catch {
      setHasMore(false);
    } finally {
      setLoadingMore(false);
    }
  }, [loadingMore, hasMore, page, selectedYear, selectedVj, sortMode]);

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

  return (
    <div className="p-4 sm:p-8 md:p-10 w-full max-w-[2400px] mx-auto space-y-6 animate-fadeIn">
      {/* Top Filter and Action Bar matching Android MoviesScreen */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#132845] pb-4">
        <div className="flex flex-wrap items-center gap-2">
          {/* Latest Sort Pill */}
          <button
            onClick={() => setSortMode('LATEST')}
            className={`flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-bold transition-all ${
              sortMode === 'LATEST'
                ? 'bg-[#10B981] text-black shadow-lg shadow-[#10B981]/30'
                : 'bg-[#0A192F] text-[#94A3B8] hover:text-white border border-[#1E293B]'
            }`}
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>Latest</span>
          </button>

          {/* Random Reshuffle Pill */}
          <button
            onClick={() => setSortMode(sortMode === 'RANDOM' ? 'LATEST' : 'RANDOM')}
            className={`flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-bold transition-all ${
              sortMode === 'RANDOM'
                ? 'bg-[#10B981] text-black shadow-lg shadow-[#10B981]/30'
                : 'bg-[#0A192F] text-[#94A3B8] hover:text-white border border-[#1E293B]'
            }`}
          >
            <Shuffle className="w-3.5 h-3.5" />
            <span>Reshuffle</span>
          </button>

          {/* Old Movies Pill */}
          <button
            onClick={() => setSortMode(sortMode === 'OLD_MOVIES' ? 'LATEST' : 'OLD_MOVIES')}
            className={`flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-bold transition-all ${
              sortMode === 'OLD_MOVIES'
                ? 'bg-[#10B981] text-black shadow-lg shadow-[#10B981]/30'
                : 'bg-[#0A192F] text-[#94A3B8] hover:text-white border border-[#1E293B]'
            }`}
          >
            <Clock className="w-3.5 h-3.5" />
            <span>Old Movies</span>
          </button>

          {/* Filter by Year Button matching Android TV dialog */}
          <button
            onClick={() => setIsYearDialogOpen(true)}
            className={`flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-bold transition-all ${
              selectedYear
                ? 'bg-[#34D399] text-black border border-[#10B981]'
                : 'bg-[#0A192F] text-[#94A3B8] hover:text-white border border-[#1E293B]'
            }`}
          >
            <Calendar className="w-3.5 h-3.5" />
            <span>{selectedYear ? `Year: ${selectedYear}` : 'Filter by Year'}</span>
          </button>

          {selectedYear && (
            <button
              onClick={() => setSelectedYear(null)}
              className="p-1 rounded-full bg-white/10 hover:bg-white/20 text-[#94A3B8]"
              title="Clear Year Filter"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>

        {/* Count badge */}
        <span className="text-xs font-bold text-[#94A3B8]">
          <strong className="text-white">{displayedMovies.length}</strong> Movies Loaded
        </span>
      </div>

      {/* VJ Horizontal Quick-Filter Bar with full roster & PC scroll controls */}
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
          className="flex items-center gap-2 overflow-x-auto vj-horizontal-scrollbar pb-2 pt-1 flex-1 scroll-smooth"
        >
          <button
            onClick={() => setSelectedVj(null)}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold whitespace-nowrap transition-all flex-shrink-0 ${
              selectedVj === null
                ? 'bg-[#10B981] text-black shadow-md'
                : 'bg-[#0A192F] text-[#94A3B8] hover:text-white border border-[#1E293B]'
            }`}
          >
            All Translators
          </button>
          {availableVjs.map(vj => (
            <button
              key={vj}
              onClick={() => setSelectedVj(selectedVj === vj ? null : vj)}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold whitespace-nowrap transition-all flex-shrink-0 ${
                selectedVj === vj
                  ? 'bg-[#10B981] text-black shadow-md'
                  : 'bg-[#0A192F] text-[#94A3B8] hover:text-white border border-[#1E293B]'
              }`}
            >
              {vj}
            </button>
          ))}
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

      {/* Loading indicator for page 1 */}
      {loading ? (
        <div className="flex flex-col items-center justify-center py-24 space-y-3">
          <Loader2 className="w-10 h-10 text-[#10B981] animate-spin" />
          <p className="text-sm font-semibold text-[#94A3B8]">Querying live Ugandan translated movies index...</p>
        </div>
      ) : displayedMovies.length === 0 ? (
        <div className="text-center py-16 px-6 space-y-4 max-w-lg mx-auto bg-[#0A192F]/50 border border-[#132845] rounded-2xl shadow-xl">
          <div className="w-12 h-12 rounded-2xl bg-[#10B981]/15 border border-[#10B981]/40 flex items-center justify-center mx-auto text-[#10B981]">
            <Tv className="w-6 h-6" />
          </div>
          <h3 className="text-lg font-bold text-white">
            {selectedYear && selectedVj
              ? `No movies found for "${selectedVj}" in ${selectedYear}`
              : selectedYear
              ? `No movies found released in ${selectedYear}`
              : selectedVj
              ? `No standalone movies found for "${selectedVj}"`
              : 'No movies found matching filters'}
          </h3>
          <p className="text-sm text-[#94A3B8]">
            {selectedVj && !selectedYear
              ? `"${selectedVj}" specializes in Ugandan Translated Series! Click below to stream all their multi-season translated series.`
              : 'Try selecting another year or clearing the filters.'}
          </p>
          <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
            {selectedVj && onNavigateToSeries && (
              <button
                onClick={() => onNavigateToSeries(selectedVj)}
                className="flex items-center gap-2 px-5 py-2.5 bg-[#10B981] hover:bg-[#34D399] text-black text-xs font-black rounded-xl transition-all shadow-lg shadow-[#10B981]/25 active:scale-95"
              >
                <Tv className="w-4 h-4" />
                <span>Browse {selectedVj} in Series</span>
              </button>
            )}
            <button
              onClick={() => { setSelectedYear(null); setSelectedVj(null); }}
              className="px-4 py-2.5 bg-[#0A192F] hover:bg-white/10 text-white border border-[#1E293B] hover:border-[#10B981]/50 text-xs font-bold rounded-xl transition-all"
            >
              Reset Filters
            </button>
          </div>
        </div>
      ) : (
        <>
          {/* Responsive Expanded Grid (up to 10 columns on ultrawide) */}
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 xl:grid-cols-7 2xl:grid-cols-8 3xl:grid-cols-10 gap-3 sm:gap-4 pt-2">
            {displayedMovies.map((movie) => (
              <TvMediaCard
                key={movie.id}
                item={movie}
                onClick={() => onMovieClick(movie)}
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
                  <span>Loading more movies...</span>
                </div>
              )}
            </div>
          )}
        </>
      )}

      {/* Year Selector Modal Dialog matching Android TV isYearSelectorOpen */}
      {isYearDialogOpen && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-[#0A192F] border border-[#10B981]/50 rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between border-b border-[#132845] pb-3">
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <Calendar className="w-4 h-4 text-[#10B981]" />
                Select Release Year
              </h3>
              <button
                onClick={() => setIsYearDialogOpen(false)}
                className="p-1 rounded-full text-[#94A3B8] hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="grid grid-cols-3 gap-2 max-h-64 overflow-y-auto pr-1">
              <button
                onClick={() => {
                  setSelectedYear(null);
                  setIsYearDialogOpen(false);
                }}
                className={`py-2 px-3 rounded-lg text-xs font-bold border transition-all ${
                  selectedYear === null
                    ? 'bg-[#10B981] text-black border-[#10B981]'
                    : 'bg-[#132845] text-[#94A3B8] hover:text-white border-transparent'
                }`}
              >
                All Years
              </button>
              {ALL_YEARS.map(yr => (
                <button
                  key={yr}
                  onClick={() => {
                    setSelectedYear(yr);
                    setIsYearDialogOpen(false);
                  }}
                  className={`py-2 px-3 rounded-lg text-xs font-bold border transition-all ${
                    selectedYear === yr
                      ? 'bg-[#10B981] text-black border-[#10B981]'
                      : 'bg-[#132845] text-[#94A3B8] hover:text-white border-transparent'
                  }`}
                >
                  {yr}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
