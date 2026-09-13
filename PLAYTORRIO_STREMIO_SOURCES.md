# PlayTorrio Stremio Sources

This repository contains a source registry for Stremio-compatible integrations used by PlayTorrio.

Only sources that expose a compatible Stremio addon manifest/stream endpoint should be connected. Website URLs are kept as source references; PlayTorrio must consume addon manifests and stream responses rather than scrape or bypass site protections.

## Requested source references

- https://latanime.org/
- https://jkanime.net/
- https://animejara.com/inicio
- https://tioanime.com/
- https://wwv.veranimes.net/
- https://animeav1.com/
- https://www.mundodonghua.com/
- https://estrenosanime.net/
- https://lamovie.org/
- https://hackstore2.com/
- https://aether.ist/
- https://pelispedia.mov/
- https://streamxhd.com/
- https://detodopeliculas.nu/
- https://gambeta.vip/
- https://pelispedia.is/
- https://entrepeliculasyseries.nz/
- https://ww2.tlnovelas.net/
- https://doramasflix.co/
- https://www.doramasyt.com/
- https://sololatino.net/

## Integration contract

For each compatible addon, PlayTorrio should resolve its manifest, request `/stream/{type}/{id}.json`, normalize returned stream objects, and pass direct media URLs plus request headers to its existing embedded media_kit/libmpv player. `externalUrl` entries should not be handed to another player.

Stremio stream responses may contain HTTP URLs, torrent metadata, headers, behavior hints, or external links. The adapter must preserve supported fields and reject unsupported/unsafe schemes instead of attempting to bypass DRM, authentication, paywalls, or access controls.
