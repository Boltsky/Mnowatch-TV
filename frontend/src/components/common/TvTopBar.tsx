import React from 'react';
import { Search } from 'lucide-react';

interface TvTopBarProps {
  currentRoute: string;
  onNavigate: (route: string) => void;
  subscriptionDays?: number;
}

export const TvTopBar: React.FC<TvTopBarProps> = ({ currentRoute, onNavigate, subscriptionDays = 28 }) => {
  const navItems = [
    { route: 'home', title: 'Home' },
    { route: 'movies', title: 'Movies' },
    { route: 'series', title: 'Series' },
    { route: 'categories', title: 'Categories' },
    { route: 'search', title: 'Search' },
    { route: 'settings', title: 'Settings' },
  ];

  return (
    <header className="sticky top-0 z-40 w-full bg-[#010318]/90 backdrop-blur-md px-4 sm:px-9 py-3 sm:py-3.5 border-b border-[#132845]/50 flex items-center justify-between">
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

      {/* Navigation Tabs matching TvNavTab */}
      <nav className="flex items-center gap-1 sm:gap-2">
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

      {/* Subscription Days Badge matching TvTopBar */}
      <div className="hidden md:flex items-center gap-2 px-3 py-1.5 rounded-full bg-[#132845] border border-[#10B981]/40">
        <span className="w-2 h-2 rounded-full bg-[#10B981] animate-pulse" />
        <span className="text-xs font-semibold text-[#94A3B8]">
          <strong className="text-white">{subscriptionDays}</strong> Days Left
        </span>
      </div>
    </header>
  );
};
