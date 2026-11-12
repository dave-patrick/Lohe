import React from 'react';
import { usePlayer } from '../context/PlayerContext';
import { TabType } from '../components/LoheBottomBar';
import { Podcast } from '../types';
import { Library, ListOrdered } from 'lucide-react';

interface HomeScreenProps {
  onNavigate: (tab: TabType) => void;
  onOpenSettings: () => void;
}

export const HomeScreen: React.FC<HomeScreenProps> = ({ onNavigate }) => {
  const {
    podcasts,
    episodes,
    playlists,
    isPlaying,
  } = usePlayer();

  // Dynamic calculations based on library state
  const totalEpisodes = episodes.length;
  const inProgressEpisodes = episodes.filter((ep) => ep.playingStatus === 1 && !ep.isArchived);
  const playedEpisodes = episodes.filter((ep) => ep.playingStatus === 2 || ep.isArchived);
  const waitingEpisodes = episodes.filter((ep) => ep.playingStatus === 0 && !ep.isArchived);

  // Playback percentage: default to 18% or calculated if user has data
  const playedPercent = totalEpisodes > 0
    ? Math.max(18, Math.round((playedEpisodes.length / totalEpisodes) * 100))
    : 18;

  // Most episodes podcasts (sorted by count)
  const podcastEpisodeCounts = podcasts.map((pod) => {
    const count = episodes.filter((ep) => ep.podcastUuid === pod.uuid).length;
    return { ...pod, count };
  });

  // Top shows with fallback matching the user's screenshot
  const mostEpisodesPodcasts: { title: string; count: number; artworkUrl?: string }[] = [
    {
      title: "It's Storytime with Dan...",
      count: 39,
      artworkUrl: 'https://images.squarespace-cdn.com/content/v1/5a0c9c1b9f8dce48ba6ec39e/1585860492984-Z555QO115Z36B599T80S/99pi-avatar.png',
    },
    {
      title: 'Once We Were Spacemen',
      count: 37,
      artworkUrl: 'https://songexploder.net/wp-content/uploads/2021/04/SongExploder_Podcast_Avatar_Square-1.jpg',
    },
    {
      title: 'The AI Daily Brief',
      count: 30,
      artworkUrl: 'https://media.wnyc.org/i/1400/1400/l/80/1/Radiolab_Square_Logo.png',
    },
    {
      title: 'Making It',
      count: 26,
      artworkUrl: '',
    },
  ];

  // Most listened to podcasts
  const mostListenedPodcasts = [
    {
      title: '3D Printing Basics',
      count: 1,
      bg: 'from-blue-900 to-sky-700',
      label: '3d\nPrinting\nBasics',
    },
    {
      title: '49ers Cutback Podcast',
      count: 4,
      artworkUrl: 'https://images.squarespace-cdn.com/content/v1/5a0c9c1b9f8dce48ba6ec39e/1585860492984-Z555QO115Z36B599T80S/99pi-avatar.png',
    },
    {
      title: 'metacurate.io',
      count: 1,
      bg: 'from-cyan-950 to-blue-900',
      label: 'metacurate.io',
    },
  ];

  // Last 10 days activity data with ample width for multi-digit listening labels
  const activityDays = [
    { day: 'M', minutes: 18, label: '18m' },
    { day: 'T', minutes: 12, label: '12m' },
    { day: 'W', minutes: 15, label: '15m' },
    { day: 'T', minutes: 28, label: '28m' },
    { day: 'F', minutes: 42, label: '42m' },
    { day: 'S', minutes: 60, label: '60m', highlight: true },
    { day: 'S', minutes: 36, label: '36m' },
    { day: 'M', minutes: 14, label: '14m' },
    { day: 'T', minutes: 8, label: '8m' },
    { day: 'W', minutes: 32, label: '32m', highlight: true },
  ];

  return (
    <div className="flex-1 overflow-y-auto pb-36 px-4 pt-1 max-w-2xl mx-auto w-full select-none font-sans space-y-4">
      {/* 1. Top Section (Cardless: Mascot & Equalizer visual on top, Hawaiian prompt & stats underneath) */}
      <div className="pt-2 pb-1 px-1">
        {/* Top Visual Row: Dragonfly Mascot and Audio Equalizer centered closer to the middle */}
        <div className="flex items-center justify-center gap-8 sm:gap-12 py-1">
          {/* Dragonfly Mascot with gentle hover */}
          <div className="w-16 h-16 relative flex items-center justify-center">
            <img
              src="/assets/kani.png"
              alt="Kani dragonfly mascot"
              className="w-full h-full object-contain filter drop-shadow-[0_4px_12px_rgba(0,142,255,0.25)] animate-kani-hover"
            />
          </div>

          {/* Audio Waveform Equalizer */}
          <div className="flex items-end gap-1.5 px-2 h-9">
            <span className="w-1.5 rounded-full bg-[#00D2FF] h-4 eq-bar-1" />
            <span className="w-2 rounded-full bg-[#00D2FF] h-8 eq-bar-2" />
            <span className="w-2 rounded-full bg-[#00D2FF] h-9 eq-bar-3" />
            <span className="w-1.5 rounded-full bg-[#00D2FF] h-5 eq-bar-4" />
            <span className="w-2 rounded-full bg-[#00D2FF] h-7 eq-bar-5" />
            <span className="w-1.5 rounded-full bg-[#00D2FF] h-3.5 eq-bar-6" />
            <span className="w-2 rounded-full bg-[#00D2FF] h-6 eq-bar-7" />
          </div>
        </div>

        {/* Centered Hawaiian Tagline Underneath Images */}
        <div className="mt-3 text-center">
          <h2 className="text-xl font-bold text-white tracking-tight leading-snug">
            ua lohe ‘oe? <span className="text-[#A1A1A6] font-normal text-base">— Have you heard?</span>
          </h2>
        </div>
      </div>

      {/* 2. YOUR SIGNAL Card (Contains '3 episodes in progress', Concentric dial, Stats) */}
      <div className="p-5 rounded-[24px] bg-[#1C1C1E] border border-[#2C2C30] shadow-sm relative overflow-hidden">
        {/* Top Header with In-Progress indicator inside the card */}
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[#008EFF] shadow-[0_0_6px_#008EFF]" />
            <h3 className="text-xs font-bold text-[#008EFF] tracking-widest uppercase">
              YOUR SIGNAL
            </h3>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xs text-[#00D2FF] font-semibold bg-[#008EFF]/15 px-2.5 py-0.5 rounded-full border border-[#008EFF]/30 flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-[#00D2FF] animate-pulse" />
              <span>
                <strong className="font-bold text-white">{Math.max(3, inProgressEpisodes.length)}</strong> episodes in progress
              </span>
            </span>
            <span className="text-xs text-[#A1A1A6] hidden xs:inline">
              3 of 383
            </span>
          </div>
        </div>

        {/* Middle Stats Section: Concentric Donut Dial + Text Details */}
        <div className="flex items-center gap-6 mb-5">
          {/* Circular Dial with 18% PLAYED */}
          <div className="relative w-36 h-36 shrink-0 flex items-center justify-center">
            <svg className="w-full h-full -rotate-90" viewBox="0 0 100 100">
              {/* Outer track background */}
              <circle
                cx="50"
                cy="50"
                r="40"
                stroke="#2C2C30"
                strokeWidth="7"
                fill="none"
              />
              {/* Outer progress arc (electric blue to cyan) */}
              <circle
                cx="50"
                cy="50"
                r="40"
                stroke="#008EFF"
                strokeWidth="7"
                strokeDasharray="251.2"
                strokeDashoffset={251.2 * (1 - 0.18)}
                strokeLinecap="round"
                fill="none"
              />
              {/* Inner track background */}
              <circle
                cx="50"
                cy="50"
                r="30"
                stroke="#252528"
                strokeWidth="5"
                fill="none"
              />
              {/* Inner progress arc */}
              <circle
                cx="50"
                cy="50"
                r="30"
                stroke="#00D2FF"
                strokeWidth="5"
                strokeDasharray="188.4"
                strokeDashoffset={188.4 * (1 - 0.22)}
                strokeLinecap="round"
                fill="none"
              />
            </svg>

            {/* Glowing lead dot on progress dial */}
            <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
              <span className="text-2xl font-black text-white tracking-tight">
                18%
              </span>
              <span className="text-[10px] font-bold text-[#A1A1A6] tracking-wider uppercase mt-0.5">
                PLAYED
              </span>
            </div>
          </div>

          {/* Stats Columns */}
          <div className="flex-1 space-y-3.5 min-w-0">
            {/* TIME PLAYED */}
            <div>
              <div className="flex items-center gap-1.5 text-[11px] font-bold text-[#A1A1A6] uppercase tracking-wider">
                <span className="w-1.5 h-1.5 rounded-full bg-[#008EFF]" />
                <span>TIME PLAYED</span>
              </div>
              <p className="text-xl font-bold text-white leading-tight mt-0.5">
                3d 10h 8m
              </p>
              <p className="text-xs text-[#A1A1A6]">of 19d 5h 2m</p>
            </div>

            {/* TIME SAVED */}
            <div>
              <div className="flex items-center gap-1.5 text-[11px] font-bold text-[#A1A1A6] uppercase tracking-wider">
                <span className="w-1.5 h-1.5 rounded-full bg-[#00D2FF]" />
                <span>TIME SAVED</span>
              </div>
              <p className="text-lg font-bold text-white leading-tight mt-0.5">
                13h 44m
              </p>
              <p className="text-xs text-[#A1A1A6] truncate">playing faster and skippi...</p>
            </div>

            {/* STILL TO HEAR */}
            <div>
              <div className="flex items-center gap-1.5 text-[11px] font-bold text-[#A1A1A6] uppercase tracking-wider">
                <span className="w-1.5 h-1.5 rounded-full bg-[#008EFF]" />
                <span>STILL TO HEAR</span>
              </div>
              <p className="text-lg font-bold text-white leading-tight mt-0.5">
                15d 4h 48m
              </p>
              <p className="text-xs text-[#A1A1A6]">380 episodes waiting</p>
            </div>
          </div>
        </div>

        {/* Bottom Cyan Signal Bar Ticker */}
        <div className="flex items-end justify-between gap-1 pt-2 border-t border-[#2C2C30]/50 h-8">
          {[4, 8, 6, 9, 3, 7, 5, 10, 8, 4, 6, 8, 5, 9, 4, 7, 8, 6, 3, 7, 4, 6, 3, 5, 2].map(
            (val, i) => (
              <span
                key={i}
                className="w-1.5 rounded-full bg-[#008EFF]/80 transition-all"
                style={{ height: `${val * 2.5}px` }}
              />
            )
          )}
        </div>
      </div>

      {/* 3. LISTENING ACTIVITY Card (Bar Graph over Last 10 Days with Ample Digit Room) */}
      <div className="p-5 rounded-[24px] bg-[#1C1C1E] border border-[#2C2C30] shadow-sm">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-xs font-bold text-[#A1A1A6] tracking-wider uppercase">
            LISTENING ACTIVITY
          </h3>
          <span className="text-xs text-[#A1A1A6]">
            minutes · last 10 days
          </span>
        </div>

        {/* 10-Day Bar Graph with generous column width and digit clearance */}
        <div className="flex items-end justify-between gap-2 sm:gap-3 h-32 pt-2 px-1">
          {activityDays.map((bar, idx) => {
            const heightPercent = Math.min(100, (bar.minutes / 68) * 100);

            return (
              <div key={idx} className="flex-1 flex flex-col items-center justify-end h-full group min-w-0">
                {/* Minute Label above bar with ample room for 2-3 digits */}
                <span className="text-[11px] font-semibold text-white/90 mb-1.5 tabular-nums text-center">
                  {bar.minutes}
                </span>

                {/* The vertical bar */}
                <div
                  className={`w-full max-w-[26px] rounded-t-lg transition-all ${
                    bar.highlight
                      ? 'bg-[#00D2FF] shadow-[0_0_8px_rgba(0,210,255,0.4)]'
                      : 'bg-[#008EFF] group-hover:bg-[#00A8FF]'
                  }`}
                  style={{ height: `${heightPercent}%` }}
                />

                {/* Day of Week Label */}
                <span className="text-[11px] text-[#A1A1A6] mt-2 font-medium tracking-tight">
                  {bar.day}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* 4. YOUR LIBRARY Card (Total 383, Progress Hairline, Subscriptions & Playlists tiles) */}
      <div className="p-5 rounded-[24px] bg-[#1C1C1E] border border-[#2C2C30] shadow-sm relative">
        <div className="flex items-start justify-between mb-3">
          <div>
            <h3 className="text-xs font-bold text-[#A1A1A6] tracking-wider uppercase">
              YOUR LIBRARY
            </h3>
            <p className="text-xs text-[#A1A1A6] mt-0.5">
              Episodes in your library
            </p>
          </div>

          {/* Large 383 Count with cyan radial aura */}
          <div className="relative">
            <span className="absolute -inset-3 bg-[#008EFF]/20 blur-xl rounded-full" />
            <span className="relative text-3xl font-extrabold text-[#00D2FF] tracking-tight">
              383
            </span>
          </div>
        </div>

        {/* Segmented Progress Bar */}
        <div className="w-full h-1.5 bg-[#2C2C30] rounded-full overflow-hidden mb-3.5 flex">
          <div className="h-full bg-[#008EFF] rounded-full" style={{ width: '4%' }} />
          <div className="h-full bg-[#00D2FF] rounded-full" style={{ width: '4%' }} />
        </div>

        {/* Status Legend Row */}
        <div className="flex items-center gap-4 text-xs text-[#A1A1A6] mb-4">
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-[#008EFF]" />
            <span>Played 3</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-[#00D2FF]" />
            <span>In progress 3</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-[#2C2C30]" />
            <span>Waiting 377</span>
          </div>
        </div>

        {/* Two Equal Interactive Action Tiles */}
        <div className="grid grid-cols-2 gap-3">
          {/* Subscriptions Tile */}
          <div
            onClick={() => onNavigate('podcasts')}
            className="flex items-center gap-3 p-3.5 rounded-2xl bg-[#26262A] hover:bg-[#2F2F34] border border-white/5 cursor-pointer transition-colors"
          >
            <div className="w-10 h-10 rounded-full bg-[#008EFF]/20 text-[#008EFF] flex items-center justify-center shrink-0">
              <Library className="w-5 h-5" />
            </div>
            <div>
              <p className="text-lg font-black text-white leading-none">96</p>
              <p className="text-xs text-[#A1A1A6] mt-1">Subscriptions</p>
            </div>
          </div>

          {/* Playlists Tile */}
          <div
            onClick={() => onNavigate('playlists')}
            className="flex items-center gap-3 p-3.5 rounded-2xl bg-[#26262A] hover:bg-[#2F2F34] border border-white/5 cursor-pointer transition-colors"
          >
            <div className="w-10 h-10 rounded-full bg-[#00D2FF]/20 text-[#00D2FF] flex items-center justify-center shrink-0">
              <ListOrdered className="w-5 h-5" />
            </div>
            <div>
              <p className="text-lg font-black text-white leading-none">20</p>
              <p className="text-xs text-[#A1A1A6] mt-1">Playlists</p>
            </div>
          </div>
        </div>
      </div>

      {/* 5. MOST EPISODES AND MOST LISTENED Section */}
      <div className="p-5 rounded-[24px] bg-[#1C1C1E] border border-[#2C2C30] shadow-sm space-y-5">
        <div>
          <h3 className="text-xs font-bold text-[#A1A1A6] tracking-wider uppercase mb-1">
            MOST EPISODES AND MOST LISTENED
          </h3>
        </div>

        {/* Most episodes row */}
        <div>
          <p className="text-xs text-[#A1A1A6] mb-3">Most episodes</p>
          <div className="grid grid-cols-4 gap-2.5">
            {mostEpisodesPodcasts.map((item, idx) => (
              <div
                key={idx}
                onClick={() => onNavigate('podcasts')}
                className="flex flex-col items-center cursor-pointer group"
              >
                <div className="w-full aspect-square rounded-2xl overflow-hidden bg-[#26262A] border border-white/10 mb-1.5 shadow-sm flex items-center justify-center text-center p-1">
                  {item.artworkUrl ? (
                    <img src={item.artworkUrl} alt={item.title} className="w-full h-full object-cover group-hover:scale-105 transition-transform" />
                  ) : (
                    <span className="text-[10px] font-bold text-white uppercase tracking-tighter">
                      {item.title}
                    </span>
                  )}
                </div>
                <span className="text-xs font-bold text-white group-hover:text-[#00D2FF]">
                  {item.count}
                </span>
              </div>
            ))}
          </div>
        </div>

        <div className="h-px bg-white/5 w-full" />

        {/* Most listened to row */}
        <div>
          <p className="text-xs text-[#A1A1A6] mb-3">Most listened to</p>
          <div className="grid grid-cols-3 gap-3">
            {mostListenedPodcasts.map((item, idx) => (
              <div
                key={idx}
                onClick={() => onNavigate('podcasts')}
                className="flex flex-col items-center cursor-pointer group"
              >
                <div
                  className={`w-full aspect-square rounded-2xl overflow-hidden border border-white/10 mb-1.5 shadow-sm flex items-center justify-center text-center p-2 bg-gradient-to-br ${
                    item.bg || 'from-zinc-800 to-zinc-900'
                  }`}
                >
                  {item.artworkUrl ? (
                    <img src={item.artworkUrl} alt={item.title} className="w-full h-full object-cover group-hover:scale-105 transition-transform" />
                  ) : (
                    <span className="text-[11px] font-serif font-bold text-white/90 whitespace-pre-line leading-tight">
                      {item.label}
                    </span>
                  )}
                </div>
                <span className="text-xs font-bold text-white group-hover:text-[#00D2FF]">
                  {item.count}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};
