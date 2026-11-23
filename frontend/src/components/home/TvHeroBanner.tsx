import React, { useState, useEffect } from 'react';
import { Play, Info, Bookmark, BookmarkCheck, ChevronLeft, ChevronRight } from 'lucide-react';
import { MediaItem } from '../../types';

interface TvHeroBannerProps {
  item?: MediaItem;
  items?: MediaItem[];
  isFavorite: boolean | ((item: MediaItem) => boolean);
  onPlayClick: (item: MediaItem) => void;
  onFavoriteClick: (item: MediaItem) => void;
  onDetailsClick: (item: MediaItem) => void;
}

export const TvHeroBanner: React.FC<TvHeroBannerProps> = ({
  item,
  items,
  isFavorite,
  onPlayClick,
  onFavoriteClick,
  onDetailsClick
}) => {
  const slides = (items && items.length > 0) ? items : (item ? [item] : []);
  const [currentIndex, setCurrentIndex] = useState<number>(0);
  const [isPaused, setIsPaused] = useState<boolean>(false);

  // Keep index within bounds if slide count changes
  const safeIndex = slides.length > 0 ? currentIndex % slides.length : 0;
  const currentItem = slides[safeIndex];

  // Auto-advance slideshow timer every 5.5 seconds (pauses on hover)
  useEffect(() => {
    if (slides.length <= 1 || isPaused) return;

    const timer = setInterval(() => {
      setCurrentIndex((prev) => (prev + 1) % slides.length);
    }, 5500);

    return () => clearInterval(timer);
  }, [slides.length, isPaused]);

  if (!currentItem) return null;

  const prevSlide = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (slides.length <= 1) return;
    setCurrentIndex((prev) => (prev - 1 + slides.length) % slides.length);
  };

  const nextSlide = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (slides.length <= 1) return;
    setCurrentIndex((prev) => (prev + 1) % slides.length);
  };

  const isCurrentFavorite = typeof isFavorite === 'function'
    ? isFavorite(currentItem)
    : Boolean(isFavorite);

  return (
    <div 
      className="relative w-full h-[360px] sm:h-[420px] md:h-[460px] lg:h-[480px] rounded-2xl sm:rounded-3xl overflow-hidden bg-[#0A192F] border border-[#1E293B] shadow-2xl mb-6 sm:mb-8 group select-none"
      onMouseEnter={() => setIsPaused(true)}
      onMouseLeave={() => setIsPaused(false)}
    >
      {/* 1. Crossfading Animated Backdrop Slides */}
      {slides.map((slide, idx) => {
        const isActive = idx === safeIndex;
        const bgImage = slide.backdropUrl || slide.posterUrl;
        return (
          <div
            key={slide.id || idx}
            className={`absolute inset-0 transition-all duration-1000 ease-in-out ${
              isActive 
                ? 'opacity-100 scale-100 z-0' 
                : 'opacity-0 scale-105 pointer-events-none -z-10'
            }`}
          >
            {bgImage ? (
              <img
                src={bgImage}
                alt={slide.title}
                className="w-full h-full object-cover object-center filter brightness-[0.7] contrast-[1.1] transition-transform duration-10000 ease-out group-hover:scale-105"
                onError={(e) => {
                  // Fallback to poster if backdrop fails
                  if (slide.posterUrl && (e.target as HTMLImageElement).src !== slide.posterUrl) {
                    (e.target as HTMLImageElement).src = slide.posterUrl;
                  }
                }}
              />
            ) : (
              <div className="w-full h-full bg-gradient-to-r from-[#010318] via-[#0A192F] to-[#132845]" />
            )}
          </div>
        );
      })}

      {/* 2. Atmospheric Multi-Layer Gradient Scrims for text contrast */}
      <div 
        className="absolute inset-0 z-10 pointer-events-none"
        style={{
          background: 'linear-gradient(to right, rgba(1, 3, 24, 0.98) 0%, rgba(1, 3, 24, 0.90) 45%, rgba(1, 3, 24, 0.40) 75%, transparent 100%)'
        }}
      />
      <div 
        className="absolute inset-0 z-10 pointer-events-none"
        style={{
          background: 'linear-gradient(to top, rgba(1, 3, 24, 0.95) 0%, rgba(1, 3, 24, 0.3) 50%, transparent 100%)'
        }}
      />

      {/* 3. Main Slideshow Content Container */}
      <div className="absolute inset-0 z-20 flex items-center justify-between p-4 sm:p-8 md:p-12">
        {/* Left Column: Metadata, Title, Description, and Actions */}
        <div 
          key={`content-${currentItem.id}-${safeIndex}`}
          className="flex flex-col justify-center max-w-2xl space-y-2.5 sm:space-y-4 animate-fadeIn"
        >
          {/* Badges row */}
          <div className="flex flex-wrap items-center gap-1.5 sm:gap-2">
            <div className="px-2.5 py-0.5 rounded-lg bg-[#10B981] text-black font-black text-[10px] sm:text-xs tracking-wider uppercase shadow-lg shadow-[#10B981]/30">
              LATEST RELEASE • {safeIndex + 1}/{slides.length}
            </div>
            {currentItem.vj && (
              <div className="px-2.5 py-0.5 rounded-lg bg-black/70 border border-[#10B981]/50 text-[#34D399] font-bold text-[10px] sm:text-xs">
                {currentItem.vj}
              </div>
            )}
            <span className="text-[11px] sm:text-xs font-semibold text-[#94A3B8]">
              1080p FHD • {currentItem.year || '2025'}
              {currentItem.genre ? ` • ${currentItem.genre.split('/').slice(0, 2).map(g => g.trim()).join(' • ')}` : ''}
              {currentItem.duration ? ` • ${currentItem.duration}` : ''}
            </span>
          </div>

          {/* Title */}
          <h1 className="text-xl sm:text-3xl md:text-5xl font-black text-white tracking-tight line-clamp-1 drop-shadow-2xl">
            {currentItem.title}
          </h1>

          {/* Overview */}
          <p className="text-xs sm:text-sm text-[#94A3B8] leading-relaxed line-clamp-2 sm:line-clamp-3 max-w-xl drop-shadow-md">
            {currentItem.description || `Ugandan translated release by ${currentItem.vj || 'Munowatch TV'}.`}
          </p>

          {/* Action Buttons row */}
          <div className="flex flex-wrap items-center gap-2 sm:gap-3 pt-1 sm:pt-2">
            <button
              onClick={() => onPlayClick(currentItem)}
              className="flex items-center gap-1.5 sm:gap-2 px-4 sm:px-8 py-2 sm:py-3.5 rounded-xl bg-[#10B981] hover:bg-[#34D399] text-black font-extrabold text-xs sm:text-base shadow-xl shadow-[#10B981]/30 transition-all hover:scale-105 active:scale-95 tv-card-focus touch-manipulation"
            >
              <Play className="w-3.5 h-3.5 sm:w-5 sm:h-5 fill-black" />
              <span>Play Now</span>
            </button>

            <button
              onClick={() => onDetailsClick(currentItem)}
              className="flex items-center gap-1.5 sm:gap-2 px-3 sm:px-6 py-2 sm:py-3.5 rounded-xl bg-[#0A192F] hover:bg-[#132845] text-white font-semibold text-xs sm:text-base border border-[#1E293B] hover:border-[#10B981]/50 transition-all hover:scale-105 active:scale-95 touch-manipulation"
            >
              <Info className="w-3.5 h-3.5 sm:w-4 sm:h-4 text-[#34D399]" />
              <span>Details</span>
            </button>

            <button
              onClick={() => onFavoriteClick(currentItem)}
              className="flex items-center gap-1.5 sm:gap-2 px-3 sm:px-5 py-2 sm:py-3.5 rounded-xl bg-[#0A192F] hover:bg-[#132845] text-white font-semibold text-xs sm:text-base border border-[#1E293B] hover:border-[#10B981]/50 transition-all hover:scale-105 active:scale-95 touch-manipulation"
            >
              {isCurrentFavorite ? (
                <>
                  <BookmarkCheck className="w-3.5 h-3.5 sm:w-4 sm:h-4 text-[#10B981]" />
                  <span className="text-[#10B981]">Saved</span>
                </>
              ) : (
                <>
                  <Bookmark className="w-3.5 h-3.5 sm:w-4 sm:h-4 text-[#94A3B8]" />
                  <span className="text-[#94A3B8]">Watchlist</span>
                </>
              )}
            </button>
          </div>
        </div>

        {/* Right Column: Inset Floating Poster Card (on large screens) */}
        {currentItem.posterUrl && (
          <div className="hidden lg:flex items-center justify-end z-20 flex-shrink-0 mr-4">
            <div
              key={`poster-${currentItem.id}-${safeIndex}`}
              onClick={() => onDetailsClick(currentItem)}
              className="w-40 xl:w-48 aspect-[2/3] rounded-2xl overflow-hidden shadow-2xl shadow-black/90 border-2 border-[#10B981]/40 cursor-pointer transform hover:scale-105 transition-all duration-300 animate-fadeIn bg-[#0A192F] group/poster"
              title={`View details for ${currentItem.title}`}
            >
              <img
                src={currentItem.posterUrl}
                alt={currentItem.title}
                className="w-full h-full object-cover group-hover/poster:brightness-105 transition-all"
              />
            </div>
          </div>
        )}
      </div>

      {/* 4. Left Previous Slide Navigation Button */}
      {slides.length > 1 && (
        <button
          onClick={prevSlide}
          className="absolute left-2 sm:left-3 top-1/2 -translate-y-1/2 z-30 w-8 h-8 sm:w-11 sm:h-11 rounded-full bg-black/60 hover:bg-[#10B981] text-white hover:text-black border border-white/10 hover:border-[#10B981] flex items-center justify-center opacity-70 sm:opacity-0 sm:group-hover:opacity-100 transition-all shadow-2xl backdrop-blur-md active:scale-95 touch-manipulation"
          title="Previous Movie"
        >
          <ChevronLeft className="w-4 h-4 sm:w-6 sm:h-6" />
        </button>
      )}

      {/* 5. Right Next Slide Navigation Button */}
      {slides.length > 1 && (
        <button
          onClick={nextSlide}
          className="absolute right-2 sm:right-3 top-1/2 -translate-y-1/2 z-30 w-8 h-8 sm:w-11 sm:h-11 rounded-full bg-black/60 hover:bg-[#10B981] text-white hover:text-black border border-white/10 hover:border-[#10B981] flex items-center justify-center opacity-70 sm:opacity-0 sm:group-hover:opacity-100 transition-all shadow-2xl backdrop-blur-md active:scale-95 touch-manipulation"
          title="Next Movie"
        >
          <ChevronRight className="w-4 h-4 sm:w-6 sm:h-6" />
        </button>
      )}

      {/* 6. Bottom Slide Indicator Dots / Segmented Bars */}
      {slides.length > 1 && (
        <div className="absolute bottom-2.5 right-2.5 sm:bottom-6 sm:right-8 z-30 flex items-center gap-1 sm:gap-2 bg-black/60 backdrop-blur-md px-2.5 sm:px-3.5 py-1 sm:py-1.5 rounded-full border border-white/10">
          {slides.map((s, idx) => (
            <button
              key={s.id || idx}
              onClick={() => setCurrentIndex(idx)}
              className={`h-1.5 sm:h-2 rounded-full transition-all duration-300 touch-manipulation ${
                idx === safeIndex
                  ? 'w-5 sm:w-8 bg-[#10B981] shadow-lg shadow-[#10B981]/50'
                  : 'w-1.5 sm:w-2 bg-white/30 hover:bg-white/60'
              }`}
              title={`Jump to slide ${idx + 1}: ${s.title}`}
            />
          ))}
        </div>
      )}
    </div>
  );
};
