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
