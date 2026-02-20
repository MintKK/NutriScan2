"""
Step 1: Clean food11_labels.txt
Removes garbage entries (__background__, /g/..., /m/..., quoted strings)
and outputs a clean label list for Gemini API processing.
"""
import re
from pathlib import Path

# Paths
LABELS_FILE = Path(__file__).parent.parent / "app" / "src" / "main" / "assets" / "ml" / "food11_labels.txt"
OUTPUT_FILE = Path(__file__).parent / "cleaned_labels.txt"

def is_garbage(label: str) -> bool:
    """Return True if the label is not a real food name."""
    label = label.strip()
    if not label:
        return True
    if label == "__background__":
        return True
    # Knowledge Graph IDs: /g/xxxx or /m/xxxx
    if re.match(r'^/[gm]/', label):
        return True
    # Quoted artifact strings like """Peanut butter" or """Bacon"
    if label.startswith('"""') or label.startswith('"'):
        return True
    return False

def main():
    raw_labels = LABELS_FILE.read_text(encoding="utf-8").splitlines()
    print(f"Total raw labels: {len(raw_labels)}")

    cleaned = []
    removed = []
    for label in raw_labels:
        label = label.strip()
        if is_garbage(label):
            removed.append(label)
        else:
            cleaned.append(label)

    # Deduplicate (some labels appear twice, e.g. "Sundae")
    seen = set()
    unique = []
    for label in cleaned:
        key = label.lower()
        if key not in seen:
            seen.add(key)
            unique.append(label)
        else:
            removed.append(f"{label} (duplicate)")

    OUTPUT_FILE.write_text("\n".join(unique), encoding="utf-8")

    print(f"Removed: {len(removed)} labels")
    print(f"Clean unique labels: {len(unique)}")
    print(f"Output written to: {OUTPUT_FILE}")
    print(f"\n--- Removed labels ---")
    for r in removed:
        print(f"  [x] {r}")

if __name__ == "__main__":
    main()
