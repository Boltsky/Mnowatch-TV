import React from 'react';
import { VjItem } from '../../types';

interface TvVjChipProps {
  vj: VjItem;
  onClick: () => void;
}

export const TvVjChip: React.FC<TvVjChipProps> = ({ vj, onClick }) => {
  return (
    <div
      onClick={onClick}
      className="flex-shrink-0 flex items-center gap-3 px-5 py-3 rounded-xl bg-[#0A192F] hover:bg-[#132845] border border-[#1E293B] hover:border-[#10B981] cursor-pointer select-none transition-all tv-card-focus shadow-md"
    >
      <div className="w-10 h-10 rounded-full bg-[#132845] border border-[#10B981]/40 flex items-center justify-center text-xl shadow">
        {vj.icon}
      </div>
      <div className="flex flex-col">
        <span className="text-sm font-bold text-white group-hover:text-[#34D399]">
          {vj.name}
        </span>
        <span className="text-[11px] text-[#94A3B8]">
          {vj.count}+ Movies
        </span>
      </div>
    </div>
  );
};
