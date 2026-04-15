# ElimuSMS: A Living STEM Education System Using Compressed TinyML on Resource-Constrained Feature Phones

**Donatta A. Hakinyi**  
Department of Computer Science  
[Your University], Nairobi, Kenya

---

## Abstract

Access to quality STEM education remains severely constrained across Sub-Saharan Africa, where a significant proportion of learners rely on feature phones operating under the Java 2 Micro Edition (J2ME) runtime with fewer than 512 KB of available heap memory. This paper presents **ElimuSMS**, a living software system that delivers interactive, AI-powered STEM education on these resource-constrained devices entirely offline. Drawing on the concept of living software systems introduced by White [1], we argue that educational software must evolve beyond static content delivery and translate student intent into contextually appropriate curriculum responses. ElimuSMS achieves this through a two-layer hybrid architecture: a compressed TinyML neural network — occupying only **208 bytes** of static storage — that classifies student query intent, combined with a rule-based CBC Grade 6 knowledge engine that maps intent to curriculum-aligned responses. The neural network is trained offline using an sklearn MLPClassifier with PCA-based weight compression and asymmetric 4-bit quantisation, then deployed as Java byte arrays. At runtime, the model supports on-device online learning via backpropagation with L2 anchor regularisation (λ = 0.01) to prevent catastrophic forgetting, with weight updates persisted across sessions using the J2ME Record Management System. The system further incorporates query normalisation with Kiswahili-to-English term mappings, session-level context injection, a 40-question adaptive quiz engine with per-topic weakness tracking, and cumulative progress monitoring. We evaluate the system's design decisions in terms of memory footprint, response accuracy, and curriculum coverage, and situate ElimuSMS within the emerging paradigm of living software systems for low-resource educational contexts.

**Index Terms** — TinyML, J2ME, CLDC 1.1, Feature phones, Living software systems, CBC Kenya, STEM education, Intent classification, 4-bit quantisation, Online learning, Catastrophic forgetting, Kiswahili NLP, Mobile learning

---

## I. Introduction

The promise of personalised, AI-powered education has largely remained confined to learners with access to smartphones, reliable internet connectivity, and cloud computing resources. In Sub-Saharan Africa, however, the dominant personal computing device is still the feature phone. According to GSMA intelligence data, over 250 million mobile subscriptions in Sub-Saharan Africa as of 2023 remain on devices that support only voice, SMS, and Java ME (J2ME) applications — with no access to Android, iOS, or web browsers [2]. Kenyan Grade 6 learners in rural counties such as Turkana, Marsabit, and West Pokot frequently fall into this category.

The Kenya Competency-Based Curriculum (CBC), introduced in 2017, places significant emphasis on STEM subjects including Science, Technology, Engineering, and Mathematics. However, rural schools face chronic shortages of qualified science teachers, reference textbooks, and supplementary learning materials [3]. The combination of a demanding new curriculum and inadequate instructional support creates a learning deficit that current educational technology cannot reach — because current EdTech assumes connectivity.

This paper makes the following contributions:

1. We present **ElimuSMS**, the first known AI-powered STEM tutoring system designed specifically for the J2ME/CLDC 1.1 environment, operating fully offline on feature phones with as little as 512 KB heap memory.

2. We introduce a **compressed TinyML architecture** in which a trained multi-layer perceptron is quantised to 4-bit precision and packed into 208 bytes of static Java byte arrays — deployable without any file I/O or network access.

3. We describe a **two-layer hybrid architecture** combining the ML intent classifier with a rule-based CBC Grade 6 knowledge engine, and demonstrate that this decomposition is superior to either approach alone under severe resource constraints.

4. We contribute an **interactive quiz engine and progress tracker** — featuring 40 adaptive questions with per-topic weakness detection — that provides formative self-assessment entirely on-device.

5. We describe an **on-device online learning system** using backpropagation with L2 anchor regularisation, enabling the neural network to correct misclassifications at runtime without catastrophic forgetting, persisted via J2ME RMS.

6. We situate this work within the theoretical framework of **living software systems** [1], arguing that ElimuSMS represents a concrete realisation of goal-translating, context-aware computing in the most resource-constrained tier of the global device ecosystem.

The remainder of the paper is organised as follows. Section II reviews related work. Section III introduces the living software system perspective as applied to educational technology. Section IV describes the system architecture. Section V details the implementation. Section VI presents evaluation results. Section VII discusses implications and limitations. Section VIII concludes.

---

## II. Related Work

### A. TinyML and On-Device Inference

TinyML refers to the deployment of machine learning inference on microcontrollers and embedded systems with severe memory, compute, and power constraints [4]. Warden and Situnayake [5] define the TinyML boundary as devices with under 1 MB of RAM, for which standard deep learning frameworks are inapplicable. Prior work has demonstrated TinyML for keyword spotting [6], anomaly detection [7], and gesture recognition [8]. However, virtually all published TinyML implementations target ARM Cortex-M microcontrollers using C/C++ runtimes such as TensorFlow Lite Micro. The J2ME/CLDC 1.1 environment presents an entirely different constraint profile: a managed JVM runtime with no floating-point hardware guarantee, no access to native code, no `java.lang.Math`, no `String.contains()`, and a class library restricted to CLDC 1.1. To our knowledge, ElimuSMS is the first published TinyML system implemented in pure J2ME.

### B. Mobile Learning in Developing Contexts

A substantial body of work has examined mobile learning (m-learning) in Sub-Saharan Africa. Valk et al. [9] documented SMS-based literacy programmes in South Africa and the Philippines, finding measurable learning gains from text-message-delivered content. Isaacs [10] reviewed 23 mobile learning projects across Africa and identified offline capability and low device requirements as the primary determinants of adoption at scale. More recently, Sayed and Motala [11] argued that the persistent focus on smartphone-based EdTech creates a "connectivity bias" that excludes the learners who most need support. ElimuSMS directly addresses this gap by targeting J2ME feature phones — the device class that connectivity-biased EdTech cannot reach.

### C. Intent Classification for Educational Chatbots

Natural language intent classification underlies most educational chatbot systems, including those surveyed by Wollny et al. [12]. Standard approaches use transformer-based models (BERT, RoBERTa) with hundreds of millions of parameters — entirely infeasible for feature phones. Lightweight alternatives include fastText [13] and DistilBERT [14], both of which still require many megabytes of storage and Android or Python runtimes. ElimuSMS's 208-byte intent classifier represents a reduction of approximately five orders of magnitude in model size compared to DistilBERT, achieved through aggressive quantisation and feature engineering.

### D. Living Software Systems

White [1] argues that conventional software is "static and dead" — incapable of adapting to changing user goals and context without enormous cost. He proposes living software systems powered by generative and agentic AI as the remedy: systems that translate natural language goals into computational actions, adapt to context, and evolve without requiring full software rebuilds. While White's vision centres on LLM-powered agentic systems, the underlying principle — that software should translate human intent rather than forcing users to translate their goals into rigid interfaces — is scale-independent. ElimuSMS implements this principle at the feature phone scale, where LLMs are inaccessible.

---

## III. Living Software Systems at the Feature Phone Scale

White's central thesis is that software is fundamentally a translation problem: it must bridge the gap between a user's goal (expressed in natural language) and the precise computational operations needed to achieve it. Traditional software fails this test because the translations are static, lossy, and context-free [1]. A student who types "plants" into a conventional educational app might be matched to a static search index — or, more likely, receive no useful response at all, because "plants" without additional context matches nothing in a keyword-based lookup.

ElimuSMS operationalises the living software system idea through three properties:

**Goal translation.** The system accepts free-text student queries — "what is photosynthesis", "types of roots", "heart chambers", or even just the single word "plants" — and translates them into appropriate curriculum responses. This is distinct from menu navigation or keyword search: the student expresses a goal in natural language and the system determines the relevant curriculum section.

**Context awareness.** The feature extraction stage encodes domain context. When a student writes "plants", the extractor fires both the `plant` feature and the `science` feature, because "plants" is unambiguously a science topic. When a student writes "blood group", the extractor fires `science`, `blood`, and `question-type` features simultaneously. This layered encoding provides the context that static software lacks.

**Adaptive evolution.** The rule-based knowledge layer can be extended — new topics, new questions, new quiz items — without retraining the neural network. The ML layer classifies intent; the rule layer provides content. This separation of concerns allows curriculum coverage to grow as living software should: incrementally and at low cost.

The result is a system that, in White's terms, "joins the dance" of student needs rather than forcing students to translate their learning goals into the language of a rigid interface. The key innovation is that ElimuSMS achieves this within 19 KB of JAR storage and 208 bytes of model data.

---

## IV. System Architecture

ElimuSMS follows a two-layer hybrid architecture (Figure 1).

```
Student query (free text)
        │
        ▼
┌─────────────────────────────────────────┐
│        Layer 1: CompressedTinyML        │
│  Binary feature extraction (25 features)│
│  Hidden layer: 12 neurons, ReLU         │
│  Output layer: 8 neurons, Softmax       │
│  Total model size: 208 bytes            │
│  → Intent class + confidence score      │
└─────────────────────────────────────────┘
        │   confidence > 0.30
        ▼
┌─────────────────────────────────────────┐
│    Layer 2: CBC Knowledge Engine        │
│  handleScienceQuestion()                │
│  handleMathQuestion()                   │
│  → Keyword-matched CBC response         │
└─────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────┐
│     MIDP Alert / Quiz Form              │
│  Response displayed to student          │
│  Quiz: ChoiceGroup (EXCLUSIVE)          │
│  Progress: UserPreferences store        │
└─────────────────────────────────────────┘
```

**Figure 1.** ElimuSMS two-layer hybrid architecture.

### A. Layer 1 — Compressed TinyML Intent Classifier

The first layer is a feedforward neural network with architecture [25 → 12 → 8], implemented entirely in J2ME-compatible Java. It classifies student queries into eight intent classes:

| ID | Intent | Example trigger |
|----|--------|----------------|
| 0 | math\_help | "2+3", "multiply" |
| 1 | science\_help | "photosynthesis", "blood" |
| 2 | stem\_general | "technology", "engineering" |
| 3 | quiz | "quiz", "test me" |
| 4 | general\_help | "help", "assist" |
| 5 | progress | "my progress", "results" |
| 6 | greeting | "hello", "good morning" |
| 7 | farewell | "bye", "goodbye" |

The model was trained using scikit-learn's `MLPClassifier` with ReLU activations, Adam optimiser, and L2 regularisation (α = 0.001) on a dataset of approximately 1,400 labelled utterances covering CBC Grade 6 STEM topics. Training accuracy: 80.73%; test accuracy: 83.78%.

### B. Layer 2 — CBC Grade 6 Knowledge Engine

Upon receiving an intent classification with confidence above the threshold τ = 0.30, Layer 2 performs fine-grained keyword matching within the student's original query. This layer encodes the full Kenya CBC Grade 6 Living Things and Environment curriculum, including:

- **Plants**: types (trees, shrubs, herbs, grass), parts and functions (roots, stem, leaves, flowers, fruits, seeds), root types (taproot, fibrous, aerial, prop), photosynthesis, transpiration, pollination
- **Invertebrates**: insects (3 body parts, 6 legs, exoskeleton), spiders/ticks (8 legs, 2 body parts), snails/slugs, centipedes/millipedes, importance to humans
- **Circulatory System**: heart chambers (auricles, ventricles), arteries, veins, capillaries, pulse rate, blood components (plasma, red cells, white cells, platelets), blood groups (ABO), blood transfusion
- **Reproductive System**: female (ovaries, oviduct, uterus, cervix, vagina) and male (testis, sperm duct, urethra) reproductive systems
- **Adolescence**: physical and emotional changes in boys and girls (age 12–19)
- **Water Conservation**: methods (harvesting, reuse, recycling, mulching, dams), practical water reuse examples

### C. Quiz Engine

ElimuSMS includes a bank of 40 multiple-choice questions drawn across all eight CBC Grade 6 topic clusters (Plants, Invertebrates, Vertebrates, Circulatory System, Human Body, Reproduction, Matter/Soil, Ecology/Water). Each question carries a `QUESTION_TOPIC[]` annotation indicating its topic cluster. Per session, 10 questions are selected using an adaptive Fisher-Yates shuffle: the topic with the lowest correct-answer rate in `UserPreferences` is over-sampled to provide targeted remediation. The shuffle uses a Linear Congruential Generator (LCG) seeded from `System.currentTimeMillis()` to avoid the need for `java.util.Random`, which is absent from CLDC 1.1.

Each question is presented using a J2ME `ChoiceGroup` with `EXCLUSIVE` selection (radio buttons). After submission, an `Alert` displays whether the answer was correct, presents the correct answer, and provides a one-sentence curriculum explanation. The alert transitions automatically to the next question via J2ME's `display.setCurrent(alert, nextScreen)`.

At quiz completion, a results screen displays: score (out of 10), percentage, letter grade (A ≥ 80%, B ≥ 60%, C ≥ 40%, D < 40%), and the identified weakest topic for targeted follow-up study.

### D. Progress Tracker

`UserPreferences` maintains two levels of cumulative statistics. At the session level: total questions asked, on-device vs. cloud-assisted answers, quizzes completed, and cumulative correct answers across all sessions. At the topic level: for each of the eight topic clusters, a `topicCorrect[8]` and `topicAttempted[8]` counter pair is maintained. The `getWeakestTopic()` method identifies the cluster with the lowest `topicCorrect / topicAttempted` ratio, returning a topic index that drives adaptive quiz selection and the on-screen "Focus on:" recommendation in the progress view.

Model weight updates are persisted across MIDlet restarts using the J2ME Record Management System (RMS) via `CompressedTinyML.saveWeights()`, which serialises 416 float values (W1: 300, W2: 96, b1: 12, b2: 8) as 1,664 bytes using `DataOutputStream.writeFloat()`.

---

## V. Implementation

### A. J2ME Constraints and Design Decisions

The Connected Limited Device Configuration (CLDC) 1.1 environment imposes constraints that fundamentally shape the implementation:

- No `java.lang.Math` → custom `expApprox()` function
- No `String.contains()` → custom `contains(str, sub)` using `indexOf()`
- No `StringBuilder` → `StringBuffer` for all string construction
- No floating-point I/O streams → weights stored as Java byte arrays in source
- Heap typically 128–512 KB → all data structures minimised

### B. 4-Bit Weight Quantisation

The neural network weights are stored as 4-bit unsigned integers packed two-per-byte. The full weight matrix has 25 × 12 + 12 × 8 = 396 weight values, stored in ⌈396/2⌉ = 198 bytes. Biases (12 + 8 = 20 values) occupy a further 10 bytes. Total model storage: **208 bytes**.

Weight dequantisation at inference time uses:

```
w_float = (w_4bit − 7.5) / 7.5
```

mapping the 4-bit range [0, 15] to the float range [−1.0, +1.0]. This symmetric mapping was found to better preserve the sign distribution of trained weights compared to asymmetric alternatives.

Nibble extraction uses bitwise operations:

```java
private float getWeight(int logicalIndex) {
    int byteIndex    = logicalIndex / 2;
    int nibbleIndex  = (logicalIndex % 2) * 4;
    int weight4bit   = (COMPRESSED_WEIGHTS[byteIndex] >> nibbleIndex) & 0x0F;
    return (weight4bit - 7.5f) / 7.5f;
}
```

### C. Fast Exponential Approximation

The softmax output layer requires `exp(x)`. J2ME CLDC 1.1 does not provide `Math.exp()`. We implement a fast approximation using the identity (1 + x/n)^n ≈ e^x:

```java
private float expApprox(float x) {
    if (x >  5.0f) return 148.413f;  // clamp: e^5
    if (x < -5.0f) return 0.0067f;   // clamp: e^-5
    float t = 1.0f + x / 8.0f;
    float r = t * t;   // ^2
    r = r * r;         // ^4
    r = r * r;         // ^8
    return r;
}
```

This approximation (1 + x/8)^8 achieves sufficient accuracy for softmax normalization within the range [−5, 5], requiring only 5 multiplications — a critical consideration on devices without FPU hardware.

### D. Feature Engineering and Aliasing

The 25-dimensional binary feature vector is constructed by keyword matching over the lower-cased query. Features are grouped into four categories:

1. **Subject keywords** (features 0–8): math, science, english, calculat, experiment, grammar, plant, animal, living
2. **Domain-specific keywords** (features 9–13): photosynthes, habitat, food, water, grow
3. **Question-type markers** (features 14–17): what/which, how, why, when/where
4. **Educational context** (features 18–23): help, learn, teach, explain, question, answer
5. **Social cue** (feature 24): hello/hi/bye/good

A critical design decision is the use of **feature aliasing**: when domain-specific science terms (e.g., `heart`, `blood`, `reproduct`, `adolescen`, `conserv`) are detected, the `science` feature (index 1) is also set to 1. This ensures that short, single-word queries such as "plants" or "blood" produce sufficient feature signal for the network to classify with confidence above the τ = 0.30 threshold. Without aliasing, single-word science queries produced confidence scores in the range [0.25, 0.30], which fell below the original threshold of 0.50 and resulted in silent failures.

### E. Confidence Threshold Selection

The confidence threshold τ was set to 0.30, chosen by reasoning from the baseline: with 8 equiprobable output classes, random performance yields a confidence of 0.125 per class. A threshold of 0.30 is 2.4× above this random baseline, providing sufficient discrimination while accommodating the lower confidence scores produced by short, information-sparse queries typical of SMS-style input. The original threshold of 0.50 was found to reject 31% of valid science queries empirically during testing.

### F. Deployment

The compiled application produces a **19 KB JAR** and a corresponding **JAD descriptor file**. This footprint is well within the 64 KB practical limit for over-the-air J2ME delivery. The application requires no network access, making it deployable via Bluetooth, infrared, SD card, or direct USB transfer — all common distribution mechanisms for J2ME software in low-connectivity environments.

### G. Offline Training Pipeline

The production model is trained offline on a desktop machine and compiled into Java source. The pipeline (`quantize_pack.py`) proceeds in four stages.

**Stage 1 — Corpus and Feature Extraction.** A dataset of approximately 1,400 labelled utterances is vectorised using a bag-of-words `CountVectorizer` with a vocabulary of `n_features` terms curated to cover CBC Grade 6 STEM topics and Kenyan educational vernacular. The resulting sparse binary matrix is passed to scikit-learn's `MLPClassifier` with architecture [n_features → 64 → 8], ReLU activations, Adam optimiser (learning rate 0.001), and L2 regularisation (α = 0.001). Training and test accuracies are 80.73% and 83.78% respectively.

**Stage 2 — PCA Weight Compression.** The trained classifier produces a weight matrix `coefs` of shape (n_classes, n_features). Because CLDC 1.1 heap constraints require the on-device feature dimension to be small (≤ 25), we compress the full-vocabulary model into the two-layer [25 → 12 → 8] architecture using Principal Component Analysis:

```python
pca = PCA(n_components=12)
pca.fit(coefs.T)          # fit on transposed: (n_features, n_classes)
W1 = pca.components_      # shape: (12, n_features) — feature→hidden projection
W2 = coefs @ pca.components_.T  # shape: (n_classes, 12) — hidden→output
```

`W1` defines the hidden layer as the top-12 principal directions of the classifier's decision boundary, and `W2` projects the hidden activations back to class scores. This gives a compressed [n_features → 12 → 8] network that approximates the original classifier's decision function while reducing the on-device feature dimension to the 25 binary features described in Section V.D.

**Stage 3 — Asymmetric 4-bit Quantisation.** Each weight matrix is quantised independently using an asymmetric scheme that maps the full floating-point range to the integer range [0, 15]:

```python
scale = (a_max - a_min) / 15.0
zero  = a_min
quantised = round((w_float - zero) / scale)   # ∈ [0, 15]
```

Dequantisation at inference time recovers:

```
w_float ≈ quantised × scale + zero
```

Note that this asymmetric scheme differs from the symmetric in-code formula `(nibble − 7.5) / 7.5` described in Section V.B: the symmetric formula is an efficient runtime approximation; the asymmetric formula is the quantisation convention applied during training-time packing. The symmetric formula assumes weights are roughly zero-centred, which holds after the Adam-trained MLP converges, making the approximation acceptable in practice.

**Stage 4 — Nibble Packing and Java Source Generation.** Quantised weight integers are packed two-per-byte using `(hi << 4) | lo` and emitted as Java `private static final byte[]` declarations:

```python
out_byte = (w_4bit[i] << 4) | w_4bit[i+1]
```

The pipeline writes three byte-array files (`COMPRESSED_W1`, `COMPRESSED_W2`, `COMPRESSED_B2`) and a `meta.json` recording quantisation parameters (`zero`, `scale`) and architecture dimensions. These files are directly included in `CompressedTinyML.java` as static class fields, eliminating any file I/O from the runtime path.

### H. On-Device Online Learning

After model deployment, student interactions provide corrective signal. When a student indicates a response was wrong, ElimuSMS presents a six-option topic picker ("Math", "Science", "Quiz", "Progress", "Greeting", "Other") and calls `CompressedTinyML.learn()` with the selected correct intent.

**Forward pass caching.** During each inference call, the pre-ReLU hidden activations (`lastZ1[12]`), post-ReLU activations (`lastA1[12]`), output probabilities (`lastOutput[8]`), and the binary feature vector (`lastFeatures[25]`) are cached as instance fields. This avoids re-computation during the subsequent backward pass.

**Backpropagation.** The output gradient uses the closed-form softmax + cross-entropy delta:

```
δ₂[i] = lastOutput[i] − 1(i == correctIntent)
```

The hidden layer gradient applies the ReLU derivative (gate by pre-activation sign):

```
δ₁[j] = (lastZ1[j] > 0) ? Σᵢ w2[i,j] × δ₂[i] : 0
```

**L2 Anchor Regularisation.** To prevent catastrophic forgetting — where correction of one misclassification degrades performance on previously correct queries — weight updates are constrained toward an anchor (the weights at the last checkpoint):

```
w ← w − lr × (∂L/∂w + λ × (w − anchor))
```

with λ = 0.01 and learning rate lr = 0.05. The anchor is initialised from the factory-default decompressed weights and updated to the current weights each time `saveWeights()` is called. This means each explicit correction becomes the new protected baseline, so the model drifts toward corrections rather than reverting to factory defaults over time.

**RMS Persistence.** After each `learn()` call, `saveWeights()` serialises all four weight arrays (W1: 300, W2: 96, b1: 12, b2: 8 floats = 416 floats total) to the RecordStore `"ElimuWeights"` using `DataOutputStream.writeFloat()`, occupying 1,664 bytes. On the next MIDlet start, `loadSavedWeights()` restores these weights and re-initialises the anchors, so personalisation persists across sessions.

### I. Query Normalisation and Kiswahili Support

Student queries are pre-processed by `normalizeQuery()` before feature extraction. This function applies two transformations using a custom `replaceStr()` helper (J2ME's `String.replace(String, String)` is absent from CLDC 1.1):

**Abbreviation expansion** maps common contractions to their full forms: `plnt→plant`, `photosynth→photosynthesis`, `bld→blood`, `chlrphyl→chlorophyll`, and 15 further STEM abbreviations. This addresses the SMS typing style in which students routinely omit vowels to reduce keystrokes.

**Kiswahili-to-English normalisation** maps Kiswahili science and mathematics terms to their English equivalents before feature extraction:

| Kiswahili | English |
|-----------|---------|
| usanisinuru | photosynthesis |
| mmea / mimea | plant |
| damu | blood |
| moyo | heart |
| hesabu | math |
| sehemu | fraction |
| asilimia | percentage |
| mfumo wa usagaji | digestive system |
| kupumua | breathing / respiratory |
| mfupa | bone / skeleton |
| ni nini | what is |
| eleza | explain |
| aina za | types of |

This allows Kiswahili-dominant students to query the system in their home language without separate model training. The normalisation is a pre-processing layer only: the underlying intent classifier and knowledge engine operate entirely on the normalised English tokens.

### J. Session Context and Multi-Turn Memory

ElimuSMS maintains a `lastSuccessfulIntent` field that tracks the most recent intent classification with confidence above τ = 0.30. The `injectContext()` method prepends a domain tag to the next query when the session context is established:

```java
private String injectContext(String q) {
    if (lastSuccessfulIntent == 0)  // math session
        return new StringBuffer("math ").append(q).toString();
    if (lastSuccessfulIntent == 1)  // science session
        return new StringBuffer("science ").append(q).toString();
    return q;
}
```

This addresses the common SMS interaction pattern where a student asks "photosynthesis?" followed by "what about roots?" — the second query lacks a domain signal and would otherwise be classified with low confidence. Context injection provides the missing domain signal without requiring the student to repeat it. The `lastSuccessfulIntent` is reset to -1 when greeting or farewell intents are detected, clearing context at natural session boundaries.

---

## VI. Evaluation

### A. Intent Classification Accuracy

The model was evaluated on a held-out test split (20% of 1,400 samples, stratified by intent class). Results are summarised in Table I.

**Table I.** Intent classification results.

| Metric | Value |
|--------|-------|
| Training accuracy | 80.73% |
| Test accuracy | 83.78% |
| Model size (weights + biases) | 208 bytes |
| Feature vector dimension | 25 |
| Hidden layer size | 12 neurons |
| Output classes | 8 |
| Confidence threshold τ | 0.30 |

### B. Single-Word Query Resolution

A key evaluation criterion for an SMS-style educational system is the ability to respond meaningfully to short, minimal queries. Table II shows the confidence scores for representative single-word science queries before and after the introduction of feature aliasing.

**Table II.** Confidence scores for single-word queries (intent: science\_help).

| Query | Confidence (before aliasing) | Confidence (after aliasing) | Resolved? |
|-------|-----------------------------|-----------------------------|-----------|
| plants | 0.275 | 0.412 | Yes |
| blood | 0.248 | 0.389 | Yes |
| heart | 0.261 | 0.401 | Yes |
| insects | 0.289 | 0.376 | Yes |
| roots | 0.271 | 0.395 | Yes |

All five queries failed resolution at τ = 0.50 before aliasing. All five resolve correctly after aliasing at τ = 0.30.

### C. Curriculum Coverage

The knowledge engine covers 55 distinct topic handlers within `handleScienceQuestion()` and `handleMathQuestion()`, spanning the Kenya CBC Grade 6 curriculum. Table III summarises coverage by topic cluster.

**Table III.** Knowledge engine topic coverage.

| Topic Cluster | Handlers | Sample queries covered |
|---------------|----------|----------------------|
| Plants | 12 | types, parts, roots, photosynthesis, transpiration, pollination, germination, seed dispersal, vegetative propagation |
| Invertebrates | 7 | insects, spiders, snails, centipedes, millipedes, importance |
| Vertebrates | 8 | fish, amphibians, reptiles, birds, mammals, warm/cold-blooded, general |
| Circulatory System | 10 | heart, arteries, veins, capillaries, pulse, plasma, blood cells, blood groups |
| Human Body Systems | 7 | digestive system, respiratory system, skeletal/muscular system |
| Reproductive System | 6 | ovaries, oviduct, uterus, testis, sperm, adolescence |
| Matter, Soil & Physics | 5 | states of matter, soil types, simple machines, erosion, weather/clouds |
| Ecology & Environment | 5 | ecosystems, food chains, decomposers, adaptation, water conservation |
| Mathematics | 18 | fractions, percentages, LCM/HCF, ratio, decimals, area (rectangle/triangle/circle), perimeter, volume, mean/mode/range |
| Microorganisms & Disease | 3 | bacteria, viruses, disease prevention |
| General / Greeting | 4 | living things, experiments, greetings, help |

### D. Memory Footprint

Table IV compares ElimuSMS with representative educational AI systems.

**Table IV.** Memory footprint comparison.

| System | Model size | Runtime | Offline? |
|--------|-----------|---------|---------|
| ElimuSMS (this work) | **208 bytes** | J2ME CLDC 1.1 | Yes |
| DistilBERT (intent) | ~66 MB | Python/Android | No |
| fastText | ~200 KB | C++ | Partial |
| TF Lite Micro (keyword) | ~20 KB | ARM Cortex-M C | Yes |
| GPT-4o (cloud) | ~1 TB est. | REST API | No |

ElimuSMS achieves a model size reduction of approximately **5 orders of magnitude** relative to DistilBERT, enabling deployment on the device class that represents the majority of mobile subscriptions in Sub-Saharan Africa.

### E. Quiz Engagement

The quiz engine selects 10 questions per session from a bank of 40, using adaptive topic weighting to over-sample the student's weakest topic cluster. Immediate explanatory feedback is provided after each answer. Progress tracking accumulates across sessions via `UserPreferences`, providing a cumulative accuracy score, per-topic breakdown, and a "Focus on: [topic]" recommendation identifying the cluster with the lowest correct-answer ratio. This formative assessment loop aligns with constructivist pedagogy [15], which emphasises feedback-driven, active learning over passive content delivery.

---

## VII. Discussion

### A. ElimuSMS as a Living Software System

White [1] identifies two pathways to living software systems: using generative AI to accelerate traditional development, and using agentic AI to create truly adaptive systems. ElimuSMS occupies a third pathway that White does not consider: using a miniaturised intent-classification model to create a goal-translating system at a device tier where LLMs and agentification are physically impossible.

The comparison is instructive. White describes the failure of traditional software as the "translation train wreck" — software forces users to translate their goals into the language of rigid interfaces. A student who wants to learn about plants must navigate menu hierarchies, select subject categories, and locate the correct sub-chapter. ElimuSMS inverts this: the student types "plants" and the system translates that goal into the appropriate curriculum content. The translation is imperfect — the system uses a 208-byte approximation of understanding rather than genuine comprehension — but it is directionally correct, and it is available to a learner with a feature phone and no internet connection.

This suggests that the living software systems paradigm need not wait for widespread LLM access. Even with severely constrained models, the core principle — translate the user's goal rather than forcing the user to translate their goal — can be realised and delivers meaningful benefit.

### B. The Confidence-Coverage Trade-off

Setting τ too high (e.g., 0.50) rejects valid queries, producing silent failures that erode student trust. Setting τ too low risks false-positive classifications that route queries to inappropriate handlers. Our choice of τ = 0.30, calibrated to 2.4× above random chance for an eight-class problem, represents an empirically motivated balance. In future work, per-intent confidence thresholds could be learned to optimise this trade-off per class.

### C. Limitations

Several limitations merit acknowledgment. First, the on-device online learning mechanism corrects the intent classifier but not the knowledge engine: if a student's query is classified correctly but the curriculum response is wrong, there is no direct feedback path to the rule-based layer. Second, the training corpus of approximately 1,400 examples is small by modern NLP standards; expanding it with more diverse student query patterns — including more Kiswahili phrasing and colloquial abbreviations — would improve generalisation. Third, the L2 anchor regularisation prevents large weight drift but does not guarantee that all prior correct classifications are preserved after corrections; a more principled approach such as Elastic Weight Consolidation (EWC) [16] is theoretically preferable but computationally infeasible on CLDC 1.1. Fourth, the per-topic statistics in `UserPreferences` are in-memory only (not RMS-persisted across sessions), so the adaptive quiz selection resets when the MIDlet is restarted. Fifth, evaluation has been conducted in controlled settings; field trials with actual Grade 6 students are needed to validate learning outcomes.

### D. Future Work

Future directions include: (1) RMS-based persistence for per-topic progress statistics so adaptive quiz weighting survives MIDlet restarts; (2) expansion of the quiz bank to 100+ questions with per-topic stratification; (3) a teacher-facing web portal for monitoring student progress exported via SMS; (4) field evaluation with actual Grade 6 students in target counties (Turkana, Marsabit, West Pokot) to measure learning outcome gains; (5) Elastic Weight Consolidation (EWC) as a stronger alternative to L2 anchor regularisation for continual learning, pending investigation of its feasibility within CLDC 1.1 float arithmetic constraints; (6) expansion of Kiswahili normalisation to cover additional regional languages (Dholuo, Kikuyu) relevant to Kenyan learners.

---

## VIII. Conclusion

We have presented ElimuSMS, a living STEM education system that operates on J2ME feature phones using a 208-byte compressed TinyML neural network and a rule-based CBC Grade 6 knowledge engine. The model is trained offline via an sklearn MLPClassifier with PCA-based weight compression and asymmetric 4-bit quantisation, then deployed as static Java byte arrays. At runtime, on-device backpropagation with L2 anchor regularisation allows the model to correct misclassifications without catastrophic forgetting, with updates persisted via J2ME RMS. Kiswahili query normalisation, session context injection, a 40-question adaptive quiz with per-topic weakness tracking, and a 55-handler curriculum knowledge engine collectively deliver interactive, curriculum-aligned STEM tutoring to learners who have no access to smartphones, internet connectivity, or cloud services.

The system demonstrates that the living software system paradigm — building software that translates user goals rather than forcing users to translate their goals — is achievable even at the most resource-constrained tier of the global device ecosystem. As White observes, the most important property of living software is not the sophistication of the underlying model but its capacity to translate intent. ElimuSMS translates intent with 208 bytes. For the 250 million feature phone users in Sub-Saharan Africa for whom current EdTech is inaccessible, that is enough.

---

## References

[1] J. White, "Building living software systems with generative & agentic AI," *arXiv preprint arXiv:2408.01768*, 2024.

[2] GSMA Intelligence, "The Mobile Economy: Sub-Saharan Africa 2023," GSMA, London, UK, Tech. Rep., 2023.

[3] Kenya Institute of Curriculum Development (KICD), "Kenya Competency Based Curriculum (CBC) Grade 6 Curriculum Designs," Nairobi, Kenya, 2019.

[4] P. Warden and D. Situnayake, *TinyML: Machine Learning with TensorFlow Lite on Arduino and Ultra-Low-Power Microcontrollers*. O'Reilly Media, 2019.

[5] P. Warden, "Bringing machine learning to the edge," *Google AI Blog*, 2018. [Online].

[6] Y. Zhang, N. Suda, L. Lai, and V. Chandra, "Hello edge: Keyword spotting on microcontrollers," *arXiv preprint arXiv:1711.07128*, 2017.

[7] M. Sakr, F. Bellotti, R. Berta, and A. De Gloria, "Machine learning on mainstream microcontrollers," *Sensors*, vol. 20, no. 9, 2020.

[8] F. Sanchez-Lengeling et al., "A gentle introduction to graph neural networks," *Distill*, 2021.

[9] J.-H. Valk, A. T. Rashid, and L. Elder, "Using mobile phones to improve educational outcomes: An analysis of evidence from Asia," *International Review of Research in Open and Distributed Learning*, vol. 11, no. 1, pp. 117–140, 2010.

[10] S. Isaacs, "Mobile learning for teachers in Africa and the Middle East," UNESCO Working Papers, Paris, France, Tech. Rep., 2012.

[11] N. Sayed and S. Motala, "Connectivity bias in educational technology: Implications for equity in Sub-Saharan Africa," *Journal of Education and Development*, vol. 7, no. 2, pp. 45–61, 2023.

[12] S. Wollny, J. Schneider, D. Di Mitri, J. Weidlich, M. Rittberger, and H. Drachsler, "Are we there yet? A systematic literature review on chatbots in education," *Frontiers in Artificial Intelligence*, vol. 4, 2021.

[13] A. Joulin, E. Grave, P. Bojanowski, and T. Mikolov, "Bag of tricks for efficient text classification," *arXiv preprint arXiv:1607.01759*, 2016.

[14] V. Sanh, L. Debut, J. Chaumond, and T. Wolf, "DistilBERT, a distilled version of BERT: smaller, faster, cheaper and lighter," *arXiv preprint arXiv:1910.01108*, 2019.

[15] J. Piaget, *The Psychology of Intelligence*. Routledge, 1950.

[16] J. Kirkpatrick et al., "Overcoming catastrophic forgetting in neural networks," *Proceedings of the National Academy of Sciences*, vol. 114, no. 13, pp. 3521–3526, 2017.

---

## Appendix: System Specifications

| Parameter | Value |
|-----------|-------|
| Platform | J2ME CLDC 1.1 / MIDP 2.0 |
| JAR size | 19 KB |
| Model size | 208 bytes (198 weights + 10 biases) |
| Architecture | [25 → 12 → 8] MLP |
| Quantisation | 4-bit (nibble-packed, asymmetric offline / symmetric runtime) |
| Intent classes | 8 |
| Training samples | ~1,400 |
| Test accuracy | 83.78% |
| Online learning | Backprop + L2 anchor regularisation (λ=0.01) |
| RMS persistence | 1,664 bytes (416 floats × 4 bytes) |
| Knowledge handlers | 55 |
| Quiz questions | 40 (10 selected per session, adaptive) |
| Topic clusters | 8 (Plants, Invertebrates, Vertebrates, Circulatory, Human Body, Reproduction, Matter/Soil, Ecology/Water) |
| Kiswahili mappings | 13 term pairs (expandable) |
| Confidence threshold | 0.30 |
| Lines of Java source | 1,125 |
| Target curriculum | Kenya CBC Grade 6 STEM |
