import React from 'react';
import { Play, Star } from 'lucide-react';
import { MediaItem } from '../../types';

interface MediaCardProps {
  item: MediaItem;
  onClick: (item: MediaItem) => void;
  onPlay: (item: MediaItem) => void;
}

export const MediaCard: React.FC<MediaCardProps> = ({ item, onClick, onPlay }) => {
  return (
    <div
      onClick={() => onClick(item)}
      className="group relative flex-shrink-0 w-32 sm:w-36 md:w-44 cursor-pointer select-none transition-transform duration-300 hover:scale-105 active:scale-95"
    >
      {/* Poster image container */}
      <div className="relative aspect-[2/3] w-full rounded-xl overflow-hidden bg-muno-card border border-muno-border group-hover:border-emerald-500/60 shadow-lg group-hover:shadow-emerald-500/20 transition-all">
        <img
          src={item.posterUrl}
          alt={item.title}
          loading="lazy"
          className="w-full h-full object-cover object-center group-hover:brightness-110 transition-all duration-300"
          onError={(e) => {
            // Fallback image placeholder
            (e.target as HTMLElement).style.display = 'none';
          }}
        />

        {/* Quality and VJ Badges */}
        <div className="absolute top-2 left-2 flex flex-col gap-1 z-10">
          <span className="px-1.5 py-0.5 rounded bg-emerald-500 text-black text-[10px] font-black uppercase tracking-wider shadow">
            {item.vj}
          </span>
        </div>

        <div className="absolute top-2 right-2 z-10">
          <span className="px-1.5 py-0.5 rounded bg-black/70 backdrop-blur-sm text-yellow-400 text-[10px] font-bold flex items-center gap-0.5 border border-white/10">
            <Star className="w-2.5 h-2.5 fill-yellow-400" />
            {item.rating || '8.5'}
          </span>
        </div>

        {/* Hover overlay with Quick Play button */}
        <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/30 to-transparent opacity-0 group-hover:opacity-100 transition-opacity flex flex-col justify-end p-3">
          <button
            onClick={(e) => {
              e.stopPropagation();
              onPlay(item);
            }}
            className="w-full py-2 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-black font-extrabold text-xs flex items-center justify-center gap-1.5 shadow-lg active:scale-95"
          >
            <Play className="w-3.5 h-3.5 fill-black" />
            Watch Now
          </button>
        </div>
      </div>

      {/* Title & Metadata */}
      <div className="mt-2 space-y-0.5">
        <h3 className="text-xs sm:text-sm font-semibold text-zinc-200 group-hover:text-emerald-400 line-clamp-1 transition-colors">
          {item.title}
        </h3>
        <p className="text-[11px] text-zinc-400 line-clamp-1">
          {item.genre}
        </p>
      </div>
    </div>
  );
};
