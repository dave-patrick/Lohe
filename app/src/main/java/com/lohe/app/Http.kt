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
