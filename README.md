# DoramaYT CloudStream

Base de extensión para CloudStream.

## Estructura

- `DoramaYT/` — módulo de la extensión.
- `DoramaYT/src/main/kotlin/.../DoramaYTScraper.kt` — parser inicial para DoramaYT.
- La capa de red/parser está separada del contrato específico del fork de CloudStream para facilitar su integración.

## Fuentes previstas

La documentación original de este repositorio incluye las fuentes solicitadas para PlayTorrio/Stremio. No se copian proveedores JavaScript de Nuvio de forma directa porque CloudStream utiliza otro contrato de proveedor.

## Siguiente integración

Para conectar el módulo directamente a una versión concreta de CloudStream hay que apuntarlo al SDK/API de esa versión y registrar una clase Provider/AnimeProvider/DramaProvider según corresponda.
