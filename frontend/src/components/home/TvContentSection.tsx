import React, { useRef } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

interface TvContentSectionProps {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}

export const TvContentSection: React.FC<TvContentSectionProps> = ({ title, subtitle, children }) => {
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  const scroll = (direction: 'left' | 'right') => {
    if (scrollContainerRef.current) {
      const scrollAmount = direction === 'left' ? -420 : 420;
      scrollContainerRef.current.scrollBy({ left: scrollAmount, behavior: 'smooth' });
    }
  };

  return (
    <div className="relative my-7 group">
      {/* Title & Subtitle header matching Compose TvContentSection */}
      <div className="mb-3 px-1">
        <h2 className="text-base sm:text-xl font-bold text-[#F8FAFC] tracking-wide">
          {title}
        </h2>
        {subtitle && (
          <p className="text-xs text-[#94A3B8] font-normal mt-0.5">
            {subtitle}
          </p>
        )}
      </div>

      {/* Chevron left for desktop hover */}
      <button
        onClick={() => scroll('left')}
        className="hidden md:flex absolute -left-4 top-[58%] -translate-y-1/2 z-30 w-10 h-10 rounded-full bg-[#0A192F]/90 hover:bg-[#10B981] hover:text-black text-white border border-[#1E293B] items-center justify-center opacity-0 group-hover:opacity-100 transition-all shadow-xl"
      >
        <ChevronLeft className="w-5 h-5" />
      </button>

      {/* Row Container */}
      <div
        ref={scrollContainerRef}
        className="flex items-center gap-4 overflow-x-auto no-scrollbar scroll-smooth py-2 px-1"
        style={{ WebkitOverflowScrolling: 'touch' }}
      >
        {children}
      </div>

      {/* Chevron right for desktop hover */}
      <button
        onClick={() => scroll('right')}
        className="hidden md:flex absolute -right-4 top-[58%] -translate-y-1/2 z-30 w-10 h-10 rounded-full bg-[#0A192F]/90 hover:bg-[#10B981] hover:text-black text-white border border-[#1E293B] items-center justify-center opacity-0 group-hover:opacity-100 transition-all shadow-xl"
      >
        <ChevronRight className="w-5 h-5" />
      </button>
    </div>
  );
};
