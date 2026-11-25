import React, { useState } from 'react';
import { usePlayer } from '../context/PlayerContext';
import { Podcast, Episode } from '../types';
import { CatalogService } from '../services/catalog';
import { formatDurationHuman } from '../utils/format';
import { QueueIndicator } from '../components/QueueIndicator';
import {
  Search,
  Plus,
  FileUp,
  FileDown,
  LayoutGrid,
  List as ListIcon,
  ChevronLeft,
  Play,
  Pause,
  ListPlus,
  Check,
  CheckCircle2,
  Trash2,
  SlidersHorizontal,
  ExternalLink,
} from 'lucide-react';

export const PodcastsScreen: React.FC = () => {
  const {
    podcasts,
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
    subscribePodcast,
    unsubscribePodcast,
    setActiveNotesEpisode,
    settings,
  } = usePlayer();

  const [searchQuery, setSearchQuery] = useState('');
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [selectedPodcast, setSelectedPodcast] = useState<Podcast | null>(null);

  // Filter within selected podcast: default is 'unplayed' per Lohe specs
  const [episodeFilter, setEpisodeFilter] = useState<'unplayed' | 'all' | 'in_progress' | 'played' | 'downloaded' | 'starred' | 'archived'>('unplayed');

  // Manual Add Feed Modal state
  const [isAddFeedOpen, setIsAddFeedOpen] = useState(false);
  const [customFeedUrl, setCustomFeedUrl] = useState('');
  const [feedLoading, setFeedLoading] = useState(false);
  const [feedError, setFeedError] = useState('');

  const activePodcasts = podcasts.filter((p) => p.isSubscribed);

  const filteredPodcasts = activePodcasts.filter((p) =>
    p.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
    (p.author && p.author.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  // Handle OPML Export
  const handleExportOpml = () => {
    const xml = CatalogService.exportOpml(podcasts);
    const blob = new Blob([xml], { type: 'application/xml' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'lohe-subscriptions.opml';
    a.click();
    URL.revokeObjectURL(url);
  };

  // Handle OPML Import
  const handleImportOpml = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = async (evt) => {
      const content = evt.target?.result as string;
      if (!content) return;
      try {
        const feeds = CatalogService.parseOpml(content);
        let importedCount = 0;
        for (const feed of feeds.slice(0, 10)) {
          try {
            const data = await CatalogService.fetchFeed(feed.feedUrl);
            if (data?.podcast) {
              subscribePodcast(data.podcast as Podcast, data.episodes as Episode[]);
              importedCount++;
            }
          } catch {
            // continue
          }
        }
        alert(`Imported ${importedCount} podcasts from OPML.`);
      } catch (err) {
        alert("Failed to parse OPML file.");
      }
    };
    reader.readAsText(file);
  };

  // Add custom feed
  const handleAddFeed = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!customFeedUrl.trim()) return;

    setFeedLoading(true);
    setFeedError('');

    try {
      const data = await CatalogService.fetchFeed(customFeedUrl.trim());
      if (!data || !data.podcast) throw new Error("Could not parse podcast feed.");

      subscribePodcast(data.podcast as Podcast, data.episodes as Episode[]);
      setIsAddFeedOpen(false);
      setCustomFeedUrl('');
      setSelectedPodcast(data.podcast as Podcast);
    } catch (err: any) {
      setFeedError(err.message || "Failed to load feed. Check URL and try again.");
    } finally {
      setFeedLoading(false);
    }
  };

  // If a podcast is selected, render the Show Detail View
  if (selectedPodcast) {
    const showEpisodes = episodes.filter((ep) => ep.podcastUuid === selectedPodcast.uuid);

    const filteredEpisodes = showEpisodes.filter((ep) => {
      if (episodeFilter === 'unplayed') return ep.playingStatus === 0 && !ep.isArchived;
      if (episodeFilter === 'in_progress') return ep.playingStatus === 1 && !ep.isArchived;
      if (episodeFilter === 'played') return ep.playingStatus === 2 && !ep.isArchived;
      if (episodeFilter === 'downloaded') return ep.isDownloaded && !ep.isArchived;
      if (episodeFilter === 'starred') return ep.starred && !ep.isArchived;
      if (episodeFilter === 'archived') return ep.isArchived;
      return !ep.isArchived; // 'all'
    });

    return (
      <div className="flex-1 overflow-y-auto pb-36 px-4 pt-3 max-w-2xl mx-auto w-full select-none">
        {/* Back navigation */}
        <button
          type="button"
          onClick={() => setSelectedPodcast(null)}
          className="flex items-center gap-1.5 py-2 px-3 -ml-2 rounded-xl text-[#b0bfd2] hover:text-white hover:bg-white/5 transition-colors text-sm font-semibold mb-2"
        >
          <ChevronLeft className="w-5 h-5" />
          <span>My Podcasts</span>
        </button>

        {/* Podcast Header Card */}
        <div className="p-5 rounded-[24px] bg-[#1C1C1E] border border-[#2C2C30] shadow-xl mb-4">
          <div className="flex gap-4">
            <div className="w-24 h-24 rounded-2xl overflow-hidden shrink-0 bg-[#26262A] shadow-md border border-white/10">
              {selectedPodcast.artworkUrl ? (
                <img src={selectedPodcast.artworkUrl} alt={selectedPodcast.title} className="w-full h-full object-cover" />
              ) : (
                <div className="w-full h-full flex items-center justify-center font-bold text-white/40">SHOW</div>
              )}
            </div>
            <div className="min-w-0 flex-1">
              <h1 className="text-xl font-bold text-white leading-tight">
                {selectedPodcast.title}
              </h1>
              {selectedPodcast.author && (
                <p className="text-xs font-semibold text-[#008EFF] mt-1">
                  {selectedPodcast.author}
                </p>
              )}
              <div className="flex items-center gap-2 mt-3">
                <button
                  type="button"
                  onClick={() => {
                    unsubscribePodcast(selectedPodcast.uuid);
                    setSelectedPodcast(null);
                  }}
                  className="px-3 py-1.5 rounded-full bg-red-500/15 hover:bg-red-500/25 text-red-400 text-xs font-semibold transition-colors"
                >
                  Unsubscribe
                </button>
              </div>
            </div>
          </div>

          {selectedPodcast.description && (
            <p className="text-xs text-[#A1A1A6] mt-4 leading-relaxed line-clamp-3">
              {selectedPodcast.description}
            </p>
          )}

          {/* Podcast Action Controls: Play All, Add All to Up Next */}
          <div className="flex items-center gap-2.5 mt-4 pt-3.5 border-t border-[#2C2C30]">
            <button
              type="button"
              disabled={filteredEpisodes.length === 0}
              onClick={() => {
                if (filteredEpisodes.length > 0) {
                  playEpisode(filteredEpisodes[0]);
                }
              }}
              className="flex-1 py-2.5 px-4 rounded-full bg-[#008EFF] hover:bg-[#00A8FF] disabled:opacity-40 text-white text-xs font-bold flex items-center justify-center gap-2 shadow-sm active:scale-95 transition-all"
            >
              <Play className="w-3.5 h-3.5 fill-current" />
              <span>Play</span>
            </button>

            {/* Up Next: Add all / Remove all from Up Next */}
            {(() => {
              const episodeIds = filteredEpisodes.map((e) => e.uuid);
              const allQueued = episodeIds.length > 0 && episodeIds.every((id) => queue.some((q) => q.uuid === id));

              return (
                <button
                  type="button"
                  disabled={filteredEpisodes.length === 0}
                  onClick={() => {
                    if (allQueued) {
                      dequeueAll(episodeIds);
                    } else {
                      enqueueAll(episodeIds, false);
                    }
                  }}
                  className={`flex-1 py-2.5 px-4 rounded-full border text-xs font-bold flex items-center justify-center gap-2 active:scale-95 transition-all ${
                    allQueued
                      ? 'bg-[#008EFF]/20 border-[#008EFF] text-[#00D2FF]'
                      : 'bg-[#26262A] hover:bg-[#2F2F34] border-[#323238] text-white'
                  }`}
                  title="Add all filtered episodes to Up Next"
                >
                  {allQueued ? <Check className="w-3.5 h-3.5" /> : <ListPlus className="w-3.5 h-3.5" />}
                  <span>{allQueued ? 'In Up Next' : 'Up Next'}</span>
                </button>
              );
            })()}
          </div>
        </div>

        {/* Filter Chips Bar */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-3 mb-2 scrollbar-none">
          {(['unplayed', 'all', 'in_progress', 'played', 'downloaded', 'starred', 'archived'] as const).map((filterKey) => (
            <button
              key={filterKey}
              type="button"
              onClick={() => setEpisodeFilter(filterKey)}
              className={`px-3 py-1.5 rounded-full text-xs font-semibold shrink-0 transition-all ${
                episodeFilter === filterKey
                  ? 'bg-[#008EFF] text-white shadow-sm'
                  : 'bg-white/5 hover:bg-white/10 text-[#A1A1A6]'
              }`}
            >
              {filterKey === 'in_progress' ? 'In Progress' : filterKey.charAt(0).toUpperCase() + filterKey.slice(1)}
            </button>
          ))}
        </div>

        {/* Episodes List */}
        <div className="space-y-2">
          {filteredEpisodes.length === 0 ? (
            <div className="p-8 text-center bg-[#17233a]/30 rounded-2xl border border-white/5">
              <p className="text-xs text-[#b0bfd2]">No episodes match this filter.</p>
            </div>
          ) : (
            filteredEpisodes.map((ep) => {
              const isCurPlaying = currentEpisode?.uuid === ep.uuid && isPlaying;
              const isQueued = queue.some((q) => q.uuid === ep.uuid);

              return (
                <div
                  key={ep.uuid}
                  onClick={() => setActiveNotesEpisode(ep)}
                  className="group flex items-center justify-between p-3 rounded-2xl bg-[#17233a]/50 hover:bg-[#17233a] border border-[#354966]/30 hover:border-[#008EFF]/40 transition-all cursor-pointer"
                >
                  <div className="flex items-center gap-3 min-w-0 pr-2">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        if (currentEpisode?.uuid === ep.uuid) togglePlayPause();
                        else playEpisode(ep);
                      }}
                      className="w-10 h-10 flex items-center justify-center rounded-full bg-[#008EFF] hover:bg-[#00A8FF] text-white shadow-sm shrink-0 active:scale-95 transition-transform"
                    >
                      {isCurPlaying ? <Pause className="w-4 h-4 fill-current" /> : <Play className="w-4 h-4 fill-current translate-x-0.5" />}
                    </button>

                    <div className="min-w-0">
                      <div className="flex items-center gap-1.5">
                        <QueueIndicator settings={settings.queueIndicator} isQueued={isQueued} />
                        <h4 className="text-sm font-semibold text-white truncate group-hover:text-[#00D2FF] transition-colors">
                          {ep.title}
                        </h4>
                      </div>
                      <div className="flex items-center gap-2 mt-1 text-xs">
                        <span className="font-bold text-[#008EFF]">
                          {formatDurationHuman(ep.durationSeconds || 0)}
                        </span>
                        {ep.isDownloaded && (
                          <span className="text-[10px] px-1.5 py-0.5 rounded bg-emerald-500/20 text-emerald-400 font-bold">
                            Downloaded
                          </span>
                        )}
                        {ep.playingStatus === 2 && (
                          <span className="text-[10px] px-1.5 py-0.5 rounded bg-white/10 text-white/60">
                            Played
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-1 shrink-0" onClick={(e) => e.stopPropagation()}>
                    <button
                      type="button"
                      onClick={() => {
                        if (isQueued) dequeue(ep.uuid);
                        else enqueue(ep.uuid);
                      }}
                      className={`w-9 h-9 flex items-center justify-center rounded-full transition-colors ${
                        isQueued
                          ? 'bg-[#008EFF]/20 text-[#00D2FF]'
                          : 'text-[#A1A1A6] hover:text-white hover:bg-white/10'
                      }`}
                      title={isQueued ? 'In Up Next' : 'Add to Up Next'}
                    >
                      {isQueued ? <Check className="w-4 h-4" /> : <ListPlus className="w-4 h-4" />}
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    );
  }

  // Main Podcasts Library Screen
  return (
    <div className="flex-1 overflow-y-auto pb-36 px-4 pt-4 max-w-2xl mx-auto w-full select-none">
      {/* Top Header */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <h1 className="text-2xl font-serif text-white">Podcasts</h1>
          <p className="text-xs text-[#b0bfd2]">
            {activePodcasts.length} subscribed show{activePodcasts.length === 1 ? '' : 's'}
          </p>
        </div>

        <div className="flex items-center gap-2">
          {/* Add custom feed */}
          <button
            type="button"
            onClick={() => setIsAddFeedOpen(true)}
            className="w-10 h-10 flex items-center justify-center rounded-xl bg-[#008EFF] text-white hover:bg-[#00A8FF] shadow-sm transition-colors"
            title="Add Podcast by RSS Feed URL"
          >
            <Plus className="w-5 h-5" />
          </button>

          {/* OPML Export */}
          <button
            type="button"
            onClick={handleExportOpml}
            className="w-10 h-10 flex items-center justify-center rounded-xl bg-white/5 hover:bg-white/10 text-white/80 hover:text-white transition-colors"
            title="Export OPML"
          >
            <FileDown className="w-5 h-5" />
          </button>

          {/* OPML Import */}
          <label className="w-10 h-10 flex items-center justify-center rounded-xl bg-white/5 hover:bg-white/10 text-white/80 hover:text-white transition-colors cursor-pointer" title="Import OPML">
            <FileUp className="w-5 h-5" />
            <input type="file" accept=".opml,.xml" onChange={handleImportOpml} className="hidden" />
          </label>

          {/* View Mode Toggle */}
          <button
            type="button"
            onClick={() => setViewMode(viewMode === 'grid' ? 'list' : 'grid')}
            className="w-10 h-10 flex items-center justify-center rounded-xl bg-white/5 hover:bg-white/10 text-white/80 hover:text-white transition-colors"
            title="Toggle Grid / List"
          >
            {viewMode === 'grid' ? <ListIcon className="w-5 h-5" /> : <LayoutGrid className="w-5 h-5" />}
          </button>
        </div>
      </div>

      {/* Search Input */}
      <div className="relative mb-5">
        <Search className="w-4 h-4 text-white/40 absolute left-3.5 top-1/2 -translate-y-1/2" />
        <input
          type="text"
          placeholder="Filter podcasts..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full pl-10 pr-4 py-2.5 rounded-2xl bg-[#17233a]/70 border border-[#354966]/40 text-sm text-white placeholder-white/40 focus:outline-none focus:border-[#008EFF]"
        />
      </div>

      {/* Podcasts Grid or List */}
      {filteredPodcasts.length === 0 ? (
        <div className="p-10 rounded-[24px] bg-[#17233a]/40 border border-[#354966]/30 text-center">
          <p className="text-sm text-[#b0bfd2]">No subscribed podcasts found.</p>
          <button
            type="button"
            onClick={() => setIsAddFeedOpen(true)}
            className="mt-3 px-4 py-2 rounded-full bg-[#008EFF] text-xs font-semibold text-white hover:bg-[#00A8FF]"
          >
            Add your first podcast feed
          </button>
        </div>
      ) : viewMode === 'grid' ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3.5">
          {filteredPodcasts.map((podcast) => (
            <div
              key={podcast.uuid}
              onClick={() => setSelectedPodcast(podcast)}
              className="group relative flex flex-col p-3 rounded-2xl bg-[#17233a]/60 hover:bg-[#17233a] border border-[#354966]/40 hover:border-[#008EFF]/50 transition-all cursor-pointer shadow-md"
            >
              <div className="relative aspect-square w-full rounded-xl overflow-hidden bg-[#26262A] border border-white/10 mb-2.5">
                {podcast.artworkUrl ? (
                  <img src={podcast.artworkUrl} alt={podcast.title} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" />
                ) : (
                  <div className="w-full h-full flex items-center justify-center font-bold text-white/30 text-xs">SHOW</div>
                )}
                {podcast.unreadCount > 0 && (
                  <span className="absolute top-2 right-2 px-2 py-0.5 bg-[#008EFF] text-white text-[10px] font-extrabold rounded-full shadow-md">
                    {podcast.unreadCount}
                  </span>
                )}
              </div>
              <h3 className="text-xs font-bold text-white truncate group-hover:text-[#00D2FF] transition-colors">
                {podcast.title}
              </h3>
              <p className="text-[11px] text-[#A1A1A6] truncate mt-0.5">
                {podcast.author || 'Podcast'}
              </p>
            </div>
          ))}
        </div>
      ) : (
        <div className="space-y-2">
          {filteredPodcasts.map((podcast) => (
            <div
              key={podcast.uuid}
              onClick={() => setSelectedPodcast(podcast)}
              className="group flex items-center justify-between p-3 rounded-2xl bg-[#17233a]/60 hover:bg-[#17233a] border border-[#354966]/40 transition-all cursor-pointer"
            >
              <div className="flex items-center gap-3 min-w-0 pr-3">
                <div className="w-12 h-12 rounded-xl overflow-hidden shrink-0 bg-[#26262A] border border-white/10">
                  {podcast.artworkUrl ? (
                    <img src={podcast.artworkUrl} alt={podcast.title} className="w-full h-full object-cover" />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center text-xs font-bold text-white/40">SHOW</div>
                  )}
                </div>
                <div className="min-w-0">
                  <h3 className="text-sm font-semibold text-white truncate group-hover:text-[#00D2FF]">
                    {podcast.title}
                  </h3>
                  <p className="text-xs text-[#A1A1A6] truncate">
                    {podcast.author || 'Podcast'}
                  </p>
                </div>
              </div>
              {podcast.unreadCount > 0 && (
                <span className="px-2 py-0.5 bg-[#008EFF] text-white text-xs font-bold rounded-full shrink-0">
                  {podcast.unreadCount}
                </span>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Add Feed URL Modal */}
      {isAddFeedOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="w-full max-w-md p-6 rounded-[24px] bg-[#17233a] border border-[#354966] text-white shadow-2xl">
            <h3 className="text-lg font-bold mb-2">Add Podcast by Feed URL</h3>
            <p className="text-xs text-[#b0bfd2] mb-4">
              Enter any standard RSS or Atom podcast feed URL.
            </p>
            <form onSubmit={handleAddFeed}>
              <input
                type="url"
                required
                placeholder="https://example.com/feed.xml"
                value={customFeedUrl}
                onChange={(e) => setCustomFeedUrl(e.target.value)}
                className="w-full px-4 py-3 rounded-xl bg-black/30 border border-white/20 text-sm text-white placeholder-white/30 focus:outline-none focus:border-[#008EFF] mb-3"
              />

              {feedError && (
                <p className="text-xs text-red-400 mb-3 bg-red-500/10 p-2.5 rounded-lg border border-red-500/20">
                  {feedError}
                </p>
              )}

              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setIsAddFeedOpen(false)}
                  className="px-4 py-2 rounded-xl text-xs font-semibold text-white/70 hover:text-white hover:bg-white/5"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={feedLoading}
                  className="px-5 py-2 rounded-xl bg-[#008EFF] hover:bg-[#00A8FF] text-white text-xs font-bold shadow-md transition-colors disabled:opacity-50"
                >
                  {feedLoading ? 'Fetching Feed...' : 'Subscribe'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
