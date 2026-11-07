# Image du client : build Vite, puis nginx qui sert les fichiers compilés
# et transmet /api au serveur.

# Étape 1 : compilation du client
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Étape 2 : seulement nginx et les fichiers statiques
FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
