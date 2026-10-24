# The dev surface: MCP over `adb forward`

The debug build carries a control surface so an agent — or a developer — can
drive Lohe without tapping through the UI. It speaks the Model Context
Protocol, so the app shows up in an MCP client as a set of tools:

```
lohe_shows          the subscribed podcasts
lohe_show           one podcast and its recent episodes
lohe_subscribe      subscribe by feed URL or by catalog title
lohe_unsubscribe    drop a subscription
lohe_refresh_feeds  re-fetch every feed
lohe_search_catalog search the iTunes catalog

lohe_episodes       list episodes, filtered by show, state or text
lohe_episode        one episode in full
lohe_mark_played    mark listened, or take it back
lohe_star           star or unstar
lohe_archive        file away, or bring back
lohe_delete_episode soft-delete, or restore
lohe_download       download for offline listening
lohe_downloads      what is downloaded

lohe_queue          the Up Next queue
lohe_enqueue        add to the queue
lohe_dequeue        take one out
lohe_clear_queue    empty it
lohe_reorder_queue  rewrite the order

lohe_playlists      the playlists
lohe_playlist       one playlist and its episodes
lohe_create_playlist
lohe_playlist_add
lohe_playlist_remove
lohe_playlist_update
lohe_delete_playlist

lohe_play           play an episode now
lohe_pause          pause playback
lohe_now_playing    what is playing

lohe_settings       read the settings
lohe_set_setting    change one

lohe_import_pocketcasts  apply a Pocket Casts export to the library
```

## How it is put together

```
DevApi           the socket server: REST endpoints and the MCP endpoint
 └── McpHttp     the MCP transport: JSON-RPC in, JSON-RPC out
      └── McpTools  the advertised catalogue and the dispatcher
           └── LoheToolkit  every operation, once
```

One implementation, two front doors. The REST endpoints in `DevApi` call
the same operations with the same arguments as the MCP tools, so the two
cannot drift apart.

- `McpProtocol` — the JSON-RPC envelope. No Android in it, so the wire
  format is covered by ordinary JVM tests.
- `McpCatalog` — the tool names, descriptions and JSON schemas.
- `LoheToolkit` — the operations, keyed by name in a table. The toolkit
  checks at startup that every advertised tool is implemented, so a tool
  that is described but not wired up fails the build's startup rather than
  answering "no such tool".
- `McpPlayback` — playback control, through a `MediaController` on the same
  handle the UI uses.

The server binds `127.0.0.1:8765` inside the app, which means it is only
reachable through `adb forward`. It only runs in a debug build.

## Reaching it

```cmd
gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.lohe.app/.MainActivity
adb forward tcp:8765 tcp:8765
```

Then the endpoint is `http://127.0.0.1:8765/mcp`.

Two things end the session without being asked to:

- The app being killed. The server lives in the app process, so the
  endpoint goes with it; start the app again.
- The device dropping off the network. Over wireless debugging the adb
  session drops when the phone sleeps, and the forward goes with it — a
  client that suddenly cannot reach the port is usually this, not the app.
  `adb forward tcp:8765 tcp:8765` puts it back.

## Pointing a client at it

Every request needs the bearer token:

```
Authorization: Bearer lohe-dev
```

For OpenCode, add it to the global configuration
(`~/.config/opencode/opencode.jsonc`):

```jsonc
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "lohe": {
      "type": "remote",
      "url": "http://127.0.0.1:8765/mcp",
      "headers": {
        "Authorization": "Bearer lohe-dev"
      },
      "enabled": true
    }
  }
}
```

Check it with `opencode mcp list`; the server should show as connected.

In OpenCode V2 the same entry sits under `mcp.servers` instead of `mcp`,
and remote servers use OAuth unless `oauth: false` is set — Lohe has no
OAuth, only the bearer token.

## Syncing from Pocket Casts

The library can be brought into line with a Pocket Casts account: the same
episodes, in the same state, in the same playlists.

`tools/pocketcasts-export.py` reads the account and writes one flat
document — shows, episodes with their listening state, playlists, folders and
the up-next queue:

```cmd
python tools\pocketcasts-export.py --email you@example.com --password ... -o export.json
adb push export.json /data/local/tmp/export.json
adb shell "cat /data/local/tmp/export.json | run-as com.lohe.app sh -c 'cat > files/export.json'"
```

Then, through the MCP tool:

```jsonc
{ "name": "lohe_import_pocketcasts", "arguments": { "fileName": "export.json" } }
```

A small export can be handed over inline as `state` instead, but a whole
library is far too large for a tool call.

The import is careful in three ways that the Pocket Casts API makes
necessary:

- **It only moves state forward.** Pocket Casts calls an episode unplayed
  when it has no record of it, which is most of the library — it prunes the
  state of everything it has finished with. An unplayed row is therefore left
  alone: Lohe knows things Pocket Casts has forgotten.
- **It does not copy deletions.** Pocket Casts' per-episode flag says the
  row has been pruned from its sync state, not that the listener deleted the
  episode. Carrying it across would empty the library.
- **A playlist is copied as the rule it is.** Pocket Casts' playlists are
  mostly filters — these shows, this length — and a filter copied as a list
  of episodes would go stale the moment a new episode arrived. A playlist
  that filters by the state of the library (played, unplayed, downloaded)
  has no equivalent here and is reported as skipped rather than copied as an
  empty list.

Two shows cannot be brought across: *The Triple R* and *Heavy Metal
Historian*. Both of their feeds are dead — 404 and no response — so there is
nothing to subscribe to.

## The REST surface
The same operations are available as plain JSON endpoints, for `curl` and
for anything that would rather not speak JSON-RPC. All of them need the
bearer token too.

```
GET  /health
GET  /shows?folder=
GET  /episodes?show=<uuid>&state=&query=&limit=&offset=
GET  /playlists
GET  /playlist/{id}
GET  /settings
GET  /queue
GET  /downloads
GET  /now
POST /settings                 {"key":..., "value":...}
POST /episode/{uuid}/played    {"played": bool}
POST /episode/{uuid}/deleted    {"deleted": bool}
POST /episode/{uuid}/starred    {"starred": bool}
POST /episode/{uuid}/archived   {"archived": bool}
POST /playlist                 {"name":...}
POST /playlist/{id}/add        {"episodeUuid":...}
POST /playlist/{id}/remove     {"episodeUuid":...}
POST /playlist/{id}/delete
POST /queue/add                {"episodeUuid":...}
POST /queue/clear
POST /play                     {"episodeUuid":...}
POST /subscribe                {"feedUrl":...}
```

```cmd
curl -H "Authorization: Bearer lohe-dev" http://127.0.0.1:8765/shows
```

## What it will not do

- It is a debug-build tool. A release build has no server and no
  `BuildConfig.DEBUG` to check, so there is nothing to reach.
- It binds the loopback interface only. The device's own network cannot
  see it; only `adb forward` can.
- The token is fixed. It keeps another app on the device from driving the
  library by accident; it is not a secret and it protects nothing worth
  stealing.
- The server lives in the app process. If the app is killed the endpoint
  goes with it, and the app has to be started again.
- `lohe_refresh_feeds` and `lohe_subscribe` are network round trips, one
  per show. They are slow on purpose: a feed that cannot be read fails on
  its own rather than taking the rest of the refresh down with it.
