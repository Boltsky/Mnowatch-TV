import React, { useState, useEffect } from 'react';
import { Search, X, Clapperboard, Film } from 'lucide-react';
import { MediaItem } from '../../types';
import { api } from '../../services/api';
import { MediaCard } from '../home/MediaCard';

interface SearchScreenProps {
  onItemClick: (item: MediaItem) => void;
  onPlay: (item: MediaItem) => void;
}

export const SearchScreen: React.FC<SearchScreenProps> = ({ onItemClick, onPlay }) => {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<MediaItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [showTvKeyboard, setShowTvKeyboard] = useState(false);

  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      return;
    }

    const timer = setTimeout(async () => {
      setLoading(true);
      const items = await api.search(query);
      setResults(items);
      setLoading(false);
    }, 250);

    return () => clearTimeout(timer);
  }, [query]);

  const tvKeyboardKeys = [
    ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'],
    ['Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'],
    ['A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'],
    ['Z', 'X', 'C', 'V', 'B', 'N', 'M', 'SPACE', 'BACKSPACE']
  ];

  const handleKeyPress = (key: string) => {
    if (key === 'SPACE') {
      setQuery(prev => prev + ' ');
    } else if (key === 'BACKSPACE') {
      setQuery(prev => prev.slice(0, -1));
    } else {
      setQuery(prev => prev + key);
    }
  };

  const quickFilters = ['VJ Junior', 'VJ Emmy', 'VJ Jingo', 'VJ Ice P', 'Action', 'Series', '2024'];

  return (
    <div className="min-h-screen bg-[#0a0f0d] text-white p-4 sm:p-6 md:p-8">
      {/* Search Bar Input */}
      <div className="max-w-2xl mx-auto space-y-4">
        <div className="relative flex items-center">
          <Search className="absolute left-4 w-5 h-5 text-emerald-400 pointer-events-none" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search movie, translated series, or VJ name..."
            className="w-full pl-12 pr-12 py-3.5 rounded-2xl bg-muno-card border border-muno-border focus:border-emerald-500 text-white placeholder-zinc-500 text-sm sm:text-base outline-none shadow-xl transition-all"
            autoFocus
          />
          {query && (
            <button
              onClick={() => setQuery('')}
              className="absolute right-4 p-1 rounded-full hover:bg-white/10 text-zinc-400 hover:text-white"
            >
              <X className="w-5 h-5" />
            </button>
          )}
        </div>

        {/* Quick VJ & Genre Filter Tags */}
        <div className="flex flex-wrap items-center gap-2 justify-center">
          {quickFilters.map((tag) => (
            <button
              key={tag}
              onClick={() => setQuery(tag)}
              className="px-3 py-1 rounded-full bg-white/5 hover:bg-emerald-500/20 text-zinc-300 hover:text-emerald-400 border border-white/10 text-xs font-semibold transition-all active:scale-95"
            >
              {tag}
            </button>
          ))}
          <button
            onClick={() => setShowTvKeyboard(!showTvKeyboard)}
            className="px-3 py-1 rounded-full bg-emerald-950 text-emerald-400 border border-emerald-500/30 text-xs font-semibold ml-2"
          >
            {showTvKeyboard ? 'Hide TV Keys' : 'TV Remote Keys'}
          </button>
        </div>

        {/* On-screen TV Keypad for Remote Control Users */}
        {showTvKeyboard && (
          <div className="p-4 rounded-2xl bg-muno-surface border border-muno-border space-y-2">
            {tvKeyboardKeys.map((row, rIdx) => (
              <div key={rIdx} className="flex justify-center gap-1.5 sm:gap-2">
                {row.map((k) => (
                  <button
                    key={k}
                    onClick={() => handleKeyPress(k)}
                    className={`py-2 px-3 rounded-lg font-bold text-xs sm:text-sm border transition-all active:scale-95 ${
                      k === 'SPACE' || k === 'BACKSPACE'
                        ? 'bg-emerald-500 text-black border-emerald-500 px-4'
                        : 'bg-white/5 hover:bg-white/20 text-white border-white/10'
                    }`}
                  >
                    {k}
                  </button>
                ))}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Results Section */}
      <div className="mt-8 max-w-7xl mx-auto">
        {loading && (
          <div className="flex items-center justify-center py-12 text-zinc-400 text-sm">
            Searching translated catalog...
          </div>
        )}

        {!loading && query && results.length === 0 && (
          <div className="flex flex-col items-center justify-center py-16 text-center space-y-2">
            <Film className="w-12 h-12 text-zinc-600 mb-2" />
            <p className="text-zinc-300 font-semibold">No movies or series found for "{query}"</p>
            <p className="text-zinc-500 text-xs">Try searching by VJ name (Junior, Emmy, Jingo, Ice P) or genre.</p>
          </div>
        )}

        {results.length > 0 && (
          <div className="space-y-4">
            <h2 className="text-sm uppercase tracking-wider font-bold text-zinc-400">
              Found {results.length} Titles
            </h2>
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3 sm:gap-4">
              {results.map((item) => (
                <MediaCard
                  key={item.id}
                  item={item}
                  onClick={onItemClick}
                  onPlay={onPlay}
                />
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
