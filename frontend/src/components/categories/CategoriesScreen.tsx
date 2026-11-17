import React, { useState, useMemo, useEffect, useCallback } from 'react';
import { MediaItem, VjItem } from '../../types';
import { TvMediaCard } from '../common/TvMediaCard';
import { Clapperboard, Layers, Loader2, ArrowDownCircle } from 'lucide-react';
import { api } from '../../services/api';

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

  const genres = [
    'Action', 'Sci-Fi', 'Adventure', 'Thriller', 'Horror', 'Crime', 'Comedy', 'Drama', 'Animation', 'Korean', 'Indian Movie', 'Kinigeria'
  ];

  const fetchCategoryPageOne = useCallback(async () => {
    setLoading(true);
    setPage(1);
    try {
      if (activeTab === 'VJ') {
        const res = await api.getMovies({ page: 1, perPage: 30, vj: selectedVj });
        if (res.items.length > 0) {
          setLiveItems(res.items);
          setTotalTitles(res.total || res.items.length);
          setHasMore(res.hasMore ?? (res.items.length >= 25));
        } else {
          setLiveItems([]);
          setHasMore(false);
        }
      } else {
        const res = await api.getMovies({ page: 1, perPage: 30, genre: selectedGenre });
        if (res.items.length > 0) {
          setLiveItems(res.items);
          setTotalTitles(res.total || res.items.length);
          setHasMore(res.hasMore ?? (res.items.length >= 15));
        } else {
          setLiveItems([]);
          setHasMore(false);
        }
      }
    } catch {
      // Keep existing
    } finally {
      setLoading(false);
    }
  }, [activeTab, selectedVj, selectedGenre]);

  useEffect(() => {
    fetchCategoryPageOne();
  }, [fetchCategoryPageOne]);

  const handleLoadMore = async () => {
    if (loadingMore || !hasMore) return;
    setLoadingMore(true);
    const nextPage = page + 1;
    try {
      const res = activeTab === 'VJ'
        ? await api.getMovies({ page: nextPage, perPage: 30, vj: selectedVj })
        : await api.getMovies({ page: nextPage, perPage: 30, genre: selectedGenre });

      if (res.items && res.items.length > 0) {
        setLiveItems(prev => {
          const ids = new Set(prev.map(p => p.id));
          return [...prev, ...res.items.filter(i => !ids.has(i.id))];
        });
        setPage(nextPage);
        if (res.total) setTotalTitles(res.total);
        setHasMore(res.hasMore ?? (res.items.length >= 15));
      } else {
        setHasMore(false);
      }
    } catch {
      setHasMore(false);
    } finally {
      setLoadingMore(false);
    }
  };

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
          Showing <strong className="text-white">{displayItems.length}</strong>{totalTitles > displayItems.length ? ` of ${totalTitles}` : ''} Titles
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
      {loading && displayItems.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 space-y-2">
          <Loader2 className="w-8 h-8 text-[#10B981] animate-spin" />
          <p className="text-xs text-[#94A3B8]">Querying live index for {activeTab === 'VJ' ? selectedVj : selectedGenre}...</p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3 sm:gap-4 pt-2">
            {displayItems.map(item => (
              <TvMediaCard
                key={item.id}
                item={item}
                onClick={() => onItemClick(item)}
                cardWidth="w-full"
              />
            ))}
          </div>

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
                    <span>Loading More Titles...</span>
                  </>
                ) : (
                  <>
                    <ArrowDownCircle className="w-4 h-4 text-[#10B981]" />
                    <span>Load More {activeTab === 'VJ' ? selectedVj : selectedGenre} (Page {page + 1})</span>
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
    </div>
  );
};
