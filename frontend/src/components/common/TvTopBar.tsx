import React from 'react';
import { Crown, Search } from 'lucide-react';

interface TvTopBarProps {
  currentRoute: string;
  onNavigate: (route: string) => void;
}

export const TvTopBar: React.FC<TvTopBarProps> = ({ currentRoute, onNavigate }) => {
  const navItems = [
    { route: 'home', title: 'Home' },
    { route: 'movies', title: 'Movies' },
    { route: 'series', title: 'Series' },
    { route: 'categories', title: 'Categories' },
    { route: 'search', title: 'Search' },
    { route: 'settings', title: 'Settings' },
  ];

  return (
    <header className="sticky top-0 z-40 w-full bg-[#010318]/90 backdrop-blur-md px-3.5 sm:px-9 py-2.5 sm:py-3.5 border-b border-[#132845]/50 flex items-center justify-between transition-all">
      {/* Brand Logo & TV Badge matching Android TvTopBar */}
      <div 
        className="flex items-center gap-2 cursor-pointer select-none"
        onClick={() => onNavigate('home')}
      >
        <div className="flex items-baseline tracking-tight">
          <span className="font-black text-xl sm:text-2xl text-white">MUNO</span>
          <span className="font-black text-xl sm:text-2xl text-[#10B981]">WATCH</span>
        </div>
        <div className="px-1.5 py-0.5 rounded border border-[#10B981] bg-[#10B981]/15">
          <span className="text-[10px] font-black text-[#10B981] tracking-wider leading-none">TV</span>
        </div>
      </div>

      {/* Navigation Tabs: visible on tablets, computers, and TVs; mobile uses bottom nav */}
      <nav className="hidden md:flex items-center gap-1 sm:gap-2">
        {navItems.map((item) => {
          const isSelected = currentRoute === item.route;
          return (
            <button
              key={item.route}
              onClick={() => onNavigate(item.route)}
              className={`px-3 sm:px-4 py-1.5 rounded-lg text-xs sm:text-sm font-semibold transition-all tv-nav-focus ${
                isSelected
                  ? 'bg-[#10B981] text-black font-extrabold shadow-lg shadow-[#10B981]/30 scale-105'
                  : 'text-[#94A3B8] hover:text-white hover:bg-white/5'
              }`}
            >
              {item.title}
            </button>
          );
        })}
      </nav>

      {/* Right Action Area: Search shortcut (mobile) + munowatch VIP badge (all screens) */}
      <div className="flex items-center gap-2">
        <button
          onClick={() => onNavigate('search')}
          className="md:hidden p-1.5 rounded-lg text-[#94A3B8] hover:text-white hover:bg-white/5 active:scale-95"
          aria-label="Search Catalog"
        >
          <Search className="w-5 h-5 text-[#10B981]" />
        </button>

        <div 
          onClick={() => onNavigate('settings')}
          className="flex items-center gap-1.5 sm:gap-2 px-2.5 sm:px-3.5 py-1 sm:py-1.5 rounded-full bg-gradient-to-r from-[#10B981]/15 to-[#132845] border border-[#10B981]/50 shadow-sm shadow-[#10B981]/20 cursor-pointer hover:border-[#10B981] transition-all"
        >
          <Crown className="w-3.5 h-3.5 sm:w-4 sm:h-4 text-amber-400 fill-amber-400" />
          <span className="text-[10px] sm:text-xs font-black tracking-wider text-white uppercase select-none">
            munowatch <span className="text-[#10B981]">VIP</span>
          </span>
        </div>
      </div>
    </header>
  );
};
