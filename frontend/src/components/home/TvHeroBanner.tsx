import React from 'react';
import { Play, Info, Bookmark, BookmarkCheck } from 'lucide-react';
import { MediaItem } from '../../types';

interface TvHeroBannerProps {
  item: MediaItem;
  isFavorite: boolean;
  onPlayClick: () => void;
  onFavoriteClick: () => void;
  onDetailsClick: () => void;
}

export const TvHeroBanner: React.FC<TvHeroBannerProps> = ({
  item,
  isFavorite,
  onPlayClick,
  onFavoriteClick,
  onDetailsClick
}) => {
  return (
    <div className="relative w-full h-[320px] sm:h-[380px] md:h-[420px] rounded-2xl overflow-hidden bg-[#132845] border border-[#1E293B] shadow-2xl mb-8 group">
      {/* Backdrop Image */}
      <img
        src={item.backdropUrl || item.posterUrl}
        alt={item.title}
        className="absolute inset-0 w-full h-full object-cover object-center filter brightness-90 group-hover:scale-102 transition-transform duration-700 ease-out"
      />

      {/* Dual Gradient Scrim matching Compose drawWithCache */}
      <div 
        className="absolute inset-0"
        style={{
          background: 'linear-gradient(to right, rgba(1, 3, 24, 0.98) 0%, rgba(1, 3, 24, 0.85) 45%, rgba(1, 3, 24, 0.3) 80%, transparent 100%)'
        }}
      />
      <div 
        className="absolute inset-0"
        style={{
          background: 'linear-gradient(to top, rgba(1, 3, 24, 0.95) 0%, transparent 60%)'
        }}
      />

      {/* Banner Content Layout matching TvHeroBanner in HomeScreen.kt */}
      <div className="absolute inset-0 p-6 sm:p-10 flex flex-col justify-center max-w-2xl z-10 space-y-3">
        {/* Badges row */}
        <div className="flex flex-wrap items-center gap-2">
          <div className="px-2 py-0.5 rounded bg-[#10B981] text-black font-extrabold text-[10px] tracking-wider uppercase">
            FEATURED PREMIERE
          </div>
          {item.vj && (
            <div className="px-2 py-0.5 rounded bg-black/60 border border-[#10B981]/50 text-[#34D399] font-bold text-[10px]">
              {item.vj}
            </div>
          )}
          <span className="text-xs font-semibold text-[#94A3B8]">
            1080p FHD • {item.year || '2024'}
          </span>
        </div>

        {/* Title */}
        <h1 className="text-2xl sm:text-4xl md:text-5xl font-black text-white tracking-tight line-clamp-1 drop-shadow-lg">
          {item.title}
        </h1>

        {/* Overview */}
        <p className="text-xs sm:text-sm text-[#94A3B8] leading-relaxed line-clamp-2 sm:line-clamp-3 max-w-xl">
          {item.description}
        </p>

        {/* Action Buttons row matching TvButton */}
        <div className="flex items-center gap-3 pt-2">
          <button
            onClick={onPlayClick}
            className="flex items-center gap-2 px-6 sm:px-8 py-3 rounded-lg bg-[#10B981] hover:bg-[#34D399] text-black font-extrabold text-sm sm:text-base shadow-lg shadow-[#10B981]/30 transition-all active:scale-95 tv-card-focus"
          >
            <Play className="w-5 h-5 fill-black" />
            <span>Play Now</span>
          </button>

          <button
            onClick={onDetailsClick}
            className="flex items-center gap-2 px-5 sm:px-6 py-3 rounded-lg bg-[#0A192F] hover:bg-[#132845] text-white font-semibold text-sm sm:text-base border border-[#1E293B] hover:border-[#10B981]/50 transition-all active:scale-95"
          >
            <Info className="w-4 h-4 text-[#34D399]" />
            <span>Details</span>
          </button>

          <button
            onClick={onFavoriteClick}
            className="flex items-center gap-2 px-4 sm:px-5 py-3 rounded-lg bg-[#0A192F] hover:bg-[#132845] text-white font-semibold text-sm sm:text-base border border-[#1E293B] hover:border-[#10B981]/50 transition-all active:scale-95"
          >
            {isFavorite ? (
              <>
                <BookmarkCheck className="w-4 h-4 text-[#10B981]" />
                <span className="text-[#10B981]">Saved</span>
              </>
            ) : (
              <>
                <Bookmark className="w-4 h-4 text-[#94A3B8]" />
                <span className="text-[#94A3B8]">Watchlist</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
};
