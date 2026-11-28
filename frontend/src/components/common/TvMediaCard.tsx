import React from 'react';
import { Play, Star } from 'lucide-react';
import { MediaItem } from '../../types';

interface TvMediaCardProps {
  item: MediaItem;
  onClick: () => void;
  cardWidth?: string;
}

export const TvMediaCard: React.FC<TvMediaCardProps> = ({ item, onClick, cardWidth = "w-32 sm:w-36 md:w-44" }) => {
  return (
    <div
      onClick={onClick}
      className={`group relative flex-shrink-0 ${cardWidth} cursor-pointer select-none tv-card-focus`}
    >
      {/* Poster box with 8dp rounded corner and elevated surface */}
      <div className="relative aspect-[2/3] w-full rounded-lg overflow-hidden bg-[#132845] border border-transparent shadow-md transition-all">
        <img
          src={item.posterUrl}
          alt={item.title}
          loading="lazy"
          className="w-full h-full object-cover object-center group-hover:brightness-105"
          onError={(e) => {
            (e.target as HTMLElement).style.display = 'none';
          }}
        />

        {/* VJ Badge top-left */}
        {item.vj && (
          <div className="absolute top-2 left-2 z-10">
            <span className="px-1.5 py-0.5 rounded bg-black/75 backdrop-blur-sm text-[#34D399] border border-[#10B981]/50 text-[10px] font-bold uppercase tracking-wider">
              {item.vj}
            </span>
          </div>
        )}

        {/* Rating Gold top-right */}
        <div className="absolute top-2 right-2 z-10">
          <span className="px-1.5 py-0.5 rounded bg-black/75 backdrop-blur-sm text-[#FBBF24] text-[10px] font-bold flex items-center gap-0.5 border border-white/10">
            <Star className="w-2.5 h-2.5 fill-[#FBBF24]" />
            {item.rating || '8.5'}
          </span>
        </div>

        {/* Mobile translucent touch play indicator */}
        <div className="absolute bottom-2 right-2 sm:hidden z-10 w-7 h-7 rounded-full bg-black/45 backdrop-blur-md border border-white/30 text-white/90 flex items-center justify-center shadow-lg">
          <Play className="w-3 h-3 fill-current ml-0.5" />
        </div>

        {/* Play overlay indicator on hover */}
        <div className="absolute inset-0 bg-gradient-to-t from-[#010318]/90 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity flex items-end p-2.5">
          <span className="text-[11px] font-bold text-[#10B981]">
            {item.isSeries ? 'View Series' : 'Watch Movie'}
          </span>
        </div>
      </div>

      {/* Metadata text below card */}
      <div className="mt-1.5 px-0.5">
        <h4 className="text-xs sm:text-sm font-semibold text-[#F8FAFC] group-hover:text-[#34D399] truncate transition-colors">
          {item.title}
        </h4>
        <div className="flex items-center gap-1.5 text-[11px] text-[#94A3B8] truncate mt-0.5">
          {item.year && <span>{item.year}</span>}
          {item.year && item.genre && <span>•</span>}
          <span className="truncate">{item.genre}</span>
        </div>
      </div>
    </div>
  );
};
