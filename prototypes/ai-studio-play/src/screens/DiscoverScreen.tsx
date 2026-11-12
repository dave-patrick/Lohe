import React, { useState, useEffect } from 'react';
import { CatalogService } from '../services/catalog';
import { CatalogPodcast, Podcast, Episode } from '../types';
import { usePlayer } from '../context/PlayerContext';
import { Search, Compass, Plus, Check, Loader2, ExternalLink } from 'lucide-react';

const SUGGESTIONS = [
  'Design',
  'Technology',
  'Science',
  'History',
  'True Crime',
  'Philosophy',
  'Music',
  'Economics',
];

export const DiscoverScreen: React.FC = () => {
  const { podcasts, subscribePodcast, playEpisode } = usePlayer();

  const [term, setTerm] = useState('');
  const [results, setResults] = useState<CatalogPodcast[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedPodcast, setSelectedPodcast] = useState<CatalogPodcast | null>(null);
  const [feedData, setFeedData] = useState<{ podcast: Partial<Podcast>; episodes: Partial<Episode>[] } | null>(null);
  const [fetchingFeed, setFetchingFeed] = useState(false);
  const [feedError, setFeedError] = useState('');

  // Default discovery recommendations
  useEffect(() => {
    handleSearch('Podcast');
  }, []);

  const handleSearch = async (searchTerm: string) => {
    if (!searchTerm.trim()) return;
    setLoading(true);
    const res = await CatalogService.searchPodcasts(searchTerm);
    setResults(res);
    setLoading(false);
  };

  const handleSelectShow = async (show: CatalogPodcast) => {
    setSelectedPodcast(show);
    setFeedData(null);
    setFeedError('');
    setFetchingFeed(true);

    try {
      const data = await CatalogService.fetchFeed(show.feedUrl);
      if (data) setFeedData(data);
      else throw new Error("Could not load feed data.");
    } catch (err: any) {
      setFeedError(err.message || "Failed to load show episodes. Please try again.");
    } finally {
      setFetchingFeed(false);
    }
  };

  const isSubscribed = (feedUrl: string) =>
    podcasts.some((p) => p.isSubscribed && p.feedUrl === feedUrl);

  return (
    <div className="flex-1 overflow-y-auto pb-36 px-4 pt-4 max-w-2xl mx-auto w-full select-none">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <h1 className="text-2xl font-serif text-white">Discover</h1>
          <p className="text-xs text-[#b0bfd2]">Find podcasts from Apple's public catalog</p>
        </div>
        <div className="w-10 h-10 rounded-xl bg-[#008EFF]/15 border border-[#008EFF]/30 flex items-center justify-center text-[#008EFF]">
          <Compass className="w-5 h-5" />
        </div>
      </div>

      {/* Search Input */}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleSearch(term);
        }}
        className="relative mb-3"
      >
        <Search className="w-4 h-4 text-white/40 absolute left-3.5 top-1/2 -translate-y-1/2" />
        <input
          type="text"
          placeholder="Search by show title, host, topic..."
          value={term}
          onChange={(e) => setTerm(e.target.value)}
          className="w-full pl-10 pr-24 py-2.5 rounded-2xl bg-[#17233a]/80 border border-[#354966]/40 text-sm text-white placeholder-white/40 focus:outline-none focus:border-[#008EFF]"
        />
        <button
          type="submit"
          className="absolute right-2 top-1/2 -translate-y-1/2 px-3 py-1 rounded-xl bg-[#008EFF] text-xs font-bold text-white hover:bg-[#00A8FF]"
        >
          Search
        </button>
      </form>

      {/* Suggestion Chips */}
      <div className="flex items-center gap-1.5 overflow-x-auto pb-3 mb-4 scrollbar-none">
        {SUGGESTIONS.map((sug) => (
          <button
            key={sug}
            type="button"
            onClick={() => {
              setTerm(sug);
              handleSearch(sug);
            }}
            className="px-3 py-1 rounded-full text-xs font-semibold bg-white/5 hover:bg-white/10 text-[#b0bfd2] hover:text-white shrink-0 transition-colors"
          >
            {sug}
          </button>
        ))}
      </div>

      {/* Results */}
      {loading ? (
        <div className="py-20 flex flex-col items-center justify-center text-white/50">
          <Loader2 className="w-7 h-7 animate-spin text-[#008EFF] mb-2" />
          <p className="text-xs">Searching podcast catalog...</p>
        </div>
      ) : results.length === 0 ? (
        <div className="p-10 rounded-[24px] bg-[#17233a]/30 border border-white/5 text-center">
          <p className="text-xs text-[#b0bfd2]">No shows found. Try searching for a different topic.</p>
        </div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          {results.map((show) => {
            const subscribed = isSubscribed(show.feedUrl);

            return (
              <div
                key={show.feedUrl}
                onClick={() => handleSelectShow(show)}
                className="group relative flex flex-col p-3 rounded-2xl bg-[#17233a]/60 hover:bg-[#17233a] border border-[#354966]/40 hover:border-[#008EFF]/50 shadow-md transition-all cursor-pointer"
              >
                <div className="relative aspect-square w-full rounded-xl overflow-hidden bg-[#26262A] border border-white/10 mb-2.5">
                  {show.artworkUrl ? (
                    <img src={show.artworkUrl} alt={show.title} className="w-full h-full object-cover group-hover:scale-105 transition-transform" />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center font-bold text-white/30 text-xs">SHOW</div>
                  )}
                  {subscribed && (
                    <div className="absolute top-2 right-2 p-1 bg-[#008EFF] text-white rounded-full shadow-md">
                      <Check className="w-3 h-3 stroke-[3]" />
                    </div>
                  )}
                </div>

                <h4 className="text-xs font-bold text-white truncate group-hover:text-[#00D2FF]">
                  {show.title}
                </h4>
                <p className="text-[11px] text-[#A1A1A6] truncate mt-0.5">
                  {show.author}
                </p>
                {show.genre && (
                  <span className="text-[10px] text-[#008EFF] mt-1 font-semibold truncate">
                    {show.genre}
                  </span>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Show Preview Modal */}
      {selectedPodcast && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="w-full max-w-lg max-h-[85vh] flex flex-col rounded-[24px] bg-[#17233a] border border-[#354966] text-white shadow-2xl overflow-hidden">
            {/* Modal Header */}
            <div className="p-5 border-b border-white/10 flex gap-4 items-start justify-between bg-[#121824]/90">
              <div className="flex gap-3.5 min-w-0">
                <img
                  src={selectedPodcast.artworkUrl}
                  alt={selectedPodcast.title}
                  className="w-16 h-16 rounded-xl object-cover border border-white/10 shrink-0"
                />
                <div className="min-w-0">
                  <h3 className="text-base font-bold text-white leading-tight">
                    {selectedPodcast.title}
                  </h3>
                  <p className="text-xs text-[#008EFF] font-semibold mt-0.5">
                    {selectedPodcast.author}
                  </p>
                  <p className="text-[11px] text-[#b0bfd2] mt-1">
                    {selectedPodcast.genre} • {selectedPodcast.episodeCount || 'Latest'} episodes
                  </p>
                </div>
              </div>

              <button
                type="button"
                onClick={() => setSelectedPodcast(null)}
                className="w-8 h-8 flex items-center justify-center rounded-full bg-white/5 hover:bg-white/10 text-white/70 hover:text-white"
              >
                ✕
              </button>
            </div>

            {/* Modal Body: Episodes Preview */}
            <div className="flex-1 overflow-y-auto p-5">
              {fetchingFeed ? (
                <div className="py-12 flex flex-col items-center justify-center text-white/60">
                  <Loader2 className="w-6 h-6 animate-spin text-[#008EFF] mb-2" />
                  <p className="text-xs">Fetching episodes directly from feed...</p>
                </div>
              ) : feedError ? (
                <div className="p-4 rounded-xl bg-red-500/10 border border-red-500/20 text-xs text-red-400">
                  {feedError}
                </div>
              ) : feedData ? (
                <div className="space-y-3">
                  <div className="flex items-center justify-between pb-2 border-b border-white/10">
                    <span className="text-xs font-bold text-white">Latest Episodes</span>
                    <button
                      type="button"
                      onClick={() => {
                        if (feedData.podcast && feedData.episodes) {
                          subscribePodcast(feedData.podcast as Podcast, feedData.episodes as Episode[]);
                          setSelectedPodcast(null);
                        }
                      }}
                      className="px-4 py-1.5 rounded-full bg-[#008EFF] hover:bg-[#00A8FF] text-white text-xs font-bold shadow-md flex items-center gap-1.5"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      <span>{isSubscribed(selectedPodcast.feedUrl) ? 'Subscribed' : 'Subscribe'}</span>
                    </button>
                  </div>

                  <div className="space-y-2">
                    {feedData.episodes.slice(0, 15).map((ep) => (
                      <div
                        key={ep.uuid}
                        className="p-3 rounded-xl bg-white/5 hover:bg-white/10 transition-colors flex items-center justify-between gap-3"
                      >
                        <div className="min-w-0">
                          <h5 className="text-xs font-semibold text-white truncate">
                            {ep.title}
                          </h5>
                          <span className="text-[11px] text-[#A1A1A6]">
                            {ep.durationSeconds ? `${Math.floor(ep.durationSeconds / 60)} min` : 'Episode'}
                          </span>
                        </div>
                        <button
                          type="button"
                          onClick={() => {
                            if (ep.audioUrl) {
                              playEpisode(ep as Episode);
                              setSelectedPodcast(null);
                            }
                          }}
                          className="px-3 py-1 rounded-full bg-white/10 hover:bg-[#008EFF] text-white text-xs font-semibold shrink-0 transition-colors"
                        >
                          Play
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              ) : null}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
