# PlayTorrio Stremio Bridge

This branch replaces the repository's provider purpose with a clean integration specification for PlayTorrio.

## Goal

A Stremio-compatible addon returns stream objects. PlayTorrio converts those objects to its existing `StreamSource` model and opens the resulting media URL in its embedded `media_kit/libmpv` player.

The integration must not launch an external player.

## Flow

`Stremio addon -> /stream/{type}/{id}.json -> normalized stream -> StreamSource -> PlayerScreen`

## Stream fields

Preserve `name`, `title`, `url`, `externalUrl`, `infoHash`, `fileIdx`, `sources`, `behaviorHints`, and `headers`.

If `behaviorHints.proxyHeaders.request` exists, merge those request headers into the playback headers.

## PlayTorrio integration

The PlayTorrio stream service should call the addon stream endpoint, map each returned object into its existing `StreamSource`, and send the selected source to `PlayerScreen`.

For direct HTTP/HLS/DASH media, `PlayerScreen` should open `source.url` using the existing internal media player and the normalized headers.

For torrent sources, keep PlayTorrio's existing `infoHash`/`fileIdx`/`sources` resolution pipeline.

## Scope

This adapter is protocol-level integration only. It does not bypass DRM, authentication, provider access controls, or other restrictions.