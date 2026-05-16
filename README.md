## Map-Sync

**Real-time terrain synchronization**: see exactly what your friends see, as they explore it.

Supports `Journeymap`, `Voxelmap`, and `Xaero's World Map` (+ minimap).

## [Download](https://modrinth.com/mod/mapsync/versions)

[Join the Discord for announcements, discussion, and support.](https://discord.gg/khMPvWjnKt)

## Usage

Drop the same MapSync jar into the `mods/` folder of:

- Your **Fabric client**, alongside your map mod of choice.
- The **Fabric server** you and your friends play on.

That's it. On join, the server tells your client where its MapSync endpoint is via a Fabric custom payload; the client auto-connects without any GUI interaction. The keybind (comma `,` by default) still opens the MapSync GUI if you want to inspect the connection, toggle the safeguard, or override the address manually.

## How it works

Every time anyone with the mod loads a chunk (even without a map mod installed), MapSync hashes the chunk and sends it to the server. The server stores it once, deduplicated by hash, and relays it to everyone else currently connected. When a compatible map mod is installed on your client, MapSync writes the received chunk into its tile cache so the area "lights up" on your map as your friends explore.

A per-chunk timestamp keeps order: older data never overwrites newer data, regardless of who saw it first.

### Preserving existing map data

If you already explored the world before installing MapSync, your local map data is preserved by default — the safeguard skips overwriting chunks the sync server hasn't shown you a newer version of. On first connection, MapSync seeds its per-chunk timestamps from Xaero's region-file mtimes, so updates from your friends still flow through as soon as they're genuinely newer than what you have locally.

Uncheck "Preserve existing map data" in the MapSync GUI to force a backfill of all chunks the server knows about (one-time, per server).

## Running a server

The bundled jar is the server. Install Fabric on your Minecraft server and drop the MapSync jar into its `mods/` folder. On startup, MapSync creates a `<world>/mapsync/` directory with `config.json`, `whitelist.json`, `uuid_cache.json`, and `db.sqlite`. The MC server's `whitelist.json` and ops list are auto-imported into MapSync's whitelist — there's no second list to maintain.

In-game commands available to operators (`/op` or `level 3`):

- `/mapsync status` — listening address, client count, whitelist size.
- `/mapsync whitelist list` — entries with cached IGNs.
- `/mapsync whitelist add|remove <player>` — accepts a UUID or a cached IGN.
- `/mapsync whitelist reload` — re-reads `whitelist.json` and re-imports MC's allowlist.
- `/mapsync clients list` — connected MapSync clients with auth state, dimension, and game address.
- `/mapsync clients kick <id>` — close one MapSync connection.

Default port is `12312/tcp`. To reach the server from a different host than the MC server, set `advertisedHost` in `config.json` and bounce the server.

## Copyright

Copyright (C) 2022 Map-Sync contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
