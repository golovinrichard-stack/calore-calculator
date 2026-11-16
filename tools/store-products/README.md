# Shop products

Builds `app/src/main/assets/seed/cz/store-products.json` (version 1: 262 products for 102 basic foods): real products of the shops' own brands
(Pilos and Milbona at Lidl, K-Classic at Kaufland, Tesco, Albert, Clever and Billa, Penny …), each
matched to a basic food, so the shopping list can say exactly what to buy in which shop. The data is
from Open Food Facts (ODbL 1.0): product name, brand, barcode and pack size from the product label.
**No prices**: Open Food Facts has none, and we don't copy shop price lists (see
[DATA_SOURCES section 4](../../docs/DATA_SOURCES.md#4-exact-products-in-czech-shops)). The user types
the price they paid when ticking the list.

An own brand is sold only in its chain, so the brand tells the shop for certain. Products of other
brands (sold in many shops) are left out: the user can still pick them in the app.

| Step | File | What it does |
|---|---|---|
| 1 | `fetch.py` | Collects products sold in Czechia of a shop's own brand with a known pack size, two ways: by searching every basic food's name (not the cooked ones) → `candidates.json`, and by listing every product of every own brand (`--by-brand`) → `brand-products.json`. About one request every 2 seconds. The files used for version 1 (fetched on 16 November 2026) are in this folder. |
| 2 | `review/*.json` | A reviewer (people or AI helpers; for version 1, AI helpers) keeps only the products that really are a basic food: plain chicken breast for raw chicken breast, not breaded or smoked chicken. `01`–`05` reviewed the search results food by food, `b01`–`b08` matched the brand lists to the food list. Format: `{"generic:food_key": ["barcode", …]}`. |
| 2b | `excluded.json` | The final check by a person or the main reviewer: reviewed products left out after all, with the reason (`{"barcode": "reason"}`), for example names only in German or French, or an unsure match. Products with letters of other alphabets in the name are also left out by `build.py`. |
| 3 | `build.py` | Writes the asset: per shop and food at most three reviewed products, the most scanned first (it becomes the shop's preferred product until the user picks another). |

## Rebuild

```bash
python3 tools/store-products/fetch.py --out tools/store-products/candidates.json
python3 tools/store-products/fetch.py --by-brand --out tools/store-products/brand-products.json
python3 tools/store-products/build.py --version 2 \
    --candidates tools/store-products/candidates.json tools/store-products/brand-products.json
```

After a new fetch, review the new candidates again (step 2) before building.

Raise `--version` whenever the file changes; phones import a newer version on the next start (a
product the user picked as preferred stays preferred). Python 3, standard library only.

Shop names and brand names are trademarks of their owners; they only label shops and products.
