FROM node:22-bookworm-slim

RUN apt-get update \
  && apt-get install -y --no-install-recommends ca-certificates chromium chromium-l10n ffmpeg fonts-noto-cjk \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
