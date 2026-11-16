# Shop products

Builds `app/src/main/assets/seed/cz/store-products.json`: real products of the shops' own brands
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
| 1 | `fetch.py` | For every basic food (not the cooked ones), searches Open Food Facts for products sold in Czechia and keeps those of a shop's own brand with a known pack size. About one request every 2 seconds. Writes `candidates.json` (the one used for version 1, fetched on 16 November 2026, is in this folder). |
| 2 | `review/*.json` | A reviewer (people or AI helpers, here AI helpers) keeps only the candidates that really are that basic food: plain chicken breast for raw chicken breast, not breaded or smoked chicken. Format: `{"generic:food_key": ["barcode", …]}`. |
| 3 | `build.py` | Writes the asset: per shop and food at most three reviewed products, the most scanned first (it becomes the shop's preferred product until the user picks another). |

## Rebuild

```bash
python3 tools/store-products/fetch.py --out tools/store-products/candidates.json
python3 tools/store-products/build.py --candidates tools/store-products/candidates.json --version 2
```

After a new fetch, review the new candidates again (step 2) before building.

Raise `--version` whenever the file changes; phones import a newer version on the next start (a
product the user picked as preferred stays preferred). Python 3, standard library only.

Shop names and brand names are trademarks of their owners; they only label shops and products.
