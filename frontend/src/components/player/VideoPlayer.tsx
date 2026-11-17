import React, { useEffect, useRef, useState, useCallback } from 'react';
import Hls from 'hls.js';
import { 
  Play, Pause, RotateCcw, RotateCw, Maximize, Minimize, 
  ArrowLeft, Volume2, VolumeX, Server, Check 
} from 'lucide-react';
import { MediaItem, Episode, PlaybackServer } from '../../types';
import { api } from '../../services/api';

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

  // Available servers
  const availableServers: PlaybackServer[] = item.servers && item.servers.length > 0
    ? item.servers
    : [
        { id: 's1', name: 'Server 1 (BunnyCDN High-Speed)', quality: '1080p FHD', streamUrl: item.streamUrl, isProxy: false },
        { id: 's2', name: 'Server 2 (Munowatch Web Streamer - CORS Safe)', quality: '1080p FHD', streamUrl: api.getProxyStreamUrl(item.streamUrl), isProxy: true }
      ];

  const [currentServer, setCurrentServer] = useState<PlaybackServer>(
    initialServer || availableServers.find(s => s.isProxy) || availableServers[0]
  );

  const [resolvedStreamUrl, setResolvedStreamUrl] = useState<string>('');
  const [isResolving, setIsResolving] = useState<boolean>(false);

  useEffect(() => {
    const rawUrl = episode?.streamUrl || item.streamUrl;
    if (rawUrl && rawUrl.length > 5) {
      setResolvedStreamUrl(currentServer.isProxy ? api.getProxyStreamUrl(rawUrl) : rawUrl);
    } else {
      setIsResolving(true);
      api.resolveStream(item.id, item.title).then(res => {
        if (res.streamUrl) {
          setResolvedStreamUrl(currentServer.isProxy ? res.streamUrl : (res.directUrl || res.streamUrl));
        }
      }).finally(() => {
        setIsResolving(false);
      });
    }
  }, [item.id, item.title, item.streamUrl, episode, currentServer]);

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
    };

    const onWaiting = () => setIsBuffering(true);
    const onPlaying = () => {
      setIsBuffering(false);
      setIsPlaying(true);
    };
    const onPause = () => setIsPlaying(false);

    video.addEventListener('timeupdate', onTimeUpdate);
    video.addEventListener('loadedmetadata', onLoadedMetadata);
    video.addEventListener('waiting', onWaiting);
    video.addEventListener('playing', onPlaying);
    video.addEventListener('pause', onPause);

    return () => {
      video.removeEventListener('timeupdate', onTimeUpdate);
      video.removeEventListener('loadedmetadata', onLoadedMetadata);
      video.removeEventListener('waiting', onWaiting);
      video.removeEventListener('playing', onPlaying);
      video.removeEventListener('pause', onPause);
    };
  }, []);

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
    resetHideTimer();
  };

  const seek = (seconds: number) => {
    const video = videoRef.current;
    if (!video) return;
    video.currentTime = Math.max(0, Math.min(video.duration || 0, video.currentTime + seconds));
    resetHideTimer();
  };

  // Fullscreen toggle with orientation lock
  const toggleFullscreen = async () => {
    const container = containerRef.current;
    if (!container) return;

    if (!document.fullscreenElement) {
      try {
        if (container.requestFullscreen) {
          await container.requestFullscreen();
        } else if ((container as any).webkitRequestFullscreen) {
          await (container as any).webkitRequestFullscreen();
        }
        setIsFullscreen(true);
        // Attempt landscape lock on mobile devices
        if (screen.orientation && (screen.orientation as any).lock) {
          try {
            await (screen.orientation as any).lock('landscape');
          } catch {}
        }
      } catch {}
    } else {
      if (document.exitFullscreen) {
        await document.exitFullscreen();
      }
      setIsFullscreen(false);
      if (screen.orientation && screen.orientation.unlock) {
        try {
          screen.orientation.unlock();
        } catch {}
      }
    }
  };

  // Mobile double-tap gesture handling
  const lastTapRef = useRef<number>(0);
  const handleTouchZone = (e: React.MouseEvent<HTMLElement> | React.TouchEvent<HTMLElement>) => {
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

      {/* Buffering Indicator */}
      {isBuffering && (
        <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/40 pointer-events-none">
          <div className="w-14 h-14 rounded-full border-4 border-emerald-500/20 border-t-emerald-500 animate-spin mb-3" />
          <span className="text-xs font-bold text-white tracking-wider bg-black/60 px-3 py-1 rounded-full">
            Buffering Stream ({currentServer.name})...
          </span>
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
              className="p-2.5 rounded-full bg-black/60 hover:bg-emerald-500 hover:text-black text-white border border-white/10 transition-all active:scale-95"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
            <div className="flex flex-col">
              <span className="text-sm sm:text-base font-bold text-white line-clamp-1">
                {item.title} {episode ? `• Ep ${episode.episodeNumber}` : ''}
              </span>
              <span className="text-xs text-emerald-400 font-semibold">
                {item.vj} • {currentServer.name}
              </span>
            </div>
          </div>

          {/* Server Switcher Pill */}
          <div className="relative">
            <button
              onClick={() => setShowServerMenu(!showServerMenu)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-black/70 hover:bg-black/90 text-zinc-300 hover:text-white border border-emerald-500/40 text-xs font-semibold shadow-lg active:scale-95"
            >
              <Server className="w-3.5 h-3.5 text-emerald-400" />
              <span>Change Server</span>
            </button>

            {showServerMenu && (
              <div className="absolute right-0 mt-2 w-64 rounded-xl bg-muno-card border border-muno-border p-2 shadow-2xl space-y-1 z-40">
                <span className="text-[11px] font-bold text-zinc-400 px-2 py-1 block uppercase">
                  Available Media CDNs
                </span>
                {availableServers.map((srv) => (
                  <button
                    key={srv.id}
                    onClick={() => {
                      setCurrentServer(srv);
                      setShowServerMenu(false);
                    }}
                    className={`w-full flex items-center justify-between px-3 py-2 rounded-lg text-xs font-medium text-left transition-all ${
                      currentServer.id === srv.id
                        ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40'
                        : 'text-zinc-300 hover:bg-white/5'
                    }`}
                  >
                    <span>{srv.name}</span>
                    {currentServer.id === srv.id && <Check className="w-3.5 h-3.5 text-emerald-400" />}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Center Quick Play/Pause Action */}
        <div className="flex items-center justify-center gap-6 sm:gap-10">
          <button
            onClick={() => seek(-10)}
            className="p-3 sm:p-4 rounded-full bg-black/60 hover:bg-white/20 text-white transition-all active:scale-90"
          >
            <RotateCcw className="w-6 h-6 sm:w-8 sm:h-8" />
          </button>

          <button
            onClick={togglePlay}
            className="p-5 sm:p-6 rounded-full bg-emerald-500 hover:bg-emerald-400 text-black shadow-2xl shadow-emerald-500/50 transition-all active:scale-90"
          >
            {isPlaying ? (
              <Pause className="w-8 h-8 sm:w-10 sm:h-10 fill-black" />
            ) : (
              <Play className="w-8 h-8 sm:w-10 sm:h-10 fill-black translate-x-0.5" />
            )}
          </button>

          <button
            onClick={() => seek(10)}
            className="p-3 sm:p-4 rounded-full bg-black/60 hover:bg-white/20 text-white transition-all active:scale-90"
          >
            <RotateCw className="w-6 h-6 sm:w-8 sm:h-8" />
          </button>
        </div>

        {/* Bottom controls: Scrubber, Timer, Volume, Fullscreen */}
        <div className="space-y-2">
          {/* Timeline progress scrubber */}
          <div
            className="relative w-full h-3 flex items-center cursor-pointer group"
            onClick={(e) => {
              const rect = e.currentTarget.getBoundingClientRect();
              const pos = (e.clientX - rect.left) / rect.width;
              if (videoRef.current && duration) {
                videoRef.current.currentTime = pos * duration;
              }
            }}
          >
            {/* Background track */}
            <div className="w-full h-1.5 bg-white/20 rounded-full overflow-hidden">
              {/* Buffered track */}
              <div
                className="h-full bg-white/30"
                style={{ width: `${(buffered / (duration || 1)) * 100}%` }}
              />
            </div>
            {/* Played track */}
            <div
              className="absolute left-0 h-1.5 bg-emerald-500 rounded-full"
              style={{ width: `${(currentTime / (duration || 1)) * 100}%` }}
            />
            {/* Scrubber thumb */}
            <div
              className="absolute w-4 h-4 rounded-full bg-white shadow-md transform -translate-x-1/2 opacity-0 group-hover:opacity-100 transition-opacity"
              style={{ left: `${(currentTime / (duration || 1)) * 100}%` }}
            />
          </div>

          <div className="flex items-center justify-between text-xs sm:text-sm text-zinc-300">
            <div className="flex items-center gap-3">
              <span className="font-semibold text-white">
                {formatTime(currentTime)} / {formatTime(duration)}
              </span>
              <button
                onClick={() => {
                  if (videoRef.current) {
                    videoRef.current.muted = !isMuted;
                    setIsMuted(!isMuted);
                  }
                }}
                className="p-1 hover:text-white"
              >
                {isMuted ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
              </button>
            </div>

            <div className="flex items-center gap-3">
              <span className="px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-400 font-bold text-[11px] border border-emerald-500/30">
                1080p FHD
              </span>
              <button
                onClick={toggleFullscreen}
                className="p-1.5 rounded-lg hover:bg-white/10 text-white transition-all active:scale-95"
              >
                {isFullscreen ? <Minimize className="w-5 h-5" /> : <Maximize className="w-5 h-5" />}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
