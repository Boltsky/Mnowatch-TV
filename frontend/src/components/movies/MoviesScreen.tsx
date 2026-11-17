import React, { useState, useMemo } from 'react';
import { MediaItem } from '../../types';
import { TvMediaCard } from '../common/TvMediaCard';
import { Sparkles, Calendar, Shuffle, Clock, X, Check, Filter } from 'lucide-react';

interface MoviesScreenProps {
  movies: MediaItem[];
  onMovieClick: (movie: MediaItem) => void;
}

export const MoviesScreen: React.FC<MoviesScreenProps> = ({ movies, onMovieClick }) => {
  const [sortMode, setSortMode] = useState<'LATEST' | 'RANDOM' | 'OLD_MOVIES'>('LATEST');
  const [selectedYear, setSelectedYear] = useState<string | null>(null);
  const [selectedVj, setSelectedVj] = useState<string | null>(null);
  const [isFilterFree, setIsFilterFree] = useState<boolean>(false);
  const [isYearDialogOpen, setIsYearDialogOpen] = useState<boolean>(false);

  // Compute available years
  const availableYears = useMemo(() => {
    const years = new Set<string>();
    movies.forEach(m => {
      if (m.year && m.year.match(/^\d{4}$/)) years.add(m.year);
    });
    return Array.from(years).sort().reverse();
  }, [movies]);

  // Compute available VJs
  const availableVjs = useMemo(() => {
    const vjs = new Set<string>();
    movies.forEach(m => {
      if (m.vj) vjs.add(m.vj);
    });
    return Array.from(vjs).sort();
  }, [movies]);

  // Filtered and sorted list
  const displayMovies = useMemo(() => {
    let list = [...movies];

    if (selectedYear) {
      list = list.filter(m => m.year === selectedYear);
    }

    if (selectedVj) {
      list = list.filter(m => m.vj.toLowerCase().includes(selectedVj.toLowerCase()));
    }

    if (sortMode === 'OLD_MOVIES') {
      list.sort((a, b) => (parseInt(a.year || '2024') - parseInt(b.year || '2024')));
    } else if (sortMode === 'RANDOM') {
      // Deterministic pseudorandom shuffle
      list.sort((a, b) => a.title.localeCompare(b.title));
    } else {
      list.sort((a, b) => (parseInt(b.year || '2024') - parseInt(a.year || '2024')));
    }

    return list;
  }, [movies, selectedYear, selectedVj, sortMode, isFilterFree]);

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
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>

        {/* Count badge */}
        <span className="text-xs font-bold text-[#94A3B8]">
          <strong className="text-white">{displayMovies.length}</strong> Movies
        </span>
      </div>

      {/* VJ Horizontal Quick-Filter Bar */}
      <div className="flex items-center gap-2 overflow-x-auto no-scrollbar py-1">
        <button
          onClick={() => setSelectedVj(null)}
          className={`px-3 py-1.5 rounded-lg text-xs font-bold whitespace-nowrap transition-all ${
            selectedVj === null
              ? 'bg-[#10B981] text-black'
              : 'bg-[#0A192F] text-[#94A3B8] hover:text-white border border-[#1E293B]'
          }`}
        >
          All Translators
        </button>
        {availableVjs.map(vj => (
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

      {/* 6-Column Grid matching Android TV MoviesScreen */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3 sm:gap-4 pt-2">
        {displayMovies.map((movie) => (
          <TvMediaCard
            key={movie.id}
            item={movie}
            onClick={() => onMovieClick(movie)}
            cardWidth="w-full"
          />
        ))}
      </div>

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
              {availableYears.map(yr => (
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
