# Map-Sync

**Real-time terrain synchronization**: See exactly what your friends see, as they explore it.

Supports `Journeymap`, `Voxelmap` and `Xaero's World Map` (+minimap).

## [Download](https://modrinth.com/mod/mapsync/versions)

[Join the Discord for announcements, discussion, and support.](https://discord.gg/khMPvWjnKt)

## Usage

Join a Minecraft server, open the GUI via the keybind (comma `,` by default) or through Mod Menu, enter the address of your Sync Server, and click "Connect".

## How it works

When you connect, you receive all chunks that your friends have mapped since the last time you played (and were connected to the Sync Server).

Every time any of your friends load a chunk with Map-Sync installed (even if they don't use any map mods!), it gets mapped and the map data gets sent to the Sync Server. It will then send it to everyone else, and if you have a compatible map mod installed (Journeymap, Voxelmap or Xaero's), the mod will display your friends' chunks.

Map-Sync tracks a timestamp per chunk, so old data will never overwrite newer data.

## Running a server

By default, a whitelist will deny any connections, which can be turned off from the config file. (**Caution**)\
You can also add and remove players via the commands below or via the config files

<details>
<summary>Config file approach</summary>

- You can control who has access to a Sync Server by editing its `allowed-users.txt`. If someone connects who is not allowed access yet, their name and UUID gets written to `denied-users.txt`, from where you can just cut+paste it into `allowed-users.txt` and restart the server to grant access.

</details>

Client authentication can also be turned off for use with unauthenticated accounts. (ex. testing purposes)

### Server commands

Run these inside the command-line interface after starting the server.

```
whitelist_load
whitelist_save
whitelist_add_ign <name> -- requires the player to have connected in the past
whitelist_remove_ign <name> -- requires the player to have connected in the past
whitelist_add <uuid>
whitelist_remove <uuid>
list -- lists players
send <uuid/name> -- sends all available data to player
kick <uuid/name> -- kicks player from sync server
```

---

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
