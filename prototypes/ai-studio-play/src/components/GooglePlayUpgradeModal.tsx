import React, { useState } from 'react';
import { usePlayer } from '../context/PlayerContext';
import {
  Crown,
  Gem,
  Download,
  Check,
  ShieldCheck,
  CreditCard,
  X,
  Zap,
  Palette,
  Layers,
  RotateCcw,
  AlertTriangle,
  Calendar,
  ChevronDown,
  ChevronUp,
  Info,
  PauseCircle,
  Ban,
  Clock,
} from 'lucide-react';
import { SubscriptionStatus } from '../services/entitlements';

export const GooglePlayUpgradeModal: React.FC = () => {
  const {
    isUpgradeModalOpen,
    closeUpgradeModal,
    upgradeFeaturePrompt,
    unlockPro,
    cancelSubscription,
    simulateSubscriptionStatus,
    acknowledgePurchase,
    restorePurchases,
    recoverInterruptedPurchases,
    subscriptionStatus,
    subscriptionExpiryTime,
    isGracePeriod,
    isAutoRenewing,
    purchaseReceipt,
  } = usePlayer();

  const [paymentMethod, setPaymentMethod] = useState<'balance' | 'card'>('balance');
  const [purchaseState, setPurchaseState] = useState<'idle' | 'processing' | 'success' | 'restoring'>('idle');
  const [showDiagnostics, setShowDiagnostics] = useState<boolean>(false);
  const [feedbackMessage, setFeedbackMessage] = useState<string | null>(null);

  if (!isUpgradeModalOpen) return null;

  const handleSubscribe = () => {
    setPurchaseState('processing');
    setTimeout(() => {
      unlockPro('ACTIVE');
      setPurchaseState('success');
      setFeedbackMessage('Google Play subscription active: US$9.99/year (Test Fixture).');
      setTimeout(() => {
        setPurchaseState('idle');
        closeUpgradeModal();
      }, 1200);
    }, 1000);
  };

  const handleRestore = async () => {
    setPurchaseState('restoring');
    await restorePurchases();
    setPurchaseState('success');
    setFeedbackMessage('Google Play subscriptions restored via BillingClient.queryPurchasesAsync().');
    setTimeout(() => {
      setPurchaseState('idle');
      closeUpgradeModal();
    }, 1200);
  };

  const handleCancelRenewal = () => {
    cancelSubscription();
    setFeedbackMessage('Auto-renewal cancelled. Pro access remains active until the end of your billing cycle.');
    setTimeout(() => setFeedbackMessage(null), 4000);
  };

  const handleSimulateStatus = (status: SubscriptionStatus) => {
    simulateSubscriptionStatus(status);
    setFeedbackMessage(`Simulated subscription lifecycle state: ${status}`);
    setTimeout(() => setFeedbackMessage(null), 3500);
  };

  const proPerks = [
    {
      icon: Layers,
      title: 'Floating Mini Player Overlay',
      desc: 'Draggable picture-in-picture overlay controls over other apps with custom sizing (48–88dp) and transparency',
    },
    {
      icon: Gem,
      title: 'Glow & Diamond Indicator Styles',
      desc: 'Ambient pulsing glow and angled diamond queue membership indicators for episode rows',
    },
    {
      icon: Palette,
      title: 'Precision Custom HEX Color Engine',
      desc: 'Unlock infinite-precision custom HEX / RGB colors with live indicator preview',
    },
    {
      icon: ShieldCheck,
      title: 'Independent Developer Supporter Entitlement',
      desc: 'Verified Google Play annual subscription supporting ongoing private, tracker-free audio development',
    },
  ];

  const formattedExpiry = subscriptionExpiryTime
    ? new Date(subscriptionExpiryTime).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      })
    : null;

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/80 backdrop-blur-md p-0 sm:p-4 select-none animate-fade-in font-sans">
      <div className="w-full max-w-lg max-h-[92vh] flex flex-col bg-[#1C1C1E] border-t sm:border border-[#2C2C30] rounded-t-[28px] sm:rounded-[28px] shadow-2xl overflow-hidden">
        {/* Google Play Billing Brand Header */}
        <div className="flex items-center justify-between px-5 pt-4 pb-2 border-b border-[#2C2C30]/50 bg-[#161618]">
          <div className="flex items-center gap-2">
            {/* Google Play 4-Color Triangle Logo */}
            <svg className="w-5 h-5 shrink-0" viewBox="0 0 24 24">
              <path fill="#4285F4" d="M3.6 1.8L13.8 12 3.6 22.2c-.4-.4-.6-1-.6-1.7V3.5c0-.7.2-1.3.6-1.7z" />
              <path fill="#34A853" d="M17.1 8.7L13.8 12l3.3 3.3 3.9-2.2c.7-.4.7-1.7 0-2.2l-3.9-2.2z" />
              <path fill="#FBBC04" d="M3.6 1.8l10.2 10.2 3.3-3.3-11.4-6.5c-.7-.4-1.5-.4-2.1-.4z" />
              <path fill="#EA4335" d="M13.8 12L3.6 22.2c.6 0 1.4 0 2.1-.4l11.4-6.5-3.3-3.3z" />
            </svg>
            <span className="text-xs font-semibold text-white/90 tracking-tight">Google Play</span>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-[11px] text-[#8E8E93] hidden xs:inline truncate max-w-[150px]">
              davepatrick@gmail.com
            </span>
            <button
              type="button"
              onClick={closeUpgradeModal}
              className="w-8 h-8 rounded-full flex items-center justify-center text-[#8E8E93] hover:text-white hover:bg-white/10 transition-colors"
              aria-label="Close"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Scrollable Body Content */}
        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          {/* App Title and Icon Header */}
          <div className="flex items-center gap-3.5 pb-1">
            <div className="w-14 h-14 rounded-2xl overflow-hidden bg-black/40 border border-white/10 shadow-lg shrink-0">
              <img
                src="/assets/official-app-icon.png"
                alt="Lohe Icon"
                className="w-full h-full object-cover"
              />
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-1.5">
                <h3 className="text-lg font-bold text-white tracking-tight leading-tight">
                  Lohe Pro Subscription
                </h3>
                <span className="px-1.5 py-0.5 rounded text-[10px] font-bold tracking-wider uppercase bg-[#008EFF] text-white shadow-sm flex items-center gap-1">
                  <Crown className="w-3 h-3 text-white" />
                  <span>ANNUAL</span>
                </span>
              </div>
              <p className="text-xs text-[#8E8E93] mt-0.5">
                Atomic 22 Industries · Google Play Subscription (lohe_pro_annual)
              </p>
            </div>
          </div>

          {/* User Feedback Alert */}
          {feedbackMessage && (
            <div className="p-3 rounded-2xl bg-[#008EFF]/15 border border-[#008EFF]/40 text-xs text-[#00D2FF] animate-fade-in flex items-center gap-2">
              <Crown className="w-4 h-4 shrink-0 text-[#00D2FF]" />
              <span>{feedbackMessage}</span>
            </div>
          )}

          {/* Trigger Banner (if opened from a specific locked feature) */}
          {upgradeFeaturePrompt && (
            <div className="p-3 rounded-2xl bg-[#008EFF]/15 border border-[#008EFF]/40 flex items-center gap-2.5 text-xs text-[#00D2FF]">
              <Crown className="w-4 h-4 text-[#00D2FF] shrink-0" />
              <span>
                Upgrade to unlock <strong className="font-bold text-white">{upgradeFeaturePrompt}</strong> and all released Pro features.
              </span>
            </div>
          )}

          {/* Existing Subscription Status Banner */}
          {purchaseReceipt && subscriptionStatus !== 'NONE' && (
            <div className="p-3.5 rounded-2xl bg-[#26262A] border border-white/10 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs text-[#8E8E93]">Subscription Status</span>
                <span
                  className={`px-2 py-0.5 rounded-full text-[11px] font-bold uppercase tracking-wider ${
                    subscriptionStatus === 'ACTIVE'
                      ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30'
                      : subscriptionStatus === 'CANCELLED'
                      ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
                      : subscriptionStatus === 'IN_GRACE_PERIOD'
                      ? 'bg-rose-500/15 text-rose-400 border border-rose-500/30'
                      : subscriptionStatus === 'ON_HOLD'
                      ? 'bg-orange-500/15 text-orange-400 border border-orange-500/30'
                      : subscriptionStatus === 'PAUSED'
                      ? 'bg-blue-500/15 text-blue-400 border border-blue-500/30'
                      : subscriptionStatus === 'REVOKED'
                      ? 'bg-red-500/15 text-red-400 border border-red-500/30'
                      : 'bg-zinc-700/50 text-zinc-400'
                  }`}
                >
                  {subscriptionStatus === 'ACTIVE' && 'Active · Auto-Renewing'}
                  {subscriptionStatus === 'CANCELLED' && 'Cancelled (Active until cycle end)'}
                  {subscriptionStatus === 'IN_GRACE_PERIOD' && 'Grace Period Active'}
                  {subscriptionStatus === 'ON_HOLD' && 'Account Hold (Payment Failed)'}
                  {subscriptionStatus === 'PAUSED' && 'Subscription Paused'}
                  {subscriptionStatus === 'REVOKED' && 'Refunded / Revoked'}
                  {subscriptionStatus === 'EXPIRED' && 'Expired'}
                  {subscriptionStatus === 'PENDING' && 'Pending Approval'}
                </span>
              </div>

              {formattedExpiry && (
                <div className="flex items-center gap-1.5 text-xs text-[#8E8E93]">
                  <Calendar className="w-3.5 h-3.5 text-[#008EFF]" />
                  <span>
                    {subscriptionStatus === 'CANCELLED'
                      ? `Access continues through ${formattedExpiry}`
                      : `Next renewal: ${formattedExpiry}`}
                  </span>
                </div>
              )}

              {isGracePeriod && (
                <div className="p-2.5 rounded-xl bg-rose-500/10 border border-rose-500/20 text-[11px] text-rose-300 flex items-start gap-2">
                  <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                  <span>
                    Your payment method could not be charged. Google Play billing grace period is active while retrying payment. Pro features remain fully accessible.
                  </span>
                </div>
              )}

              {/* Explicit Test Fixture Label */}
              {purchaseReceipt.isTestFixture && (
                <div className="p-2 rounded-lg bg-amber-500/10 border border-amber-500/20 text-[10px] text-amber-300 flex items-center gap-1.5">
                  <Info className="w-3.5 h-3.5 shrink-0 text-amber-400" />
                  <span>
                    <strong>Prototype Test Fixture</strong> — Simulated sandbox receipt for testing. Not a valid proof of purchase in production.
                  </span>
                </div>
              )}

              {subscriptionStatus === 'ACTIVE' && isAutoRenewing && (
                <button
                  type="button"
                  onClick={handleCancelRenewal}
                  className="w-full mt-1 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 text-xs text-[#8E8E93] hover:text-white transition-colors"
                >
                  Cancel Auto-Renewal in Google Play
                </button>
              )}
            </div>
          )}

          {/* Single Annually Renewing Subscription Card */}
          <div className="p-4 rounded-2xl bg-[#008EFF]/15 border-2 border-[#008EFF] shadow-[0_0_20px_rgba(0,142,255,0.25)] relative">
            <span className="absolute -top-3 right-4 px-2.5 py-0.5 rounded-full bg-[#008EFF] text-white text-[10px] font-bold shadow-md">
              ALL PRO FEATURES INCLUDED
            </span>

            <div className="flex items-baseline justify-between">
              <div>
                <h4 className="text-base font-bold text-white">Lohe Pro Annual</h4>
                <p className="text-xs text-[#8E8E93] mt-0.5">Base Plan: lohe-pro-annual-base</p>
              </div>
              <div className="text-right">
                <span className="text-2xl font-black text-white tracking-tight">US$9.99</span>
                <span className="text-xs font-normal text-[#8E8E93]"> / year</span>
              </div>
            </div>

            <div className="mt-3 pt-3 border-t border-white/10 flex items-center justify-between text-xs text-[#00D2FF]">
              <span>Just $0.83 / month</span>
              <span>Billed annually via Google Play</span>
            </div>
          </div>

          {/* Pro Perks List */}
          <div className="space-y-2 pt-1">
            <p className="text-xs font-bold uppercase tracking-wider text-[#8E8E93]">
              Included with Lohe Pro Subscription:
            </p>
            {proPerks.map((perk, idx) => {
              const Icon = perk.icon;
              return (
                <div
                  key={idx}
                  className="flex items-start gap-3 p-2.5 rounded-xl bg-white/[0.03] border border-white/5"
                >
                  <div className="w-7 h-7 rounded-lg bg-[#008EFF]/20 text-[#008EFF] flex items-center justify-center shrink-0 mt-0.5">
                    <Icon className="w-3.5 h-3.5" />
                  </div>
                  <div className="min-w-0">
                    <p className="text-xs font-bold text-white leading-tight">{perk.title}</p>
                    <p className="text-[11px] text-[#8E8E93] leading-snug mt-0.5">{perk.desc}</p>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Payment Method Selector */}
          <div className="pt-1">
            <p className="text-xs font-bold uppercase tracking-wider text-[#8E8E93] mb-2">
              Google Play Payment Method:
            </p>
            <div className="p-3 rounded-2xl bg-[#26262A] border border-white/5 space-y-2">
              <label
                onClick={() => setPaymentMethod('balance')}
                className="flex items-center justify-between cursor-pointer"
              >
                <div className="flex items-center gap-2.5">
                  <span className="w-4 h-4 rounded-full border border-white/30 flex items-center justify-center">
                    {paymentMethod === 'balance' && (
                      <span className="w-2.5 h-2.5 rounded-full bg-[#008EFF]" />
                    )}
                  </span>
                  <div className="text-xs">
                    <span className="text-white font-medium">Google Play balance: </span>
                    <span className="text-[#00D2FF] font-bold">$12.50</span>
                  </div>
                </div>
                <span className="text-[11px] text-[#8E8E93]">Instant</span>
              </label>

              <div className="h-px bg-white/5" />

              <label
                onClick={() => setPaymentMethod('card')}
                className="flex items-center justify-between cursor-pointer"
              >
                <div className="flex items-center gap-2.5">
                  <span className="w-4 h-4 rounded-full border border-white/30 flex items-center justify-center">
                    {paymentMethod === 'card' && (
                      <span className="w-2.5 h-2.5 rounded-full bg-[#008EFF]" />
                    )}
                  </span>
                  <div className="flex items-center gap-1.5 text-xs text-white font-medium">
                    <CreditCard className="w-3.5 h-3.5 text-[#8E8E93]" />
                    <span>Visa •••• 4242</span>
                  </div>
                </div>
                <span className="text-[11px] text-[#8E8E93]">Default</span>
              </label>
            </div>
          </div>

          {/* Official Google Play Disclosure Terms */}
          <div className="p-3 rounded-xl bg-white/[0.02] border border-white/5 text-[11px] text-[#8E8E93] space-y-1.5">
            <p className="leading-relaxed">
              <strong className="text-white/80">Renewal Terms:</strong> US$9.99/year plus applicable taxes. Subscription automatically renews annually unless cancelled at least 24 hours before the end of the billing cycle in Google Play &gt; Payments &amp; subscriptions.
            </p>
            <p className="leading-relaxed">
              <strong className="text-white/80">Cancellation &amp; Grace:</strong> Cancellation retains Pro until your paid billing cycle ends. If payment fails, Google Play's configured billing grace period preserves your listening. Confirmed expiry pauses the floating overlay while preserving all saved preferences.
            </p>
          </div>

          {/* Lifecycle & Diagnostics Testing Drawer */}
          <div className="pt-1">
            <button
              type="button"
              onClick={() => setShowDiagnostics(!showDiagnostics)}
              className="w-full flex items-center justify-between py-2 text-xs font-semibold text-[#8E8E93] hover:text-white"
            >
              <span>Test Google Play Subscriptions Lifecycle States</span>
              {showDiagnostics ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
            </button>

            {showDiagnostics && (
              <div className="p-3 rounded-xl bg-[#141416] border border-white/10 space-y-2.5 mt-1">
                <div className="p-2 rounded-lg bg-amber-500/10 border border-amber-500/20 text-[10px] text-amber-300">
                  <strong>Notice:</strong> These fixtures test client state transitions and UI gating in the prototype. In native release builds, real entitlement requires Google Play server verification.
                </div>
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <button
                    type="button"
                    onClick={() => handleSimulateStatus('ACTIVE')}
                    className="p-2 rounded-lg bg-white/5 hover:bg-emerald-500/20 text-emerald-400 font-medium text-left"
                  >
                    1. Active (Renewing)
                  </button>
                  <button
                    type="button"
                    onClick={() => handleSimulateStatus('CANCELLED')}
                    className="p-2 rounded-lg bg-white/5 hover:bg-amber-500/20 text-amber-400 font-medium text-left"
                  >
                    2. Cancelled (Retains Pro)
                  </button>
                  <button
                    type="button"
                    onClick={() => handleSimulateStatus('IN_GRACE_PERIOD')}
                    className="p-2 rounded-lg bg-white/5 hover:bg-rose-500/20 text-rose-400 font-medium text-left"
                  >
                    3. Grace Period (lineItems[0].expiryTime)
                  </button>
                  <button
                    type="button"
                    onClick={() => handleSimulateStatus('ON_HOLD')}
                    className="p-2 rounded-lg bg-white/5 hover:bg-orange-500/20 text-orange-400 font-medium text-left"
                  >
                    4. Account Hold (Suspended)
                  </button>
                  <button
                    type="button"
                    onClick={() => handleSimulateStatus('PAUSED')}
                    className="p-2 rounded-lg bg-white/5 hover:bg-blue-500/20 text-blue-400 font-medium text-left"
                  >
                    5. Paused (Suspended)
                  </button>
                  <button
                    type="button"
                    onClick={() => handleSimulateStatus('REVOKED')}
                    className="p-2 rounded-lg bg-white/5 hover:bg-red-500/20 text-red-400 font-medium text-left"
                  >
                    6. Revoked / Refund (Derived State)
                  </button>
                  <button
                    type="button"
                    onClick={() => handleSimulateStatus('PENDING')}
                    className="p-2 rounded-lg bg-white/5 hover:bg-yellow-500/20 text-yellow-300 font-medium text-left"
                  >
                    7. Pending Payment
                  </button>
                  <button
                    type="button"
                    onClick={() => handleSimulateStatus('EXPIRED')}
                    className="p-2 rounded-lg bg-white/5 hover:bg-zinc-600/30 text-zinc-300 font-medium text-left"
                  >
                    8. Expired (Preferences Kept)
                  </button>
                </div>

                <div className="pt-2 border-t border-white/5 flex items-center justify-between gap-2">
                  <button
                    type="button"
                    onClick={async () => {
                      await recoverInterruptedPurchases();
                      setFeedbackMessage('Recovered interrupted purchase & acknowledged.');
                    }}
                    className="flex-1 py-1.5 px-2 rounded-lg bg-white/5 hover:bg-white/10 text-[11px] text-[#A1A1A6] hover:text-white"
                  >
                    Test Interrupted Recovery
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      acknowledgePurchase();
                      setFeedbackMessage('Purchase acknowledged.');
                    }}
                    className="flex-1 py-1.5 px-2 rounded-lg bg-white/5 hover:bg-white/10 text-[11px] text-[#A1A1A6] hover:text-white"
                  >
                    Test Acknowledge
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Bottom Action Footer with 1-Tap Subscribe */}
        <div className="p-4 bg-[#161618] border-t border-[#2C2C30] space-y-2.5">
          {purchaseState === 'idle' && (
            <button
              type="button"
              onClick={handleSubscribe}
              className="w-full py-3.5 rounded-full bg-[#008EFF] hover:bg-[#00A8FF] text-white font-bold text-sm shadow-[0_4px_16px_rgba(0,142,255,0.4)] flex items-center justify-center gap-2 active:scale-98 transition-all"
            >
              <Zap className="w-4 h-4 fill-white" />
              <span>Subscribe · US$9.99/year</span>
            </button>
          )}

          {purchaseState === 'processing' && (
            <div className="w-full py-3.5 rounded-full bg-[#26262A] text-white font-semibold text-xs flex items-center justify-center gap-2.5">
              <span className="w-4 h-4 border-2 border-[#008EFF] border-t-transparent rounded-full animate-spin" />
              <span>Connecting to Google Play Billing Service...</span>
            </div>
          )}

          {purchaseState === 'restoring' && (
            <div className="w-full py-3.5 rounded-full bg-[#26262A] text-white font-semibold text-xs flex items-center justify-center gap-2.5">
              <span className="w-4 h-4 border-2 border-emerald-400 border-t-transparent rounded-full animate-spin" />
              <span>Restoring Google Play subscriptions...</span>
            </div>
          )}

          {purchaseState === 'success' && (
            <div className="w-full py-3.5 rounded-full bg-emerald-500 text-black font-bold text-sm flex items-center justify-center gap-2 animate-bounce">
              <Check className="w-5 h-5 stroke-[3]" />
              <span>Subscription Confirmed! Lohe Pro Active.</span>
            </div>
          )}

          {/* Secondary Actions: Restore & Supported Google Play Library Info */}
          <div className="flex items-center justify-between text-xs px-2 pt-1">
            <button
              type="button"
              onClick={handleRestore}
              className="text-[#8E8E93] hover:text-white flex items-center gap-1 transition-colors"
            >
              <RotateCcw className="w-3 h-3" />
              <span>Restore Subscriptions</span>
            </button>

            <span className="text-[10px] text-[#8E8E93]/70">
              Google Play Billing Library v8.0.0+
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default GooglePlayUpgradeModal;
