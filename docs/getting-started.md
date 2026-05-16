# Getting Started

MapSync now ships as a single Fabric jar that serves as both the client mod and the bundled server. There's no separate server binary, no Node.js, no Docker.

## Server install

1. Run a Fabric Minecraft server matching the MapSync jar's Minecraft version (see the jar filename — e.g. `MapSync-2.2.0-SNAPSHOT-26.1.2.jar` is for MC 26.1.2).
2. Drop the MapSync jar into your server's `mods/` folder. The `fabric-api` mod must also be present.
3. Start the server.

On first start, MapSync creates `<world>/mapsync/` containing:

- `config.json` — port (`12312` default), host, auth toggle, whitelist toggle, advertised host.
- `whitelist.json` — UUID allowlist. MC's `whitelist.json` and ops list are auto-imported.
- `uuid_cache.json` — name → UUID resolution for `/mapsync whitelist add <ign>`.
- `db.sqlite` — chunk and timestamp persistence.

If you're migrating from the standalone `mapsync-server`, copy your old `db.sqlite`, `whitelist.json`, and `uuid_cache.json` into `<world>/mapsync/`. The on-disk formats are identical.

## Client install

Drop the same MapSync jar into your **Fabric client's** `mods/` folder, alongside whichever map mod you use (Xaero's World Map, JourneyMap, or Voxelmap). Join the server normally — the MapSync handshake runs automatically:

1. The server pushes a `mapsync:sync_address` payload on join.
2. The client opens a websocket to that address.
3. The auth handshake runs through Mojang's session server (or offline-mode if the server is configured with `auth: false`).
4. The server checks the connecting UUID against its whitelist.
5. Chunks start streaming.

Open the MapSync GUI in-game with the comma `,` keybind (or via Mod Menu) to see connection status, toggle auto-connect, or flip the "Preserve existing map data" safeguard.

## Operator commands

All commands require op (`level 3`):

| Command | Effect |
|---|---|
| `/mapsync status` | Listening address, client count, config flags, on-disk path. |
| `/mapsync whitelist list` | All whitelisted entries with cached IGNs. |
| `/mapsync whitelist add <uuid-or-ign>` | Add to whitelist; IGN must have joined the MC server before. |
| `/mapsync whitelist remove <uuid-or-ign>` | Remove from whitelist. |
| `/mapsync whitelist reload` | Re-read `whitelist.json` and re-import MC's allowlist. |
| `/mapsync clients list` | Connected MapSync clients with auth state, dimension, game address. |
| `/mapsync clients kick <id>` | Close one connection. |

## Configuration

`<world>/mapsync/config.json`:

```json
{
  "host": "0.0.0.0",
  "port": 12312,
  "whitelist": true,
  "auth": true,
  "advertisedHost": ""
}
```

- `host`: bind address for the websocket. `0.0.0.0` accepts from anywhere.
- `port`: TCP port for the websocket. Default `12312`.
- `whitelist`: if `true`, only UUIDs in `whitelist.json` (or MC's whitelist/ops) can connect.
- `auth`: if `true`, run Mojang session-server auth on connection. Disable only for offline-mode servers.
- `advertisedHost`: hostname the client should connect to. Empty (default) means "use the same host as the MC server" — fine for single-host deployments. Override if MapSync runs behind a reverse proxy with a separate hostname.
