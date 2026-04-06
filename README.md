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

You can learn how to setup and manage your mapsync server in [docs/getting-started](./docs/getting-started.md)

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
