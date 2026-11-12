import React, { useState } from 'react';
import { usePlayer } from '../context/PlayerContext';
import { formatDuration } from '../utils/format';
import {
  ChevronDown,
  RotateCcw,
  RotateCw,
  Play,
  Pause,
  Moon,
  Volume2,
  FileText,
  Star,
  ListMusic,
  Gauge,
  Scissors,
} from 'lucide-react';

export const PlayerModal: React.FC = () => {
  const {
    currentEpisode,
    isPlaying,
    currentTime,
    duration,
    playbackSpeed,
    volumeBoost,
    skipSilence,
    sleepTimerSecondsRemaining,
    sleepTimerMode,
    togglePlayPause,
    seekTo,
    skipBackward,
    skipForward,
    setSpeed,
    toggleVolumeBoost,
    toggleSkipSilence,
    setSleepTimer,
    toggleStar,
    isPlayerOpen,
    setIsPlayerOpen,
    setActiveNotesEpisode,
    settings,
    isPro,
    openUpgradeModal,
  } = usePlayer();

  const [showSpeedMenu, setShowSpeedMenu] = useState(false);
  const [showSleepMenu, setShowSleepMenu] = useState(false);

  if (!isPlayerOpen || !currentEpisode) return null;

  const remaining = Math.max(0, duration - currentTime);
  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;

  const speeds = [0.75, 1.0, 1.1, 1.2, 1.25, 1.5, 1.75, 2.0, 2.5];
  const sleepOptions = [
    { label: '5 minutes', value: 5 },
    { label: '15 minutes', value: 15 },
    { label: '30 minutes', value: 30 },
    { label: '45 minutes', value: 45 },
    { label: '60 minutes', value: 60 },
    { label: 'End of episode', value: 'end_of_episode' as const },
  ];

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-[#121214] text-[#F4F4F5] overflow-y-auto">
      {/* Background artwork blur ambient wash */}
      <div
        className="fixed inset-0 opacity-25 filter blur-3xl pointer-events-none scale-125"
        style={{
          backgroundImage: `url(${currentEpisode.artworkUrl})`,
          backgroundSize: 'cover',
          backgroundPosition: 'center',
        }}
      />

      {/* Header bar */}
      <div className="relative z-10 flex items-center justify-between p-4 max-w-xl w-full mx-auto">
        <button
          type="button"
          onClick={() => setIsPlayerOpen(false)}
          className="w-11 h-11 flex items-center justify-center rounded-full bg-white/5 hover:bg-white/10 text-white transition-colors"
          title="Minimize player"
        >
          <ChevronDown className="w-6 h-6" />
        </button>

        <div className="text-center truncate px-4">
          <p className="text-xs uppercase tracking-widest text-[#008EFF] font-bold">Now Playing</p>
          <h3 className="text-sm font-semibold text-white truncate max-w-[240px]">
            {currentEpisode.podcastTitle}
          </h3>
        </div>

        <button
          type="button"
          onClick={() => setActiveNotesEpisode(currentEpisode)}
          className="w-11 h-11 flex items-center justify-center rounded-full bg-white/5 hover:bg-white/10 text-white transition-colors"
          title="Episode Notes"
        >
          <FileText className="w-5 h-5" />
        </button>
      </div>

      {/* Main Content Area */}
      <div className="relative z-10 flex-1 flex flex-col justify-between max-w-md w-full mx-auto px-6 pb-8 pt-2">
        {/* Large Artwork */}
        <div className="flex-1 flex items-center justify-center py-4">
          <div className="relative aspect-square w-full max-w-[320px] rounded-3xl overflow-hidden shadow-[0_20px_50px_rgba(0,142,255,0.25)] border border-white/10">
            {currentEpisode.artworkUrl ? (
              <img
                src={currentEpisode.artworkUrl}
                alt={currentEpisode.title}
                className="w-full h-full object-cover"
              />
            ) : (
              <div className="w-full h-full bg-[#1C1C1E] flex items-center justify-center text-4xl font-bold text-white/40">
                Lohe
              </div>
            )}
          </div>
        </div>

        {/* Title & Metadata */}
        <div className="my-4">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h2 className="text-xl font-bold text-white leading-snug line-clamp-2">
                {currentEpisode.title}
              </h2>
              <p className="text-sm text-[#00A8FF] font-medium mt-1">
                {currentEpisode.podcastTitle}
              </p>
            </div>
            <button
              type="button"
              onClick={() => toggleStar(currentEpisode.uuid)}
              className={`p-2.5 rounded-full transition-colors ${
                currentEpisode.starred
                  ? 'text-amber-400 bg-amber-400/10'
                  : 'text-white/40 hover:text-white bg-white/5'
              }`}
              title={currentEpisode.starred ? 'Starred' : 'Star episode'}
            >
              <Star className="w-5 h-5 fill-current" />
            </button>
          </div>
        </div>

        {/* Scrubber Slider */}
        <div className="my-2">
          <input
            type="range"
            min={0}
            max={duration || 1}
            value={currentTime}
            onChange={(e) => seekTo(Number(e.target.value))}
            className="w-full h-2 rounded-lg bg-white/15 accent-[#008EFF] cursor-pointer"
          />
          <div className="flex justify-between items-center text-xs mt-1.5 font-bold">
            <span className="text-[#008EFF]">{formatDuration(currentTime)}</span>
            <span className="text-white/50">-{formatDuration(remaining)}</span>
          </div>
        </div>

        {/* Transport Primary Controls */}
        <div className="flex items-center justify-center gap-6 my-4">
          {/* Skip backward */}
          <button
            type="button"
            onClick={() => skipBackward()}
            className="w-12 h-12 flex items-center justify-center rounded-full bg-white/5 hover:bg-white/15 text-white active:scale-95 transition-all"
            title={`Skip back ${settings.skipBackSeconds}s`}
          >
            <RotateCcw className="w-6 h-6" />
            <span className="sr-only">Skip back</span>
          </button>

          {/* Large Main Play/Pause Button */}
          <button
            type="button"
            onClick={togglePlayPause}
            className="w-20 h-20 flex items-center justify-center rounded-full bg-[#008EFF] hover:bg-[#00A8FF] text-white shadow-[0_10px_30px_rgba(0,142,255,0.45)] active:scale-95 transition-all"
            title={isPlaying ? 'Pause' : 'Play'}
          >
            {isPlaying ? (
              <Pause className="w-9 h-9 fill-current" />
            ) : (
              <Play className="w-9 h-9 fill-current translate-x-1" />
            )}
          </button>

          {/* Skip forward */}
          <button
            type="button"
            onClick={() => skipForward()}
            className="w-12 h-12 flex items-center justify-center rounded-full bg-white/5 hover:bg-white/15 text-white active:scale-95 transition-all"
            title={`Skip forward ${settings.skipForwardSeconds}s`}
          >
            <RotateCw className="w-6 h-6" />
            <span className="sr-only">Skip forward</span>
          </button>
        </div>

        {/* Secondary controls row */}
        <div className="flex items-center justify-around py-2 border-t border-white/10 text-xs">
          {/* Speed Selector */}
          <div className="relative">
            <button
              type="button"
              onClick={() => setShowSpeedMenu(!showSpeedMenu)}
              className="flex items-center gap-1 px-3 py-1.5 rounded-full bg-white/5 hover:bg-white/10 text-[#F4F4F5] font-semibold transition-colors"
            >
              <Gauge className="w-3.5 h-3.5 text-[#008EFF]" />
              <span>{playbackSpeed}x</span>
            </button>
            {showSpeedMenu && (
              <div className="absolute bottom-10 left-0 bg-[#1C1C1E] border border-white/10 rounded-2xl shadow-xl p-2 grid grid-cols-3 gap-1 w-48 z-50">
                {speeds.map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => {
                      setSpeed(s);
                      setShowSpeedMenu(false);
                    }}
                    className={`px-2 py-1.5 rounded-lg text-xs font-semibold ${
                      playbackSpeed === s
                        ? 'bg-[#008EFF] text-white'
                        : 'text-white/70 hover:bg-white/10'
                    }`}
                  >
                    {s}x
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Sleep Timer */}
          <div className="relative">
            <button
              type="button"
              onClick={() => setShowSleepMenu(!showSleepMenu)}
              className={`flex items-center gap-1 px-3 py-1.5 rounded-full font-semibold transition-colors ${
                sleepTimerSecondsRemaining !== null || sleepTimerMode
                  ? 'bg-[#008EFF]/20 text-[#00D2FF] border border-[#008EFF]/40'
                  : 'bg-white/5 hover:bg-white/10 text-[#F4F4F5]'
              }`}
            >
              <Moon className="w-3.5 h-3.5" />
              <span>
                {sleepTimerSecondsRemaining !== null
                  ? `${Math.ceil(sleepTimerSecondsRemaining / 60)}m`
                  : sleepTimerMode
                  ? 'End of Ep'
                  : 'Sleep'}
              </span>
            </button>
            {showSleepMenu && (
              <div className="absolute bottom-10 left-1/2 -translate-x-1/2 bg-[#1C1C1E] border border-white/10 rounded-2xl shadow-xl p-2 w-48 flex flex-col gap-1 z-50">
                <p className="text-[11px] font-bold text-white/50 px-2 py-1">Sleep Timer</p>
                {sleepOptions.map((opt) => (
                  <button
                    key={opt.label}
                    type="button"
                    onClick={() => {
                      setSleepTimer(opt.value);
                      setShowSleepMenu(false);
                    }}
                    className="text-left px-3 py-1.5 rounded-lg text-xs font-medium text-white hover:bg-white/10"
                  >
                    {opt.label}
                  </button>
                ))}
                {(sleepTimerSecondsRemaining !== null || sleepTimerMode) && (
                  <button
                    type="button"
                    onClick={() => {
                      setSleepTimer(null);
                      setShowSleepMenu(false);
                    }}
                    className="text-left px-3 py-1.5 rounded-lg text-xs font-medium text-red-400 hover:bg-red-400/10"
                  >
                    Turn off timer
                  </button>
                )}
              </div>
            )}
          </div>

          {/* Volume Boost Toggle */}
          <button
            type="button"
            onClick={toggleVolumeBoost}
            className={`flex items-center gap-1 px-3 py-1.5 rounded-full font-semibold transition-colors ${
              volumeBoost
                ? 'bg-[#008EFF]/20 text-[#00D2FF] border border-[#008EFF]/40'
                : 'bg-white/5 hover:bg-white/10 text-[#F4F4F5]'
            }`}
            title="Volume Boost"
          >
            <Volume2 className="w-3.5 h-3.5" />
            <span>Boost</span>
          </button>

          {/* Skip Silence Toggle */}
          <button
            type="button"
            onClick={toggleSkipSilence}
            className={`flex items-center gap-1 px-3 py-1.5 rounded-full font-semibold transition-colors ${
              skipSilence
                ? 'bg-[#008EFF]/20 text-[#00D2FF] border border-[#008EFF]/40'
                : 'bg-white/5 hover:bg-white/10 text-[#F4F4F5]'
            }`}
            title="Trim silence"
          >
            <Scissors className="w-3.5 h-3.5" />
            <span>Trim</span>
          </button>
        </div>
      </div>
    </div>
  );
};
