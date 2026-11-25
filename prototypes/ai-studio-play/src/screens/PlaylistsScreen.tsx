import React, { useState } from 'react';
import { usePlayer } from '../context/PlayerContext';
import { Playlist, Episode } from '../types';
import { formatDurationHuman } from '../utils/format';
import { QueueIndicator } from '../components/QueueIndicator';
import {
  Plus,
  Play,
  Pause,
  ArrowLeft,
  Pin,
  ListMusic,
  Check,
  Trash2,
  SlidersHorizontal,
  MoreVertical,
  Settings,
  Folder,
  Sliders,
} from 'lucide-react';

interface PlaylistsScreenProps {
  onOpenSettings?: () => void;
}

export const PlaylistsScreen: React.FC<PlaylistsScreenProps> = ({ onOpenSettings }) => {
  const {
    playlists,
    episodes,
    currentEpisode,
    isPlaying,
    playEpisode,
    togglePlayPause,
    enqueue,
    dequeue,
    enqueueAll,
    dequeueAll,
    queue,
    createPlaylist,
    deletePlaylist,
    setActiveNotesEpisode,
    settings,
    isPro,
    openUpgradeModal,
  } = usePlayer();

  const [selectedPlaylist, setSelectedPlaylist] = useState<Playlist | null>(null);
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('list');
  const [selectedFolder, setSelectedFolder] = useState<string>('all');
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [filterUnplayedOnly, setFilterUnplayedOnly] = useState(false);
  const [sortAsc, setSortAsc] = useState(true);

  // Folders computation
  const folders = [
    { id: 'all', label: `All (${playlists.length})`, isFolder: false },
    { id: 'AI', label: 'AI (1)', isFolder: true },
    { id: 'Football', label: 'Football (1)', isFolder: true },
  ];

  // Resolve episodes for a playlist
  const getPlaylistEpisodes = (pl: Playlist): Episode[] => {
    let result: Episode[] = [];
    if (pl.isSmart) {
      if (pl.smartType === 'in_progress') result = episodes.filter((e) => e.playingStatus === 1 && !e.isArchived);
      else if (pl.smartType === 'unplayed') result = episodes.filter((e) => e.playingStatus === 0 && !e.isArchived);
      else if (pl.smartType === 'played') result = episodes.filter((e) => e.playingStatus === 2);
      else if (pl.smartType === 'downloaded') result = episodes.filter((e) => e.isDownloaded && !e.isArchived);
      else if (pl.smartType === 'new_releases') {
        const sevenDaysAgo = Date.now() - 7 * 86400000;
        result = episodes.filter((e) => e.published >= sevenDaysAgo && !e.isArchived);
      }
    } else {
      if (pl.episodeIds && pl.episodeIds.length > 0) {
        result = episodes.filter((e) => pl.episodeIds?.includes(e.uuid) && !e.isArchived);
      } else if (pl.assignedPodcastUuids && pl.assignedPodcastUuids.length > 0) {
        result = episodes.filter((e) => pl.assignedPodcastUuids?.includes(e.podcastUuid) && !e.isArchived);
      } else {
        // Default library list for "Everything" / custom mixes
        result = episodes.filter((e) => !e.isArchived);
      }
    }

    if (filterUnplayedOnly) {
      result = result.filter((e) => e.playingStatus === 0);
    }

    return result;
  };

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim()) return;
    createPlaylist(newTitle.trim());
    setNewTitle('');
    setIsCreateOpen(false);
  };

  // Play entire playlist from first episode
  const handlePlayPlaylist = (pl: Playlist) => {
    const epList = getPlaylistEpisodes(pl);
    if (epList.length > 0) {
      playEpisode(epList[0]);
    }
  };

  // Filter playlists by active folder
  const displayedPlaylists = playlists.filter((pl) => {
    if (selectedFolder === 'all') return true;
    return pl.folder?.toLowerCase() === selectedFolder.toLowerCase();
  });

  // Render 2x2 collage artwork or single artwork
  const renderArtCollage = (pl: Playlist, className = 'w-full h-full') => {
    const artList = pl.artworkUrls || [];
    if (artList.length >= 4) {
      return (
        <div className={`grid grid-cols-2 grid-rows-2 ${className}`}>
          <img src={artList[0]} alt="" className="w-full h-full object-cover" />
          <img src={artList[1]} alt="" className="w-full h-full object-cover" />
          <img src={artList[2]} alt="" className="w-full h-full object-cover" />
          <img src={artList[3]} alt="" className="w-full h-full object-cover" />
        </div>
      );
    }
    const single = artList[0] || '/assets/official-app-icon.png';
    return <img src={single} alt="" className={`object-cover ${className}`} />;
  };

  // =================================================================
  // 1. PLAYLIST DETAIL SCREEN (Matches Screenshot_20261112_080005_Lohe.jpg)
  // =================================================================
  if (selectedPlaylist) {
    const plEpisodes = getPlaylistEpisodes(selectedPlaylist);
    const totalDurationSec = plEpisodes.reduce((acc, ep) => acc + (ep.durationSeconds || 0), 0);
    const durationDisplay = selectedPlaylist.durationString || formatDurationHuman(totalDurationSec);
    const epCountDisplay = selectedPlaylist.totalEpisodeCount || plEpisodes.length;
    const assignedCount = selectedPlaylist.assignedPodcastCount || 97;

    const epIds = plEpisodes.map((e) => e.uuid);
    const allQueued = epIds.length > 0 && epIds.every((id) => queue.some((q) => q.uuid === id));

    return (
      <div className="flex-1 overflow-y-auto pb-44 px-4 sm:px-6 pt-2 max-w-2xl mx-auto w-full select-none font-sans">
        {/* Top App Bar */}
        <div className="sticky top-0 z-20 flex items-center justify-between py-3 bg-[#121214]/95 backdrop-blur-md -mx-4 px-4 border-b border-[#26262A]/60">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setSelectedPlaylist(null)}
              className="w-10 h-10 flex items-center justify-center -ml-2 rounded-full text-white/90 hover:text-white hover:bg-white/10 active:scale-95 transition-all"
              aria-label="Back to playlists"
            >
              <ArrowLeft className="w-6 h-6 text-white" />
            </button>
            <div>
              <h1 className="text-xl font-bold text-white tracking-tight leading-snug">
                {selectedPlaylist.title}
              </h1>
              <p className="text-xs text-[#8E8E93]">
                {epCountDisplay} episodes · {durationDisplay}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1 text-[#8E8E93]">
            <button
              type="button"
              onClick={() => setSortAsc(!sortAsc)}
              className="w-10 h-10 flex items-center justify-center rounded-full hover:text-white hover:bg-white/10 transition-colors"
              title="Sort episodes"
            >
              <SlidersHorizontal className="w-5 h-5" />
            </button>
            <button
              type="button"
              className="w-10 h-10 flex items-center justify-center rounded-full hover:text-white hover:bg-white/10 transition-colors"
              title="More options"
            >
              <MoreVertical className="w-5 h-5" />
            </button>
            {onOpenSettings && (
              <button
                type="button"
                onClick={onOpenSettings}
                className="w-10 h-10 flex items-center justify-center rounded-full hover:text-white hover:bg-white/10 transition-colors"
                title="Settings"
              >
                <Settings className="w-5 h-5" />
              </button>
            )}
          </div>
        </div>

        {/* Assigned Podcasts Subheader */}
        <div className="flex items-center justify-between mt-4 mb-3">
          <div>
            <h2 className="text-base font-bold text-white">Podcasts</h2>
            <p className="text-xs text-[#8E8E93] mt-0.5">{assignedCount} assigned</p>
          </div>
          <button
            type="button"
            className="text-sm font-semibold text-[#008EFF] hover:underline"
          >
            Edit
          </button>
        </div>

        {/* Action Bar: Dual Pill Buttons (Play & Up Next) + Filter Icon */}
        <div className="flex items-center gap-3 mb-5">
          <button
            type="button"
            disabled={plEpisodes.length === 0}
            onClick={() => handlePlayPlaylist(selectedPlaylist)}
            className="flex-1 py-3 px-6 rounded-full bg-[#008EFF] hover:bg-[#00A8FF] disabled:opacity-40 text-white text-base font-bold flex items-center justify-center gap-2.5 shadow-md active:scale-95 transition-all"
          >
            <Play className="w-4 h-4 fill-white" />
            <span>Play</span>
          </button>

          <button
            type="button"
            disabled={plEpisodes.length === 0}
            onClick={() => {
              if (allQueued) dequeueAll(epIds);
              else enqueueAll(epIds, false);
            }}
            className={`flex-1 py-3 px-6 rounded-full border text-base font-semibold flex items-center justify-center gap-2 active:scale-95 transition-all ${
              allQueued
                ? 'bg-[#008EFF]/20 border-[#008EFF] text-[#00D2FF]'
                : 'bg-[#1C1C1E] hover:bg-[#26262A] border-[#2C2C30] text-white'
            }`}
          >
            <ListMusic className="w-4 h-4" />
            <span>{allQueued ? 'In Up Next' : 'Up Next'}</span>
          </button>

          {/* Filter button (Three decreasing horizontal lines) */}
          <button
            type="button"
            onClick={() => setFilterUnplayedOnly(!filterUnplayedOnly)}
            className={`w-11 h-11 rounded-full flex items-center justify-center transition-colors shrink-0 ${
              filterUnplayedOnly ? 'bg-[#008EFF]/20 text-[#00D2FF]' : 'text-[#008EFF] hover:bg-white/10'
            }`}
            title={filterUnplayedOnly ? 'Showing unplayed' : 'Filter episodes'}
          >
            <Sliders className="w-5 h-5" />
          </button>
        </div>

        {/* Episode List Rows (Numbered, compact Material 3 style) */}
        <div className="space-y-2">
          {plEpisodes.map((ep, idx) => {
            const isCurPlaying = currentEpisode?.uuid === ep.uuid && isPlaying;
            const isQueued = queue.some((q) => q.uuid === ep.uuid);

            return (
              <div
                key={ep.uuid}
                onClick={() => setActiveNotesEpisode(ep)}
                className="group relative flex items-center justify-between p-3 rounded-[18px] bg-[#1C1C1E] hover:bg-[#26262A] border border-[#2C2C30]/50 transition-all cursor-pointer overflow-hidden"
              >
                {/* Left: Index number + Artwork + Details */}
                <div className="flex items-center gap-3 min-w-0 pr-2">
                  <span
                    className={`text-xs font-semibold w-4 text-center shrink-0 ${
                      idx === 0 || isCurPlaying ? 'text-[#008EFF] font-bold' : 'text-[#8E8E93]'
                    }`}
                  >
                    {idx + 1}
                  </span>

                  <div className="w-10 h-10 rounded-lg overflow-hidden bg-black/40 shrink-0 border border-white/5 shadow-sm">
                    {ep.artworkUrl ? (
                      <img src={ep.artworkUrl} alt="" className="w-full h-full object-cover" />
                    ) : (
                      <img src="/assets/official-app-icon.png" alt="" className="w-full h-full object-cover" />
                    )}
                  </div>

                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <QueueIndicator settings={settings.queueIndicator} isQueued={isQueued} />
                      <h4 className="text-[14px] font-medium text-white truncate group-hover:text-[#00D2FF]">
                        {ep.title}
                      </h4>
                    </div>
                    <p className="text-xs text-[#8E8E93] truncate mt-0.5">
                      Today · <span className="font-bold text-[#008EFF]">{formatDurationHuman(ep.durationSeconds || 0)}</span> · {ep.podcastTitle}
                    </p>
                  </div>
                </div>

                {/* Right: Play Button in Electric Blue */}
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    if (currentEpisode?.uuid === ep.uuid) togglePlayPause();
                    else playEpisode(ep);
                  }}
                  className="w-10 h-10 flex items-center justify-center rounded-full text-[#008EFF] hover:bg-white/10 shrink-0 active:scale-95 transition-all"
                  aria-label="Play episode"
                >
                  {isCurPlaying ? (
                    <Pause className="w-4 h-4 fill-current" />
                  ) : (
                    <Play className="w-4 h-4 fill-current" />
                  )}
                </button>
              </div>
            );
          })}
        </div>
      </div>
    );
  }

  // =================================================================
  // 2. PLAYLISTS OVERVIEW SCREEN (Grid / List Mode)
  // Matches Screenshot_20261112_075957_Lohe.jpg & Screenshot_20261112_080011_Lohe.jpg
  // =================================================================
  return (
    <div className="flex-1 overflow-y-auto pb-44 px-4 sm:px-6 pt-3 max-w-2xl mx-auto w-full select-none font-sans">
      {/* Top App Bar */}
      <header className="flex items-center justify-between mb-3">
        <div className="flex items-baseline gap-2">
          <h1 className="text-2xl font-bold text-white tracking-tight">Playlists</h1>
          <span className="text-lg font-normal text-[#8E8E93]">
            {playlists.length}
          </span>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setIsCreateOpen(true)}
            className="px-4 py-2 rounded-full bg-[#008EFF] hover:bg-[#00A8FF] text-white text-sm font-semibold flex items-center gap-1.5 shadow-md active:scale-95 transition-all"
          >
            <Plus className="w-4 h-4 stroke-[2.5]" />
            <span>New</span>
          </button>

          {onOpenSettings && (
            <button
              type="button"
              onClick={onOpenSettings}
              className="w-10 h-10 flex items-center justify-center rounded-full text-[#8E8E93] hover:text-white hover:bg-white/10 transition-colors"
              title="Settings"
              aria-label="Settings"
            >
              <Settings className="w-5 h-5" />
            </button>
          )}
        </div>
      </header>

      {/* Action / View Controls Row */}
      <div className="flex items-center justify-between py-2 mb-3">
        {/* Sort Button */}
        <button
          type="button"
          onClick={() => setSortAsc(!sortAsc)}
          className="flex items-center gap-1.5 text-sm font-medium text-[#008EFF] hover:text-[#00D2FF] transition-colors"
        >
          <SlidersHorizontal className="w-4 h-4" />
          <span>Sort</span>
        </button>

        {/* Segmented Control (Grid / List) + Overflow Menu */}
        <div className="flex items-center gap-2">
          <div className="flex items-center rounded-full bg-[#1C1C1E] border border-[#2C2C30] p-0.5">
            <button
              type="button"
              onClick={() => setViewMode('grid')}
              className={`px-3.5 py-1.5 rounded-full text-xs font-semibold flex items-center gap-1 transition-all ${
                viewMode === 'grid'
                  ? 'bg-[#26262A] text-white border border-[#3A3A40]'
                  : 'text-[#8E8E93] hover:text-white'
              }`}
            >
              {viewMode === 'grid' && <Check className="w-3.5 h-3.5 text-[#008EFF]" />}
              <span>Grid</span>
            </button>
            <button
              type="button"
              onClick={() => setViewMode('list')}
              className={`px-3.5 py-1.5 rounded-full text-xs font-semibold flex items-center gap-1 transition-all ${
                viewMode === 'list'
                  ? 'bg-[#26262A] text-white border border-[#3A3A40]'
                  : 'text-[#8E8E93] hover:text-white'
              }`}
            >
              {viewMode === 'list' && <Check className="w-3.5 h-3.5 text-[#008EFF]" />}
              <span>List</span>
            </button>
          </div>

          <button
            type="button"
            className="w-9 h-9 flex items-center justify-center rounded-full text-[#8E8E93] hover:text-white hover:bg-white/10 transition-colors"
            title="Options"
          >
            <MoreVertical className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Folder Chips Horizontal Scroll */}
      <div className="flex items-center gap-2 overflow-x-auto pb-3 mb-4 scrollbar-none">
        {folders.map((f) => {
          const isActive = selectedFolder === f.id;
          return (
            <button
              key={f.id}
              type="button"
              onClick={() => setSelectedFolder(f.id)}
              className={`px-4 py-2 rounded-2xl text-xs font-semibold flex items-center gap-1.5 shrink-0 transition-all ${
                isActive
                  ? 'bg-[#1E3B57] text-[#00D2FF] border border-[#008EFF]/40'
                  : 'bg-[#1C1C1E] text-[#8E8E93] border border-[#2C2C30] hover:text-white hover:bg-[#26262A]'
              }`}
            >
              {f.isFolder && <Folder className="w-3.5 h-3.5 text-[#008EFF] fill-[#008EFF]" />}
              <span>{f.label}</span>
            </button>
          );
        })}
      </div>

      {/* ============================================================= */}
      {/* A. GRID MODE VIEW (Matches Screenshot_20261112_075957_Lohe.jpg) */}
      {/* ============================================================= */}
      {viewMode === 'grid' && (
        <div className="grid grid-cols-2 gap-3.5">
          {displayedPlaylists.map((pl) => {
            const plEpisodes = getPlaylistEpisodes(pl);
            const epCount = pl.totalEpisodeCount || plEpisodes.length;
            const durationStr = pl.durationString || formatDurationHuman(plEpisodes.reduce((a, b) => a + (b.durationSeconds || 0), 0));

            return (
              <div
                key={pl.id}
                onClick={() => setSelectedPlaylist(pl)}
                className="group p-3 rounded-[20px] bg-[#1C1C1E] hover:bg-[#26262A] border border-[#2C2C30] hover:border-[#008EFF]/50 shadow-md transition-all cursor-pointer flex flex-col justify-between"
              >
                {/* 2x2 Artwork Area with Pin & Auto-download Badges */}
                <div className="relative aspect-square w-full rounded-[14px] overflow-hidden bg-black/40 mb-2.5 shadow-sm">
                  {renderArtCollage(pl)}

                  {/* Pin badge in top right */}
                  {pl.isPinned && (
                    <div className="absolute top-2 right-2 w-7 h-7 rounded-full bg-black/60 backdrop-blur-md flex items-center justify-center text-white shadow">
                      <Pin className="w-3.5 h-3.5 fill-white text-white" />
                    </div>
                  )}

                  {/* Auto-download badge at bottom-left */}
                  {pl.autoDownload && (
                    <div className="absolute bottom-2 left-2 px-2 py-0.5 rounded-full bg-black/75 backdrop-blur-md text-[10px] font-semibold text-white shadow">
                      Auto-download
                    </div>
                  )}
                </div>

                {/* Playlist Info */}
                <div>
                  <h3 className="text-[15px] font-bold text-white group-hover:text-[#00D2FF] transition-colors truncate">
                    {pl.title}
                  </h3>
                  <p className="text-xs mt-0.5">
                    <span className="font-bold text-[#008EFF]">{epCount}</span>{' '}
                    <span className="text-[#8E8E93]">episodes</span>
                  </p>
                  <p className="text-xs text-[#8E8E93] mt-0.5">{durationStr}</p>
                </div>

                {/* Card Action Row: Overflow & Play */}
                <div className="flex items-center justify-between mt-3 pt-2 border-t border-white/5">
                  <button
                    type="button"
                    onClick={(e) => e.stopPropagation()}
                    className="p-1.5 text-[#8E8E93] hover:text-white rounded-full transition-colors"
                  >
                    <MoreVertical className="w-4 h-4" />
                  </button>

                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      handlePlayPlaylist(pl);
                    }}
                    className="w-10 h-10 rounded-full bg-[#1E3B57] hover:bg-[#008EFF] text-[#008EFF] hover:text-white flex items-center justify-center shadow transition-all active:scale-95"
                    aria-label={`Play ${pl.title}`}
                  >
                    <Play className="w-4 h-4 fill-current ml-0.5" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* ============================================================= */}
      {/* B. LIST MODE VIEW (Matches Screenshot_20261112_080011_Lohe.jpg) */}
      {/* ============================================================= */}
      {viewMode === 'list' && (
        <div className="space-y-2.5">
          {displayedPlaylists.map((pl) => {
            const plEpisodes = getPlaylistEpisodes(pl);
            const epCount = pl.totalEpisodeCount || plEpisodes.length;
            const durationStr = pl.durationString || formatDurationHuman(plEpisodes.reduce((a, b) => a + (b.durationSeconds || 0), 0));
            const unplayedCount = pl.unplayedCount !== undefined ? pl.unplayedCount : epCount;

            return (
              <div
                key={pl.id}
                onClick={() => setSelectedPlaylist(pl)}
                className="group p-3 rounded-[20px] bg-[#1C1C1E] hover:bg-[#26262A] border border-[#2C2C30] hover:border-[#008EFF]/50 shadow-md transition-all cursor-pointer flex items-center justify-between"
              >
                {/* Left: Thumbnail & Details */}
                <div className="flex items-center gap-3.5 min-w-0 pr-2">
                  <div className="w-14 h-14 rounded-[14px] overflow-hidden bg-black/40 shrink-0 shadow-sm">
                    {renderArtCollage(pl)}
                  </div>

                  <div className="min-w-0">
                    <div className="flex items-center gap-1.5">
                      <h3 className="text-base font-bold text-white group-hover:text-[#00D2FF] truncate">
                        {pl.title}
                      </h3>
                      {pl.isPinned && (
                        <Pin className="w-3.5 h-3.5 text-[#8E8E93] fill-[#8E8E93] shrink-0" />
                      )}
                    </div>
                    <p className="text-xs mt-0.5 text-[#8E8E93] truncate">
                      <span className="font-bold text-[#008EFF]">{epCount}</span> ep · {durationStr}
                    </p>
                    <p className="text-xs text-[#8E8E93] truncate mt-0.5">
                      {pl.folder ? `${pl.folder} · ` : ''}{unplayedCount} unplayed
                    </p>
                  </div>
                </div>

                {/* Right: Overflow & Play Button */}
                <div className="flex items-center gap-2 shrink-0" onClick={(e) => e.stopPropagation()}>
                  <button
                    type="button"
                    className="p-2 text-[#8E8E93] hover:text-white rounded-full transition-colors"
                  >
                    <MoreVertical className="w-4 h-4" />
                  </button>

                  <button
                    type="button"
                    onClick={() => handlePlayPlaylist(pl)}
                    className="w-10 h-10 rounded-full bg-[#1E3B57] hover:bg-[#008EFF] text-[#008EFF] hover:text-white flex items-center justify-center shadow transition-all active:scale-95"
                    aria-label={`Play ${pl.title}`}
                  >
                    <Play className="w-4 h-4 fill-current ml-0.5" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Create Playlist Modal */}
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4">
          <div className="w-full max-w-sm p-6 rounded-[24px] bg-[#1C1C1E] border border-[#2C2C30] text-white shadow-2xl">
            <h3 className="text-lg font-bold mb-2">Create New Playlist</h3>
            <p className="text-xs text-[#8E8E93] mb-4">
              Give your playlist a title to group your shows and listening sessions.
            </p>
            <form onSubmit={handleCreate}>
              <input
                type="text"
                autoFocus
                placeholder="Playlist name..."
                value={newTitle}
                onChange={(e) => setNewTitle(e.target.value)}
                className="w-full px-4 py-3 rounded-xl bg-black/40 border border-[#2C2C30] text-sm text-white placeholder-white/30 focus:outline-none focus:border-[#008EFF] mb-5"
              />
              <div className="flex justify-end gap-2.5">
                <button
                  type="button"
                  onClick={() => setIsCreateOpen(false)}
                  className="px-4 py-2.5 rounded-xl text-xs font-semibold text-white/70 hover:text-white"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2.5 rounded-xl bg-[#008EFF] hover:bg-[#00A8FF] text-white text-xs font-bold shadow-md"
                >
                  Create
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default PlaylistsScreen;
