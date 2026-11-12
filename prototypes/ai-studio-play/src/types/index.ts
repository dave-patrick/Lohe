import type { PlayPurchaseReceipt } from '../services/entitlements';

export interface Podcast {
  uuid: string;
  title: string;
  author?: string;
  description?: string;
  artworkUrl?: string;
  feedUrl: string;
  lastEpisodePublished?: number;
  subscribedAt: number;
  unreadCount: number;
  autoDownload: boolean;
  autoAddNewEpisodesToUpNext: boolean;
  autoAddNewEpisodesAtTop: boolean;
  isSubscribed: boolean;
  feedEtag?: string;
  feedLastModified?: string;
}

export type PlayingStatus = 0 | 1 | 2; // 0 = unplayed, 1 = in progress, 2 = played

export interface Episode {
  uuid: string;
  podcastUuid: string;
  podcastTitle: string;
  title: string;
  audioUrl: string;
  published: number; // epoch ms
  durationSeconds?: number;
  sizeBytes?: number;
  fileType?: string;
  author?: string;
  artworkUrl?: string;
  description?: string;
  playingStatus: PlayingStatus;
  playedAt?: number | null;
  playedUpToSeconds: number;
  listenedSeconds: number;
  starred: boolean;
  isArchived: boolean;
  archivedAt?: number | null;
  queuePosition: number | null; // null if not queued
  lastPlayedAt?: number | null;
  isDownloaded?: boolean;
}

export interface Playlist {
  id: string;
  title: string;
  isSmart: boolean;
  smartType?: 'played' | 'in_progress' | 'unplayed' | 'downloaded' | 'new_releases' | 'not_downloaded';
  isPinned?: boolean;
  episodeIds?: string[];
  assignedPodcastUuids?: string[];
  sortBy?: 'manual' | 'date' | 'length' | 'title';
  sortDirection?: 'asc' | 'desc';
  folder?: string;
  autoDownload?: boolean;
  artworkUrls?: string[];
  assignedPodcastCount?: number;
  totalEpisodeCount?: number;
  durationString?: string;
  unplayedCount?: number;
}

export interface QueueIndicatorSettings {
  enabled: boolean;
  style: 'dot' | 'ring' | 'icon' | 'pill' | 'diamond' | 'glow';
  color: string;
  size: number;
  customHex?: string;
}

export interface PlaybackSettings {
  appearance: 'dark' | 'light';
  defaultPlaybackSpeed: number;
  skipBackSeconds: number;
  skipForwardSeconds: number;
  skipSilenceByDefault: boolean;
  volumeBoostByDefault: boolean;
  archiveDelayHours: number; // 0 (immediately), 24, 48, 168 (7 days), -1 (never)
  queueIndicator: QueueIndicatorSettings;
  floatingPlayerEnabled: boolean;
  floatingSizeDp?: number;
  floatingTransparencyPct?: number;
  markPlayedWhenFinished?: boolean;
  autoRewindSeconds?: number;
  resumeOnConnect?: boolean;
  defaultSleepTimerMinutes?: number;
  batteryOptimizationStatus?: 'unrestricted' | 'optimized' | 'restricted';
  keepStarredEpisodes?: boolean;
  deleteAudioOnArchive?: boolean;
  archiveOnManualPlayed?: boolean;
  includeArchivedInSignal?: boolean;
  autoDownloadQueueCount?: number;
  downloadWifiOnly?: boolean;
  autoDownloadNewEpisodes?: boolean;
  storageLocation?: 'internal' | 'sdcard';
  appEdition?: 'foss' | 'play';
  appFlavor?: 'play_free' | 'play_paid' | 'foss';
  purchaseReceipt?: PlayPurchaseReceipt | null;
  // Legacy fields kept for backward compatibility with existing storage
  isProUnlocked?: boolean;
  proPurchaseDate?: number;
  proOrderId?: string;
  proPlanType?: 'annual';
}

export interface CatalogPodcast {
  title: string;
  author: string;
  feedUrl: string;
  artworkUrl?: string;
  genre?: string;
  description?: string;
  episodeCount?: number;
}
