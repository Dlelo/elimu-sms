# build_dataset.py
# Basic heuristic extraction for MCQ style and short answer Qs

import os
import re
import csv
from pathlib import Path

def extract_questions_from_text(s):
    lines = s.splitlines()
    qs = []
    buf = []

    for line in lines:
        line_stripped = line.strip()
        if re.match(r'^\d+\s*[.)]', line_stripped):
            if buf:
                qs.append("\n".join(buf).strip())
                buf = []
            buf.append(line_stripped)
        else:
            if line_stripped:
                buf.append(line_stripped)

    if buf:
        qs.append("\n".join(buf).strip())
    return qs

def heuristics_label(qtext):
    q_lower = qtext.lower()

    if re.search(r'choose|a\)|b\)|c\)|d\)|option', q_lower):
        intent = "mcq"
    elif re.search(r'\+|\-|\/|\*|fraction|÷', q_lower):
        intent = "math_arithmetic"
    elif re.search(r'what is|define|explain|describe|which is', q_lower):
        intent = "science_def"
    else:
        intent = "general_question"

    return intent, ""  # placeholder answer

texts = []
for txt_file in Path("data").glob("*.txt"):
    texts.append(txt_file.read_text(encoding="utf-8"))

all_questions = []
for t in texts:
    qs = extract_questions_from_text(t)
    for q in qs:
        intent, answer = heuristics_label(q)
        all_questions.append((q.replace("\n", " "), intent, answer))

os.makedirs("out", exist_ok=True)

with open("out/dataset.csv", "w", encoding="utf-8", newline='') as f:
    writer = csv.writer(f)
    writer.writerow(["question", "intent", "answer"])
    for q, i, a in all_questions:
        writer.writerow([q, i, a])

print("Wrote out/dataset.csv with", len(all_questions), "rows. Please open and clean/annotate answers and intent labels.")
