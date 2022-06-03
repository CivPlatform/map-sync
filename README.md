# Map-Sync

Share map data with your friends, live, privately.
Supports Journeymap and Voxelmap.

## [Download](https://github.com/CivPlatform/map-sync/releases)

[Join the Discord for announcements, discussion, and support.](https://discord.gg/khMPvWjnKt)

## Usage

Join a Minecraft server, press the GUI keybind (comma `,` by default), enter the address of your Sync Server, and click "Connect".

## How it works

When you connect, you receive all chunks that your friends have mapped since the last time you played (and were connected to the Sync Server).

Every time any of your friends load a chunk with Map-Sync installed (even if they don't use any map mods!), it gets mapped and the map data gets sent to the Sync Server. It will then send it to everyone else, and if you have a compatible map mod installed (Journeymap or Voxelmap), the mod will display your friends' chunks.

Map-Sync tracks a timestamp per chunk, so old data will never overwrite newer data.

You can control who has access to a Sync Server by editing its `allowed-users.txt`. If someone connects who is not allowed access yet, their name and UUID gets written to `denied-users.txt`, from where you can just cut+paste it into `allowed-users.txt` and restart the server to grant access.

## Running a server

<details open>
<summary>Docker Install (recommended)</summary>
<br />

1. [Install the Docker Engine](https://docs.docker.com/engine/install/), if you haven't already.
2. [Install Docker Compose](https://docs.docker.com/compose/install/) (We're using Docker Compose V2, so update if you haven't already done so.)
3. Open a terminal.
4. Clone our code. 
    - `git clone https://github.com/CivPlatform/map-sync.git`
5. Change your working directory. 
    - `cd map-sync/`
6. To run the server with interactive prompt: 
    - `docker compose run --rm -it -p 12312:12312 map-sync`
    - To stop the interactive prompt: hit ctrl-c twice
7. To run the server headless: 
    - `docker compose up map-sync -d`
    - To stop the headless server: `docker compose down map-sync`
</details>

<details>
<summary>System Install</summary>
<br />

- install recent nodejs (~17)
- clone code, `cd server`
- `npm install`
- `npm run build` -- this has to be run after every time the code is edited
- `npm run start`
- to stop, press Ctrl+C twice
</details>

### Server commands

Run these inside the command-line interface after starting the server.

```
whitelist_load
whitelist_save
whitelist_add_ign <name> -- requires the player to have connected in the past
whitelist_remove_ign <name> -- requires the player to have connected in the past
whitelist_add <uuid>
whitelist_remove <uuid>
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
