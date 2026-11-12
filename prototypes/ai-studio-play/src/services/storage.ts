import { Podcast, Episode, Playlist, PlaybackSettings } from '../types';

const STORAGE_KEYS = {
  PODCASTS: 'lohe_podcasts_v1',
  EPISODES: 'lohe_episodes_v1',
  PLAYLISTS: 'lohe_playlists_v1',
  SETTINGS: 'lohe_settings_v1',
  LAST_PLAYING_ID: 'lohe_last_playing_id',
};

export const DEFAULT_SETTINGS: PlaybackSettings = {
  appearance: 'dark',
  defaultPlaybackSpeed: 1.0,
  skipBackSeconds: 15,
  skipForwardSeconds: 30,
  skipSilenceByDefault: false,
  volumeBoostByDefault: false,
  archiveDelayHours: 24,
  queueIndicator: {
    enabled: true,
    style: 'dot',
    color: 'blue',
    size: 8,
  },
  floatingPlayerEnabled: false,
  floatingSizeDp: 56,
  floatingTransparencyPct: 25,
  markPlayedWhenFinished: true,
  autoRewindSeconds: 3,
  resumeOnConnect: true,
  defaultSleepTimerMinutes: 0,
  batteryOptimizationStatus: 'unrestricted',
  keepStarredEpisodes: true,
  deleteAudioOnArchive: true,
  archiveOnManualPlayed: true,
  includeArchivedInSignal: true,
  autoDownloadQueueCount: 5,
  downloadWifiOnly: true,
  autoDownloadNewEpisodes: false,
  storageLocation: 'internal',
  appEdition: 'play',
  appFlavor: 'play_free',
  purchaseReceipt: null,
  isProUnlocked: false,
};

export const DEFAULT_PLAYLISTS: Playlist[] = [
  {
    id: 'pl_everything',
    title: 'Everything',
    isSmart: false,
    isPinned: true,
    totalEpisodeCount: 395,
    durationString: '15d 15h 58m',
    unplayedCount: 395,
    assignedPodcastCount: 97,
    artworkUrls: [
      'https://images.squarespace-cdn.com/content/v1/5a0c9c1b9f8dce48ba6ec39e/1585860492984-Z555QO115Z36B599T80S/99pi-avatar.png',
      'https://songexploder.net/wp-content/uploads/2021/04/SongExploder_Podcast_Avatar_Square-1.jpg',
      'https://media.wnyc.org/i/1400/1400/l/80/1/Radiolab_Square_Logo.png',
      'https://images.squarespace-cdn.com/content/v1/5a0c9c1b9f8dce48ba6ec39e/1585860492984-Z555QO115Z36B599T80S/99pi-avatar.png',
    ],
  },
  {
    id: 'pl_sports',
    title: 'Sports',
    isSmart: false,
    isPinned: true,
    folder: 'Football',
    autoDownload: true,
    totalEpisodeCount: 11,
    durationString: '7h 55m',
    unplayedCount: 11,
    assignedPodcastCount: 4,
    artworkUrls: [
      'https://images.squarespace-cdn.com/content/v1/5a0c9c1b9f8dce48ba6ec39e/1585860492984-Z555QO115Z36B599T80S/99pi-avatar.png',
      'https://songexploder.net/wp-content/uploads/2021/04/SongExploder_Podcast_Avatar_Square-1.jpg',
      'https://media.wnyc.org/i/1400/1400/l/80/1/Radiolab_Square_Logo.png',
      'https://images.squarespace-cdn.com/content/v1/5a0c9c1b9f8dce48ba6ec39e/1585860492984-Z555QO115Z36B599T80S/99pi-avatar.png',
    ],
  },
  {
    id: 'pl_productivity',
    title: 'Productivity',
    isSmart: false,
    totalEpisodeCount: 24,
    durationString: '18h 24m',
    unplayedCount: 24,
    assignedPodcastCount: 6,
    artworkUrls: [
      'https://images.squarespace-cdn.com/content/v1/5a0c9c1b9f8dce48ba6ec39e/1585860492984-Z555QO115Z36B599T80S/99pi-avatar.png',
    ],
  },
  {
    id: 'pl_star_wars',
    title: 'Star Wars',
    isSmart: false,
    totalEpisodeCount: 25,
    durationString: '1d 46m',
    unplayedCount: 25,
    assignedPodcastCount: 5,
    artworkUrls: [
      'https://songexploder.net/wp-content/uploads/2021/04/SongExploder_Podcast_Avatar_Square-1.jpg',
      'https://media.wnyc.org/i/1400/1400/l/80/1/Radiolab_Square_Logo.png',
      'https://images.squarespace-cdn.com/content/v1/5a0c9c1b9f8dce48ba6ec39e/1585860492984-Z555QO115Z36B599T80S/99pi-avatar.png',
      'https://songexploder.net/wp-content/uploads/2021/04/SongExploder_Podcast_Avatar_Square-1.jpg',
    ],
  },
  {
    id: 'pl_maker',
    title: 'Maker',
    isSmart: false,
    totalEpisodeCount: 23,
    durationString: '1d 1h 2m',
    unplayedCount: 23,
    assignedPodcastCount: 4,
    artworkUrls: [
      'https://images.squarespace-cdn.com/content/v1/5a0c9c1b9f8dce48ba6ec39e/1585860492984-Z555QO115Z36B599T80S/99pi-avatar.png',
    ],
  },
  {
    id: 'pl_short',
    title: 'Short',
    isSmart: false,
    totalEpisodeCount: 2,
    durationString: '4m',
    unplayedCount: 2,
    assignedPodcastCount: 2,
    artworkUrls: [
      'https://media.wnyc.org/i/1400/1400/l/80/1/Radiolab_Square_Logo.png',
    ],
  },
  {
    id: 'pl_tech',
    title: 'Tech',
    isSmart: false,
    totalEpisodeCount: 32,
    durationString: '1d 12h',
    unplayedCount: 32,
    assignedPodcastCount: 8,
    artworkUrls: [
      'https://songexploder.net/wp-content/uploads/2021/04/SongExploder_Podcast_Avatar_Square-1.jpg',
    ],
  },
  {
    id: 'pl_ai',
    title: 'AI Daily Digest',
    folder: 'AI',
    isSmart: false,
    totalEpisodeCount: 14,
    durationString: '3h 12m',
    unplayedCount: 8,
    assignedPodcastCount: 3,
  },
  { id: 'smart_in_progress', title: 'In Progress', isSmart: true, smartType: 'in_progress', isPinned: false },
  { id: 'smart_unplayed', title: 'Unplayed', isSmart: true, smartType: 'unplayed', isPinned: false },
  { id: 'smart_new_releases', title: 'New Releases (7d)', isSmart: true, smartType: 'new_releases', isPinned: false },
  { id: 'smart_downloaded', title: 'Downloaded', isSmart: true, smartType: 'downloaded', isPinned: false },
  { id: 'smart_played', title: 'Played', isSmart: true, smartType: 'played', isPinned: false },
];

const INITIAL_PODCASTS: Podcast[] = [
  {
    uuid: 'pod_99pi',
    title: '99% Invisible',
    author: 'Roman Mars',
    description: 'Design is everywhere in our lives, perhaps most importantly in the places where we\'ve just stopped noticing. 99% Invisible is a weekly exploration of the process and power of design and architecture.',
    artworkUrl: 'https://images.squarespace-cdn.com/content/v1/5a0c9c1b9f8dce48ba6ec39e/1585860492984-Z555QO115Z36B599T80S/99pi-avatar.png',
    feedUrl: 'https://feeds.simplecast.com/BqbsxVfO',
    subscribedAt: Date.now() - 86400000 * 14,
    lastEpisodePublished: Date.now() - 3600000 * 18,
    unreadCount: 2,
    autoDownload: true,
    autoAddNewEpisodesToUpNext: true,
    autoAddNewEpisodesAtTop: false,
    isSubscribed: true,
  },
  {
    uuid: 'pod_song_exploder',
    title: 'Song Exploder',
    author: 'Hrishikesh Hirway',
    description: 'Song Exploder is a podcast where musicians take apart their songs, and piece by piece, tell the story of how they were made.',
    artworkUrl: 'https://songexploder.net/wp-content/uploads/2021/04/SongExploder_Podcast_Avatar_Square-1.jpg',
    feedUrl: 'https://feed.songexploder.net/songexploder',
    subscribedAt: Date.now() - 86400000 * 20,
    lastEpisodePublished: Date.now() - 86400000 * 2,
    unreadCount: 1,
    autoDownload: false,
    autoAddNewEpisodesToUpNext: false,
    autoAddNewEpisodesAtTop: false,
    isSubscribed: true,
  },
  {
    uuid: 'pod_radiolab',
    title: 'Radiolab',
    author: 'WNYC Studios',
    description: 'Investigating a strange world. Radiolab is on a curiosity bender, exploring science, philosophy, and the human experience.',
    artworkUrl: 'https://media.wnyc.org/i/1400/1400/l/80/1/Radiolab_Square_Logo.png',
    feedUrl: 'https://feeds.wnyc.org/radiolab',
    subscribedAt: Date.now() - 86400000 * 30,
    lastEpisodePublished: Date.now() - 86400000 * 4,
    unreadCount: 2,
    autoDownload: false,
    autoAddNewEpisodesToUpNext: true,
    autoAddNewEpisodesAtTop: true,
    isSubscribed: true,
  },
];

const INITIAL_EPISODES: Episode[] = [
  {
    uuid: 'ep_ai_1',
    podcastUuid: 'pod_ai_news',
    podcastTitle: 'Daily AI News',
    title: 'Daily AI News Digest: Gartner Predicts 40% AI Agent Workflows',
    audioUrl: 'https://traffic.libsyn.com/secure/atpfm/atp577.mp3',
    published: Date.now() - 3600000 * 2,
    durationSeconds: 360,
    sizeBytes: 8500000,
    artworkUrl: 'https://songexploder.net/wp-content/uploads/2021/04/SongExploder_Podcast_Avatar_Square-1.jpg',
    description: '<p>Gartner reports accelerating adoption of autonomous multi-agent developer workflows across cloud and enterprise platforms.</p>',
    playingStatus: 0,
    playedUpToSeconds: 0,
    listenedSeconds: 0,
    starred: true,
    isArchived: false,
    queuePosition: 1,
    isDownloaded: true,
  },
  {
    uuid: 'ep_ai_2',
    podcastUuid: 'pod_ai_news',
    podcastTitle: 'Daily AI News',
    title: 'Daily AI News Digest: Gartner Pre-briefing on Enterprise AI',
    audioUrl: 'https://traffic.libsyn.com/secure/atpfm/atp576.mp3',
    published: Date.now() - 3600000 * 4,
    durationSeconds: 120,
    sizeBytes: 3200000,
    artworkUrl: 'https://songexploder.net/wp-content/uploads/2021/04/SongExploder_Podcast_Avatar_Square-1.jpg',
    description: '<p>Key takeaways from enterprise infrastructure briefings on small reasoning models and cost-effective serving.</p>',
    playingStatus: 0,
    playedUpToSeconds: 0,
    listenedSeconds: 0,
    starred: false,
    isArchived: false,
    queuePosition: 2,
    isDownloaded: false,
  },
  {
    uuid: 'ep_bandsplain',
    podcastUuid: 'pod_bandsplain',
    podcastTitle: 'Bandsplain',
    title: 'Best New Music of Ireland With Lisa O\'Neill',
    audioUrl: 'https://traffic.libsyn.com/secure/songexploder/SongExploder194FleetFoxes.mp3',
    published: Date.now() - 3600000 * 6,
    durationSeconds: 4500,
    sizeBytes: 72000000,
    artworkUrl: 'https://media.wnyc.org/i/1400/1400/l/80/1/Radiolab_Square_Logo.png',
    description: '<p>Exploring contemporary folk composition, storytelling traditions, and innovative instrumentation across Irish songwriting.</p>',
    playingStatus: 0,
    playedUpToSeconds: 0,
    listenedSeconds: 0,
    starred: true,
    isArchived: false,
    queuePosition: null,
    isDownloaded: false,
  },
  {
    uuid: 'ep_stuff_manatees',
    podcastUuid: 'pod_sysk',
    podcastTitle: 'Stuff You Should Know',
    title: 'The World Needs More Manatees',
    audioUrl: 'https://traffic.libsyn.com/secure/atpfm/atp577.mp3',
    published: Date.now() - 3600000 * 8,
    durationSeconds: 2640,
    sizeBytes: 42000000,
    artworkUrl: 'https://images.squarespace-cdn.com/content/v1/5a0c9c1b9f8dce48ba6ec39e/1585860492984-Z555QO115Z36B599T80S/99pi-avatar.png',
    description: '<p>Everything you ever wanted to know about gentle sea cows, warm spring habitats, and marine conservation efforts.</p>',
    playingStatus: 0,
    playedUpToSeconds: 0,
    listenedSeconds: 0,
    starred: false,
    isArchived: false,
    queuePosition: null,
    isDownloaded: true,
  },
  {
    uuid: 'ep_49ers_dallas',
    podcastUuid: 'pod_lockedon49ers',
    podcastTitle: 'Locked On 49ers - Daily Podcast',
    title: 'DALLAS SHOOTOUT: San Francisco 49ers Gameplan',
    audioUrl: 'https://traffic.libsyn.com/secure/atpfm/atp576.mp3',
    published: Date.now() - 3600000 * 10,
    durationSeconds: 1620,
    sizeBytes: 25000000,
    artworkUrl: 'https://media.wnyc.org/i/1400/1400/l/80/1/Radiolab_Square_Logo.png',
    description: '<p>Defensive matchups, red zone efficiency, and key injury updates heading into Sunday\'s conference clash.</p>',
    playingStatus: 0,
    playedUpToSeconds: 0,
    listenedSeconds: 0,
    starred: true,
    isArchived: false,
    queuePosition: 3,
    isDownloaded: false,
  },
  {
    uuid: 'ep_starfleet_whales',
    podcastUuid: 'pod_starfleet',
    podcastTitle: 'We Are Starfleet: A Star Trek Podcast',
    title: 'THERE BE WHALES HERE!',
    audioUrl: 'https://traffic.libsyn.com/secure/songexploder/SongExploder194FleetFoxes.mp3',
    published: Date.now() - 3600000 * 12,
    durationSeconds: 5520,
    sizeBytes: 88000000,
    artworkUrl: 'https://songexploder.net/wp-content/uploads/2021/04/SongExploder_Podcast_Avatar_Square-1.jpg',
    description: '<p>Deep retrospective commentary on Star Trek IV: The Voyage Home, humpback whale acoustics, and 1980s San Francisco.</p>',
    playingStatus: 0,
    playedUpToSeconds: 0,
    listenedSeconds: 0,
    starred: false,
    isArchived: false,
    queuePosition: null,
    isDownloaded: false,
  },
  {
    uuid: 'ep_ai_news_today',
    podcastUuid: 'pod_ai0',
    podcastTitle: 'ai0.news — Daily AI News',
    title: 'AI News — November 12, 2026: Reasoning Models Advance',
    audioUrl: 'https://traffic.libsyn.com/secure/atpfm/atp577.mp3',
    published: Date.now() - 3600000 * 14,
    durationSeconds: 180,
    sizeBytes: 4000000,
    artworkUrl: 'https://images.squarespace-cdn.com/content/v1/5a0c9c1b9f8dce48ba6ec39e/1585860492984-Z555QO115Z36B599T80S/99pi-avatar.png',
    description: '<p>Rapid round-up of multimodal reasoning breakthroughs, inference benchmarking, and open weights releases.</p>',
    playingStatus: 0,
    playedUpToSeconds: 0,
    listenedSeconds: 0,
    starred: false,
    isArchived: false,
    queuePosition: 4,
    isDownloaded: true,
  },
  {
    uuid: 'ep_99pi_580',
    podcastUuid: 'pod_99pi',
    podcastTitle: '99% Invisible',
    title: '580 - The Great Postal Strike',
    audioUrl: 'https://traffic.libsyn.com/secure/atpfm/atp577.mp3', // reliable working audio stream
    published: Date.now() - 3600000 * 18,
    durationSeconds: 2160,
    sizeBytes: 34500000,
    artworkUrl: 'https://images.squarespace-cdn.com/content/v1/5a0c9c1b9f8dce48ba6ec39e/1585860492984-Z555QO115Z36B599T80S/99pi-avatar.png',
    description: '<p>In 1970, over 200,000 postal workers walked off the job in an illegal wildcat strike that brought commerce and communications to a standstill, ultimately transforming the postal system forever.</p>',
    playingStatus: 1, // in progress!
    playedUpToSeconds: 420,
    listenedSeconds: 420,
    starred: true,
    isArchived: false,
    queuePosition: 1, // Up next
    lastPlayedAt: Date.now() - 3600000 * 2,
    isDownloaded: true,
  },
  {
    uuid: 'ep_song_exploder_260',
    podcastUuid: 'pod_song_exploder',
    podcastTitle: 'Song Exploder',
    title: 'Fleet Foxes - Can I Believe You',
    audioUrl: 'https://traffic.libsyn.com/secure/songexploder/SongExploder194FleetFoxes.mp3',
    published: Date.now() - 86400000 * 2,
    durationSeconds: 1240,
    sizeBytes: 19800000,
    artworkUrl: 'https://songexploder.net/wp-content/uploads/2021/04/SongExploder_Podcast_Avatar_Square-1.jpg',
    description: '<p>Robin Pecknold broke down how he constructed the harmonies and layered guitars, drawing from hundreds of vocal memos recorded over years.</p>',
    playingStatus: 0,
    playedUpToSeconds: 0,
    listenedSeconds: 0,
    starred: false,
    isArchived: false,
    queuePosition: 2,
    isDownloaded: false,
  },
  {
    uuid: 'ep_99pi_579',
    podcastUuid: 'pod_99pi',
    podcastTitle: '99% Invisible',
    title: '579 - Maps of the Invisible',
    audioUrl: 'https://traffic.libsyn.com/secure/atpfm/atp576.mp3',
    published: Date.now() - 86400000 * 8,
    durationSeconds: 1980,
    sizeBytes: 31600000,
    artworkUrl: 'https://images.squarespace-cdn.com/content/v1/5a0c9c1b9f8dce48ba6ec39e/1585860492984-Z555QO115Z36B599T80S/99pi-avatar.png',
    description: '<p>How cartographers chart the electromagnetic spectrum, subterranean conduits, and the invisible infrastructures beneath our feet.</p>',
    playingStatus: 0,
    playedUpToSeconds: 0,
    listenedSeconds: 0,
    starred: true,
    isArchived: false,
    queuePosition: 3,
    isDownloaded: false,
  },
  {
    uuid: 'ep_radiolab_cell',
    podcastUuid: 'pod_radiolab',
    podcastTitle: 'Radiolab',
    title: 'The Secret Architecture of Cells',
    audioUrl: 'https://chrt.fm/track/53A61E/pdst.fm/e/wnyc.streamguys1.com/radiolab/radiolab091523.mp3',
    published: Date.now() - 86400000 * 4,
    durationSeconds: 3120,
    sizeBytes: 49000000,
    artworkUrl: 'https://media.wnyc.org/i/1400/1400/l/80/1/Radiolab_Square_Logo.png',
    description: '<p>Inside every cell in your body, molecular engines spin thousands of times a minute. We dive deep into the micro-machinery that powers life.</p>',
    playingStatus: 0,
    playedUpToSeconds: 0,
    listenedSeconds: 0,
    starred: false,
    isArchived: false,
    queuePosition: null,
    isDownloaded: true,
  },
  {
    uuid: 'ep_radiolab_archive',
    podcastUuid: 'pod_radiolab',
    podcastTitle: 'Radiolab',
    title: 'Falling',
    audioUrl: 'https://chrt.fm/track/53A61E/pdst.fm/e/wnyc.streamguys1.com/radiolab/radiolab091523.mp3',
    published: Date.now() - 86400000 * 40,
    durationSeconds: 3450,
    sizeBytes: 52000000,
    artworkUrl: 'https://media.wnyc.org/i/1400/1400/l/80/1/Radiolab_Square_Logo.png',
    description: '<p>Stories of the terrifying and weightless experience of falling through air, space, and memory.</p>',
    playingStatus: 2, // played!
    playedAt: Date.now() - 86400000 * 5,
    playedUpToSeconds: 3450,
    listenedSeconds: 3450,
    starred: false,
    isArchived: false,
    queuePosition: null,
    lastPlayedAt: Date.now() - 86400000 * 5,
    isDownloaded: false,
  },
];

export class StorageService {
  static getSettings(): PlaybackSettings {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.SETTINGS);
      if (data) return { ...DEFAULT_SETTINGS, ...JSON.parse(data) };
    } catch {
      // fallback
    }
    return DEFAULT_SETTINGS;
  }

  static saveSettings(settings: PlaybackSettings): void {
    localStorage.setItem(STORAGE_KEYS.SETTINGS, JSON.stringify(settings));
  }

  static getPodcasts(): Podcast[] {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.PODCASTS);
      if (data) return JSON.parse(data);
    } catch {
      // fallback
    }
    StorageService.savePodcasts(INITIAL_PODCASTS);
    return INITIAL_PODCASTS;
  }

  static savePodcasts(podcasts: Podcast[]): void {
    localStorage.setItem(STORAGE_KEYS.PODCASTS, JSON.stringify(podcasts));
  }

  static getEpisodes(): Episode[] {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.EPISODES);
      if (data) return JSON.parse(data);
    } catch {
      // fallback
    }
    StorageService.saveEpisodes(INITIAL_EPISODES);
    return INITIAL_EPISODES;
  }

  static saveEpisodes(episodes: Episode[]): void {
    localStorage.setItem(STORAGE_KEYS.EPISODES, JSON.stringify(episodes));
  }

  static getPlaylists(): Playlist[] {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.PLAYLISTS);
      if (data) return JSON.parse(data);
    } catch {
      // fallback
    }
    StorageService.savePlaylists(DEFAULT_PLAYLISTS);
    return DEFAULT_PLAYLISTS;
  }

  static savePlaylists(playlists: Playlist[]): void {
    localStorage.setItem(STORAGE_KEYS.PLAYLISTS, JSON.stringify(playlists));
  }
}
