import React from 'react';
import { usePlayer } from '../context/PlayerContext';
import { Play, Pause, FileText, ChevronUp } from 'lucide-react';

export const NowPlayingBar: React.FC = () => {
  const {
    currentEpisode,
    isPlaying,
    currentTime,
    duration,
    togglePlayPause,
    setIsPlayerOpen,
    setActiveNotesEpisode,
    queue,
  } = usePlayer();

  const activeEpisode = currentEpisode || queue[0];
  if (!activeEpisode) return null;

  const progressFraction = duration > 0 ? Math.min(1, currentTime / duration) : 0;

  return (
    <div className="fixed bottom-[74px] left-0 right-0 z-30 px-3 max-w-2xl mx-auto pointer-events-none">
      <div
        onClick={() => setIsPlayerOpen(true)}
        className="pointer-events-auto group relative flex items-center justify-between p-2.5 rounded-[16px] bg-[#17233a]/95 dark:bg-[#17233a]/95 border border-[#354966]/60 shadow-[0_8px_32px_rgba(0,142,255,0.18)] backdrop-blur-xl cursor-pointer select-none transition-all hover:border-[#008EFF]/50"
      >
        {/* Glowing hairline progress indicator at top */}
        <div className="absolute top-0 left-3 right-3 h-[2px] bg-white/10 rounded-full overflow-hidden">
          <div
            className="h-full bg-gradient-to-r from-[#008EFF] to-[#00D2FF] shadow-[0_0_8px_#008EFF] transition-all duration-300"
            style={{ width: `${progressFraction * 100}%` }}
          />
        </div>

        {/* Left: Artwork & Episode Details */}
        <div className="flex items-center gap-3 min-w-0 pr-2">
          <div className="relative w-12 h-12 rounded-xl overflow-hidden shrink-0 bg-[#26262A] shadow-md border border-white/10">
            {activeEpisode.artworkUrl ? (
              <img
                src={activeEpisode.artworkUrl}
                alt={activeEpisode.title}
                className="w-full h-full object-cover"
              />
            ) : (
              <div className="w-full h-full flex items-center justify-center text-xs font-bold text-white/50 bg-[#1C1C1E]">
                EP
              </div>
            )}
          </div>

          <div className="min-w-0 flex flex-col justify-center">
            <h4 className="text-sm font-semibold text-white truncate group-hover:text-[#00D2FF] transition-colors leading-tight">
              {activeEpisode.title}
            </h4>
            <p className="text-xs text-[#b0bfd2] truncate mt-0.5">
              {activeEpisode.podcastTitle}
            </p>
            {queue.find((e) => e.uuid !== activeEpisode.uuid) && (
              <p className="text-[11px] text-[#008EFF] font-semibold truncate mt-0.5">
                Next up · {queue.find((e) => e.uuid !== activeEpisode.uuid)?.title}
              </p>
            )}
          </div>
        </div>

        {/* Right: Actions */}
        <div className="flex items-center gap-2 shrink-0" onClick={(e) => e.stopPropagation()}>
          {/* Signature Play/Pause 47.25dp disc with 28dp glyph */}
          <button
            type="button"
            onClick={togglePlayPause}
            className="w-[46px] h-[46px] flex items-center justify-center rounded-full bg-[#008EFF] hover:bg-[#00A8FF] text-white shadow-[0_4px_16px_rgba(0,142,255,0.45)] active:scale-95 transition-transform"
            title={isPlaying ? "Pause" : "Play"}
          >
            {isPlaying ? (
              <Pause className="w-5 h-5 fill-current" />
            ) : (
              <Play className="w-5 h-5 fill-current translate-x-0.5" />
            )}
          </button>

          {/* Chevron Up Expand */}
          <button
            type="button"
            onClick={() => setIsPlayerOpen(true)}
            className="w-8 h-8 flex items-center justify-center rounded-full text-white/70 hover:text-white hover:bg-white/10 transition-colors"
            title="Expand player"
          >
            <ChevronUp className="w-5 h-5" />
          </button>
        </div>
      </div>
    </div>
  );
};
