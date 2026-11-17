import React, { useState, useMemo } from 'react';
import { MediaItem } from '../../types';
import { TvMediaCard } from '../common/TvMediaCard';
import { Tv, Sparkles } from 'lucide-react';

interface SeriesScreenProps {
  series: MediaItem[];
  onSeriesClick: (item: MediaItem) => void;
}

export const SeriesScreen: React.FC<SeriesScreenProps> = ({ series, onSeriesClick }) => {
  const [selectedVj, setSelectedVj] = useState<string | null>(null);

  const availableVjs = useMemo(() => {
    const vjs = new Set<string>();
    series.forEach(s => {
      if (s.vj) vjs.add(s.vj);
    });
    return Array.from(vjs).sort();
  }, [series]);

  const displaySeries = useMemo(() => {
    if (!selectedVj) return series;
    return series.filter(s => s.vj.toLowerCase().includes(selectedVj.toLowerCase()));
  }, [series, selectedVj]);

  return (
    <div className="p-4 sm:p-9 max-w-7xl mx-auto space-y-6 animate-fadeIn">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-[#132845] pb-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-[#10B981]/15 border border-[#10B981]/40 flex items-center justify-center">
            <Tv className="w-5 h-5 text-[#10B981]" />
          </div>
          <div>
            <h1 className="text-xl sm:text-2xl font-black text-white">Ugandan Translated Series</h1>
            <p className="text-xs text-[#94A3B8]">Full multi-season series with Veejay voiceovers</p>
          </div>
        </div>

        <span className="text-xs font-bold text-[#94A3B8]">
          <strong className="text-white">{displaySeries.length}</strong> Series
        </span>
      </div>

      {/* VJ Filter Pills */}
      <div className="flex items-center gap-2 overflow-x-auto no-scrollbar py-1">
        <button
          onClick={() => setSelectedVj(null)}
          className={`px-3 py-1.5 rounded-lg text-xs font-bold whitespace-nowrap transition-all ${
            selectedVj === null
              ? 'bg-[#10B981] text-black shadow-md'
              : 'bg-[#0A192F] text-[#94A3B8] hover:text-white border border-[#1E293B]'
          }`}
        >
          All Series
        </button>
        {availableVjs.map(vj => (
          <button
            key={vj}
            onClick={() => setSelectedVj(selectedVj === vj ? null : vj)}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold whitespace-nowrap transition-all ${
              selectedVj === vj
                ? 'bg-[#10B981] text-black shadow-md'
                : 'bg-[#0A192F] text-[#94A3B8] hover:text-white border border-[#1E293B]'
            }`}
          >
            {vj}
          </button>
        ))}
      </div>

      {/* Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3 sm:gap-4 pt-2">
        {displaySeries.map((item) => (
          <TvMediaCard
            key={item.id}
            item={item}
            onClick={() => onSeriesClick(item)}
            cardWidth="w-full"
          />
        ))}
      </div>
    </div>
  );
};
