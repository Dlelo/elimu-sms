# extract_texts.py
# Requires: pip install pdfminer.six
import os
from pdfminer.high_level import extract_text

pdfs = [
    ("data/INT SCI KPSEA 2025.pdf", "data/INT_SCI_KPSEA_2025.txt"),
    ("data/MAT KPSEA 2025.pdf", "data/MAT_KPSEA_2025.txt")
]

os.makedirs("data", exist_ok=True)

for src, dst in pdfs:
    print("Extracting:", src)
    text = extract_text(src)
    with open(dst, "w", encoding="utf-8") as f:
        f.write(text)
    print("Saved:", dst)
