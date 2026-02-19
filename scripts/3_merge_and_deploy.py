"""
Step 3: Merge generated nutrition data with existing food_items.json
and deploy to the Android app's assets folder.

This script:
1. Loads the existing food_items.json (USDA-based, ~30 items)
2. Loads the AI-generated food_items_generated.json (~1900 items)
3. Merges them, preferring USDA data for duplicates (more accurate)
4. Adds cross-references in aliases (label name ↔ existing aliases)
5. Writes the final merged file to the app's assets directory
"""
import json
from pathlib import Path

SCRIPTS_DIR = Path(__file__).parent
GENERATED_FILE = SCRIPTS_DIR / "food_items_generated.json"
EXISTING_FILE = SCRIPTS_DIR.parent / "app" / "src" / "main" / "assets" / "food_items.json"
OUTPUT_FILE = EXISTING_FILE  # Overwrite the app's food_items.json
BACKUP_FILE = SCRIPTS_DIR / "food_items_backup.json"

def normalize(name: str) -> str:
    return name.strip().lower()

def main():
    # --- Load existing (USDA) data ---
    if EXISTING_FILE.exists():
        existing_items = json.loads(EXISTING_FILE.read_text(encoding="utf-8"))
        print(f"Loaded {len(existing_items)} existing items from food_items.json")
        # Backup
        BACKUP_FILE.write_text(
            json.dumps(existing_items, indent=4, ensure_ascii=False),
            encoding="utf-8"
        )
        print(f"Backup saved to: {BACKUP_FILE}")
    else:
        existing_items = []
        print("No existing food_items.json found, starting fresh.")

    # --- Load generated data ---
    if not GENERATED_FILE.exists():
        print(f"ERROR: {GENERATED_FILE} not found!")
        print("Run 2_generate_nutrition.py first.")
        exit(1)

    generated_items = json.loads(GENERATED_FILE.read_text(encoding="utf-8"))
    print(f"Loaded {len(generated_items)} generated items")

    # --- Build index of existing items (prefer USDA data) ---
    merged = {}
    for item in existing_items:
        key = normalize(item["name"])
        merged[key] = item

    # --- Merge generated items (skip duplicates) ---
    added = 0
    skipped = 0
    for item in generated_items:
        key = normalize(item["name"])
        if key not in merged:
            merged[key] = item
            added += 1
        else:
            # Existing USDA entry wins, but merge unique aliases
            existing_aliases = set(
                a.strip().lower()
                for a in merged[key].get("aliases", "").split(",")
                if a.strip()
            )
            new_aliases = set(
                a.strip().lower()
                for a in item.get("aliases", "").split(",")
                if a.strip()
            )
            combined = existing_aliases | new_aliases
            if combined:
                merged[key]["aliases"] = ",".join(sorted(combined))
            skipped += 1

    # --- Sort and write ---
    final_items = sorted(merged.values(), key=lambda x: x["name"].lower())

    OUTPUT_FILE.write_text(
        json.dumps(final_items, indent=4, ensure_ascii=False),
        encoding="utf-8"
    )

    print(f"\n{'='*50}")
    print(f"Existing items (USDA):    {len(existing_items)}")
    print(f"AI-generated items:       {len(generated_items)}")
    print(f"New items added:          {added}")
    print(f"Duplicates (USDA wins):   {skipped}")
    print(f"Final total:              {len(final_items)}")
    print(f"Output written to:        {OUTPUT_FILE}")
    print(f"\nYour app will now use the expanded database on next build!")

if __name__ == "__main__":
    main()
