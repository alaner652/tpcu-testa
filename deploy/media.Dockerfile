FROM node:22-bookworm-slim

RUN apt-get update \
  && apt-get install -y --no-install-recommends ca-certificates chromium ffmpeg \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
