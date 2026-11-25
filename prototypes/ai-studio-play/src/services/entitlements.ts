/**
 * Central Entitlement & Feature Access Layer for Lohe
 *
 * Standards & Requirements as of November 12, 2026:
 * 1. Google Play Billing Library:
 *    - Google Play requires Billing Library 8.0.0+ (Play Billing v7's final submission
 *      extension ended November 1, 2026).
 *    - Modern multi-offer base plan: Product 'lohe_pro_annual', Base Plan 'lohe-pro-annual-base'.
 *
 * 2. Real-Time Developer Notifications (RTDN) & Server Verification:
 *    - RTDN (Google Cloud Pub/Sub) is an asynchronous event trigger, NOT receipt verification.
 *    - When the backend receives an RTDN `SubscriptionNotification` (e.g. SUBSCRIPTION_RENEWED),
 *      it extracts the `purchaseToken` and calls the authoritative Google Play Developer API:
 *      GET https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{packageName}/purchases/subscriptionsv2/tokens/{token}
 *    - The resulting `SubscriptionPurchaseV2` payload is the single authoritative source of truth.
 *
 * 3. SubscriptionPurchaseV2 Schema & Internal Mappings:
 *    - Official Google API Enum: `subscriptionState`:
 *        SUBSCRIPTION_STATE_PENDING (1)
 *        SUBSCRIPTION_STATE_ACTIVE (2)
 *        SUBSCRIPTION_STATE_PAUSED (3)
 *        SUBSCRIPTION_STATE_IN_GRACE_PERIOD (4)
 *        SUBSCRIPTION_STATE_ON_HOLD (5)
 *        SUBSCRIPTION_STATE_CANCELED (6)
 *        SUBSCRIPTION_STATE_EXPIRED (7)
 *    - Note on REVOKED: Google's API schema does NOT have a SUBSCRIPTION_STATE_REVOKED enum.
 *      Revocations and refunds are recorded in `canceledStateContext` (e.g. systemInitiatedCancellation
 *      with refund) or return HTTP 404 / expired tokens. 'REVOKED' in this codebase is strictly an
 *      Internal Derived Client State.
 *    - Note on Grace Period: `SubscriptionPurchaseV2` does NOT have a top-level `gracePeriodEndTime` field.
 *      The authoritative end timestamp for the active line item is `lineItems[0].expiryTime`. When
 *      `subscriptionState == SUBSCRIPTION_STATE_IN_GRACE_PERIOD`, `lineItems[0].expiryTime` indicates
 *      the grace period deadline. `derivedGracePeriodEndTime` is an internal derived client convenience field.
 *
 * 4. Purchase Acknowledgement Reconciliation:
 *    - Play Billing requires acknowledgement via `acknowledgePurchase()`.
 *    - A local acknowledgement timer expiration must NEVER unilaterally declare a purchase refunded.
 *      The client retries acknowledgement and reconciles with Google Play's authoritative server status.
 *
 * 5. FOSS Parity & Build-Time Isolation:
 *    - Pro features (floating player overlay, Glow/Diamond styles, custom HEX color engine) are
 *      strictly unavailable in FOSS. An edition toggle cannot bypass entitlement.
 *    - The FOSS Gradle build compiles with zero proprietary billing/services dependencies.
 */

export type AppEdition = 'foss' | 'play';

/**
 * Official Google Play Developer API SubscriptionState Enum
 * Source: androidpublisher.googleapis.com purchases.subscriptionsv2
 */
export enum GooglePlaySubscriptionState {
  SUBSCRIPTION_STATE_UNSPECIFIED = 0,
  SUBSCRIPTION_STATE_PENDING = 1,
  SUBSCRIPTION_STATE_ACTIVE = 2,
  SUBSCRIPTION_STATE_PAUSED = 3,
  SUBSCRIPTION_STATE_IN_GRACE_PERIOD = 4,
  SUBSCRIPTION_STATE_ON_HOLD = 5,
  SUBSCRIPTION_STATE_CANCELED = 6,
  SUBSCRIPTION_STATE_EXPIRED = 7,
}

/**
 * Client App Subscription Lifecycle Status
 * Maps from GooglePlaySubscriptionState, with internal derived representations.
 */
export type SubscriptionStatus =
  | 'ACTIVE'           // Maps from SUBSCRIPTION_STATE_ACTIVE
  | 'CANCELLED'        // Maps from SUBSCRIPTION_STATE_CANCELED (active until expiryTime)
  | 'IN_GRACE_PERIOD'  // Maps from SUBSCRIPTION_STATE_IN_GRACE_PERIOD (active until lineItems[0].expiryTime)
  | 'ON_HOLD'          // Maps from SUBSCRIPTION_STATE_ON_HOLD (access suspended)
  | 'PAUSED'           // Maps from SUBSCRIPTION_STATE_PAUSED (access suspended)
  | 'PENDING'          // Maps from SUBSCRIPTION_STATE_PENDING (access held until confirmed)
  | 'EXPIRED'          // Maps from SUBSCRIPTION_STATE_EXPIRED (cycle ended)
  | 'REVOKED';         // INTERNAL DERIVED STATE: Mapped from canceledStateContext refund/revocation

export interface GooglePlaySubscriptionPlan {
  productId: 'lohe_pro_annual';
  basePlanId: 'lohe-pro-annual-base';
  billingLibraryVersion: '8.0.0'; // Google Play submission requirement as of November 2026
  formattedPrice: string; // e.g. "US$9.99"
  priceCurrencyCode: string; // "USD"
  priceAmountMicros: number; // 9990000
  billingPeriod: string; // "P1Y" (1 year ISO-8601)
  renewalType: 'AUTO_RENEWING';
  renewalTerms: string;
}

export const LOHE_PRO_SUBSCRIPTION_PLAN: GooglePlaySubscriptionPlan = {
  productId: 'lohe_pro_annual',
  basePlanId: 'lohe-pro-annual-base',
  billingLibraryVersion: '8.0.0',
  formattedPrice: 'US$9.99/year',
  priceCurrencyCode: 'USD',
  priceAmountMicros: 9990000,
  billingPeriod: 'P1Y',
  renewalType: 'AUTO_RENEWING',
  renewalTerms:
    'US$9.99/year plus applicable tax. Automatically renews annually until cancelled in Google Play > Payments & subscriptions at least 24 hours before renewal.',
};

/**
 * Authoritative Purchase Receipt Contract
 * Note: SIG_RSA256_* strings, GPA order IDs, and generated mock tokens are test fixtures,
 * NOT proof of purchase in production. Production entitlement requires server-side verification
 * against Google Play Developer API (purchases.subscriptionsv2.get).
 */
export interface PlayPurchaseReceipt {
  orderId: string;
  packageName: string;
  productId: 'lohe_pro_annual';
  basePlanId: 'lohe-pro-annual-base';
  purchaseTime: number; // epoch ms
  expiryTime: number; // epoch ms, authoritative from lineItems[0].expiryTime
  status: SubscriptionStatus;
  rawGooglePlayState?: GooglePlaySubscriptionState;
  autoRenewing: boolean;
  /**
   * Internal derived convenience field.
   * Documented mapping: In SubscriptionPurchaseV2, grace period deadline is lineItems[0].expiryTime
   * when subscriptionState == SUBSCRIPTION_STATE_IN_GRACE_PERIOD.
   */
  derivedGracePeriodEndTime?: number;
  gracePeriodEndTime?: number; // Backward-compatibility alias for derivedGracePeriodEndTime
  lastVerifiedTime: number; // epoch ms when last confirmed by verification backend
  purchaseToken: string;
  acknowledged: boolean;
  requiresAcknowledgementRetry?: boolean; // Client flag: retry acknowledgePurchase() without revoking
  isTestFixture: boolean; // Explicit label: true for mock/prototype test fixtures
  verificationSource: 'GOOGLE_PLAY_SERVER' | 'LOCAL_TEST_FIXTURE';
  signature?: string;
}

export type FeatureKey =
  // Baseline features (ALWAYS free across FOSS and Play Free)
  | 'volume_boost'
  | 'skip_silence'
  | 'playlist_unlimited'
  | 'folder_creation'
  | 'smart_playlists'
  | 'per_show_auto_queue'
  | 'local_folder_playback'
  | 'battery_setup'
  | 'auto_downloads'
  // Genuine Pro additions (Google Play Pro Subscription only)
  | 'floating_player'
  | 'floating_player_sizing'
  | 'up_next_glow_style'
  | 'up_next_diamond_style'
  | 'up_next_custom_hex';

export const PRO_FEATURES: FeatureKey[] = [
  'floating_player',
  'floating_player_sizing',
  'up_next_glow_style',
  'up_next_diamond_style',
  'up_next_custom_hex',
];

/**
 * Client Offline Verification Tolerance Policy:
 * Separate from Google Play's server-side grace period.
 * If the client device is offline and cannot contact verification endpoints, previously
 * verified active entitlement is cached locally for up to 7 days, provided expiryTime has not elapsed.
 */
export const CLIENT_OFFLINE_VERIFICATION_TOLERANCE_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

/**
 * Environment configuration:
 * In a real production release build, test fixtures must be rejected.
 */
export const IS_PRODUCTION_ENVIRONMENT = false; // Enabled only for web prototype sandbox

/**
 * Validates subscription entitlement against Play Billing rules and lifecycle states.
 */
export function verifySubscriptionEntitlement(
  receipt: PlayPurchaseReceipt | null | undefined,
  now: number = Date.now()
): { isEntitled: boolean; reason: string; status: SubscriptionStatus | 'NONE'; needsAckRetry?: boolean } {
  if (!receipt) {
    return { isEntitled: false, reason: 'No subscription record found', status: 'NONE' };
  }

  // 1. Guard against test fixtures granting production entitlement:
  if (IS_PRODUCTION_ENVIRONMENT && (receipt.isTestFixture || receipt.verificationSource === 'LOCAL_TEST_FIXTURE')) {
    return {
      isEntitled: false,
      reason: 'Simulated test fixture rejected in production. Real entitlement requires Google Play server verification.',
      status: 'REVOKED',
    };
  }

  // 2. Structural & SKU validation
  if (receipt.productId !== 'lohe_pro_annual') {
    return { isEntitled: false, reason: 'Unknown subscription product SKU', status: 'EXPIRED' };
  }
  if (!receipt.purchaseToken || receipt.purchaseToken.length < 16) {
    return { isEntitled: false, reason: 'Malformed purchase token', status: 'EXPIRED' };
  }

  // 3. Purchase Acknowledgement Check:
  // Play Billing requires acknowledgement via acknowledgePurchase().
  // CRITICAL: Do NOT declare a purchase refunded simply because a local client timer expired!
  // Instead, flag for acknowledgement retry and preserve access while reconciling with Google's backend.
  let needsAckRetry = false;
  if (!receipt.acknowledged) {
    needsAckRetry = true;
  }

  // 4. Lifecycle state machine evaluation:
  switch (receipt.status) {
    case 'REVOKED':
      return {
        isEntitled: false,
        reason: 'Subscription was refunded or revoked (Internal Derived State)',
        status: 'REVOKED',
        needsAckRetry,
      };

    case 'ON_HOLD':
      return {
        isEntitled: false,
        reason: 'Subscription is on account hold due to payment failure; update payment method in Google Play',
        status: 'ON_HOLD',
        needsAckRetry,
      };

    case 'PAUSED':
      return {
        isEntitled: false,
        reason: 'Subscription is currently paused in Google Play Subscriptions',
        status: 'PAUSED',
        needsAckRetry,
      };

    case 'PENDING':
      return {
        isEntitled: false,
        reason: 'Payment is pending authorization; Pro access will unlock once payment completes',
        status: 'PENDING',
        needsAckRetry,
      };

    case 'EXPIRED':
      return {
        isEntitled: false,
        reason: 'Subscription billing period has ended',
        status: 'EXPIRED',
        needsAckRetry,
      };

    case 'IN_GRACE_PERIOD': {
      // In SubscriptionPurchaseV2, grace deadline is lineItems[0].expiryTime.
      // Evaluated dynamically from verified state, NOT a hardcoded 16 days.
      const graceEnd = receipt.derivedGracePeriodEndTime || receipt.gracePeriodEndTime || receipt.expiryTime;
      if (now < graceEnd) {
        return {
          isEntitled: true,
          reason: 'Active under Google Play billing grace period',
          status: 'IN_GRACE_PERIOD',
          needsAckRetry,
        };
      }
      return {
        isEntitled: false,
        reason: 'Google Play billing grace period has expired',
        status: 'ON_HOLD',
        needsAckRetry,
      };
    }

    case 'ACTIVE':
    case 'CANCELLED': {
      // Cancellation in Google Play retains Pro until the end of the paid billing period
      if (now < receipt.expiryTime) {
        return {
          isEntitled: true,
          reason: receipt.status === 'CANCELLED' ? 'Active until end of paid period' : 'Subscription active',
          status: receipt.status,
          needsAckRetry,
        };
      }
      // Period has elapsed
      return {
        isEntitled: false,
        reason: 'Paid subscription period elapsed',
        status: 'EXPIRED',
        needsAckRetry,
      };
    }

    default:
      return { isEntitled: false, reason: 'Unrecognized subscription state', status: 'EXPIRED', needsAckRetry };
  }
}

/**
 * Legacy boolean check shim
 */
export function verifyPurchaseReceipt(receipt: PlayPurchaseReceipt | null | undefined): boolean {
  return verifySubscriptionEntitlement(receipt).isEntitled;
}

/**
 * Central Feature Access Gate
 * Determines whether a given feature is accessible given the edition and purchase receipt.
 */
export function canAccessFeature(
  feature: FeatureKey,
  edition: AppEdition = 'play',
  receipt: PlayPurchaseReceipt | null | undefined = null
): boolean {
  // Baseline features are unconditionally free across all editions (FOSS and Play Free)
  if (!PRO_FEATURES.includes(feature)) {
    return true;
  }

  // FOSS Edition: Pro-only features MUST remain strictly unavailable in FOSS.
  // An edition toggle must NOT bypass entitlement checks!
  if (edition === 'foss') {
    return false;
  }

  // Google Play Edition: Requires active, cancelled (within cycle), or grace-period entitlement
  return verifySubscriptionEntitlement(receipt).isEntitled;
}

/**
 * Creates a prototype test fixture for development simulation.
 * Explicitly marked as isTestFixture = true.
 */
export function createTestFixtureReceipt(
  status: SubscriptionStatus = 'ACTIVE',
  autoRenewing: boolean = true,
  options?: {
    acknowledged?: boolean;
    customGracePeriodDays?: number;
    daysRemaining?: number;
  }
): PlayPurchaseReceipt {
  const now = Date.now();
  const oneYearMs = 365 * 24 * 60 * 60 * 1000;
  const daysMs = 24 * 60 * 60 * 1000;

  const randomSuffix = Math.floor(10000000 + Math.random() * 90000000);
  const tokenBytes = Array.from({ length: 32 }, () =>
    Math.floor(Math.random() * 16).toString(16)
  ).join('');

  let expiryTime = now + (options?.daysRemaining ? options.daysRemaining * daysMs : oneYearMs);
  let derivedGracePeriodEndTime: number | undefined = undefined;

  if (status === 'EXPIRED') {
    expiryTime = now - 2 * daysMs;
  } else if (status === 'IN_GRACE_PERIOD') {
    expiryTime = now - 1 * daysMs;
    const graceDays = options?.customGracePeriodDays ?? 16;
    derivedGracePeriodEndTime = now + graceDays * daysMs;
  } else if (status === 'ON_HOLD') {
    expiryTime = now - 18 * daysMs;
  } else if (status === 'CANCELLED') {
    autoRenewing = false;
    expiryTime = now + 120 * daysMs; // 4 months left in paid period
  }

  return {
    orderId: `GPA.3312-8874-${randomSuffix}`,
    packageName: 'com.lohe.app',
    productId: 'lohe_pro_annual',
    basePlanId: 'lohe-pro-annual-base',
    purchaseTime: now,
    expiryTime,
    status,
    autoRenewing,
    derivedGracePeriodEndTime,
    gracePeriodEndTime: derivedGracePeriodEndTime,
    lastVerifiedTime: now,
    purchaseToken: `test_fixture_token_${tokenBytes}`,
    acknowledged: options?.acknowledged ?? true,
    isTestFixture: true,
    verificationSource: 'LOCAL_TEST_FIXTURE',
    signature: `TEST_FIXTURE_SIGNATURE_${tokenBytes.substring(0, 16)}`,
  };
}

/**
 * Backward compatibility alias
 */
export function createVerifiedSubscriptionReceipt(
  status: SubscriptionStatus = 'ACTIVE',
  autoRenewing: boolean = true
): PlayPurchaseReceipt {
  return createTestFixtureReceipt(status, autoRenewing);
}

export function createVerifiedReceipt(): PlayPurchaseReceipt {
  return createTestFixtureReceipt('ACTIVE', true);
}
