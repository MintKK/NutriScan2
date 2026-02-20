"""
Step 2: Generate nutritional data for cleaned food labels using Gemini API.

Usage:
    pip install google-genai
    set GEMINI_API_KEY=your_api_key_here      (Windows)
    export GEMINI_API_KEY=your_api_key_here    (Linux/Mac)
    python 2_generate_nutrition.py

The script processes labels in batches (20 per request) to stay within
rate limits and reduce cost. Output is saved as food_items_generated.json.

RESUME SUPPORT: If the script is interrupted, re-run it. It will detect
existing progress and skip already-processed labels.
"""
import json
import os
import time
from pathlib import Path

try:
    from google import genai
except ImportError:
    print("Please install the Gemini SDK: pip install google-genai")
    exit(1)

# --- Config -----------------------------------------------------------------
CLEANED_LABELS_FILE = Path(__file__).parent / "cleaned_labels.txt"
OUTPUT_JSON = Path(__file__).parent / "food_items_generated.json"
BATCH_SIZE = 100         # labels per API call (large batches to minimize request count)
DELAY_BETWEEN_BATCHES = 5  # seconds between batches
MAX_RETRIES = 3          # retries per batch on rate-limit errors

# Free tier: gemini-2.5-flash = 20 req/day, gemini-2.0-flash = 0 req/day (removed)
# With batch size 100: 20 requests × 100 labels = 2,000 labels max per day
# Total labels: 1,975 → fits in a single day's quota
MODEL = "gemini-2.5-flash"

# --- Gemini Client -----------------------------------------------------------
API_KEY = os.environ.get("GEMINI_API_KEY")
if not API_KEY:
    print("ERROR: Set the GEMINI_API_KEY environment variable.")
    print("  Windows PowerShell:  $env:GEMINI_API_KEY = 'YOUR_KEY'")
    print("  Windows CMD:         set GEMINI_API_KEY=YOUR_KEY")
    print("  Linux/Mac:           export GEMINI_API_KEY=YOUR_KEY")
    exit(1)

client = genai.Client(api_key=API_KEY)

SYSTEM_PROMPT = """You are a professional nutritionist and food scientist.
Given a list of food/dish names, estimate the macronutrient content per 100 grams
of a typical serving. Return ONLY a JSON array with no markdown formatting.

Each element must have exactly these fields:
- "name": the food name (lowercase, exactly as provided)
- "kcalPer100g": estimated calories per 100g (integer)
- "proteinPer100g": grams of protein per 100g (float, 1 decimal)
- "carbsPer100g": grams of carbohydrates per 100g (float, 1 decimal)
- "fatPer100g": grams of fat per 100g (float, 1 decimal)
- "aliases": a comma-separated string of 2-5 alternative names, common spellings,
  or the main ingredient(s). For regional dishes, include the English description.
  Example: for "Pad thai" -> "pad thai noodles,thai stir fry noodles,phad thai"

For mixed dishes (e.g., "Carbonara"), estimate based on a typical restaurant serving
including all components (pasta, sauce, meat, etc) averaged to 100g.

IMPORTANT:
- Return ONLY the raw JSON array. No markdown code fences. No explanation.
- Every food in the input list MUST appear in the output array.
- Use realistic nutritional values. When uncertain, use values for the most
  common/popular version of the dish.
"""

def build_prompt(labels: list[str]) -> str:
    numbered = "\n".join(f"{i+1}. {label}" for i, label in enumerate(labels))
    return f"Estimate nutrition for these {len(labels)} foods:\n\n{numbered}"

def parse_response(text: str) -> list[dict]:
    """Extract JSON array from response, handling possible markdown fences."""
    text = text.strip()
    if text.startswith("```"):
        lines = text.split("\n")
        text = "\n".join(lines[1:-1]).strip()
    return json.loads(text)

def process_batch(labels: list[str], batch_num: int, total_batches: int) -> list[dict]:
    """Send a batch of labels to Gemini with retry + exponential backoff."""
    prompt = build_prompt(labels)
    
    for attempt in range(1, MAX_RETRIES + 1):
        print(f"  Batch {batch_num}/{total_batches}: {len(labels)} labels (attempt {attempt})...")
        try:
            response = client.models.generate_content(
                model=MODEL,
                contents=prompt,
                config=genai.types.GenerateContentConfig(
                    system_instruction=SYSTEM_PROMPT,
                    temperature=0.2,
                ),
            )
            items = parse_response(response.text)
            print(f"    [OK] Got {len(items)} items")
            return items
        except json.JSONDecodeError as e:
            print(f"    [ERR] JSON parse error: {e}")
            print(f"    Raw: {response.text[:200]}...")
            if attempt < MAX_RETRIES:
                wait = 5 * attempt
                print(f"    Retrying in {wait}s...")
                time.sleep(wait)
        except Exception as e:
            error_str = str(e)
            if "429" in error_str or "RESOURCE_EXHAUSTED" in error_str:
                wait = 10 * attempt  # exponential backoff: 10s, 20s, 30s
                print(f"    [RATE LIMIT] Waiting {wait}s before retry...")
                time.sleep(wait)
            else:
                print(f"    [ERR] API error: {e}")
                if attempt < MAX_RETRIES:
                    time.sleep(5)
    
    print(f"    [FAIL] Batch {batch_num} failed after {MAX_RETRIES} attempts")
    return []

def load_existing_progress() -> dict[str, dict]:
    """Load previously generated items for resume support."""
    if OUTPUT_JSON.exists():
        try:
            items = json.loads(OUTPUT_JSON.read_text(encoding="utf-8"))
            index = {item["name"].lower().strip(): item for item in items}
            print(f"Resuming: found {len(index)} previously generated items")
            return index
        except (json.JSONDecodeError, KeyError):
            pass
    return {}

def main():
    # Load cleaned labels
    labels = CLEANED_LABELS_FILE.read_text(encoding="utf-8").strip().splitlines()
    print(f"Loaded {len(labels)} cleaned labels")
    print(f"Using model: {MODEL}")

    # Load existing progress for resume
    existing = load_existing_progress()

    # Filter out already-processed labels
    remaining = [l for l in labels if l.lower().strip() not in existing]
    print(f"Remaining to process: {len(remaining)} (skipping {len(labels) - len(remaining)} already done)")

    if not remaining:
        print("All labels already processed! Nothing to do.")
        print(f"Output: {OUTPUT_JSON}")
        return

    # Split into batches
    batches = [remaining[i:i + BATCH_SIZE] for i in range(0, len(remaining), BATCH_SIZE)]
    total_batches = len(batches)
    print(f"Processing {len(remaining)} labels in {total_batches} batches...\n")

    all_items = dict(existing)  # start with existing items
    failed_labels = []

    for i, batch in enumerate(batches, 1):
        items = process_batch(batch, i, total_batches)
        if items:
            for item in items:
                name = item.get("name", "").lower().strip()
                if name:
                    item["name"] = name
                    item["kcalPer100g"] = int(item.get("kcalPer100g", 0))
                    item["proteinPer100g"] = round(float(item.get("proteinPer100g", 0)), 1)
                    item["carbsPer100g"] = round(float(item.get("carbsPer100g", 0)), 1)
                    item["fatPer100g"] = round(float(item.get("fatPer100g", 0)), 1)
                    item["aliases"] = item.get("aliases", "")
                    all_items[name] = item
        else:
            failed_labels.extend(batch)

        # Save progress after every batch (enables resume on crash)
        save_progress(all_items)

        # Rate limiting
        if i < total_batches:
            time.sleep(DELAY_BETWEEN_BATCHES)

    # Final report
    print(f"\n{'='*50}")
    print(f"Total items generated: {len(all_items)}")
    if failed_labels:
        print(f"Failed labels ({len(failed_labels)}):")
        for fl in failed_labels:
            print(f"  - {fl}")
    print(f"Output saved to: {OUTPUT_JSON}")
    print(f"\nNext step: py 3_merge_and_deploy.py")

def save_progress(items_dict: dict):
    """Save current progress to disk (called after every batch)."""
    sorted_items = sorted(items_dict.values(), key=lambda x: x["name"])
    OUTPUT_JSON.write_text(
        json.dumps(sorted_items, indent=4, ensure_ascii=False),
        encoding="utf-8"
    )

if __name__ == "__main__":
    main()
