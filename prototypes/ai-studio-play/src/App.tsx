import React, { useState } from 'react';
import { PlayerProvider } from './context/PlayerContext';
import { LoheBottomBar, TabType } from './components/LoheBottomBar';
import { NowPlayingBar } from './components/NowPlayingBar';
import { PlayerModal } from './components/PlayerModal';
import { EpisodeNotesSheet } from './components/EpisodeNotesSheet';
import { HomeScreen } from './screens/HomeScreen';
import { PodcastsScreen } from './screens/PodcastsScreen';
import { QueueScreen } from './screens/QueueScreen';
import { PlaylistsScreen } from './screens/PlaylistsScreen';
import { DiscoverScreen } from './screens/DiscoverScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { LoheWordmark } from './components/LoheWordmark';
import { GooglePlayUpgradeModal } from './components/GooglePlayUpgradeModal';
import { usePlayer } from './context/PlayerContext';
import { Settings, Crown, Download } from 'lucide-react';

const AppContent: React.FC = () => {
  const [currentTab, setCurrentTab] = useState<TabType>('home');
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const { isPro, openUpgradeModal } = usePlayer();

  return (
    <div className="flex flex-col h-screen w-screen overflow-hidden bg-[#121214] text-[#F4F4F5] font-sans">
      {/* Top App Header with Lohe Wordmark (25% larger), Version tag underneath, and Settings Gear */}
      <header className="h-16 px-4 flex items-center justify-between z-30 shrink-0 max-w-2xl w-full mx-auto select-none">
        <div
          onClick={() => setCurrentTab('home')}
          className="flex flex-col items-center cursor-pointer transition-transform active:scale-95 pt-0.5 leading-none"
        >
          <LoheWordmark className="h-10 sm:h-11 w-auto" />
          <span className="text-[7px] font-extralight text-white/40 font-mono tracking-widest text-center -mt-2 sm:-mt-2.5 select-none leading-none">
            v0.11.1
          </span>
        </div>

        <div className="flex items-center gap-2.5">
          {/* Pro Status Badge / Upgrade Button matching Lohe color scheme */}
          {isPro ? (
            <span className="px-2 py-0.5 rounded-full text-[10px] font-bold tracking-wider uppercase bg-[#008EFF] text-white shadow-sm flex items-center gap-1">
              <Crown className="w-3 h-3 text-white" />
              <span>PRO</span>
            </span>
          ) : (
            <button
              type="button"
              onClick={() => openUpgradeModal('Lohe Pro Additions')}
              className="px-2.5 py-1 rounded-full text-[11px] font-semibold bg-[#008EFF]/15 hover:bg-[#008EFF]/25 text-[#00D2FF] border border-[#008EFF]/30 shadow-sm flex items-center gap-1.5 active:scale-95 transition-all"
            >
              <Crown className="w-3.5 h-3.5 text-[#00D2FF]" />
              <span>Get Pro</span>
            </button>
          )}

          {/* Direct Download Source Code ZIP Link for immediate browser download */}
          <a
            href="./lohe-source-code.zip"
            download="lohe-source-code.zip"
            className="w-9 h-9 flex items-center justify-center rounded-xl text-[#A1A1A6] hover:text-[#00D2FF] hover:bg-[#008EFF]/10 active:scale-95 transition-all border border-transparent hover:border-[#008EFF]/30"
            title="Download pure source code ZIP archive (84 KB)"
            aria-label="Download Source Code ZIP"
          >
            <Download className="w-4 h-4" />
          </a>

          {/* Top-Right Settings Gear */}
          <button
            type="button"
            onClick={() => setIsSettingsOpen(true)}
            className="w-9 h-9 flex items-center justify-center rounded-xl text-[#A1A1A6] hover:text-white hover:bg-white/10 active:scale-95 transition-all"
            title="Settings"
            aria-label="Settings"
          >
            <Settings className="w-5 h-5" />
          </button>
        </div>
      </header>

      {/* Main Screen Views */}
      <main className="flex-1 overflow-hidden flex flex-col relative">
        {currentTab === 'home' && (
          <HomeScreen
            onNavigate={(tab) => setCurrentTab(tab)}
            onOpenSettings={() => setIsSettingsOpen(true)}
          />
        )}
        {currentTab === 'podcasts' && <PodcastsScreen />}
        {currentTab === 'queue' && (
          <QueueScreen onOpenSettings={() => setIsSettingsOpen(true)} />
        )}
        {currentTab === 'playlists' && (
          <PlaylistsScreen onOpenSettings={() => setIsSettingsOpen(true)} />
        )}
        {currentTab === 'discover' && <DiscoverScreen />}
      </main>

      {/* Persistent Mini Player (NowPlayingBar) */}
      <NowPlayingBar />

      {/* Persistent Bottom Bar */}
      <LoheBottomBar currentTab={currentTab} onSelectTab={setCurrentTab} />

      {/* Full-Screen Now Playing Modal */}
      <PlayerModal />

      {/* Episode Notes Bottom Sheet */}
      <EpisodeNotesSheet />

      {/* Settings Modal */}
      {isSettingsOpen && <SettingsScreen onClose={() => setIsSettingsOpen(false)} />}

      {/* Google Play Billing In-App Upgrade Modal */}
      <GooglePlayUpgradeModal />
    </div>
  );
};

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

class ErrorBoundary extends React.Component<{ children: React.ReactNode }, ErrorBoundaryState> {
  constructor(props: { children: React.ReactNode }) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: any) {
    console.error("ErrorBoundary caught:", error, errorInfo);
  }

  handleReset = () => {
    localStorage.clear();
    window.location.reload();
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex flex-col items-center justify-center min-h-screen bg-[#121214] text-[#F4F4F5] p-6 text-center">
          <img src="/assets/kani.png" alt="Kani" className="w-24 h-24 mb-4 object-contain" />
          <h2 className="text-xl font-bold font-serif mb-2">Something went wrong</h2>
          <p className="text-xs text-[#b0bfd2] max-w-sm mb-4">
            {this.state.error?.message || "An unexpected error occurred."}
          </p>
          <button
            type="button"
            onClick={this.handleReset}
            className="px-5 py-2.5 rounded-full bg-[#008EFF] text-xs font-bold text-white hover:bg-[#00A8FF] transition-colors"
          >
            Reset Library and Reload
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}

export const App: React.FC = () => {
  return (
    <ErrorBoundary>
      <PlayerProvider>
        <AppContent />
      </PlayerProvider>
    </ErrorBoundary>
  );
};

export default App;
