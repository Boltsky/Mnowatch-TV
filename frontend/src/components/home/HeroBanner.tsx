import React from 'react';
import { Play, Info, Star, Clock } from 'lucide-react';
import { MediaItem } from '../../types';

interface HeroBannerProps {
  item: MediaItem;
  onPlay: (item: MediaItem) => void;
  onDetails: (item: MediaItem) => void;
}

export const HeroBanner: React.FC<HeroBannerProps> = ({ item, onPlay, onDetails }) => {
  return (
    <div className="relative w-full h-[60vh] sm:h-[68vh] md:h-[75vh] min-h-[420px] max-h-[680px] overflow-hidden rounded-2xl md:rounded-3xl border border-muno-border/60 shadow-2xl mb-8 group">
      {/* Backdrop Image with Multi-Gradient Overlay */}
      <img
        src={item.backdropUrl || item.posterUrl}
        alt={item.title}
        className="absolute inset-0 w-full h-full object-cover object-center filter brightness-90 group-hover:scale-105 transition-transform duration-700 ease-out"
      />
      <div className="absolute inset-0 bg-gradient-to-t from-[#0a0f0d] via-[#0a0f0d]/60 to-transparent" />
      <div className="absolute inset-0 bg-gradient-to-r from-[#0a0f0d] via-[#0a0f0d]/70 to-transparent w-full md:w-2/3" />

      {/* Content overlay */}
      <div className="absolute bottom-0 left-0 right-0 p-5 sm:p-8 md:p-12 flex flex-col justify-end max-w-3xl space-y-3 sm:space-y-4">
        {/* Badges row */}
        <div className="flex flex-wrap items-center gap-2">
          <span className="px-2.5 py-1 rounded-md bg-emerald-500 text-black font-extrabold text-xs uppercase tracking-wider shadow-lg shadow-emerald-500/30">
            {item.vj}
          </span>
          <span className="px-2 py-0.5 rounded bg-white/10 backdrop-blur-sm text-zinc-300 font-semibold text-xs border border-white/10 flex items-center gap-1">
            <Star className="w-3 h-3 text-yellow-400 fill-yellow-400" />
            {item.rating || '8.5'}
          </span>
          {item.year && (
            <span className="px-2 py-0.5 rounded bg-white/5 text-zinc-400 text-xs">
              {item.year}
            </span>
          )}
          {item.duration && (
            <span className="px-2 py-0.5 rounded bg-white/5 text-zinc-400 text-xs flex items-center gap-1">
              <Clock className="w-3 h-3" />
              {item.duration}
            </span>
          )}
          <span className="px-2 py-0.5 rounded bg-emerald-950/80 text-emerald-400 text-xs border border-emerald-500/40">
            1080p FHD
          </span>
        </div>

        {/* Title */}
        <h1 className="text-2xl sm:text-4xl md:text-5xl font-black tracking-tight text-white drop-shadow-md">
          {item.title}
        </h1>

        {/* Overview */}
        <p className="text-zinc-300 text-xs sm:text-sm md:text-base line-clamp-2 sm:line-clamp-3 leading-relaxed max-w-2xl drop-shadow">
          {item.description}
        </p>

        {/* Action Buttons */}
        <div className="flex items-center gap-3 pt-2">
          <button
            onClick={() => onPlay(item)}
            className="flex items-center gap-2.5 px-6 sm:px-8 py-3 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-black font-extrabold text-sm sm:text-base transition-all shadow-lg shadow-emerald-500/25 active:scale-95"
          >
            <Play className="w-5 h-5 fill-black" />
            Play Stream
          </button>
          <button
            onClick={() => onDetails(item)}
            className="flex items-center gap-2 px-4 sm:px-6 py-3 rounded-xl bg-white/10 hover:bg-white/20 backdrop-blur-md text-white font-semibold text-sm sm:text-base border border-white/10 transition-all active:scale-95"
          >
            <Info className="w-5 h-5" />
            More Info
          </button>
        </div>
      </div>
    </div>
  );
};
