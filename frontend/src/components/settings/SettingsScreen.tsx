import React, { useState } from 'react';
import { ShieldCheck, Server, Trash2, Cpu, CheckCircle2, RotateCcw, Monitor, Crown } from 'lucide-react';

export const SettingsScreen: React.FC = () => {
  const [preferredServer, setPreferredServer] = useState<'s1' | 's2'>('s2');
  const [preferredQuality, setPreferredQuality] = useState<'1080p' | '720p' | 'auto'>('1080p');
  const [historyCleared, setHistoryCleared] = useState(false);

  const clearHistory = () => {
    localStorage.removeItem('munowatch_history');
    setHistoryCleared(true);
    setTimeout(() => setHistoryCleared(false), 3000);
  };

  return (
    <div className="p-4 sm:p-9 max-w-4xl mx-auto space-y-6 animate-fadeIn pb-24 md:pb-12">
      {/* Title */}
      <div className="border-b border-[#132845] pb-4">
        <h1 className="text-xl sm:text-2xl font-black text-white">Settings & Playback</h1>
        <p className="text-xs text-[#94A3B8]">Configure streaming parameters and application preferences</p>
      </div>

      {/* 1. Subscription Status Card matching Android TV Settings */}
      <div className="p-5 rounded-2xl bg-[#0A192F] border border-[#10B981]/40 shadow-xl space-y-3">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-[#10B981]/20 border border-[#10B981] flex items-center justify-center flex-shrink-0">
              <ShieldCheck className="w-6 h-6 text-[#10B981]" />
            </div>
            <div>
              <h2 className="text-sm sm:text-base font-bold text-white">Munowatch VIP Membership</h2>
              <span className="text-xs text-[#10B981] font-semibold flex items-center gap-1">
                <CheckCircle2 className="w-3.5 h-3.5" />
                Active Premium VIP Member
              </span>
            </div>
          </div>
          <div className="self-start sm:self-auto flex items-center gap-1.5 px-3.5 py-1.5 rounded-full bg-[#10B981]/15 border border-[#10B981] text-xs font-black text-[#10B981] tracking-wide uppercase shadow-sm">
            <Crown className="w-3.5 h-3.5 text-amber-400 fill-amber-400" />
            <span>munowatch VIP</span>
          </div>
        </div>
      </div>

      {/* 2. Streaming Server Preference */}
      <div className="p-5 rounded-2xl bg-[#0A192F] border border-[#132845] shadow-xl space-y-4">
        <h2 className="text-sm font-bold text-white flex items-center gap-2">
          <Server className="w-4 h-4 text-[#34D399]" />
          Default Media Delivery Server
        </h2>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <button
            onClick={() => setPreferredServer('s2')}
            className={`p-4 rounded-xl border text-left transition-all ${
              preferredServer === 's2'
                ? 'bg-[#132845] border-[#10B981] text-white shadow-lg shadow-[#10B981]/15'
                : 'bg-[#010318]/60 border-[#1E293B] text-[#94A3B8] hover:text-white'
            }`}
          >
            <div className="flex items-center justify-between mb-1">
              <span className="font-bold text-sm text-[#10B981]">Server 2 (CORS-Safe Proxy)</span>
              {preferredServer === 's2' && <CheckCircle2 className="w-4 h-4 text-[#10B981]" />}
            </div>
            <p className="text-xs text-[#94A3B8]">
              Recommended for all browsers. Bypasses cross-origin restrictions with HTTP 206 range seeking.
            </p>
          </button>

          <button
            onClick={() => setPreferredServer('s1')}
            className={`p-4 rounded-xl border text-left transition-all ${
              preferredServer === 's1'
                ? 'bg-[#132845] border-[#10B981] text-white shadow-lg shadow-[#10B981]/15'
                : 'bg-[#010318]/60 border-[#1E293B] text-[#94A3B8] hover:text-white'
            }`}
          >
            <div className="flex items-center justify-between mb-1">
              <span className="font-bold text-sm text-[#34D399]">Server 1 (BunnyCDN Line-Rate)</span>
              {preferredServer === 's1' && <CheckCircle2 className="w-4 h-4 text-[#10B981]" />}
            </div>
            <p className="text-xs text-[#94A3B8]">
              Direct line-rate edge delivery for low-latency playback where supported.
            </p>
          </button>
        </div>
      </div>

      {/* 3. Preferred Stream Quality */}
      <div className="p-5 rounded-2xl bg-[#0A192F] border border-[#132845] shadow-xl space-y-3">
        <h2 className="text-sm font-bold text-white flex items-center gap-2">
          <Monitor className="w-4 h-4 text-[#34D399]" />
          Default Video Resolution
        </h2>
        <div className="flex flex-wrap gap-2">
          {[
            { id: '1080p', label: '1080p Full HD' },
            { id: '720p', label: '720p HD' },
            { id: 'auto', label: 'Auto (Adaptive)' }
          ].map(q => (
            <button
              key={q.id}
              onClick={() => setPreferredQuality(q.id as any)}
              className={`px-4 py-2 rounded-lg text-xs font-bold border transition-all ${
                preferredQuality === q.id
                  ? 'bg-[#10B981] text-black border-[#10B981] shadow'
                  : 'bg-[#132845] text-[#94A3B8] border-transparent hover:text-white'
              }`}
            >
              {q.label}
            </button>
          ))}
        </div>
      </div>

      {/* 4. Local Watch Cache & History */}
      <div className="p-5 rounded-2xl bg-[#0A192F] border border-[#132845] shadow-xl space-y-3 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-white">Watch History & Local Cache</h2>
          <p className="text-xs text-[#94A3B8]">Clear remembered positions and continue watching cards</p>
        </div>
        <button
          onClick={clearHistory}
          className="self-start sm:self-auto flex items-center gap-2 px-4 py-2 rounded-lg bg-red-500/10 hover:bg-red-500 text-red-400 hover:text-white border border-red-500/30 text-xs font-bold transition-all active:scale-95"
        >
          <Trash2 className="w-3.5 h-3.5" />
          <span>{historyCleared ? 'Cleared!' : 'Clear History'}</span>
        </button>
      </div>

      {/* 5. Application Architecture Info */}
      <div className="p-5 rounded-2xl bg-[#0A192F] border border-[#132845] shadow-xl space-y-2 text-xs text-[#94A3B8]">
        <div className="flex items-center gap-2 text-white font-bold mb-1">
          <Cpu className="w-4 h-4 text-[#10B981]" />
          Munowatch Cross-Platform Specification
        </div>
        <p>• <strong>App Edition:</strong> Universal Web & Android TV Leanback (Mobile, Tablet, Desktop, TV)</p>
        <p>• <strong>Player Pipeline:</strong> HLS.js 1.5.18 + HTML5 Media Source Extensions</p>
        <p>• <strong>CORS Bridge:</strong> munowatch-api.onrender.com (Node.js/Express)</p>
        <p>• <strong>Android TV Host:</strong> Decoupled in app/ module</p>
      </div>
    </div>
  );
};
