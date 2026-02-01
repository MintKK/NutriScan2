import os
import urllib.request

# 1. The URL for the labels (using a GitHub mirror which is often less blocked)
url = "https://raw.githubusercontent.com/second-state/wasmedge-quickjs/main/example_js/tensorflow_lite_demo/aiy_food_V1_labelmap.txt"

# 2. The specific path where Android needs the file
destination_folder = os.path.join("app", "src", "main", "assets", "ml")
destination_file = os.path.join(destination_folder, "food11_labels.txt")

# Ensure the folder exists
if not os.path.exists(destination_folder):
    print(f"Creating folder: {destination_folder}")
    os.makedirs(destination_folder)

print(f"Attempting to download labels to: {destination_file}...")

try:
    # 3. Download the file
    urllib.request.urlretrieve(url, destination_file)
    print("✅ Success! The file 'food11_labels.txt' has been placed correctly.")
except Exception as e:
    print(f"❌ Download failed: {e}")
    
    # EMERGENCY BACKUP: If download fails, create a dummy file so app doesn't crash
    print("⚠️ Creating a generic backup file so your app won't crash...")
    with open(destination_file, "w") as f:
        f.write("Background\n")
        for i in range(2001):
            f.write(f"Food Item {i+1}\n")
    print("✅ Backup file created. Your app will run, but labels will be generic (e.g. 'Food Item 55').")