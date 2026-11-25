import React from 'react';
import { Home, Library, ListMusic, ListOrdered, Compass } from 'lucide-react';
import { usePlayer } from '../context/PlayerContext';

export type TabType = 'home' | 'podcasts' | 'queue' | 'playlists' | 'discover';

interface LoheBottomBarProps {
  currentTab: TabType;
  onSelectTab: (tab: TabType) => void;
}

export const LoheBottomBar: React.FC<LoheBottomBarProps> = ({ currentTab, onSelectTab }) => {
  const { queue, podcasts } = usePlayer();

  const totalUnread = podcasts
    .filter((p) => p.isSubscribed)
    .reduce((sum, p) => sum + (p.unreadCount || 0), 0);

  const tabs: { id: TabType; label: string; icon: React.ComponentType<{ className?: string }>; badge?: number }[] = [
    { id: 'home', label: 'Home', icon: Home },
    { id: 'podcasts', label: 'Podcasts', icon: Library, badge: totalUnread > 0 ? totalUnread : undefined },
    { id: 'discover', label: 'Discover', icon: Compass },
    { id: 'playlists', label: 'Playlists', icon: ListOrdered },
    { id: 'queue', label: 'Up Next', icon: ListMusic, badge: queue.length > 0 ? queue.length : undefined },
  ];

  return (
    <nav
      className="fixed bottom-0 left-0 right-0 z-40 h-[68px] max-w-2xl mx-auto px-4 bg-[#121824]/95 dark:bg-[#121824]/95 border-t border-[#354966]/40 backdrop-blur-xl flex items-center justify-around select-none"
      aria-label="Lohe navigation"
    >
      {tabs.map((tab) => {
        const Icon = tab.icon;
        const isActive = currentTab === tab.id;

        return (
          <button
            key={tab.id}
            type="button"
            onClick={() => onSelectTab(tab.id)}
            className={`relative min-w-[56px] min-h-[48px] flex flex-col items-center justify-center gap-0.5 rounded-2xl transition-all duration-200 active:scale-95 ${
              isActive
                ? 'text-[#008EFF] font-semibold'
                : 'text-[#A1A1A6] hover:text-[#F4F4F5]'
            }`}
          >
            <div className="relative">
              <Icon className={`w-5 h-5 transition-transform ${isActive ? 'scale-105' : ''}`} />
              {tab.badge !== undefined && (
                <span className="absolute -top-1.5 -right-3 px-1.5 py-0.2 bg-[#008EFF] text-white text-[10px] font-bold rounded-full shadow-sm">
                  {tab.badge > 99 ? '99+' : tab.badge}
                </span>
              )}
            </div>
            <span className="text-[11px] tracking-tight">{tab.label}</span>
            {isActive && (
              <span className="w-1 h-1 rounded-full bg-[#008EFF] shadow-[0_0_4px_#008EFF]" />
            )}
          </button>
        );
      })}
    </nav>
  );
};
