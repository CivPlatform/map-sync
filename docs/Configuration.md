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

| Variable           | Description                                                                           |
| ------------------ | ------------------------------------------------------------------------------------- |
| `MAPSYNC_DATA_DIR` | Used to chanage the folder map-sync uses to store things like the db and config files |
| `WHITELIST_URL`    | Fetches whitelist file from a url                                                     |
