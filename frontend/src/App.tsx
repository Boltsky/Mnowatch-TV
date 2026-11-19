import React, { useState, useEffect, useCallback, useRef, useLayoutEffect } from 'react';
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

  // Set of visited screens for Keep-Alive caching (lazy-mount on first visit, preserve thereafter)
  const [visitedScreens, setVisitedScreens] = useState<Set<string>>(() => {
    const rawHash = window.location.hash.replace(/^#\/?/, '').trim().toLowerCase();
    const screen = ['movies', 'series', 'categories', 'search', 'settings'].find(s => rawHash.startsWith(s)) || 'home';
    return new Set(['home', screen]);
  });

  // Track per-screen scroll positions to resume exact scroll coordinates on Back
  const scrollPositions = useRef<Record<string, number>>({});
  const prevSelectedItemRef = useRef<MediaItem | null>(null);
  const prevPlayerTargetRef = useRef<any>(null);

  // Keep visitedScreens updated whenever currentScreen changes
  useEffect(() => {
    setVisitedScreens(prev => {
      if (prev.has(currentScreen)) return prev;
      const next = new Set(prev);
      next.add(currentScreen);
      return next;
    });
  }, [currentScreen]);

  // Set manual scroll restoration so native browser popstate does not jump to top
  useEffect(() => {
    if ('scrollRestoration' in window.history) {
      window.history.scrollRestoration = 'manual';
    }
  }, []);

  // Continuously track scroll position of the active catalog screen when not viewing details/player
  useEffect(() => {
    const handleScroll = () => {
      if (!selectedItem && !playerTarget) {
        const y = window.scrollY || window.pageYOffset || document.documentElement.scrollTop || 0;
        scrollPositions.current[currentScreen] = y;
      }
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, [currentScreen, selectedItem, playerTarget]);

  // Instant scroll position restoration when returning from ContentDetails or VideoPlayer
  useLayoutEffect(() => {
    const justClosedDetails = prevSelectedItemRef.current && !selectedItem;
    const justClosedPlayer = prevPlayerTargetRef.current && !playerTarget;

    if (justClosedDetails || (justClosedPlayer && !selectedItem)) {
      const targetY = scrollPositions.current[currentScreen] || 0;
      window.scrollTo({ top: targetY, behavior: 'instant' });
      requestAnimationFrame(() => {
        window.scrollTo({ top: targetY, behavior: 'instant' });
        requestAnimationFrame(() => {
          window.scrollTo({ top: targetY, behavior: 'instant' });
        });
      });
    }

    prevSelectedItemRef.current = selectedItem;
    prevPlayerTargetRef.current = playerTarget;
  }, [selectedItem, playerTarget, currentScreen]);

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
    // If clicking current active tab when not in details, smoothly scroll to top
    if (screen === currentScreen && !selectedItem && !playerTarget) {
      scrollPositions.current[screen] = 0;
      window.scrollTo({ top: 0, behavior: 'smooth' });
      return;
    }

    // Save scroll position of current screen before navigating away
    if (!selectedItem && !playerTarget) {
      const y = window.scrollY || window.pageYOffset || document.documentElement.scrollTop || 0;
      scrollPositions.current[currentScreen] = y;
    }

    setPreviousScreen(currentScreen);
    setCurrentScreen(screen);
    setSelectedItem(null);
    setPlayerTarget(null);
    window.location.hash = `#/${screen}`;

    const targetY = scrollPositions.current[screen] || 0;
    requestAnimationFrame(() => {
      window.scrollTo({ top: targetY, behavior: 'instant' });
      requestAnimationFrame(() => {
        window.scrollTo({ top: targetY, behavior: 'instant' });
      });
    });
  };

  const handleItemClick = (item: MediaItem) => {
    // Save scroll position of current screen before opening movie details
    if (!selectedItem) {
      const y = window.scrollY || window.pageYOffset || document.documentElement.scrollTop || 0;
      scrollPositions.current[currentScreen] = y;
    }
    setPreviousScreen(currentScreen);
    setSelectedItem(item);
    window.location.hash = `#/title/${encodeURIComponent(item.id)}`;
    window.scrollTo({ top: 0, behavior: 'instant' });
  };

  const handlePlay = (item: MediaItem, episode?: Episode, server?: PlaybackServer) => {
    if (!selectedItem) {
      const y = window.scrollY || window.pageYOffset || document.documentElement.scrollTop || 0;
      scrollPositions.current[currentScreen] = y;
    }
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

  return (
    <div className="min-h-screen bg-[#010318] text-[#F8FAFC] pb-20 md:pb-8 flex flex-col">
      {/* 1. Fullscreen Video Player overlay */}
      {playerTarget && (
        <VideoPlayer
          item={playerTarget.item}
          episode={playerTarget.episode}
          initialServer={playerTarget.server}
          onBack={handleBack}
          onSelectMovie={handleItemClick}
        />
      )}

      {/* Main App view (hidden when VideoPlayer is active so player has total immersion) */}
      <div style={{ display: playerTarget ? 'none' : 'flex' }} className="flex-col flex-1">
        {/* Universal TopBar with munowatch VIP & responsive navigation on EVERY page */}
        <TvTopBar
          currentRoute={selectedItem ? previousScreen : currentScreen}
          onNavigate={navigateTo}
        />

        {/* Main Screen Views */}
        <div className="flex-1 w-full">
          {/* If Details View is active, render ContentDetails below Universal TopBar */}
          {selectedItem && (
            <ContentDetails
              item={selectedItem}
              allMovies={[...allMovies, ...allSeries]}
              onPlay={handlePlay}
              onBack={handleBack}
              onSelectMovie={handleItemClick}
              onToggleWatchlist={toggleFavorite}
              isInWatchlist={watchlist.some(w => w.id === selectedItem.id)}
            />
          )}

          {/* Preserved Catalog Views: hidden while ContentDetails is open, preserving all loaded movies, filters, and scroll position */}
          <div style={{ display: selectedItem ? 'none' : 'block' }}>
            {/* --- SCREEN: HOME --- */}
            {visitedScreens.has('home') && (
              <div style={{ display: currentScreen === 'home' ? 'block' : 'none' }}>
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
              </div>
            )}

            {/* --- SCREEN: MOVIES (Clone of MoviesScreen.kt) --- */}
            {visitedScreens.has('movies') && (
              <div style={{ display: currentScreen === 'movies' ? 'block' : 'none' }}>
                <MoviesScreen
                  movies={allMovies}
                  onMovieClick={handleItemClick}
                />
              </div>
            )}

            {/* --- SCREEN: SERIES (Clone of SeriesScreen.kt) --- */}
            {visitedScreens.has('series') && (
              <div style={{ display: currentScreen === 'series' ? 'block' : 'none' }}>
                <SeriesScreen
                  series={allSeries}
                  onSeriesClick={handleItemClick}
                />
              </div>
            )}

            {/* --- SCREEN: CATEGORIES (Clone of CategoriesScreen.kt) --- */}
            {visitedScreens.has('categories') && (
              <div style={{ display: currentScreen === 'categories' ? 'block' : 'none' }}>
                <CategoriesScreen
                  movies={[...allMovies, ...allSeries]}
                  vjs={vjList}
                  onItemClick={handleItemClick}
                />
              </div>
            )}

            {/* --- SCREEN: SEARCH (Clone of SearchScreen.kt with TV Keypad) --- */}
            {visitedScreens.has('search') && (
              <div style={{ display: currentScreen === 'search' ? 'block' : 'none' }}>
                <SearchScreen
                  onItemClick={handleItemClick}
                  onPlay={handlePlay}
                />
              </div>
            )}

            {/* --- SCREEN: SETTINGS (Clone of SettingsScreen.kt) --- */}
            {visitedScreens.has('settings') && (
              <div style={{ display: currentScreen === 'settings' ? 'block' : 'none' }}>
                <SettingsScreen />
              </div>
            )}
          </div>
        </div>

        {/* Mobile Bottom Navigation for phone thumb reachability (< 768px) */}
        <MunowatchMobileNav
          currentScreen={currentScreen}
          onNavigate={navigateTo}
        />
      </div>
    </div>
  );
};

export default App;
