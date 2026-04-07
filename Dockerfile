# base is shared between build/test and deploy
FROM node:25-alpine AS base

ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"

WORKDIR /usr/src/app/

## so corepack knows pnpm's version
COPY ./mapsync-server/package.json ./mapsync-server/pnpm-lock.yaml ./mapsync-server/pnpm-workspace.yaml ./
## prevent prompt to download
ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0
## enable corepack and pnpm
RUN corepack enable
## setup for offline
RUN corepack pack
## don't call out to network anymore
ENV COREPACK_ENABLE_NETWORK=0

FROM base AS build
RUN --mount=type=cache,id=pnpm,target=/pnpm/store pnpm install --frozen-lockfile

# copy source as late as possible, to reuse docker cache with node_modules
COPY ./mapsync-server /usr/src/app
RUN pnpm run build

FROM build AS test
RUN pnpm run test

# final image only includes minimal files
FROM base AS deploy

COPY --from=build /usr/src/app/node_modules /usr/src/app/node_modules
COPY --from=build /usr/src/app/dist /usr/src/app/dist

ENV NODE_ENV=production
ENV HOST=0.0.0.0

#Mount your FS or volume or whatnot to this folder
RUN mkdir /data
ENV MAPSYNC_DATA_DIR=/data

EXPOSE 12312/tcp

CMD [ "pnpm", "start" ]
