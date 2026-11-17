import React, { useState } from 'react';
import { Play, ArrowLeft, Star, Clock, Server, Check } from 'lucide-react';
import { MediaItem, Episode, PlaybackServer } from '../../types';

interface ContentDetailsProps {
  item: MediaItem;
  onPlay: (item: MediaItem, episode?: Episode, server?: PlaybackServer) => void;
  onBack: () => void;
}

export const ContentDetails: React.FC<ContentDetailsProps> = ({ item, onPlay, onBack }) => {
  const [selectedServer, setSelectedServer] = useState<PlaybackServer | undefined>(
    item.servers?.[0]
  );
  const [selectedSeason, setSelectedSeason] = useState<number>(1);

  const seasons = item.seasons || [];
  const currentSeason = seasons.find(s => s.seasonNumber === selectedSeason) || seasons[0];

  return (
    <div className="min-h-screen bg-[#0a0f0d] text-white pb-24 md:pb-12 animate-fadeIn">
      {/* Back button header */}
      <div className="sticky top-0 z-30 flex items-center px-4 sm:px-8 py-4 bg-[#0a0f0d]/80 backdrop-blur-md border-b border-muno-border/50">
        <button
          onClick={onBack}
          className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-white/10 hover:bg-white/20 text-white font-medium text-sm transition-all"
        >
          <ArrowLeft className="w-4 h-4" />
          Back
        </button>
      </div>

      {/* Hero Banner Section */}
      <div className="relative w-full h-[40vh] sm:h-[50vh] min-h-[300px] overflow-hidden">
        <img
          src={item.backdropUrl || item.posterUrl}
          alt={item.title}
          className="w-full h-full object-cover object-center filter brightness-75"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-[#0a0f0d] via-[#0a0f0d]/60 to-transparent" />
      </div>

      {/* Main Content Details */}
      <div className="max-w-6xl mx-auto px-4 sm:px-8 -mt-24 sm:-mt-32 relative z-10">
        <div className="flex flex-col md:flex-row gap-6 md:gap-10 items-start">
          {/* Poster */}
          <div className="w-40 sm:w-52 md:w-64 aspect-[2/3] rounded-2xl overflow-hidden shadow-2xl border-2 border-emerald-500/40 flex-shrink-0 bg-muno-card">
            <img src={item.posterUrl} alt={item.title} className="w-full h-full object-cover" />
          </div>

          {/* Metadata */}
          <div className="flex-1 space-y-4">
            <div className="flex flex-wrap items-center gap-2">
              <span className="px-3 py-1 rounded-md bg-emerald-500 text-black font-extrabold text-xs uppercase tracking-wider">
                {item.vj}
              </span>
              <span className="px-2.5 py-1 rounded bg-white/10 text-yellow-400 font-bold text-xs flex items-center gap-1">
                <Star className="w-3.5 h-3.5 fill-yellow-400" />
                {item.rating || '8.5'}
              </span>
              {item.year && (
                <span className="px-2.5 py-1 rounded bg-white/5 text-zinc-300 text-xs">
                  {item.year}
                </span>
              )}
              {item.duration && (
                <span className="px-2.5 py-1 rounded bg-white/5 text-zinc-300 text-xs flex items-center gap-1">
                  <Clock className="w-3.5 h-3.5" />
                  {item.duration}
                </span>
              )}
              <span className="px-2.5 py-1 rounded bg-emerald-950 text-emerald-400 text-xs border border-emerald-500/30">
                1080p FHD
              </span>
            </div>

            <h1 className="text-2xl sm:text-4xl font-black tracking-tight text-white">
              {item.title}
            </h1>

            <p className="text-zinc-400 text-sm font-medium">
              {item.genre}
            </p>

            <p className="text-zinc-300 text-sm sm:text-base leading-relaxed">
              {item.description}
            </p>

            {/* Server Selector */}
            {item.servers && item.servers.length > 0 && (
              <div className="pt-2 space-y-2">
                <span className="text-xs uppercase font-bold text-zinc-400 flex items-center gap-1.5">
                  <Server className="w-3.5 h-3.5 text-emerald-400" />
                  Streaming Server:
                </span>
                <div className="flex flex-wrap gap-2">
                  {item.servers.map((srv) => (
                    <button
                      key={srv.id}
                      onClick={() => setSelectedServer(srv)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 border transition-all ${
                        selectedServer?.id === srv.id
                          ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500 shadow-md'
                          : 'bg-white/5 text-zinc-400 border-white/10 hover:border-white/30'
                      }`}
                    >
                      {selectedServer?.id === srv.id && <Check className="w-3 h-3 text-emerald-400" />}
                      {srv.name} ({srv.quality})
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Main Play CTA */}
            {!item.isSeries && (
              <div className="pt-4">
                <button
                  onClick={() => onPlay(item, undefined, selectedServer)}
                  className="flex items-center gap-3 px-8 py-3.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-black font-extrabold text-base shadow-xl shadow-emerald-500/30 transition-all active:scale-95"
                >
                  <Play className="w-5 h-5 fill-black" />
                  Play Full Movie
                </button>
              </div>
            )}
          </div>
        </div>

        {/* TV Series Season & Episode Picker */}
        {item.isSeries && seasons.length > 0 && (
          <div className="mt-12 space-y-6">
            <div className="flex items-center justify-between border-b border-muno-border pb-3">
              <h2 className="text-xl font-bold text-white">Episodes</h2>
              {seasons.length > 1 && (
                <div className="flex gap-2">
                  {seasons.map(s => (
                    <button
                      key={s.seasonNumber}
                      onClick={() => setSelectedSeason(s.seasonNumber)}
                      className={`px-3 py-1 rounded-lg text-xs font-bold transition-all ${
                        selectedSeason === s.seasonNumber
                          ? 'bg-emerald-500 text-black'
                          : 'bg-white/5 text-zinc-400 hover:text-white'
                      }`}
                    >
                      Season {s.seasonNumber}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
              {currentSeason?.episodes.map(ep => (
                <div
                  key={ep.episodeNumber}
                  onClick={() => onPlay(item, ep, selectedServer)}
                  className="group flex gap-3 p-3 rounded-xl bg-muno-card border border-muno-border hover:border-emerald-500/50 cursor-pointer transition-all hover:scale-[1.02] active:scale-98"
                >
                  <div className="relative w-28 aspect-video rounded-lg overflow-hidden bg-black/50 flex-shrink-0">
                    <img
                      src={ep.thumbnailUrl || item.backdropUrl || item.posterUrl}
                      alt={ep.title}
                      className="w-full h-full object-cover"
                    />
                    <div className="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                      <Play className="w-6 h-6 fill-emerald-400 text-emerald-400" />
                    </div>
                  </div>
                  <div className="flex flex-col justify-center overflow-hidden">
                    <span className="text-[11px] font-bold text-emerald-400">
                      Episode {ep.episodeNumber}
                    </span>
                    <h3 className="text-sm font-semibold text-zinc-200 group-hover:text-white truncate">
                      {ep.title}
                    </h3>
                    {ep.duration && (
                      <span className="text-[11px] text-zinc-500 mt-1">
                        {ep.duration}
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
