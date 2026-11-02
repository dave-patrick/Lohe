# Google Play readiness — Lohe

See [play-console-submission-checklist.md](play-console-submission-checklist.md) for the detailed owner checklist and current evidence. This replaces the initial draft's inaccurate Data Safety and permission claims.

## Implemented

- Offline privacy text in About and public HTML generated from [privacy-policy.md](privacy-policy.md). Public contact: atomic22industries@gmail.com.
- Shared contextual overlay/notification setup from Settings and the player. Notification refusal leaves listening available; Android notification settings provides recovery.
- `-PlohePlayStore=true` phone/tablet profile removes unverified car declarations. Ordinary Release and Debug retain the car implementation.
- Minified, non-debuggable, isolated `smokeRelease` for device validation.
- Private release signing remains separate from Debug. CI builds an explicitly unsigned AAB, retains its R8 mapping and does not upload Debug APKs.
- Pages stages only public files, excluding handoff prompts and internal submission notes.
- Bundle verification checks manifest/permissions/car exclusion/signatures and native/ZIP 16 KB alignment where applicable.

## Corrections to the initial draft

Local history is not automatically off-device collection. Catalog queries, IP/request information and publisher network transfers require assessment. Do not infer no collection from the absence of a developer server.

The network security config still permits arbitrary HTTP podcasts; it is not a domain allowlist or proof of encryption. This is disclosed in the policy and submission guidance.

Overlay access is granted in Android special-access settings. Notification permission applies to non-exempt notifications; media-session playback has platform exemptions. Foreground-service declarations/videos must match actual services and Console forms. RECEIVE_BOOT_COMPLETED supports maintenance, not automatic audio at boot.

AAB compilation is not runtime validation or Play approval. An unsigned CI AAB is not a signed submission artifact.

## Remaining owner/publication work

Privacy HTML is prepared at docs/privacy/index.html for https://dave-patrick.github.io/Lohe/privacy/. Local edits do not publish it. The handoff forbids remote pushes, so deployment and live URL verification remain outstanding.

The owner must complete account verification, signing enrollment/update compatibility, store assets, app-content/Data Safety/audience/rating declarations, FGS videos, any account-specific closed testing and Play review. Use the detailed checklist rather than treating this as a compliance certificate.
