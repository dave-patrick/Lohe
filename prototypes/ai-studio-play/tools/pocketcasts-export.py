"""Export a Pocket Casts library in the shape Lohe can import.

Pocket Casts' API is two dialects: a web API that serves a handful of
endpoints and a mobile API that speaks in rules and sync state. This script
translates both into one flat document — shows, episodes with their listening
state, playlists, folders and the up-next queue — so that the importer in the
app has a single shape to work from.

    python tools/pocketcasts-export.py --email you@example.com --password ... -o export.json

The password is used once, to get a token, and the token is used for
everything after that. It is never written to the export.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

API = "https://api.pocketcasts.com"
LOGIN = f"{API}/user/login"

# The API answers a metadata request with at most this many episodes.
EPISODE_BATCH = 40
WORKERS = 8


def request(url, token=None, payload=None, method=None):
    data = json.dumps(payload).encode() if payload is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=90) as response:
        body = response.read().decode()
    return json.loads(body) if body.strip() else {}


def login(email, password):
    result = request(LOGIN, payload={"email": email, "password": password}, method="POST")
    token = result.get("token")
    if not token:
        raise SystemExit(f"Login failed: {result}")
    return token


def post(path, token, payload=None):
    return request(f"{API}/{path}", token=token, payload=payload or {}, method="POST")


def get(path, token):
    return request(f"{API}/{path}", token=token)


def fetch_episode_states(token, show_uuids):
    """The per-show episode state: what is deleted, what is played, how far."""
    def one(uuid):
        try:
            return uuid, post("user/podcast/episodes", token, {"uuid": uuid}).get("episodes", [])
        except Exception as e:  # noqa: BLE001
            print(f"  state fetch failed for {uuid}: {e}", file=sys.stderr)
            return uuid, []

    states = {}
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        for uuid, episodes in pool.map(one, show_uuids):
            states[uuid] = {e["uuid"]: e for e in episodes}
    return states


def fetch_episode_metadata(token, show_uuids, states):
    """Full episode records: title, media URL, duration, publication date."""
    jobs = []
    for uuid in show_uuids:
        pending = list(states.get(uuid, {}).keys())
        for i in range(0, len(pending), EPISODE_BATCH):
            chunk = pending[i:i + EPISODE_BATCH]
            jobs.append((uuid, chunk))

    records = {}

    def one(job):
        show, chunk = job
        try:
            result = post("user/episodes", token, {
                "podcastUuids": [show] * len(chunk),
                "episodeUuids": chunk,
            })
            return result.get("episodes", [])
        except Exception as e:  # noqa: BLE001
            print(f"  metadata batch failed: {e}", file=sys.stderr)
            return []

    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        for episodes in pool.map(one, jobs):
            for episode in episodes:
                records[episode["uuid"]] = episode
    return records


def status_of(state):
    """Pocket Casts playing status, as the words the importer speaks."""
    playing = state.get("playingStatus")
    if playing == 3:
        return "played"
    if playing == 2:
        return "in_progress"
    return "unplayed"


def published_ms(published):
    if not published:
        return None
    # "2026-10-20T18:15:03Z"
    try:
        import datetime
        return int(datetime.datetime.fromisoformat(published.replace("Z", "+00:00")).timestamp() * 1000)
    except ValueError:
        return None


def build(token, args):
    print("reading subscriptions...")
    subscription = post("user/podcast/list", token)
    shows = subscription.get("podcasts", [])
    folders = subscription.get("folders", [])
    show_uuids = [s["uuid"] for s in shows]
    titles = {s["uuid"]: s["title"] for s in shows}

    print(f"  {len(shows)} shows in {len(folders)} folders")

    print("reading episode state...")
    states = fetch_episode_states(token, show_uuids)

    print("reading episode metadata...")
    metadata = fetch_episode_metadata(token, show_uuids, states)

    print("reading playlists...")
    playlist_list = post("user/playlist/list", token).get("playlists", [])
    playlists = []
    for playlist in playlist_list:
        detail = get(f"user/playlists/{playlist['uuid']}?web=1", token)
        podcast_uuids = [u for u in (detail.get("podcastUuids") or "").split(",") if u]
        min_minutes = playlist.get("longerThan") or 0
        max_minutes = playlist.get("shorterThan") or 0
        if podcast_uuids or min_minutes or max_minutes:
            # It collects by a rule: these shows, this length.
            kind = "rule"
        else:
            # It collects by the state of the library — played, unplayed,
            # downloaded, starred — which is a filter on everything rather
            # than a set of shows. Lohe cannot say that, so it is marked and
            # left alone rather than copied as an empty playlist.
            kind = "state"
        playlists.append({
            "name": playlist.get("title", ""),
            "kind": kind,
            "episodeUuids": [],
            "podcastUuids": podcast_uuids,
            "minDurationMinutes": min_minutes,
            "maxDurationMinutes": max_minutes,
        })

    print("reading folders...")
    folder_by_show = {}
    for show in shows:
        folder_uuid = show.get("folderUuid")
        if folder_uuid:
            folder_by_show.setdefault(folder_uuid, []).append(show["uuid"])
    export_folders = [
        {"name": folder.get("name", ""), "podcastUuids": folder_by_show.get(folder["folderUuid"], [])}
        for folder in folders
    ]

    print("reading up next...")
    up_next = post("up_next/sync", token)
    queue = up_next.get("order") or list(up_next.get("episodes", {}).keys())

    print("assembling episodes...")
    episodes = []
    seen = set()
    for show_uuid, rows in states.items():
        for uuid, state in rows.items():
            seen.add(uuid)
            meta = metadata.get(uuid, {})
            episodes.append({
                "uuid": uuid,
                "podcastUuid": show_uuid,
                "title": meta.get("title"),
                "mediaUrl": meta.get("url"),
                "publishedMs": published_ms(meta.get("published")),
                "durationSeconds": meta.get("duration"),
                "status": status_of(state),
                "playedUpToSeconds": state.get("playedUpTo") or 0,
                "starred": bool(meta.get("starred")) or bool(state.get("starred")),
                # Pocket Casts' per-episode flag says the row has been pruned
                # from its sync state, which is what it does to every episode
                # it has finished with. Carrying it over as a deletion would
                # empty the library, so it is deliberately not carried.
                "deleted": False,
            })

    # The queued episodes are usually not in the per-show state at all: they
    # are new, and Pocket Casts has not written their state back yet. The
    # queue carries enough about them to match them, so they are added here
    # or the queue cannot be copied across.
    for uuid, entry in up_next.get("episodes", {}).items():
        if uuid in seen:
            continue
        seen.add(uuid)
        episodes.append({
            "uuid": uuid,
            "podcastUuid": entry.get("podcast", ""),
            "title": entry.get("title"),
            "mediaUrl": entry.get("url"),
            "publishedMs": None,
            "durationSeconds": None,
            "status": "unplayed",
            "playedUpToSeconds": 0,
            "starred": False,
            "deleted": False,
        })

    return {
        "podcasts": [
            {"uuid": s["uuid"], "title": s["title"], "feedUrl": s.get("feedUrl")}
            for s in shows
        ],
        "episodes": episodes,
        "playlists": playlists,
        "folders": export_folders,
        "upNext": queue,
        "_note": "Exported from the Pocket Casts API. feedUrl is null: the API does not expose feed addresses.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--email")
    parser.add_argument("--password")
    parser.add_argument("--token", help="Use an existing token instead of logging in.")
    parser.add_argument("-o", "--out", required=True, help="Where to write the export.")
    args = parser.parse_args()

    if args.token:
        token = args.token
    elif args.email and args.password:
        token = login(args.email, args.password)
    else:
        parser.error("give --token, or --email and --password")

    export = build(token, args)
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(export, handle, ensure_ascii=False)
    size = os.path.getsize(args.out)
    print(f"\nwrote {args.out} ({size / 1_000_000:.1f} MB)")
    print(f"  {len(export['podcasts'])} shows")
    print(f"  {len(export['episodes'])} episodes")
    print(f"  {len(export['playlists'])} playlists")
    print(f"  {len(export['folders'])} folders")
    print(f"  {len(export['upNext'])} queued episodes")


if __name__ == "__main__":
    main()
