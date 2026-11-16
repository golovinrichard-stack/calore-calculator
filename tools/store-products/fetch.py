#!/usr/bin/env python3
"""Step 1 of the shop products seed: candidate products from Open Food Facts.

Two ways, both keeping only products sold in Czechia whose brand is a shop's own brand (Pilos at
Lidl, K-Classic at Kaufland, Tesco, Clever at Billa, Albert Quality at Albert …), so the shop is
certain:
- by food (default): searches every basic food's name; writes {food key: {candidates: [...]}}.
- by brand (--by-brand): lists every product of every own brand; writes {"products": [...]}.
Step 2 (a reviewer, see README) keeps only the products that really are a basic food, and
build.py writes the app's seed file.

Usage (from the repository root, needs internet):
    python3 tools/store-products/fetch.py --out tools/store-products/candidates.json
    python3 tools/store-products/fetch.py --by-brand --out tools/store-products/brand-products.json

Python 3, standard library only. About one request every 2 seconds.
"""
import argparse
import json
import os
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FOODS = os.path.join(ROOT, "app/src/main/assets/foods/generic-cz.json")
SEARCH = "https://search.openfoodfacts.org/search"
USER_AGENT = "Calore-dataset-tool/0.14 (store products seed)"

# Own brands: products of these brands are sold only in that chain (store keys of seed/cz/stores.json).
OWN_BRANDS = {
    "cz:lidl": ["lidl", "pilos", "milbona", "chef select", "combino", "freshona", "kania", "alesto", "vitasia", "baresa",
                "crownfield", "golden seafood", "ocean sea", "nixe", "sol mar", "eridanous", "italiamo", "deluxe", "snack day",
                "favorina", "fin carre", "mister choc", "bellarom", "solevita", "dulano", "vemondo", "pikok", "rivercote",
                "lovilio", "tastino", "trattoria alfredo", "sondey", "mcennedy", "lord nelson", "gelatelli", "linessa"],
    "cz:kaufland": ["kaufland", "k classic", "k bio", "k jarmark", "k favourites", "k purland", "k take it veggie", "k free from", "k to go", "exquisit"],
    "cz:tesco": ["tesco", "tesco finest", "tesco value", "tesco organic", "tesco free from", "tesco plant chef", "stockwell co", "hearty food co"],
    "cz:albert": ["albert", "albert quality", "albert excellent", "albert bio", "nature s promise"],
    "cz:billa": ["billa", "clever", "billa bio", "billa premium", "ja naturlich"],
    "cz:penny": ["penny"],
    "cz:globus": ["globus"],
}


def norm(text):
    text = unicodedata.normalize("NFKD", text.lower())
    text = "".join(c for c in text if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", " ", text).strip()


BRAND_TO_STORE = {norm(b): store for store, brands in OWN_BRANDS.items() for b in brands}


def store_of(brands):
    for brand in brands if isinstance(brands, list) else str(brands or "").split(","):
        store = BRAND_TO_STORE.get(norm(brand))
        if store:
            return store, brand.strip()
    return None, None


def pack_of(hit):
    """Pack size in grams or millilitres, from product_quantity or the quantity text."""
    unit = (hit.get("product_quantity_unit") or "").lower()
    amount = hit.get("product_quantity")
    try:
        amount = float(amount) if amount is not None else None
    except (TypeError, ValueError):
        amount = None
    if amount and unit in ("g", "ml"):
        return amount, "ML" if unit == "ml" else "G"
    m = re.search(r"(\d+(?:[.,]\d+)?)\s*(kg|g|ml|l)\b", str(hit.get("quantity") or "").lower())
    if not m:
        return (amount, "G") if amount and not unit else (None, None)
    value = float(m.group(1).replace(",", "."))
    return {"kg": (value * 1000, "G"), "g": (value, "G"), "l": (value * 1000, "ML"), "ml": (value, "ML")}[m.group(2)]


def search_term(food):
    name = food["nameCs"].split(",")[0]
    name = re.sub(r"\(.*?\)", "", name)
    return name.strip()


def search(term, page=1, full=False):
    params = {
        "q": f'{term} countries_tags:"en:czech-republic"',
        "langs": "cs,en",
        "page_size": "100",
        "page": str(page),
        "fields": "code,product_name,product_name_cs,brands,quantity,product_quantity,product_quantity_unit,unique_scans_n",
    }
    request = urllib.request.Request(SEARCH + "?" + urllib.parse.urlencode(params), headers={"User-Agent": USER_AGENT})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=40) as response:
                answer = json.load(response)
                return answer if full else answer.get("hits", [])
        except Exception as e:  # network hiccup: wait and retry
            print(f"  retry {attempt + 1} for {term}: {e}", file=sys.stderr)
            time.sleep(5 * (attempt + 1))
    return {} if full else []


def candidate_of(hit):
    """A product of a shop's own brand with a known pack size, or None."""
    store, brand = store_of(hit.get("brands"))
    pack, unit = pack_of(hit)
    name = (hit.get("product_name_cs") or hit.get("product_name") or "").strip()
    if not store or not pack or not name or not hit.get("code"):
        return None
    return {"code": hit["code"], "name": name, "brand": brand, "store": store, "pack": pack, "unit": unit,
            "scans": hit.get("unique_scans_n") or 0}


def by_brand(out):
    """Every product of every own brand sold in Czechia."""
    products = {}
    for store, brands in OWN_BRANDS.items():
        for brand in brands:
            tag = norm(brand).replace(" ", "-")
            page, pages = 1, 1
            while page <= pages:
                answer = search(f'brands_tags:"{tag}"', page=page, full=True)
                pages = min(answer.get("page_count") or 1, 30)
                for hit in answer.get("hits", []):
                    candidate = candidate_of(hit)
                    if candidate and candidate["store"] == store:
                        products.setdefault(candidate["code"], candidate)
                print(f"{store} {tag} page {page}/{pages}: {len(products)} products so far", flush=True)
                page += 1
                time.sleep(2)
    with open(out, "w", encoding="utf-8") as f:
        f.write('{"products": [\n' + ",\n".join(json.dumps(p, ensure_ascii=False) for p in sorted(products.values(), key=lambda p: (p["store"], p["name"]))) + "\n]}\n")
    print(f"wrote {out}: {len(products)} products")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True)
    parser.add_argument("--by-brand", action="store_true")
    args = parser.parse_args()
    if args.by_brand:
        return by_brand(args.out)
    with open(FOODS, encoding="utf-8") as f:
        foods = json.load(f)["foods"]
    skip = re.compile(r"_(cooked|boiled|roasted|baked|mashed)$")
    result = {}
    for food in foods:
        key = food["key"]
        if skip.search(key):
            continue
        term = search_term(food)
        hits = search(term)
        candidates = [c for c in map(candidate_of, hits) if c]
        result[key] = {"nameCs": food["nameCs"], "nameEn": food["nameEn"], "term": term, "candidates": candidates}
        print(f"{key}: {len(hits)} hits, {len(candidates)} own-brand candidates", flush=True)
        time.sleep(2)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    print(f"wrote {args.out}: {sum(len(v['candidates']) for v in result.values())} candidates for {len(result)} foods")


if __name__ == "__main__":
    sys.exit(main())
