import React from 'react';
import { QueueIndicatorSettings } from '../types';
import { ListMusic } from 'lucide-react';

interface QueueIndicatorProps {
  settings: QueueIndicatorSettings;
  isQueued: boolean;
}

// Map named presets to hex values if not already hex
const COLOR_MAP: Record<string, string> = {
  blue: '#008EFF',
  cyan: '#00D2FF',
  teal: '#14B8A6',
  amber: '#FF9F0A',
  purple: '#A855F7',
  coral: '#F43F5E',
  emerald: '#10B981',
  white: '#FFFFFF',
};

export const QueueIndicator: React.FC<QueueIndicatorProps> = ({ settings, isQueued }) => {
  if (!settings.enabled || !isQueued) return null;

  const resolvedColor =
    settings.customHex || COLOR_MAP[settings.color.toLowerCase()] || settings.color || '#008EFF';
  const sizePixels = settings.size || 8;

  if (settings.style === 'dot') {
    return (
      <span
        style={{
          width: `${sizePixels}px`,
          height: `${sizePixels}px`,
          backgroundColor: resolvedColor,
        }}
        className="inline-block rounded-full shrink-0 shadow-sm transition-all"
        title="In Up Next queue"
      />
    );
  }

  if (settings.style === 'ring') {
    return (
      <span
        style={{
          width: `${sizePixels}px`,
          height: `${sizePixels}px`,
          borderColor: resolvedColor,
          borderWidth: sizePixels >= 14 ? '2.5px' : '2px',
        }}
        className="inline-block rounded-full shrink-0 border bg-transparent shadow-sm transition-all"
        title="In Up Next queue"
      />
    );
  }

  if (settings.style === 'pill') {
    return (
      <span
        style={{
          width: `${Math.max(3, Math.round(sizePixels * 0.4))}px`,
          height: `${Math.max(10, sizePixels + 4)}px`,
          backgroundColor: resolvedColor,
        }}
        className="inline-block rounded-full shrink-0 shadow-sm transition-all"
        title="In Up Next queue"
      />
    );
  }

  if (settings.style === 'diamond') {
    const dim = Math.max(6, Math.round(sizePixels * 0.85));
    return (
      <span
        style={{
          width: `${dim}px`,
          height: `${dim}px`,
          backgroundColor: resolvedColor,
        }}
        className="inline-block rotate-45 shrink-0 shadow-sm rounded-[1px] transition-all"
        title="In Up Next queue"
      />
    );
  }

  if (settings.style === 'glow') {
    return (
      <span
        style={{
          width: `${sizePixels}px`,
          height: `${sizePixels}px`,
          backgroundColor: resolvedColor,
          boxShadow: `0 0 10px ${resolvedColor}`,
        }}
        className="inline-block rounded-full shrink-0 animate-pulse transition-all"
        title="In Up Next queue"
      />
    );
  }

  // 'icon' style (ListMusic queue glyph)
  const iconSize = Math.max(12, sizePixels + 2);
  return (
    <ListMusic
      style={{
        width: `${iconSize}px`,
        height: `${iconSize}px`,
        color: resolvedColor,
      }}
      className="shrink-0 transition-all"
    />
  );
};

export default QueueIndicator;
