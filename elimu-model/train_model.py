import numpy as np
import pandas as pd
from sklearn.neural_network import MLPClassifier
from sklearn.model_selection import train_test_split
import json

# Define intents (match your MicroResponses class)
INTENTS = {
    0: "math_help",
    1: "science_help",
    2: "stem_general",   # was stem_help — now routes to science
    3: "quiz",
    4: "general_help",
    5: "progress",
    6: "greeting",
    7: "farewell"
}

def create_training_data():
    """Create comprehensive training dataset with EXPLICIT plants examples"""
    training_data = [
        # Math questions (Intent 0)
        ("help with math", 0),
        ("how to calculate", 0),
        ("addition problem", 0),
        ("subtraction help", 0),
        ("multiplication", 0),
        ("division", 0),
        ("math homework", 0),
        ("solve equation", 0),
        ("numbers", 0),
        ("arithmetic", 0),
        ("math problem", 0),
        ("calculate area", 0),
        ("math question", 0),
        ("learn math", 0),
        ("mathematics", 0),

        # ====== SCIENCE QUESTIONS - ENHANCED WITH PLANTS ======
        ("science help", 1),
        ("science experiment", 1),
        ("plants and animals", 1),
        ("weather", 1),
        ("environment", 1),
        ("physics", 1),
        ("chemistry", 1),
        ("biology", 1),
        ("scientific method", 1),
        ("nature", 1),
        ("science project", 1),
        ("learn science", 1),
        ("science question", 1),

        # EXPLICIT PLANTS EXAMPLES - SCIENCE INTENT
        ("plants", 1),                    # Single word
        ("about plants", 1),              # Simple phrase
        ("plant growth", 1),              # Science concept
        ("types of plants", 1),           # Classification
        ("watering plants", 1),           # Care
        ("plants need sunlight", 1),      # Science fact
        ("how do plants grow", 1),        # Question form
        ("what are plants", 1),           # Definition
        ("plant biology", 1),             # Scientific term
        ("photosynthesis in plants", 1),  # Advanced concept
        ("plant cells", 1),               # Biology
        ("plant reproduction", 1),        # Science topic
        ("plant science", 1),             # Explicit science
        ("botany plants", 1),             # Academic term
        ("indoor plants", 1),             # Variant
        ("outdoor plants", 1),            # Variant
        ("flowering plants", 1),          # Specific type
        ("medicinal plants", 1),          # Application
        ("plant experiments", 1),         # Science method
        ("study plants", 1),              # Learning context

        # Animals - also science
        ("animals", 1),
        ("about animals", 1),
        ("animal habitats", 1),
        ("types of animals", 1),

        # Weather - science
        ("weather patterns", 1),
        ("climate change", 1),
        ("rainfall", 1),

        # STEM general questions (Intent 2) - technology and engineering
        ("technology", 2),
        ("engineering", 2),
        ("stem", 2),
        ("coding", 2),
        ("computer", 2),
        ("design", 2),
        ("build", 2),
        ("construct", 2),
        ("stem project", 2),
        ("technology help", 2),
        ("engineering problem", 2),
        ("how does it work", 2),

        # Quiz (Intent 3)
        ("take quiz", 3),
        ("test me", 3),
        ("practice questions", 3),
        ("exam", 3),
        ("quiz time", 3),
        ("give me a quiz", 3),
        ("practice test", 3),

        # General help (Intent 4)
        ("help", 4),
        ("can you help", 4),
        ("assist me", 4),
        ("I need help", 4),
        ("please help", 4),
        ("help me", 4),

        # Progress (Intent 5)
        ("my progress", 5),
        ("how am I doing", 5),
        ("show progress", 5),
        ("statistics", 5),
        ("my results", 5),
        ("progress report", 5),

        # Greeting (Intent 6)
        ("hello", 6),
        ("hi", 6),
        ("good morning", 6),
        ("hey", 6),
        ("good afternoon", 6),
        ("good evening", 6),

        # Farewell (Intent 7)
        ("bye", 7),
        ("goodbye", 7),
        ("see you", 7),
        ("exit", 7),
        ("quit", 7),
        ("close", 7)
    ]

    # Add variations for ALL examples (including plants)
    variations = []
    for text, intent in training_data[:]:  # Copy original list
        words = text.split()
        # Create variations for plants examples too
        variations.append((f"can you {text}", intent))
        variations.append((f"I need {text}", intent))
        variations.append((f"please {text}", intent))
        variations.append((f"show me {text}", intent))
        variations.append((text + " please", intent))
        variations.append((f"how to {text}", intent))
        variations.append((f"what is {text}", intent))
        variations.append((f"tell me about {text}", intent))
        variations.append((f"explain {text}", intent))

        # For plants specifically, add more variations
        if "plant" in text.lower() and intent == 1:
            variations.append((f"science of {text}", 1))
            variations.append((f"biology of {text}", 1))
            variations.append((f"learn about {text}", 1))
            variations.append((f"teach me {text}", 1))

    training_data.extend(variations)

    # Add NEGATIVE examples: plants NOT in English context
    # These help the model distinguish
    negative_examples = [
        # Plants should NOT be English (intent 2)
        ("stem plants", 2),  # This is confusing, let's avoid
        ("plants grammar", 2),  # This shouldn't exist
        ("plants reading", 2),  # This is rare
    ]

    # Instead, add more clear examples
    # Plants in science context vs other contexts
    for i in range(5):
        training_data.append((f"science topic plants example {i}", 1))
        training_data.append((f"biology plants study {i}", 1))

    # Save to CSV
    df = pd.DataFrame(training_data, columns=['text', 'intent'])
    df.to_csv('data/training_data_enhanced.csv', index=False)

    print(f"Created {len(training_data)} training samples")
    print(f"Science examples with 'plant': {len([t for t, i in training_data if 'plant' in t.lower() and i == 1])}")
    print(f"All examples with 'plant': {len([t for t, _ in training_data if 'plant' in t.lower()])}")

    return training_data

def extract_features(text):
    """Enhanced feature extraction - add 'plants' as science keyword"""
    features = np.zeros(17, dtype=np.float32)  # Increased to 17 features
    text_lower = text.lower()

    # Feature 0-6: Subject keywords (plants at position 6)
    subject_keywords = ["math", "science", "stem", "calculate", "experiment", "grammar", "plants"]
    for i, keyword in enumerate(subject_keywords):
        if keyword in text_lower:
            features[i] = 1.0

    # Feature 7-10: Question types
    if "what" in text_lower or "which" in text_lower:
        features[7] = 1.0
    if "how" in text_lower:
        features[8] = 1.0
    if "why" in text_lower:
        features[9] = 1.0
    if "when" in text_lower or "where" in text_lower:
        features[10] = 1.0

    # Feature 11-16: Educational context
    context_keywords = ["help", "learn", "teach", "explain", "question", "answer"]
    for i, keyword in enumerate(context_keywords, 11):
        if keyword in text_lower:
            features[i] = 1.0

    return features

def quantize_weights(weights, num_bits=4):
    """Convert weights to 4-bit quantized values"""
    # Scale weights to 0-15 range (4-bit)
    min_val = np.min(weights)
    max_val = np.max(weights)

    # Normalize to [0, 1]
    normalized = (weights - min_val) / (max_val - min_val)
    # Quantize to 4-bit integers [0, 15]
    quantized = np.round(normalized * (2**num_bits - 1)).astype(np.uint8)

    return quantized, min_val, max_val

def pack_4bit_to_bytes(quantized_weights):
    """Pack 4-bit weights into bytes (2 weights per byte)"""
    flattened = quantized_weights.flatten()
    packed = []

    for i in range(0, len(flattened), 2):
        if i + 1 < len(flattened):
            # Pack two 4-bit values into one byte
            packed_byte = (flattened[i] << 4) | flattened[i + 1]
        else:
            # Last byte (odd number of weights)
            packed_byte = flattened[i] << 4
        packed.append(packed_byte)

    return np.array(packed, dtype=np.uint8)

def generate_java_code(packed_weights, packed_biases, feature_size=17, hidden_size=12, output_size=8):
    """Generate the Java array initialization code"""
    # Generate weights array
    java_weights = "private static final byte[] COMPRESSED_WEIGHTS = {\n    "
    weight_lines = []

    for i in range(0, len(packed_weights), 8):
        line_weights = packed_weights[i:i+8]
        hex_values = [f"(byte)0x{w:02X}" for w in line_weights]
        weight_lines.append(", ".join(hex_values))

    java_weights += ",\n    ".join(weight_lines)
    java_weights += "\n};\n"

    # Generate biases array
    java_biases = "private static final byte[] COMPRESSED_BIASES = {\n    "
    bias_lines = []

    for i in range(0, len(packed_biases), 8):
        line_biases = packed_biases[i:i+8]
        hex_values = [f"(byte)0x{b:02X}" for b in line_biases]
        bias_lines.append(", ".join(hex_values))

    java_biases += ",\n    ".join(bias_lines)
    java_biases += "\n};\n"

    # Generate weight positions
    total_weights = feature_size * hidden_size + hidden_size * output_size
    positions = list(range(0, total_weights))

    java_positions = "private static final short[] WEIGHT_POSITIONS = {\n    "
    pos_lines = []
    for i in range(0, len(positions), 10):
        line_pos = positions[i:i+10]
        pos_lines.append(", ".join(map(str, line_pos)))
    java_positions += ",\n    ".join(pos_lines)
    java_positions += "\n};\n"

    # Generate feature extraction code
    java_features = """
    // FEATURE EXTRACTION FOR NEW MODEL (17 features)
    private byte[] extractFeatures(String text) {
        byte[] features = new byte[17];
        String lower = text.toLowerCase();

        // Feature 0-6: Subject keywords (plants at position 6)
        String[] subjectKeywords = {"math", "science", "stem", "calculate", "experiment", "grammar", "plants"};
        for (int i = 0; i < subjectKeywords.length; i++) {
            if (contains(lower, subjectKeywords[i])) {
                features[i] = 1;
            }
        }

        // Feature 7-10: Question types
        if (contains(lower, "what") || contains(lower, "which")) {
            features[7] = 1;
        }
        if (contains(lower, "how")) {
            features[8] = 1;
        }
        if (contains(lower, "why")) {
            features[9] = 1;
        }
        if (contains(lower, "when") || contains(lower, "where")) {
            features[10] = 1;
        }

        // Feature 11-16: Educational context
        String[] contextKeywords = {"help", "learn", "teach", "explain", "question", "answer"};
        for (int i = 0; i < contextKeywords.length; i++) {
            if (contains(lower, contextKeywords[i])) {
                features[11 + i] = 1;
            }
        }

        return features;
    }
    """

    return java_weights, java_biases, java_positions, java_features

def test_model(model, test_cases):
    """Test the model with specific cases"""
    print("\n=== MODEL TESTING ===")
    for text, expected_intent in test_cases:
        features = extract_features(text)
        prediction = model.predict([features])[0]
        confidence = np.max(model.predict_proba([features]))

        status = "✓" if prediction == expected_intent else "✗"
        print(f"{status} '{text}'")
        print(f"  Expected: {expected_intent} ({INTENTS[expected_intent]})")
        print(f"  Predicted: {prediction} ({INTENTS[prediction]})")
        print(f"  Confidence: {confidence:.3f}")
        print(f"  Features with 'plants': {'plants' in text.lower()}")
        print()

def main():
    print("=== Elimu AI Model Trainer - PLANTS ENHANCED ===\n")

    # Step 1: Create enhanced training data
    print("1. Creating enhanced training data with explicit plants examples...")
    training_data = create_training_data()

    # Step 2: Extract features
    print("2. Extracting features...")
    X = np.array([extract_features(text) for text, _ in training_data])
    y = np.array([label for _, label in training_data])

    print(f"   Training data shape: X{X.shape}, y{y.shape}")
    print(f"   Feature size: {X.shape[1]}")

    # Step 3: Split data
    print("3. Splitting data...")
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    # Step 4: Train model
    print("4. Training model...")
    model = MLPClassifier(
        hidden_layer_sizes=(12,),
        activation='relu',
        solver='adam',
        max_iter=3000,  # Increased iterations
        random_state=42,
        learning_rate_init=0.001,
        alpha=0.001  # Regularization
    )

    model.fit(X_train, y_train)

    # Step 5: Evaluate
    train_score = model.score(X_train, y_train)
    test_score = model.score(X_test, y_test)
    print(f"   Training accuracy: {train_score:.3f}")
    print(f"   Test accuracy: {test_score:.3f}")

    # Test specific cases
    test_cases = [
        ("plants", 1),
        ("about plants", 1),
        ("plant growth", 1),
        ("help with plants", 1),
        ("science plants", 1),
        ("math addition", 0),
        ("stem grammar", 2),
        ("science experiment", 1),
        ("animals", 1),
    ]

    test_model(model, test_cases)

    # Step 6: Export weights
    print("6. Exporting weights...")
    weights = model.coefs_
    biases = model.intercepts_

    input_hidden_weights = weights[0]  # 17x12
    hidden_biases = biases[0]          # 12
    hidden_output_weights = weights[1] # 12x8
    output_biases = biases[1]          # 8

    # Quantize weights
    input_hidden_quantized, ih_min, ih_max = quantize_weights(input_hidden_weights)
    hidden_output_quantized, ho_min, ho_max = quantize_weights(hidden_output_weights)
    hidden_biases_quantized, hb_min, hb_max = quantize_weights(hidden_biases)
    output_biases_quantized, ob_min, ob_max = quantize_weights(output_biases)

    # Pack weights
    all_weights = np.concatenate([input_hidden_quantized.flatten(), hidden_output_quantized.flatten()])
    all_biases = np.concatenate([hidden_biases_quantized.flatten(), output_biases_quantized.flatten()])

    packed_weights = pack_4bit_to_bytes(all_weights)
    packed_biases = pack_4bit_to_bytes(all_biases)

    print(f"   Packed weights: {len(packed_weights)} bytes")
    print(f"   Packed biases: {len(packed_biases)} bytes")

    # Step 7: Generate Java code
    print("7. Generating Java code...")
    java_weights, java_biases, java_positions, java_features = generate_java_code(
        packed_weights, packed_biases,
        feature_size=17, hidden_size=12, output_size=8
    )

    # Save Java code to file
    with open('generated_java_code_plants.txt', 'w') as f:
        f.write("=== GENERATED JAVA CODE FOR CompressedTinyML - PLANTS ENHANCED ===\n\n")
        f.write("// 1. Update your feature extraction to 17 features\n")
        f.write("// 2. Copy these arrays to your CompressedTinyML class\n\n")

        f.write("// Feature size constants\n")
        f.write("private static final int FEATURE_SIZE = 17;\n")
        f.write("private static final int HIDDEN_SIZE = 12;\n")
        f.write("private static final int OUTPUT_SIZE = 8;\n\n")

        f.write(java_weights + "\n")
        f.write(java_biases + "\n")
        f.write(java_positions + "\n")
        f.write("// COPY THIS EXTRACT FEATURES METHOD:\n")
        f.write(java_features)

    # Step 8: Save model config
    print("8. Saving model configuration...")
    model_config = {
        'feature_size': 17,
        'hidden_size': 12,
        'output_size': 8,
        'total_weights': len(all_weights),
        'total_biases': len(all_biases),
        'intents': INTENTS,
        'accuracy': {
            'training': float(train_score),
            'test': float(test_score)
        },
        'plants_training_info': {
            'total_examples': len(training_data),
            'plants_science_examples': len([t for t, i in training_data if 'plant' in t.lower() and i == 1]),
            'test_cases': test_cases
        }
    }

    with open('model_config_plants.json', 'w') as f:
        json.dump(model_config, f, indent=2)

    print("\n" + "=" * 50)
    print("✅ Enhanced training completed successfully!")
    print("\n📁 Generated files:")
    print("   - generated_java_code_plants.txt (copy to your Java class)")
    print("   - model_config_plants.json (model configuration)")
    print("   - data/training_data_enhanced.csv (enhanced training dataset)")

    print("\n🔧 Key changes made:")
    print("   1. Added 20+ explicit 'plants' examples with science intent")
    print("   2. Increased feature size to 17 (added 'plants' as keyword)")
    print("   3. Added more training variations for plants")
    print("   4. Increased training iterations to 3000")

    print("\n📋 Next steps:")
    print("1. Run this script to generate new model")
    print("2. Copy arrays from generated_java_code_plants.txt")
    print("3. Update CompressedTinyML with new extractFeatures() method")
    print("4. Test with 'plants' query")
    print("5. Monitor confidence scores in debug output")

if __name__ == "__main__":
    main()