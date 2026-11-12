import React from 'react';
import { usePlayer } from '../context/PlayerContext';
import { formatDurationHuman, formatBytes, formatDate } from '../utils/format';
import { X, Play, ListPlus, Check, Archive, Download, Star } from 'lucide-react';

export const EpisodeNotesSheet: React.FC = () => {
  const {
    activeNotesEpisode,
    setActiveNotesEpisode,
    playEpisode,
    enqueue,
    dequeue,
    archiveEpisode,
    unarchiveEpisode,
    toggleDownload,
    toggleStar,
    queue,
  } = usePlayer();

  if (!activeNotesEpisode) return null;

  const isQueued = queue.some((ep) => ep.uuid === activeNotesEpisode.uuid);

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/60 backdrop-blur-sm p-0 sm:p-4 animate-in fade-in duration-200">
      <div
        className="w-full max-w-xl max-h-[85vh] sm:max-h-[80vh] flex flex-col rounded-t-[24px] sm:rounded-[24px] bg-[#17233a] border border-[#354966]/60 text-[#F4F4F5] shadow-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Top Header */}
        <div className="flex items-center justify-between p-4 border-b border-white/10 shrink-0 bg-[#17233a]/90 backdrop-blur-md">
          <div className="flex items-center gap-3 min-w-0 pr-2">
            <div className="w-12 h-12 rounded-xl overflow-hidden shrink-0 bg-[#26262A] border border-white/10">
              {activeNotesEpisode.artworkUrl ? (
                <img
                  src={activeNotesEpisode.artworkUrl}
                  alt={activeNotesEpisode.title}
                  className="w-full h-full object-cover"
                />
              ) : (
                <div className="w-full h-full flex items-center justify-center font-bold text-xs text-white/50">
                  EP
                </div>
              )}
            </div>
            <div className="min-w-0">
              <p className="text-xs font-semibold text-[#008EFF] truncate">
                {activeNotesEpisode.podcastTitle}
              </p>
              <h3 className="text-sm font-bold text-white truncate">
                {activeNotesEpisode.title}
              </h3>
            </div>
          </div>

          <button
            type="button"
            onClick={() => setActiveNotesEpisode(null)}
            className="w-9 h-9 flex items-center justify-center rounded-full bg-white/5 hover:bg-white/10 text-white/70 hover:text-white transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Metadata info strip */}
        <div className="flex items-center gap-4 px-5 py-2.5 bg-black/20 text-xs text-[#b0bfd2] border-b border-white/5">
          <span>{formatDate(activeNotesEpisode.published)}</span>
          {activeNotesEpisode.durationSeconds ? (
            <>
              <span>•</span>
              <span className="font-bold text-[#008EFF]">
                {formatDurationHuman(activeNotesEpisode.durationSeconds)}
              </span>
            </>
          ) : null}
          {activeNotesEpisode.sizeBytes ? (
            <>
              <span>•</span>
              <span>{formatBytes(activeNotesEpisode.sizeBytes)}</span>
            </>
          ) : null}
        </div>

        {/* Action Buttons Row with 48dp targets */}
        <div className="flex items-center justify-around p-3 border-b border-white/10 bg-[#121824]">
          {/* Play */}
          <button
            type="button"
            onClick={() => {
              playEpisode(activeNotesEpisode);
              setActiveNotesEpisode(null);
            }}
            className="min-h-[48px] px-4 flex items-center gap-2 rounded-full bg-[#008EFF] hover:bg-[#00A8FF] text-white font-semibold text-xs shadow-md active:scale-95 transition-all"
          >
            <Play className="w-4 h-4 fill-current" />
            <span>Play</span>
          </button>

          {/* Up Next toggle */}
          <button
            type="button"
            onClick={() => {
              if (isQueued) dequeue(activeNotesEpisode.uuid);
              else enqueue(activeNotesEpisode.uuid);
            }}
            className={`min-h-[48px] px-3.5 flex items-center gap-2 rounded-full font-semibold text-xs transition-colors border ${
              isQueued
                ? 'bg-[#008EFF]/20 border-[#008EFF] text-[#00D2FF]'
                : 'bg-white/5 hover:bg-white/10 border-white/10 text-white'
            }`}
          >
            {isQueued ? <Check className="w-4 h-4" /> : <ListPlus className="w-4 h-4" />}
            <span>{isQueued ? 'In Queue' : 'Up Next'}</span>
          </button>

          {/* Download Toggle */}
          <button
            type="button"
            onClick={() => toggleDownload(activeNotesEpisode.uuid)}
            className={`min-h-[48px] px-3.5 flex items-center gap-2 rounded-full font-semibold text-xs transition-colors border ${
              activeNotesEpisode.isDownloaded
                ? 'bg-emerald-500/20 border-emerald-500/50 text-emerald-400'
                : 'bg-white/5 hover:bg-white/10 border-white/10 text-white'
            }`}
          >
            <Download className="w-4 h-4" />
            <span>{activeNotesEpisode.isDownloaded ? 'Downloaded' : 'Download'}</span>
          </button>

          {/* Archive Toggle */}
          <button
            type="button"
            onClick={() => {
              if (activeNotesEpisode.isArchived) unarchiveEpisode(activeNotesEpisode.uuid);
              else {
                archiveEpisode(activeNotesEpisode.uuid);
                setActiveNotesEpisode(null);
              }
            }}
            className="min-h-[48px] px-3 flex items-center gap-1.5 rounded-full bg-white/5 hover:bg-white/10 text-[#A1A1A6] hover:text-white text-xs border border-white/10 transition-colors"
          >
            <Archive className="w-4 h-4" />
            <span>{activeNotesEpisode.isArchived ? 'Restore' : 'Archive'}</span>
          </button>
        </div>

        {/* Scrollable Episode Show Notes with HTML rendering */}
        <div className="flex-1 overflow-y-auto p-5 text-sm leading-relaxed text-[#E4E4E7]">
          {activeNotesEpisode.description ? (
            <div
              className="prose prose-invert prose-blue max-w-none [&_a]:text-[#008EFF] [&_a]:underline [&_p]:mb-3 [&_ul]:list-disc [&_ul]:pl-5 [&_li]:mb-1 font-sans"
              dangerouslySetInnerHTML={{ __html: activeNotesEpisode.description }}
            />
          ) : (
            <p className="text-[#A1A1A6] italic">No description provided for this episode.</p>
          )}
        </div>
      </div>
    </div>
  );
};
