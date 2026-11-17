import React from 'react';
import { Home, Film, Tv, Search, Clapperboard } from 'lucide-react';

interface MobileNavProps {
  currentScreen: string;
  onNavigate: (screen: string) => void;
}

export const MunowatchMobileNav: React.FC<MobileNavProps> = ({ currentScreen, onNavigate }) => {
  const tabs = [
    { id: 'home', label: 'Home', icon: Home },
    { id: 'movies', label: 'Movies', icon: Film },
    { id: 'series', label: 'Series', icon: Tv },
    { id: 'vjs', label: 'VJs', icon: Clapperboard },
    { id: 'search', label: 'Search', icon: Search },
  ];

  return (
    <div className="fixed bottom-0 left-0 right-0 z-50 md:hidden bg-[#0a0f0d]/95 backdrop-blur-lg border-t border-muno-border px-2 py-2 flex items-center justify-around pb-safe">
      {tabs.map((tab) => {
        const Icon = tab.icon;
        const isActive = currentScreen === tab.id;
        return (
          <button
            key={tab.id}
            onClick={() => onNavigate(tab.id)}
            className={`flex flex-col items-center justify-center py-1 px-3 rounded-lg min-w-[56px] min-h-[44px] transition-all active:scale-90 ${
              isActive ? 'text-emerald-400 font-bold' : 'text-zinc-400 hover:text-white'
            }`}
          >
            <Icon className={`w-5 h-5 mb-1 ${isActive ? 'text-emerald-400' : ''}`} />
            <span className="text-[10px] tracking-tight">{tab.label}</span>
          </button>
        );
      })}
    </div>
  );
};
