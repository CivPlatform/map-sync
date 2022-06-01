# base is shared between build/test and deploy
FROM node:18-alpine AS base

WORKDIR /usr/src/app/

# contains various scripts, so include in all images
COPY ./server/package.json /usr/src/app/package.json

FROM base AS build

COPY ./server/yarn.lock /usr/src/app/yarn.lock
RUN yarn

# copy source as late as possible, to reuse docker cache with node_modules
COPY ./server /usr/src/app
RUN yarn build

FROM build AS test
RUN yarn test

# final image only includes minimal files
FROM base AS deploy

COPY --from=build /usr/src/app/node_modules /usr/src/app/node_modules
COPY --from=build /usr/src/app/dist /usr/src/app/dist

ENV NODE_ENV=production
ENV HOST=0.0.0.0

#Mount your FS or volume or whatnot to this folder
RUN mkdir /mapsync_data
ENV MAPSYNC_DATA_DIR=/mapsync_data

EXPOSE 12312/tcp

CMD [ "yarn", "start" ]
