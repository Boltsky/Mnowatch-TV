import React, { useState } from 'react';
import { Home, Film, Tv, Search, Bookmark, Settings, Clapperboard, ChevronRight } from 'lucide-react';

interface SidebarProps {
  currentScreen: string;
  onNavigate: (screen: string) => void;
}

export const MunowatchSidebar: React.FC<SidebarProps> = ({ currentScreen, onNavigate }) => {
  const [isExpanded, setIsExpanded] = useState(false);

  const navItems = [
    { id: 'home', label: 'Home', icon: Home },
    { id: 'movies', label: 'Movies', icon: Film },
    { id: 'series', label: 'TV Series', icon: Tv },
    { id: 'vjs', label: 'VJ Catalog', icon: Clapperboard },
    { id: 'search', label: 'Search', icon: Search },
    { id: 'watchlist', label: 'My Watchlist', icon: Bookmark },
    { id: 'settings', label: 'Settings', icon: Settings },
  ];

  return (
    <aside
      className={`fixed left-0 top-0 bottom-0 z-40 hidden md:flex flex-col bg-[#0a0f0d]/95 backdrop-blur-md border-r border-muno-border transition-all duration-300 ease-in-out ${
        isExpanded ? 'w-64 shadow-2xl shadow-black/80' : 'w-20'
      }`}
      onMouseEnter={() => setIsExpanded(true)}
      onMouseLeave={() => setIsExpanded(false)}
    >
      {/* Brand Header */}
      <div className="h-20 flex items-center px-5 gap-3 border-b border-muno-border/50">
        <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-emerald-500 to-emerald-700 flex items-center justify-center shadow-lg shadow-emerald-500/20 flex-shrink-0">
          <Film className="w-6 h-6 text-black" />
        </div>
        {isExpanded && (
          <div className="flex flex-col overflow-hidden">
            <span className="font-black text-lg tracking-wider text-white">MUNOWATCH</span>
            <span className="text-[10px] uppercase font-bold text-emerald-400 tracking-widest -mt-1">TV & STREAM</span>
          </div>
        )}
      </div>

      {/* Navigation Links */}
      <nav className="flex-1 py-6 px-3 space-y-2">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = currentScreen === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onNavigate(item.id)}
              className={`w-full flex items-center gap-4 px-3.5 py-3 rounded-xl font-medium transition-all duration-200 group relative ${
                isActive
                  ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30'
                  : 'text-zinc-400 hover:text-white hover:bg-white/5'
              }`}
            >
              <Icon className={`w-6 h-6 flex-shrink-0 transition-transform group-hover:scale-110 ${isActive ? 'text-emerald-400' : ''}`} />
              {isExpanded && (
                <span className="truncate text-sm font-semibold tracking-wide">
                  {item.label}
                </span>
              )}
              {isActive && (
                <div className="absolute left-0 top-2 bottom-2 w-1 bg-emerald-500 rounded-r-full" />
              )}
            </button>
          );
        })}
      </nav>

      {/* Footer Info */}
      {isExpanded && (
        <div className="p-4 border-t border-muno-border/50 text-[11px] text-zinc-500 text-center">
          Munowatch Web v1.2 • Zero Buffer
        </div>
      )}
    </aside>
  );
};
