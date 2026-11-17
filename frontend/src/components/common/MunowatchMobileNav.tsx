import React from 'react';
import { Home, Film, Tv, Layers, Search, Settings } from 'lucide-react';

interface MobileNavProps {
  currentScreen: string;
  onNavigate: (screen: string) => void;
}

export const MunowatchMobileNav: React.FC<MobileNavProps> = ({ currentScreen, onNavigate }) => {
  const tabs = [
    { id: 'home', label: 'Home', icon: Home },
    { id: 'movies', label: 'Movies', icon: Film },
    { id: 'series', label: 'Series', icon: Tv },
    { id: 'categories', label: 'Categories', icon: Layers },
    { id: 'search', label: 'Search', icon: Search },
    { id: 'settings', label: 'Settings', icon: Settings },
  ];

  return (
    <nav 
      aria-label="Mobile Navigation"
      className="fixed bottom-0 left-0 right-0 z-50 md:hidden bg-[#010318]/95 backdrop-blur-xl border-t border-[#132845]/90 pt-1.5 px-1 shadow-[0_-4px_20px_rgba(0,0,0,0.6)]"
      style={{ paddingBottom: 'max(env(safe-area-inset-bottom, 0px), 8px)' }}
    >
      <div className="grid grid-cols-6 items-center max-w-lg mx-auto">
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const isActive = currentScreen === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => onNavigate(tab.id)}
              className={`flex flex-col items-center justify-center py-1 rounded-xl transition-all active:scale-90 select-none ${
                isActive ? 'text-[#10B981]' : 'text-[#94A3B8] hover:text-white'
              }`}
            >
              <div className="relative">
                <Icon className={`w-5 h-5 transition-transform ${isActive ? 'scale-110' : ''}`} />
              </div>
              <span className={`text-[10px] tracking-tight mt-1 transition-all ${
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
