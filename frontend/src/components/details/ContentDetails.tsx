import React, { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { 
  Play, 
  ArrowLeft, 
  Star, 
  Clock, 
  Server, 
  Check, 
  Sparkles, 
  BookOpen, 
  Bookmark, 
  BookmarkCheck, 
  Headphones, 
  Shield, 
  Film, 
  Loader2, 
  AlertCircle, 
  Tv,
  Download,
  CheckCircle2,
  X,
  Radio,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';
import { MediaItem, Episode, PlaybackServer, StreamQualityOption } from '../../types';
import { api, isTrailerOrIntroUrl } from '../../services/api';
import { isVjMatch } from '../../utils/vjUtils';
import { offlineStorage, ActiveDownload } from '../../services/offlineStorage.service';
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
  item: initialItem,
  allMovies = [],
  onPlay,
  onBack,
  onSelectMovie,
  onToggleWatchlist,
  isInWatchlist = false
}) => {
  const [movie, setMovie] = useState<MediaItem>(initialItem);
  const [loadingDetails, setLoadingDetails] = useState<boolean>(false);

  const defaultProxyServer: PlaybackServer = {
    id: 'server-proxy',
    name: 'Server 2 (Munowatch Web Streamer - CORS Safe)',
    quality: '1080p Full HD',
    streamUrl: initialItem.streamUrl ? api.getProxyStreamUrl(initialItem.streamUrl) : '',
    isProxy: true
  };

  const [selectedServer, setSelectedServer] = useState<PlaybackServer>(
    initialItem.servers?.find(s => s.isProxy) || initialItem.servers?.[0] || defaultProxyServer
  );
  const [selectedSeason, setSelectedSeason] = useState<number>(1);
  const [recommendations, setRecommendations] = useState<MediaItem[]>([]);
  const [loadingRecs, setLoadingRecs] = useState<boolean>(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [downloadedIds, setDownloadedIds] = useState<Set<string>>(new Set());
  const [activeDownloads, setActiveDownloads] = useState<ActiveDownload[]>([]);
  const [estimatedFileSize, setEstimatedFileSize] = useState<string>('');
  const [qualities, setQualities] = useState<StreamQualityOption[]>([]);
  const [selectedQuality, setSelectedQuality] = useState<StreamQualityOption | null>(null);

  const recScrollRef = useRef<HTMLDivElement>(null);

  const scrollRecs = (direction: 'left' | 'right') => {
    if (recScrollRef.current) {
      const scrollAmount = direction === 'left' ? -480 : 480;
      recScrollRef.current.scrollBy({ left: scrollAmount, behavior: 'smooth' });
    }
  };

  const refreshDownloadedStatus = useCallback(async () => {
    try {
      const list = await offlineStorage.getAllOfflineMedia();
      setDownloadedIds(new Set(list.map(i => i.id)));
    } catch {}
  }, []);

  useEffect(() => {
    refreshDownloadedStatus();

    const unsub = offlineStorage.subscribeActiveDownloads((dls) => {
      setActiveDownloads([...dls]);
      if (dls.some(d => d.status === 'completed')) {
        refreshDownloadedStatus();
      }
    });

    return () => unsub();
  }, [refreshDownloadedStatus]);

  // Resolve video qualities and file size on mount or when movie changes
  useEffect(() => {
    let isMounted = true;
    api.resolveStream(movie.id, movie.title, movie.vj).then(res => {
      if (!isMounted) return;

      if (res.qualities && res.qualities.length > 0) {
        setQualities(res.qualities);
        // Prefer 1080p Full HD by default, fallback to Data Saver
        const fhd = res.qualities.find(q => q.id === 'fhd');
        const defaultQ = fhd || res.qualities[0];
        setSelectedQuality(defaultQ);
        if (defaultQ.fileSizeFormatted) {
          setEstimatedFileSize(defaultQ.fileSizeFormatted);
        }
      } else if (res.fileSizeFormatted) {
        setEstimatedFileSize(res.fileSizeFormatted);
      }

      if (res.servers && res.servers.length > 0) {
        setMovie(prev => ({
          ...prev,
          servers: res.servers,
          streamUrl: res.streamUrl || prev.streamUrl
        }));
        const matchingServer = res.servers.find(s => s.tier === 'fhd' && s.isProxy) ||
                              res.servers.find(s => s.tier === 'fhd') ||
                              res.servers.find(s => s.isProxy) ||
                              res.servers[0];
        setSelectedServer(matchingServer);
      }
    }).catch(() => {});

    return () => { isMounted = false; };
  }, [movie.id, movie.title, movie.vj]);

  const handleSelectQuality = (q: StreamQualityOption) => {
    setSelectedQuality(q);
    if (q.fileSizeFormatted) {
      setEstimatedFileSize(q.fileSizeFormatted);
    }
    if (movie.servers && movie.servers.length > 0) {
      const match = movie.servers.find(s => s.tier === q.id && s.isProxy) ||
                    movie.servers.find(s => s.tier === q.id) ||
                    movie.servers[0];
      if (match) setSelectedServer(match);
    }
  };

  const isMovieDownloaded = downloadedIds.has(movie.id);
  const getEpDownloadId = (ep: Episode) => `${movie.id}_s${selectedSeason || 1}_e${ep.episodeNumber}`;
  const isEpDownloaded = (ep: Episode) => downloadedIds.has(getEpDownloadId(ep)) || (ep.id ? downloadedIds.has(ep.id) : false);
  const getEpActiveDownload = (ep: Episode) => activeDownloads.find(d => d.id === getEpDownloadId(ep) || d.id === ep.id);
  const currentMovieDownload = activeDownloads.find(d => d.mediaId === movie.id && !d.episodeNumber);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 4000);
  };

  const handleDirectDeviceSave = (target: { item: MediaItem; episode?: Episode }) => {
    const rawDirect = selectedQuality?.directUrl || (selectedServer && !selectedServer.isProxy ? selectedServer.streamUrl : undefined);
    const directCandidate = rawDirect && !rawDirect.includes('/stream/proxy') && !rawDirect.includes('preview.php')
      ? rawDirect
      : undefined;

    const cleanTitle = (target.item.title || 'Movie').replace(/[<>:"/\\|?*]+/g, '').trim();
    const cleanVj = (target.item.vj || '').replace(/[<>:"/\\|?*]+/g, '').trim();
    const vjPart = cleanVj ? ` (${cleanVj})` : '';
    let boltFilename = '';
    if (target.episode) {
      const sNum = String(target.episode.seasonNumber || 1).padStart(2, '0');
      const eNum = String(target.episode.episodeNumber).padStart(2, '0');
      boltFilename = `${cleanTitle} - S${sNum}E${eNum}${vjPart} - Bolt.mp4`;
    } else {
      boltFilename = `${cleanTitle}${vjPart} - Bolt.mp4`;
    }

    const downloadUrl = api.getDownloadUrl({
      id: target.episode?.id || target.item.id,
      title: target.item.title,
      vj: target.item.vj,
      season: target.episode?.seasonNumber,
      episode: target.episode?.episodeNumber,
      filename: boltFilename,
      url: directCandidate,
      quality: selectedQuality?.id || 'fhd'
    });

    const targetKey = target.episode ? getEpDownloadId(target.episode) : target.item.id;
    setDownloadedIds(prev => new Set(prev).add(targetKey));

    showToast(`Downloading "${boltFilename}" to Local Storage...`);

    // 1. Direct browser download trigger without blank tab popups
    const a = document.createElement('a');
    a.href = downloadUrl;
    a.setAttribute('download', boltFilename);
    document.body.appendChild(a);
    a.click();
    setTimeout(() => {
      try { document.body.removeChild(a); } catch {}
    }, 2000);

    // 2. Fallback iframe for older mobile webviews
    const iframe = document.createElement('iframe');
    iframe.style.display = 'none';
    iframe.src = downloadUrl;
    document.body.appendChild(iframe);
    setTimeout(() => {
      try { document.body.removeChild(iframe); } catch {}
    }, 60000);
  };

  // 1. Fetch Genuine Individual Movie Details on Mount (matching Android getMovieDetails)
  useEffect(() => {
    let isCancelled = false;
    setMovie(initialItem);

    const loadRichDetails = async () => {
      setLoadingDetails(true);
      try {
        const fullDetails = await api.getMovieDetails(initialItem.id, initialItem.title);
        if (!isCancelled && fullDetails) {
          const cleanServers = fullDetails.servers?.filter(s => s.streamUrl && !isTrailerOrIntroUrl(s.streamUrl, fullDetails.title || initialItem.title) && !s.streamUrl.includes('preview.php')) || [];
          const isTrailer = isTrailerOrIntroUrl(fullDetails.streamUrl, fullDetails.title || initialItem.title);
          const sanitizedStreamUrl = isTrailer || (fullDetails.streamUrl && fullDetails.streamUrl.includes('preview.php')) ? '' : (fullDetails.streamUrl || '');
          const isAvailable = fullDetails.isStreamAvailable !== false && !isTrailer && (sanitizedStreamUrl.length > 0 || cleanServers.length > 0 || fullDetails.id.startsWith('tk_'));

          setMovie(prev => ({
            ...prev,
            ...fullDetails,
            streamUrl: sanitizedStreamUrl || prev.streamUrl,
            servers: cleanServers.length > 0 ? cleanServers : prev.servers,
            isStreamAvailable: isAvailable,
            // Keep existing poster/backdrop if better
            posterUrl: fullDetails.posterUrl || prev.posterUrl,
            backdropUrl: fullDetails.backdropUrl || prev.backdropUrl || fullDetails.posterUrl || prev.posterUrl
          }));

          if (cleanServers.length > 0) {
            setSelectedServer(cleanServers.find(s => s.isProxy) || cleanServers[0]);
          }
        }
      } catch {} finally {
        if (!isCancelled) setLoadingDetails(false);
      }
    };

    loadRichDetails();

    return () => {
      isCancelled = true;
    };
  }, [initialItem.id, initialItem.title]);

  // 2. Recommendations: Find matching movies by VJ, Genre, or Sequel/counterpart
  useEffect(() => {
    let isCancelled = false;

    const findRecommendations = async () => {
      setLoadingRecs(true);
      const cleanVj = movie.vj.toLowerCase().replace('vj', '').trim();
      const primaryGenre = movie.genre.split(/[,/&]/)[0].trim().toLowerCase();

      // 1. Filter local cache first
      const localMatches = allMovies.filter(m => {
        if (m.id === movie.id) return false;
        const vjMatch = isVjMatch(m.vj, movie.vj);
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
          ? await api.getMovies({ vj: movie.vj, perPage: 16 })
          : await api.getMovies({ genre: primaryGenre || 'Action', perPage: 16 });

        if (!isCancelled && res.items && res.items.length > 0) {
          const combined = [...localMatches, ...res.items.filter(i => i.id !== movie.id)];
          const seen = new Set<string>();
          const deduped: MediaItem[] = [];
          for (const m of combined) {
            const key = m.title.toLowerCase().replace(/[^a-z0-9]/g, '');
            if (!seen.has(key) && m.id !== movie.id) {
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
  }, [movie.id, movie.vj, movie.genre, allMovies]);

  const seasons = movie.seasons || [];
  
  // Synchronize selected season when seasons are fetched
  useEffect(() => {
    if (movie.seasons && movie.seasons.length > 0) {
      if (!movie.seasons.some(s => s.seasonNumber === selectedSeason)) {
        setSelectedSeason(movie.seasons[0].seasonNumber);
      }
    }
  }, [movie.seasons, selectedSeason]);

  const currentSeason = seasons.find(s => s.seasonNumber === selectedSeason) || seasons[0];

  const handleCardClick = (target: MediaItem) => {
    if (onSelectMovie) {
      onSelectMovie(target);
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <div className="min-h-screen bg-[#010318] text-white pb-24 md:pb-16 animate-fadeIn relative overflow-x-hidden">
      {/* Sticky Cinematic Top Navigation Bar */}
      <div className="sticky top-0 z-40 flex items-center justify-between px-4 sm:px-8 py-3.5 bg-[#010318]/90 backdrop-blur-md border-b border-[#132845]/70">
        <button
          onClick={onBack}
          className="flex items-center gap-2 px-3.5 py-1.5 rounded-xl bg-white/10 hover:bg-white/20 text-white font-semibold text-xs sm:text-sm border border-white/10 transition-all hover:scale-105 active:scale-95"
        >
          <ArrowLeft className="w-4 h-4 text-[#10B981]" />
          <span>Back</span>
        </button>

        <div className="flex items-center gap-2">
          <div className="px-2.5 py-1 rounded-lg bg-[#10B981]/15 border border-[#10B981]/40 text-[#10B981] font-extrabold text-[11px] uppercase tracking-wider">
            MUNOWATCH TV
          </div>
          <span className="hidden sm:inline-block px-2.5 py-1 rounded-lg bg-white/5 text-[#94A3B8] text-xs font-medium border border-white/5">
            1080p FHD
          </span>
        </div>
      </div>

      {/* Atmospheric Ambient Hero Backdrop (Layered behind, not pushing content down) */}
      <div className="absolute top-0 left-0 right-0 h-[680px] overflow-hidden pointer-events-none z-0">
        <img
          src={movie.backdropUrl || movie.posterUrl}
          alt={movie.title}
          className="w-full h-full object-cover object-top filter brightness-[0.32] contrast-[1.15] scale-105"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-[#010318] via-[#010318]/75 to-transparent" />
        <div className="absolute inset-0 bg-gradient-to-r from-[#010318]/95 via-[#010318]/65 to-transparent" />
        <div className="absolute inset-0 bg-radial-gradient from-transparent via-[#010318]/40 to-[#010318]/90" />
      </div>

      {/* Main Details Presentation: Balanced, Extended Top Webpage */}
      <div className="w-full max-w-[2000px] mx-auto px-4 sm:px-8 md:px-12 pt-6 sm:pt-8 relative z-10 space-y-10">
        <div className="flex flex-col md:flex-row gap-8 lg:gap-12 items-start">
          {/* Extended High-Resolution Movie Poster */}
          <div className="w-52 xs:w-60 sm:w-72 md:w-80 lg:w-[360px] xl:w-[410px] aspect-[2/3] rounded-2xl overflow-hidden shadow-2xl shadow-black/90 border-2 border-[#10B981]/50 flex-shrink-0 bg-[#0A192F] relative group mx-auto md:mx-0 transition-all duration-300 hover:scale-[1.01] hover:border-[#10B981] hover:shadow-[#10B981]/25">
            <img src={movie.posterUrl} alt={movie.title} className="w-full h-full object-cover" />
            <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity flex items-end p-4">
              <span className="text-xs font-bold text-[#10B981]">Ugandan Translated</span>
            </div>
          </div>

          {/* Metadata & Actions */}
          <div className="flex-1 space-y-4 pt-2">
            {/* Tag Badges Row matching Android TV DetailsScreen Badges */}
            <div className="flex flex-wrap items-center gap-2">
              <span className="px-3 py-1 rounded-lg bg-[#10B981] text-black font-extrabold text-xs uppercase tracking-wider shadow-lg shadow-[#10B981]/25">
                {movie.vj}
              </span>
              <span className="px-2.5 py-1 rounded-lg bg-white/10 text-yellow-400 font-bold text-xs flex items-center gap-1 border border-white/10">
                <Star className="w-3.5 h-3.5 fill-yellow-400" />
                {movie.rating || '8.8'}
              </span>
              {movie.year && (
                <span className="px-2.5 py-1 rounded-lg bg-white/5 text-[#94A3B8] text-xs font-medium border border-white/5">
                  {movie.year}
                </span>
              )}
              {movie.duration && (
                <span className="px-2.5 py-1 rounded-lg bg-white/5 text-[#94A3B8] text-xs font-medium flex items-center gap-1 border border-white/5">
                  <Clock className="w-3.5 h-3.5 text-[#94A3B8]" />
                  {movie.duration}
                </span>
              )}
              <span className="px-2.5 py-1 rounded-lg bg-[#10B981]/15 text-[#34D399] text-xs font-bold border border-[#10B981]/30">
                1080p Full HD
              </span>
            </div>

            {/* Title */}
            <h1 className="text-2xl sm:text-4xl md:text-5xl font-black tracking-tight text-white leading-tight">
              {movie.title}
            </h1>

            {/* Genre */}
            <p className="text-[#94A3B8] text-sm sm:text-base font-semibold">
              {movie.genre}
            </p>

            {/* MOVIE DESCRIPTION Surface Container matching Android TV SurfaceBox */}
            <div className="pt-2 space-y-2">
              <div className="flex items-center gap-2 text-[#10B981] font-bold text-xs tracking-wider uppercase">
                <BookOpen className="w-4 h-4" />
                <span>Movie Description</span>
              </div>

              <div className="p-4 sm:p-5 rounded-2xl bg-[#0A192F]/80 border border-[#132845] backdrop-blur-sm">
                <p className="text-[#F8FAFC] text-sm sm:text-base leading-relaxed">
                  {movie.description || (loadingDetails ? 'Retrieving genuine synopsis from Munowatch catalog...' : `Luganda translated blockbuster brought to you by ${movie.vj}.`)}
                </p>
              </div>
            </div>

            {/* INDIVIDUAL MOVIE SPECIFICATIONS STRIP matching Android TV Details */}
            <div className="pt-1 grid grid-cols-1 sm:grid-cols-3 gap-2.5">
              <div className="p-3 rounded-xl bg-[#0A192F]/60 border border-[#132845] flex items-center gap-2.5">
                <Headphones className="w-4 h-4 text-[#10B981] flex-shrink-0" />
                <div className="overflow-hidden">
                  <span className="text-[10px] uppercase font-bold text-[#94A3B8] block">Audio & Voiceover</span>
                  <span className="text-xs font-bold text-white truncate block">Luganda ({movie.vj})</span>
                </div>
              </div>

              <div className="p-3 rounded-xl bg-[#0A192F]/60 border border-[#132845] flex items-center gap-2.5">
                <Film className="w-4 h-4 text-[#34D399] flex-shrink-0" />
                <div className="overflow-hidden">
                  <span className="text-[10px] uppercase font-bold text-[#94A3B8] block">Format & Delivery</span>
                  <span className="text-xs font-bold text-white truncate block">1080p Full HD • CORS Safe</span>
                </div>
              </div>

              <div className="p-3 rounded-xl bg-[#0A192F]/60 border border-[#132845] flex items-center gap-2.5">
                <Shield className="w-4 h-4 text-[#10B981] flex-shrink-0" />
                <div className="overflow-hidden">
                  <span className="text-[10px] uppercase font-bold text-[#94A3B8] block">Subscription Access</span>
                  <span className="text-xs font-bold text-[#10B981] truncate block">Munowatch VIP Unlocked</span>
                </div>
              </div>
            </div>

            {/* Quality Tier Selector (1080p Full HD vs Data Saver) */}
            {qualities && qualities.length > 1 ? (
              <div className="pt-2 space-y-2">
                <span className="text-xs uppercase tracking-wider font-extrabold text-[#94A3B8] flex items-center gap-1.5">
                  <Sparkles className="w-3.5 h-3.5 text-[#10B981]" />
                  Choose Video Quality:
                </span>
                <div className="flex flex-wrap gap-2.5">
                  {qualities.map((q) => {
                    const isSelected = selectedQuality?.id === q.id;
                    return (
                      <button
                        key={q.id}
                        onClick={() => handleSelectQuality(q)}
                        className={`px-4 py-2.5 rounded-xl text-xs font-black flex items-center gap-2 border transition-all ${
                          isSelected
                            ? 'bg-[#10B981] text-black border-[#10B981] shadow-lg shadow-[#10B981]/25 scale-105'
                            : 'bg-[#0A192F] text-[#F8FAFC] border-[#1b3457] hover:border-[#10B981]'
                        }`}
                      >
                        {isSelected ? (
                          <Check className="w-3.5 h-3.5 text-black stroke-[3]" />
                        ) : q.id === 'fhd' ? (
                          <Sparkles className="w-3.5 h-3.5 text-[#10B981]" />
                        ) : (
                          <Radio className="w-3.5 h-3.5 text-[#94A3B8]" />
                        )}
                        <span>{q.label}</span>
                        <span className={`text-[10px] font-mono px-2 py-0.5 rounded-full ${
                          isSelected ? 'bg-black/20 text-black font-extrabold' : 'bg-white/10 text-[#10B981] font-semibold'
                        }`}>
                          {q.fileSizeFormatted}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>
            ) : movie.servers && movie.servers.length > 1 ? (
              <div className="pt-2 space-y-2">
                <span className="text-xs uppercase font-bold text-[#94A3B8] flex items-center gap-1.5">
                  <Server className="w-3.5 h-3.5 text-[#10B981]" />
                  Select Media Server:
                </span>
                <div className="flex flex-wrap gap-2">
                  {movie.servers.map((srv) => (
                    <button
                      key={srv.id}
                      onClick={() => setSelectedServer(srv)}
                      className={`px-3.5 py-2 rounded-xl text-xs font-bold flex items-center gap-2 border transition-all ${
                        selectedServer?.id === srv.id
                          ? 'bg-[#10B981]/20 text-[#34D399] border-[#10B981] shadow-lg shadow-[#10B981]/20 scale-105'
                          : 'bg-[#0A192F] text-[#94A3B8] border-[#132845] hover:border-[#94A3B8]'
                      }`}
                    >
                      {selectedServer?.id === srv.id && <Check className="w-3.5 h-3.5 text-[#10B981]" />}
                      <span>{srv.name}</span>
                      <span className="text-[10px] text-[#94A3B8] font-normal">({srv.quality})</span>
                    </button>
                  ))}
                </div>
              </div>
            ) : null}

            {/* Unavailable Stream Notice Banner */}
            {(!movie.streamUrl || isTrailerOrIntroUrl(movie.streamUrl, movie.title) || movie.isStreamAvailable === false) && !movie.id.startsWith('tk_') && (
              <div className="p-3.5 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-300 text-xs font-semibold flex items-center gap-2">
                <AlertCircle className="w-4 h-4 flex-shrink-0 text-amber-400" />
                <span>Full Ugandan translated stream for this title is currently unavailable or undergoing verification. Promotional trailers are strictly suppressed.</span>
              </div>
            )}

            {/* Live In-Place Download Progress Card */}
            {currentMovieDownload && (
              <div className="pt-3 pb-1">
                <div className="p-3.5 rounded-xl bg-gradient-to-r from-[#0d1f3b] to-[#0A192F] border border-[#10B981]/60 shadow-xl space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <div className="flex items-center gap-2">
                      <Radio className="w-3.5 h-3.5 text-[#10B981] animate-pulse" />
                      <span className="font-bold text-white">Downloading for Offline Watching...</span>
                    </div>
                    <span className="font-mono font-bold text-[#10B981]">{currentMovieDownload.progress}%</span>
                  </div>
                  <div className="w-full h-2 rounded-full bg-[#132845] overflow-hidden">
                    <div 
                      className="h-full rounded-full bg-gradient-to-r from-[#10B981] to-emerald-400 transition-all duration-300 shadow-sm shadow-[#10B981]/50"
                      style={{ width: `${currentMovieDownload.progress}%` }}
                    />
                  </div>
                  <div className="flex items-center justify-between text-[11px] text-[#94A3B8]">
                    <span>
                      {offlineStorage.formatBytes(currentMovieDownload.loadedBytes)}
                      {currentMovieDownload.totalBytes > 0 && ` of ${offlineStorage.formatBytes(currentMovieDownload.totalBytes)}`}
                    </span>
                    <span className="font-mono text-emerald-400 font-semibold">
                      {offlineStorage.formatSpeed(currentMovieDownload.speedBps)}
                    </span>
                  </div>
                </div>
              </div>
            )}

            {/* Primary Actions: Start Watching & Watchlist */}
            <div className="pt-4 flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
              {movie.isSeries ? (
                <button
                  onClick={() => {
                    const firstEp = seasons[0]?.episodes?.[0];
                    const targetServer = (selectedServer && !selectedServer.streamUrl.includes('preview.php') ? selectedServer : null) ||
                      movie.servers?.find(s => s.tier === (selectedQuality?.id || 'fhd') && s.isProxy) ||
                      movie.servers?.find(s => s.isProxy) ||
                      movie.servers?.[0];
                    onPlay(movie, firstEp, targetServer);
                  }}
                  className="flex items-center justify-center gap-3 px-8 py-3.5 sm:py-4 rounded-xl bg-[#10B981] hover:bg-[#34D399] text-black font-extrabold text-sm sm:text-base shadow-xl shadow-[#10B981]/30 transition-all hover:scale-105 active:scale-95"
                >
                  <Play className="w-5 h-5 fill-black" />
                  <span>
                    {seasons[0]?.episodes?.[0]
                      ? `Watch S${seasons[0].seasonNumber}:E${seasons[0].episodes[0].episodeNumber}`
                      : 'Start Series'}
                  </span>
                </button>
              ) : (
                <button
                  onClick={() => {
                    const targetServer = (selectedServer && !selectedServer.streamUrl.includes('preview.php') ? selectedServer : null) ||
                      movie.servers?.find(s => s.tier === (selectedQuality?.id || 'fhd') && s.isProxy) ||
                      movie.servers?.find(s => s.isProxy) ||
                      movie.servers?.[0];
                    onPlay(movie, undefined, targetServer);
                  }}
                  className="flex items-center justify-center gap-3 px-8 py-3.5 sm:py-4 rounded-xl bg-[#10B981] hover:bg-[#34D399] text-black font-extrabold text-sm sm:text-base shadow-xl shadow-[#10B981]/30 transition-all hover:scale-105 active:scale-95"
                >
                  <Play className="w-5 h-5 fill-black" />
                  <span>Start Watching</span>
                </button>
              )}

              {onToggleWatchlist && (
                <button
                  onClick={() => onToggleWatchlist(movie)}
                  className={`flex items-center justify-center gap-2.5 px-5 py-3.5 sm:py-4 rounded-xl text-sm font-bold border transition-all ${
                    isInWatchlist
                      ? 'bg-[#10B981]/15 text-[#10B981] border-[#10B981]'
                      : 'bg-[#0A192F] text-[#F8FAFC] border-[#132845] hover:border-[#94A3B8]'
                  }`}
                >
                  {isInWatchlist ? (
                    <>
                      <BookmarkCheck className="w-4 h-4 text-[#10B981]" />
                      <span>In Watchlist</span>
                    </>
                  ) : (
                    <>
                      <Bookmark className="w-4 h-4 text-[#94A3B8]" />
                      <span>Add to Watchlist</span>
                    </>
                  )}
                </button>
              )}

              {/* Live In-Progress Download State or Download/Downloaded Button */}
              {currentMovieDownload ? (
                <div className="flex items-center gap-3 px-5 py-3 sm:py-3.5 rounded-xl bg-[#0d1f3b] border border-[#10B981] shadow-lg shadow-[#10B981]/20">
                  <div className="w-4 h-4 rounded-full border-2 border-[#10B981] border-t-transparent animate-spin flex-shrink-0" />
                  <div className="flex flex-col">
                    <div className="flex items-center gap-1.5 text-xs font-black text-white">
                      <span>Downloading</span>
                      <span className="font-mono text-[#10B981]">{currentMovieDownload.progress}%</span>
                    </div>
                    <span className="text-[10px] text-[#94A3B8] font-mono">
                      {offlineStorage.formatBytes(currentMovieDownload.loadedBytes)}
                      {currentMovieDownload.totalBytes > 0 && ` / ${offlineStorage.formatBytes(currentMovieDownload.totalBytes)}`}
                    </span>
                  </div>
                  <button
                    onClick={() => offlineStorage.cancelDownload(currentMovieDownload.id)}
                    className="ml-2 p-1 text-[#94A3B8] hover:text-red-400 rounded hover:bg-white/5 transition-all"
                    title="Cancel download"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              ) : (
                <button
                  onClick={() => handleDirectDeviceSave({ item: movie, episode: movie.isSeries ? seasons[0]?.episodes?.[0] : undefined })}
                  className={`flex items-center justify-center gap-2.5 px-6 py-3.5 sm:py-4 rounded-xl text-sm font-bold border transition-all hover:scale-105 active:scale-95 ${
                    isMovieDownloaded
                      ? 'bg-[#10B981]/20 text-[#10B981] border-[#10B981] shadow-lg shadow-[#10B981]/15'
                      : 'bg-[#0A192F] text-white border-[#132845] hover:border-[#10B981]'
                  }`}
                  title="Download Video to Local Storage"
                >
                  {isMovieDownloaded ? (
                    <>
                      <CheckCircle2 className="w-4 h-4 text-[#10B981]" />
                      <span>Downloaded</span>
                      {estimatedFileSize && <span className="text-[11px] text-[#10B981]/80 font-normal">({estimatedFileSize})</span>}
                    </>
                  ) : (
                    <>
                      <Download className="w-4 h-4 text-[#10B981]" />
                      <span>Download Video to Local Storage</span>
                      {estimatedFileSize && <span className="text-[11px] text-[#94A3B8] font-normal">({estimatedFileSize})</span>}
                    </>
                  )}
                </button>
              )}
            </div>
          </div>
        </div>

        {/* TV Series Season & Episode Picker (if series) */}
        {movie.isSeries && (
          <div className="mt-12 space-y-6 pt-6 border-t border-[#132845]">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h2 className="text-xl sm:text-2xl font-black text-white flex items-center gap-2">
                  <Tv className="w-6 h-6 text-[#10B981]" />
                  <span>Seasons & Episodes</span>
                </h2>
                <p className="text-xs text-[#94A3B8]">Select a season and tap any episode to play</p>
              </div>

              {seasons.length > 1 && (
                <div className="flex flex-wrap gap-2">
                  {seasons.map(s => (
                    <button
                      key={s.seasonNumber}
                      onClick={() => setSelectedSeason(s.seasonNumber)}
                      className={`px-4 py-2 rounded-xl text-xs font-bold transition-all border flex items-center gap-2 ${
                        selectedSeason === s.seasonNumber
                          ? 'bg-[#10B981] text-black border-[#10B981] shadow-lg shadow-[#10B981]/25 scale-105'
                          : 'bg-[#0A192F] text-[#94A3B8] border-[#132845] hover:text-white hover:border-[#10B981]/40'
                      }`}
                    >
                      <span>Season {s.seasonNumber}</span>
                      <span className={`px-1.5 py-0.5 rounded text-[10px] ${
                        selectedSeason === s.seasonNumber ? 'bg-black/20 text-black font-extrabold' : 'bg-white/10 text-[#94A3B8]'
                      }`}>
                        {s.episodes.length} Eps
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </div>

            {loadingDetails && (!seasons || seasons.length === 0) ? (
              <div className="flex flex-col items-center justify-center py-16 space-y-3 bg-[#0A192F]/50 rounded-2xl border border-[#132845]">
                <Loader2 className="w-8 h-8 text-[#10B981] animate-spin" />
                <p className="text-xs font-semibold text-[#94A3B8]">Loading all seasons and episodes from catalog...</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                {currentSeason?.episodes.map(ep => (
                  <div
                    key={ep.id || `${currentSeason.seasonNumber}_${ep.episodeNumber}`}
                    onClick={() => onPlay(movie, ep, selectedServer)}
                    className="group flex flex-col p-3 rounded-2xl bg-[#0A192F] border border-[#132845] hover:border-[#10B981]/60 cursor-pointer transition-all hover:scale-[1.02] active:scale-98 shadow-md"
                  >
                    <div className="relative aspect-video w-full rounded-xl overflow-hidden bg-black/60 flex-shrink-0">
                      <img
                        src={ep.thumbnailUrl || movie.backdropUrl || movie.posterUrl}
                        alt={ep.title}
                        className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                      />
                      <div className="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                        <div className="w-10 h-10 rounded-full bg-[#10B981] flex items-center justify-center shadow-lg shadow-[#10B981]/40">
                          <Play className="w-5 h-5 fill-black text-black ml-0.5" />
                        </div>
                      </div>
                      <div className="absolute top-2 left-2 px-2 py-0.5 rounded-md bg-black/70 backdrop-blur-md text-[10px] font-extrabold text-[#10B981] border border-white/10">
                        S{currentSeason.seasonNumber}:E{ep.episodeNumber}
                      </div>

                      {/* Downloaded badge or live progress bar across bottom of thumbnail */}
                      {getEpActiveDownload(ep) ? (
                        <div className="absolute bottom-0 left-0 right-0 h-1.5 bg-[#132845] overflow-hidden">
                          <div 
                            className="h-full bg-gradient-to-r from-[#10B981] to-emerald-300 transition-all duration-300"
                            style={{ width: `${getEpActiveDownload(ep)!.progress}%` }}
                          />
                        </div>
                      ) : isEpDownloaded(ep) ? (
                        <div className="absolute top-2 right-2 px-1.5 py-0.5 rounded bg-[#10B981] text-black text-[9px] font-black uppercase flex items-center gap-1 shadow">
                          <CheckCircle2 className="w-3 h-3" />
                          <span>Downloaded</span>
                        </div>
                      ) : null}
                    </div>
                    <div className="pt-2.5 flex flex-col justify-between flex-1">
                      <div>
                        <h3 className="text-xs sm:text-sm font-bold text-white group-hover:text-[#34D399] line-clamp-1">
                          {ep.title}
                        </h3>
                      </div>
                      <div className="flex items-center justify-between pt-2 text-[11px] text-[#94A3B8]">
                        <span>{ep.duration || '45m'}</span>
                        <div className="flex items-center gap-2">
                          {getEpActiveDownload(ep) ? (
                            <div className="flex items-center gap-1 px-1.5 py-0.5 rounded bg-[#10B981]/15 border border-[#10B981]/40 text-[#10B981] text-[10px] font-mono">
                              <div className="w-2.5 h-2.5 rounded-full border border-[#10B981] border-t-transparent animate-spin" />
                              <span>{getEpActiveDownload(ep)!.progress}%</span>
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  offlineStorage.cancelDownload(getEpActiveDownload(ep)!.id);
                                }}
                                className="text-[#94A3B8] hover:text-red-400 ml-0.5"
                                title="Cancel"
                              >
                                <X className="w-3 h-3" />
                              </button>
                            </div>
                          ) : isEpDownloaded(ep) ? (
                            <span className="flex items-center gap-1 text-[10px] font-bold text-[#10B981] bg-[#10B981]/15 px-2 py-0.5 rounded border border-[#10B981]/30">
                              <CheckCircle2 className="w-3 h-3 text-[#10B981]" />
                              <span>Downloaded</span>
                            </span>
                          ) : (
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handleDirectDeviceSave({ item: movie, episode: ep });
                              }}
                              className="p-1 rounded-md bg-white/5 hover:bg-[#10B981]/20 text-[#94A3B8] hover:text-[#10B981] transition-all"
                              title={`Download Episode ${ep.episodeNumber} to Local Storage`}
                            >
                              <Download className="w-3.5 h-3.5" />
                            </button>
                          )}
                          <span className="text-[#10B981] font-semibold group-hover:underline flex items-center gap-1">
                            <Play className="w-2.5 h-2.5 fill-[#10B981]" /> Watch
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* MORE LIKE THIS Section matching Android TV DetailsScreen.kt */}
        {recommendations.length > 0 && (
          <div className="mt-14 pt-8 border-t border-[#132845] space-y-5 animate-fadeIn">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <Sparkles className="w-5 h-5 text-[#10B981]" />
                <h2 className="text-lg sm:text-xl font-black text-white uppercase tracking-wider">
                  More Like This
                </h2>
                <span className="text-xs font-semibold text-[#94A3B8]">
                  • {recommendations.length} Related Titles
                </span>
              </div>

              {/* Header Navigation Scroll Buttons */}
              <div className="flex items-center gap-2">
                <button
                  onClick={() => scrollRecs('left')}
                  className="p-2 rounded-xl bg-[#0A192F] hover:bg-[#10B981] hover:text-black text-white border border-[#132845] transition-all hover:scale-105 active:scale-95 shadow-md flex items-center justify-center"
                  title="Scroll Left (or use mouse wheel)"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <button
                  onClick={() => scrollRecs('right')}
                  className="p-2 rounded-xl bg-[#0A192F] hover:bg-[#10B981] hover:text-black text-white border border-[#132845] transition-all hover:scale-105 active:scale-95 shadow-md flex items-center justify-center"
                  title="Scroll Right"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>

            {/* Horizontal Scrolling Row with Controls & Mouse Wheel Support */}
            <div className="relative group/rail">
              {/* Floating Left Chevron */}
              <button
                onClick={() => scrollRecs('left')}
                className="hidden md:flex absolute -left-3 lg:-left-5 top-1/2 -translate-y-1/2 z-20 w-11 h-11 rounded-full bg-black/85 hover:bg-[#10B981] hover:text-black text-white border border-[#10B981]/40 items-center justify-center opacity-0 group-hover/rail:opacity-100 transition-all shadow-2xl backdrop-blur-md hover:scale-110 active:scale-95"
                title="Scroll Left"
              >
                <ChevronLeft className="w-6 h-6" />
              </button>

              <div
                ref={recScrollRef}
                onWheel={(e) => {
                  if (e.deltaY !== 0 && recScrollRef.current) {
                    recScrollRef.current.scrollLeft += e.deltaY;
                  }
                }}
                className="flex items-center gap-3.5 sm:gap-4 overflow-x-auto vj-horizontal-scrollbar scroll-smooth py-3 px-4 sm:px-1 -mx-4 sm:mx-0 select-none"
                style={{ WebkitOverflowScrolling: 'touch' }}
              >
                {recommendations.map((recMovie) => (
                  <TvMediaCard
                    key={recMovie.id}
                    item={recMovie}
                    onClick={() => handleCardClick(recMovie)}
                    cardWidth="w-36 sm:w-44 md:w-52"
                  />
                ))}
              </div>

              {/* Floating Right Chevron */}
              <button
                onClick={() => scrollRecs('right')}
                className="hidden md:flex absolute -right-3 lg:-right-5 top-1/2 -translate-y-1/2 z-20 w-11 h-11 rounded-full bg-black/85 hover:bg-[#10B981] hover:text-black text-white border border-[#10B981]/40 items-center justify-center opacity-0 group-hover/rail:opacity-100 transition-all shadow-2xl backdrop-blur-md hover:scale-110 active:scale-95"
                title="Scroll Right"
              >
                <ChevronRight className="w-6 h-6" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Toast Notification Banner */}
      {toastMessage && (
        <div className="fixed top-16 right-4 z-50 flex items-center gap-2 px-4 py-3 bg-[#10B981] text-black font-bold rounded-xl shadow-2xl animate-in fade-in slide-in-from-top-4 duration-200">
          <CheckCircle2 className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm">{toastMessage}</span>
        </div>
      )}
    </div>
  );
};
