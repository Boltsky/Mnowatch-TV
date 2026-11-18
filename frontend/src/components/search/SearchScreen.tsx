import React, { useState, useEffect } from 'react';
import { Search, X, Film, Sparkles } from 'lucide-react';
import { MediaItem } from '../../types';
import { api, isTrailerOrIntroUrl } from '../../services/api';
import { TvMediaCard } from '../common/TvMediaCard';

const isTrailerOnly = (item: MediaItem) => {
  if (!item.streamUrl) return false;
  return isTrailerOrIntroUrl(item.streamUrl, item.title) && !item.id.startsWith('tk_') && !item.streamUrl.includes('preview.php') && !item.streamUrl.includes('twolekede');
};

interface SearchScreenProps {
  onItemClick: (item: MediaItem) => void;
  onPlay: (item: MediaItem) => void;
}

export const SearchScreen: React.FC<SearchScreenProps> = ({ onItemClick }) => {
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
      const cleanItems = (items || []).filter(i => !isTrailerOnly(i));
      setResults(cleanItems);
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

  const quickFilters = ['VJ Junior', 'VJ Emmy', 'VJ Jingo', 'VJ Ice P', 'VJ Ulio', 'Action', 'Series', '2025', '2024'];

  return (
    <div className="min-h-screen bg-[#010318] text-white p-4 sm:p-6 md:p-8 pb-24 md:pb-12 animate-fadeIn">
      {/* Search Bar Input */}
      <div className="max-w-2xl mx-auto space-y-4">
        <div className="relative flex items-center">
          <Search className="absolute left-4 w-5 h-5 text-[#10B981] pointer-events-none" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search movie, translated series, or VJ name..."
            className="w-full pl-12 pr-12 py-3.5 rounded-2xl bg-[#0A192F] border border-[#132845] focus:border-[#10B981] text-white placeholder-[#94A3B8] text-sm sm:text-base outline-none shadow-xl transition-all"
            autoFocus
          />
          {query && (
            <button
              onClick={() => setQuery('')}
              className="absolute right-4 p-1.5 rounded-full hover:bg-white/10 text-[#94A3B8] hover:text-white"
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>

        {/* Quick VJ & Genre Filter Tags */}
        <div className="flex flex-wrap items-center gap-1.5 sm:gap-2 justify-center">
          {quickFilters.map((tag) => (
            <button
              key={tag}
              onClick={() => setQuery(tag)}
              className="px-2.5 sm:px-3 py-1 rounded-full bg-[#0A192F] hover:bg-[#10B981]/20 text-[#94A3B8] hover:text-[#34D399] border border-[#1E293B] hover:border-[#10B981]/50 text-xs font-semibold transition-all active:scale-95"
            >
              {tag}
            </button>
          ))}
          <button
            onClick={() => setShowTvKeyboard(!showTvKeyboard)}
            className="hidden sm:inline-flex px-3 py-1 rounded-full bg-[#10B981]/15 text-[#10B981] border border-[#10B981]/40 text-xs font-semibold ml-2 hover:bg-[#10B981]/25 transition-all"
          >
            {showTvKeyboard ? 'Hide TV Keys' : 'TV Remote Keys'}
          </button>
        </div>

        {/* On-screen TV Keypad for Remote Control / TV Users */}
        {showTvKeyboard && (
          <div className="p-3 sm:p-4 rounded-2xl bg-[#0A192F] border border-[#132845] space-y-2 max-w-xl mx-auto shadow-2xl">
            {tvKeyboardKeys.map((row, rIdx) => (
              <div key={rIdx} className="flex justify-center gap-1 sm:gap-2">
                {row.map((k) => (
                  <button
                    key={k}
                    onClick={() => handleKeyPress(k)}
                    className={`py-1.5 sm:py-2 px-2 sm:px-3 rounded-lg font-bold text-xs sm:text-sm border transition-all active:scale-95 ${
                      k === 'SPACE' || k === 'BACKSPACE'
                        ? 'bg-[#10B981] text-black border-[#10B981] px-3 sm:px-4 text-xs font-black'
                        : 'bg-[#132845] hover:bg-[#10B981]/20 text-white border-transparent hover:border-[#10B981]/50'
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
          <div className="flex items-center justify-center py-12 text-[#94A3B8] text-sm">
            Searching translated catalog...
          </div>
        )}

        {!loading && query && results.length === 0 && (
          <div className="flex flex-col items-center justify-center py-16 text-center space-y-2">
            <Film className="w-12 h-12 text-[#1E293B] mb-2" />
            <p className="text-white font-semibold">No movies or series found for "{query}"</p>
            <p className="text-[#94A3B8] text-xs">Try searching by VJ name (Junior, Emmy, Jingo, Ice P) or category.</p>
          </div>
        )}

        {results.length > 0 && (
          <div className="space-y-4">
            <div className="flex items-center justify-between border-b border-[#132845] pb-3">
              <h2 className="text-xs sm:text-sm uppercase tracking-wider font-bold text-[#94A3B8] flex items-center gap-1.5">
                <Sparkles className="w-4 h-4 text-[#10B981]" />
                Found <strong className="text-white">{results.length}</strong> Titles
              </h2>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3 sm:gap-4">
              {results.map((item) => (
                <TvMediaCard
                  key={item.id}
                  item={item}
                  onClick={() => onItemClick(item)}
                  cardWidth="w-full"
                />
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
