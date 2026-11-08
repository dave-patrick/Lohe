import json

state = json.load(open("D:/My Drive/Dev Projects/Lohe/tools/out/pocketcasts-state.json", encoding="utf-8"))
playlists = state["playlists"]
episodes = state["episodes"]

print(f"Total PC playlists: {len(playlists)}")
print("=" * 60)

for pl in playlists:
    name = pl["name"]
    kind = pl.get("kind")
    show_count = len(pl.get("podcastUuids", []))
    ep_count = len(pl.get("episodeUuids", []))
    finished = pl.get("finished")
    downloaded = pl.get("downloaded")
    not_downloaded = pl.get("notDownloaded")
    starred = pl.get("starred")
    all_podcasts = pl.get("allPodcasts")

    unplayed_non_deleted = 0
    for ep in episodes:
        if ep.get("deleted", False):
            continue
        if ep.get("status") != "unplayed":
            continue
        if all_podcasts or ep["podcastUuid"] in pl.get("podcastUuids", []):
            unplayed_non_deleted += 1

    print(f"{name}")
    print(f"  kind={kind}, shows={show_count}, explicit_eps={ep_count}")
    print(f"  finished={finished}, downloaded={downloaded}, notDownloaded={not_downloaded}, starred={starred}, allPodcasts={all_podcasts}")
    print(f"  Estimated unplayed non-deleted matches: {unplayed_non_deleted}")
    print()
EOF
