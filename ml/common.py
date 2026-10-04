"""Utilidades compartidas del pipeline de riesgo de WalkSecurity."""
import csv
import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).parent
RAW = ROOT / "data" / "raw"
DATA = ROOT / "data"
OUTPUT = ROOT / "output"

# Zona urbana de Pasto (para descartar coincidencias de otros lugares)
BBOX = (1.15, -77.34, 1.27, -77.22)

# Sufijos que distinguen etapas/sectores de un mismo barrio: "Aquine Alto I Sector 2" -> "Aquine Alto"
_SUFFIX = re.compile(
    r"\b(sector|etapa|p\.?h\.?|manzana|bloque|torre|conjunto|urbanizacion|reservado)\b.*$|\b(i{1,3}|iv|v|vi{0,3}|\d+)\b",
    re.I,
)


def normalize(text: str) -> str:
    """Minúsculas, sin tildes ni signos: para comparar nombres de barrios con titulares."""
    text = unicodedata.normalize("NFD", text.lower())
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9ñ ]+", " ", text).strip()


def base_name(name: str) -> str:
    cleaned = _SUFFIX.sub(" ", normalize(name))
    return re.sub(r"\s+", " ", cleaned).strip()


def load_barrios() -> list[dict]:
    """Barrios de Pasto (OpenStreetMap) agrupados por nombre base, con su centro."""
    path = DATA / "barrios.csv"
    if path.exists():
        with path.open(encoding="utf-8") as f:
            return [
                {**row, "lat": float(row["lat"]), "lng": float(row["lng"])}
                for row in csv.DictReader(f)
            ]
    elements = json.loads((RAW / "osm_barrios.json").read_text(encoding="utf-8"))["elements"]
    groups: dict[str, list] = {}
    for e in elements:
        name = e.get("tags", {}).get("name")
        lat = e.get("lat") or e.get("center", {}).get("lat")
        lng = e.get("lon") or e.get("center", {}).get("lon")
        if not name or lat is None:
            continue
        key = base_name(name)
        if len(key) >= 4:
            groups.setdefault(key, []).append((name, lat, lng))
    barrios = []
    for key, items in sorted(groups.items()):
        barrios.append({
            "key": key,
            "name": min((n for n, _, _ in items), key=len),
            "lat": round(sum(i[1] for i in items) / len(items), 6),
            "lng": round(sum(i[2] for i in items) / len(items), 6),
        })
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["key", "name", "lat", "lng"])
        writer.writeheader()
        writer.writerows(barrios)
    return barrios
