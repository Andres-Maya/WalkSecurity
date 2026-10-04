"""
Paso 1b: los titulares casi nunca dicen el barrio; el cuerpo de la noticia sí. Este paso abre cada
noticia de delito en Pasto y guarda SOLO datos derivados (barrio mencionado y franja horaria) en
data/raw/article_facts.jsonl. No se guarda el texto de los artículos.

    python fetch_articles.py
"""
import html
import json
import re
import time
import urllib.parse
import urllib.request

from common import RAW, load_barrios, normalize
from extract_incidents import CATEGORIES, HOURS, NOT_AN_EVENT, PLACE_WORDS

HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) WalkSecurity-research/1.0 (proyecto academico)"}
BATCH_URL = "https://news.google.com/_/DotsSplashUi/data/batchexecute"
RESOLVED = re.compile(r'garturlres\\",\\"(https?://[^"\\]+)')
PARAGRAPH = re.compile(r"<p[^>]*>(.*?)</p>", re.S | re.I)
TAG = re.compile(r"<[^>]+>")
MAX_ARTICLES = 400


def get(url: str, data: bytes | None = None) -> str:
    request = urllib.request.Request(url, data=data, headers=HEADERS)
    with urllib.request.urlopen(request, timeout=20) as response:
        return response.read(1_500_000).decode("utf-8", "replace")


def resolve(link: str) -> str | None:
    """Los enlaces de Google News son redirecciones cifradas: se piden los datos para resolverlas."""
    page = get(link)
    signature = re.search(r'data-n-a-sg="([^"]+)"', page)
    timestamp = re.search(r'data-n-a-ts="([^"]+)"', page)
    if not signature or not timestamp:
        return None
    article_id = link.split("/articles/")[1].split("?")[0]
    inner = json.dumps([
        "garturlreq",
        [["X", "X", ["X", "X"], None, None, 1, 1, "US:en", None, 1, None, None, None, None, None, 0, 1],
         "X", "X", 1, [1, 1, 1], 1, 1, None, 0, 0, None, 0],
        article_id, int(timestamp.group(1)), signature.group(1),
    ])
    payload = json.dumps([[["Fbv4je", inner, None, "generic"]]])
    body = get(BATCH_URL, data=("f.req=" + urllib.parse.quote(payload)).encode())
    match = RESOLVED.search(body)
    return match.group(1) if match else None


def facts(article_html: str, barrios: list[dict]) -> dict:
    paragraphs = [normalize(html.unescape(TAG.sub(" ", p))) for p in PARAGRAPH.findall(article_html)]
    text = " ".join(p for p in paragraphs if len(p) > 60)[:6000]   # cuerpo, sin menús ni pies
    found = None
    for barrio in barrios:
        # En el cuerpo solo se acepta la forma explícita "barrio X" / "sector X" (menos falsos positivos)
        match = re.search(rf"\b{PLACE_WORDS}\s+(?:de\s+)?(?:el |la |los |las )?{re.escape(barrio['key'])}\b", text)
        if match and (found is None or match.start() < found[0]):
            found = (match.start(), barrio["name"])
    hour = next((h for p, h in HOURS if re.search(p, text)), None)
    return {"mentionsPasto": "pasto" in text, "barrio": found[1] if found else None, "hour": hour}


def main() -> None:
    barrios = load_barrios()
    for b in barrios:   # "El Común" también aparece como "barrio Común"
        b["key"] = re.sub(r"^(el|la|los|las) ", "", b["key"])
    candidates = []
    with (RAW / "news.jsonl").open(encoding="utf-8") as f:
        for line in f:
            item = json.loads(line)
            title = normalize(item["title"].rsplit(" - ", 1)[0])
            if "pasto" not in title and "pasto" not in normalize(item["source"]):
                continue
            if NOT_AN_EVENT.search(title) or not any(re.search(p, title) for p, _, _ in CATEGORIES):
                continue
            candidates.append(item)
    candidates = candidates[:MAX_ARTICLES]
    # Reanudable: lo ya leído en una ejecución anterior no se vuelve a pedir
    facts_path = RAW / "article_facts.jsonl"
    done = set()
    if facts_path.exists():
        with facts_path.open(encoding="utf-8") as f:
            done = {json.loads(line)["link"] for line in f}
    candidates = [c for c in candidates if c["link"] not in done]
    print(f"{len(candidates)} noticias de delitos en Pasto por revisar ({len(done)} ya leídas)", flush=True)

    ok = with_barrio = 0
    with facts_path.open("a", encoding="utf-8") as out:
        for index, item in enumerate(candidates, 1):
            record = {"link": item["link"]}
            try:
                url = resolve(item["link"])
                if url:
                    record.update(facts(get(url), barrios))
                    record["domain"] = urllib.parse.urlparse(url).netloc
                    ok += 1
                    with_barrio += record["barrio"] is not None
            except Exception as error:   # sitios que bloquean, tiempo agotado, etc.
                record["error"] = str(error)[:80]
            out.write(json.dumps(record, ensure_ascii=False) + "\n")
            out.flush()
            if index % 20 == 0 or index == len(candidates):
                print(f"[{index}/{len(candidates)}] leídas: {ok} · con barrio: {with_barrio}", flush=True)
            time.sleep(1.0)
    print(f"Listo: {ok} artículos leídos, {with_barrio} con barrio")


if __name__ == "__main__":
    main()
