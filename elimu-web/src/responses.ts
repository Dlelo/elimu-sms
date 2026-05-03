// Topic-specific response table for offline/low-confidence queries.
//
// Ported faithfully from ElimuSMSMidlet.handleMathQuestion and
// handleScienceQuestion (Java). The order matters — the J2ME side uses
// an if/else chain where the first matching keyword wins, so we model
// that with an ordered array and find() rather than a hash map.
//
// Adding new topics: insert a row above any more general entry whose
// keyword would otherwise short-circuit (e.g., "small intestine" must
// come before plain "intestin").

interface Entry {
  /** True if this entry matches the lower-cased question. */
  match(lower: string): boolean;
  /** The response shown to the learner (CBC Grade-6 micro-lesson). */
  text: string;
  /** UI bubble title; also used as the meta tag. */
  title: string;
}

function has(s: string, w: string): boolean {
  return s.indexOf(w) >= 0;
}

// ── Math content (mirrors handleMathQuestion order) ────────────────────────

const MATH_ENTRIES: readonly Entry[] = [
  {
    match: (l) => has(l, "lcm") || has(l, "least common"),
    title: "Math — LCM",
    text: "LCM (Least Common Multiple): the smallest number that is a multiple of two numbers.\nExample: LCM of 12 and 18\nMultiples of 12: 12, 24, 36...\nMultiples of 18: 18, 36...\nLCM = 36\nUse: adding fractions with different denominators.",
  },
  {
    match: (l) => has(l, "hcf") || has(l, "highest common") || has(l, "gcd"),
    title: "Math — HCF",
    text: "HCF (Highest Common Factor): the largest number that divides two numbers exactly.\nExample: HCF of 12 and 18\nFactors of 12: 1,2,3,4,6,12\nFactors of 18: 1,2,3,6,9,18\nHCF = 6\nUse: simplifying fractions.",
  },
  {
    match: (l) => has(l, "improper") || has(l, "mixed number"),
    title: "Math — Improper / Mixed",
    text: "Improper fraction: numerator is bigger than denominator. Example: 7/3\nMixed number: whole number + fraction. Example: 2 1/3\nConvert: 7/3 = 2 remainder 1 = 2 1/3\nConvert back: 2 1/3 = (2×3+1)/3 = 7/3",
  },
  {
    match: (l) => has(l, "fraction"),
    title: "Math — Fractions",
    text: "Fractions show part of a whole.\nNumerator (top) / Denominator (bottom)\nExample: Amina has 8 mangoes. She eats 3. She ate 3/8 of the mangoes.\nEquivalent: 1/2 = 2/4 = 4/8\nSimplify: 6/8 = 3/4 (divide by HCF=2)",
  },
  {
    match: (l) => has(l, "percent"),
    title: "Math — Percentages",
    text: "Percentage means out of 100.\nExample: Kamau scored 18/20 in a test.\n18/20 × 100 = 90%\nProfit %: a shopkeeper bought maize for Ksh 200, sold for Ksh 250.\nProfit = 50, profit % = 50/200 × 100 = 25%",
  },
  {
    match: (l) => has(l, "ratio"),
    title: "Math — Ratio",
    text: "Ratio compares two quantities.\nExample: in a class of 30, there are 18 girls and 12 boys.\nGirls : boys = 18:12 = 3:2\nShare Ksh 500 in ratio 2:3 → 5 parts, one part = 100.\n2 parts = Ksh 200, 3 parts = Ksh 300.",
  },
  {
    match: (l) => has(l, "decimal"),
    title: "Math — Decimals",
    text: "Decimals use a dot to show parts less than one.\n0.5 = 5/10 = 1/2\n0.25 = 25/100 = 1/4\n0.75 = 75/100 = 3/4\nAdding: 1.5 + 2.3 = 3.8\nMultiplying: 0.4 × 3 = 1.2",
  },
  {
    match: (l) => has(l, "mean") || has(l, "average"),
    title: "Math — Mean",
    text: "Mean (average): add all values, divide by how many.\nExample: marks of 5 pupils — 60, 72, 58, 80, 65.\nSum = 335, Mean = 335 / 5 = 67.\nThe average score is 67 marks.",
  },
  {
    match: (l) => has(l, "mode"),
    title: "Math — Mode",
    text: "Mode: the value that appears most often in a set of data.\nExample: ages 12, 13, 12, 14, 13, 12, 15\nMode = 12 (appears 3 times)\nA set can have more than one mode (bimodal).",
  },
  {
    match: (l) => has(l, "range"),
    title: "Math — Range",
    text: "Range: difference between the highest and lowest value.\nExample: rainfall (mm) — 40, 25, 60, 15, 50.\nHighest = 60, lowest = 15. Range = 60 − 15 = 45 mm.",
  },
  {
    match: (l) => has(l, "area") && has(l, "triangle"),
    title: "Math — Area of Triangle",
    text: "Area of a triangle = ½ × base × height\nExample: base = 8 cm, height = 5 cm.\nArea = ½ × 8 × 5 = 20 sq cm.",
  },
  {
    match: (l) => has(l, "area") && has(l, "circle"),
    title: "Math — Area of Circle",
    text: "Area of a circle = π × r × r ≈ 3.14 × r².\nExample: radius = 7 cm.\nArea = 3.14 × 7 × 7 = 153.86 sq cm.\nDiameter = 2 × radius.",
  },
  {
    match: (l) => has(l, "area") && (has(l, "rectangle") || has(l, "square")),
    title: "Math — Area",
    text: "Area of rectangle = length × width.\nExample: l = 10 cm, w = 4 cm → 40 sq cm.\nArea of square = side × side.\nExample: side = 6 cm → 36 sq cm.",
  },
  {
    match: (l) => has(l, "area"),
    title: "Math — Area",
    text: "Area formulas:\nRectangle = length × width\nSquare = side × side\nTriangle = ½ × base × height\nCircle = 3.14 × r²\nParallelogram = base × height",
  },
  {
    match: (l) => has(l, "perimeter"),
    title: "Math — Perimeter",
    text: "Perimeter: total length around a shape.\nRectangle = 2 × (length + width)\nExample: l = 8 cm, w = 3 cm → P = 2 × 11 = 22 cm.\nSquare = 4 × side.\nCircle (circumference) = 2 × π × radius.",
  },
  {
    match: (l) => has(l, "volume"),
    title: "Math — Volume",
    text: "Volume formulas:\nCuboid = length × width × height.\nExample: 5 × 4 × 3 = 60 cm³.\nCube = side³.\nCylinder = π × r² × height.",
  },
];

// ── Science content (mirrors handleScienceQuestion order) ──────────────────

const SCIENCE_ENTRIES: readonly Entry[] = [
  // Plants
  { match: (l) => has(l, "photosynthes"),
    title: "Science — Photosynthesis",
    text: "Photosynthesis: plants make food using sunlight, water, and CO₂.\nNeeds: chlorophyll, water, carbon dioxide, sunlight.\nProduces: glucose (food) + oxygen (released through stomata)." },
  { match: (l) => has(l, "chlorophyll"),
    title: "Science — Chlorophyll",
    text: "Chlorophyll: the green colouring matter in leaves.\nIt absorbs sunlight, which provides the energy for photosynthesis." },
  { match: (l) => has(l, "stomata") || has(l, "stoma"),
    title: "Science — Stomata",
    text: "Stomata: tiny holes on leaves.\nThey let in CO₂ for photosynthesis and release O₂ and water vapour (transpiration)." },
  { match: (l) => has(l, "transpir"),
    title: "Science — Transpiration",
    text: "Transpiration: plants lose excess water through stomata in the leaves.\nHigh when: hot, sunny, dry, windy.\nLow when: cold, wet, calm, rainy." },
  { match: (l) => has(l, "pollinat"),
    title: "Science — Pollination",
    text: "Pollination transfers pollen from anther to stigma to fertilise flowers.\nAgents: wind and insects (especially bees).\nThis enables plants to produce seeds and fruit." },
  { match: (l) => has(l, "germinat"),
    title: "Science — Germination",
    text: "Germination: a seed sprouts into a seedling.\nNeeds: water, warmth, oxygen (NOT light initially).\nStages: seed absorbs water → radicle (root) emerges → plumule (shoot) emerges → seedling grows leaves." },
  { match: (l) => has(l, "vegetative") || has(l, "cutting") || has(l, "grafting"),
    title: "Science — Vegetative Propagation",
    text: "Vegetative propagation: growing new plants from plant parts (not seeds).\nMethods:\n• Cuttings — sugarcane, cassava, kales\n• Grafting — fruit trees (mango, avocado)\n• Runners — strawberry, couch grass\n• Tubers — Irish potato, arrowroot\n• Bulbs — onions, garlic" },
  { match: (l) => has(l, "part") && has(l, "plant"),
    title: "Science — Plant Parts",
    text: "External parts of a plant:\n• Roots — absorb water, anchor, store food\n• Stem — transports water, supports the plant\n• Leaves — photosynthesis, breathing, transpiration\n• Flowers — reproductive organs\n• Fruits — protect seeds, store food\n• Seeds — germinate into new plants" },
  { match: (l) => has(l, "type") && has(l, "root"),
    title: "Science — Root Types",
    text: "Two types of roots:\n1. Taproot — one main root growing deep, with side roots (legumes, acacia, fruit trees).\n2. Fibrous — many equal shallow roots (grass, maize, onions, sugarcane).\nOthers: aerial roots (breathing), prop roots (support)." },
  { match: (l) => has(l, "type") && has(l, "plant"),
    title: "Science — Plant Types",
    text: "Four types of plants:\n1. Trees — big, single trunk (mango, coconut, avocado).\n2. Shrubs — many woody stems (hibiscus, rose, cotton).\n3. Herbs — small, soft green stems (mint, coriander).\n4. Grass — short, narrow leaves." },
  { match: (l) => has(l, "plant"),
    title: "Science — Plants",
    text: "Plants are living things that make their own food (photosynthesis).\nMain parts: roots, stem, leaves, flowers, fruits, seeds.\nGroups: trees, shrubs, herbs, grass.\nAsk about: photosynthesis, chlorophyll, stomata, transpiration, germination, root types." },

  // Digestive system (specific before general)
  { match: (l) => has(l, "small intestine") || (has(l, "intestin") && has(l, "small")),
    title: "Science — Small Intestine",
    text: "Small intestine: absorbs digested nutrients into the blood.\nAbout 6 m long and coiled. Tiny finger-like projections (villi) increase surface area for absorption." },
  { match: (l) => has(l, "large intestine") || (has(l, "intestin") && has(l, "large")),
    title: "Science — Large Intestine",
    text: "Large intestine: absorbs water from indigestible food. Solid waste (faeces) is stored in the rectum until expelled." },
  { match: (l) => has(l, "stomach"),
    title: "Science — Stomach",
    text: "Stomach: churns food, makes hydrochloric acid (kills bacteria), and the enzyme pepsin breaks down protein. Food becomes chyme before moving to the small intestine." },
  { match: (l) => has(l, "liver") || has(l, "bile"),
    title: "Science — Liver",
    text: "Liver: produces bile (stored in gallbladder), detoxifies blood, stores glycogen, makes blood proteins. Bile emulsifies fats in the small intestine." },
  { match: (l) => has(l, "saliva") || has(l, "amylase"),
    title: "Science — Saliva",
    text: "Saliva: produced by salivary glands. Contains the enzyme amylase, which breaks down starch into simpler sugars. Saliva also moistens food for swallowing." },
  { match: (l) => has(l, "digest"),
    title: "Science — Digestive System",
    text: "Digestive system: mouth → oesophagus → stomach → small intestine → large intestine → rectum.\n• Mouth — saliva starts breaking starch.\n• Stomach — acid + pepsin break protein.\n• Small intestine — absorbs nutrients via villi.\n• Large intestine — absorbs water.\n• Rectum — stores waste." },

  // Respiratory system
  { match: (l) => has(l, "diaphragm"),
    title: "Science — Diaphragm",
    text: "Diaphragm: dome-shaped muscle below the lungs.\nInhale → diaphragm contracts and flattens → chest expands → air rushes in.\nExhale → diaphragm relaxes and domes up → chest contracts → air is pushed out." },
  { match: (l) => has(l, "lung"),
    title: "Science — Lungs",
    text: "Lungs: where gaseous exchange happens. Oxygen enters the blood; CO₂ is released. Tiny air sacs called alveoli have thin walls and many blood vessels — large surface area for diffusion." },
  { match: (l) => has(l, "respirat") || has(l, "breath"),
    title: "Science — Respiratory System",
    text: "Respiratory system: breathing in O₂ and breathing out CO₂.\nInhale: diaphragm contracts → chest expands → air in.\nExhale: diaphragm relaxes → chest contracts → air out.\nGaseous exchange happens in the alveoli of the lungs." },

  // Circulatory
  { match: (l) => has(l, "heart"),
    title: "Science — Heart",
    text: "Heart: a muscular organ that pumps blood around the body.\nFour chambers: 2 atria (top), 2 ventricles (bottom).\nRight side pumps blood to the lungs; left side pumps oxygenated blood to the body." },
  { match: (l) => has(l, "blood") || has(l, "circul") || has(l, "vein") || has(l, "artery") || has(l, "arteries"),
    title: "Science — Circulatory System",
    text: "Circulatory system: heart + blood vessels + blood.\nArteries — carry blood AWAY from the heart (red, oxygenated, except pulmonary).\nVeins — carry blood TO the heart (blue, deoxygenated, except pulmonary).\nCapillaries — tiny vessels where exchange happens.\nBlood: plasma + red cells (O₂) + white cells (immunity) + platelets (clotting)." },

  // Skeletal & muscular
  { match: (l) => has(l, "skeleton"),
    title: "Science — Skeleton",
    text: "Human skeleton has 206 bones.\nFunctions:\n1. Support and shape the body.\n2. Protect organs (skull → brain, ribs → heart and lungs).\n3. Allow movement (with muscles).\n4. Produce blood cells (red bone marrow).\n5. Store calcium and minerals." },
  { match: (l) => has(l, "joint"),
    title: "Science — Joints",
    text: "Joint: where two bones meet.\n• Hinge — one direction (knee, elbow, finger).\n• Ball-and-socket — all directions (hip, shoulder).\n• Fixed — immovable (skull bones).\n• Pivot — rotating (neck).\nCartilage cushions joints." },
  { match: (l) => has(l, "muscle"),
    title: "Science — Muscles",
    text: "Muscles attach to bones by tendons. They contract and relax to cause movement.\nThey work in antagonistic pairs:\n• Biceps contracts → arm bends.\n• Triceps contracts → arm straightens.\nThe diaphragm and the heart are also muscles." },
  { match: (l) => has(l, "bone"),
    title: "Science — Bones",
    text: "Bones are made mainly of calcium.\nKey bones:\n• Femur — largest bone (thigh).\n• Skull — protects the brain.\n• Ribs (12 pairs) — protect heart and lungs.\n• Spine (vertebral column) — supports the body." },

  // Vertebrate groups
  { match: (l) => has(l, "amphibian") || has(l, "frog") || has(l, "toad"),
    title: "Science — Amphibians",
    text: "Amphibians: cold-blooded vertebrates.\n• Young (tadpoles) breathe via gills, live in water.\n• Adults breathe via lungs and moist skin.\n• Lay eggs in water.\nExamples: frogs, toads, salamanders." },
  { match: (l) => has(l, "reptile") || has(l, "lizard") || has(l, "snake"),
    title: "Science — Reptiles",
    text: "Reptiles: cold-blooded vertebrates.\n• Dry, scaly skin.\n• Breathe through lungs.\n• Lay leathery-shell eggs on land.\nExamples: lizards, snakes, crocodiles, tortoises." },
  { match: (l) => has(l, "bird") || has(l, "feather"),
    title: "Science — Birds",
    text: "Birds: warm-blooded vertebrates.\n• Feathers and wings.\n• Beak (no teeth).\n• Lay hard-shelled eggs.\n• Hollow bones (most can fly).\nExamples: eagles, pigeons, ostriches." },
  { match: (l) => has(l, "mammal"),
    title: "Science — Mammals",
    text: "Mammals: warm-blooded vertebrates.\n• Hair or fur on body.\n• Give birth to live young (mostly).\n• Feed young with milk from mammary glands.\nExamples: humans, cows, whales, bats, dogs." },
  { match: (l) => has(l, "fish") || has(l, "gill"),
    title: "Science — Fish",
    text: "Fish: cold-blooded vertebrates.\n• Breathe via gills.\n• Covered with scales.\n• Use fins for movement.\n• Lay eggs in water.\nExamples: tilapia, mudfish, shark, catfish." },

  // Simple machines
  { match: (l) => has(l, "lever"),
    title: "Science — Lever",
    text: "Lever: a rigid bar that pivots on a fulcrum.\nThree classes:\n1. Fulcrum between load and effort (seesaw, scissors).\n2. Load between fulcrum and effort (wheelbarrow, nutcracker).\n3. Effort between fulcrum and load (tweezers, fishing rod)." },
  { match: (l) => has(l, "pulley"),
    title: "Science — Pulley",
    text: "Pulley: grooved wheel with a rope.\n• Fixed pulley — changes direction only (flagpole).\n• Movable pulley — reduces effort (cranes, water wells).\n• Block-and-tackle — multiple pulleys (construction)." },
  { match: (l) => has(l, "inclined") || has(l, "ramp"),
    title: "Science — Inclined Plane",
    text: "Inclined plane (ramp): a sloping flat surface.\nReduces the effort needed to raise an object.\nThe longer the ramp, the less effort needed.\nExamples: wheelchair ramps, roads up hills, stairs, loading ramps." },
  { match: (l) => has(l, "wedge"),
    title: "Science — Wedge",
    text: "Wedge: two inclined planes back-to-back.\nUsed to split, cut, or hold.\nExamples: axe, knife, chisel, nail, doorstop, plough.\nThe thinner the wedge, the easier it cuts." },
  { match: (l) => has(l, "screw"),
    title: "Science — Screw",
    text: "Screw: an inclined plane wrapped around a cylinder.\nConverts rotational force into linear force.\nExamples: wood screws, bolts, jar lids, drill bits, water pumps." },
  { match: (l) => has(l, "simple machine") || (has(l, "machine") && (has(l, "type") || has(l, "kind"))),
    title: "Science — Simple Machines",
    text: "Six simple machines (all make work easier):\n1. Lever — rigid bar on fulcrum.\n2. Pulley — grooved wheel with rope.\n3. Inclined plane — sloping surface.\n4. Wedge — two inclined planes.\n5. Screw — inclined plane on a cylinder.\n6. Wheel and axle." },

  // States of matter
  { match: (l) => has(l, "state") && has(l, "matter"),
    title: "Science — States of Matter",
    text: "Three states of matter:\n1. Solid — definite shape AND volume (ice, rock, wood).\n2. Liquid — definite volume, takes shape of container (water, oil).\n3. Gas — no definite shape or volume; fills container (air, steam, CO₂).\nParticles move fastest in gas, slowest in solid." },
  { match: (l) => has(l, "melting") || has(l, "melt"),
    title: "Science — Melting",
    text: "Melting: solid → liquid when heated. Melting point of ice = 0 °C.\nReverse (liquid → solid) is freezing/solidification." },
  { match: (l) => has(l, "boiling") || has(l, "evaporat"),
    title: "Science — Boiling / Evaporation",
    text: "Boiling: liquid → gas at boiling point (100 °C for water).\nEvaporation: liquid → gas below boiling point (surface only).\nExamples: wet clothes drying (evaporation), water boiling (boiling)." },
  { match: (l) => has(l, "condensat"),
    title: "Science — Condensation",
    text: "Condensation: gas → liquid when cooled.\nExamples: water droplets on a cold glass, morning dew on grass, clouds forming, steam hitting a cold surface.\nReverse of evaporation." },
  { match: (l) => has(l, "solid"),
    title: "Science — Solid",
    text: "Solid: particles are closely packed in fixed positions.\n• Definite shape and volume.\n• Cannot be compressed easily.\nExamples: ice, rock, wood, iron, salt." },
  { match: (l) => has(l, "liquid"),
    title: "Science — Liquid",
    text: "Liquid: particles are close but free to move.\n• Definite volume; takes shape of container.\n• Cannot be compressed.\nExamples: water, milk, oil, blood." },

  // Soil
  { match: (l) => has(l, "soil") && (has(l, "type") || has(l, "kind")),
    title: "Science — Soil Types",
    text: "Three types of soil:\n1. Sandy — large particles, drains fast, poor in nutrients.\n2. Clay — tiny particles, holds water, heavy and sticky when wet.\n3. Loam — mixture of sand, silt, clay. BEST for farming." },
  { match: (l) => has(l, "loam"),
    title: "Science — Loam Soil",
    text: "Loam soil: a mixture of sand, silt, and clay.\nBest soil for farming — retains moisture and nutrients, drains well, rich in humus." },
  { match: (l) => has(l, "sandy"),
    title: "Science — Sandy Soil",
    text: "Sandy soil: large particles, drains water quickly, poor in nutrients, light and pale.\nFound in coastal areas and deserts. Improved by adding organic matter." },
  { match: (l) => has(l, "clay") && has(l, "soil"),
    title: "Science — Clay Soil",
    text: "Clay soil: very small particles, holds a lot of water, heavy and sticky when wet.\nGood for pottery and bricks. Improved by adding sand and organic matter." },
  { match: (l) => has(l, "erosion") || (has(l, "soil") && has(l, "erode")),
    title: "Science — Soil Erosion",
    text: "Soil erosion: removal of topsoil by water or wind.\nCauses: deforestation, overgrazing, poor farming.\nPrevention: plant trees and grass, terracing, mulching, contour ploughing, windbreaks." },
  { match: (l) => has(l, "soil") && has(l, "conserv"),
    title: "Science — Soil Conservation",
    text: "Soil conservation: protecting topsoil from erosion.\nMethods: agroforestry, mulching, terracing, windbreaks, crop rotation, contour ploughing." },
  { match: (l) => has(l, "soil"),
    title: "Science — Soil",
    text: "Soil: top layer of the earth where plants grow.\nKenya's farmland is mostly loam — the best soil for farming.\nAsk about: soil types, sandy soil, clay soil, loam, soil erosion, soil conservation." },

  // Microorganisms & disease
  { match: (l) => has(l, "bacteria"),
    title: "Science — Bacteria",
    text: "Bacteria: single-celled microorganisms.\nHarmful: TB, cholera, typhoid, food poisoning.\nUseful: make yoghurt and cheese, fix nitrogen in soil, decompose dead matter." },
  { match: (l) => has(l, "virus"),
    title: "Science — Viruses",
    text: "Viruses: smallest microorganisms; reproduce inside living cells.\nCause: flu, HIV/AIDS, polio, COVID-19, measles.\nPrevented by vaccines and hygiene. Antibiotics do not work on viruses." },
  { match: (l) => has(l, "fungus") || has(l, "fungi"),
    title: "Science — Fungi",
    text: "Fungi: moulds and yeasts.\nHarmful: ringworm, athlete's foot, food spoilage.\nUseful: yeast in bread, mushrooms, antibiotics like penicillin." },
  { match: (l) => has(l, "microorganism") || has(l, "germ") || has(l, "disease"),
    title: "Science — Microorganisms",
    text: "Microorganisms: tiny living things — bacteria, viruses, fungi, protozoa.\nHarmful: cause diseases (TB, cholera, flu, malaria).\nUseful: make bread, yoghurt, cheese; fix nitrogen; decompose waste." },

  // Weather
  { match: (l) => has(l, "weather"),
    title: "Science — Weather",
    text: "Weather: daily atmospheric conditions (sunny, rainy, cloudy, windy).\nClimate: average weather over a long period.\nKenya's two rainy seasons: long rains (March–May) and short rains (October–December)." },

  // Animals (general fallback)
  { match: (l) => has(l, "animal") || has(l, "vertebrat"),
    title: "Science — Animals",
    text: "Animals are living things that move and feed on plants or other animals.\nGroups (vertebrates have backbones): fish, amphibians, reptiles, birds, mammals.\nInvertebrates: insects, worms, snails, spiders." },
];

// ── Public lookup functions ─────────────────────────────────────────────────

export interface ResponseHit {
  text: string;
  title: string;
}

export function mathResponse(question: string): ResponseHit | null {
  const lower = question.toLowerCase();
  for (const e of MATH_ENTRIES) {
    if (e.match(lower)) return { text: e.text, title: e.title };
  }
  return null;
}

export function scienceResponse(question: string): ResponseHit | null {
  const lower = question.toLowerCase();
  for (const e of SCIENCE_ENTRIES) {
    if (e.match(lower)) return { text: e.text, title: e.title };
  }
  return null;
}
