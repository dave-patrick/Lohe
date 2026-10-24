package com.lohe.app.data

internal fun shouldAutoArchive(played: Boolean, archived: Boolean, playedAt: Long?, hours: Int, now: Long): Boolean =
    played && !archived && hours >= 0 && playedAt != null && now >= playedAt &&
        now - playedAt >= hours * 3_600_000L
