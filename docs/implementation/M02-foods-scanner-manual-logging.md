# M2: Foods, barcode scanner and manual logging

**Goal:** basic foods with Czech names and full nutrition data, Czech-first product search, a barcode scanner, and quick add. Manual logging stays the main way to track anything that isn't a prep box.
**Users get:** they can find "kuřecí prsa" or "rýže", scan a product, or type in a dish's calories.
**Depends on:** M0.
**Read first:** [data-model.md](data-model.md) (M2), [`../DATA_SOURCES.md`](../DATA_SOURCES.md).
**New dependencies:** CameraX (`androidx.camera:camera-camera2`, `camera-lifecycle`, `camera-view`), ZXing core (`com.google.zxing:core`, Apache 2.0). Not ML Kit: it is closed source.

## Tasks

### M2.1 Food data model (database version 4)

- [ ] Add the `foods` and `diary_entries` columns from data-model M2.
- [ ] `Nutrients` gets `ironMg`, `magnesiumMg` and `vitaminCMg` (nullable, same "unknown is not zero" rule). Update `plus`, `scaledBy` and their tests.
- [ ] `Food` gets `key`, localized names, `section`, `allergens`, `animalFlags`, `defaultPackGrams`, `storage`, `shelfLifeDays` and `gramsPerPiece`. The display name uses `nameCs` or `nameEn` for the current locale, then `name`.

**Done when:** the migration test passes and the existing food and diary tests pass.

### M2.2 Basic foods dataset

- [ ] Tool in `tools/generic-foods/` (Python 3 or Kotlin, the builder's choice) that reads:
  - the USDA FoodData Central downloads (Foundation Foods and SR Legacy, public domain), and
  - `tools/generic-foods/selection.csv`: `key, fdcId, nameCs, nameEn, section, allergens, animalFlags, defaultPackGrams, storage, shelfLifeDays, gramsPerPiece`,

  and writes `app/src/main/assets/foods/generic-cz.json` with nutrients per 100 g: energy (kcal), protein, carbohydrate, fat, fibre, sugars, iron, magnesium and vitamin C, plus a `datasetVersion` and a `sources` list.
- [ ] Choose about **300 common foods** for Czech home cooking, spread over all `ShopSection`s.
- [ ] Foods that USDA doesn't have (for example *tvaroh*, Czech breads) are added with values from a typical Czech product label on Open Food Facts, with `sourceRef = off:<barcode>`.
- [ ] Optional: if ÚZEI allows it (owner input #2), use NutriDatabaze.cz values for Czech foods instead.
- [ ] In the app, an importer runs on start when the asset's `datasetVersion` is newer than the stored one. It upserts by `key`, keeps uuids stable, and never touches the user's own foods.
- [ ] Ask the owner to review the Czech names.

**Done when:** the JSON is committed together with its sources; an importer test passes (first import, re-import of a newer version, user foods untouched); a search for "kuřecí prsa" (Czech) and "chicken breast" (English) finds the same food.

### M2.3 Better food search

- [ ] Local search also matches `nameCs` and `nameEn`.
- [ ] Result order: **Basic foods**, then **Your foods**, then **Open Food Facts**.
- [ ] Open Food Facts requests prefer products sold in the Czech Republic: languages `cs,en` (already from the locale), plus a country filter if the search API supports it. Check the current API and note what works in `DATA_SOURCES.md`.
- [ ] The parser also reads `quantity` / `product_quantity` (pack size), `stores_tags`, `categories_tags` (mapped to `ShopSection`, best effort) and `allergens_tags` (mapped to `Allergen`). Add fixtures and tests like the existing ones.

### M2.4 Barcode scanner

- [ ] A scanner screen: CameraX preview, ZXing decoding (EAN-13, EAN-8, UPC-A, UPC-E, and QR codes for M11), torch button, and "Type the code" as a fallback.
- [ ] Camera permission with a short explanation. Camera images are never saved or sent.
- [ ] After a scan: look for the barcode in local foods first. If it isn't there, request `https://world.openfoodfacts.org/api/v2/product/<barcode>` with only the fields we parse. Then open the food detail screen, so the user can log it.
- [ ] If the product is unknown: "Not found. Add it yourself?" opens the custom food form with the barcode filled in.
- [ ] Entry points now: Add food. Later the pantry (M5) and product picking (M5) reuse the scanner.

**Done when:** scanning a known product opens it in under about 2 seconds on a normal connection; offline, a scanned food that was used before still works; tests cover the barcode lookup parser.

### M2.5 Quick add

- [ ] In Add food: **Quick add**, with a name (optional), kcal (required), and protein, carbs and fat (optional).
- [ ] Saved as `entryType = QUICK_ADD`, `grams = 0`, `foodId = NULL`. Tapping it edits the values (not grams).
- [ ] Useful for meals out or dishes whose ingredients you don't know.

**Done when:** a quick add appears in the diary, counts in the totals, and can be edited and deleted with undo.

### M2.6 Manual logging stays the same

- [ ] Search, custom foods, servings, editing grams and delete with undo work exactly as before.
- [ ] Add a UI test for the existing flow (search → pick → grams → log) if one doesn't exist.

## Out of scope

Shop products and prices (M5); recipes and dishes (M3).
