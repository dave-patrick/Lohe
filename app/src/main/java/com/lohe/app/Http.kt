package com.lohe.app

/**
 * The User-Agent Lohe sends with every network request.
 *
 * Podcast hosts vary in how picky they are. Some serve any request; others
 * block anything that does not look like a known podcast client, and a bare
 * name with no contact details is the sort of thing that gets 403'd. The
 * convention in podcasting is to identify the client and give a way to reach
 * it, so that is what this does.
 *
 * Kept in one place because it is sent to three different hosts (feed
 * fetches, the catalog search, and episode downloads) and a mismatch between
 * them is exactly the kind of thing that looks like a random 403.
 */
const val LOHE_USER_AGENT = "Lohe/0.8.0 (+https://github.com/dave-patrick/Lohe)"

/**
 * The User-Agent used for a second attempt after a 403.
 *
 * Some hosts put a bot wall in front of a feed and refuse anything that does
 * not look like a browser, including perfectly respectable podcast clients
 * that identify themselves. The feed is public and the listener has asked for
 * it, so a single retry as a browser is reasonable. It is a fallback, not the
 * default: the first attempt always identifies itself honestly, and the
 * failure message says plainly that the server refused.
 */
const val LOHE_FALLBACK_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
