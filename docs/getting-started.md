# Getting Started

## Installing The Server

The MapSync server is **recommened** to be run install a docker container

1. [Install the Docker Engine](https://docs.docker.com/engine/install/), if you haven't already.
2. [Install Docker Compose](https://docs.docker.com/compose/install/) (We're using Docker Compose V2, so update if you haven't already done so.)
3. Open a terminal.
4. Clone our code.
    - `git clone https://github.com/CivPlatform/map-sync.git`
5. Change your working directory.
    - `cd map-sync/`
6. To run the server:
    - `docker compose up -d`
    - To stop the headless server: `docker compose down`

Your MapSync server is now running!

## Updating Your MapSync Server

Updating your MapSync server is breeze!

1. Return to the folder you install the server too
    - Should be the one with the `docker-compose.yaml` file
2. Download the update and restart the server
    - `docker compose up -d --pull "always"`

Your now running the latest MapSync server!

## Configuring MapSync

By default, a whitelist will deny any connections, which can be turned off from the config file. (**Caution**)\
You can also add and remove players via the commands below or via the config files

### Using the CLI

If you want to manage your mapsync server via the CLI:

1. Return to the folder you install the server too
    - Should be the one with the `docker-compose.yaml` file
2. Attach to the container
    - `docker compose run --rm -it -p 12312:12312 map-sync`
3. Run any of these commands as you wish:

```
help -- prints more info about below commands
whitelist_load -- reload whitelist
whitelist_save -- force whitelist to save
whitelist_add_ign <name> -- requires the player to have connected in the past
whitelist_remove_ign <name> -- requires the player to have connected in the past
whitelist_add <uuid> -- add to whitelist by UUID
whitelist_remove <uuid> -- remove from whitelist by UUID
list -- lists players
send <uuid/name> -- sends all available data to player
kick <uuid/name> -- kicks player from sync server
```

4. When your down, use the key sequence to detach from the contaier
    - `CTRL-p CTRL-q`
    - **DO NOT USE** `CTRL-c`, this will stop the server
        - If you did accidentally stop the server, run `docker compose up -d`

### Editing The Config Files

You can control who has access to a Sync Server by editing its `allowed-users.txt`. If someone connects who is not allowed access yet, their name and UUID gets written to `denied-users.txt`, from where you can just cut+paste it into `allowed-users.txt` and restart the server to grant access.

These files can be found in the `data` directory
