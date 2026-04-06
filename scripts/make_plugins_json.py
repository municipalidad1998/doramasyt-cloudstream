import json, os, sys

providers = [
    {"n": "DoramasYTProvider",    "name": "DoramasYT",    "d": "Doramas con subtitulos en Espanol y Latino", "t": ["AsianDrama","TvSeries"]},
    {"n": "DoramasFlixProvider",  "name": "DoramasFlix",  "d": "Doramas subtitulados en espanol",            "t": ["AsianDrama","TvSeries"]},
    {"n": "DoramasiaProvider",    "name": "Doramasia",    "d": "Doramas y series asiaticas en espanol",      "t": ["AsianDrama","TvSeries"]},
    {"n": "PeliCineHDProvider",   "name": "PeliCineHD",   "d": "Peliculas y series en HD 1080P Latino",      "t": ["Movie","TvSeries"]},
    {"n": "PelisJuanitaProvider", "name": "PelisJuanita", "d": "Peliculas y series en espanol latino HD",    "t": ["Movie","TvSeries"]},
]

output_dir = sys.argv[1] if len(sys.argv) > 1 else "output"
raw  = "https://raw.githubusercontent.com/municipalidad1998/doramasyt-cloudstream/refs/heads/builds"
repo = "https://github.com/municipalidad1998/doramasyt-cloudstream"
icon = "https://raw.githubusercontent.com/municipalidad1998/doramasyt-cloudstream/main"

plugins = []
for p in providers:
    cs3  = f"{output_dir}/{p['n']}.cs3"
    size = os.path.getsize(cs3) if os.path.exists(cs3) else 0
    plugins.append({
        "url":           f"{raw}/{p['n']}.cs3",
        "status":        1,
        "version":       2,
        "name":          p["name"],
        "internalName":  p["n"],
        "authors":       ["municipalidad1998"],
        "description":   p["d"],
        "fileSize":      size,
        "repositoryUrl": repo,
        "language":      "es",
        "tvTypes":       p["t"],
        "iconUrl":       f"{icon}/{p['n']}/icon.png",
        "apiVersion":    1,
    })

out_file = f"{output_dir}/plugins.json"
with open(out_file, "w") as f:
    json.dump(plugins, f, indent=4, ensure_ascii=False)
print(f"✅ plugins.json generado con {len(plugins)} plugins en {out_file}")
