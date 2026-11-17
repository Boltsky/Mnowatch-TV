import React, { useState, useEffect, useMemo } from 'react';
import { Play, ArrowLeft, Star, Clock, Server, Check, Sparkles, BookOpen, Bookmark, BookmarkCheck } from 'lucide-react';
import { MediaItem, Episode, PlaybackServer } from '../../types';
import { api } from '../../services/api';
import { TvMediaCard } from '../common/TvMediaCard';

interface ContentDetailsProps {
  item: MediaItem;
  allMovies?: MediaItem[];
  onPlay: (item: MediaItem, episode?: Episode, server?: PlaybackServer) => void;
  onBack: () => void;
  onSelectMovie?: (item: MediaItem) => void;
  onToggleWatchlist?: (item: MediaItem) => void;
  isInWatchlist?: boolean;
}

export const ContentDetails: React.FC<ContentDetailsProps> = ({
  item,
  allMovies = [],
  onPlay,
  onBack,
  onSelectMovie,
  onToggleWatchlist,
  isInWatchlist = false
}) => {
  const defaultProxyServer: PlaybackServer = {
    id: 'server-proxy',
    name: 'Server 2 (Munowatch Web Streamer - CORS Safe)',
    quality: '1080p Full HD',
    streamUrl: item.streamUrl ? api.getProxyStreamUrl(item.streamUrl) : '',
    isProxy: true
  };

  const [selectedServer, setSelectedServer] = useState<PlaybackServer>(
    item.servers?.find(s => s.isProxy) || item.servers?.[0] || defaultProxyServer
  );
  const [selectedSeason, setSelectedSeason] = useState<number>(1);
  const [recommendations, setRecommendations] = useState<MediaItem[]>([]);
  const [loadingRecs, setLoadingRecs] = useState<boolean>(false);

  // Recommendations: Find matching movies by VJ, Genre, or Sequel/counterpart
  useEffect(() => {
    let isCancelled = false;

    const findRecommendations = async () => {
      setLoadingRecs(true);
      const cleanVj = item.vj.toLowerCase().replace('vj', '').trim();
      const primaryGenre = item.genre.split(/[,/&]/)[0].trim().toLowerCase();

      // 1. Filter local cache first
      const localMatches = allMovies.filter(m => {
        if (m.id === item.id) return false;
        const vjMatch = cleanVj && m.vj.toLowerCase().includes(cleanVj);
        const genreMatch = primaryGenre && m.genre.toLowerCase().includes(primaryGenre);
        return vjMatch || genreMatch;
      });

      if (localMatches.length >= 8) {
        if (!isCancelled) {
          setRecommendations(localMatches.slice(0, 16));
          setLoadingRecs(false);
        }
        return;
      }

      // 2. Fetch live VJ/genre catalog if local pool is small
      try {
        const res = cleanVj
          ? await api.getMovies({ vj: item.vj, perPage: 16 })
          : await api.getMovies({ genre: primaryGenre || 'Action', perPage: 16 });

        if (!isCancelled && res.items && res.items.length > 0) {
          const combined = [...localMatches, ...res.items.filter(i => i.id !== item.id)];
          const seen = new Set<string>();
          const deduped: MediaItem[] = [];
          for (const m of combined) {
            const key = m.title.toLowerCase().replace(/[^a-z0-9]/g, '');
            if (!seen.has(key) && m.id !== item.id) {
              seen.add(key);
              deduped.push(m);
            }
          }
          setRecommendations(deduped.slice(0, 16));
        } else if (!isCancelled) {
          setRecommendations(localMatches);
        }
      } catch {
        if (!isCancelled) setRecommendations(localMatches);
      } finally {
        if (!isCancelled) setLoadingRecs(false);
      }
    };

    findRecommendations();

    return () => {
      isCancelled = true;
    };
  }, [item.id, item.vj, item.genre, allMovies]);

  const seasons = item.seasons || [];
  const currentSeason = seasons.find(s => s.seasonNumber === selectedSeason) || seasons[0];

  const handleCardClick = (target: MediaItem) => {
    if (onSelectMovie) {
      onSelectMovie(target);
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <div className="min-h-screen bg-[#010318] text-white pb-24 md:pb-16 animate-fadeIn">
      {/* Sticky Cinematic Top Navigation Bar */}
      <div className="sticky top-0 z-40 flex items-center justify-between px-4 sm:px-8 py-3.5 bg-[#010318]/85 backdrop-blur-md border-b border-[#132845]/70">
        <button
          onClick={onBack}
          className="flex items-center gap-2 px-3.5 py-1.5 rounded-xl bg-white/10 hover:bg-white/20 text-white font-semibold text-xs sm:text-sm border border-white/10 transition-all hover:scale-105 active:scale-95"
        >
          <ArrowLeft className="w-4 h-4 text-emerald-400" />
          <span>Back</span>
        </button>

        <div className="flex items-center gap-2">
          <div className="px-2.5 py-1 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 font-extrabold text-[11px] uppercase tracking-wider">
            MUNOWATCH TV
          </div>
          <span className="hidden sm:inline-block px-2.5 py-1 rounded-lg bg-white/5 text-zinc-400 text-xs font-medium border border-white/5">
            1080p FHD
          </span>
        </div>
      </div>

      {/* Hero Backdrop with Atmospheric Gradient Blend */}
      <div className="relative w-full h-[45vh] sm:h-[55vh] min-h-[320px] max-h-[500px] overflow-hidden">
        <img
          src={item.backdropUrl || item.posterUrl}
          alt={item.title}
          className="w-full h-full object-cover object-center filter brightness-[0.65] contrast-[1.1] scale-105"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-[#010318] via-[#010318]/70 to-transparent" />
        <div className="absolute inset-0 bg-radial-gradient from-transparent to-[#010318]/80 pointer-events-none" />
      </div>

      {/* Main Details Presentation matching Android TV DetailsScreen.kt */}
      <div className="max-w-7xl mx-auto px-4 sm:px-8 -mt-32 sm:-mt-44 relative z-10 space-y-10">
        <div className="flex flex-col md:flex-row gap-6 sm:gap-10 items-start">
          {/* Main Elevated Poster */}
          <div className="w-48 sm:w-56 md:w-72 aspect-[2/3] rounded-2xl overflow-hidden shadow-2xl shadow-black/80 border-2 border-emerald-500/40 flex-shrink-0 bg-[#0A192F] relative group mx-auto md:mx-0">
            <img src={item.posterUrl} alt={item.title} className="w-full h-full object-cover" />
            <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity flex items-end p-4">
              <span className="text-xs font-bold text-emerald-400">Ugandan Translated</span>
            </div>
          </div>

          {/* Metadata & Actions */}
          <div className="flex-1 space-y-4 pt-2">
            {/* Tag Pills */}
            <div className="flex flex-wrap items-center gap-2">
              <span className="px-3 py-1 rounded-lg bg-emerald-500 text-black font-extrabold text-xs uppercase tracking-wider shadow-lg shadow-emerald-500/25">
                {item.vj}
              </span>
              <span className="px-2.5 py-1 rounded-lg bg-white/10 text-yellow-400 font-bold text-xs flex items-center gap-1 border border-white/10">
                <Star className="w-3.5 h-3.5 fill-yellow-400" />
                {item.rating || '8.8'}
              </span>
              {item.year && (
                <span className="px-2.5 py-1 rounded-lg bg-white/5 text-zinc-300 text-xs font-medium border border-white/5">
                  {item.year}
                </span>
              )}
              {item.duration && (
                <span className="px-2.5 py-1 rounded-lg bg-white/5 text-zinc-300 text-xs font-medium flex items-center gap-1 border border-white/5">
                  <Clock className="w-3.5 h-3.5 text-zinc-400" />
                  {item.duration}
                </span>
              )}
              <span className="px-2.5 py-1 rounded-lg bg-emerald-950/80 text-emerald-400 text-xs font-bold border border-emerald-500/30">
                1080p Full HD
              </span>
            </div>

            {/* Title */}
            <h1 className="text-2xl sm:text-4xl md:text-5xl font-black tracking-tight text-white leading-tight">
              {item.title}
            </h1>

            {/* Genre */}
            <p className="text-zinc-400 text-sm sm:text-base font-semibold">
              {item.genre}
            </p>

            {/* MOVIE DESCRIPTION Surface Container matching Android TV SurfaceBox */}
            <div className="pt-2 space-y-2">
              <div className="flex items-center gap-2 text-emerald-400 font-bold text-xs tracking-wider uppercase">
                <BookOpen className="w-4 h-4" />
                <span>Movie Description</span>
              </div>

              <div className="p-4 sm:p-5 rounded-2xl bg-[#0A192F]/70 border border-[#132845] backdrop-blur-sm">
                <p className="text-zinc-200 text-sm sm:text-base leading-relaxed">
                  {item.description || `${item.title} is a translated blockbuster brought to you in Luganda by ${item.vj}.`}
                </p>
              </div>
            </div>

            {/* Streaming Server Selector Pills */}
            {item.servers && item.servers.length > 0 && (
              <div className="pt-2 space-y-2">
                <span className="text-xs uppercase font-bold text-zinc-400 flex items-center gap-1.5">
                  <Server className="w-3.5 h-3.5 text-emerald-400" />
                  Select Media Server:
                </span>
                <div className="flex flex-wrap gap-2">
                  {item.servers.map((srv) => (
                    <button
                      key={srv.id}
                      onClick={() => setSelectedServer(srv)}
                      className={`px-3.5 py-2 rounded-xl text-xs font-bold flex items-center gap-2 border transition-all ${
                        selectedServer?.id === srv.id
                          ? 'bg-emerald-500/20 text-emerald-400 border-emerald-500 shadow-lg shadow-emerald-500/20 scale-105'
                          : 'bg-[#0A192F] text-zinc-400 border-[#132845] hover:border-zinc-500'
                      }`}
                    >
                      {selectedServer?.id === srv.id && <Check className="w-3.5 h-3.5 text-emerald-400" />}
                      <span>{srv.name}</span>
                      <span className="text-[10px] text-zinc-400 font-normal">({srv.quality})</span>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Primary Actions: Start Watching & Watchlist */}
            <div className="pt-4 flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
              {!item.isSeries && (
                <button
                  onClick={() => onPlay(item, undefined, selectedServer)}
                  className="flex items-center justify-center gap-3 px-8 py-3.5 sm:py-4 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-black font-extrabold text-sm sm:text-base shadow-xl shadow-emerald-500/30 transition-all hover:scale-105 active:scale-95"
                >
                  <Play className="w-5 h-5 fill-black" />
                  <span>Start Watching</span>
                </button>
              )}

              {onToggleWatchlist && (
                <button
                  onClick={() => onToggleWatchlist(item)}
                  className={`flex items-center justify-center gap-2.5 px-5 py-3.5 sm:py-4 rounded-xl text-sm font-bold border transition-all ${
                    isInWatchlist
                      ? 'bg-emerald-500/15 text-emerald-400 border-emerald-500'
                      : 'bg-[#0A192F] text-zinc-300 border-[#132845] hover:border-zinc-500 hover:text-white'
                  }`}
                >
                  {isInWatchlist ? (
                    <>
                      <BookmarkCheck className="w-4 h-4 text-emerald-400" />
                      <span>In Watchlist</span>
                    </>
                  ) : (
                    <>
                      <Bookmark className="w-4 h-4 text-zinc-400" />
                      <span>Add to Watchlist</span>
                    </>
                  )}
                </button>
              )}
            </div>
          </div>
        </div>

        {/* TV Series Season & Episode Picker (if series) */}
        {item.isSeries && seasons.length > 0 && (
          <div className="mt-12 space-y-6 pt-6 border-t border-[#132845]">
            <div className="flex items-center justify-between">
              <h2 className="text-xl sm:text-2xl font-black text-white">Episodes</h2>
              {seasons.length > 1 && (
                <div className="flex gap-2">
                  {seasons.map(s => (
                    <button
                      key={s.seasonNumber}
                      onClick={() => setSelectedSeason(s.seasonNumber)}
                      className={`px-3.5 py-1.5 rounded-xl text-xs font-bold transition-all border ${
                        selectedSeason === s.seasonNumber
                          ? 'bg-emerald-500 text-black border-emerald-500'
                          : 'bg-[#0A192F] text-zinc-400 border-[#132845] hover:text-white'
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
                  className="group flex gap-3.5 p-3.5 rounded-2xl bg-[#0A192F] border border-[#132845] hover:border-emerald-500/60 cursor-pointer transition-all hover:scale-[1.02] active:scale-98"
                >
                  <div className="relative w-28 sm:w-32 aspect-video rounded-xl overflow-hidden bg-black/60 flex-shrink-0">
                    <img
                      src={ep.thumbnailUrl || item.backdropUrl || item.posterUrl}
                      alt={ep.title}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                    />
                    <div className="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                      <Play className="w-6 h-6 fill-emerald-400 text-emerald-400" />
                    </div>
                  </div>
                  <div className="flex flex-col justify-center overflow-hidden">
                    <span className="text-[11px] font-extrabold text-emerald-400 uppercase tracking-wider">
                      Episode {ep.episodeNumber}
                    </span>
                    <h3 className="text-sm font-bold text-zinc-100 group-hover:text-emerald-300 truncate">
                      {ep.title}
                    </h3>
                    {ep.duration && (
                      <span className="text-[11px] text-zinc-400 mt-1">
                        {ep.duration}
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* MORE LIKE THIS Section matching Android TV DetailsScreen.kt (Lines 523-566) */}
        {recommendations.length > 0 && (
          <div className="mt-14 pt-8 border-t border-[#132845] space-y-5 animate-fadeIn">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <Sparkles className="w-5 h-5 text-emerald-400" />
                <h2 className="text-lg sm:text-xl font-black text-white uppercase tracking-wider">
                  More Like This
                </h2>
                <span className="text-xs font-semibold text-[#94A3B8]">
                  • {recommendations.length} Related Titles
                </span>
              </div>
            </div>

            {/* Horizontal Scrolling Row of Recommended Movies */}
            <div className="flex items-center gap-3 sm:gap-4 overflow-x-auto no-scrollbar py-2 -mx-4 px-4 sm:mx-0 sm:px-0">
              {recommendations.map((recMovie) => (
                <TvMediaCard
                  key={recMovie.id}
                  item={recMovie}
                  onClick={() => handleCardClick(recMovie)}
                  cardWidth="w-32 sm:w-40 md:w-48"
                />
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
