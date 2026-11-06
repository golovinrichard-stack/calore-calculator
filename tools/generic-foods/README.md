# Basic foods dataset

Builds `app/src/main/assets/foods/generic-cz.json`: about 300 basic foods for Czech home cooking with Czech and English names, shop section, allergens and nutrients per 100 g (energy, protein, carbohydrate, fat, fibre, sugars, iron, magnesium, vitamin C). See M2.2 in [`docs/implementation/`](../../docs/implementation/M02-foods-scanner-manual-logging.md) and [`docs/DATA_SOURCES.md`](../../docs/DATA_SOURCES.md).

| File | What it is |
|---|---|
| `selection.csv` | The chosen USDA foods (`fdcId`) with our names and shop data. Lists in a column are separated by `;`. |
| `off-foods.json` | Czech foods USDA doesn't have (tvaroh, Czech bread, dumplings …), from one typical product label on Open Food Facts each. |
| `build.py` | Reads both and the USDA downloads, checks everything and writes the JSON. Python 3, standard library only. |

## Rebuild

1. Download and unzip the **JSON** versions of *SR Legacy* and *Foundation Foods* from <https://fdc.nal.usda.gov/download-datasets> (public domain).
2. Run, with the next dataset version:

   ```bash
   python3 tools/generic-foods/build.py --sr FoodData_Central_sr_legacy_food_json_2018-04.json --foundation FoodData_Central_foundation_food_json_2025-12-18.json --version 2
   ```

3. Commit the CSV, the JSON and the asset together. Phones re-import the basic foods when the bundled `datasetVersion` is newer; they update rows by `key`, keep their ids, and never touch the user's own foods.

Czech names are our own translations; please report better ones.
