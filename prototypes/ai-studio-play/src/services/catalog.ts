import { XMLParser } from 'fast-xml-parser';
import { CatalogPodcast, Podcast, Episode } from '../types';

export class CatalogService {
  /**
   * Search iTunes Podcast Directory.
   * iTunes search endpoint supports CORS and returns podcast metadata + feed URLs.
   */
  static async searchPodcasts(term: string): Promise<CatalogPodcast[]> {
    const trimmed = term.trim();
    if (!trimmed) return [];

    try {
      const response = await fetch(
        `https://itunes.apple.com/search?media=podcast&term=${encodeURIComponent(trimmed)}&limit=25`
      );
      if (!response.ok) return [];

      const data = await response.json();
      if (!data.results || !Array.isArray(data.results)) return [];

      return data.results.map((item: any) => ({
        title: item.collectionName || item.trackName || 'Untitled Podcast',
        author: item.artistName || 'Unknown Artist',
        feedUrl: item.feedUrl || '',
        artworkUrl: item.artworkUrl600 || item.artworkUrl100 || '',
        genre: item.primaryGenreName || 'Podcasts',
        episodeCount: item.trackCount || 0,
      })).filter((p: CatalogPodcast) => Boolean(p.feedUrl));
    } catch (err) {
      console.warn('Catalog search error:', err);
      return [];
    }
  }

  /**
   * Fetch and parse an RSS podcast feed with CORS proxy fallback.
   */
  static async fetchFeed(feedUrl: string): Promise<{ podcast: Partial<Podcast>; episodes: Partial<Episode>[] } | null> {
    let xmlText = '';

    // First attempt: direct fetch
    try {
      const resp = await fetch(feedUrl, { headers: { 'Accept': 'application/rss+xml, application/xml, text/xml' } });
      if (resp.ok) {
        xmlText = await resp.text();
      }
    } catch {
      // Direct fetch failed (e.g. CORS)
    }

    // Second attempt: public CORS proxy if direct failed
    if (!xmlText) {
      try {
        const proxyUrl = `https://api.allorigins.win/raw?url=${encodeURIComponent(feedUrl)}`;
        const resp = await fetch(proxyUrl);
        if (resp.ok) {
          xmlText = await resp.text();
        }
      } catch (err) {
        console.warn('Proxy fetch failed:', err);
      }
    }

    if (!xmlText) {
      throw new Error("Could not fetch podcast feed. The server may be unreachable or restricting access.");
    }

    const parser = new XMLParser({
      ignoreAttributes: false,
      attributeNamePrefix: '@_',
    });

    const parsed = parser.parse(xmlText);
    const channel = parsed.rss?.channel || parsed.channel || parsed.feed;
    if (!channel) throw new Error("Invalid RSS/Atom podcast feed format.");

    const podcastTitle = channel.title || 'Untitled Show';
    const podcastAuthor = channel['itunes:author'] || channel.author || channel.managingEditor || '';
    const podcastDesc = channel.description || channel['itunes:summary'] || '';
    const podcastArtwork = channel['itunes:image']?.['@_href'] || channel.image?.url || '';

    const rawItems = channel.item || channel.entry || [];
    const itemsArray = Array.isArray(rawItems) ? rawItems : [rawItems];

    const podcastUuid = 'pod_' + Math.random().toString(36).substring(2, 10);

    const episodes: Partial<Episode>[] = itemsArray.slice(0, 50).map((it: any) => {
      const enclosure = it.enclosure;
      const audioUrl = enclosure?.['@_url'] || it.link?.['@_href'] || it.link || '';
      const durationRaw = it['itunes:duration'];
      let durationSeconds = 0;

      if (typeof durationRaw === 'number') {
        durationSeconds = Math.round(durationRaw);
      } else if (typeof durationRaw === 'string') {
        if (durationRaw.includes(':')) {
          const parts = durationRaw.split(':').map(Number);
          if (parts.length === 3) durationSeconds = parts[0] * 3600 + parts[1] * 60 + parts[2];
          else if (parts.length === 2) durationSeconds = parts[0] * 60 + parts[1];
        } else {
          durationSeconds = parseInt(durationRaw, 10) || 0;
        }
      }

      const published = it.pubDate ? new Date(it.pubDate).getTime() : Date.now();
      const description = it['content:encoded'] || it.description || it['itunes:summary'] || '';

      return {
        uuid: 'ep_' + Math.random().toString(36).substring(2, 11),
        podcastUuid,
        podcastTitle,
        title: it.title || 'Untitled Episode',
        audioUrl,
        published: isNaN(published) ? Date.now() : published,
        durationSeconds: durationSeconds || 1800,
        sizeBytes: parseInt(enclosure?.['@_length'], 10) || 25000000,
        artworkUrl: it['itunes:image']?.['@_href'] || podcastArtwork,
        description,
        playingStatus: 0 as const,
        playedUpToSeconds: 0,
        listenedSeconds: 0,
        starred: false,
        isArchived: false,
        queuePosition: null,
      };
    }).filter((ep: Partial<Episode>) => Boolean(ep.audioUrl));

    return {
      podcast: {
        uuid: podcastUuid,
        title: podcastTitle,
        author: podcastAuthor,
        description: podcastDesc,
        artworkUrl: podcastArtwork,
        feedUrl,
        subscribedAt: Date.now(),
        lastEpisodePublished: episodes[0]?.published || Date.now(),
        unreadCount: episodes.length,
        autoDownload: false,
        autoAddNewEpisodesToUpNext: false,
        autoAddNewEpisodesAtTop: false,
        isSubscribed: true,
      },
      episodes,
    };
  }

  /**
   * Export podcasts to standard OPML format.
   */
  static exportOpml(podcasts: Podcast[]): string {
    const outlines = podcasts
      .filter((p) => p.isSubscribed)
      .map(
        (p) =>
          `      <outline type="rss" text="${escapeXml(p.title)}" title="${escapeXml(p.title)}" xmlUrl="${escapeXml(p.feedUrl)}" htmlUrl="${escapeXml(p.artworkUrl || '')}" />`
      )
      .join('\n');

    return `<?xml version="1.0" encoding="UTF-8"?>
<opml version="1.0">
  <head>
    <title>Lohe Subscriptions</title>
    <dateCreated>${new Date().toUTCString()}</dateCreated>
  </head>
  <body>
    <outline text="feeds">
${outlines}
    </outline>
  </body>
</opml>`;
  }

  /**
   * Parse OPML file into array of feed URLs.
   */
  static parseOpml(opmlText: string): { title: string; feedUrl: string }[] {
    const parser = new XMLParser({ ignoreAttributes: false, attributeNamePrefix: '@_' });
    const parsed = parser.parse(opmlText);
    const feeds: { title: string; feedUrl: string }[] = [];

    const walk = (node: any) => {
      if (!node) return;
      if (node['@_xmlUrl']) {
        feeds.push({
          title: node['@_title'] || node['@_text'] || 'Subscribed Podcast',
          feedUrl: node['@_xmlUrl'],
        });
      }
      const children = node.outline;
      if (Array.isArray(children)) {
        children.forEach(walk);
      } else if (children) {
        walk(children);
      }
    };

    const body = parsed.opml?.body || parsed.body;
    if (body) walk(body);
    return feeds;
  }
}

function escapeXml(unsafe: string): string {
  return unsafe
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}
