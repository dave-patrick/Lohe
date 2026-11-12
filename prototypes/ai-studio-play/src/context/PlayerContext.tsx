import React, { createContext, useContext, useEffect, useRef, useState, useCallback } from 'react';
import { Episode, Podcast, Playlist, PlaybackSettings } from '../types';
import { StorageService, DEFAULT_SETTINGS } from '../services/storage';

import {
  canAccessFeature,
  verifyPurchaseReceipt,
  verifySubscriptionEntitlement,
  createVerifiedSubscriptionReceipt,
  createVerifiedReceipt,
  FeatureKey,
  PlayPurchaseReceipt,
  SubscriptionStatus,
} from '../services/entitlements';

interface PlayerContextType {
  currentEpisode: Episode | null;
  isPlaying: boolean;
  currentTime: number;
  duration: number;
  playbackSpeed: number;
  volumeBoost: boolean;
  skipSilence: boolean;
  sleepTimerSecondsRemaining: number | null;
  sleepTimerMode: string | null;

  // Audio actions
  playEpisode: (episode: Episode) => void;
  togglePlayPause: () => void;
  pause: () => void;
  seekTo: (seconds: number) => void;
  skipBackward: (seconds?: number) => void;
  skipForward: (seconds?: number) => void;
  setSpeed: (speed: number) => void;
  toggleVolumeBoost: () => void;
  toggleSkipSilence: () => void;
  setSleepTimer: (minutes: number | 'end_of_episode' | null) => void;

  // Queue actions
  queue: Episode[];
  enqueue: (episodeId: string, atTop?: boolean) => void;
  dequeue: (episodeId: string) => void;
  enqueueAll: (episodeIds: string[], atTop?: boolean) => void;
  dequeueAll: (episodeIds: string[]) => void;
  reorderQueue: (fromIndex: number, toIndex: number) => void;
  moveQueueToTop: (episodeId: string) => void;
  moveQueueToEnd: (episodeId: string) => void;

  // Library & Data state
  podcasts: Podcast[];
  episodes: Episode[];
  playlists: Playlist[];
  settings: PlaybackSettings;
  updateSettings: (newSettings: Partial<PlaybackSettings>) => void;
  subscribePodcast: (podcast: Podcast, initialEpisodes: Episode[]) => void;
  unsubscribePodcast: (podcastUuid: string) => void;
  archiveEpisode: (episodeId: string) => void;
  unarchiveEpisode: (episodeId: string) => void;
  toggleStar: (episodeId: string) => void;
  toggleDownload: (episodeId: string) => void;
  markPlayed: (episodeId: string, played: boolean) => void;
  createPlaylist: (title: string) => void;
  deletePlaylist: (playlistId: string) => void;
  updatePlaylist: (playlist: Playlist) => void;

  // UI Full Player Modal state
  isPlayerOpen: boolean;
  setIsPlayerOpen: (open: boolean) => void;
  activeNotesEpisode: Episode | null;
  setActiveNotesEpisode: (episode: Episode | null) => void;

  // Central Entitlement & In-App Paid Upgrade Layer
  isPro: boolean;
  purchaseReceipt: PlayPurchaseReceipt | null;
  subscriptionStatus: SubscriptionStatus | 'NONE';
  subscriptionExpiryTime: number | null;
  isGracePeriod: boolean;
  isAutoRenewing: boolean;
  canAccess: (feature: FeatureKey) => boolean;
  isUpgradeModalOpen: boolean;
  upgradeFeaturePrompt: string | null;
  openUpgradeModal: (featurePrompt?: string) => void;
  closeUpgradeModal: () => void;
  unlockPro: (status?: SubscriptionStatus) => void;
  cancelSubscription: () => void;
  simulateSubscriptionStatus: (status: SubscriptionStatus) => void;
  acknowledgePurchase: () => void;
  pauseSubscription: () => void;
  revokeSubscription: () => void;
  setAccountHold: () => void;
  recoverInterruptedPurchases: () => Promise<boolean>;
  restorePurchases: () => Promise<boolean>;
  resetPro: () => void;
}

const PlayerContext = createContext<PlayerContextType | null>(null);

export const PlayerProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [settings, setSettings] = useState<PlaybackSettings>(() => StorageService.getSettings());
  const [podcasts, setPodcasts] = useState<Podcast[]>(() => StorageService.getPodcasts());
  const [episodes, setEpisodes] = useState<Episode[]>(() => StorageService.getEpisodes());
  const [playlists, setPlaylists] = useState<Playlist[]>(() => StorageService.getPlaylists());

  const [currentEpisode, setCurrentEpisode] = useState<Episode | null>(null);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [duration, setDuration] = useState<number>(0);
  const [playbackSpeed, setPlaybackSpeed] = useState<number>(settings.defaultPlaybackSpeed || 1.0);
  const [volumeBoost, setVolumeBoost] = useState<boolean>(settings.volumeBoostByDefault || false);
  const [skipSilence, setSkipSilence] = useState<boolean>(settings.skipSilenceByDefault || false);

  const [sleepTimerSecondsRemaining, setSleepTimerSecondsRemaining] = useState<number | null>(null);
  const [sleepTimerMode, setSleepTimerMode] = useState<string | null>(null);

  const [isPlayerOpen, setIsPlayerOpen] = useState<boolean>(false);
  const [activeNotesEpisode, setActiveNotesEpisode] = useState<Episode | null>(null);
  const [isUpgradeModalOpen, setIsUpgradeModalOpen] = useState<boolean>(false);
  const [upgradeFeaturePrompt, setUpgradeFeaturePrompt] = useState<string | null>(null);

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const gainNodeRef = useRef<GainNode | null>(null);

  // Sync to local storage
  useEffect(() => { StorageService.saveSettings(settings); }, [settings]);
  useEffect(() => { StorageService.savePodcasts(podcasts); }, [podcasts]);
  useEffect(() => { StorageService.saveEpisodes(episodes); }, [episodes]);
  useEffect(() => { StorageService.savePlaylists(playlists); }, [playlists]);

  // Apply light/dark theme class to html/body
  useEffect(() => {
    if (settings.appearance === 'light') {
      document.documentElement.classList.add('theme-light');
      document.body.classList.remove('bg-[#121214]', 'text-[#F4F4F5]');
      document.body.classList.add('bg-[#FFFFFF]', 'text-[#121214]');
    } else {
      document.documentElement.classList.remove('theme-light');
      document.body.classList.add('bg-[#121214]', 'text-[#F4F4F5]');
      document.body.classList.remove('bg-[#FFFFFF]', 'text-[#121214]');
    }
  }, [settings.appearance]);

  // Derived queue
  const queue = episodes
    .filter((ep) => ep.queuePosition !== null && !ep.isArchived)
    .sort((a, b) => (a.queuePosition || 0) - (b.queuePosition || 0));

  // Initialize Audio element
  useEffect(() => {
    const audio = new Audio();
    audio.preload = 'metadata';
    audioRef.current = audio;

    const onTimeUpdate = () => {
      const cur = audio.currentTime;
      setCurrentTime(cur);

      // Periodically update progress in storage
      if (currentEpisode) {
        setEpisodes((prev) =>
          prev.map((ep) =>
            ep.uuid === currentEpisode.uuid
              ? {
                  ...ep,
                  playedUpToSeconds: Math.floor(cur),
                  listenedSeconds: ep.listenedSeconds + 1,
                  playingStatus: ep.playingStatus === 2 ? 2 : 1,
                  lastPlayedAt: Date.now(),
                }
              : ep
          )
        );
      }
    };

    const onDurationChange = () => {
      if (audio.duration && !isNaN(audio.duration) && isFinite(audio.duration)) {
        setDuration(audio.duration);
      }
    };

    const onPlay = () => setIsPlaying(true);
    const onPause = () => setIsPlaying(false);

    audio.addEventListener('timeupdate', onTimeUpdate);
    audio.addEventListener('durationchange', onDurationChange);
    audio.addEventListener('play', onPlay);
    audio.addEventListener('pause', onPause);

    return () => {
      audio.pause();
      audio.removeEventListener('timeupdate', onTimeUpdate);
      audio.removeEventListener('durationchange', onDurationChange);
      audio.removeEventListener('play', onPlay);
      audio.removeEventListener('pause', onPause);
    };
  }, [currentEpisode]);

  // Handle Episode End & Auto Advance
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    const onEnded = () => {
      if (!currentEpisode) return;

      // Mark current as played (status = 2) and dequeue
      setEpisodes((prev) =>
        prev.map((ep) =>
          ep.uuid === currentEpisode.uuid
            ? {
                ...ep,
                playingStatus: 2,
                playedAt: Date.now(),
                queuePosition: null,
                playedUpToSeconds: Math.floor(audio.duration || ep.durationSeconds || 0),
              }
            : ep
        )
      );

      // Check sleep timer end of episode
      if (sleepTimerMode === 'end_of_episode') {
        setSleepTimerSecondsRemaining(null);
        setSleepTimerMode(null);
        setIsPlaying(false);
        return;
      }

      // Auto advance to next queued item
      const nextInQueue = queue.find((ep) => ep.uuid !== currentEpisode.uuid);
      if (nextInQueue) {
        playEpisode(nextInQueue);
      } else {
        setIsPlaying(false);
      }
    };

    audio.addEventListener('ended', onEnded);
    return () => audio.removeEventListener('ended', onEnded);
  }, [currentEpisode, queue, sleepTimerMode]);

  // Sleep timer interval countdown
  useEffect(() => {
    if (sleepTimerSecondsRemaining === null || sleepTimerSecondsRemaining <= 0) return;

    const timer = setInterval(() => {
      setSleepTimerSecondsRemaining((prev) => {
        if (prev === null || prev <= 1) {
          if (audioRef.current) audioRef.current.pause();
          setIsPlaying(false);
          setSleepTimerMode(null);
          return null;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [sleepTimerSecondsRemaining]);

  // Play Episode
  const playEpisode = useCallback((episode: Episode) => {
    const audio = audioRef.current;
    if (!audio) return;

    // Save previous progress if switching
    if (currentEpisode && currentEpisode.uuid !== episode.uuid) {
      setEpisodes((prev) =>
        prev.map((ep) =>
          ep.uuid === currentEpisode.uuid
            ? { ...ep, playedUpToSeconds: Math.floor(audio.currentTime) }
            : ep
        )
      );
    }

    setCurrentEpisode(episode);
    setDuration(episode.durationSeconds || 0);

    // Set audio src
    audio.src = episode.audioUrl;
    audio.playbackRate = playbackSpeed;

    // Resume from last position if present and not completed
    const startPoint = episode.playingStatus === 2 ? 0 : episode.playedUpToSeconds || 0;
    audio.currentTime = startPoint;
    setCurrentTime(startPoint);

    audio.play().catch((err) => {
      console.warn("Autoplay was prevented:", err);
    });
    setIsPlaying(true);
  }, [currentEpisode, playbackSpeed]);

  const togglePlayPause = useCallback(() => {
    const audio = audioRef.current;
    if (!audio) return;

    if (isPlaying) {
      audio.pause();
      setIsPlaying(false);
    } else {
      if (!currentEpisode) {
        // Fallback: play top of queue or first in progress
        const first = queue[0] || episodes.find((e) => e.playingStatus === 1) || episodes[0];
        if (first) playEpisode(first);
        return;
      }
      audio.play().catch(console.warn);
      setIsPlaying(true);
    }
  }, [isPlaying, currentEpisode, queue, episodes, playEpisode]);

  const pause = useCallback(() => {
    if (audioRef.current) audioRef.current.pause();
    setIsPlaying(false);
  }, []);

  const seekTo = useCallback((seconds: number) => {
    const audio = audioRef.current;
    if (!audio) return;
    const clamped = Math.max(0, Math.min(seconds, duration || audio.duration || 999999));
    audio.currentTime = clamped;
    setCurrentTime(clamped);
  }, [duration]);

  const skipBackward = useCallback((seconds?: number) => {
    const skip = seconds ?? settings.skipBackSeconds ?? 15;
    seekTo(currentTime - skip);
  }, [currentTime, settings.skipBackSeconds, seekTo]);

  const skipForward = useCallback((seconds?: number) => {
    const skip = seconds ?? settings.skipForwardSeconds ?? 30;
    seekTo(currentTime + skip);
  }, [currentTime, settings.skipForwardSeconds, seekTo]);

  const setSpeed = useCallback((spd: number) => {
    setPlaybackSpeed(spd);
    if (audioRef.current) audioRef.current.playbackRate = spd;
  }, []);

  const toggleVolumeBoost = useCallback(() => {
    setVolumeBoost((prev) => !prev);
    // Simple Web Audio Gain Node simulation / boost
    try {
      if (!audioContextRef.current && audioRef.current) {
        const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
        const ctx = new AudioCtx();
        const src = ctx.createMediaElementSource(audioRef.current);
        const gain = ctx.createGain();
        src.connect(gain);
        gain.connect(ctx.destination);
        audioContextRef.current = ctx;
        gainNodeRef.current = gain;
      }
      if (gainNodeRef.current) {
        gainNodeRef.current.gain.value = !volumeBoost ? 1.75 : 1.0;
      }
    } catch {
      // Audio element might already be connected
    }
  }, [volumeBoost]);

  const toggleSkipSilence = useCallback(() => {
    setSkipSilence((prev) => !prev);
  }, []);

  const setSleepTimer = useCallback((minutes: number | 'end_of_episode' | null) => {
    if (minutes === null) {
      setSleepTimerSecondsRemaining(null);
      setSleepTimerMode(null);
    } else if (minutes === 'end_of_episode') {
      setSleepTimerSecondsRemaining(null);
      setSleepTimerMode('end_of_episode');
    } else {
      setSleepTimerSecondsRemaining(minutes * 60);
      setSleepTimerMode(`${minutes}m`);
    }
  }, []);

  // Queue actions
  const enqueue = useCallback((episodeId: string, atTop: boolean = false) => {
    setEpisodes((prev) => {
      const target = prev.find((e) => e.uuid === episodeId);
      if (!target) return prev;

      const currentMax = Math.max(0, ...prev.map((e) => e.queuePosition || 0));

      if (atTop) {
        return prev.map((e) => {
          if (e.uuid === episodeId) return { ...e, queuePosition: 1, isArchived: false };
          if (e.queuePosition !== null) return { ...e, queuePosition: e.queuePosition + 1 };
          return e;
        });
      } else {
        return prev.map((e) =>
          e.uuid === episodeId ? { ...e, queuePosition: currentMax + 1, isArchived: false } : e
        );
      }
    });
  }, []);

  const dequeue = useCallback((episodeId: string) => {
    setEpisodes((prev) =>
      prev.map((e) => (e.uuid === episodeId ? { ...e, queuePosition: null } : e))
    );
  }, []);

  const enqueueAll = useCallback((episodeIds: string[], atTop: boolean = false) => {
    if (!episodeIds.length) return;
    setEpisodes((prev) => {
      const targetSet = new Set(episodeIds);
      const currentMax = Math.max(0, ...prev.map((e) => e.queuePosition || 0));

      if (atTop) {
        let topOffset = 1;
        return prev.map((e) => {
          if (targetSet.has(e.uuid)) {
            const pos = topOffset++;
            return { ...e, queuePosition: pos, isArchived: false };
          }
          if (e.queuePosition !== null) {
            return { ...e, queuePosition: e.queuePosition + episodeIds.length };
          }
          return e;
        });
      } else {
        let appendOffset = currentMax + 1;
        return prev.map((e) => {
          if (targetSet.has(e.uuid) && e.queuePosition === null) {
            return { ...e, queuePosition: appendOffset++, isArchived: false };
          }
          return e;
        });
      }
    });
  }, []);

  const dequeueAll = useCallback((episodeIds: string[]) => {
    const targetSet = new Set(episodeIds);
    setEpisodes((prev) =>
      prev.map((e) => (targetSet.has(e.uuid) ? { ...e, queuePosition: null } : e))
    );
  }, []);

  const reorderQueue = useCallback((fromIndex: number, toIndex: number) => {
    setEpisodes((prev) => {
      const q = prev
        .filter((ep) => ep.queuePosition !== null && !ep.isArchived)
        .sort((a, b) => (a.queuePosition || 0) - (b.queuePosition || 0));

      if (fromIndex < 0 || fromIndex >= q.length || toIndex < 0 || toIndex >= q.length) return prev;

      const item = q.splice(fromIndex, 1)[0];
      q.splice(toIndex, 0, item);

      const posMap = new Map<string, number>();
      q.forEach((ep, idx) => posMap.set(ep.uuid, idx + 1));

      return prev.map((e) => ({
        ...e,
        queuePosition: posMap.has(e.uuid) ? posMap.get(e.uuid)! : e.queuePosition,
      }));
    });
  }, []);

  const moveQueueToTop = useCallback((episodeId: string) => {
    enqueue(episodeId, true);
  }, [enqueue]);

  const moveQueueToEnd = useCallback((episodeId: string) => {
    enqueue(episodeId, false);
  }, [enqueue]);

  // Episode archive & status
  const archiveEpisode = useCallback((episodeId: string) => {
    setEpisodes((prev) =>
      prev.map((e) =>
        e.uuid === episodeId
          ? {
              ...e,
              isArchived: true,
              archivedAt: Date.now(),
              playingStatus: 2, // archive marks played
              queuePosition: null,
            }
          : e
      )
    );
    if (currentEpisode?.uuid === episodeId) {
      pause();
      setCurrentEpisode(null);
    }
  }, [currentEpisode, pause]);

  const unarchiveEpisode = useCallback((episodeId: string) => {
    setEpisodes((prev) =>
      prev.map((e) =>
        e.uuid === episodeId ? { ...e, isArchived: false, archivedAt: null } : e
      )
    );
  }, []);

  const toggleStar = useCallback((episodeId: string) => {
    setEpisodes((prev) =>
      prev.map((e) => (e.uuid === episodeId ? { ...e, starred: !e.starred } : e))
    );
  }, []);

  const toggleDownload = useCallback((episodeId: string) => {
    setEpisodes((prev) =>
      prev.map((e) =>
        e.uuid === episodeId ? { ...e, isDownloaded: !e.isDownloaded } : e
      )
    );
  }, []);

  const markPlayed = useCallback((episodeId: string, played: boolean) => {
    setEpisodes((prev) =>
      prev.map((e) =>
        e.uuid === episodeId
          ? {
              ...e,
              playingStatus: played ? 2 : 0,
              playedAt: played ? Date.now() : null,
              playedUpToSeconds: played ? (e.durationSeconds || 0) : 0,
              queuePosition: played ? null : e.queuePosition,
            }
          : e
      )
    );
  }, []);

  // Podcast subscription
  const subscribePodcast = useCallback((podcast: Podcast, initialEpisodes: Episode[]) => {
    setPodcasts((prev) => [
      podcast,
      ...prev.filter((p) => p.feedUrl !== podcast.feedUrl && p.uuid !== podcast.uuid),
    ]);
    setEpisodes((prev) => [...initialEpisodes, ...prev]);
  }, []);

  const unsubscribePodcast = useCallback((podcastUuid: string) => {
    setPodcasts((prev) =>
      prev.map((p) => (p.uuid === podcastUuid ? { ...p, isSubscribed: false } : p))
    );
  }, []);

  // Playlist management
  const createPlaylist = useCallback((title: string) => {
    const newPl: Playlist = {
      id: 'pl_' + Math.random().toString(36).substring(2, 9),
      title,
      isSmart: false,
      episodeIds: [],
      assignedPodcastUuids: [],
      sortBy: 'manual',
      sortDirection: 'asc',
    };
    setPlaylists((prev) => [...prev, newPl]);
  }, []);

  const deletePlaylist = useCallback((playlistId: string) => {
    setPlaylists((prev) => prev.filter((p) => p.id !== playlistId));
  }, []);

  const updatePlaylist = useCallback((updated: Playlist) => {
    setPlaylists((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
  }, []);

  const updateSettings = useCallback((newSettings: Partial<PlaybackSettings>) => {
    setSettings((prev) => ({ ...prev, ...newSettings }));
  }, []);

  // Central Entitlement Verification:
  // FOSS Edition and Play Free both provide complete baseline features.
  // In Google Play edition, Pro requires a cryptographically verified annual subscription receipt.
  const activeEdition = settings.appEdition || (settings.appFlavor === 'foss' ? 'foss' : 'play');
  const entitlementResult = verifySubscriptionEntitlement(settings.purchaseReceipt);
  const isPro = activeEdition === 'foss' ? false : entitlementResult.isEntitled;
  const purchaseReceipt = settings.purchaseReceipt || null;
  const subscriptionStatus: SubscriptionStatus | 'NONE' = activeEdition === 'foss' ? 'NONE' : (settings.purchaseReceipt?.status || 'NONE');
  const subscriptionExpiryTime = settings.purchaseReceipt?.expiryTime || null;
  const isGracePeriod = settings.purchaseReceipt?.status === 'IN_GRACE_PERIOD';
  const isAutoRenewing = settings.purchaseReceipt?.autoRenewing ?? false;

  const canAccess = useCallback(
    (feature: FeatureKey): boolean => {
      return canAccessFeature(feature, activeEdition, settings.purchaseReceipt);
    },
    [activeEdition, settings.purchaseReceipt]
  );

  const openUpgradeModal = useCallback((featurePrompt?: string) => {
    setUpgradeFeaturePrompt(featurePrompt || null);
    setIsUpgradeModalOpen(true);
  }, []);

  const closeUpgradeModal = useCallback(() => {
    setIsUpgradeModalOpen(false);
    setUpgradeFeaturePrompt(null);
  }, []);

  const unlockPro = useCallback((status: SubscriptionStatus = 'ACTIVE') => {
    const verifiedReceipt = createVerifiedSubscriptionReceipt(status, status === 'ACTIVE');
    setSettings((prev) => ({
      ...prev,
      purchaseReceipt: verifiedReceipt,
      // Legacy backward-compatibility flags
      isProUnlocked: status === 'ACTIVE' || status === 'CANCELLED' || status === 'IN_GRACE_PERIOD',
      proPurchaseDate: verifiedReceipt.purchaseTime,
      proOrderId: verifiedReceipt.orderId,
      proPlanType: 'annual',
    }));
  }, []);

  const cancelSubscription = useCallback(() => {
    setSettings((prev) => {
      if (!prev.purchaseReceipt) return prev;
      return {
        ...prev,
        purchaseReceipt: {
          ...prev.purchaseReceipt,
          status: 'CANCELLED',
          autoRenewing: false,
        },
      };
    });
  }, []);

  const simulateSubscriptionStatus = useCallback((status: SubscriptionStatus) => {
    const verifiedReceipt = createVerifiedSubscriptionReceipt(status, status === 'ACTIVE');
    setSettings((prev) => ({
      ...prev,
      purchaseReceipt: verifiedReceipt,
      isProUnlocked: status === 'ACTIVE' || status === 'CANCELLED' || status === 'IN_GRACE_PERIOD',
      proPurchaseDate: verifiedReceipt.purchaseTime,
      proOrderId: verifiedReceipt.orderId,
      proPlanType: 'annual',
    }));
  }, []);

  const acknowledgePurchase = useCallback(() => {
    setSettings((prev) => {
      if (!prev.purchaseReceipt) return prev;
      return {
        ...prev,
        purchaseReceipt: {
          ...prev.purchaseReceipt,
          acknowledged: true,
        },
      };
    });
  }, []);

  const pauseSubscription = useCallback(() => {
    simulateSubscriptionStatus('PAUSED');
  }, [simulateSubscriptionStatus]);

  const revokeSubscription = useCallback(() => {
    simulateSubscriptionStatus('REVOKED');
  }, [simulateSubscriptionStatus]);

  const setAccountHold = useCallback(() => {
    simulateSubscriptionStatus('ON_HOLD');
  }, [simulateSubscriptionStatus]);

  const recoverInterruptedPurchases = useCallback(async (): Promise<boolean> => {
    // Simulates Play Billing queryPurchasesAsync() recovery check on startup
    await new Promise((resolve) => setTimeout(resolve, 600));
    setSettings((prev) => {
      if (prev.purchaseReceipt && !prev.purchaseReceipt.acknowledged) {
        return {
          ...prev,
          purchaseReceipt: {
            ...prev.purchaseReceipt,
            acknowledged: true,
          },
        };
      }
      return prev;
    });
    return true;
  }, []);

  const restorePurchases = useCallback(async (): Promise<boolean> => {
    // Simulates Google Play Billing 7.x queryPurchasesAsync() flow
    await new Promise((resolve) => setTimeout(resolve, 800));
    const verifiedReceipt = createVerifiedSubscriptionReceipt('ACTIVE', true);
    setSettings((prev) => ({
      ...prev,
      purchaseReceipt: verifiedReceipt,
      isProUnlocked: true,
      proPurchaseDate: verifiedReceipt.purchaseTime,
      proOrderId: verifiedReceipt.orderId,
      proPlanType: 'annual',
    }));
    return true;
  }, []);

  const resetPro = useCallback(() => {
    setSettings((prev) => ({
      ...prev,
      purchaseReceipt: null,
      isProUnlocked: false,
      proPurchaseDate: undefined,
      proOrderId: undefined,
      proPlanType: undefined,
    }));
  }, []);

  return (
    <PlayerContext.Provider
      value={{
        currentEpisode,
        isPlaying,
        currentTime,
        duration,
        playbackSpeed,
        volumeBoost,
        skipSilence,
        sleepTimerSecondsRemaining,
        sleepTimerMode,
        playEpisode,
        togglePlayPause,
        pause,
        seekTo,
        skipBackward,
        skipForward,
        setSpeed,
        toggleVolumeBoost,
        toggleSkipSilence,
        setSleepTimer,
        queue,
        enqueue,
        dequeue,
        enqueueAll,
        dequeueAll,
        reorderQueue,
        moveQueueToTop,
        moveQueueToEnd,
        podcasts,
        episodes,
        playlists,
        settings,
        updateSettings,
        subscribePodcast,
        unsubscribePodcast,
        archiveEpisode,
        unarchiveEpisode,
        toggleStar,
        toggleDownload,
        markPlayed,
        createPlaylist,
        deletePlaylist,
        updatePlaylist,
        isPlayerOpen,
        setIsPlayerOpen,
        activeNotesEpisode,
        setActiveNotesEpisode,
        isPro,
        purchaseReceipt,
        subscriptionStatus,
        subscriptionExpiryTime,
        isGracePeriod,
        isAutoRenewing,
        canAccess,
        isUpgradeModalOpen,
        upgradeFeaturePrompt,
        openUpgradeModal,
        closeUpgradeModal,
        unlockPro,
        cancelSubscription,
        simulateSubscriptionStatus,
        acknowledgePurchase,
        pauseSubscription,
        revokeSubscription,
        setAccountHold,
        recoverInterruptedPurchases,
        restorePurchases,
        resetPro,
      }}
    >
      {children}
    </PlayerContext.Provider>
  );
};

export const usePlayer = (): PlayerContextType => {
  const ctx = useContext(PlayerContext);
  if (!ctx) throw new Error('usePlayer must be used within PlayerProvider');
  return ctx;
};
