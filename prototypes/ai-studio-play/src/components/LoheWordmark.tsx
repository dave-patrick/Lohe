import React from 'react';

export const LoheWordmark: React.FC<{ className?: string }> = ({ className = 'h-8 sm:h-9 w-auto' }) => {
  return (
    <div className="flex items-center">
      <img
        src="/assets/lohe-wordmark-transparent.png"
        alt="LOHE"
        className={`${className} object-contain filter drop-shadow-[0_0_10px_rgba(0,210,255,0.45)] select-none`}
      />
    </div>
  );
};
