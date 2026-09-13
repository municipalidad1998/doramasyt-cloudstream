# PlayTorrio Stremio Bridge

The PlayTorrio integration uses the Stremio addon protocol as the source interface. An addon manifest declares its resources and a stream resource returns playable stream objects.

PlayTorrio flow:

`manifest.json -> stream/{type}/{id}.json -> normalized StreamSource -> embedded media_kit/libmpv player`

The adapter preserves `name`, `title`, `url`, `externalUrl`, `infoHash`, `fileIdx`, `sources`, `behaviorHints`, and supported request headers. For direct HTTP media, the internal player receives the URL and headers. Torrent metadata remains in PlayTorrio's existing torrent pipeline.

This bridge is protocol-level integration only. It does not implement scraping, DRM circumvention, authentication bypass, or access-control bypass.
