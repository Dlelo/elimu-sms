ElimuSMS - Model Training Pipeline
===================================

This directory contains the Python pipeline that trains the on-device intent
classifier and produces the 4-bit quantised weights baked into
src/com/elimu/CompressedTinyML.java.

Stages:
  1. build_dataset.py   - extract Q/A pairs from KPSEA PDFs -> training CSV
  2. train_model.py     - train an MLPClassifier (sklearn) -> .joblib model
  3. quantize_pack.py   - 4-bit quantise + nibble-pack -> Java byte arrays
  4. (manual)           - paste byte arrays into CompressedTinyML.java


Prerequisites
-------------

  - Python 3.9 or newer (tested with 3.11)
  - pip
  - For PDF text extraction: pdftotext or equivalent (already pre-extracted
    .txt files are committed in data/, so this is optional)


One-time setup
--------------

  cd elimu-model

  # Create and activate a virtual environment
  python3 -m venv .venv
  source .venv/bin/activate          # Windows: .venv\Scripts\activate

  # Install dependencies
  pip install --upgrade pip
  pip install numpy pandas scikit-learn joblib


Running the pipeline
--------------------

All commands assume the venv is active and you are in the elimu-model/
directory.

  # 1. (optional) Rebuild the training dataset from the KPSEA source texts
  python build_dataset.py
  # writes: data/training_data_enhanced.csv

  # 2. Train the MLP classifier
  python train_model.py
  # writes: model_out/model.joblib + model_config.json
  # prints train/test accuracy

  # 3. Quantise and pack weights into Java byte arrays
  python quantize_pack.py
  # writes: model_out/java/weights_w1.java
  #         model_out/java/weights_w2.java
  #         model_out/java/biases_out.java
  #         model_out/meta.json

  # 4. Copy the byte arrays from model_out/java/*.java into
  #    ../src/com/elimu/CompressedTinyML.java
  #    (replace the COMPRESSED_WEIGHTS and COMPRESSED_BIASES literals)

  # 5. Build the MIDlet (top-level project) - see ../README.md


Switching to the alternative "Living Things" model
--------------------------------------------------

  python train_living.py
  # uses model_config_plants.json and writes a plants-only variant


Sanity check
------------

After step 2, model_config.json should contain non-trivial training/test
accuracies (the current committed run reports ~52% test on 8 intents; the
production model in CompressedTinyML.java reaches ~91.79% test after the
26-feature greeting/farewell split). If your run prints near-random accuracy,
verify that data/training_data_enhanced.csv was regenerated and contains
samples for all 8 intent labels.


Troubleshooting
---------------

  - "ModuleNotFoundError: No module named 'sklearn'"
    -> The venv is not active. Re-run: source .venv/bin/activate

  - "FileNotFoundError: data/training_data_enhanced.csv"
    -> Run step 1 (build_dataset.py) first.

  - quantize_pack.py says "Model shape" mismatch
    -> The feature size in train_model.py (FEATURE_SIZE) must match
       FEATURE_SIZE in src/com/elimu/CompressedTinyML.java (currently 26).


Where the model ends up at runtime
----------------------------------

The packed byte arrays are baked into CompressedTinyML.java as static
final fields, so no model file is loaded from disk on the feature phone.
The MIDlet then optionally overlays the static defaults with persisted
weights from RecordStore (RMS) when the on-device online learning is
enabled.

To rebuild and run the full app on an emulator after retraining, see
../README.md.
