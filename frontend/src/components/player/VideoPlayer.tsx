import React, { useEffect, useRef, useState, useCallback } from 'react';
import Hls from 'hls.js';
import { 
  Play, Pause, RotateCcw, RotateCw, Maximize, Minimize, 
  ArrowLeft, Volume2, VolumeX, Server, Check, AlertCircle, RefreshCw
} from 'lucide-react';
import { MediaItem, Episode, PlaybackServer } from '../../types';
import { api, isTrailerOrIntroUrl } from '../../services/api';

interface VideoPlayerProps {
  item: MediaItem;
  episode?: Episode;
  initialServer?: PlaybackServer;
  onBack: () => void;
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({ item, episode, initialServer, onBack }) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const hlsRef = useRef<Hls | null>(null);

  const [isPlaying, setIsPlaying] = useState<boolean>(true);
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [duration, setDuration] = useState<number>(0);
  const [buffered, setBuffered] = useState<number>(0);
  const [isMuted, setIsMuted] = useState<boolean>(false);
  const [isFullscreen, setIsFullscreen] = useState<boolean>(false);
  const [showControls, setShowControls] = useState<boolean>(true);
  const [isBuffering, setIsBuffering] = useState<boolean>(true);
  const [showServerMenu, setShowServerMenu] = useState<boolean>(false);
  const [seekRipple, setSeekRipple] = useState<{ text: string; side: 'left' | 'right' | 'center' } | null>(null);

  const [hasError, setHasError] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string>('');
  const [isResolving, setIsResolving] = useState<boolean>(false);

  // Available servers (strictly filtered to eliminate trailers and promos)
  const rawServers: PlaybackServer[] = item.servers && item.servers.length > 0
    ? item.servers
    : (item.streamUrl && !isTrailerOrIntroUrl(item.streamUrl, item.title) ? [
        { id: 's1', name: 'Server 1 (BunnyCDN High-Speed)', quality: '1080p FHD', streamUrl: item.streamUrl, isProxy: false },
        { id: 's2', name: 'Server 2 (Munowatch Web Streamer - CORS Safe)', quality: '1080p FHD', streamUrl: api.getProxyStreamUrl(item.streamUrl), isProxy: true }
      ] : []);

  const availableServers = rawServers.filter(s => s.streamUrl && !isTrailerOrIntroUrl(s.streamUrl, item.title));

  const [currentServer, setCurrentServer] = useState<PlaybackServer>(
    initialServer && !isTrailerOrIntroUrl(initialServer.streamUrl, item.title)
      ? initialServer
      : (availableServers.find(s => s.isProxy) || availableServers[0] || {
          id: 's2',
          name: 'Server 2 (Munowatch Web Streamer - CORS Safe)',
          quality: '1080p FHD',
          streamUrl: '',
          isProxy: true
        })
  );

  const [resolvedStreamUrl, setResolvedStreamUrl] = useState<string>('');

  const triggerStreamResolution = useCallback(async () => {
    setHasError(false);
    setIsResolving(true);

    // 1. If series episode has explicit stream URL
    if (episode?.streamUrl && episode.streamUrl.length > 10 && !isTrailerOrIntroUrl(episode.streamUrl, episode.title || item.title)) {
      if (episode.streamUrl.includes('preview.php') || episode.streamUrl.includes('id=')) {
        try {
          const res = await api.resolveStream(episode.id || item.id, episode.title || item.title);
          if (res.streamUrl && !isTrailerOrIntroUrl(res.streamUrl, item.title) && !isTrailerOrIntroUrl(res.directUrl, item.title)) {
            const finalUrl = currentServer.isProxy ? res.streamUrl : (res.directUrl || res.streamUrl);
            setResolvedStreamUrl(finalUrl);
            setIsResolving(false);
            return;
          }
        } catch {}
      }
      const finalUrl = currentServer.isProxy ? api.getProxyStreamUrl(episode.streamUrl) : episode.streamUrl;
      setResolvedStreamUrl(finalUrl);
      setIsResolving(false);
      return;
    }

    // 2. Always query verified stream resolver to validate title match and avoid wrong movie playback
    try {
      const res = await api.resolveStream(item.id, item.title);
      if (res.streamUrl && !isTrailerOrIntroUrl(res.streamUrl, item.title) && !isTrailerOrIntroUrl(res.directUrl, item.title)) {
        const finalUrl = currentServer.isProxy ? res.streamUrl : (res.directUrl || res.streamUrl);
        setResolvedStreamUrl(finalUrl);
      } else if (
        item.streamUrl &&
        !isTrailerOrIntroUrl(item.streamUrl, item.title) &&
        (item.streamUrl.includes('movviez.com') || (item.streamUrl.includes('b-cdn.net') && !item.streamUrl.includes('trailer256')))
      ) {
        const finalUrl = currentServer.isProxy ? api.getProxyStreamUrl(item.streamUrl) : item.streamUrl;
        setResolvedStreamUrl(finalUrl);
      } else {
        setHasError(true);
        setErrorMessage(
          res.error || `A genuine full-length Ugandan translated stream is not currently available for "${item.title}". We do not stream promotional trailers or intro clips.`
        );
      }
    } catch (err: any) {
      if (item.streamUrl && !isTrailerOrIntroUrl(item.streamUrl, item.title)) {
        const finalUrl = currentServer.isProxy ? api.getProxyStreamUrl(item.streamUrl) : item.streamUrl;
        setResolvedStreamUrl(finalUrl);
      } else {
        setHasError(true);
        setErrorMessage(
          `A genuine full-length Ugandan translated stream is not currently available for "${item.title}". We do not stream promotional trailers or intro clips.`
        );
      }
    } finally {
      setIsResolving(false);
    }
  }, [item.id, item.title, item.streamUrl, episode, currentServer]);

  useEffect(() => {
    triggerStreamResolution();
  }, [triggerStreamResolution]);

  const activeStreamUrl = resolvedStreamUrl;

  const hideControlsTimer = useRef<any>(null);

  const resetHideTimer = useCallback(() => {
    setShowControls(true);
    if (hideControlsTimer.current) clearTimeout(hideControlsTimer.current);
    hideControlsTimer.current = setTimeout(() => {
      if (isPlaying) setShowControls(false);
    }, 4000);
  }, [isPlaying]);

  // Video stream initialization
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !activeStreamUrl) return;

    setIsBuffering(true);
    setHasError(false);

    if (hlsRef.current) {
      hlsRef.current.destroy();
      hlsRef.current = null;
    }

    const isHls = activeStreamUrl.includes('.m3u8');

    if (isHls && Hls.isSupported()) {
      const hls = new Hls({
        enableWorker: true,
        lowLatencyMode: true,
      });
      hlsRef.current = hls;
      hls.loadSource(activeStreamUrl);
      hls.attachMedia(video);

      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        video.play().catch(() => setIsPlaying(false));
      });

      hls.on(Hls.Events.ERROR, (_, data) => {
        if (data.fatal) {
          switch (data.type) {
            case Hls.ErrorTypes.NETWORK_ERROR:
              hls.startLoad();
              break;
            case Hls.ErrorTypes.MEDIA_ERROR:
              hls.recoverMediaError();
              break;
            default:
              hls.destroy();
              setHasError(true);
              setErrorMessage('HLS media stream encountered an error.');
              break;
          }
        }
      });
    } else {
      video.src = activeStreamUrl;
      video.load();
      video.play().catch(() => setIsPlaying(false));
    }

    return () => {
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
    };
  }, [activeStreamUrl]);

  // Video event listeners
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const onTimeUpdate = () => {
      setCurrentTime(video.currentTime);
      if (video.buffered.length > 0) {
        setBuffered(video.buffered.end(video.buffered.length - 1));
      }
    };

    const onLoadedMetadata = () => {
      setDuration(video.duration);
      setIsBuffering(false);
      setHasError(false);
    };

    const onWaiting = () => setIsBuffering(true);
    const onPlaying = () => {
      setIsBuffering(false);
      setIsPlaying(true);
      setHasError(false);
    };
    const onPause = () => setIsPlaying(false);

    const onError = () => {
      setIsBuffering(false);
      // Automatically attempt proxy failover if direct playback failed (e.g. CORS restriction)
      if (!currentServer.isProxy && activeStreamUrl) {
        const proxyServer = availableServers.find(s => s.isProxy);
        if (proxyServer) {
          setCurrentServer(proxyServer);
          setResolvedStreamUrl(api.getProxyStreamUrl(activeStreamUrl));
          return;
        }
      }
      setHasError(true);
      setErrorMessage('Media connection stalled. Switching to secure CORS failover...');
    };

    video.addEventListener('timeupdate', onTimeUpdate);
    video.addEventListener('loadedmetadata', onLoadedMetadata);
    video.addEventListener('waiting', onWaiting);
    video.addEventListener('playing', onPlaying);
    video.addEventListener('pause', onPause);
    video.addEventListener('error', onError);

    return () => {
      video.removeEventListener('timeupdate', onTimeUpdate);
      video.removeEventListener('loadedmetadata', onLoadedMetadata);
      video.removeEventListener('waiting', onWaiting);
      video.removeEventListener('playing', onPlaying);
      video.removeEventListener('pause', onPause);
      video.removeEventListener('error', onError);
    };
  }, [currentServer, activeStreamUrl]);

  // Controls playback toggle
  const togglePlay = () => {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) {
      video.play();
      setIsPlaying(true);
    } else {
      video.pause();
      setIsPlaying(false);
    }
  };

  const seek = (seconds: number) => {
    const video = videoRef.current;
    if (!video) return;
    video.currentTime = Math.max(0, Math.min(video.currentTime + seconds, duration));
  };

  const handleSeekChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const video = videoRef.current;
    if (!video) return;
    const newTime = parseFloat(e.target.value);
    video.currentTime = newTime;
    setCurrentTime(newTime);
  };

  const toggleMute = () => {
    const video = videoRef.current;
    if (!video) return;
    video.muted = !isMuted;
    setIsMuted(!isMuted);
  };

  const toggleFullscreen = () => {
    const container = containerRef.current;
    if (!container) return;

    if (!document.fullscreenElement) {
      container.requestFullscreen().then(() => setIsFullscreen(true)).catch(() => {});
    } else {
      document.exitFullscreen().then(() => setIsFullscreen(false)).catch(() => {});
    }
  };

  // Double tap gestures (±10s Seek, Double-tap center for Play/Pause)
  const lastTapRef = useRef<number>(0);
  const handleTouchZone = (e: React.TouchEvent | React.MouseEvent) => {
    const now = Date.now();
    const isDoubleTap = now - lastTapRef.current < 300;
    lastTapRef.current = now;

    if (!isDoubleTap) {
      resetHideTimer();
      return;
    }

    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect) return;

    const clientX = 'touches' in e && e.touches.length > 0
      ? e.touches[0].clientX
      : (e as React.MouseEvent).clientX;

    const relX = (clientX - rect.left) / rect.width;

    if (relX < 0.38) {
      seek(-10);
      setSeekRipple({ text: '-10s', side: 'left' });
      setTimeout(() => setSeekRipple(null), 700);
    } else if (relX > 0.62) {
      seek(10);
      setSeekRipple({ text: '+10s', side: 'right' });
      setTimeout(() => setSeekRipple(null), 700);
    } else {
      togglePlay();
      setSeekRipple({ text: isPlaying ? 'Pause' : 'Play', side: 'center' });
      setTimeout(() => setSeekRipple(null), 700);
    }
  };

  const formatTime = (secs: number) => {
    if (isNaN(secs)) return '00:00';
    const m = Math.floor(secs / 60);
    const s = Math.floor(secs % 60);
    const h = Math.floor(m / 60);
    if (h > 0) {
      const remM = m % 60;
      return `${h}:${remM < 10 ? '0' : ''}${remM}:${s < 10 ? '0' : ''}${s}`;
    }
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  return (
    <div
      ref={containerRef}
      onMouseMove={resetHideTimer}
      onTouchStart={resetHideTimer}
      className="fixed inset-0 z-50 bg-black flex items-center justify-center select-none overflow-hidden"
    >
      {/* Video Element */}
      <video
        ref={videoRef}
        className="w-full h-full object-contain"
        playsInline
        onClick={handleTouchZone}
      />

      {/* Resolving Stream Overlay */}
      {isResolving && (
        <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/85 z-40 p-6 text-center space-y-3">
          <div className="w-14 h-14 rounded-full border-4 border-emerald-500/20 border-t-emerald-500 animate-spin" />
          <p className="text-white font-bold text-base">Connecting to Ugandan media stream node...</p>
          <p className="text-xs text-zinc-400">Resolving 1080p full stream for {item.title}</p>
        </div>
      )}

      {/* Buffering Indicator */}
      {isBuffering && !isResolving && !hasError && (
        <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/40 pointer-events-none">
          <div className="w-14 h-14 rounded-full border-4 border-emerald-500/20 border-t-emerald-500 animate-spin mb-3" />
          <span className="text-xs font-bold text-white tracking-wider bg-black/60 px-3 py-1 rounded-full">
            Buffering Stream ({currentServer.name})...
          </span>
        </div>
      )}

      {/* Stream Error Fallback Screen */}
      {hasError && !isResolving && (
        <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/90 z-50 p-6 text-center space-y-4">
          <div className="w-14 h-14 rounded-full bg-amber-500/10 border border-amber-500/40 flex items-center justify-center text-amber-400">
            <AlertCircle className="w-7 h-7" />
          </div>
          <div className="space-y-1">
            <h3 className="text-lg font-bold text-white">Full Ugandan Movie Unavailable</h3>
            <p className="text-xs text-zinc-400 max-w-md">
              {errorMessage || `A genuine full-length Ugandan translated stream is not currently available for "${item.title}". We do not stream promotional trailers or intro clips.`}
            </p>
          </div>
          <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
            <button
              onClick={triggerStreamResolution}
              className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-black font-extrabold text-xs transition-all shadow-lg shadow-emerald-500/30"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              Retry Playback
            </button>
            <button
              onClick={onBack}
              className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-white/10 hover:bg-white/20 text-white font-semibold text-xs border border-white/20 transition-all"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
              Back to Details
            </button>
            <button
              onClick={onBack}
              className="px-5 py-2.5 rounded-xl bg-white/5 hover:bg-white/10 text-zinc-400 font-semibold text-xs transition-all"
            >
              Explore Other Movies
            </button>
          </div>
        </div>
      )}

      {/* Seek Animation Ripple Badges */}
      {seekRipple && (
        <div
          className={`absolute top-1/2 -translate-y-1/2 pointer-events-none z-30 transition-all duration-300 animate-ping ${
            seekRipple.side === 'left' ? 'left-16' : seekRipple.side === 'right' ? 'right-16' : 'left-1/2 -translate-x-1/2'
          }`}
        >
          <div className="px-4 py-2 rounded-full bg-emerald-500/90 text-black font-black text-sm shadow-2xl">
            {seekRipple.text}
          </div>
        </div>
      )}

      {/* Over-The-Screen Display (OSD) Controls */}
      <div
        className={`absolute inset-0 flex flex-col justify-between p-4 sm:p-6 md:p-8 bg-gradient-to-t from-black/90 via-transparent to-black/80 transition-opacity duration-300 ${
          showControls ? 'opacity-100' : 'opacity-0 pointer-events-none'
        }`}
      >
        {/* Top bar: Back button, Title & Server Switcher */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button
              onClick={onBack}
              className="p-2.5 rounded-xl bg-white/10 hover:bg-white/20 text-white transition-all backdrop-blur-md active:scale-95"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
            <div>
              <h2 className="text-base sm:text-lg font-bold text-white line-clamp-1">
                {item.title}
              </h2>
              <p className="text-xs text-zinc-400 flex items-center gap-2">
                <span className="text-emerald-400 font-bold">{item.vj}</span>
                {episode && <span>• Episode {episode.episodeNumber}: {episode.title}</span>}
              </p>
            </div>
          </div>

          {/* Server Selector Button */}
          <div className="relative">
            <button
              onClick={() => setShowServerMenu(!showServerMenu)}
              className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-white/10 hover:bg-white/20 text-white text-xs font-semibold backdrop-blur-md border border-white/10 transition-all"
            >
              <Server className="w-3.5 h-3.5 text-emerald-400" />
              <span className="hidden sm:inline">{currentServer.name}</span>
              <span className="sm:hidden">Server</span>
            </button>

            {/* Server Dropdown */}
            {showServerMenu && (
              <div className="absolute right-0 mt-2 w-56 rounded-xl bg-[#0a0f0d]/95 border border-emerald-500/30 p-2 shadow-2xl backdrop-blur-xl z-50 space-y-1">
                <div className="px-2 py-1 text-[10px] font-bold uppercase tracking-wider text-zinc-400 border-b border-white/10">
                  Select Media Server
                </div>
                {availableServers.map(srv => (
                  <button
                    key={srv.id}
                    onClick={() => {
                      setCurrentServer(srv);
                      setShowServerMenu(false);
                    }}
                    className={`w-full flex items-center justify-between px-2.5 py-2 rounded-lg text-xs font-medium text-left transition-all ${
                      currentServer.id === srv.id
                        ? 'bg-emerald-500/20 text-emerald-400 font-bold'
                        : 'text-zinc-300 hover:bg-white/5'
                    }`}
                  >
                    <span>{srv.name}</span>
                    {currentServer.id === srv.id && <Check className="w-3.5 h-3.5" />}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Center Big Play/Pause Button */}
        <div className="self-center">
          <button
            onClick={togglePlay}
            className="p-5 sm:p-6 rounded-full bg-emerald-500/90 hover:bg-emerald-400 text-black shadow-2xl shadow-emerald-500/40 transition-all hover:scale-110 active:scale-95"
          >
            {isPlaying ? <Pause className="w-8 h-8 fill-black" /> : <Play className="w-8 h-8 fill-black translate-x-0.5" />}
          </button>
        </div>

        {/* Bottom bar: Timeline scrubber & transport controls */}
        <div className="space-y-3">
          {/* Scrubber slider */}
          <div className="relative flex items-center group">
            {/* Buffered progress track */}
            <div
              className="absolute left-0 top-1/2 -translate-y-1/2 h-1 bg-white/30 rounded-full pointer-events-none transition-all"
              style={{ width: `${(buffered / (duration || 1)) * 100}%` }}
            />
            {/* Active playback progress track */}
            <div
              className="absolute left-0 top-1/2 -translate-y-1/2 h-1 bg-emerald-500 rounded-full pointer-events-none transition-all"
              style={{ width: `${(currentTime / (duration || 1)) * 100}%` }}
            />
            <input
              type="range"
              min={0}
              max={duration || 100}
              value={currentTime}
              onChange={handleSeekChange}
              className="w-full h-1 bg-transparent appearance-none cursor-pointer accent-emerald-500 z-10 opacity-0 group-hover:opacity-100 transition-opacity"
            />
          </div>

          {/* Time & Action buttons */}
          <div className="flex items-center justify-between text-xs font-medium text-zinc-300">
            <div className="flex items-center gap-4">
              <button onClick={togglePlay} className="hover:text-white transition-colors">
                {isPlaying ? <Pause className="w-5 h-5 fill-current" /> : <Play className="w-5 h-5 fill-current" />}
              </button>

              <button onClick={() => seek(-10)} className="hover:text-white transition-colors" title="-10 Seconds">
                <RotateCcw className="w-5 h-5" />
              </button>

              <button onClick={() => seek(10)} className="hover:text-white transition-colors" title="+10 Seconds">
                <RotateCw className="w-5 h-5" />
              </button>

              <button onClick={toggleMute} className="hover:text-white transition-colors">
                {isMuted ? <VolumeX className="w-5 h-5 text-red-400" /> : <Volume2 className="w-5 h-5" />}
              </button>

              <span className="font-mono text-zinc-400">
                {formatTime(currentTime)} / {formatTime(duration)}
              </span>
            </div>

            <div className="flex items-center gap-3">
              <span className="hidden sm:inline-block px-2 py-0.5 rounded bg-white/10 text-[10px] font-bold text-emerald-400">
                1080p Full HD
              </span>
              <button onClick={toggleFullscreen} className="hover:text-white transition-colors">
                {isFullscreen ? <Minimize className="w-5 h-5" /> : <Maximize className="w-5 h-5" />}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
