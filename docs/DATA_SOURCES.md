# Data sources

Where Calore gets its information about foods, products, prices, recipes and health, under which licence, and what goes over the network.

> **Date:** 6 November 2026. Update this file whenever a data source or a network connection is added or changed (rule 6 in [`implementation/README.md`](implementation/README.md)).

**Status:** *in use* = the app uses it today; *planned (Mx)* = will be added in that milestone; *waiting* = needs an input from the project owner; *candidate* = possible later.

## Overview

| Data | Source | Licence | How the app gets it | Status |
|---|---|---|---|---|
| Branded products, barcodes, nutrition | Open Food Facts | ODbL | Online, from the phone | Search and barcode: in use |
| Basic foods (raw chicken, rice, vegetables …) | USDA FoodData Central | Public domain (CC0) | Shipped inside the app | In use (M2): 278 foods |
| Czech basic foods missing in USDA (tvaroh, Czech bread …) | Czech product labels on Open Food Facts; maybe NutriDatabaze.cz | ODbL; NutriDatabaze unknown | Shipped inside the app | In use (M2): 6 foods; NutriDatabaze waiting |
| Exact products in Czech shops | The owner's research; the user's own picks; Open Food Facts | Own data; ODbL | Shipped seed file; picked in the app | Waiting (M5) |
| Prices | What the user paid; the owner's research | Own data | Typed in the app; seed file | Planned (M5) |
| List of shops | Written by us | 0BSD | Shipped seed file | Planned (M5) |
| Recipes | Starter pack written by us; the user's own | 0BSD | Shipped seed file; the app | Planned (M3) |
| Storage times | Czech and EU consumer advice, USDA, NHS | Public guidance | Default values in the code | Planned (M4) |
| Calorie and body-fat formulas | Published equations | Facts | In the code | In use; more in M1 |
| Cycle and nutrition science | Research papers | Facts | Tip texts | Planned (M7) |

---

## 1. Open Food Facts

**What:** a free, community-built database of food products from all over the world: product names (in Czech when someone entered them), brands, barcodes, nutrition per 100 g, serving and pack sizes, shops, categories and allergens.

**Used for:** the food search (today), the barcode scanner (M2), picking exact products for the shopping list (M5), and the values of some Czech basic foods (M2).

**How:** the phone talks to Open Food Facts directly:

| Request | Address | Since |
|---|---|---|
| Search | `https://search.openfoodfacts.org/search` | today |
| One product by barcode | `https://world.openfoodfacts.org/api/v2/product/<barcode>?fields=…` (only the fields Calore reads) | M2 (in use) |

**What is sent:** only the words typed, or the barcode; the language (`cs,en` for Czech phones); the app's country as a search filter (`countries_tags:"en:czech-republic"`); and the app name in the user agent (`Calore/<version> (Android; open-source calorie tracker)`). Like any website, Open Food Facts sees the phone's internet address.

**Licence:** the database is under the Open Database License (ODbL); single entries under the Database Contents License; product photos under CC BY-SA. We must credit "Food data © Open Food Facts contributors" (README and the About screen). Data we derive from it and ship, such as Czech basic foods taken from labels, stays under ODbL.

**Quality:** anyone can edit Open Food Facts, so values can be missing or wrong, and Czech products are not all there. Missing values show as "—", never as zero. Users can fix products on openfoodfacts.org, and can always create their own foods.

**Country filter (checked 6 November 2026, M2.3):** the search API (search-a-licious) accepts a filter inside the query: `q=tvaroh countries_tags:"en:czech-republic"` returns only products sold in Czechia. Calore searches with this filter first; if it finds fewer than 10 products, it searches again without the filter and lists the other products after the Czech ones. Characters with a meaning in the query syntax (`: " ( ) *` …) are removed from what the user typed. The `langs` parameter must include `en`: `langs=cs` alone returns nothing, `langs=cs,en` works. The parser also reads the pack size (`product_quantity` or `quantity`), `stores_tags`, `categories_tags` (mapped to a shop section, best effort) and `allergens_tags`.

## 2. USDA FoodData Central

**What:** laboratory-analysed nutrient values for basic foods, published by the US Department of Agriculture. We use the *Foundation Foods* and *SR Legacy* data sets.

**Used for:** about 300 basic foods with Czech and English names (M2.2), including iron, magnesium and vitamin C, which the cycle tips need (M7).

**How:** the builder downloads the files once from <https://fdc.nal.usda.gov/>. A script in `tools/generic-foods/` picks the foods and writes `app/src/main/assets/foods/generic-cz.json`. **The app never connects to USDA.** Dataset version 1 (6 November 2026): 278 foods from SR Legacy (April 2018) and Foundation Foods (December 2025), plus 6 Czech foods from Open Food Facts labels: 284 in total. Every food records its source (`usda:<fdcId>` or `off:<barcode>`).

**Licence:** public domain (CC0 1.0). No conditions; we still credit it in About.

**Notes:** the data is American. For basic, unprocessed foods (meat, rice, vegetables, oils) the values are close to what is sold in Czechia. Czech names are our own translations, reviewed by the owner.

## 3. Czech food composition database (NutriDatabaze.cz)

**What:** the official Czech food composition database, run by ÚZEI (Ústav zemědělské ekonomiky a informací) in Prague: foods on the Czech market, with Czech and English names.

**Status:** *candidate, waiting.* We couldn't find terms that allow reuse inside an app. **Owner input #2:** ask ÚZEI for permission. Until there is a written yes, the app uses none of its values.

## 4. Exact products in Czech shops

**Why:** the shopping list should say exactly what to buy in each shop, for example "Lidl: Kuřecí prsní řízky 500 g × 3".

**Sources, in this order:**

1. **The owner's research** (done in a separate conversation). It will be turned into `app/src/main/assets/seed/cz/store-products.json` (format in [M5.6](implementation/M05-plan-pantry-shopping.md)). Every product records its source and date. *Status: waiting for the research (input #1).*
2. **The user's own picks:** on any shopping line, search Open Food Facts, scan the barcode in the shop, or type the name and pack size. Saved on the phone, and shared with the household in M11.
3. **Open Food Facts** data for scanned products (pack size, brand).

**Rules:**
- We don't copy product lists or prices from shop websites or apps without permission. Their terms of use usually forbid it, and in the EU databases are protected by the *sui generis* database right. If the owner's research used such a source, check its terms before the data is shipped in the app.
- Shop names (Albert, Billa, Globus, Kaufland, Lidl, Penny, Tesco, Rohlik.cz, Košík.cz) are trademarks of their owners. We use them only to label shops.

## 5. Prices

**What:** what the user paid (typed when ticking the shopping list, or on a product), and prices from the owner's research if it has them. Every price has a date, and the app shows how old it is.

**Where it goes:** stays on the phone; shared with the household in M11. Not sent anywhere else, and not part of research reports.

**Later candidate:** Open Prices, the open price database of Open Food Facts (ODbL). Its coverage of Czech shops is unknown. Not in this implementation.

## 6. Shops

A list of common Czech chains written by us (names only): Albert, Billa, Globus, Kaufland, Lidl, Penny, Tesco, Rohlik.cz, Košík.cz, and "Market / other". No shop locations and no GPS.

## 7. Recipes

- **Starter pack:** 30 recipes written for Calore ([M3.4](implementation/M03-recipes-and-dishes.md)), original texts, under the same licence as the app (0BSD).
- **The user's recipes** stay on the phone (shared with the household in M11).
- Nutrition is always calculated from the foods in sections 1–3.

## 8. Storage times (food-safety defaults)

| Default | Value | Based on |
|---|---|---|
| Cooked food in the fridge | 3 days (each recipe can change it) | Czech consumer advice and USDA: 3–4 days for cooked leftovers. We use the safer end. |
| Cooling | Into the fridge within 2 hours; fridge at 4 °C or colder | USDA; Czech consumer advice |
| Freezer, best quality | About 3 months | USDA: 3–4 months for leftovers |
| After thawing in the fridge | Eat within 24 hours | NHS (UK) |
| Cooked rice | Users can set 1 day for rice dishes | NHS: no more than 1 day in the fridge before reheating |
| Pantry items without a date | Rough defaults per shop section ([algorithms A17](implementation/algorithms.md)) | Placeholders only: the date printed on the pack always wins |

**Sources:** USDA Food Safety and Inspection Service, "Leftovers and Food Safety"; NHS (UK) advice on storing and reheating food and cooked rice; Czech consumer articles that repeat the 3–4 day and 2-hour rules. Before the first public release, check the official Czech wording at bezpecnostpotravin.cz and update this table.

The app always says: **"A guide, not a guarantee. When in doubt, throw it out."**

## 9. Formulas

| Formula | Used for | Status |
|---|---|---|
| Mifflin-St Jeor (Mifflin et al., 1990) | Resting energy (BMR) | In use |
| Activity multipliers 1.2–1.9 | Maintenance calories | In use |
| Katch-McArdle | BMR from lean body mass | M1 |
| US Navy circumference method (Hodgdon and Beckett, 1984) | Body fat from tape measurements | M1 |
| 7,700 kcal per kg of body weight change | Goal speed, smart maintenance | M1 |
| Protein 1.6–2.2 g per kg | Default protein targets (common sports-nutrition range, for example Morton et al., 2018) | M1 |

## 10. Cycle and nutrition science

| Finding | Source | Used for |
|---|---|---|
| Resting energy use is only slightly higher in the luteal phase | Benton, Hutchins and Dawes (2020), *PLoS ONE*, meta-analysis: <https://pubmed.ncbi.nlm.nih.gov/32658929/> | No calorie target change in M7 |
| About 40–45 kcal per day higher in the luteal phase | Löfberg et al. (2024), *Medicine & Science in Sports & Exercise* | Same |
| Vitamin C helps the body absorb iron from plant foods | Standard nutrition science | Period tips |
| Magnesium may ease PMS symptoms; the evidence is mixed | Several small studies | We suggest magnesium-rich foods only, never supplements |

## 11. Everything that leaves the phone

| Connection | When | What is sent | Since |
|---|---|---|---|
| Open Food Facts search | The user searches online | Search words, language | today |
| Open Food Facts product | The user scans a barcode that is not on the phone yet | Barcode | M2 (in use) |
| Household sync | Phones on the same home Wi-Fi, if a household was created | Shared data only (lists, pantry, boxes, recipes, plans, requests), encrypted, and it never leaves the home network | M11 |
| Research server | Only if the user agreed, per category | Weekly summaries ([privacy policy, appendix A](PRIVACY_POLICY.md#appendix-a-research-report-fields)) | M10 |
| Android backup | If the user has Android backup switched on | The app's data, handled by Android and the user's backup account | today |

Nothing else: no analytics, no advertising, no crash-reporting services.

## Credits shown in the app (About)

- Food data © Open Food Facts contributors, available under the Open Database License.
- Basic food data: USDA FoodData Central (public domain).
- Czech food composition data: NutriDatabaze.cz, ÚZEI (only if permission is given).
