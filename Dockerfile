# base is shared between build/test and deploy
# See options at: https://hub.docker.com/r/oven/bun
FROM oven/bun:latest AS base

WORKDIR /usr/src/app/

# contains various scripts, so include in all images
COPY ./server/package.json /usr/src/app/package.json

FROM base AS build

COPY ./server/bun.lock /usr/src/app/bun.lock
COPY ./server/bunfig.toml /usr/src/app/bunfig.toml
RUN bun install

# copy source as late as possible, to reuse docker cache with node_modules
COPY ./server /usr/src/app

# final image only includes minimal files
FROM base AS deploy

COPY --from=build /usr/src/app/bun.lock /usr/src/app/bun.lock
COPY --from=build /usr/src/app/bunfig.toml /usr/src/app/bunfig.toml
COPY --from=build /usr/src/app/node_modules /usr/src/app/node_modules
COPY --from=build /usr/src/app/src /usr/src/app/src

ENV NODE_ENV=production
ENV HOST=0.0.0.0

#Mount your FS or volume or whatnot to this folder
# TODO: Fix env override of config data
ENV MAPSYNC_DATA_DIR=/data

# EXPOSE 12312/tcp

CMD [ "bun", "run", "start" ]
