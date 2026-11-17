import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { MediaItem } from '../../types';
import { TvMediaCard } from '../common/TvMediaCard';
import { Sparkles, Calendar, Shuffle, Clock, X, Loader2, ArrowDownCircle } from 'lucide-react';
import { api } from '../../services/api';

interface MoviesScreenProps {
  movies: MediaItem[];
  onMovieClick: (movie: MediaItem) => void;
}

const ALL_YEARS = [
  '2026', '2025', '2024', '2023', '2022', '2021', '2020',
  '2019', '2018', '2017', '2016', '2015', '2014', '2013', '2012', '2011', '2010'
];

const POPULAR_VJS = [
  'VJ Junior', 'VJ Emmy', 'VJ Jingo', 'VJ Ice P', 'VJ Ulio',
  'VJ Kriss Sweet', 'VJ HD', 'VJ MK Kisuule', 'VJ Mark', 'VJ Nelly', 'VJ Kimuli'
];

export const MoviesScreen: React.FC<MoviesScreenProps> = ({ movies: initialMovies, onMovieClick }) => {
  const [movieList, setMovieList] = useState<MediaItem[]>(initialMovies || []);
  const [page, setPage] = useState<number>(1);
  const [loading, setLoading] = useState<boolean>(false);
  const [loadingMore, setLoadingMore] = useState<boolean>(false);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [sortMode, setSortMode] = useState<'LATEST' | 'RANDOM' | 'OLD_MOVIES'>('LATEST');
  const [selectedYear, setSelectedYear] = useState<string | null>(null);
  const [selectedVj, setSelectedVj] = useState<string | null>(null);
  const [isYearDialogOpen, setIsYearDialogOpen] = useState<boolean>(false);

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
      setMovieList(res.items);
      setHasMore(res.items.length >= 12);
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
  const handleLoadMore = async () => {
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
        setMovieList(prev => {
          const existingIds = new Set(prev.map(p => p.id));
          const newItems = res.items.filter(i => !existingIds.has(i.id));
          return [...prev, ...newItems];
        });
        setPage(nextPage);
        setHasMore(res.items.length >= 12);
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
          <strong className="text-white">{movieList.length}</strong> Movies Loaded
        </span>
      </div>

      {/* VJ Horizontal Quick-Filter Bar */}
      <div className="flex items-center gap-2 overflow-x-auto no-scrollbar py-1">
        <button
          onClick={() => setSelectedVj(null)}
          className={`px-3 py-1.5 rounded-lg text-xs font-bold whitespace-nowrap transition-all ${
            selectedVj === null
              ? 'bg-[#10B981] text-black shadow-md'
              : 'bg-[#0A192F] text-[#94A3B8] hover:text-white border border-[#1E293B]'
          }`}
        >
          All Translators
        </button>
        {POPULAR_VJS.map(vj => (
          <button
            key={vj}
            onClick={() => setSelectedVj(selectedVj === vj ? null : vj)}
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

      {/* Loading indicator for page 1 */}
      {loading ? (
        <div className="flex flex-col items-center justify-center py-24 space-y-3">
          <Loader2 className="w-10 h-10 text-[#10B981] animate-spin" />
          <p className="text-sm font-semibold text-[#94A3B8]">Querying live Ugandan translated movies index...</p>
        </div>
      ) : movieList.length === 0 ? (
        <div className="text-center py-20 space-y-3">
          <p className="text-base text-[#94A3B8]">No movies found matching the selected filter.</p>
          <button
            onClick={() => { setSelectedYear(null); setSelectedVj(null); }}
            className="px-4 py-2 bg-[#10B981] text-black text-xs font-bold rounded-lg"
          >
            Reset Filters
          </button>
        </div>
      ) : (
        <>
          {/* 6-Column Grid matching Android TV MoviesScreen */}
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3 sm:gap-4 pt-2">
            {movieList.map((movie) => (
              <TvMediaCard
                key={movie.id}
                item={movie}
                onClick={() => onMovieClick(movie)}
                cardWidth="w-full"
              />
            ))}
          </div>

          {/* Load More Button for Infinite Catalog Exploration */}
          {hasMore && (
            <div className="flex flex-col items-center justify-center pt-8 pb-4 space-y-2">
              <button
                onClick={handleLoadMore}
                disabled={loadingMore}
                className="flex items-center gap-2 px-6 py-3 bg-[#0A192F] hover:bg-[#132845] border border-[#10B981]/60 hover:border-[#10B981] text-white hover:text-[#34D399] font-bold text-sm rounded-xl shadow-lg transition-all active:scale-95 disabled:opacity-50"
              >
                {loadingMore ? (
                  <>
                    <Loader2 className="w-4 h-4 text-[#10B981] animate-spin" />
                    <span>Loading Next 24 Movies...</span>
                  </>
                ) : (
                  <>
                    <ArrowDownCircle className="w-4 h-4 text-[#10B981]" />
                    <span>Load More Movies (Page {page + 1})</span>
                  </>
                )}
              </button>
              <span className="text-xs text-[#94A3B8]">
                Accessing 17,000+ Ugandan translated movies index
              </span>
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
