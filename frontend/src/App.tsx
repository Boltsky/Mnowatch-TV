import React, { useState, useEffect } from 'react';
import { MediaItem, CatalogRail, Episode, PlaybackServer, VjItem } from './types';
import { api, FALLBACK_VJS } from './services/api';
import { MunowatchSidebar } from './components/common/MunowatchSidebar';
import { MunowatchMobileNav } from './components/common/MunowatchMobileNav';
import { MunowatchHeader } from './components/common/MunowatchHeader';
import { HeroBanner } from './components/home/HeroBanner';
import { ContentRail } from './components/home/ContentRail';
import { MediaCard } from './components/home/MediaCard';
import { ContentDetails } from './components/details/ContentDetails';
import { SearchScreen } from './components/search/SearchScreen';
import { VideoPlayer } from './components/player/VideoPlayer';
import { Clapperboard, Film, Bookmark, Sparkles } from 'lucide-react';

export const App: React.FC = () => {
  const [currentScreen, setCurrentScreen] = useState<string>('home');
  const [homeRails, setHomeRails] = useState<CatalogRail[]>([]);
  const [featuredItem, setFeaturedItem] = useState<MediaItem | null>(null);
  const [vjList, setVjList] = useState<VjItem[]>(FALLBACK_VJS);
  const [selectedVj, setSelectedVj] = useState<string | null>(null);
  const [allMovies, setAllMovies] = useState<MediaItem[]>([]);
  const [allSeries, setAllSeries] = useState<MediaItem[]>([]);
  
  // Navigation targets
  const [selectedItem, setSelectedItem] = useState<MediaItem | null>(null);
  const [playerTarget, setPlayerTarget] = useState<{
    item: MediaItem;
    episode?: Episode;
    server?: PlaybackServer;
  } | null>(null);

  // Load home catalog
  useEffect(() => {
    const loadCatalog = async () => {
      const data = await api.getHome();
      setHomeRails(data.rails);
      if (data.featured) setFeaturedItem(data.featured);

      const vjs = await api.getVjs();
      setVjList(vjs);
    };
    loadCatalog();
  }, []);

  // HTML5 History API Integration for Android & Mobile Hardware Back Buttons
  useEffect(() => {
    const handlePopState = (e: PopStateEvent) => {
      if (playerTarget) {
        setPlayerTarget(null);
        return;
      }
      if (selectedItem) {
        setSelectedItem(null);
        return;
      }
      if (e.state && e.state.screen) {
        setCurrentScreen(e.state.screen);
      } else {
        setCurrentScreen('home');
      }
    };

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, [playerTarget, selectedItem]);

  const navigateTo = (screen: string) => {
    window.history.pushState({ screen }, '', `#/${screen}`);
    setCurrentScreen(screen);
    setSelectedItem(null);
    setPlayerTarget(null);
    window.scrollTo({ top: 0, behavior: 'smooth' });

    if (screen === 'movies' && allMovies.length === 0) {
      api.getMovies().then(setAllMovies);
    }
    if (screen === 'series' && allSeries.length === 0) {
      api.getSeries().then(setAllSeries);
    }
  };

  const handleItemClick = (item: MediaItem) => {
    window.history.pushState({ screen: 'details', id: item.id }, '', `#/title/${item.id}`);
    setSelectedItem(item);
  };

  const handlePlay = (item: MediaItem, episode?: Episode, server?: PlaybackServer) => {
    window.history.pushState({ screen: 'player', id: item.id }, '', `#/watch/${item.id}`);
    setPlayerTarget({ item, episode, server });
  };

  const handleBack = () => {
    if (window.history.length > 1) {
      window.history.back();
    } else {
      if (playerTarget) setPlayerTarget(null);
      else if (selectedItem) setSelectedItem(null);
      else setCurrentScreen('home');
    }
  };

  // If Player is active, render Fullscreen VideoPlayer
  if (playerTarget) {
    return (
      <VideoPlayer
        item={playerTarget.item}
        episode={playerTarget.episode}
        initialServer={playerTarget.server}
        onBack={handleBack}
      />
    );
  }

  // If Details view is active
  if (selectedItem) {
    return (
      <ContentDetails
        item={selectedItem}
        onPlay={handlePlay}
        onBack={handleBack}
      />
    );
  }

  return (
    <div className="min-h-screen bg-[#0a0f0d] text-white flex">
      {/* Left Desktop / TV Sidebar */}
      <MunowatchSidebar
        currentScreen={currentScreen}
        onNavigate={navigateTo}
      />

      {/* Main Content Viewport */}
      <div className="flex-1 min-w-0 md:pl-20 pb-20 md:pb-8">
        {/* Top Header */}
        <MunowatchHeader
          onSearchClick={() => navigateTo('search')}
          onNavigateHome={() => navigateTo('home')}
        />

        {/* SCREEN: HOME */}
        {currentScreen === 'home' && (
          <main className="p-4 sm:p-6 md:p-8 max-w-7xl mx-auto">
            {/* Featured Hero Banner */}
            {featuredItem && (
              <HeroBanner
                item={featuredItem}
                onPlay={handlePlay}
                onDetails={handleItemClick}
              />
            )}

            {/* Quick VJ Selector Bar */}
            <div className="mb-6 space-y-2">
              <span className="text-xs uppercase font-extrabold text-zinc-400 tracking-wider flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5 text-emerald-400" />
                Featured Ugandan VJ Translators
              </span>
              <div className="flex items-center gap-2 overflow-x-auto no-scrollbar py-1">
                {vjList.map((vj) => (
                  <button
                    key={vj.id}
                    onClick={() => {
                      setSelectedVj(selectedVj === vj.name ? null : vj.name);
                    }}
                    className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-bold whitespace-nowrap transition-all border active:scale-95 ${
                      selectedVj === vj.name
                        ? 'bg-emerald-500 text-black border-emerald-500 shadow-lg shadow-emerald-500/25'
                        : 'bg-muno-card hover:bg-white/10 text-zinc-300 border-muno-border'
                    }`}
                  >
                    <span>{vj.icon}</span>
                    <span>{vj.name}</span>
                    <span className="text-[10px] opacity-70">({vj.count})</span>
                  </button>
                ))}
              </div>
            </div>

            {/* Catalog Rails */}
            {homeRails.map((rail) => {
              if (selectedVj && rail.vj && !rail.vj.toLowerCase().includes(selectedVj.toLowerCase())) {
                return null;
              }
              return (
                <ContentRail
                  key={rail.id}
                  title={rail.title}
                  items={rail.items}
                  onItemClick={handleItemClick}
                  onPlay={handlePlay}
                />
              );
            })}
          </main>
        )}

        {/* SCREEN: MOVIES */}
        {currentScreen === 'movies' && (
          <main className="p-4 sm:p-6 md:p-8 max-w-7xl mx-auto space-y-6">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center">
                <Film className="w-5 h-5 text-emerald-400" />
              </div>
              <h1 className="text-xl sm:text-2xl font-black text-white">Ugandan Translated Movies</h1>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3 sm:gap-4">
              {allMovies.map((item) => (
                <MediaCard
                  key={item.id}
                  item={item}
                  onClick={handleItemClick}
                  onPlay={handlePlay}
                />
              ))}
            </div>
          </main>
        )}

        {/* SCREEN: SERIES */}
        {currentScreen === 'series' && (
          <main className="p-4 sm:p-6 md:p-8 max-w-7xl mx-auto space-y-6">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center">
                <Film className="w-5 h-5 text-emerald-400" />
              </div>
              <h1 className="text-xl sm:text-2xl font-black text-white">Ugandan Translated TV Series</h1>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3 sm:gap-4">
              {allSeries.map((item) => (
                <MediaCard
                  key={item.id}
                  item={item}
                  onClick={handleItemClick}
                  onPlay={handlePlay}
                />
              ))}
            </div>
          </main>
        )}

        {/* SCREEN: VJS */}
        {currentScreen === 'vjs' && (
          <main className="p-4 sm:p-6 md:p-8 max-w-7xl mx-auto space-y-6">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center">
                <Clapperboard className="w-5 h-5 text-emerald-400" />
              </div>
              <h1 className="text-xl sm:text-2xl font-black text-white">Top Ugandan Video Jockeys (VJs)</h1>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4">
              {vjList.map((vj) => (
                <div
                  key={vj.id}
                  onClick={() => {
                    setSelectedVj(vj.name);
                    navigateTo('home');
                  }}
                  className="p-5 rounded-2xl bg-muno-card border border-muno-border hover:border-emerald-500 cursor-pointer transition-all hover:scale-105 active:scale-95 group shadow-xl"
                >
                  <div className="text-3xl mb-3">{vj.icon}</div>
                  <h3 className="text-lg font-bold text-white group-hover:text-emerald-400">
                    {vj.name}
                  </h3>
                  <p className="text-xs text-zinc-400 mt-1">
                    {vj.count}+ Translated Movies & Series
                  </p>
                </div>
              ))}
            </div>
          </main>
        )}

        {/* SCREEN: SEARCH */}
        {currentScreen === 'search' && (
          <SearchScreen
            onItemClick={handleItemClick}
            onPlay={handlePlay}
          />
        )}

        {/* SCREEN: WATCHLIST */}
        {currentScreen === 'watchlist' && (
          <main className="p-4 sm:p-6 md:p-8 max-w-7xl mx-auto space-y-6">
            <div className="flex items-center gap-3">
              <Bookmark className="w-6 h-6 text-emerald-400" />
              <h1 className="text-xl sm:text-2xl font-black text-white">My Watchlist</h1>
            </div>
            <p className="text-sm text-zinc-400">
              Your saved translated movies will appear here for fast one-click streaming.
            </p>
          </main>
        )}

        {/* SCREEN: SETTINGS */}
        {currentScreen === 'settings' && (
          <main className="p-4 sm:p-6 md:p-8 max-w-3xl mx-auto space-y-6">
            <h1 className="text-2xl font-black text-white">Settings & Playback</h1>
            <div className="p-5 rounded-2xl bg-muno-card border border-muno-border space-y-4">
              <h2 className="text-base font-bold text-emerald-400">Streaming Engine Details</h2>
              <div className="text-sm text-zinc-300 space-y-2">
                <p>• <strong>Application:</strong> Munowatch Web Streamer</p>
                <p>• <strong>Version:</strong> v1.2.0 (Responsive Universal Build)</p>
                <p>• <strong>Proxy Engine:</strong> HTTP 206 Partial Content enabled</p>
                <p>• <strong>Stream Quality:</strong> Up to 1080p Full HD</p>
                <p>• <strong>Supported Devices:</strong> Android, iOS, Windows, Mac, Smart TVs</p>
              </div>
            </div>
          </main>
        )}
      </div>

      {/* Bottom Navigation for Mobile Devices (< 768px) */}
      <MunowatchMobileNav
        currentScreen={currentScreen}
        onNavigate={navigateTo}
      />
    </div>
  );
};

export default App;
