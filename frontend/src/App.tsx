import React, { useState, useEffect, useCallback } from 'react';
import { MediaItem, CatalogRail, Episode, PlaybackServer, VjItem } from './types';
import { api, FALLBACK_VJS } from './services/api';
import { TvTopBar } from './components/common/TvTopBar';
import { MunowatchMobileNav } from './components/common/MunowatchMobileNav';
import { TvHeroBanner } from './components/home/TvHeroBanner';
import { TvContentSection } from './components/home/TvContentSection';
import { TvMediaCard } from './components/common/TvMediaCard';
import { TvVjChip } from './components/home/TvVjChip';
import { MoviesScreen } from './components/movies/MoviesScreen';
import { SeriesScreen } from './components/series/SeriesScreen';
import { CategoriesScreen } from './components/categories/CategoriesScreen';
import { SearchScreen } from './components/search/SearchScreen';
import { SettingsScreen } from './components/settings/SettingsScreen';
import { ContentDetails } from './components/details/ContentDetails';
import { VideoPlayer } from './components/player/VideoPlayer';

export const App: React.FC = () => {
  const [currentScreen, setCurrentScreen] = useState<string>('home');
  const [previousScreen, setPreviousScreen] = useState<string>('home');
  const [homeRails, setHomeRails] = useState<CatalogRail[]>([]);
  const [featuredItem, setFeaturedItem] = useState<MediaItem | null>(null);
  const [vjList, setVjList] = useState<VjItem[]>(FALLBACK_VJS);
  const [allMovies, setAllMovies] = useState<MediaItem[]>([]);
  const [allSeries, setAllSeries] = useState<MediaItem[]>([]);
  const [watchlist, setWatchlist] = useState<MediaItem[]>([]);
  const [continueWatching, setContinueWatching] = useState<any[]>([]);

  // Navigation targets
  const [selectedItem, setSelectedItem] = useState<MediaItem | null>(null);
  const [playerTarget, setPlayerTarget] = useState<{
    item: MediaItem;
    episode?: Episode;
    server?: PlaybackServer;
  } | null>(null);

  // Hash route parsing
  const parseHashRoute = useCallback(() => {
    const rawHash = window.location.hash.replace(/^#\/?/, '').trim();
    if (rawHash.startsWith('watch/')) {
      return { type: 'watch' as const, id: decodeURIComponent(rawHash.replace('watch/', '')) };
    }
    if (rawHash.startsWith('title/')) {
      return { type: 'title' as const, id: decodeURIComponent(rawHash.replace('title/', '')) };
    }
    const cleanScreen = rawHash.split('?')[0].toLowerCase();
    if (['home', 'movies', 'series', 'categories', 'search', 'settings'].includes(cleanScreen)) {
      return { type: 'screen' as const, screen: cleanScreen };
    }
    return { type: 'screen' as const, screen: 'home' };
  }, []);

  // Synchronize state from URL hash
  const syncFromHash = useCallback(async (moviesPool: MediaItem[], seriesPool: MediaItem[]) => {
    const route = parseHashRoute();
    if (route.type === 'screen') {
      setPlayerTarget(null);
      setSelectedItem(null);
      setCurrentScreen(route.screen);
      setPreviousScreen(route.screen);
    } else if (route.type === 'title') {
      setPlayerTarget(null);
      const combined = [...moviesPool, ...seriesPool];
      const normalizedRouteId = route.id.replace(/\+/g, ' ');
      let item = combined.find(i =>
        i.id === route.id ||
        i.id === normalizedRouteId ||
        i.id.replace(/\+/g, ' ') === normalizedRouteId ||
        decodeURIComponent(i.id) === decodeURIComponent(route.id)
      );
      if (!item) {
        try {
          const fetched = await api.getMovieDetails(route.id);
          if (fetched) item = fetched;
        } catch {}
      }
      if (!item && route.id.includes('+')) {
        try {
          const fetched = await api.getMovieDetails(normalizedRouteId);
          if (fetched) item = fetched;
        } catch {}
      }
      if (item) {
        setSelectedItem(item);
      }
    } else if (route.type === 'watch') {
      const combined = [...moviesPool, ...seriesPool];
      const normalizedRouteId = route.id.replace(/\+/g, ' ');
      let item = combined.find(i =>
        i.id === route.id ||
        i.id === normalizedRouteId ||
        i.id.replace(/\+/g, ' ') === normalizedRouteId ||
        decodeURIComponent(i.id) === decodeURIComponent(route.id)
      );
      if (!item) {
        try {
          const fetched = await api.getMovieDetails(route.id);
          if (fetched) item = fetched;
        } catch {}
      }
      if (!item && route.id.includes('+')) {
        try {
          const fetched = await api.getMovieDetails(normalizedRouteId);
          if (fetched) item = fetched;
        } catch {}
      }
      if (item) {
        setPlayerTarget({ item });
      }
    }
  }, [parseHashRoute]);

  // Load catalog on mount & initialize route
  useEffect(() => {
    const loadData = async () => {
      const data = await api.getHome();
      setHomeRails(data.rails);
      if (data.featured) setFeaturedItem(data.featured);

      const vjs = await api.getVjs();
      setVjList(vjs);

      const moviesRes = await api.getMovies();
      const loadedMovies = moviesRes.items || [];
      setAllMovies(loadedMovies);

      const seriesRes = await api.getSeries();
      const loadedSeries = seriesRes.items || [];
      setAllSeries(loadedSeries);

      // Load saved watchlist from localStorage
      try {
        const savedWl = localStorage.getItem('munowatch_watchlist');
        if (savedWl) setWatchlist(JSON.parse(savedWl));
      } catch {}

      // Load continue watching
      try {
        const savedCw = localStorage.getItem('munowatch_history');
        if (savedCw) setContinueWatching(JSON.parse(savedCw));
      } catch {}

      // Synchronize initial route from URL hash
      await syncFromHash(loadedMovies, loadedSeries);
    };
    loadData();
  }, [syncFromHash]);

  // Listen to popstate and hashchange events for smooth back/forward navigation
  useEffect(() => {
    const onHashOrPop = () => {
      syncFromHash(allMovies, allSeries);
    };

    window.addEventListener('popstate', onHashOrPop);
    window.addEventListener('hashchange', onHashOrPop);
    return () => {
      window.removeEventListener('popstate', onHashOrPop);
      window.removeEventListener('hashchange', onHashOrPop);
    };
  }, [allMovies, allSeries, syncFromHash]);

  const navigateTo = (screen: string) => {
    setPreviousScreen(currentScreen);
    setCurrentScreen(screen);
    setSelectedItem(null);
    setPlayerTarget(null);
    window.location.hash = `#/${screen}`;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleItemClick = (item: MediaItem) => {
    setPreviousScreen(currentScreen);
    setSelectedItem(item);
    window.location.hash = `#/title/${encodeURIComponent(item.id)}`;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handlePlay = (item: MediaItem, episode?: Episode, server?: PlaybackServer) => {
    setPlayerTarget({ item, episode, server });
    window.location.hash = `#/watch/${encodeURIComponent(item.id)}`;

    // Save to Continue Watching in localStorage
    try {
      const existing = continueWatching.filter(i => i.id !== item.id);
      const updated = [
        {
          id: item.id,
          title: item.title,
          posterUrl: item.posterUrl,
          vj: item.vj,
          progress: 35,
          timestamp: Date.now()
        },
        ...existing
      ].slice(0, 10);
      setContinueWatching(updated);
      localStorage.setItem('munowatch_history', JSON.stringify(updated));
    } catch {}
  };

  const toggleFavorite = (item: MediaItem) => {
    const exists = watchlist.some(w => w.id === item.id);
    let updated: MediaItem[];
    if (exists) {
      updated = watchlist.filter(w => w.id !== item.id);
    } else {
      updated = [item, ...watchlist];
    }
    setWatchlist(updated);
    try {
      localStorage.setItem('munowatch_watchlist', JSON.stringify(updated));
    } catch {}
  };

  const handleBack = () => {
    if (window.history.length > 1) {
      window.history.back();
    } else {
      if (playerTarget) {
        setPlayerTarget(null);
        if (selectedItem) {
          window.location.hash = `#/title/${encodeURIComponent(selectedItem.id)}`;
        } else {
          navigateTo(previousScreen || 'home');
        }
      } else if (selectedItem) {
        setSelectedItem(null);
        navigateTo(previousScreen || 'home');
      } else {
        navigateTo('home');
      }
    }
  };

  // 1. If Video Player is active, render full-screen immersive cinema player
  if (playerTarget) {
    return (
      <VideoPlayer
        item={playerTarget.item}
        episode={playerTarget.episode}
        initialServer={playerTarget.server}
        onBack={handleBack}
        onSelectMovie={handleItemClick}
      />
    );
  }

  return (
    <div className="min-h-screen bg-[#010318] text-[#F8FAFC] pb-20 md:pb-8 flex flex-col">
      {/* Universal TopBar with munowatch VIP & responsive navigation on EVERY page */}
      <TvTopBar
        currentRoute={selectedItem ? previousScreen : currentScreen}
        onNavigate={navigateTo}
      />

      {/* Main Screen Views */}
      <div className="flex-1 w-full">
        {/* If Details View is active, render ContentDetails below Universal TopBar */}
        {selectedItem ? (
          <ContentDetails
            item={selectedItem}
            allMovies={[...allMovies, ...allSeries]}
            onPlay={handlePlay}
            onBack={handleBack}
            onSelectMovie={handleItemClick}
            onToggleWatchlist={toggleFavorite}
            isInWatchlist={watchlist.some(w => w.id === selectedItem.id)}
          />
        ) : (
          <>
            {/* --- SCREEN: HOME --- */}
            {currentScreen === 'home' && (
              <main className="p-4 sm:p-8 md:p-10 w-full max-w-[2400px] mx-auto space-y-6">
            {/* 1. Featured Hero Banner matching TvHeroBanner in HomeScreen.kt */}
            {featuredItem && (
              <TvHeroBanner
                item={featuredItem}
                isFavorite={watchlist.some(w => w.id === featuredItem.id)}
                onPlayClick={() => handlePlay(featuredItem)}
                onFavoriteClick={() => toggleFavorite(featuredItem)}
                onDetailsClick={() => handleItemClick(featuredItem)}
              />
            )}

            {/* 2. Continue Watching (if exists) matching Android ContinueWatching section */}
            {continueWatching.length > 0 && (
              <TvContentSection
                title="Continue Watching"
                subtitle="Resume where you left off"
              >
                {continueWatching.map(cw => (
                  <div
                    key={cw.id}
                    onClick={() => {
                      const fullItem = allMovies.find(m => m.id === cw.id) || allSeries.find(s => s.id === cw.id) || featuredItem;
                      if (fullItem) handlePlay(fullItem);
                    }}
                    className="flex-shrink-0 w-44 sm:w-56 cursor-pointer select-none tv-card-focus group rounded-xl overflow-hidden bg-[#0A192F] border border-[#1E293B]"
                  >
                    <div className="relative aspect-video w-full bg-[#132845] overflow-hidden">
                      <img src={cw.posterUrl} alt={cw.title} className="w-full h-full object-cover" />
                      {/* Progress bar */}
                      <div className="absolute bottom-0 left-0 right-0 h-1 bg-white/20">
                        <div className="h-full bg-[#10B981]" style={{ width: `${cw.progress || 40}%` }} />
                      </div>
                    </div>
                    <div className="p-2.5">
                      <h4 className="text-xs font-bold text-white truncate group-hover:text-[#34D399]">
                        {cw.title}
                      </h4>
                      <span className="text-[10px] text-[#10B981] font-semibold">{cw.vj || 'Ugandan Translated'}</span>
                    </div>
                  </div>
                ))}
              </TvContentSection>
            )}

            {/* 3. My Watchlist Row (if saved) matching Android HomeScreen */}
            {watchlist.length > 0 && (
              <TvContentSection
                title="My Watchlist"
                subtitle="Saved movies & series for later"
              >
                {watchlist.map(item => (
                  <TvMediaCard
                    key={`wl_${item.id}`}
                    item={item}
                    onClick={() => handleItemClick(item)}
                  />
                ))}
              </TvContentSection>
            )}

            {/* 4. Latest Translated Movies */}
            <TvContentSection
              title="Latest Translated Movies"
              subtitle="Freshly dubbed and updated releases"
            >
              {allMovies.slice(0, 10).map(movie => (
                <TvMediaCard
                  key={movie.id}
                  item={movie}
                  onClick={() => handleItemClick(movie)}
                />
              ))}
            </TvContentSection>

            {/* 5. Popular Translated Series matching Android HomeScreen */}
            {allSeries.length > 0 && (
              <TvContentSection
                title="Popular Series"
                subtitle="Full seasons translated with Veejay commentary"
              >
                {allSeries.map(s => (
                  <TvMediaCard
                    key={s.id}
                    item={s}
                    onClick={() => handleItemClick(s)}
                  />
                ))}
              </TvContentSection>
            )}

            {/* 6. Featured Ugandan Video Jockeys (VJs) chips matching TvVjChip in HomeScreen */}
            <TvContentSection
              title="Ugandan Video Jockeys (VJs)"
              subtitle="Browse by your favorite translator"
            >
              {vjList.map(vj => (
                <TvVjChip
                  key={vj.id}
                  vj={vj}
                  onClick={() => navigateTo('categories')}
                />
              ))}
            </TvContentSection>

            {/* 7. Catalog Rails */}
            {homeRails.map(rail => (
              <TvContentSection
                key={rail.id}
                title={rail.title}
              >
                {rail.items.map(item => (
                  <TvMediaCard
                    key={item.id}
                    item={item}
                    onClick={() => handleItemClick(item)}
                  />
                ))}
              </TvContentSection>
            ))}
          </main>
        )}

        {/* --- SCREEN: MOVIES (Clone of MoviesScreen.kt) --- */}
        {currentScreen === 'movies' && (
          <MoviesScreen
            movies={allMovies}
            onMovieClick={handleItemClick}
          />
        )}

        {/* --- SCREEN: SERIES (Clone of SeriesScreen.kt) --- */}
        {currentScreen === 'series' && (
          <SeriesScreen
            series={allSeries}
            onSeriesClick={handleItemClick}
          />
        )}

        {/* --- SCREEN: CATEGORIES (Clone of CategoriesScreen.kt) --- */}
        {currentScreen === 'categories' && (
          <CategoriesScreen
            movies={[...allMovies, ...allSeries]}
            vjs={vjList}
            onItemClick={handleItemClick}
          />
        )}

        {/* --- SCREEN: SEARCH (Clone of SearchScreen.kt with TV Keypad) --- */}
        {currentScreen === 'search' && (
          <SearchScreen
            onItemClick={handleItemClick}
            onPlay={handlePlay}
          />
        )}

        {/* --- SCREEN: SETTINGS (Clone of SettingsScreen.kt) --- */}
        {currentScreen === 'settings' && (
          <SettingsScreen />
        )}
          </>
        )}
      </div>

      {/* Mobile Bottom Navigation for phone thumb reachability (< 768px) */}
      <MunowatchMobileNav
        currentScreen={currentScreen}
        onNavigate={navigateTo}
      />
    </div>
  );
};

export default App;
