import json
state = json.load(open('out/pocketcasts-state.json', encoding='utf-8'))
for pl in state['playlists']:
    if pl['name'] == 'Music':
        print('Pocket Casts Music:')
        print(f'  kind={pl["kind"]}')
        print(f'  podcastUuids={len(pl["podcastUuids"])}')
        print(f'  episodeUuids={len(pl["episodeUuids"])}')
        print(f'  minDuration={pl["minDurationMinutes"]} maxDuration={pl["maxDurationMinutes"]}')
        print(f'  allPodcasts={pl["allPodcasts"]}')
