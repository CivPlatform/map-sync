# Configuration

## File

> config.json

```json
{
  // Whether map-sync uses a whitelist
  "whitelist": true
}
```

_(Make sure that there are no comments in the actual config file)_

## Environment Variables

| Variable            | Description                                                                           |
| ------------------- | ------------------------------------------------------------------------------------- |
| `MAPSYNC_DATA_DIR`  | Used to chanage the folder map-sync uses to store things like the db and config files |
| `WHITELIST_URL`     | Fetches whitelist file from a url                                                     |
| `GAME_ADDRESS`      | Only allow a client to connect from a specific minecraft server                       |
| `MAPSYNC_DUMB_TERM` | Disables the cli                                                                      |
| `SQLITE_PATH`       | Specify a custom path and name for the database file **(not recommended)**            |
| `PORT`              | Specify a custom port                                                                 |
| `HOST`              | Specify a custom host **(not recommended)**                                           |
| `DISABLE_AUTH`      | Disables authentication with Mojang servers **(highly not recommended)**              |
