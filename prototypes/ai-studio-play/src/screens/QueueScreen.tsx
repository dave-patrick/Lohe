import React from 'react';
import { usePlayer } from '../context/PlayerContext';
import { formatDurationHuman } from '../utils/format';
import { QueueIndicator } from '../components/QueueIndicator';
import {
  GripVertical,
  Play,
  Pause,
  Trash2,
  MoreVertical,
  Settings,
  Bug,
  ChevronUp,
  ChevronDown,
} from 'lucide-react';

interface QueueScreenProps {
  onOpenSettings?: () => void;
}

export const QueueScreen: React.FC<QueueScreenProps> = ({ onOpenSettings }) => {
  const {
    queue,
    currentEpisode,
    isPlaying,
    currentTime,
    duration,
    playEpisode,
    togglePlayPause,
    dequeue,
    reorderQueue,
    setActiveNotesEpisode,
    settings,
    dequeueAll,
  } = usePlayer();

  const totalRemainingMinutes = Math.round(
    queue.reduce((sum, ep) => sum + (ep.durationSeconds || 0), 0) / 60
  );

  const activePlaying = currentEpisode || queue[0];
  const progressPercent =
    activePlaying && duration > 0 ? Math.min(100, (currentTime / duration) * 100) : 35;

  return (
    <div className="flex-1 overflow-y-auto pb-44 px-4 sm:px-6 pt-3 max-w-2xl mx-auto w-full select-none font-sans">
      {/* Top App Bar (Matches 47289.jpg) */}
      <header className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-bold text-white tracking-tight">Up Next</h1>

        <div className="flex items-center gap-2">
          {/* Clear Queue Trash Button */}
          {queue.length > 0 && (
            <button
              type="button"
              onClick={() => dequeueAll(queue.map((q) => q.uuid))}
              className="w-10 h-10 flex items-center justify-center rounded-full text-[#8E8E93] hover:text-red-400 hover:bg-white/5 transition-colors"
              title="Clear queue"
              aria-label="Clear queue"
            >
              <Trash2 className="w-5 h-5" />
            </button>
          )}

          {/* More options ⋮ */}
          <button
            type="button"
            className="w-10 h-10 flex items-center justify-center rounded-full text-[#8E8E93] hover:text-white hover:bg-white/5 transition-colors"
            title="Queue options"
          >
            <MoreVertical className="w-5 h-5" />
          </button>

          {/* Settings Gear */}
          {onOpenSettings && (
            <button
              type="button"
              onClick={onOpenSettings}
              className="w-10 h-10 flex items-center justify-center rounded-full text-[#8E8E93] hover:text-white hover:bg-white/5 transition-colors"
              title="Settings"
            >
              <Settings className="w-5 h-5" />
            </button>
          )}

          {/* Red BUG Badge (Lohe developer affordance per project.md) */}
          <button
            type="button"
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-[#E5484D] hover:bg-[#F2555A] text-white text-xs font-bold shadow transition-all active:scale-95"
            title="Diagnostics"
          >
            <Bug className="w-3.5 h-3.5" />
            <span>BUG</span>
          </button>
        </div>
      </header>

      {/* NOW PLAYING CARD (Exact match to 47289.jpg) */}
      {activePlaying && (
        <div className="mb-4">
          <span className="text-xs font-bold uppercase tracking-wider text-[#008EFF] block mb-1.5 ml-1">
            NOW PLAYING
          </span>
          <div
            onClick={() => setActiveNotesEpisode(activePlaying)}
            className="group relative p-3.5 rounded-[20px] bg-[#1C1C1E] border border-[#2C2C30] hover:border-[#008EFF]/40 transition-all cursor-pointer shadow-md overflow-hidden"
          >
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-3.5 min-w-0 pr-2">
                <div className="w-12 h-12 rounded-xl overflow-hidden bg-black/40 shrink-0 border border-white/5 shadow-sm">
                  {activePlaying.artworkUrl ? (
                    <img src={activePlaying.artworkUrl} alt="" className="w-full h-full object-cover" />
                  ) : (
                    <img src="/assets/official-app-icon.png" alt="" className="w-full h-full object-cover" />
                  )}
                </div>

                <div className="min-w-0">
                  <h3 className="text-base font-bold text-white group-hover:text-[#00D2FF] truncate">
                    {activePlaying.title}
                  </h3>
                  <p className="text-xs text-[#8E8E93] truncate mt-0.5">
                    {activePlaying.podcastTitle}
                  </p>
                </div>
              </div>

              {/* Play / Pause in blue */}
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  if (currentEpisode?.uuid === activePlaying.uuid) togglePlayPause();
                  else playEpisode(activePlaying);
                }}
                className="w-10 h-10 rounded-full flex items-center justify-center text-[#008EFF] hover:bg-white/10 shrink-0 active:scale-95 transition-all"
                aria-label="Play or pause"
              >
                {currentEpisode?.uuid === activePlaying.uuid && isPlaying ? (
                  <Pause className="w-5 h-5 fill-current" />
                ) : (
                  <Play className="w-5 h-5 fill-current" />
                )}
              </button>
            </div>

            {/* Hairline progress line along bottom of card */}
            <div className="absolute bottom-0 left-3.5 right-3.5 h-[2px] bg-white/10 rounded-full overflow-hidden">
              <div
                className="h-full bg-[#008EFF] transition-all duration-300"
                style={{ width: `${progressPercent}%` }}
              />
            </div>
          </div>
        </div>
      )}

      {/* Queue Stat Line (Matches 47289.jpg) */}
      <div className="mb-3 px-1">
        <p className="text-sm text-[#8E8E93] font-medium">
          {queue.length} episodes next · {totalRemainingMinutes} min remaining
        </p>
      </div>

      {/* Queue Episodes List (Matches 47289.jpg) */}
      {queue.length === 0 ? (
        <div className="p-8 rounded-[20px] bg-[#1C1C1E] border border-[#2C2C30] text-center">
          <p className="text-sm text-[#8E8E93]">No upcoming episodes in Up Next.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {queue.map((ep, index) => {
            const isCurPlaying = currentEpisode?.uuid === ep.uuid && isPlaying;
            const durationMin = Math.round((ep.durationSeconds || 1200) / 60);

            return (
              <div
                key={ep.uuid}
                onClick={() => setActiveNotesEpisode(ep)}
                className="group flex items-center justify-between p-3 rounded-[18px] bg-[#1C1C1E] hover:bg-[#26262A] border border-[#2C2C30]/60 transition-all cursor-pointer shadow-sm"
              >
                {/* Left: Drag Handle ≡ + Artwork + Details */}
                <div className="flex items-center gap-3 min-w-0 pr-2">
                  <div
                    className="cursor-grab text-[#8E8E93] hover:text-white px-0.5 shrink-0"
                    onClick={(e) => e.stopPropagation()}
                    title="Drag to reorder"
                  >
                    <GripVertical className="w-4 h-4" />
                  </div>

                  <div className="w-10 h-10 rounded-xl overflow-hidden bg-black/40 shrink-0 border border-white/5 shadow-sm">
                    {ep.artworkUrl ? (
                      <img src={ep.artworkUrl} alt="" className="w-full h-full object-cover" />
                    ) : (
                      <img src="/assets/official-app-icon.png" alt="" className="w-full h-full object-cover" />
                    )}
                  </div>

                  <div className="min-w-0">
                    <h4 className="text-[14px] font-medium text-white truncate group-hover:text-[#00D2FF]">
                      {ep.title}
                    </h4>
                    <p className="text-xs text-[#8E8E93] truncate mt-0.5">
                      22 Sep · {durationMin} min · {ep.podcastTitle}
                    </p>
                  </div>
                </div>

                {/* Right: Reorder Up/Down arrows & Blue Play Button */}
                <div className="flex items-center gap-1 shrink-0" onClick={(e) => e.stopPropagation()}>
                  {index > 0 && (
                    <button
                      type="button"
                      onClick={() => reorderQueue(index, index - 1)}
                      className="w-7 h-7 flex items-center justify-center rounded text-[#8E8E93] hover:text-white hover:bg-white/10 transition-colors"
                      title="Move up"
                    >
                      <ChevronUp className="w-4 h-4" />
                    </button>
                  )}
                  {index < queue.length - 1 && (
                    <button
                      type="button"
                      onClick={() => reorderQueue(index, index + 1)}
                      className="w-7 h-7 flex items-center justify-center rounded text-[#8E8E93] hover:text-white hover:bg-white/10 transition-colors"
                      title="Move down"
                    >
                      <ChevronDown className="w-4 h-4" />
                    </button>
                  )}

                  <button
                    type="button"
                    onClick={() => {
                      if (currentEpisode?.uuid === ep.uuid) togglePlayPause();
                      else playEpisode(ep);
                    }}
                    className="w-10 h-10 flex items-center justify-center rounded-full text-[#008EFF] hover:bg-white/10 shrink-0 active:scale-95 transition-all"
                    aria-label="Play episode"
                  >
                    {isCurPlaying ? (
                      <Pause className="w-4 h-4 fill-current" />
                    ) : (
                      <Play className="w-4 h-4 fill-current ml-0.5" />
                    )}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default QueueScreen;
