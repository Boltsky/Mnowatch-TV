import React from 'react';
import { Film, Search, Clapperboard } from 'lucide-react';

interface HeaderProps {
  onSearchClick: () => void;
  onNavigateHome: () => void;
}

export const MunowatchHeader: React.FC<HeaderProps> = ({ onSearchClick, onNavigateHome }) => {
  return (
    <header className="sticky top-0 z-30 flex items-center justify-between px-4 sm:px-6 md:px-8 py-3.5 bg-[#0a0f0d]/80 backdrop-blur-md border-b border-muno-border/40">
      <div className="flex items-center gap-3 cursor-pointer" onClick={onNavigateHome}>
        <div className="w-8 h-8 rounded-lg bg-emerald-500 flex items-center justify-center shadow-md shadow-emerald-500/20">
          <Film className="w-5 h-5 text-black" />
        </div>
        <div className="flex items-baseline gap-1.5">
          <span className="font-extrabold text-lg sm:text-xl tracking-wider text-white">MUNOWATCH</span>
          <span className="text-[10px] font-black uppercase text-emerald-400 tracking-widest bg-emerald-500/10 px-1.5 py-0.5 rounded border border-emerald-500/20">
            ONLINE
          </span>
        </div>
      </div>

      <div className="flex items-center gap-3">
        <button
          onClick={onSearchClick}
          className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-white/5 border border-muno-border text-zinc-300 hover:text-white hover:border-emerald-500/50 transition-all text-xs sm:text-sm active:scale-95"
        >
          <Search className="w-4 h-4 text-emerald-400" />
          <span className="hidden sm:inline">Search VJ or Title...</span>
        </button>
      </div>
    </header>
  );
};
