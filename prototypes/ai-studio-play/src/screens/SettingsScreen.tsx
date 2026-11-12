import React, { useState } from 'react';
import { usePlayer } from '../context/PlayerContext';
import { QueueIndicator } from '../components/QueueIndicator';
import {
  ArrowLeft,
  ChevronRight,
  Sun,
  Moon,
  Shield,
  ShieldCheck,
  Trash2,
  Check,
  Battery,
  Sliders,
  ExternalLink,
  Volume2,
  FastForward,
  Rewind,
  MoonStar,
  Headphones,
  HardDrive,
  Wifi,
  Crown,
  Info,
  Clock,
  Archive,
  DownloadCloud,
  ShoppingBag,
  Heart,
  Download,
  RefreshCw,
  Award,
  Zap,
} from 'lucide-react';

export const SettingsScreen: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const {
    settings,
    updateSettings,
    episodes,
    isPro,
    purchaseReceipt,
    canAccess,
    openUpgradeModal,
    unlockPro,
    resetPro,
  } = usePlayer();

  const [activeCategory, setActiveCategory] = useState<
    'index' | 'appearance' | 'playback' | 'floating' | 'archiving' | 'downloads' | 'about'
  >('index');

  const [showPrivacyPolicy, setShowPrivacyPolicy] = useState(false);
  const [showBatteryModal, setShowBatteryModal] = useState(false);
  const [showClearConfirm, setShowClearConfirm] = useState(false);
  const [statusNotification, setStatusNotification] = useState<string | null>(null);
  const [isCheckingUpdate, setIsCheckingUpdate] = useState(false);

  const currentFlavor = settings.appFlavor || 'foss';

  const downloadedEpisodes = episodes.filter((e) => e.isDownloaded);
  const totalDownloadedBytes = downloadedEpisodes.reduce((acc, e) => acc + (e.sizeBytes || 25000000), 0);
  const formattedStorage = (totalDownloadedBytes / (1024 * 1024)).toFixed(1) + ' MB';

  const notify = (msg: string) => {
    setStatusNotification(msg);
    setTimeout(() => setStatusNotification(null), 3000);
  };

  const getHeaderTitle = () => {
    switch (activeCategory) {
      case 'index':
        return 'Settings';
      case 'appearance':
        return 'Appearance';
      case 'playback':
        return 'Playback';
      case 'floating':
        return 'Floating player';
      case 'archiving':
        return 'Archiving';
      case 'downloads':
        return 'Downloads';
      case 'about':
        return 'About';
    }
  };

  const handleBack = () => {
    if (activeCategory === 'index') {
      onClose();
    } else {
      setActiveCategory('index');
    }
  };

  const handleCheckUpdate = () => {
    setIsCheckingUpdate(true);
    setTimeout(() => {
      setIsCheckingUpdate(false);
      if (currentFlavor === 'foss') {
        notify('Latest FOSS release: Lohe v0.11.1 (Up to date on GitHub & F-Droid).');
      } else {
        notify('Google Play Store: Lohe v0.11.1 is the latest release.');
      }
    }, 1000);
  };

  const colorPresets = [
    { id: 'blue', label: 'Electric Blue', hex: '#008EFF' },
    { id: 'cyan', label: 'Glow Cyan', hex: '#00D2FF' },
    { id: 'teal', label: 'Emerald Teal', hex: '#14B8A6' },
    { id: 'amber', label: 'Warm Amber', hex: '#FF9F0A' },
    { id: 'purple', label: 'Neon Purple', hex: '#A855F7' },
    { id: 'coral', label: 'Rose Coral', hex: '#F43F5E' },
    { id: 'white', label: 'Crisp White', hex: '#FFFFFF' },
  ];

  return (
    <div className="fixed inset-0 z-40 flex flex-col bg-[#121214] text-[#F4F4F5] overflow-y-auto font-sans select-none">
      {/* Native Android Top App Bar */}
      <header className="sticky top-0 z-30 flex items-center h-16 px-4 bg-[#121214]/95 backdrop-blur-md border-b border-[#26262A] max-w-2xl w-full mx-auto shrink-0">
        <button
          type="button"
          onClick={handleBack}
          className="w-12 h-12 flex items-center justify-center -ml-2 rounded-full text-white/90 hover:text-white hover:bg-white/10 active:scale-95 transition-all"
          aria-label="Back"
        >
          <ArrowLeft className="w-6 h-6 text-white" />
        </button>
        <h1 className="text-xl font-bold text-white ml-2 tracking-tight">
          {getHeaderTitle()}
        </h1>
      </header>

      {/* Global Toast Notification */}
      {statusNotification && (
        <div className="fixed top-20 left-1/2 -translate-x-1/2 z-50 px-4 py-2 rounded-full bg-[#008EFF] text-white text-xs font-bold shadow-lg animate-fade-in flex items-center gap-2">
          <Check className="w-4 h-4" />
          <span>{statusNotification}</span>
        </div>
      )}

      <div className="flex-1 max-w-2xl w-full mx-auto pb-44 px-4 sm:px-6">
        {/* ======================================================== */}
        {/* 1. SETTINGS INDEX (Matches screenshot-settings.png)       */}
        {/* ======================================================== */}
        {activeCategory === 'index' && (
          <div className="pt-2 space-y-4">
            {/* Pro Upgrade / Pro Active Card */}
            {/* Pro Upgrade / Pro Active / FOSS Libre Card */}
            {(settings.appEdition || (settings.appFlavor === 'foss' ? 'foss' : 'play')) === 'foss' ? (
              <div className="p-4 rounded-2xl bg-white/[0.04] border border-white/10 shadow-sm">
                <div className="flex items-center justify-between mb-1.5">
                  <div className="flex items-center gap-2">
                    <span className="w-6 h-6 rounded-lg bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold text-xs">
                      ✓
                    </span>
                    <h3 className="text-sm font-bold text-white">
                      FOSS Standalone Edition
                    </h3>
                  </div>
                  <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-400">
                    Libre
                  </span>
                </div>
                <p className="text-xs text-[#8E8E93] leading-relaxed">
                  100% Free and Open Source. Built without proprietary Google Play libraries or tracking. All free baseline features (volume boost, unlimited playlists, smart playlists, per-show queueing) are fully active.
                </p>
                <div className="mt-2 text-[11px] text-[#8E8E93]">
                  Pro additions (floating player overlay and custom styles) are exclusive to Google Play builds and unavailable in FOSS.
                </div>
              </div>
            ) : !isPro ? (
              <div
                onClick={() => openUpgradeModal('Lohe Pro Additions')}
                className="p-4 rounded-2xl bg-gradient-to-r from-[#008EFF]/15 via-[#00D2FF]/10 to-transparent border border-[#008EFF]/40 cursor-pointer shadow-lg hover:border-[#008EFF]/70 transition-all group"
              >
                <div className="flex items-center justify-between mb-1.5">
                  <div className="flex items-center gap-2">
                    <span className="w-6 h-6 rounded-lg bg-[#008EFF] text-white flex items-center justify-center font-black text-xs shadow-sm">
                      ★
                    </span>
                    <h3 className="text-sm font-bold text-white group-hover:text-[#00D2FF] transition-colors">
                      Upgrade to Lohe Pro
                    </h3>
                  </div>
                  <span className="text-xs font-bold text-[#00D2FF]">
                    US$9.99/year
                  </span>
                </div>
                <p className="text-xs text-[#8E8E93] leading-relaxed">
                  Support independent development and unlock Floating Mini Player Overlay over other apps, Glow/Diamond indicator styles, and precision custom HEX color engine.
                </p>
                <div className="mt-3 flex items-center justify-between text-xs">
                  <span className="text-[#008EFF] font-semibold flex items-center gap-1 group-hover:translate-x-0.5 transition-transform">
                    <span>Subscribe with Google Play</span>
                    <ChevronRight className="w-3.5 h-3.5" />
                  </span>
                  <span className="text-[10px] text-[#8E8E93]">Annual Subscription · Cancel anytime</span>
                </div>
              </div>
            ) : (
              <div className="p-3.5 rounded-2xl bg-[#008EFF]/10 border border-[#008EFF]/30 flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <span className="w-6 h-6 rounded-lg bg-[#008EFF] text-white flex items-center justify-center font-black text-xs shadow-sm">
                    ★
                  </span>
                  <div>
                    <h4 className="text-xs font-bold text-white flex items-center gap-1.5">
                      <span>Lohe Pro Active</span>
                      <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-[#008EFF]/20 text-[#00D2FF]">
                        US$9.99/yr
                      </span>
                    </h4>
                    <p className="text-[11px] text-[#8E8E93]">
                      Floating player, Glow/Diamond styles, and custom HEX colors unlocked
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={() => openUpgradeModal()}
                    className="text-[11px] text-[#00D2FF] hover:text-white px-2.5 py-1 rounded-lg bg-[#008EFF]/20 hover:bg-[#008EFF]/30 transition-colors"
                  >
                    Manage
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      resetPro();
                      notify('Reset to Free Tier for verification');
                    }}
                    className="text-[11px] text-[#8E8E93] hover:text-white px-2 py-1 rounded-lg bg-white/5 hover:bg-white/10 transition-colors"
                  >
                    Reset
                  </button>
                </div>
              </div>
            )}

            <div className="divide-y divide-[#26262A]">
              {/* Appearance */}
              <button
                type="button"
                onClick={() => setActiveCategory('appearance')}
                className="w-full flex items-center justify-between py-4.5 px-1 text-left group hover:bg-white/[0.02] active:bg-white/[0.05] transition-colors"
              >
                <div className="pr-4">
                  <h3 className="text-[17px] font-medium text-white group-hover:text-[#008EFF] transition-colors">
                    Appearance
                  </h3>
                  <p className="text-[13px] text-[#8E8E93] mt-0.5">
                    Dark or light theme
                  </p>
                </div>
                <ChevronRight className="w-5 h-5 text-[#008EFF] shrink-0" />
              </button>

              {/* Playback */}
              <button
                type="button"
                onClick={() => setActiveCategory('playback')}
                className="w-full flex items-center justify-between py-4.5 px-1 text-left group hover:bg-white/[0.02] active:bg-white/[0.05] transition-colors"
              >
                <div className="pr-4">
                  <h3 className="text-[17px] font-medium text-white group-hover:text-[#008EFF] transition-colors">
                    Playback
                  </h3>
                  <p className="text-[13px] text-[#8E8E93] mt-0.5">
                    Speed, skip buttons, silence, and finishing episodes
                  </p>
                </div>
                <ChevronRight className="w-5 h-5 text-[#008EFF] shrink-0" />
              </button>

              {/* Floating player */}
              <button
                type="button"
                onClick={() => {
                  if (!canAccess('floating_player')) {
                    openUpgradeModal('Floating Mini Player Overlay');
                  }
                  setActiveCategory('floating');
                }}
                className="w-full flex items-center justify-between py-4.5 px-1 text-left group hover:bg-white/[0.02] active:bg-white/[0.05] transition-colors"
              >
                <div className="pr-4">
                  <div className="flex items-center gap-2">
                    <h3 className="text-[17px] font-medium text-white group-hover:text-[#008EFF] transition-colors">
                      Floating player
                    </h3>
                    {!canAccess('floating_player') && (
                      <span className="px-1.5 py-0.2 rounded text-[10px] font-bold bg-[#008EFF]/15 text-[#00D2FF] border border-[#008EFF]/30">
                        PRO
                      </span>
                    )}
                  </div>
                  <p className="text-[13px] text-[#8E8E93] mt-0.5">
                    Cross-app controls, size, transparency, and battery setup
                  </p>
                </div>
                <ChevronRight className="w-5 h-5 text-[#008EFF] shrink-0" />
              </button>

            {/* Archiving */}
            <button
              type="button"
              onClick={() => setActiveCategory('archiving')}
              className="w-full flex items-center justify-between py-4.5 px-1 text-left group hover:bg-white/[0.02] active:bg-white/[0.05] transition-colors"
            >
              <div className="pr-4">
                <h3 className="text-[17px] font-medium text-white group-hover:text-[#008EFF] transition-colors">
                  Archiving
                </h3>
                <p className="text-[13px] text-[#8E8E93] mt-0.5">
                  Archive delay and downloaded-copy cleanup
                </p>
              </div>
              <ChevronRight className="w-5 h-5 text-[#008EFF] shrink-0" />
            </button>

            {/* Downloads */}
            <button
              type="button"
              onClick={() => setActiveCategory('downloads')}
              className="w-full flex items-center justify-between py-4.5 px-1 text-left group hover:bg-white/[0.02] active:bg-white/[0.05] transition-colors"
            >
              <div className="pr-4">
                <h3 className="text-[17px] font-medium text-white group-hover:text-[#008EFF] transition-colors">
                  Downloads
                </h3>
                <p className="text-[13px] text-[#8E8E93] mt-0.5">
                  Automatic downloads for Up Next
                </p>
              </div>
              <ChevronRight className="w-5 h-5 text-[#008EFF] shrink-0" />
            </button>

            {/* About */}
            <button
              type="button"
              onClick={() => setActiveCategory('about')}
              className="w-full flex items-center justify-between py-4.5 px-1 text-left group hover:bg-white/[0.02] active:bg-white/[0.05] transition-colors"
            >
              <div className="pr-4">
                <h3 className="text-[17px] font-medium text-white group-hover:text-[#008EFF] transition-colors">
                  About
                </h3>
                <p className="text-[13px] text-[#8E8E93] mt-0.5">
                  Version and app information
                </p>
              </div>
              <ChevronRight className="w-5 h-5 text-[#008EFF] shrink-0" />
            </button>
          </div>
        </div>
        )}

        {/* ======================================================== */}
        {/* 2. ABOUT SCREEN (With Multi-Flavor Support)               */}
        {/* ======================================================== */}
        {activeCategory === 'about' && (
          <div className="pt-2">
            {/* App Edition Selector (FOSS Edition & Google Play Edition) */}
            <div className="mb-6 p-1.5 rounded-2xl bg-[#1C1C1E] border border-[#2C2C30]">
              <div className="text-[11px] font-bold uppercase tracking-wider text-[#8E8E93] text-center mb-1.5">
                App Edition & Distribution
              </div>
              <div className="grid grid-cols-2 gap-1.5">
                <button
                  type="button"
                  onClick={() => {
                    updateSettings({ appEdition: 'play', appFlavor: 'play_free' });
                    notify('Switched to: Google Play Edition (Play Free matches FOSS; Pro in-app upgrade)');
                  }}
                  className={`py-2 px-2 rounded-xl text-xs font-semibold text-center transition-all ${
                    (settings.appEdition || (settings.appFlavor === 'foss' ? 'foss' : 'play')) === 'play'
                      ? 'bg-[#008EFF] text-white shadow-md'
                      : 'text-[#8E8E93] hover:text-white hover:bg-white/5'
                  }`}
                >
                  Google Play Edition
                </button>
                <button
                  type="button"
                  onClick={() => {
                    updateSettings({ appEdition: 'foss', appFlavor: 'foss' });
                    notify('Switched to: FOSS / Standalone Edition (Libre, No Google Services)');
                  }}
                  className={`py-2 px-2 rounded-xl text-xs font-semibold text-center transition-all ${
                    (settings.appEdition || (settings.appFlavor === 'foss' ? 'foss' : 'play')) === 'foss'
                      ? 'bg-[#008EFF] text-white shadow-md'
                      : 'text-[#8E8E93] hover:text-white hover:bg-white/5'
                  }`}
                >
                  FOSS / Standalone
                </button>
              </div>
            </div>

            {/* Centered Top Block (Exact match to Screenshot_20261112_075552_Lohe_b0936090.jpg) */}
            <div className="flex flex-col items-center text-center">
              <div className="w-[134px] h-[134px] relative mb-4">
                <img
                  src="/assets/official-app-icon.png"
                  alt="Lohe App Icon"
                  className="w-full h-full object-contain rounded-[28px] shadow-[0_12px_32px_rgba(0,142,255,0.38)]"
                />
              </div>

              <div className="flex justify-center mb-3">
                <img
                  src="/assets/lohe-wordmark-transparent.png"
                  alt="LOHE"
                  className="h-10 sm:h-11 w-auto object-contain filter drop-shadow-[0_0_14px_rgba(0,210,255,0.45)]"
                />
              </div>

              {/* Dynamic Version String per Flavor */}
              <p className="text-[#8E8E93] text-[14px] font-normal mb-1">
                Version 0.11.1 (build 2026.10.11.90
                {currentFlavor === 'play_free' && '-playFree'}
                {currentFlavor === 'play_paid' && '-playPaid'}
                {currentFlavor === 'foss' && '-foss'}
                )
              </p>

              <p className="text-[#008EFF] text-[17px] font-bold tracking-tight mb-3">
                ua lohe ‘oe? - Have you heard?
              </p>

              <button
                type="button"
                onClick={() => setShowPrivacyPolicy(true)}
                className="text-[#008EFF] text-[15px] font-normal hover:underline cursor-pointer mb-5"
              >
                Privacy policy
              </button>
            </div>

            {/* Edition-Specific Feature Cards */}
            {/* A. Google Play Edition (Play Free matches FOSS, Pro is in-app upgrade) */}
            {(settings.appEdition || (settings.appFlavor === 'foss' ? 'foss' : 'play')) === 'play' && (
              <div className="mb-6 p-4 rounded-2xl bg-[#1C1C1E] border border-[#2C2C30] space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <ShoppingBag className="w-4 h-4 text-[#008EFF]" />
                    <span className="text-sm font-bold text-white">Google Play Edition</span>
                  </div>
                  <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-[#008EFF]/15 text-[#00D2FF]">
                    com.lohe.app
                  </span>
                </div>
                <p className="text-xs text-[#8E8E93] leading-relaxed">
                  Distributed via Google Play Store with Google Play Billing Library 8.0.0+. Play Free includes all baseline features (volume boost, skip silence, unlimited playlists and folders, smart playlists, per-show queueing, and local folder playback).
                </p>

                {/* Google Play In-App Paid Upgrade to Lohe Pro */}
                <div className="pt-3 border-t border-[#26262A]">
                  <div className="p-3.5 rounded-xl bg-gradient-to-br from-[#008EFF]/15 via-[#00D2FF]/10 to-transparent border border-[#008EFF]/30">
                    <div className="flex items-center justify-between mb-1.5">
                      <div className="flex items-center gap-1.5">
                        <Zap className="w-4 h-4 text-[#008EFF]" />
                        <span className="text-xs font-bold text-white">Annual Subscription (Lohe Pro)</span>
                      </div>
                      <span className={`text-[10px] font-bold uppercase px-2 py-0.5 rounded-full ${
                        isPro ? 'bg-[#008EFF] text-white' : 'bg-white/10 text-[#00D2FF]'
                      }`}>
                        {isPro ? 'PRO ACTIVE' : 'FREE BASELINE'}
                      </span>
                    </div>

                    <p className="text-[11px] text-[#8E8E93] leading-relaxed mb-3">
                      {isPro
                        ? `Pro subscription verified via Google Play. Order: ${purchaseReceipt?.orderId || 'GPA.3312-8874-9012'}. Base plan: lohe-pro-annual-base.`
                        : 'Upgrade to Lohe Pro to unlock Floating Player overlay over other apps, additional Glow/Diamond indicator styles, and precision custom HEX colors via Google Play (US$9.99/year).'}
                    </p>

                    <div className="flex items-center gap-2">
                      {!isPro ? (
                        <button
                          type="button"
                          onClick={() => openUpgradeModal('Lohe Pro In-App Upgrade')}
                          className="flex-1 py-2 rounded-xl bg-[#008EFF] hover:bg-[#00A8FF] text-white text-xs font-bold flex items-center justify-center gap-1.5 shadow-md active:scale-95 transition-all"
                        >
                          <Zap className="w-3.5 h-3.5 fill-white" />
                          <span>Subscribe to Pro (US$9.99/year)</span>
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => {
                            resetPro();
                            notify('Reset to Free Tier for verification');
                          }}
                          className="py-1.5 px-3 rounded-xl bg-white/5 hover:bg-white/10 text-[11px] text-[#8E8E93] hover:text-white transition-colors"
                        >
                          Reset (Test Free Tier)
                        </button>
                      )}
                    </div>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={handleCheckUpdate}
                  disabled={isCheckingUpdate}
                  className="w-full py-2.5 rounded-xl bg-white/5 hover:bg-white/10 text-xs font-semibold text-[#8E8E93] hover:text-white flex items-center justify-center gap-2 transition-colors"
                >
                  <RefreshCw className={`w-3.5 h-3.5 ${isCheckingUpdate ? 'animate-spin' : ''}`} />
                  <span>Check for Play Store Updates</span>
                </button>
              </div>
            )}

            {/* C. FOSS / Standalone Edition */}
            {currentFlavor === 'foss' && (
              <div className="mb-6 p-4 rounded-2xl bg-[#1C1C1E] border border-[#2C2C30] space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <ShieldCheck className="w-4 h-4 text-[#00D2FF]" />
                    <span className="text-sm font-bold text-white">FOSS / Standalone Edition</span>
                  </div>
                  <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-400">
                    com.lohe.app.foss
                  </span>
                </div>
                <p className="text-xs text-[#8E8E93] leading-relaxed">
                  100% Free and Open Source. Stripped of all proprietary Google libraries (no Play Billing, no Firebase, no Play Services). Available on F-Droid and GitHub Releases.
                </p>

                {/* Direct APK Update Checker */}
                <div className="p-3 rounded-xl bg-black/40 border border-[#2C2C30] flex items-center justify-between">
                  <div>
                    <p className="text-xs font-semibold text-white">GitHub / F-Droid Updates</p>
                    <p className="text-[11px] text-[#8E8E93]">v0.11.1 (Signed Release APK)</p>
                  </div>
                  <button
                    type="button"
                    onClick={handleCheckUpdate}
                    disabled={isCheckingUpdate}
                    className="px-3 py-1.5 rounded-lg bg-[#008EFF] hover:bg-[#00A8FF] text-white text-xs font-semibold flex items-center gap-1.5 transition-colors"
                  >
                    <Download className="w-3.5 h-3.5" />
                    <span>Download APK</span>
                  </button>
                </div>

                {/* FOSS Sponsorship Links */}
                <div className="pt-2 border-t border-[#26262A] flex items-center justify-between text-xs text-[#8E8E93]">
                  <span>Community Support:</span>
                  <div className="flex items-center gap-3">
                    <a
                      href="https://github.com/dave-patrick/Lohe"
                      target="_blank"
                      rel="noreferrer"
                      className="text-[#008EFF] hover:underline font-semibold"
                    >
                      GitHub Sponsors
                    </a>
                    <span>·</span>
                    <span className="text-[#8E8E93]">LiberaPay</span>
                  </div>
                </div>
              </div>
            )}

            {/* Prototype Package & Source ZIP Download Card */}
            <div className="mb-6 p-4 rounded-2xl bg-[#1C1C1E] border border-[#2C2C30] space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Archive className="w-4 h-4 text-[#00D2FF]" />
                  <span className="text-sm font-bold text-white">Project Source Code & Build Export</span>
                </div>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-[#008EFF]/15 text-[#00D2FF]">
                  v0.11.1
                </span>
              </div>
              <p className="text-xs text-[#8E8E93] leading-relaxed">
                Download the complete code written for Lohe (React prototype, Kotlin/Compose native Android service files, and Gradle build configurations with Play Billing 8.0.0+ integration). Git branch: <code className="text-[#00D2FF] font-mono text-[11px]">codex/ai-studio-play-prototype</code>.
              </p>
              <div className="pt-2 flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-2.5">
                <a
                  href="./lohe-source-code.zip"
                  download="lohe-source-code.zip"
                  className="px-3.5 py-2 rounded-xl bg-[#008EFF] hover:bg-[#00A8FF] text-white text-xs font-semibold flex items-center justify-center gap-2 transition-all active:scale-95 shadow-md"
                >
                  <Download className="w-4 h-4" />
                  <span>Download Source Code ZIP (84 KB)</span>
                </a>
                <a
                  href="./lohe-play-prototype.zip"
                  download="lohe-play-prototype.zip"
                  className="px-3 py-2 rounded-xl bg-white/5 hover:bg-white/10 text-[#A1A1A6] hover:text-white text-xs font-medium flex items-center justify-center gap-1.5 transition-all border border-white/10"
                >
                  <span>Full Package + Assets (22 MB)</span>
                </a>
              </div>
            </div>

            <div className="w-full border-t border-[#26262A] my-6" />

            <div className="text-left space-y-6 text-[15px] leading-relaxed">
              <div>
                <p className="text-[#008EFF] font-medium leading-snug">
                  To hear. To listen. To take something to heart.
                </p>
                <p className="text-[#E4E4E7] mt-1 font-normal leading-relaxed">
                  In Hawaiian, that is Lohe (lōhe). We built the app around the simple idea of listening on your own terms. Whether you are into gripping stories, honest conversations, the daily news, or a deep dive on a random obsession, Lohe lets you hear what you want, exactly how you want to hear it. It stays out of your way and remembers where you left off.
                </p>
              </div>

              <p className="text-[#E4E4E7] font-normal">
                Ua lohe ‘oe?
              </p>

              <div>
                <h3 className="text-[#008EFF] font-medium mb-1">
                  Kani
                </h3>
                <p className="text-[#E4E4E7] font-normal leading-relaxed">
                  That little dragonfly on your home screen is Kani. His name comes from the Hawaiian idea of listening with quiet, focused attention. He keeps an eye on your library so your next listen is always ready when you are.
                </p>
              </div>

              <div>
                <h3 className="text-[#008EFF] font-medium mb-1">
                  Credits
                </h3>
                <p className="text-[#E4E4E7] font-normal">
                  An Atomic 22 Industries app.
                </p>
              </div>
            </div>

            <div className="mt-12 mb-8 flex flex-col items-center justify-center text-center">
              <img
                src="/assets/atomic22-logo.png"
                alt="Atomic 22 Industries"
                className="h-10 w-auto object-contain filter drop-shadow-[0_0_10px_rgba(0,142,255,0.4)]"
              />
              <p className="text-[#8E8E93] text-[13px] mt-4 font-normal">
                © 2026 Atomic 22 Industries
              </p>
              <p className="text-[#8E8E93] text-[13px] mt-0.5 font-normal">
                Kani mascot by Atomic 22 Design
              </p>
            </div>
          </div>
        )}

        {/* ======================================================== */}
        {/* 3. APPEARANCE SUBPAGE                                    */}
        {/* ======================================================== */}
        {activeCategory === 'appearance' && (
          <div className="space-y-6 pt-3">
            <div className="p-5 rounded-2xl bg-[#1C1C1E] border border-[#2C2C30]">
              <h3 className="text-sm font-bold text-white mb-1">Theme</h3>
              <p className="text-xs text-[#8E8E93] mb-3">Choose between dark charcoal and light paper theme.</p>
              <div className="grid grid-cols-2 gap-3">
                <button
                  type="button"
                  onClick={() => updateSettings({ appearance: 'dark' })}
                  className={`flex items-center justify-center gap-2 py-3 rounded-xl border text-xs font-bold transition-all ${
                    settings.appearance === 'dark'
                      ? 'bg-[#008EFF] border-[#008EFF] text-white shadow-md'
                      : 'bg-black/30 border-white/10 text-white/70 hover:bg-black/40'
                  }`}
                >
                  <Moon className="w-4 h-4" />
                  <span>Dark (Charcoal)</span>
                </button>
                <button
                  type="button"
                  onClick={() => updateSettings({ appearance: 'light' })}
                  className={`flex items-center justify-center gap-2 py-3 rounded-xl border text-xs font-bold transition-all ${
                    settings.appearance === 'light'
                      ? 'bg-[#008EFF] border-[#008EFF] text-white shadow-md'
                      : 'bg-black/30 border-white/10 text-white/70 hover:bg-black/40'
                  }`}
                >
                  <Sun className="w-4 h-4" />
                  <span>Light (Paper)</span>
                </button>
              </div>
            </div>

            <div className="p-5 rounded-2xl bg-[#1C1C1E] border border-[#2C2C30]">
              <div className="flex items-center justify-between mb-2">
                <div>
                  <h3 className="text-base font-medium text-white">Up Next Indicator</h3>
                  <p className="text-xs text-[#8E8E93]">
                    Visual badge on episode rows currently lined up in Up Next.
                  </p>
                </div>
                <input
                  type="checkbox"
                  checked={settings.queueIndicator.enabled}
                  onChange={(e) =>
                    updateSettings({
                      queueIndicator: { ...settings.queueIndicator, enabled: e.target.checked },
                    })
                  }
                  className="w-5 h-5 accent-[#008EFF] cursor-pointer"
                />
              </div>

              {settings.queueIndicator.enabled && (
                <div className="mt-4 space-y-5 pt-4 border-t border-[#26262A]">
                  <div className="p-4 rounded-xl bg-black/40 border border-[#2C2C30] flex items-center justify-between">
                    <div className="flex items-center gap-2.5 min-w-0">
                      <QueueIndicator settings={settings.queueIndicator} isQueued={true} />
                      <span className="text-sm font-medium text-white truncate">
                        Daily AI News Digest: Gartner Predicts 40% AI...
                      </span>
                    </div>
                    <span className="text-[11px] text-[#008EFF] uppercase font-bold tracking-wider shrink-0 ml-2">
                      Live Preview
                    </span>
                  </div>

                  <div>
                    <label className="text-sm font-medium text-white block mb-2">
                      Indicator Style
                    </label>
                    <div className="grid grid-cols-3 sm:grid-cols-6 gap-2">
                      {(
                        [
                          { id: 'dot', label: 'Dot', feature: null },
                          { id: 'ring', label: 'Ring', feature: null },
                          { id: 'icon', label: 'Icon', feature: null },
                          { id: 'pill', label: 'Pill', feature: null },
                          { id: 'diamond', label: 'Diamond', feature: 'up_next_diamond_style' as const },
                          { id: 'glow', label: 'Glow', feature: 'up_next_glow_style' as const },
                        ] as const
                      ).map((item) => {
                        const isLocked = item.feature ? !canAccess(item.feature) : false;
                        return (
                          <button
                            key={item.id}
                            type="button"
                            onClick={() => {
                              if (isLocked) {
                                openUpgradeModal(`${item.label} Up Next Indicator Style`);
                                return;
                              }
                              updateSettings({
                                queueIndicator: { ...settings.queueIndicator, style: item.id },
                              });
                            }}
                            className={`py-2 px-1 rounded-xl text-xs font-semibold flex flex-col items-center gap-1.5 transition-all relative ${
                              settings.queueIndicator.style === item.id
                                ? 'bg-[#008EFF] text-white shadow-md'
                                : 'bg-white/5 text-[#8E8E93] hover:text-white hover:bg-white/10'
                            }`}
                          >
                            {isLocked && (
                              <span className="absolute top-1 right-1 text-[8px] font-black px-1 rounded bg-[#008EFF] text-white leading-tight shadow-sm">
                                PRO
                              </span>
                            )}
                            <div className="h-4 flex items-center justify-center">
                              <QueueIndicator
                                settings={{ ...settings.queueIndicator, style: item.id }}
                                isQueued={true}
                              />
                            </div>
                            <span>{item.label}</span>
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  <div>
                    <div className="flex items-center justify-between mb-2">
                      <label className="text-sm font-medium text-white">Size</label>
                      <span className="text-xs font-bold text-[#008EFF]">
                        {settings.queueIndicator.size || 8} px
                      </span>
                    </div>

                    <input
                      type="range"
                      min="6"
                      max="20"
                      step="1"
                      value={settings.queueIndicator.size || 8}
                      onChange={(e) =>
                        updateSettings({
                          queueIndicator: {
                            ...settings.queueIndicator,
                            size: Number(e.target.value),
                          },
                        })
                      }
                      className="w-full accent-[#008EFF] cursor-pointer mb-2"
                    />

                    <div className="flex gap-2">
                      {[
                        { label: '8 px (Small)', val: 8 },
                        { label: '12 px (Medium)', val: 12 },
                        { label: '16 px (Large)', val: 16 },
                      ].map((preset) => (
                        <button
                          key={preset.val}
                          type="button"
                          onClick={() =>
                            updateSettings({
                              queueIndicator: {
                                ...settings.queueIndicator,
                                size: preset.val,
                              },
                            })
                          }
                          className={`flex-1 py-1.5 rounded-lg text-xs font-medium border transition-colors ${
                            (settings.queueIndicator.size || 8) === preset.val
                              ? 'bg-[#008EFF]/20 border-[#008EFF] text-[#00D2FF]'
                              : 'bg-white/5 border-white/5 text-[#8E8E93] hover:text-white'
                          }`}
                        >
                          {preset.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div>
                    <label className="text-sm font-medium text-white block mb-2">
                      Color Swatches & Custom Picker
                    </label>

                    <div className="flex flex-wrap items-center gap-2.5 mb-3">
                      {colorPresets.map((c) => {
                        const isSelected =
                          (settings.queueIndicator.customHex?.toLowerCase() === c.hex.toLowerCase()) ||
                          (!settings.queueIndicator.customHex &&
                            settings.queueIndicator.color.toLowerCase() === c.id);

                        return (
                          <button
                            key={c.id}
                            type="button"
                            onClick={() =>
                              updateSettings({
                                queueIndicator: {
                                  ...settings.queueIndicator,
                                  color: c.id,
                                  customHex: c.hex,
                                },
                              })
                            }
                            className={`w-9 h-9 rounded-full relative flex items-center justify-center transition-transform active:scale-90 ${
                              isSelected ? 'ring-2 ring-white ring-offset-2 ring-offset-[#1C1C1E] scale-105' : 'hover:opacity-90'
                            }`}
                            style={{ backgroundColor: c.hex }}
                            title={c.label}
                          >
                            {isSelected && (
                              <Check
                                className={`w-4 h-4 stroke-[3] ${
                                  c.hex === '#FFFFFF' ? 'text-black' : 'text-white'
                                }`}
                              />
                            )}
                          </button>
                        );
                      })}

                      <div
                        onClick={() => {
                          if (!canAccess('up_next_custom_hex')) {
                            openUpgradeModal('Custom Hex Indicator Colors');
                          }
                        }}
                        className="relative"
                      >
                        <label
                          className={`h-9 px-3 rounded-full border flex items-center gap-2 cursor-pointer transition-colors text-xs text-white ${
                            !canAccess('up_next_custom_hex')
                              ? 'bg-white/5 border-[#008EFF]/30 hover:border-[#008EFF]/60'
                              : 'bg-white/5 border-white/10 hover:bg-white/10'
                          }`}
                          title={!canAccess('up_next_custom_hex') ? 'Lohe Pro Feature' : 'Pick custom hex color'}
                        >
                          <span className="w-4 h-4 rounded-full border border-white/30" style={{ backgroundColor: settings.queueIndicator.customHex || '#008EFF' }} />
                          <span>Custom</span>
                          {!canAccess('up_next_custom_hex') ? (
                            <span className="text-[8px] font-black px-1 rounded bg-[#008EFF] text-white leading-tight shadow-sm">
                              PRO
                            </span>
                          ) : (
                            <input
                              type="color"
                              value={settings.queueIndicator.customHex || '#008EFF'}
                              onChange={(e) =>
                                updateSettings({
                                  queueIndicator: {
                                    ...settings.queueIndicator,
                                    customHex: e.target.value,
                                    color: e.target.value,
                                  },
                                })
                              }
                              className="sr-only"
                            />
                          )}
                        </label>
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      <span className="text-xs text-[#8E8E93]">Hex:</span>
                      <input
                        type="text"
                        disabled={!canAccess('up_next_custom_hex')}
                        value={settings.queueIndicator.customHex || '#008EFF'}
                        onClick={() => {
                          if (!canAccess('up_next_custom_hex')) openUpgradeModal('Custom Hex Indicator Colors');
                        }}
                        onChange={(e) => {
                          if (!canAccess('up_next_custom_hex')) return;
                          updateSettings({
                            queueIndicator: {
                              ...settings.queueIndicator,
                              customHex: e.target.value,
                              color: e.target.value,
                            },
                          });
                        }}
                        className={`w-28 px-2.5 py-1 rounded-lg border text-xs font-mono text-white focus:outline-none ${
                          !canAccess('up_next_custom_hex')
                            ? 'bg-black/20 border-white/5 cursor-pointer opacity-70'
                            : 'bg-black/40 border-[#2C2C30] focus:border-[#008EFF]'
                        }`}
                        placeholder="#008EFF"
                      />
                      {!canAccess('up_next_custom_hex') && (
                        <span className="text-[10px] text-[#00D2FF] font-semibold cursor-pointer" onClick={() => openUpgradeModal('Custom Hex Colors')}>
                          Unlock with Pro
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {/* ======================================================== */}
        {/* 4. PLAYBACK SUBPAGE                                      */}
        {/* ======================================================== */}
        {activeCategory === 'playback' && (
          <div className="space-y-6 pt-3">
            <div className="p-5 rounded-2xl bg-[#1C1C1E] border border-[#2C2C30] space-y-6">
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-base font-medium text-white">Default playback speed</label>
                  <span className="text-base font-bold text-[#008EFF]">
                    {settings.defaultPlaybackSpeed.toFixed(2)}×
                  </span>
                </div>

                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() =>
                      updateSettings({
                        defaultPlaybackSpeed: Math.max(0.5, Number((settings.defaultPlaybackSpeed - 0.05).toFixed(2))),
                      })
                    }
                    className="w-9 h-9 rounded-xl bg-white/5 hover:bg-white/10 text-white font-bold flex items-center justify-center text-sm"
                  >
                    -
                  </button>

                  <input
                    type="range"
                    min="0.5"
                    max="3.5"
                    step="0.05"
                    value={settings.defaultPlaybackSpeed}
                    onChange={(e) => updateSettings({ defaultPlaybackSpeed: Number(e.target.value) })}
                    className="flex-1 accent-[#008EFF] cursor-pointer"
                  />

                  <button
                    type="button"
                    onClick={() =>
                      updateSettings({
                        defaultPlaybackSpeed: Math.min(3.5, Number((settings.defaultPlaybackSpeed + 0.05).toFixed(2))),
                      })
                    }
                    className="w-9 h-9 rounded-xl bg-white/5 hover:bg-white/10 text-white font-bold flex items-center justify-center text-sm"
                  >
                    +
                  </button>
                </div>

                <div className="flex flex-wrap gap-1.5 mt-3">
                  {[0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0].map((s) => (
                    <button
                      key={s}
                      type="button"
                      onClick={() => updateSettings({ defaultPlaybackSpeed: s })}
                      className={`py-1.5 px-3 rounded-lg text-xs font-semibold transition-all ${
                        settings.defaultPlaybackSpeed === s
                          ? 'bg-[#008EFF] text-white shadow-sm'
                          : 'bg-white/5 text-[#8E8E93] hover:text-white hover:bg-white/10'
                      }`}
                    >
                      {s}×
                    </button>
                  ))}
                </div>
              </div>

              <div className="pt-4 border-t border-[#26262A]">
                <div className="flex items-center justify-between mb-2">
                  <label className="text-base font-medium text-white flex items-center gap-2">
                    <Rewind className="w-4 h-4 text-[#008EFF]" />
                    <span>Skip back</span>
                  </label>
                  <span className="text-sm font-bold text-[#008EFF]">
                    {settings.skipBackSeconds} seconds
                  </span>
                </div>

                <input
                  type="range"
                  min="5"
                  max="90"
                  step="5"
                  value={settings.skipBackSeconds}
                  onChange={(e) => updateSettings({ skipBackSeconds: Number(e.target.value) })}
                  className="w-full accent-[#008EFF] cursor-pointer mb-2"
                />

                <div className="flex gap-2">
                  {[5, 10, 15, 30, 45, 60].map((sec) => (
                    <button
                      key={sec}
                      type="button"
                      onClick={() => updateSettings({ skipBackSeconds: sec })}
                      className={`flex-1 py-1.5 rounded-lg text-xs font-medium border transition-colors ${
                        settings.skipBackSeconds === sec
                          ? 'bg-[#008EFF]/20 border-[#008EFF] text-[#00D2FF]'
                          : 'bg-white/5 border-white/5 text-[#8E8E93] hover:text-white'
                      }`}
                    >
                      {sec}s
                    </button>
                  ))}
                </div>
              </div>

              <div className="pt-4 border-t border-[#26262A]">
                <div className="flex items-center justify-between mb-2">
                  <label className="text-base font-medium text-white flex items-center gap-2">
                    <FastForward className="w-4 h-4 text-[#008EFF]" />
                    <span>Skip forward</span>
                  </label>
                  <span className="text-sm font-bold text-[#008EFF]">
                    {settings.skipForwardSeconds} seconds
                  </span>
                </div>

                <input
                  type="range"
                  min="5"
                  max="90"
                  step="5"
                  value={settings.skipForwardSeconds}
                  onChange={(e) => updateSettings({ skipForwardSeconds: Number(e.target.value) })}
                  className="w-full accent-[#008EFF] cursor-pointer mb-2"
                />

                <div className="flex gap-2">
                  {[10, 15, 30, 45, 60, 90].map((sec) => (
                    <button
                      key={sec}
                      type="button"
                      onClick={() => updateSettings({ skipForwardSeconds: sec })}
                      className={`flex-1 py-1.5 rounded-lg text-xs font-medium border transition-colors ${
                        settings.skipForwardSeconds === sec
                          ? 'bg-[#008EFF]/20 border-[#008EFF] text-[#00D2FF]'
                          : 'bg-white/5 border-white/5 text-[#8E8E93] hover:text-white'
                      }`}
                    >
                      {sec}s
                    </button>
                  ))}
                </div>
              </div>

              <div className="pt-4 border-t border-[#26262A]">
                <div className="flex items-center justify-between mb-1.5">
                  <div>
                    <h4 className="text-base font-medium text-white">Auto-rewind after long pause</h4>
                    <p className="text-xs text-[#8E8E93] mt-0.5">
                      Jumps backward after 5+ minutes of pause to recover listening context.
                    </p>
                  </div>
                  <span className="text-sm font-bold text-[#008EFF]">
                    {(settings.autoRewindSeconds ?? 3) === 0 ? 'Off' : `${settings.autoRewindSeconds ?? 3}s`}
                  </span>
                </div>

                <input
                  type="range"
                  min="0"
                  max="15"
                  step="1"
                  value={settings.autoRewindSeconds ?? 3}
                  onChange={(e) => updateSettings({ autoRewindSeconds: Number(e.target.value) })}
                  className="w-full accent-[#008EFF] cursor-pointer mt-2"
                />
              </div>

              <div className="pt-4 border-t border-[#26262A] flex items-center justify-between">
                <div>
                  <h4 className="text-base font-medium text-white">Mark played when finished</h4>
                  <p className="text-xs text-[#8E8E93] mt-0.5">Keeps finished episodes out of continue listening.</p>
                </div>
                <input
                  type="checkbox"
                  checked={settings.markPlayedWhenFinished ?? true}
                  onChange={(e) => updateSettings({ markPlayedWhenFinished: e.target.checked })}
                  className="w-5 h-5 accent-[#008EFF] cursor-pointer"
                />
              </div>

              <div className="pt-4 border-t border-[#26262A] flex items-center justify-between">
                <div>
                  <h4 className="text-base font-medium text-white">Skip silence by default</h4>
                  <p className="text-xs text-[#8E8E93] mt-0.5">Automatically compress quiet pauses without pitch distortion.</p>
                </div>
                <input
                  type="checkbox"
                  checked={settings.skipSilenceByDefault}
                  onChange={(e) => updateSettings({ skipSilenceByDefault: e.target.checked })}
                  className="w-5 h-5 accent-[#008EFF] cursor-pointer"
                />
              </div>

              <div className="pt-4 border-t border-[#26262A] flex items-center justify-between">
                <div>
                  <h4 className="text-base font-medium text-white flex items-center gap-1.5">
                    <Volume2 className="w-4 h-4 text-[#008EFF]" />
                    <span>Volume boost by default</span>
                  </h4>
                  <p className="text-xs text-[#8E8E93] mt-0.5">Normalize dialogue loudness and lift quiet recordings.</p>
                </div>
                <input
                  type="checkbox"
                  checked={settings.volumeBoostByDefault}
                  onChange={(e) => updateSettings({ volumeBoostByDefault: e.target.checked })}
                  className="w-5 h-5 accent-[#008EFF] cursor-pointer"
                />
              </div>

              <div className="pt-4 border-t border-[#26262A] flex items-center justify-between">
                <div>
                  <h4 className="text-base font-medium text-white flex items-center gap-1.5">
                    <Headphones className="w-4 h-4 text-[#008EFF]" />
                    <span>Resume on headset connect</span>
                  </h4>
                  <p className="text-xs text-[#8E8E93] mt-0.5">Automatically resumes playback when headphones or Bluetooth connect.</p>
                </div>
                <input
                  type="checkbox"
                  checked={settings.resumeOnConnect ?? true}
                  onChange={(e) => updateSettings({ resumeOnConnect: e.target.checked })}
                  className="w-5 h-5 accent-[#008EFF] cursor-pointer"
                />
              </div>

              <div className="pt-4 border-t border-[#26262A]">
                <div className="flex items-center justify-between mb-2">
                  <label className="text-base font-medium text-white flex items-center gap-1.5">
                    <MoonStar className="w-4 h-4 text-[#008EFF]" />
                    <span>Default sleep timer</span>
                  </label>
                  <span className="text-xs font-bold text-[#008EFF]">
                    {(settings.defaultSleepTimerMinutes || 0) === 0 ? 'Off' : `${settings.defaultSleepTimerMinutes} min`}
                  </span>
                </div>

                <div className="grid grid-cols-3 sm:grid-cols-6 gap-2">
                  {[
                    { label: 'Off', val: 0 },
                    { label: '15m', val: 15 },
                    { label: '30m', val: 30 },
                    { label: '45m', val: 45 },
                    { label: '60m', val: 60 },
                    { label: 'End ep', val: -1 },
                  ].map((item) => (
                    <button
                      key={item.val}
                      type="button"
                      onClick={() => updateSettings({ defaultSleepTimerMinutes: item.val })}
                      className={`py-2 rounded-xl text-xs font-semibold transition-all ${
                        (settings.defaultSleepTimerMinutes || 0) === item.val
                          ? 'bg-[#008EFF] text-white shadow-sm'
                          : 'bg-white/5 text-[#8E8E93] hover:text-white hover:bg-white/10'
                      }`}
                    >
                      {item.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ======================================================== */}
        {/* 5. FLOATING PLAYER & BATTERY SUBPAGE                     */}
        {/* ======================================================== */}
        {activeCategory === 'floating' && (
          <div className="space-y-5 pt-3">
            {/* Pro Status Banner for Floating Overlay */}
            {!canAccess('floating_player') && (
              <div
                onClick={() => {
                  if ((settings.appEdition || (settings.appFlavor === 'foss' ? 'foss' : 'play')) === 'foss') {
                    notify('Floating overlay is unavailable in the FOSS edition.');
                  } else {
                    openUpgradeModal('Floating Mini Player Overlay');
                  }
                }}
                className="p-4 rounded-2xl bg-[#008EFF]/10 border border-[#008EFF]/30 flex items-center justify-between cursor-pointer hover:bg-[#008EFF]/15 transition-all group"
              >
                <div className="flex items-center gap-3 min-w-0 pr-2">
                  <span className="w-8 h-8 rounded-xl bg-[#008EFF] text-white flex items-center justify-center font-bold text-xs shadow-md shrink-0">
                    PRO
                  </span>
                  <div className="min-w-0">
                    <h4 className="text-sm font-bold text-white group-hover:text-[#00D2FF] transition-colors truncate">
                      Floating Overlay is a Pro Feature
                    </h4>
                    <p className="text-xs text-[#8E8E93] mt-0.5">
                      {(settings.appEdition || (settings.appFlavor === 'foss' ? 'foss' : 'play')) === 'foss'
                        ? 'Unavailable in FOSS standalone builds. Standard in-app mini-player remains free.'
                        : 'Display persistent controls over other apps. Your saved size and transparency are preserved.'}
                    </p>
                  </div>
                </div>
                {(settings.appEdition || (settings.appFlavor === 'foss' ? 'foss' : 'play')) !== 'foss' && (
                  <button
                    type="button"
                    className="px-3.5 py-1.5 rounded-xl bg-[#008EFF] hover:bg-[#00A8FF] text-white text-xs font-bold shadow-sm shrink-0"
                  >
                    Subscribe (US$9.99/yr)
                  </button>
                )}
              </div>
            )}

            <div className="p-5 rounded-2xl bg-[#1C1C1E] border border-[#2C2C30] space-y-5">
              {/* Display over other apps Toggle */}
              <div
                className="flex items-center justify-between cursor-pointer"
                onClick={() => {
                  if (!canAccess('floating_player')) {
                    openUpgradeModal('Floating Mini Player Overlay');
                  }
                }}
              >
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="text-base font-medium text-white">Display over other apps</h3>
                    {!canAccess('floating_player') && (
                      <span className="px-1.5 py-0.2 rounded text-[10px] font-bold bg-[#008EFF]/15 text-[#00D2FF] border border-[#008EFF]/30">
                        PRO
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-[#8E8E93] mt-0.5">
                    Show persistent floating playback controls outside Lohe.
                  </p>
                </div>
                <input
                  type="checkbox"
                  disabled={!canAccess('floating_player')}
                  checked={canAccess('floating_player') ? (settings.floatingPlayerEnabled ?? false) : false}
                  onChange={(e) => {
                    if (!canAccess('floating_player')) {
                      openUpgradeModal('Floating Mini Player Overlay');
                      return;
                    }
                    updateSettings({ floatingPlayerEnabled: e.target.checked });
                  }}
                  className={`w-5 h-5 accent-[#008EFF] ${!canAccess('floating_player') ? 'opacity-60 cursor-pointer' : 'cursor-pointer'}`}
                />
              </div>

              {/* Floating Button Size */}
              <div className="pt-4 border-t border-[#26262A]">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <label className="text-sm font-medium text-white">Floating Button Size</label>
                    {!canAccess('floating_player') && (
                      <span className="text-[9px] font-bold px-1.5 py-0.2 rounded bg-[#008EFF]/15 text-[#00D2FF] border border-[#008EFF]/30">
                        PRO
                      </span>
                    )}
                  </div>
                  {!canAccess('floating_player') && (
                    <span className="text-[11px] text-[#8E8E93]">
                      Saved: {settings.floatingSizeDp || 56} dp
                    </span>
                  )}
                </div>

                <div className="grid grid-cols-4 gap-2">
                  {[48, 56, 72, 88].map((dp) => (
                    <button
                      key={dp}
                      type="button"
                      onClick={() => {
                        if (!canAccess('floating_player')) {
                          openUpgradeModal('Floating Button Size');
                          return;
                        }
                        updateSettings({ floatingSizeDp: dp });
                      }}
                      className={`py-2.5 rounded-xl text-xs font-bold transition-all ${
                        (settings.floatingSizeDp || 56) === dp
                          ? 'bg-[#008EFF] text-white shadow-md'
                          : 'bg-white/5 text-white/70 hover:bg-white/10'
                      }`}
                    >
                      {dp} dp {dp === 56 && '(Default)'}
                    </button>
                  ))}
                </div>
              </div>

              {/* Transparency Slider */}
              <div className="pt-4 border-t border-[#26262A]">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <label className="text-sm font-medium text-white">Transparency</label>
                    {!canAccess('floating_player') && (
                      <span className="text-[9px] font-bold px-1.5 py-0.2 rounded bg-[#008EFF]/15 text-[#00D2FF] border border-[#008EFF]/30">
                        PRO
                      </span>
                    )}
                  </div>
                  <span className="text-xs text-[#008EFF] font-bold">
                    {settings.floatingTransparencyPct || 25}%
                  </span>
                </div>
                <input
                  type="range"
                  min="0"
                  max="75"
                  step="5"
                  value={settings.floatingTransparencyPct || 25}
                  onClick={() => {
                    if (!canAccess('floating_player')) {
                      openUpgradeModal('Floating Player Transparency');
                    }
                  }}
                  onChange={(e) => {
                    if (!canAccess('floating_player')) {
                      openUpgradeModal('Floating Player Transparency');
                      return;
                    }
                    updateSettings({ floatingTransparencyPct: Number(e.target.value) });
                  }}
                  className={`w-full accent-[#008EFF] ${!canAccess('floating_player') ? 'cursor-pointer opacity-75' : 'cursor-pointer'}`}
                />
                <div className="flex justify-between text-[11px] text-[#8E8E93] mt-1">
                  <span>Opaque (0%)</span>
                  <span>Max subtle (75%)</span>
                </div>
              </div>

              <div className="pt-4 border-t border-[#26262A] space-y-3">
                <div className="flex items-center justify-between">
                  <h4 className="text-base font-medium text-white flex items-center gap-2">
                    <Battery className="w-5 h-5 text-[#008EFF]" />
                    <span>Battery Setup</span>
                  </h4>
                  <span
                    className={`px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1.5 ${
                      (settings.batteryOptimizationStatus || 'unrestricted') === 'unrestricted'
                        ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30'
                        : 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
                    }`}
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-current" />
                    <span>
                      {(settings.batteryOptimizationStatus || 'unrestricted') === 'unrestricted'
                        ? 'Unrestricted (Optimal)'
                        : 'Optimized (May sleep)'}
                    </span>
                  </span>
                </div>

                <p className="text-xs text-[#8E8E93] leading-relaxed">
                  Android background limits may kill floating controls during screen-off audio playback unless Lohe is granted an exemption from battery optimization.
                </p>

                <button
                  type="button"
                  onClick={() => setShowBatteryModal(true)}
                  className="w-full py-3 px-4 rounded-xl bg-[#008EFF]/15 hover:bg-[#008EFF]/25 border border-[#008EFF]/30 text-[#00D2FF] text-xs font-bold flex items-center justify-center gap-2 transition-colors active:scale-98"
                >
                  <ExternalLink className="w-4 h-4" />
                  <span>Open Android Battery Settings</span>
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ======================================================== */}
        {/* 6. ARCHIVING SUBPAGE                                     */}
        {/* ======================================================== */}
        {activeCategory === 'archiving' && (
          <div className="space-y-5 pt-3">
            <div className="p-5 rounded-2xl bg-[#1C1C1E] border border-[#2C2C30] space-y-5">
              <div>
                <h3 className="text-base font-medium text-white flex items-center gap-2">
                  <Archive className="w-4 h-4 text-[#008EFF]" />
                  <span>Archive delay</span>
                </h3>
                <p className="text-xs text-[#8E8E93] mt-1 leading-relaxed">
                  When an episode finishes, Lohe marks it played and removes it from Up Next. Downloaded files remain available for your chosen delay before storage is cleaned up.
                </p>

                <div className="space-y-2 pt-3">
                  {[
                    { label: 'Immediately', hours: 0 },
                    { label: '24 hours (default)', hours: 24 },
                    { label: '48 hours', hours: 48 },
                    { label: '7 days', hours: 168 },
                    { label: 'Never automatically delete', hours: -1 },
                  ].map((opt) => (
                    <button
                      key={opt.hours}
                      type="button"
                      onClick={() => updateSettings({ archiveDelayHours: opt.hours })}
                      className={`w-full flex items-center justify-between p-3.5 rounded-xl border text-sm font-medium transition-all ${
                        settings.archiveDelayHours === opt.hours
                          ? 'bg-[#008EFF]/15 border-[#008EFF] text-[#00D2FF]'
                          : 'bg-black/20 border-white/5 text-white/80 hover:bg-black/30'
                      }`}
                    >
                      <span>{opt.label}</span>
                      {settings.archiveDelayHours === opt.hours && <Check className="w-4 h-4 text-[#008EFF]" />}
                    </button>
                  ))}
                </div>
              </div>

              <div className="pt-4 border-t border-[#26262A] flex items-center justify-between">
                <div>
                  <h4 className="text-base font-medium text-white">Keep starred episodes</h4>
                  <p className="text-xs text-[#8E8E93] mt-0.5">
                    Never automatically delete or archive episodes marked with a star.
                  </p>
                </div>
                <input
                  type="checkbox"
                  checked={settings.keepStarredEpisodes ?? true}
                  onChange={(e) => updateSettings({ keepStarredEpisodes: e.target.checked })}
                  className="w-5 h-5 accent-[#008EFF] cursor-pointer"
                />
              </div>

              <div className="pt-4 border-t border-[#26262A] flex items-center justify-between">
                <div>
                  <h4 className="text-base font-medium text-white">Delete downloaded audio on archive</h4>
                  <p className="text-xs text-[#8E8E93] mt-0.5">
                    Frees device storage when an episode is archived while preserving played records.
                  </p>
                </div>
                <input
                  type="checkbox"
                  checked={settings.deleteAudioOnArchive ?? true}
                  onChange={(e) => updateSettings({ deleteAudioOnArchive: e.target.checked })}
                  className="w-5 h-5 accent-[#008EFF] cursor-pointer"
                />
              </div>

              <div className="pt-4 border-t border-[#26262A] flex items-center justify-between">
                <div>
                  <h4 className="text-base font-medium text-white">Archive on manual mark as played</h4>
                  <p className="text-xs text-[#8E8E93] mt-0.5">
                    Immediately trigger archive rules whenever you tap Mark Played.
                  </p>
                </div>
                <input
                  type="checkbox"
                  checked={settings.archiveOnManualPlayed ?? true}
                  onChange={(e) => updateSettings({ archiveOnManualPlayed: e.target.checked })}
                  className="w-5 h-5 accent-[#008EFF] cursor-pointer"
                />
              </div>

              <div className="pt-4 border-t border-[#26262A] flex items-center justify-between">
                <div>
                  <h4 className="text-base font-medium text-white">Include archived in Signal stats</h4>
                  <p className="text-xs text-[#8E8E93] mt-0.5">
                    Count actual listened hours from archived episodes towards your Signal dial.
                  </p>
                </div>
                <input
                  type="checkbox"
                  checked={settings.includeArchivedInSignal ?? true}
                  onChange={(e) => updateSettings({ includeArchivedInSignal: e.target.checked })}
                  className="w-5 h-5 accent-[#008EFF] cursor-pointer"
                />
              </div>
            </div>
          </div>
        )}

        {/* ======================================================== */}
        {/* 7. DOWNLOADS SUBPAGE                                     */}
        {/* ======================================================== */}
        {activeCategory === 'downloads' && (
          <div className="space-y-5 pt-3">
            <div className="p-5 rounded-2xl bg-[#1C1C1E] border border-[#2C2C30] space-y-5">
              <div>
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-base font-medium text-white flex items-center gap-2">
                    <HardDrive className="w-4 h-4 text-[#008EFF]" />
                    <span>Device Storage Overview</span>
                  </h3>
                  <span className="text-xs font-bold text-[#008EFF]">{formattedStorage} used</span>
                </div>

                <div className="h-3 w-full bg-white/10 rounded-full overflow-hidden flex mb-2">
                  <div className="bg-[#008EFF] h-full" style={{ width: '4%' }} title="Lohe Audio" />
                  <div className="bg-purple-500/50 h-full" style={{ width: '38%' }} title="Apps & System" />
                  <div className="bg-transparent h-full flex-1" title="Free Space" />
                </div>

                <div className="flex items-center justify-between text-[11px] text-[#8E8E93]">
                  <div className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-[#008EFF]" />
                    <span>Lohe ({downloadedEpisodes.length} episodes)</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-purple-400" />
                    <span>System (42.4 GB)</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-white/30" />
                    <span>Free (84.6 GB)</span>
                  </div>
                </div>
              </div>

              <div className="pt-4 border-t border-[#26262A] space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <h4 className="text-base font-medium text-white flex items-center gap-1.5">
                      <DownloadCloud className="w-4 h-4 text-[#008EFF]" />
                      <span>Automatic downloads for Up Next</span>
                    </h4>
                    <p className="text-xs text-[#8E8E93] mt-0.5">
                      Pre-fetch upcoming episodes in your queue for offline listening.
                    </p>
                  </div>
                  <input
                    type="checkbox"
                    checked={(settings.autoDownloadQueueCount ?? 5) > 0}
                    onChange={(e) =>
                      updateSettings({
                        autoDownloadQueueCount: e.target.checked ? 5 : 0,
                      })
                    }
                    className="w-5 h-5 accent-[#008EFF] cursor-pointer"
                  />
                </div>

                {(settings.autoDownloadQueueCount ?? 5) > 0 && (
                  <div className="p-3.5 rounded-xl bg-black/30 border border-[#2C2C30]">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs font-semibold text-white">Pre-download Queue Depth</span>
                      <span className="text-xs font-bold text-[#008EFF]">
                        Next {settings.autoDownloadQueueCount ?? 5} episodes
                      </span>
                    </div>
                    <input
                      type="range"
                      min="1"
                      max="15"
                      step="1"
                      value={settings.autoDownloadQueueCount ?? 5}
                      onChange={(e) =>
                        updateSettings({ autoDownloadQueueCount: Number(e.target.value) })}
                      className="w-full accent-[#008EFF] cursor-pointer"
                    />
                    <div className="flex justify-between text-[10px] text-[#8E8E93] mt-1">
                      <span>Next 1 episode</span>
                      <span>Next 15 episodes</span>
                    </div>
                  </div>
                )}
              </div>

              <div className="pt-4 border-t border-[#26262A] flex items-center justify-between">
                <div>
                  <h4 className="text-base font-medium text-white flex items-center gap-1.5">
                    <Wifi className="w-4 h-4 text-[#008EFF]" />
                    <span>Download over Wi-Fi only</span>
                  </h4>
                  <p className="text-xs text-[#8E8E93] mt-0.5">
                    Prevents episode downloads from consuming mobile cellular data.
                  </p>
                </div>
                <input
                  type="checkbox"
                  checked={settings.downloadWifiOnly ?? true}
                  onChange={(e) => updateSettings({ downloadWifiOnly: e.target.checked })}
                  className="w-5 h-5 accent-[#008EFF] cursor-pointer"
                />
              </div>

              <div className="pt-4 border-t border-[#26262A] flex items-center justify-between">
                <div>
                  <h4 className="text-base font-medium text-white">Auto-download newly released episodes</h4>
                  <p className="text-xs text-[#8E8E93] mt-0.5">
                    Automatically download new releases for opted-in subscribed podcasts.
                  </p>
                </div>
                <input
                  type="checkbox"
                  checked={settings.autoDownloadNewEpisodes ?? false}
                  onChange={(e) => updateSettings({ autoDownloadNewEpisodes: e.target.checked })}
                  className="w-5 h-5 accent-[#008EFF] cursor-pointer"
                />
              </div>

              <div className="pt-4 border-t border-[#26262A]">
                <label className="text-sm font-medium text-white block mb-2">Storage Location</label>
                <div className="space-y-2">
                  {[
                    {
                      id: 'internal',
                      label: 'Internal Storage',
                      path: '/data/user/0/com.lohe.app/files/audio',
                      free: '84.6 GB available',
                    },
                    {
                      id: 'sdcard',
                      label: 'External SD Card',
                      path: '/storage/emulated/0/Lohe/Audio',
                      free: '128.0 GB available',
                    },
                  ].map((loc) => (
                    <button
                      key={loc.id}
                      type="button"
                      onClick={() =>
                        updateSettings({ storageLocation: loc.id as 'internal' | 'sdcard' })}
                      className={`w-full flex items-center justify-between p-3 rounded-xl border text-left transition-all ${
                        (settings.storageLocation || 'internal') === loc.id
                          ? 'bg-[#008EFF]/15 border-[#008EFF] text-white'
                          : 'bg-black/20 border-white/5 text-[#8E8E93] hover:text-white'
                      }`}
                    >
                      <div>
                        <p className="text-xs font-bold text-white">{loc.label}</p>
                        <p className="text-[11px] text-[#8E8E93] font-mono mt-0.5">{loc.path}</p>
                      </div>
                      <span className="text-[11px] text-[#008EFF] font-semibold">{loc.free}</span>
                    </button>
                  ))}
                </div>
              </div>

              <div className="pt-4 border-t border-[#26262A] space-y-2.5">
                <button
                  type="button"
                  onClick={() => notify('Temporary audio cache cleared (0 MB).')}
                  className="w-full py-2.5 px-4 rounded-xl bg-white/5 hover:bg-white/10 text-white text-xs font-semibold flex items-center justify-center gap-2 transition-colors"
                >
                  <Trash2 className="w-3.5 h-3.5 text-[#8E8E93]" />
                  <span>Clean Download Cache</span>
                </button>

                <button
                  type="button"
                  onClick={() => setShowClearConfirm(true)}
                  className="w-full py-2.5 px-4 rounded-xl bg-red-500/15 hover:bg-red-500/25 text-red-400 text-xs font-bold flex items-center justify-center gap-2 transition-colors"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                  <span>Clear All Downloaded Audio</span>
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* ======================================================== */}
      {/* 8. BATTERY SETTINGS DIALOG                               */}
      {/* ======================================================== */}
      {showBatteryModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4">
          <div className="w-full max-w-md p-6 rounded-[24px] bg-[#1C1C1E] border border-[#2C2C30] text-white shadow-2xl">
            <div className="flex items-center justify-between pb-3 border-b border-[#2C2C30] mb-4">
              <div className="flex items-center gap-2">
                <Battery className="w-5 h-5 text-[#008EFF]" />
                <h3 className="text-base font-bold">Android Battery Setup</h3>
              </div>
              <button
                type="button"
                onClick={() => setShowBatteryModal(false)}
                className="w-8 h-8 rounded-full bg-white/10 hover:bg-white/20 flex items-center justify-center text-sm"
              >
                ✕
              </button>
            </div>

            <div className="space-y-4 text-xs text-[#8E8E93] leading-relaxed">
              <p>
                To keep floating player controls and background playback responsive without being killed by Android Doze:
              </p>

              <div className="p-3.5 rounded-xl bg-black/40 border border-[#2C2C30] text-white space-y-2">
                <p className="font-semibold text-[#00D2FF]">Required Steps on Android:</p>
                <ol className="list-decimal pl-4 space-y-1 text-xs text-[#A1A1A6]">
                  <li>Open Android <strong className="text-white">Settings</strong></li>
                  <li>Tap <strong className="text-white">Apps</strong> → Select <strong className="text-white">Lohe</strong></li>
                  <li>Tap <strong className="text-white">App battery usage</strong> (or Battery)</li>
                  <li>Select <strong className="text-white text-[#00D2FF]">Unrestricted</strong></li>
                </ol>
              </div>

              <div className="pt-2 flex items-center justify-between">
                <span className="text-white font-medium">Current Status in Lohe:</span>
                <button
                  type="button"
                  onClick={() => {
                    const next =
                      (settings.batteryOptimizationStatus || 'unrestricted') === 'unrestricted'
                        ? 'optimized'
                        : 'unrestricted';
                    updateSettings({ batteryOptimizationStatus: next });
                    notify(`Battery status updated to ${next}.`);
                  }}
                  className={`px-3 py-1 rounded-full text-xs font-bold transition-all ${
                    (settings.batteryOptimizationStatus || 'unrestricted') === 'unrestricted'
                      ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                      : 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                  }`}
                >
                  {(settings.batteryOptimizationStatus || 'unrestricted') === 'unrestricted'
                    ? '● Unrestricted'
                    : '⚠️ Optimized'}
                </button>
              </div>
            </div>

            <div className="mt-6 flex justify-end">
              <button
                type="button"
                onClick={() => setShowBatteryModal(false)}
                className="px-5 py-2.5 rounded-xl bg-[#008EFF] hover:bg-[#00A8FF] text-white text-xs font-bold shadow-md"
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ======================================================== */}
      {/* 9. CLEAR STORAGE CONFIRMATION DIALOG                     */}
      {/* ======================================================== */}
      {showClearConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4">
          <div className="w-full max-w-sm p-6 rounded-[24px] bg-[#1C1C1E] border border-[#2C2C30] text-white shadow-2xl">
            <h3 className="text-base font-bold mb-2">Delete all offline audio?</h3>
            <p className="text-xs text-[#8E8E93] mb-5 leading-relaxed">
              This will remove {downloadedEpisodes.length} local audio files ({formattedStorage}) from your device. Your playlists, subscriptions, and playback history will be preserved.
            </p>
            <div className="flex justify-end gap-2.5">
              <button
                type="button"
                onClick={() => setShowClearConfirm(false)}
                className="px-4 py-2 rounded-xl text-xs font-semibold text-white/70 hover:text-white"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowClearConfirm(false);
                  notify('Cleared all downloaded audio files.');
                }}
                className="px-4 py-2 rounded-xl bg-red-500 hover:bg-red-600 text-white text-xs font-bold shadow"
              >
                Delete All
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ======================================================== */}
      {/* 10. OFFLINE PRIVACY POLICY MODAL                         */}
      {/* ======================================================== */}
      {showPrivacyPolicy && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4">
          <div className="w-full max-w-lg max-h-[85vh] flex flex-col rounded-[24px] bg-[#1C1C1E] border border-[#2C2C30] text-white shadow-2xl overflow-hidden">
            <div className="p-4 border-b border-[#2C2C30] flex items-center justify-between bg-[#1C1C1E]/95">
              <div className="flex items-center gap-2">
                <Shield className="w-4 h-4 text-[#008EFF]" />
                <h3 className="text-base font-bold text-white">Privacy Policy — Lohe</h3>
              </div>
              <button
                type="button"
                onClick={() => setShowPrivacyPolicy(false)}
                className="w-8 h-8 flex items-center justify-center rounded-full bg-white/10 hover:bg-white/20 text-white"
                aria-label="Close"
              >
                ✕
              </button>
            </div>
            <div className="p-6 overflow-y-auto text-xs leading-relaxed text-[#A1A1A6] space-y-4 font-sans">
              <p className="text-[11px] text-[#A1A1A6]/70">
                Last updated: October 31, 2026 • Atomic 22 Industries
              </p>

              <div>
                <h4 className="font-bold text-white text-sm">1. Your library and preferences</h4>
                <p className="mt-1">
                  Lohe stores subscriptions, feed addresses, episode metadata, listening positions and history, playlists, Up Next, downloaded audio, and preferences strictly on your device. These are used to organize your library, resume playback, refresh subscribed feeds, and manage downloads. Lohe does not operate an account or library-sync server and does not automatically upload your listening history to the developer.
                </p>
              </div>

              <div>
                <h4 className="font-bold text-white text-sm">2. Zero Advertising, Zero Analytics</h4>
                <p className="mt-1">
                  You do not need an account. Lohe does not include advertising, analytics, advertising identifiers, or an automatic crash-reporting service. It does not request contacts, location, camera, microphone, or access to an inventory of your installed apps.
                </p>
              </div>

              <div>
                <h4 className="font-bold text-white text-sm">3. Direct Requests to Podcast Services</h4>
                <p className="mt-1">
                  Searching for podcasts connects directly to Apple's public iTunes catalog. Subscribing, refreshing feeds, viewing artwork, and streaming audio connects directly to publishers' servers without passing through any intermediate server.
                </p>
              </div>

              <div>
                <h4 className="font-bold text-white text-sm">4. Contact Information</h4>
                <p className="mt-1">
                  For privacy inquiries or technical questions, contact Atomic 22 Industries at <span className="text-[#008EFF]">atomic22industries@gmail.com</span>.
                </p>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SettingsScreen;
