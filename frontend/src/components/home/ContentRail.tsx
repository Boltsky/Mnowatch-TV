import React, { useRef } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { MediaCard } from './MediaCard';
import { MediaItem } from '../../types';

interface ContentRailProps {
  title: string;
  items: MediaItem[];
  onItemClick: (item: MediaItem) => void;
  onPlay: (item: MediaItem) => void;
}

export const ContentRail: React.FC<ContentRailProps> = ({ title, items, onItemClick, onPlay }) => {
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  const scroll = (direction: 'left' | 'right') => {
    if (scrollContainerRef.current) {
      const scrollAmount = direction === 'left' ? -400 : 400;
      scrollContainerRef.current.scrollBy({ left: scrollAmount, behavior: 'smooth' });
    }
  };

  if (!items || items.length === 0) return null;

  return (
    <div className="relative my-6 group">
      {/* Rail Title */}
      <div className="flex items-center justify-between mb-3 px-1">
        <h2 className="text-base sm:text-xl font-bold text-white tracking-wide">
          {title}
        </h2>
      </div>

      {/* Chevron left */}
      <button
        onClick={() => scroll('left')}
        className="hidden md:flex absolute -left-4 top-1/2 -translate-y-1/2 z-20 w-10 h-10 rounded-full bg-black/80 hover:bg-emerald-500 hover:text-black text-white border border-muno-border items-center justify-center opacity-0 group-hover:opacity-100 transition-all shadow-xl"
      >
        <ChevronLeft className="w-6 h-6" />
      </button>

      {/* Scrollable Container with touch momentum */}
      <div
        ref={scrollContainerRef}
        className="flex items-center gap-3 sm:gap-4 overflow-x-auto no-scrollbar scroll-smooth py-2 px-1"
        style={{ WebkitOverflowScrolling: 'touch' }}
      >
        {items.map((item) => (
          <MediaCard
            key={item.id}
            item={item}
            onClick={onItemClick}
            onPlay={onPlay}
          />
        ))}
      </div>

      {/* Chevron right */}
      <button
        onClick={() => scroll('right')}
        className="hidden md:flex absolute -right-4 top-1/2 -translate-y-1/2 z-20 w-10 h-10 rounded-full bg-black/80 hover:bg-emerald-500 hover:text-black text-white border border-muno-border items-center justify-center opacity-0 group-hover:opacity-100 transition-all shadow-xl"
      >
        <ChevronRight className="w-6 h-6" />
      </button>
    </div>
  );
};
