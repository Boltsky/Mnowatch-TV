import React from 'react';
import { Home, Film, Tv, Layers, Download, Search, Settings } from 'lucide-react';

interface MobileNavProps {
  currentScreen: string;
  onNavigate: (screen: string) => void;
}

export const MunowatchMobileNav: React.FC<MobileNavProps> = ({ currentScreen, onNavigate }) => {
  const tabs = [
    { id: 'home', label: 'Home', icon: Home },
    { id: 'movies', label: 'Movies', icon: Film },
    { id: 'series', label: 'Series', icon: Tv },
    { id: 'categories', label: 'Genres', icon: Layers },
    { id: 'downloads', label: 'Offline', icon: Download },
    { id: 'search', label: 'Search', icon: Search },
    { id: 'settings', label: 'Settings', icon: Settings },
  ];

  return (
    <nav 
      aria-label="Mobile Navigation"
      className="fixed bottom-0 left-0 right-0 z-50 md:hidden bg-[#010318]/95 backdrop-blur-xl border-t border-[#132845]/90 pt-1.5 px-0.5 sm:px-2 shadow-[0_-4px_24px_rgba(0,0,0,0.7)] select-none"
      style={{ paddingBottom: 'max(env(safe-area-inset-bottom, 0px), 8px)' }}
    >
      <div className="grid grid-cols-7 items-center max-w-md mx-auto">
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const isActive = currentScreen === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => onNavigate(tab.id)}
              className={`flex flex-col items-center justify-center min-h-[44px] py-1 px-0.5 rounded-xl transition-all active:scale-90 touch-manipulation ${
                isActive ? 'text-[#10B981]' : 'text-[#94A3B8] hover:text-white'
              }`}
            >
              <div className="relative">
                <Icon className={`w-4 h-4 sm:w-5 sm:h-5 transition-transform ${isActive ? 'scale-110 text-[#10B981]' : ''}`} />
              </div>
              <span className={`text-[9px] sm:text-[10px] tracking-tight mt-0.5 truncate max-w-full transition-all ${
                isActive ? 'font-black text-[#10B981]' : 'font-medium'
              }`}>
                {tab.label}
              </span>
              {isActive && (
                <span className="w-1 h-1 rounded-full bg-[#10B981] shadow-[0_0_6px_#10B981] mt-0.5" />
              )}
            </button>
          );
        })}
      </div>
    </nav>
  );
};
