import React, { useState, useMemo } from 'react';
import { MediaItem, VjItem } from '../../types';
import { TvMediaCard } from '../common/TvMediaCard';
import { Clapperboard, Film, Layers } from 'lucide-react';

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

  const genres = [
    'Action', 'Sci-Fi', 'Adventure', 'Thriller', 'Horror', 'Crime', 'Comedy', 'Drama', 'Animation'
  ];

  const filteredItems = useMemo(() => {
    if (activeTab === 'VJ') {
      const cleanVj = selectedVj.toLowerCase().replace('vj', '').trim();
      return movies.filter(m => m.vj.toLowerCase().includes(cleanVj));
    } else {
      const cleanGenre = selectedGenre.toLowerCase().trim();
      return movies.filter(m => m.genre.toLowerCase().includes(cleanGenre));
    }
  }, [movies, activeTab, selectedVj, selectedGenre]);

  return (
    <div className="p-4 sm:p-9 max-w-7xl mx-auto space-y-6 animate-fadeIn">
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
          <strong className="text-white">{filteredItems.length}</strong> Titles
        </span>
      </div>

      {/* Categories Horizontal Pills Row */}
      <div className="flex items-center gap-2.5 overflow-x-auto no-scrollbar py-1">
        {activeTab === 'VJ' ? (
          vjs.map(v => (
            <button
              key={v.id}
              onClick={() => setSelectedVj(v.name)}
              className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold whitespace-nowrap transition-all border ${
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
              className={`px-4 py-2.5 rounded-xl text-xs font-bold whitespace-nowrap transition-all border ${
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

      {/* Grid of movies matching Android Categories grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3 sm:gap-4 pt-2">
        {filteredItems.map(item => (
          <TvMediaCard
            key={item.id}
            item={item}
            onClick={() => onItemClick(item)}
            cardWidth="w-full"
          />
        ))}
      </div>
    </div>
  );
};
