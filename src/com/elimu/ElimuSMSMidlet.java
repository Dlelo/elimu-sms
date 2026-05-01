package com.elimu;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

public class ElimuSMSMidlet extends MIDlet implements CommandListener {

    // ── Screens ──────────────────────────────────────────────────────────────
    private Display    display;
    private List       mainMenu;
    private Form       questionForm;
    private TextField  questionField;
    private Form       quizForm;       // rebuilt per question
    private ChoiceGroup quizChoices;   // radio-button options

    // ── AI ───────────────────────────────────────────────────────────────────
    private CompressedTinyML aiModel;
    private MicroResponses   responses;

    // ── Commands ─────────────────────────────────────────────────────────────
    private Command selectCmd = new Command("Select", Command.OK,     1);
    private Command sendCmd   = new Command("Send",   Command.OK,     1);
    private Command submitCmd = new Command("Submit", Command.OK,     1);
    private Command backCmd   = new Command("Back",   Command.BACK,   2);
    private Command exitCmd   = new Command("Exit",   Command.EXIT,   3);
    private Command okCmd     = new Command("OK",     Command.OK,     1);
    private Command moreCmd   = new Command("More",   Command.SCREEN, 2);

    // ── "More" tier walk state ───────────────────────────────────────────────
    // Each on-screen response remembers the question + intent that produced it.
    // Tapping "More" walks outward: tap 1 tries the cloud (deeper answer when
    // online; broader CBC-level overview when offline); tap 2+ cycles through
    // related-keyword siblings inside the same intent (offline only).
    private String lastQuestion = null;
    private int    lastIntent   = -1;
    private int    moreLevel    = 0;
    private int    relatedCursor = 0;
    private int    lastSuccessfulIntent = -1; // -1 = no context yet

    // ── Quiz state ───────────────────────────────────────────────────────────
    private int quizIndex = 0;
    private int quizScore = 0;
    private int[] quizOrder = new int[10];
    private int quizSessionLength = 10;
    private int quizRandSeed;
    private int quizMode = 0;         // 0 = Math, 1 = Science
    private List quizTypeList;

    // ── Quiz bank (CBC Grade 6, 40 questions) ────────────────────────────────
    private static final String[] QUESTIONS = {
        // Topic 0: Plants (0-7)
        "What process do plants use to make their own food?",
        "What is chlorophyll?",
        "Which root type grows one main root deep into the soil?",
        "What do stomata do?",
        "What is transpiration in plants?",
        "Which root type has many equal shallow roots?",
        "What is the main function of the stem?",
        "What is pollination?",
        // Topic 1: Invertebrates (8-12)
        "How many body parts do insects have?",
        "How many pairs of legs do insects have?",
        "How many pairs of legs do spiders and ticks have?",
        "What is the hard outer covering of insects called?",
        "How many pairs of legs do millipedes have per body segment?",
        // Topic 2: Vertebrates (13-17)
        "Which vertebrate breathes through gills as a young one and lungs as an adult?",
        "Which vertebrate has dry scaly skin and is cold-blooded?",
        "Which vertebrate has feathers and a beak?",
        "Which vertebrate feeds its young with milk?",
        "Which vertebrate breathes only through gills throughout its life?",
        // Topic 3: Circulatory (18-23)
        "How many chambers does the human heart have?",
        "Which blood group is the universal donor?",
        "Which blood vessel carries blood AWAY from the heart?",
        "What is the liquid part of blood called?",
        "What is the main function of red blood cells?",
        "What is the function of platelets?",
        // Topic 4: Human Body (24-29)
        "Where does digestion begin in the human body?",
        "Which organ absorbs most of the nutrients from digested food?",
        "Which organ produces bile to help digest fats?",
        "What is the main function of the lungs?",
        "Which muscle helps in breathing by moving up and down?",
        "How many bones does an adult human body have?",
        // Topic 5: Reproduction (30-33)
        "Where does fertilisation of the egg occur in females?",
        "Which organ produces egg cells (ova) in females?",
        "What is ovulation?",
        "Which of the following is a change that occurs in BOTH boys and girls during puberty?",
        // Topic 6: Matter/Soil (34-36)
        "What state of matter has a definite shape and definite volume?",
        "Which type of soil is best for crop growing as it retains water and nutrients well?",
        "What are the two main causes of soil erosion?",
        // Topic 7: Ecology/Water (37-39)
        "What does a food chain show?",
        "What is a habitat?",
        "What is the role of decomposers in an ecosystem?",
        // Topic 8: Mathematics (40-52) — KPSEA Grade 6 Term 1
        "What is the place value of digit 4 in 492,600?",
        "Round off 56,380 to the nearest thousand.",
        "Convert 40% as a fraction in its simplest form.",
        "A governor distributed 2,793 T-shirts equally among 147 regions. How many T-shirts did each region receive?",
        "Multiply: 2,141 x 16",
        "Mwamburi's string is 7cm long. Mwakideu's is 2cm shorter. What is the total length of their strings?",
        "A grandmother cooked 12 litres 500ml of porridge. She gave 9 litres 600ml to farm workers. How much is left for her grandchildren?",
        "The circumference of a circle is 22cm and its diameter is 7cm. How many times is the circumference longer than the diameter?",
        "Ole Macho had 25 cows and n sheep. The total number of animals was 80. Which equation can be used to find n?",
        "Matunda had p oranges and 15 mangoes. The total number of fruits was 32. What is the value of p?",
        "What is the square root of 361?",
        "What is the next missing number in the pattern? 10, 12, 16, 24, 40, ___",
        "A trader bought 260kg of sugar, sold 175kg, then bought 195kg more. How many kg does the trader have now?",
        // KNEC KPSEA Mathematics (53-88)
        "There were sixty five thousand and fifty people at an agricultural show. What is the number in symbols?",
        "A school had 1204 learners. 132 left and later 214 joined. How many learners are there now?",
        "A factory produced 7,435 pieces of soap and 1,263 were packed. How many pieces were NOT packed?",
        "A lorry carried 452 cartons with 24 books each. How many books altogether?",
        "Work out: 64 / 4 x 2 + 8",
        "Arrange the fractions 3/4, 2/5, 1/3 from smallest to largest.",
        "Arrange 0.75, 1.25, 0.8, 1.135 from largest to smallest.",
        "Neema ran on a 21m 45cm track three times. What total distance did she cover?",
        "A worker bought 175kg cement, 473kg sand and 282kg ballast. What is the total mass?",
        "Jeru visited his grandmother for 13 days. How many weeks and days is that?",
        "Asha started reading at 11:15am and read for 1 and a half hours. What time did she stop?",
        "Which list shows money paid by the public to a county government of Kenya?",
        "Masudi bought 2 packets of milk (sh55 each), a loaf of bread (sh50) and tea leaves (sh45). He gave sh500. What was his balance?",
        "Which shape has all angles equal, 4 lines of symmetry and all sides equal?",
        "COVID-19 cases: Nakuru=135, Garissa=17, Kwale=8, Kisii=10. Total=656. How many cases were in Nairobi?",
        "Nine hundred and fifty-four thousand eight hundred and sixty in symbols is?",
        "Using digits 5, 9, 0, 2, 1, 4, 8, what is the largest 7-digit number possible?",
        "A truck carried 572 cartons with 37 books each. How many books in total?",
        "A farmer planted 365 trees in each of 12 gardens. How many trees were planted altogether?",
        "Work out: 36 / 6 x 7 + 25 - 39",
        "Estimate: 1395 books shared equally among 23 schools (round each to nearest 10).",
        "Philip scored 27 out of 30 in a science test. What was the score as a percentage?",
        "Round off 69.687 to 2 decimal places.",
        "A bottle is 27cm 8mm tall. What is its height in millimetres?",
        "A businessman sold 50.123kg then 12.5kg of beans. How many kilograms altogether?",
        "1278 desks were distributed equally among 6 schools. How many desks per school?",
        "A lady was given a tender to supply 100 bags of beans, each weighing 90kg. How many tonnes?",
        "Nanjala's school is 5km 200m away. She walks to school and back Monday to Friday. Total distance?",
        "Kioko arranged boxes in 3 layers, each with 4 columns and 5 rows. How many boxes altogether?",
        "A rectangular tank measures 50cm x 80cm x 60cm. How many cubic cm of water does it hold?",
        "How many quarter litres can be obtained from 20 litres?",
        "Wesonga had P cows. He gave away 72 and remained with 48. Which equation represents this?",
        "Solve: 9x - 7 = 35 + 2x",
        "Green grocery: carrots 0.27, tomatoes 0.345, kales 0.135. What portion do these three cover?",
        "In a triangle, the angles a, b and c satisfy which relationship?",
        "Ann has x bananas, Tom has the same as Ann, Tesse has 3. Which expression shows the total?",
        // Science additions (89-98)
        "Which of the following is NOT a function of leaves?",
        "Which characteristics are for a spider and a snail respectively?",
        "Which one is a characteristic of BOTH a snake and a lizard?",
        "Which statement shows the function of the colon? (i) Absorbs water & minerals (ii) Absorbs digested food (iii) Digests proteins (iv) Food chewed by teeth",
        "A thin sheet of muscle that separates the chest and abdomen is called?",
        "A learner's head was damaged in an accident and became unconscious. Which skull function was impaired?",
        "Which type of teeth is: sharp pointed, longer than other teeth, has one root?",
        "In the female reproductive system, fertilisation takes place in the?",
        "Balloons started bursting during a hot afternoon. What conclusion can be made?",
        "Which component of air makes up 0.03%?"
    };

    private static final String[][] OPTIONS = {
        // Q0
        {"A) Transpiration","B) Photosynthesis","C) Respiration","D) Absorption"},
        // Q1
        {"A) Type of root","B) Blood cell","C) Green pigment in leaves","D) Type of stem"},
        // Q2
        {"A) Fibrous","B) Aerial","C) Prop","D) Taproot"},
        // Q3
        {"A) Store food","B) Exchange gases","C) Absorb water","D) Produce seeds"},
        // Q4
        {"A) Making food","B) Absorbing water","C) Losing water through leaves","D) Producing seeds"},
        // Q5
        {"A) Fibrous","B) Taproot","C) Aerial","D) Prop"},
        // Q6
        {"A) Store seeds","B) Transport water and food","C) Absorb minerals","D) Trap sunlight"},
        // Q7
        {"A) Transfer of pollen to fertilise flowers","B) Making food from sunlight","C) Losing water through leaves","D) Germination of seeds"},
        // Q8
        {"A) 2","B) 3","C) 4","D) 5"},
        // Q9
        {"A) 2 pairs","B) 3 pairs","C) 4 pairs","D) 5 pairs"},
        // Q10
        {"A) 3 pairs","B) 4 pairs","C) 2 pairs","D) 5 pairs"},
        // Q11
        {"A) Exoskeleton","B) Shell","C) Scales","D) Cartilage"},
        // Q12
        {"A) 1 pair","B) 2 pairs","C) 3 pairs","D) 4 pairs"},
        // Q13
        {"A) Fish","B) Reptile","C) Amphibian","D) Bird"},
        // Q14
        {"A) Amphibian","B) Reptile","C) Mammal","D) Bird"},
        // Q15
        {"A) Bird","B) Mammal","C) Amphibian","D) Fish"},
        // Q16
        {"A) Reptile","B) Bird","C) Fish","D) Mammal"},
        // Q17
        {"A) Fish","B) Amphibian","C) Reptile","D) Bird"},
        // Q18
        {"A) 2","B) 3","C) 4","D) 6"},
        // Q19
        {"A) A","B) B","C) AB","D) O"},
        // Q20
        {"A) Vein","B) Capillary","C) Artery","D) Plasma"},
        // Q21
        {"A) Haemoglobin","B) Platelets","C) Plasma","D) Serum"},
        // Q22
        {"A) Fight germs","B) Carry oxygen","C) Clot blood","D) Produce antibodies"},
        // Q23
        {"A) Blood clotting","B) Carry oxygen","C) Fight infection","D) Produce hormones"},
        // Q24
        {"A) Mouth","B) Stomach","C) Small intestine","D) Oesophagus"},
        // Q25
        {"A) Stomach","B) Small intestine","C) Large intestine","D) Liver"},
        // Q26
        {"A) Stomach","B) Pancreas","C) Liver","D) Kidney"},
        // Q27
        {"A) Pump blood","B) Exchange oxygen and carbon dioxide","C) Digest food","D) Filter waste"},
        // Q28
        {"A) Diaphragm","B) Bicep","C) Heart","D) Stomach"},
        // Q29
        {"A) 100","B) 150","C) 206","D) 300"},
        // Q30
        {"A) Uterus","B) Oviduct (Fallopian tube)","C) Ovary","D) Cervix"},
        // Q31
        {"A) Ovary","B) Uterus","C) Fallopian tube","D) Cervix"},
        // Q32
        {"A) Fertilisation of an egg","B) Monthly blood loss","C) Release of an egg from the ovary","D) Development of a foetus"},
        // Q33
        {"A) Menstruation","B) Voice breaking","C) Hips widening","D) Growth of body hair"},
        // Q34
        {"A) Solid","B) Liquid","C) Gas","D) Plasma"},
        // Q35
        {"A) Sandy soil","B) Clay soil","C) Loam soil","D) Gravel"},
        // Q36
        {"A) Sun and heat","B) Water and wind","C) Plants and animals","D) Rocks and minerals"},
        // Q37
        {"A) The feeding relationships between organisms","B) A chain used to hold food","C) How food is stored","D) The water cycle"},
        // Q38
        {"A) A type of food","B) The natural home of an organism","C) A water body","D) A type of plant"},
        // Q39
        {"A) Produce food by photosynthesis","B) Eat other animals","C) Break down dead matter into nutrients","D) Pollinate flowers"},
        // Q40
        {"A) Tens of thousands","B) Hundreds of thousands","C) Four hundred thousand","D) Millions"},
        // Q41
        {"A) 56,000","B) 56,300","C) 60,000","D) 56,400"},
        // Q42
        {"A) 40/100","B) 2/5","C) 4/10","D) 2/4"},
        // Q43
        {"A) 29","B) 19","C) 18","D) 27"},
        // Q44
        {"A) 33,156","B) 34,246","C) 34,256","D) 33,255"},
        // Q45
        {"A) 9 cm","B) 900 mm","C) 12,000 mm","D) 12 cm"},
        // Q46
        {"A) 22L 100ml","B) 2L 100ml","C) 2L 1100ml","D) 2L 900ml"},
        // Q47
        {"A) 15","B) 3 1/7","C) 7/22","D) 7 1/3"},
        // Q48
        {"A) 80 = 25 - n","B) 80 + 25 = n","C) 25 + n = 80","D) 25 x n = 80"},
        // Q49
        {"A) 17","B) 47","C) 15","D) 32"},
        // Q50
        {"A) 29","B) 19","C) 17","D) 27"},
        // Q51
        {"A) 32","B) 42","C) 64","D) 72"},
        // Q52
        {"A) 20","B) 85","C) 280","D) 455"},
        // Q53
        {"A) 60,550","B) 65,550","C) 60,505","D) 65,050"},
        // Q54
        {"A) 1,550","B) 1,286","C) 1,122","D) 858"},
        // Q55
        {"A) 6,172","B) 6,232","C) 6,272","D) 8,698"},
        // Q56
        {"A) 11,848","B) 10,848","C) 9,648","D) 2,712"},
        // Q57
        {"A) 160","B) 40","C) 16","D) 4"},
        // Q58
        {"A) 1/3, 2/5, 3/4","B) 3/4, 1/3, 2/5","C) 2/5, 1/3, 3/4","D) 1/3, 3/4, 2/5"},
        // Q59
        {"A) 0.75, 0.8, 1.135, 1.25","B) 0.75, 1.135, 1.25, 0.8","C) 1.135, 1.25, 0.8, 0.75","D) 1.25, 1.135, 0.8, 0.75"},
        // Q60
        {"A) 7m 15cm","B) 42m 90cm","C) 64m 35cm","D) 128m 70cm"},
        // Q61
        {"A) 930 kg","B) 755 kg","C) 648 kg","D) 457 kg"},
        // Q62
        {"A) 1 week 3 days","B) 1 week 6 days","C) 2 weeks 0 days","D) 2 weeks 3 days"},
        // Q63
        {"A) 11:45 am","B) 11:45 pm","C) 12:45 pm","D) 12:45 am"},
        // Q64
        {"A) Land fees, school fees, parking, cess","B) Market fees, cess, business permit, security fee","C) Market fees, business permit, cess, parking fee","D) Land fees, security fee, business permit, parking fee"},
        // Q65
        {"A) sh 350","B) sh 295","C) sh 255","D) sh 205"},
        // Q66
        {"A) Right angled triangle","B) Equilateral triangle","C) Square","D) Rectangle"},
        // Q67
        {"A) 170","B) 386","C) 486","D) 1,142"},
        // Q68
        {"A) 950,486","B) 954,860","C) 945,680","D) 944,806"},
        // Q69
        {"A) 5,902,148","B) 9,501,248","C) 9,854,210","D) 0,124,589"},
        // Q70
        {"A) 20,164","B) 16,421","C) 21,164","D) 31,461"},
        // Q71
        {"A) 4,380","B) 377","C) 4,280","D) 8,043"},
        // Q72
        {"A) 30","B) 26","C) 14","D) 28"},
        // Q73
        {"A) 75","B) 60","C) 70","D) 80"},
        // Q74
        {"A) 90%","B) 70%","C) 80%","D) 88%"},
        // Q75
        {"A) 70.00","B) 69.69","C) 69.70","D) 69.680"},
        // Q76
        {"A) 107 mm","B) 213 mm","C) 278 mm","D) 19 mm"},
        // Q77
        {"A) 62.623 kg","B) 62.63 kg","C) 63.236 kg","D) 63.32 kg"},
        // Q78
        {"A) 213","B) 312","C) 123","D) 218"},
        // Q79
        {"A) 9,000 tonnes","B) 9 tonnes","C) 90 tonnes","D) 900 tonnes"},
        // Q80
        {"A) 50km 200m","B) 52km 200m","C) 52km 400m","D) 52km"},
        // Q81
        {"A) 90","B) 30","C) 60","D) 12"},
        // Q82
        {"A) 190 cm3","B) 2,400 cm3","C) 24,000 cm3","D) 240,000 cm3"},
        // Q83
        {"A) 5","B) 24","C) 80","D) 16"},
        // Q84
        {"A) P + 72 = 48","B) P - 72 = 48","C) P + 48 = 72","D) 72 - 48 = P"},
        // Q85
        {"A) x = 42","B) x = 4","C) x > 6","D) x = 6"},
        // Q86
        {"A) 0.86","B) 0.75","C) 0.655","D) 1.00"},
        // Q87
        {"A) a + b + c = 180","B) a + b - c = 180","C) a - b + c = 180","D) a - b - c = 180"},
        // Q88
        {"A) x + x + 3","B) x + 3","C) 3x + 3","D) 3x"},
        // Q89 Science - Plants
        {"A) Absorb water and mineral salts","B) Exchange gases through stomata","C) Make food by photosynthesis","D) Remove waste through transpiration"},
        // Q90 Science - Invertebrates
        {"A) 2 body parts; moves by gliding","B) 2 body parts; 3 body parts","C) 8 legs; moves by gliding","D) 8 legs; 3 body parts"},
        // Q91 Science - Vertebrates
        {"A) Have scales","B) Give birth to live young","C) Are warm-blooded","D) Breathe through gills"},
        // Q92 Science - Human Body
        {"A) i - Absorbs water and mineral salts","B) ii - Absorbs digested food","C) iii - Digests proteins","D) iv - Food chewed by teeth"},
        // Q93 Science - Human Body
        {"A) Lungs","B) Diaphragm","C) Ileum","D) Stomach"},
        // Q94 Science - Human Body
        {"A) Supporting the neck","B) Balancing the body","C) Protecting the lungs","D) Enclosing and protecting the brain"},
        // Q95 Science - Human Body
        {"A) Premolar","B) Molar","C) Incisor","D) Canine"},
        // Q96 Science - Reproduction
        {"A) Ovary","B) Oviduct (Fallopian tube)","C) Uterus","D) Vagina"},
        // Q97 Science - Matter
        {"A) Gases expand when heated","B) Gases contract when heated","C) Balloons expand when heated","D) Gases contract when cooled"},
        // Q98 Science - Ecology/Air
        {"A) Oxygen","B) Nitrogen","C) Inert gases","D) Carbon dioxide"}
    };

    // Correct answer index (0=A, 1=B, 2=C, 3=D)
    private static final int[] ANSWERS = {
        1, 2, 3, 1, 2, 0, 1, 0,   // 0-7  Plants
        1, 1, 1, 0, 1,             // 8-12 Invertebrates
        2, 1, 0, 3, 0,             // 13-17 Vertebrates
        2, 3, 2, 2, 1, 0,          // 18-23 Circulatory
        0, 1, 2, 1, 0, 2,          // 24-29 Human Body
        1, 0, 2, 3,                // 30-33 Reproduction
        0, 2, 1,                   // 34-36 Matter/Soil
        0, 1, 2,                   // 37-39 Ecology/Water
        1, 0, 1, 1, 2, 3, 3, 1, 2, 0, 1, 3, 2,          // 40-52 Mathematics (KPSEA Term 1)
        3, 1, 0, 1, 1, 0, 3, 2, 0, 1, 2, 2, 1, 2, 2,   // 53-67 Mathematics (KNEC KPSEA)
        1, 2, 2, 0, 3, 2, 0, 1, 2, 0, 0, 1, 3, 2, 3,   // 68-82 Mathematics (KNEC KPSEA)
        2, 1, 3, 1, 0, 0,                               // 83-88 Mathematics (KNEC KPSEA)
        0, 2, 0, 0, 1, 3, 3, 1, 0, 3                   // 89-98 Science
    };

    private static final String[] EXPLANATIONS = {
        // Q0
        "Photosynthesis: plants use sunlight, water and CO2 to make food using chlorophyll.",
        // Q1
        "Chlorophyll is the green pigment in leaves. It absorbs sunlight for photosynthesis.",
        // Q2
        "Taproot: one main root growing deep with lateral side roots. Examples: legumes, acacia.",
        // Q3
        "Stomata are tiny holes on leaves. They let in CO2 and release O2 and water vapour.",
        // Q4
        "Transpiration: plants lose excess water through stomata as water vapour.",
        // Q5
        "Fibrous roots: many equal shallow roots growing in all directions. Examples: grass, maize, onions.",
        // Q6
        "Stem functions: transports water from roots to leaves and food from leaves to roots; supports the plant.",
        // Q7
        "Pollination: transfer of pollen from anther to stigma to fertilise flowers. Agents: wind and insects.",
        // Q8
        "Insects have 3 body parts: head, thorax and abdomen.",
        // Q9
        "Insects have 3 pairs of legs (6 legs total), attached to the thorax.",
        // Q10
        "Spiders and ticks have 4 pairs of legs (8 legs), 2 body parts, no wings, no antennae.",
        // Q11
        "Exoskeleton: the hard outer covering of insects. It protects them and gives shape.",
        // Q12
        "Millipedes: 2 pairs of legs per segment. Centipedes: 1 pair per segment.",
        // Q13
        "Amphibians (frogs, toads): breathe through gills when young (tadpoles) and lungs/skin as adults.",
        // Q14
        "Reptiles: cold-blooded, dry scaly skin. Examples: lizards, snakes, crocodiles, tortoises.",
        // Q15
        "Birds: feathers, beak, wings, lay eggs, warm-blooded. Examples: eagles, pigeons, ostriches.",
        // Q16
        "Mammals: warm-blooded, hair/fur, give birth to live young (mostly) and feed them milk. Examples: cows, whales, bats.",
        // Q17
        "Fish: breathe through gills, cold-blooded, scales, fins. Examples: tilapia, shark, mudfish.",
        // Q18
        "Heart has 4 chambers: 2 auricles (upper, receive blood) and 2 ventricles (lower, pump blood).",
        // Q19
        "Blood group O is the universal donor - it can give blood to all other blood groups.",
        // Q20
        "Arteries carry blood away from the heart under high pressure. Thick elastic walls, narrow lumen.",
        // Q21
        "Plasma is the pale yellow liquid part of blood. It transports food, hormones, CO2 and wastes.",
        // Q22
        "Red blood cells contain haemoglobin which carries oxygen from the lungs to the body cells.",
        // Q23
        "Platelets are tiny cells that help blood clot when you are injured, stopping bleeding from cuts.",
        // Q24
        "Digestion begins in the mouth where teeth chew food and saliva (enzyme amylase) breaks down starch.",
        // Q25
        "Small intestine: absorbs digested nutrients into the blood. It is long and coiled, with villi for absorption.",
        // Q26
        "The liver produces bile, stored in the gallbladder, which helps break down (emulsify) fats.",
        // Q27
        "Lungs: exchange gases - oxygen enters the blood and carbon dioxide leaves. This is called gaseous exchange.",
        // Q28
        "Diaphragm: a dome-shaped muscle below the lungs. It contracts (moves down) when inhaling and relaxes when exhaling.",
        // Q29
        "An adult human skeleton has 206 bones. Babies are born with about 270 bones that fuse as they grow.",
        // Q30
        "Fertilisation occurs in the oviduct (Fallopian tube). The fertilised egg then moves to the uterus to develop.",
        // Q31
        "Ovaries produce eggs (ova) by ovulation every 28 days. They also produce female sex hormones.",
        // Q32
        "Ovulation: release of a mature egg from the ovary. Occurs approximately every 28 days.",
        // Q33
        "During puberty, both boys and girls experience growth of body hair, acne (pimples), and rapid height increase.",
        // Q34
        "Solid: definite shape and volume, particles closely packed. Example: ice, stone, wood.",
        // Q35
        "Loam soil: a mixture of sand, silt and clay. Best for farming - retains moisture and nutrients while draining excess water.",
        // Q36
        "Soil erosion: removal of topsoil mainly by water (rain, rivers) and wind. Prevented by planting vegetation, terracing, mulching.",
        // Q37
        "Food chain: shows who eats whom (feeding relationships). Energy flows from producer to herbivore to carnivore.",
        // Q38
        "Habitat: the natural home of an organism where it gets food, water, shelter and space it needs to survive.",
        // Q39
        "Decomposers (bacteria, fungi) break down dead plants and animals into nutrients that enrich the soil.",
        // Q40
        "In 492,600 the digit 4 is in the hundred-thousands place (4 x 100,000 = 400,000).",
        // Q41
        "56,380: the hundreds digit is 3 (less than 5), so round down. Answer: 56,000.",
        // Q42
        "40% = 40/100. Divide both by HCF 20: 40/100 = 2/5. This is the simplest form.",
        // Q43
        "2,793 / 147 = 19. Check: 147 x 19 = 147 x 20 - 147 = 2,940 - 147 = 2,793.",
        // Q44
        "2,141 x 16 = 2,141 x 10 + 2,141 x 6 = 21,410 + 12,846 = 34,256.",
        // Q45
        "Mwakideu's string = 7 - 2 = 5 cm. Total = 7 + 5 = 12 cm.",
        // Q46
        "12L 500ml = 12,500ml. 12,500 - 9,600 = 2,900ml = 2 litres 900ml.",
        // Q47
        "Circumference / diameter = 22 / 7 = 3 1/7. This ratio is pi (pi = 22/7 approx 3.14).",
        // Q48
        "Total = cows + sheep: 25 + n = 80. Solve: n = 80 - 25 = 55 sheep.",
        // Q49
        "p + 15 = 32. Therefore p = 32 - 15 = 17 oranges.",
        // Q50
        "sqrt(361) = 19, because 19 x 19 = 361. Check: 18x18=324, 20x20=400.",
        // Q51
        "Differences: 2, 4, 8, 16 (each doubles). Next difference = 32. Answer: 40 + 32 = 72.",
        // Q52
        "260 - 175 = 85 kg remaining. 85 + 195 = 280 kg total.",
        // Q53
        "65,050: sixty five thousand (65,000) + fifty (50) = 65,050.",
        // Q54
        "1204 - 132 = 1072. Then 1072 + 214 = 1286 learners.",
        // Q55
        "7435 - 1263 = 6172 pieces not packed.",
        // Q56
        "452 x 24 = 452 x 20 + 452 x 4 = 9040 + 1808 = 10848 books.",
        // Q57
        "BODMAS: 64/4=16, 16x2=32, 32+8=40. Division and multiplication before addition.",
        // Q58
        "Convert to decimals: 1/3=0.333, 2/5=0.4, 3/4=0.75. Ascending: 1/3, 2/5, 3/4.",
        // Q59
        "Descending: 1.25 > 1.135 > 0.8 > 0.75. Compare digits position by position from left.",
        // Q60
        "21m 45cm x 3 = 63m 135cm = 63m + 1m 35cm = 64m 35cm.",
        // Q61
        "175 + 473 + 282 = 648 + 282 = 930 kg total.",
        // Q62
        "13 / 7 = 1 remainder 6. Therefore 1 week 6 days.",
        // Q63
        "11:15am + 1hr 30min (1.5 hours) = 12:45pm.",
        // Q64
        "County revenues: market fees, business permits, cess (levy on produce), parking fees. School fees and land registry go to national government.",
        // Q65
        "2x55 + 50 + 45 = 110 + 95 = 205. Change = 500 - 205 = sh 295.",
        // Q66
        "A square: all 4 angles = 90 degrees, all 4 sides equal, 4 lines of symmetry (2 diagonals + horizontal + vertical).",
        // Q67
        "Others: 135+17+8+10 = 170. Nairobi = 656 - 170 = 486 cases.",
        // Q68
        "954,860: nine hundred and fifty-four thousand (954,000) + eight hundred and sixty (860).",
        // Q69
        "Arrange digits in descending order: 9,8,5,4,2,1,0 gives 9854210.",
        // Q70
        "572 x 37 = 572 x 30 + 572 x 7 = 17160 + 4004 = 21164 books.",
        // Q71
        "365 x 12 = 365 x 10 + 365 x 2 = 3650 + 730 = 4380 trees.",
        // Q72
        "BODMAS: 36/6=6, 6x7=42, 42+25=67, 67-39=28.",
        // Q73
        "Round 1395 to 1400, round 23 to 20. Estimate: 1400/20 = 70 books per school.",
        // Q74
        "27/30 x 100 = 2700/30 = 90%.",
        // Q75
        "69.687: the 3rd decimal is 7 (>=5), round up the 2nd decimal: 69.69.",
        // Q76
        "27cm 8mm = 27 x 10 + 8 = 270 + 8 = 278mm.",
        // Q77
        "50.123 + 12.500 = 62.623 kg. Align decimal points when adding.",
        // Q78
        "1278 / 6 = 213. Check: 6 x 213 = 1278.",
        // Q79
        "100 x 90kg = 9000kg. 9000 / 1000 = 9 tonnes (1 tonne = 1000 kg).",
        // Q80
        "Round trip daily: 5.2km x 2 = 10.4km. For 5 days: 10.4 x 5 = 52km.",
        // Q81
        "4 columns x 5 rows = 20 boxes per layer. 20 x 3 layers = 60 boxes.",
        // Q82
        "Volume = 50 x 80 x 60 = 240,000 cm3.",
        // Q83
        "20 litres / 0.25 = 80. Equivalently: 20 x 4 = 80 quarter litres.",
        // Q84
        "P - 72 = 48: Wesonga had P cows, gave away 72, remained with 48. Solve: P = 48 + 72 = 120.",
        // Q85
        "9x - 2x = 35 + 7. 7x = 42. x = 6.",
        // Q86
        "Carrots(0.27) + Tomatoes(0.345) + Kales(0.135) = 0.75.",
        // Q87
        "Angles in any triangle always sum to 180 degrees: a + b + c = 180.",
        // Q88
        "Ann=x, Tom=x (same as Ann), Tesse=3. Total = x + x + 3.",
        // Q89
        "Leaves do NOT absorb water - that is the job of roots. Leaves exchange gases through stomata, make food via photosynthesis, and release water by transpiration.",
        // Q90
        "Spider: 2 body parts (cephalothorax + abdomen) and 8 legs. Snail: 1 body part, moves by gliding on a muscular foot.",
        // Q91
        "Snakes and lizards are both reptiles: cold-blooded, dry scaly skin. They lay eggs and breathe through lungs.",
        // Q92
        "The colon (large intestine) absorbs water and mineral salts from undigested food. The small intestine absorbs digested nutrients.",
        // Q93
        "The diaphragm is a dome-shaped sheet of muscle below the lungs. It separates the chest from the abdomen and moves to help breathing.",
        // Q94
        "The skull (cranium) encloses and protects the brain. A damaged skull impairs brain protection, which can cause unconsciousness.",
        // Q95
        "Canine teeth: sharp, pointed, longer than incisors, one root. Used for tearing food. Located at the corners of the mouth.",
        // Q96
        "Fertilisation (joining of sperm and egg) occurs in the oviduct (Fallopian tube). The fertilised egg then moves to the uterus to develop.",
        // Q97
        "Gases expand when heated. Hot afternoon sun warms the air inside balloons, causing them to expand and eventually burst.",
        // Q98
        "Composition of air: Nitrogen 78%, Oxygen 21%, Carbon dioxide 0.03%, Inert gases (Argon etc.) ~0.97%."
    };

    // Topic index for each question (0=Plants, 1=Invertebrates, 2=Vertebrates,
    //   3=Circulatory, 4=HumanBody, 5=Reproduction, 6=Matter, 7=Ecology, 8=Mathematics)
    private static final int[] QUESTION_TOPIC = {
        0,0,0,0,0,0,0,0,           // 0-7  Plants
        1,1,1,1,1,                  // 8-12 Invertebrates
        2,2,2,2,2,                  // 13-17 Vertebrates
        3,3,3,3,3,3,                // 18-23 Circulatory
        4,4,4,4,4,4,                // 24-29 Human Body
        5,5,5,5,                    // 30-33 Reproduction
        6,6,6,                      // 34-36 Matter/Soil
        7,7,7,                      // 37-39 Ecology/Water
        8,8,8,8,8,8,8,8,8,8,8,8,8,    // 40-52  Mathematics (KPSEA Term 1)
        8,8,8,8,8,8,8,8,8,8,8,8,8,8,8, // 53-67  Mathematics (KNEC KPSEA)
        8,8,8,8,8,8,8,8,8,8,8,8,8,8,8, // 68-82  Mathematics (KNEC KPSEA)
        8,8,8,8,8,8,                    // 83-88  Mathematics (KNEC KPSEA)
        0,1,2,4,4,4,4,5,6,7             // 89-98  Science
    };

    // ── Lifecycle ────────────────────────────────────────────────────────────
    public ElimuSMSMidlet() {
        display = Display.getDisplay(this);
    }

    public void startApp() {
        EvaluationLogger.load();
        EvaluationLogger.newSession();
        UserPreferences.loadSRS();
        SMSManager.setCloudUrl(getAppProperty("Elimu-CloudURL"));
        FederatedLearning.configure(
                getAppProperty("Elimu-FLEnabled"),
                getAppProperty("Elimu-CloudURL"));
        initializeAI();
        // Opportunistic global pull: silent on offline, applies new global if online.
        if (FederatedLearning.isEnabled()) {
            FederatedLearning.pullGlobalOpportunistic(aiModel);
        }
        showMainMenu();
    }

    private void initializeAI() {
        try {
            aiModel   = new CompressedTinyML();
            responses = new MicroResponses();
            aiModel.loadModel();
            System.out.println("=== ElimuSMS STEM AI Ready ===");
            aiModel.testModel();
        } catch (Exception e) {
            StringBuffer sb = new StringBuffer("AI init failed: ");
            sb.append(e.getMessage());
            showError(sb.toString());
        }
    }

    // ── Main Menu ────────────────────────────────────────────────────────────
    private void showMainMenu() {
        mainMenu = new List("ElimuSMS - STEM Grade 6", Choice.IMPLICIT);
        mainMenu.append("Ask Question",  null);
        mainMenu.append("Math Help",     null);
        mainMenu.append("Science Help",  null);
        mainMenu.append("Math Quiz",     null);
        mainMenu.append("Science Quiz",  null);
        mainMenu.append("My Progress",   null);
        mainMenu.addCommand(selectCmd);
        mainMenu.addCommand(exitCmd);
        mainMenu.setCommandListener(this);
        display.setCurrent(mainMenu);
    }

    // ── Command routing ──────────────────────────────────────────────────────
    public void commandAction(Command c, Displayable d) {
        if (c == exitCmd) {
            destroyApp(false);
            notifyDestroyed();
        } else if (c == selectCmd && d == mainMenu) {
            handleMenuSelection();
        } else if (c == sendCmd && d == questionForm) {
            handleQuestionSubmission();
        } else if (c == submitCmd) {
            handleQuizSubmit();
        } else if (c == okCmd) {
            display.setCurrent(mainMenu);
        } else if (c == moreCmd) {
            handleMore();
        } else if (c == selectCmd && d == quizTypeList) {
            startQuiz(quizTypeList.getSelectedIndex()); // 0=Math, 1=Science
        } else if (c == backCmd) {
            showMainMenu();
        }
    }

    private void handleMenuSelection() {
        switch (mainMenu.getSelectedIndex()) {
            case 0: showQuestionScreen(); break;
            case 1: showQuestionScreen(); break;  // Math
            case 2: showQuestionScreen(); break;  // Science
            case 3: startQuiz(0);         break;  // Math Quiz
            case 4: startQuiz(1);         break;  // Science Quiz
            case 5: showProgress();       break;
        }
    }

    // ── Question Screen ──────────────────────────────────────────────────────
    private void showQuestionScreen() {
        questionForm = new Form("Ask a STEM Question");
        questionField = new TextField("Question:", "", 80, TextField.ANY);
        questionForm.append(questionField);
        questionForm.addCommand(sendCmd);
        questionForm.addCommand(backCmd);
        questionForm.setCommandListener(this);
        display.setCurrent(questionForm);
    }

    private void handleQuestionSubmission() {
        String question = questionField.getString().trim();
        if (question.length() == 0) return;
        processQuery(question);
    }

    // ── AI query dispatch ────────────────────────────────────────────────────
    private void processQuery(String question) {
        try {
            question = injectContext(normalizeQuery(question));
            StringBuffer dbg = new StringBuffer("Question: ");
            dbg.append(question);
            System.out.println(dbg.toString());

            byte  intentId   = aiModel.predict(question);
            float confidence = aiModel.getLastConfidence();
            EvaluationLogger.recordPrediction(intentId, confidence);

            // Track state for the "More" tier walk: a fresh question resets
            // the cursor so subsequent "More" taps start from the top.
            lastQuestion  = question;
            lastIntent    = intentId;
            moreLevel     = 0;
            relatedCursor = 0;

            dbg = new StringBuffer("Intent: ");
            dbg.append(intentId);
            dbg.append("  Confidence: ");
            dbg.append(confidence);
            System.out.println(dbg.toString());

            boolean answeredLocally = true;
            if (!aiModel.shouldFallbackToCloud()) {
                // Update session context: track math/science topic; clear on greeting/farewell
                if (intentId == 0 || intentId == 1) {
                    lastSuccessfulIntent = intentId;
                } else if (intentId == 6 || intentId == 7) {
                    lastSuccessfulIntent = -1;
                }
                switch (intentId) {
                    case 0: handleMathQuestion(question);    break;
                    case 1: handleScienceQuestion(question); break;
                    case 2: handleScienceQuestion(question); break; // was English - route to science
                    case 3: showQuizTypePicker();            break;
                    case 4: answeredLocally = handleLowConfidence(question); break;
                    case 5: showProgress();                  break;
                    case 6: showResponse("Hello! Ask me about math or science.", "Hi!"); break;
                    case 7: showResponse("Goodbye! Keep learning STEM!", "Bye!"); break;
                    default: answeredLocally = handleLowConfidence(question); break;
                }
            } else if (intentId == 0 && isMathQuery(question)) {
                // Classifier agrees it's math; keyword confirms it — route at low confidence
                lastSuccessfulIntent = 0;
                handleMathQuestion(question);
            } else if ((intentId == 1 || intentId == 2) && isScienceQuery(question)) {
                // Classifier agrees it's science; keyword confirms it — route at low confidence
                lastSuccessfulIntent = 1;
                handleScienceQuestion(question);
            } else {
                answeredLocally = handleLowConfidence(question);
            }
            if (answeredLocally) UserPreferences.incrementLocalAnswers();
        } catch (Exception e) {
            showError("Oops! Something went wrong. Try a different question.");
        }
    }

    // ── Math ─────────────────────────────────────────────────────────────────
    private void handleMathQuestion(String question) {
        String lower = question.toLowerCase();

        if (contains(lower, "lcm") || contains(lower, "least common")) {
            showResponse("LCM (Least Common Multiple): the smallest number that is a multiple of two numbers.\nExample: LCM of 12 and 18\nMultiples of 12: 12, 24, 36...\nMultiples of 18: 18, 36...\nLCM = 36\nUse: adding fractions with different denominators.", "Math - LCM");
        } else if (contains(lower, "hcf") || contains(lower, "highest common") || contains(lower, "gcd")) {
            showResponse("HCF (Highest Common Factor): the largest number that divides two numbers exactly.\nExample: HCF of 12 and 18\nFactors of 12: 1,2,3,4,6,12\nFactors of 18: 1,2,3,6,9,18\nHCF = 6\nUse: simplifying fractions.", "Math - HCF");
        } else if (contains(lower, "improper") || contains(lower, "mixed number")) {
            showResponse("Improper fraction: numerator is bigger than denominator. Example: 7/3\nMixed number: whole number + fraction. Example: 2 1/3\nConvert: 7/3 = 2 remainder 1 = 2 1/3\nConvert back: 2 1/3 = (2x3+1)/3 = 7/3", "Math - Improper/Mixed");
        } else if (contains(lower, "fraction")) {
            showResponse("Fractions show part of a whole.\nNumerator (top) / Denominator (bottom)\nExample: Amina has 8 mangoes. She eats 3. She ate 3/8 of the mangoes.\nEquivalent: 1/2 = 2/4 = 4/8\nSimplify: 6/8 = 3/4 (divide by HCF=2)", "Math - Fractions");
        } else if (contains(lower, "percent")) {
            showResponse("Percentage means out of 100.\nExample: Kamau scored 18/20 in a test.\n18/20 x 100 = 90%\nOr: A shopkeeper in Nairobi bought maize for Ksh 200 and sold for Ksh 250.\nProfit = 50. Profit % = 50/200 x 100 = 25%", "Math - Percentages");
        } else if (contains(lower, "ratio")) {
            showResponse("Ratio compares two quantities.\nExample: In a class of 30, there are 18 girls and 12 boys.\nRatio of girls to boys = 18:12 = 3:2\nShare Ksh 500 in ratio 2:3:\nTotal parts = 5. One part = 500/5 = 100.\n2 parts = Ksh 200, 3 parts = Ksh 300.", "Math - Ratio");
        } else if (contains(lower, "decimal")) {
            showResponse("Decimals use a dot to show parts less than one.\n0.5 = 5/10 = 1/2\n0.25 = 25/100 = 1/4\n0.75 = 75/100 = 3/4\nAdding: 1.5 + 2.3 = 3.8\nMultiplying: 0.4 x 3 = 1.2", "Math - Decimals");
        } else if (contains(lower, "mean") || contains(lower, "average")) {
            showResponse("Mean (average): add all values, divide by how many.\nExample: Marks of 5 pupils: 60, 72, 58, 80, 65\nSum = 60+72+58+80+65 = 335\nMean = 335/5 = 67\nThe average score is 67 marks.", "Math - Mean");
        } else if (contains(lower, "mode")) {
            showResponse("Mode: the value that appears most often in a set of data.\nExample: Ages: 12, 13, 12, 14, 13, 12, 15\nMode = 12 (appears 3 times)\nA set can have more than one mode (bimodal).", "Math - Mode");
        } else if (contains(lower, "range")) {
            showResponse("Range: difference between the highest and lowest value.\nExample: Rainfall (mm): 40, 25, 60, 15, 50\nHighest = 60, Lowest = 15\nRange = 60 - 15 = 45 mm", "Math - Range");
        } else if (contains(lower, "area") && contains(lower, "triangle")) {
            showResponse("Area of a triangle = 1/2 x base x height\nExample: base = 8cm, height = 5cm\nArea = 1/2 x 8 x 5 = 20 sq cm", "Math - Area of Triangle");
        } else if (contains(lower, "area") && contains(lower, "circle")) {
            showResponse("Area of a circle = pi x r x r (approximately 3.14 x r x r)\nExample: radius = 7cm\nArea = 3.14 x 7 x 7 = 153.86 sq cm\nDiameter = 2 x radius", "Math - Area of Circle");
        } else if (contains(lower, "area") && (contains(lower, "rectangle") || contains(lower, "square"))) {
            showResponse("Area of rectangle = length x width\nExample: l = 10cm, w = 4cm\nArea = 10 x 4 = 40 sq cm\nArea of square = side x side\nExample: side = 6cm\nArea = 6 x 6 = 36 sq cm", "Math - Area");
        } else if (contains(lower, "area")) {
            showResponse("Area formulas:\nRectangle = length x width\nSquare = side x side\nTriangle = 1/2 x base x height\nCircle = 3.14 x radius x radius\nParallelogram = base x height", "Math - Area");
        } else if (contains(lower, "perimeter")) {
            showResponse("Perimeter: total length around a shape.\nRectangle = 2 x (length + width)\nExample: l=8cm, w=3cm, P = 2x(8+3) = 22cm\nSquare = 4 x side\nCircle (circumference) = 2 x 3.14 x radius", "Math - Perimeter");
        } else if (contains(lower, "volume")) {
            showResponse("Volume formulas:\nCuboid = length x width x height\nExample: 5cm x 4cm x 3cm = 60 cubic cm\nCube = side x side x side\nCylinder = 3.14 x radius x radius x height", "Math - Volume");
        } else if (question.indexOf('+') >= 0 || question.indexOf('-') >= 0
                || question.indexOf('*') >= 0 || question.indexOf('/') >= 0) {
            showResponse(evaluateMathExpression(question), "Math");
        } else {
            showResponse("Math topics I can help with:\nfractions, LCM, HCF, percentages, ratio,\ndecimals, area, perimeter, volume,\nmean, mode, range.\nOr type a calculation: 15*4", "Math Help");
        }
    }

    // ── Science / Living Things (CBC Grade 6) ────────────────────────────────
    private void handleScienceQuestion(String question) {
        // Backstop: arithmetic operators or digit/digit fraction → route to math
        if (question.indexOf('+') >= 0 || question.indexOf('*') >= 0) {
            handleMathQuestion(question); return;
        }
        String noSp = removeSpaces(question);
        for (int i = 1; i < noSp.length() - 1; i++) {
            if (noSp.charAt(i) == '/' && Character.isDigit(noSp.charAt(i - 1))
                    && Character.isDigit(noSp.charAt(i + 1))) {
                handleMathQuestion(question); return;
            }
        }
        String lower = question.toLowerCase();

        // --- Plants ---
        if (contains(lower, "photosynthes")) {
            showResponse("Photosynthesis: plants make food using sunlight, water and CO2.\nNeeds: chlorophyll, water, carbon dioxide, sunlight.", "Science");
        } else if (contains(lower, "chlorophyll")) {
            showResponse("Chlorophyll is the green colouring matter in leaves. It absorbs sunlight for photosynthesis.", "Science");
        } else if (contains(lower, "stomata") || contains(lower, "stoma")) {
            showResponse("Stomata are tiny holes on leaves. They let in CO2 and release O2 and water vapour (transpiration).", "Science");
        } else if (contains(lower, "transpir")) {
            showResponse("Transpiration: plants lose excess water through stomata.\nHigh when: hot, sunny, dry, windy.\nLow when: cold, wet, calm, rainy.", "Science");
        } else if (contains(lower, "pollinat")) {
            showResponse("Pollination transfers pollen to fertilise flowers. Wind and insects (bees) are the main agents. It enables plants to produce seeds.", "Science");
        } else if (contains(lower, "type") && (contains(lower, "plant") || contains(lower, "root"))) {
            if (contains(lower, "root")) {
                showResponse("2 types of roots:\n1. Taproot - one main root growing deep (legumes, acacia, fruit trees)\n2. Fibrous roots - many equal shallow roots (grass, maize, onions, sugarcane)\nOthers: aerial roots (breathing), prop roots (support)", "Science - Root Types");
            } else {
                showResponse("4 types of plants:\n1. Trees - big, single trunk (mango, coconut, avocado)\n2. Shrubs - many woody stems (hibiscus, rose, cotton)\n3. Herbs - small, soft green stems (mint, coriander)\n4. Grass - short, narrow leaves", "Science - Plant Types");
            }
        } else if (contains(lower, "part") && contains(lower, "plant")) {
            showResponse("External parts of a plant:\n- Roots: absorb water, anchor, store food\n- Stem: transports water, supports\n- Leaves: photosynthesis, breathing\n- Flowers: reproductive organs\n- Fruits: protect seeds, store food\n- Seeds: germinate into new plants", "Science - Plant Parts");
        } else if (contains(lower, "function") && contains(lower, "root")) {
            showResponse("Functions of roots:\n1. Absorb water and mineral salts from soil\n2. Anchor/support the plant in soil\n3. Store food (carrots, cassava, arrowroots)", "Science - Roots");
        } else if (contains(lower, "function") && contains(lower, "stem")) {
            showResponse("Functions of the stem:\n1. Transports water from roots to leaves\n2. Carries food from leaves to roots\n3. Supports upper parts of the plant\n4. Stem tubers store food: cactus, sugarcane, Irish potato", "Science - Stem");
        } else if (contains(lower, "function") && (contains(lower, "leaf") || contains(lower, "leave"))) {
            showResponse("Functions of leaves:\n1. Make food via photosynthesis\n2. Breathing - exchange gases through stomata\n3. Store food (kales, cabbages, spinach)\n4. Remove excess water through transpiration", "Science - Leaves");
        } else if (contains(lower, "taproot") || contains(lower, "fibrous")) {
            showResponse("Taproot: one main root growing deep with lateral side roots.\nExamples: legumes, acacia, fruit trees.\n\nFibrous roots: many equal shallow roots, grow in all directions.\nExamples: cereals, grass, sisal, onions, sugarcane, coconuts.", "Science - Root Types");

        // --- Digestive System ---
        } else if (contains(lower, "digest") || contains(lower, "digestion")) {
            showResponse("Digestive system: mouth -> oesophagus -> stomach -> small intestine -> large intestine -> rectum.\nMouth: chews, saliva breaks starch.\nStomach: churns, acid, pepsin breaks protein.\nSmall intestine: absorbs nutrients.\nLarge intestine: absorbs water.\nRectum: stores faeces.", "Science - Digestive System");
        } else if (contains(lower, "stomach")) {
            showResponse("Stomach: churns food, produces hydrochloric acid (kills bacteria), and the enzyme pepsin breaks down protein. Food becomes chyme before moving to the small intestine.", "Science - Stomach");
        } else if (contains(lower, "small intestine") || (contains(lower, "intestin") && contains(lower, "small"))) {
            showResponse("Small intestine: absorbs digested nutrients into the blood. About 6m long, coiled. Has tiny finger-like projections called villi that increase the surface area for absorption.", "Science - Small Intestine");
        } else if (contains(lower, "large intestine") || (contains(lower, "intestin") && contains(lower, "large"))) {
            showResponse("Large intestine: absorbs water from the remaining indigestible food. The remaining solid waste (faeces) is stored in the rectum until it is expelled.", "Science - Large Intestine");
        } else if (contains(lower, "liver") || contains(lower, "bile")) {
            showResponse("Liver: produces bile (stored in gallbladder), detoxifies blood, stores glycogen (energy), produces blood proteins. Bile emulsifies (breaks down) fats in the small intestine.", "Science - Liver");
        } else if (contains(lower, "saliva") || contains(lower, "amylase")) {
            showResponse("Saliva: produced by salivary glands in the mouth. Contains enzyme amylase that breaks down starch into simpler sugars. Saliva also moistens food to make swallowing easier.", "Science - Saliva");
        } else if (contains(lower, "oesophag") || contains(lower, "gullet")) {
            showResponse("Oesophagus (gullet): muscular tube from the mouth/throat to the stomach. Food moves down by peristalsis (wave-like muscle contractions). About 25cm long.", "Science - Oesophagus");

        // --- Respiratory System ---
        } else if (contains(lower, "respirat") || contains(lower, "respirator")) {
            showResponse("Respiratory system: breathing in oxygen and breathing out CO2.\nInhale: diaphragm contracts, chest expands, air in.\nExhale: diaphragm relaxes, chest contracts, air out.\nGaseous exchange happens in the alveoli of the lungs.", "Science - Respiratory System");
        } else if (contains(lower, "lung")) {
            showResponse("Lungs: where gaseous exchange happens. Oxygen from air enters the blood; CO2 from blood is released. Tiny air sacs called alveoli have thin walls and many blood vessels, increasing surface area.", "Science - Lungs");
        } else if (contains(lower, "diaphragm")) {
            showResponse("Diaphragm: dome-shaped muscle below the lungs.\nInhaling: diaphragm contracts and flattens -> chest expands -> air rushes in.\nExhaling: diaphragm relaxes and domes up -> chest contracts -> air pushed out.", "Science - Diaphragm");
        } else if (contains(lower, "trachea") || contains(lower, "windpipe")) {
            showResponse("Trachea (windpipe): tube from the throat down to the lungs. Strengthened by rings of cartilage to keep it open. It branches into two bronchi, one for each lung.", "Science - Trachea");
        } else if (contains(lower, "bronch")) {
            showResponse("Bronchi: two large tubes from the trachea into each lung. They branch into smaller bronchioles, which end in tiny air sacs called alveoli where gaseous exchange occurs.", "Science - Bronchi");

        // --- Skeletal & Muscular System ---
        } else if (contains(lower, "skeleton") || (contains(lower, "bone") && contains(lower, "all"))) {
            showResponse("Human skeleton: 206 bones.\nFunctions:\n1. Support and give shape to the body\n2. Protect organs (skull-brain, ribs-heart/lungs)\n3. Allow movement (with muscles)\n4. Produce blood cells (in red bone marrow)\n5. Store calcium and minerals", "Science - Skeleton");
        } else if (contains(lower, "bone") || contains(lower, "femur") || contains(lower, "skull") || contains(lower, "rib")) {
            showResponse("Bones: made mainly of calcium. Types:\n- Femur: largest bone (thigh)\n- Skull: protects the brain\n- Ribs (12 pairs): protect heart and lungs\n- Spine (vertebral column): supports the body\nJoints connect bones; cartilage reduces friction.", "Science - Bones");
        } else if (contains(lower, "joint")) {
            showResponse("Joint: where two bones meet.\nTypes:\n- Hinge joint: one direction (knee, elbow, finger)\n- Ball-and-socket: all directions (hip, shoulder)\n- Fixed (immovable): skull bones\n- Pivot: rotating (neck)\nCartilage cushions joints.", "Science - Joints");
        } else if (contains(lower, "cartilage")) {
            showResponse("Cartilage: smooth, flexible connective tissue found at joints. It reduces friction between bones, absorbs shock, and protects bone surfaces from wearing down.", "Science - Cartilage");
        } else if (contains(lower, "muscle")) {
            showResponse("Muscles: attached to bones by tendons. They contract (shorten) and relax to cause movement.\nMuscles work in antagonistic pairs:\n- Bicep contracts -> arm bends\n- Tricep contracts -> arm straightens\nDiaphragm and heart are also muscles.", "Science - Muscles");

        // --- Vertebrate Groups ---
        } else if (contains(lower, "amphibian") || contains(lower, "frog") || contains(lower, "toad")) {
            showResponse("Amphibians: cold-blooded vertebrates.\n- Young (tadpoles): breathe through gills, live in water\n- Adults: breathe through lungs and moist skin\n- Lay eggs in water\n- Smooth moist skin\nExamples: frogs, toads, salamanders", "Science - Amphibians");
        } else if (contains(lower, "reptile") || contains(lower, "lizard") || contains(lower, "snake") || contains(lower, "crocodile") || contains(lower, "tortoise")) {
            showResponse("Reptiles: cold-blooded vertebrates.\n- Dry scaly skin\n- Breathe through lungs\n- Lay eggs on land (leathery shells)\n- Most cannot regulate body temperature\nExamples: lizards, snakes, crocodiles, tortoises, chameleons", "Science - Reptiles");
        } else if (contains(lower, "bird") || contains(lower, "feather") || contains(lower, "beak")) {
            showResponse("Birds: warm-blooded vertebrates.\n- Have feathers and wings\n- Beak (no teeth)\n- Lay eggs with hard shells\n- Breathe through lungs\n- Hollow bones (most can fly)\nExamples: eagles, pigeons, ostriches, hummingbirds", "Science - Birds");
        } else if (contains(lower, "mammal")) {
            showResponse("Mammals: warm-blooded vertebrates.\n- Hair or fur on body\n- Give birth to live young (mostly)\n- Feed young with milk from mammary glands\n- Breathe through lungs\nExamples: humans, cows, whales, bats, dogs", "Science - Mammals");
        } else if (contains(lower, "fish") || contains(lower, "gill") || contains(lower, "tilapia")) {
            showResponse("Fish: cold-blooded vertebrates.\n- Breathe through gills (throughout life)\n- Covered with scales\n- Use fins for movement\n- Lay eggs in water\n- Cold-blooded\nExamples: tilapia, mudfish, shark, catfish, eel", "Science - Fish");

        // --- Plant Reproduction & Germination ---
        } else if (contains(lower, "germinat")) {
            showResponse("Germination: a seed sprouts into a seedling.\nConditions needed: water, warmth, oxygen (NOT light initially).\nStages: seed absorbs water -> radicle (root) emerges -> plumule (shoot) emerges -> seedling grows leaves.\nNote: light is not needed for germination itself.", "Science - Germination");
        } else if (contains(lower, "seed dispersal") || contains(lower, "dispersal")) {
            showResponse("Seed dispersal: how seeds spread away from the parent plant.\nMethods:\n- Wind: dandelion, acacia (light, fluffy)\n- Water: coconut, mangrove\n- Animals: hooks on fur/clothing, berries eaten\n- Explosion (self): beans, peas, cotton\nPrevents overcrowding.", "Science - Seed Dispersal");
        } else if (contains(lower, "vegetative") || contains(lower, "cutting") || contains(lower, "grafting")) {
            showResponse("Vegetative propagation: growing new plants from plant parts (not seeds).\nMethods:\n- Cuttings: sugarcane, cassava, kales\n- Grafting/budding: fruit trees (mango, avocado)\n- Runners: strawberry, couch grass\n- Tubers: Irish potato, arrowroot\n- Bulbs: onions, garlic", "Science - Vegetative Propagation");

        // --- Simple Machines ---
        } else if (contains(lower, "simple machine") || (contains(lower, "machine") && (contains(lower, "type") || contains(lower, "kind")))) {
            showResponse("6 simple machines (all make work easier):\n1. Lever - rigid bar on fulcrum\n2. Pulley - grooved wheel with rope\n3. Inclined plane - sloping surface (ramp)\n4. Wedge - two inclined planes back-to-back\n5. Screw - inclined plane wound around a cylinder\n6. Wheel and axle - wheel attached to a rod", "Science - Simple Machines");
        } else if (contains(lower, "lever")) {
            showResponse("Lever: a rigid bar that pivots on a fulcrum.\n3 classes based on position of fulcrum, load, effort:\n- Class 1: fulcrum between load and effort (seesaw, crowbar, scissors)\n- Class 2: load between fulcrum and effort (wheelbarrow, nutcracker)\n- Class 3: effort between fulcrum and load (tweezers, fishing rod)", "Science - Lever");
        } else if (contains(lower, "pulley")) {
            showResponse("Pulley: grooved wheel with a rope.\nChanges direction of force and can reduce effort needed.\nFixed pulley: changes direction only (flagpole).\nMovable pulley: reduces effort (cranes, water wells).\nBlock and tackle: multiple pulleys, used in construction.", "Science - Pulley");
        } else if (contains(lower, "inclined plane") || (contains(lower, "inclined") && contains(lower, "plane"))) {
            showResponse("Inclined plane (ramp): a sloping flat surface.\nReduces the effort needed to raise an object.\nThe longer the ramp, the less effort needed.\nExamples: ramps for wheelchairs, roads up hills, stairs, loading ramps for trucks.", "Science - Inclined Plane");
        } else if (contains(lower, "wedge")) {
            showResponse("Wedge: two inclined planes placed back to back.\nUsed to split, cut, or hold objects in place.\nExamples: axe, knife, chisel, nail, door stopper, plough.\nThe thinner the wedge, the easier it cuts.", "Science - Wedge");
        } else if (contains(lower, "screw")) {
            showResponse("Screw: an inclined plane wrapped around a cylinder.\nConverts rotational (turning) force into linear (straight) force.\nExamples: wood screws, bolts, jar lids, drill bits, water pumps.\nEach turn of the screw moves it a small distance.", "Science - Screw");
        } else if (contains(lower, "fulcrum") || contains(lower, "effort") || contains(lower, "load")) {
            showResponse("Parts of a lever:\n- Load: the object being moved or lifted\n- Effort: the force applied to move the load\n- Fulcrum: the pivot point (where lever rests)\nMechanical advantage = Load / Effort\nIf MA > 1, the machine makes work easier.", "Science - Lever Parts");

        // --- States of Matter ---
        } else if (contains(lower, "state") && contains(lower, "matter")) {
            showResponse("3 states of matter:\n1. Solid: definite shape AND definite volume (ice, rock, wood)\n2. Liquid: definite volume, takes shape of container (water, milk, oil)\n3. Gas: no definite shape or volume, fills container (air, steam, CO2)\nParticles move fastest in gas, slowest in solid.", "Science - States of Matter");
        } else if (contains(lower, "solid")) {
            showResponse("Solid: particles are closely packed in fixed positions.\n- Definite shape and volume\n- Cannot be compressed easily\n- Particles vibrate but do not move around\nExamples: ice, rock, wood, iron, salt", "Science - Solid");
        } else if (contains(lower, "liquid")) {
            showResponse("Liquid: particles are close together but free to move.\n- Definite volume, takes shape of container\n- Cannot be compressed\n- Flows and pours\nExamples: water, milk, oil, blood, mercury", "Science - Liquid");
        } else if (contains(lower, "gas") && !contains(lower, "gaseous exchange") && !contains(lower, "exchange gases")) {
            showResponse("Gas: particles are far apart and move freely.\n- No definite shape or volume\n- Fills its container completely\n- Can be compressed\nExamples: air, steam, oxygen, CO2, nitrogen", "Science - Gas");
        } else if (contains(lower, "melting") || contains(lower, "melt")) {
            showResponse("Melting: change of state from solid to liquid when heated (gains energy).\nMelting point of ice = 0 degrees C\nExamples: ice melts to water, butter melts when heated.\nThe reverse (liquid to solid) is called freezing/solidification.", "Science - Melting");
        } else if (contains(lower, "boiling") || contains(lower, "evaporat")) {
            showResponse("Boiling: liquid changes to gas at boiling point (100 degrees C for water).\nEvaporation: liquid changes to gas below boiling point (surface only).\nExamples: wet clothes dry (evaporation), water boils when heated (boiling).\nBoth need heat energy.", "Science - Boiling/Evaporation");
        } else if (contains(lower, "condensat") || contains(lower, "cooling")) {
            showResponse("Condensation: gas changes to liquid when cooled (loses energy).\nExamples:\n- Water droplets on a cold glass\n- Morning dew on grass\n- Clouds forming (water vapour cools)\n- Steam hitting a cold surface\nReverse of evaporation.", "Science - Condensation");

        // --- Soil ---
        } else if (contains(lower, "soil") && (contains(lower, "type") || contains(lower, "kind"))) {
            showResponse("3 types of soil:\n1. Sandy: large particles, drains fast, poor nutrients, pale, light. (Coastal/desert)\n2. Clay: tiny particles, holds water, heavy, sticky when wet. Good for pottery.\n3. Loam: mixture of sand, silt, clay. BEST for farming - retains moisture and nutrients.", "Science - Soil Types");
        } else if (contains(lower, "sandy")) {
            showResponse("Sandy soil: large particles, drains water quickly, poor in nutrients, light and pale.\nFound: coastal areas, deserts.\nNot best for farming (poor nutrients, dries quickly).\nImproved by adding organic matter (compost/manure).", "Science - Sandy Soil");
        } else if (contains(lower, "clay") && contains(lower, "soil")) {
            showResponse("Clay soil: very small particles, holds a lot of water, heavy and sticky when wet.\nCan waterlog plants if drainage is poor.\nGood for: making pottery and bricks.\nImproved by adding sand and organic matter.", "Science - Clay Soil");
        } else if (contains(lower, "loam")) {
            showResponse("Loam soil: a mixture of sand, silt and clay.\nBEST soil for farming:\n- Retains moisture and nutrients\n- Good drainage (excess water drains away)\n- Rich in humus (decayed matter)\n- Most fertile soil type\nMost farmland in Kenya has loam soil.", "Science - Loam Soil");
        } else if (contains(lower, "soil") && (contains(lower, "erosion") || contains(lower, "erode"))) {
            showResponse("Soil erosion: removal of topsoil by water or wind.\nCauses: deforestation, overgrazing, poor farming practices.\nPrevention:\n1. Plant trees and grass (roots hold soil)\n2. Terracing (steps on hillside)\n3. Mulching (cover soil)\n4. Contour ploughing\n5. Windbreaks", "Science - Soil Erosion");
        } else if (contains(lower, "erosion")) {
            showResponse("Soil erosion: removal of topsoil by water or wind.\nCauses: deforestation, overgrazing, poor farming practices.\nPrevention:\n1. Plant trees and grass (roots hold soil)\n2. Terracing (steps on hillside)\n3. Mulching (cover soil)\n4. Contour ploughing\n5. Windbreaks", "Science - Soil Erosion");
        } else if (contains(lower, "soil") && contains(lower, "conserv")) {
            showResponse("Soil conservation: protecting topsoil from erosion.\nMethods:\n1. Agroforestry (trees and crops together)\n2. Mulching (covering soil with plant material)\n3. Terracing (on slopes)\n4. Windbreaks (rows of trees)\n5. Crop rotation\n6. Contour ploughing", "Science - Soil Conservation");

        // --- Microorganisms & Disease ---
        } else if (contains(lower, "microorganism") || contains(lower, "micro-organism") || contains(lower, "germ")) {
            showResponse("Microorganisms: tiny living things seen only with microscope.\nTypes: bacteria, viruses, fungi, protozoa.\nHarmful: cause diseases (TB, cholera, flu, malaria).\nUseful: make bread, yoghurt, cheese; fix nitrogen; decompose waste.", "Science - Microorganisms");
        } else if (contains(lower, "bacteria")) {
            showResponse("Bacteria: single-celled microorganisms.\nHarmful: TB, cholera, typhoid, food poisoning.\nUseful: make yoghurt and cheese; fix nitrogen in soil; decompose dead matter.\nKilled by: antibiotics, boiling, proper food storage.", "Science - Bacteria");
        } else if (contains(lower, "virus")) {
            showResponse("Viruses: smallest microorganisms, not cells. They can only reproduce inside living cells.\nCauses: flu, HIV/AIDS, polio, COVID-19, measles, common cold.\nPrevented by: vaccines, hygiene, avoiding contact.\nCannot be treated by antibiotics.", "Science - Viruses");
        } else if (contains(lower, "fungus") || contains(lower, "fungi")) {
            showResponse("Fungi: moulds and yeasts.\nHarmful: cause ringworm, athlete's foot, food spoilage, thrush.\nUseful: yeast makes bread rise; mushrooms are edible; fungi produce antibiotics (penicillin).\nGrow in warm, moist, dark conditions.", "Science - Fungi");
        } else if (contains(lower, "disease") || contains(lower, "infection")) {
            showResponse("Diseases: caused by pathogens (bacteria, viruses, fungi, protozoa).\nPrevention:\n1. Wash hands regularly\n2. Drink clean/boiled water\n3. Get vaccinated\n4. Use mosquito nets (malaria)\n5. Proper food storage\n6. Cover mouth when coughing/sneezing", "Science - Disease Prevention");

        // --- Weather & Climate ---
        } else if (contains(lower, "weather") && !contains(lower, "whether")) {
            showResponse("Weather: daily atmospheric conditions (sunny, rainy, cloudy, windy, cold).\nClimate: average weather over a long period.\nKenya has a tropical climate with two rainy seasons:\n- Long rains: March-May\n- Short rains: October-December\nWeather instruments: thermometer, rain gauge, wind vane.", "Science - Weather");
        } else if (contains(lower, "cloud")) {
            showResponse("Clouds: formed when water vapour rises, cools and condenses around dust particles.\nTypes:\n- Cumulus: fluffy, fair weather\n- Stratus: flat layers, overcast/drizzle\n- Cirrus: wispy, high up, ice crystals\n- Cumulonimbus: thunderstorm clouds", "Science - Clouds");
        } else if (contains(lower, "rain") || contains(lower, "rainfall") || contains(lower, "precipitat")) {
            showResponse("Rainfall/Precipitation: water droplets fall from clouds when they become too heavy.\nKenya: two rainy seasons - long rains (March-May), short rains (October-December).\nMeasured with a rain gauge in mm.\nImportant for: farming, water supply, rivers and dams.", "Science - Rainfall");

        // --- Ecosystems ---
        } else if (contains(lower, "decomposer")) {
            showResponse("Decomposers: organisms that break down dead matter.\nExamples: bacteria, fungi, millipedes, earthworms.\nThey recycle nutrients back into the soil.\nWithout decomposers, dead plants and animals would pile up.\nEssential for ecosystem health and soil fertility.", "Science - Decomposers");
        } else if (contains(lower, "adaptation")) {
            showResponse("Adaptation: special features that help organisms survive in their habitat.\nExamples:\n- Cactus: stores water in thick stem (desert)\n- Fish: gills, streamlined body (water)\n- Camel: hump stores fat, broad feet (desert)\n- Birds: wings and hollow bones (air)\n- Polar bear: thick fur (cold)", "Science - Adaptation");
        } else if (contains(lower, "ecosystem")) {
            showResponse("Ecosystem: all living things (biotic) and their environment (abiotic) in an area, interacting together.\nComponents: producers, consumers, decomposers.\nExamples: forest, lake, grassland, desert, wetland.\nFood chains and food webs show feeding relationships.", "Science - Ecosystem");

        // --- Invertebrates ---
        } else if (contains(lower, "insect") && (contains(lower, "character") || contains(lower, "feature") || contains(lower, "part") || contains(lower, "body"))) {
            showResponse("Insect characteristics:\n- 3 body parts: head, thorax, abdomen\n- 3 pairs of legs (6 legs)\n- Usually 2 pairs of wings (beetles/ants have no wings)\n- 1 pair of antennae (feelers)\n- Hard exoskeleton", "Science - Insects");
        } else if (contains(lower, "spider") || contains(lower, "tick")) {
            showResponse("Spiders & ticks:\n- 2 body parts\n- 4 pairs of legs (8 legs)\n- No wings\n- No antennae", "Science - Spiders & Ticks");
        } else if (contains(lower, "snail") || contains(lower, "slug")) {
            showResponse("Snails & slugs:\n- Soft body, no wings\n- 2 pairs of feelers (receptacles)\n- Move by crawling on slimy mucus using muscular foot\n- Snails have shells; slugs do not", "Science - Snails & Slugs");
        } else if (contains(lower, "centipede") || contains(lower, "millipede")) {
            showResponse("Centipedes: 1 pair of legs per segment.\nMillipedes: 2 pairs of legs per segment, coil when disturbed.\nBoth: 2 body sections (head + trunk) divided into many segments.", "Science - Centipedes & Millipedes");
        } else if (contains(lower, "importance") && contains(lower, "invertebr")) {
            showResponse("Importance of invertebrates:\n1. Food - termites eaten by some; bees produce honey\n2. Pollination - insects pollinate flowering plants\n3. Clean environment - millipedes eat decaying matter, making compost", "Science - Invertebrates");
        } else if (contains(lower, "invertebr")) {
            showResponse("Invertebrates: animals without a backbone.\nExamples: bees, flies, grasshoppers, earthworms, lobsters, snails, millipedes.\nGroups: insects, spiders/ticks, snails/slugs, centipedes/millipedes.", "Science - Invertebrates");
        } else if (contains(lower, "vertebr")) {
            showResponse("Vertebrates have a backbone. Examples: fish, amphibians, reptiles, birds and mammals.", "Science");
        } else if (contains(lower, "food chain") || contains(lower, "food web")) {
            showResponse("A food chain shows who eats whom. Producer -> Herbivore -> Carnivore. Energy flows along the chain.", "Science");
        } else if (contains(lower, "habitat")) {
            showResponse("A habitat is the natural home of an organism. It provides food, water, shelter and space.", "Science");

        // --- Circulatory System ---
        } else if (contains(lower, "circulat")) {
            showResponse("Circulatory system: heart + blood + blood vessels.\nTransports: oxygen (lungs to body), digested food, CO2 (body to lungs), heat and waste products.", "Science - Circulatory System");
        } else if (contains(lower, "heart") && (contains(lower, "chamber") || contains(lower, "auricle") || contains(lower, "ventricle") || contains(lower, "pump"))) {
            showResponse("Heart has 4 chambers:\n- 2 auricles (upper, thin walls) - receive blood\n- 2 ventricles (lower, thick walls) - pump blood\nRight auricle: receives deoxygenated blood.\nLeft ventricle: pumps oxygenated blood to body via aorta.", "Science - Heart");
        } else if (contains(lower, "artery") || contains(lower, "arteries")) {
            showResponse("Arteries:\n- Thick elastic walls, narrow lumen\n- Carry blood AWAY from heart\n- Carry oxygenated blood (except pulmonary artery)\n- Blood flows under HIGH pressure", "Science - Arteries");
        } else if (contains(lower, "vein")) {
            showResponse("Veins:\n- Thin walls, wide lumen\n- Have VALVES to prevent backflow\n- Carry blood TOWARDS heart\n- Carry deoxygenated blood (except pulmonary vein)", "Science - Veins");
        } else if (contains(lower, "capillar")) {
            showResponse("Capillaries:\n- Very thin walls, no valves\n- Reach every part of the body\n- Exchange: oxygen and food into cells; CO2 and wastes into blood", "Science - Capillaries");
        } else if (contains(lower, "pulse")) {
            showResponse("Pulse: beat felt in arteries caused by the heart pumping.\nNormal resting pulse: 60-100 beats per minute.\nPulse INCREASES during activity - body needs more oxygen.", "Science - Pulse");
        } else if (contains(lower, "plasma")) {
            showResponse("Plasma:\n- Liquid part of blood (pale yellow)\n- Transports: digested food, oxygen, CO2, waste products, heat, hormones, blood cells.", "Science - Plasma");
        } else if (contains(lower, "red blood")) {
            showResponse("Red blood cells:\n- Biconcave shape\n- Contain haemoglobin (carries oxygen)\n- No nucleus when mature\n- Made in red bone marrow\n- Carry oxygen from lungs to body", "Science - Red Blood Cells");
        } else if (contains(lower, "white blood")) {
            showResponse("White blood cells:\n- Larger than red cells, have nucleus\n- No definite shape\n- Made in yellow bone marrow and lymph glands\n- Fight and kill germs by engulfing them\n- Ratio to red cells: 1:600", "Science - White Blood Cells");
        } else if (contains(lower, "platelet")) {
            showResponse("Platelets:\n- Tiny oval shaped cells in plasma\n- Help blood CLOT when injured\n- Stop bleeding from cuts and wounds", "Science - Platelets");
        } else if (contains(lower, "blood group") || contains(lower, "blood type") || contains(lower, "transfus")) {
            showResponse("ABO Blood Groups:\n- O: Universal DONOR (gives to all)\n- AB: Universal RECIPIENT (receives from all)\n- A: gives to A and AB\n- B: gives to B and AB\nBlood transfusion replaces lost blood.", "Science - Blood Groups");
        } else if (contains(lower, "blood vessel")) {
            showResponse("Blood vessels:\n1. Arteries - thick walls, carry blood away from heart\n2. Veins - thin walls, valves, carry blood towards heart\n3. Capillaries - thin walls, reach every cell, exchange substances", "Science - Blood Vessels");
        } else if (contains(lower, "blood") || contains(lower, "heart")) {
            showResponse("Blood has 4 components:\n1. Plasma - liquid, transports substances\n2. Red blood cells - carry oxygen\n3. White blood cells - fight germs\n4. Platelets - clotting\nThe heart pumps blood through arteries, veins and capillaries.", "Science - Blood");

        // --- Reproductive System ---
        } else if (contains(lower, "ovary") || contains(lower, "oviduct") || contains(lower, "fallopian")) {
            showResponse("Ovary: produces egg cells (ova) by ovulation, produces hormones.\nOviduct (Fallopian tube): connects ovary to uterus; site of fertilization.", "Science - Female Reproduction");
        } else if (contains(lower, "uterus") || contains(lower, "womb") || contains(lower, "cervix")) {
            showResponse("Uterus (womb): where fertilized egg develops into foetus.\nCervix: connects vagina to uterus; opens during childbirth.", "Science - Female Reproduction");
        } else if (contains(lower, "female") && contains(lower, "reproduct")) {
            showResponse("Female reproductive system:\n- Ovaries: produce eggs (ova)\n- Oviduct: fertilization occurs here\n- Uterus/Womb: foetus develops here\n- Cervix: connects vagina to uterus\n- Vagina: birth canal", "Science - Female Reproduction");
        } else if (contains(lower, "testis") || contains(lower, "testes") || contains(lower, "sperm")) {
            showResponse("Testis: produces sperm and hormones, enclosed in scrotum.\nSperm duct: carries sperm to urethra.\nGlands produce seminal fluid. Semen = seminal fluid + sperm.", "Science - Male Reproduction");
        } else if (contains(lower, "male") && contains(lower, "reproduct")) {
            showResponse("Male reproductive system:\n- Testis: produces sperm and hormones\n- Sperm duct: carries sperm to urethra\n- Urethra: passage for sperm and urine\n- Glands: produce seminal fluid\n- Penis: delivers sperm", "Science - Male Reproduction");
        } else if (contains(lower, "reproduct")) {
            showResponse("Reproductive systems allow living things to produce young.\nFemale: ovaries, oviduct, uterus, cervix, vagina.\nMale: testis, sperm duct, urethra, glands, penis.", "Science - Reproduction");

        // --- Adolescence ---
        } else if (contains(lower, "adolescen") || contains(lower, "puberty")) {
            showResponse("Adolescence (age 12-19):\nBoys: voice breaks, chest broadens, facial hair, sperm mature, wet dreams.\nGirls: breasts grow, hips widen, menstruation begins (every 28 days).\nBoth: body hair, pimples (acne), rapid height and weight increase.", "Science - Adolescence");
        } else if (contains(lower, "menstruat")) {
            showResponse("Menstruation: monthly shedding of uterus lining if egg is not fertilized.\nOccurs once a month, lasts 4-5 days.\nOvulation: release of an egg from the ovary every 28 days.", "Science - Menstruation");

        // --- Water Conservation ---
        } else if (contains(lower, "conserv") && contains(lower, "water")) {
            showResponse("Water conservation: proper care and use of water.\nWays to conserve:\n1. Harvest rainwater\n2. Reuse water\n3. Use water sparingly\n4. Mulching/shading\n5. Build dams\n6. Recycle water\n7. Reduce water use", "Science - Water Conservation");
        } else if ((contains(lower, "reus") || contains(lower, "reuse")) && contains(lower, "water")) {
            showResponse("Reusing water:\n- Clothes wash water: flush toilet, clean house\n- Fruit/vegetable wash water: water crops on farm\n- Clothes water: sprinkle on earthen floors to reduce dust\n- Handwashing water: mop floors", "Science - Water Reuse");

        // --- Plant overview (catch-all for bare "plant"/"plants"/"grow") ---
        } else if (contains(lower, "plant") || contains(lower, "grow")) {
            showResponse("PLANTS (CBC Grade 6):\n4 types: Trees, Shrubs, Herbs, Grass\n6 parts: roots, stem, leaves, flowers, fruits, seeds\nRoot types: Taproot (deep - mango, acacia) | Fibrous (shallow - maize, grass)\nFood-making: Photosynthesis\n  sunlight + water + CO2 + chlorophyll -> glucose + O2\nAsk: 'photosynthesis' 'transpiration' 'parts of plant' 'types of roots' 'pollination' 'germination'", "Science - Plants");

        // --- General ---
        } else if (contains(lower, "living")) {
            showResponse("Living things: move, feed, grow, breathe, reproduce, respond to stimuli and excrete waste.\nExamples: plants, animals, fungi.", "Science - Living Things");
        } else if (contains(lower, "animal")) {
            showResponse("Animals: classified as vertebrates (have backbone) or invertebrates (no backbone).\nInvertebrates include: insects, spiders, snails, centipedes, millipedes.", "Science");
        } else if (contains(lower, "experiment")) {
            showResponse("Try: germinate seeds in wet cotton, observe plants in light vs dark, check pulse before/after exercise, or track animal behaviour.", "Science");
        } else {
            showResponse("Grade 6 Science topics:\n- Plants, Invertebrates, Vertebrates\n- Circulatory & Reproductive systems\n- Digestive, Respiratory & Skeletal systems\n- Simple machines, States of matter\n- Soil, Weather, Ecosystems\n- Microorganisms & Disease\nAsk about any topic!", "Science");
        }
    }

    // ── Low confidence fallback ───────────────────────────────────────────────
    /**
     * Returns true if answered on-device, false if dispatched to cloud.
     * Caller uses the return value to keep local/cloud answer counters consistent.
     */
    private boolean handleLowConfidence(String question) {
        if (question.indexOf('+') >= 0 || question.indexOf('-') >= 0
                || question.indexOf('*') >= 0 || question.indexOf('/') >= 0) {
            showResponse(evaluateMathExpression(question), "Math");
            return true;
        }
        askCloud(question);
        return false;
    }

    private void askCloud(final String question) {
        Alert waiting = new Alert("Cloud");
        waiting.setString("Asking the cloud AI...");
        waiting.setTimeout(Alert.FOREVER);
        display.setCurrent(waiting);

        SMSManager.sendToCloudAI(question, new CloudResponseListener() {
            public void onResponse(final String answer) {
                display.callSerially(new Runnable() {
                    public void run() { showResponse(answer, "Cloud Answer"); }
                });
            }
            public void onError(final String reason) {
                display.callSerially(new Runnable() {
                    public void run() {
                        showResponse(
                            "Cloud unreachable. I can help with Math and Science.\n"
                            + "Try: 'photosynthesis', 'fraction', 'LCM', '5*4'.",
                            "Offline");
                    }
                });
            }
        });
    }

    // ── LCG random number generator ──────────────────────────────────────────
    private int nextRand() {
        quizRandSeed = quizRandSeed * 1664525 + 1013904223;
        return quizRandSeed & 0x7fffffff;
    }

    // ── Select quiz questions using Fisher-Yates shuffle ─────────────────────
    private void showQuizTypePicker() {
        quizTypeList = new List("Choose Quiz Type", Choice.IMPLICIT);
        quizTypeList.append("Math Quiz",    null);
        quizTypeList.append("Science Quiz", null);
        quizTypeList.addCommand(selectCmd);
        quizTypeList.addCommand(backCmd);
        quizTypeList.setCommandListener(this);
        display.setCurrent(quizTypeList);
    }

    private void selectQuizQuestions() {
        quizRandSeed = (int) System.currentTimeMillis();
        // Build pool: math = topic 8 only; science = topics 0-7 only
        int poolSize = 0;
        for (int i = 0; i < QUESTIONS.length; i++) {
            if (quizMode == 0 && QUESTION_TOPIC[i] == 8) poolSize++;
            else if (quizMode == 1 && QUESTION_TOPIC[i] < 8) poolSize++;
        }
        int[] pool = new int[poolSize];
        int k = 0;
        for (int i = 0; i < QUESTIONS.length; i++) {
            if (quizMode == 0 && QUESTION_TOPIC[i] == 8) pool[k++] = i;
            else if (quizMode == 1 && QUESTION_TOPIC[i] < 8) pool[k++] = i;
        }
        // Fisher-Yates shuffle on pool
        for (int i = poolSize - 1; i > 0; i--) {
            int j = nextRand() % (i + 1);
            if (j < 0) j = -j;
            int tmp = pool[i]; pool[i] = pool[j]; pool[j] = tmp;
        }
        quizSessionLength = poolSize < 10 ? poolSize : 10;
        System.arraycopy(pool, 0, quizOrder, 0, quizSessionLength);
    }

    // ── Quiz ─────────────────────────────────────────────────────────────────
    private void startQuiz(int mode) {
        quizMode = mode;
        selectQuizQuestions();
        quizIndex = 0;
        quizScore = 0;
        showQuizQuestion(0);
    }

    private void showQuizQuestion(int sessionIdx) {
        int actualIdx = quizOrder[sessionIdx];

        StringBuffer title = new StringBuffer(quizMode == 0 ? "Math Quiz " : "Science Quiz ");
        title.append(sessionIdx + 1);
        title.append("/");
        title.append(quizSessionLength);

        quizForm = new Form(title.toString());

        StringItem qItem = new StringItem("", QUESTIONS[actualIdx]);
        quizForm.append(qItem);

        quizChoices = new ChoiceGroup("", Choice.EXCLUSIVE);
        for (int i = 0; i < OPTIONS[actualIdx].length; i++) {
            quizChoices.append(OPTIONS[actualIdx][i], null);
        }
        quizForm.append(quizChoices);

        quizForm.addCommand(submitCmd);
        quizForm.addCommand(backCmd);
        quizForm.setCommandListener(this);
        display.setCurrent(quizForm);
    }

    private void handleQuizSubmit() {
        int selected = quizChoices.getSelectedIndex();
        if (selected < 0) {
            showError("Please select an answer first!");
            return;
        }

        int currentActualIdx = quizOrder[quizIndex];
        boolean correct = (selected == ANSWERS[currentActualIdx]);
        if (correct) quizScore++;

        // Record per-topic result + SM-2 SRS update
        UserPreferences.recordTopicResult(QUESTION_TOPIC[currentActualIdx], correct);
        UserPreferences.srsUpdate(QUESTION_TOPIC[currentActualIdx], correct ? 5 : 0);

        StringBuffer msg = new StringBuffer();
        if (correct) {
            msg.append("CORRECT!\n\n");
        } else {
            msg.append("WRONG!\n\nCorrect answer: ");
            msg.append(OPTIONS[currentActualIdx][ANSWERS[currentActualIdx]]);
            msg.append("\n\n");
        }
        msg.append(EXPLANATIONS[currentActualIdx]);

        int nextIndex = quizIndex + 1;
        quizIndex = nextIndex;

        Displayable nextScreen;
        if (nextIndex < quizSessionLength) {
            // Pre-build the next question form so the Alert can transition to it
            int nextActualIdx = quizOrder[nextIndex];
            StringBuffer nextTitle = new StringBuffer(quizMode == 0 ? "Math Quiz " : "Science Quiz ");
            nextTitle.append(nextIndex + 1);
            nextTitle.append("/");
            nextTitle.append(quizSessionLength);
            Form nextQuizForm = new Form(nextTitle.toString());
            StringItem nqItem = new StringItem("", QUESTIONS[nextActualIdx]);
            nextQuizForm.append(nqItem);
            ChoiceGroup nextChoices = new ChoiceGroup("", Choice.EXCLUSIVE);
            for (int i = 0; i < OPTIONS[nextActualIdx].length; i++) {
                nextChoices.append(OPTIONS[nextActualIdx][i], null);
            }
            nextQuizForm.append(nextChoices);
            nextQuizForm.addCommand(submitCmd);
            nextQuizForm.addCommand(backCmd);
            nextQuizForm.setCommandListener(this);
            quizForm    = nextQuizForm;
            quizChoices = nextChoices;
            nextScreen  = nextQuizForm;
        } else {
            // All questions done — build results screen
            UserPreferences.recordQuizResult(quizScore, quizSessionLength);
            nextScreen = buildResultsForm();
        }

        Alert feedback = new Alert(correct ? "Correct!" : "Wrong");
        feedback.setString(msg.toString());
        feedback.setTimeout(4000);
        display.setCurrent(feedback, nextScreen);
    }

    private Form buildResultsForm() {
        Form results = new Form(quizMode == 0 ? "Math Quiz Results" : "Science Quiz Results");

        int total = quizSessionLength;
        int pct   = (quizScore * 100) / total;
        String grade;
        if      (pct >= 80) grade = "A - Excellent!";
        else if (pct >= 60) grade = "B - Good";
        else if (pct >= 40) grade = "C - Keep studying";
        else                grade = "D - Review the topics";

        StringBuffer sb = new StringBuffer();
        sb.append("Score: ");  sb.append(quizScore);
        sb.append(" / ");      sb.append(total);
        sb.append("\nPercentage: "); sb.append(pct); sb.append("%");
        sb.append("\nGrade: ");      sb.append(grade);
        sb.append("\n\nTotal quizzes taken: ");
        sb.append(UserPreferences.getQuizAttempts());
        sb.append("\nOverall correct: ");
        sb.append(UserPreferences.getQuizTotalCorrect());
        sb.append(" / ");
        sb.append(UserPreferences.getQuizTotalAnswered());

        results.append(new StringItem("", sb.toString()));
        results.addCommand(backCmd);
        results.setCommandListener(this);
        return results;
    }

    // ── Progress ─────────────────────────────────────────────────────────────
    private void showProgress() {
        int questions = UserPreferences.getTotalQuestions();
        int local     = UserPreferences.getLocalAnswers();
        int attempts  = UserPreferences.getQuizAttempts();
        int correct   = UserPreferences.getQuizTotalCorrect();
        int answered  = UserPreferences.getQuizTotalAnswered();

        StringBuffer sb = new StringBuffer();
        sb.append("=== My STEM Progress ===");
        sb.append("\nQuestions asked: "); sb.append(questions);
        sb.append("\nAnswered on-device: "); sb.append(local);

        sb.append("\n\n=== Quiz History ===");
        sb.append("\nQuizzes taken: "); sb.append(attempts);
        if (answered > 0) {
            sb.append("\nTotal correct: "); sb.append(correct);
            sb.append(" / "); sb.append(answered);
            int overallPct = (correct * 100) / answered;
            sb.append("\nOverall score: "); sb.append(overallPct); sb.append("%");
            String overallGrade;
            if      (overallPct >= 80) overallGrade = "A - Excellent!";
            else if (overallPct >= 60) overallGrade = "B - Good";
            else if (overallPct >= 40) overallGrade = "C - Keep studying";
            else                       overallGrade = "D - Review topics";
            sb.append("\nGrade: "); sb.append(overallGrade);
        } else {
            sb.append("\nNo quizzes taken yet.\nTake a quiz to track your score!");
        }

        sb.append("\n\n=== Topic Breakdown ===");
        for (int t = 0; t < 9; t++) {
            int tCorrect   = UserPreferences.getTopicCorrect(t);
            int tAttempted = UserPreferences.getTopicAttempted(t);
            sb.append("\n"); sb.append(UserPreferences.getTopicName(t));
            sb.append(": "); sb.append(tCorrect);
            sb.append("/"); sb.append(tAttempted);
        }

        int weakest = UserPreferences.getWeakestTopic();
        sb.append("\n\nWeakest topic: ");
        sb.append(UserPreferences.getTopicName(weakest));

        sb.append("\n\n=== Adaptive Review (SRS) ===");
        for (int t = 0; t < 9; t++) {
            sb.append("\n");
            sb.append(UserPreferences.getTopicName(t));
            sb.append(": ");
            sb.append(UserPreferences.getSRSStatus(t));
        }
        int dueTopic = UserPreferences.getDueTopicForReview();
        if (dueTopic >= 0) {
            sb.append("\n-> Study now: ");
            sb.append(UserPreferences.getTopicName(dueTopic));
        }

        sb.append("\n\n=== AI Analytics ===");
        sb.append("\n"); sb.append(EvaluationLogger.getSummary());
        sb.append("\nIntents: "); sb.append(EvaluationLogger.getIntentDistribution());

        showResponse(sb.toString(), "My Progress");
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /**
     * Prepend the last topic keyword so ambiguous follow-up questions inherit context.
     */
    private boolean isMathQuery(String q) {
        if (q.indexOf('+') >= 0 || q.indexOf('*') >= 0) return true;
        // fraction pattern: digit/digit
        for (int i = 1; i < q.length() - 1; i++) {
            if (q.charAt(i) == '/' && Character.isDigit(q.charAt(i - 1))
                    && Character.isDigit(q.charAt(i + 1))) return true;
        }
        // subtraction between numbers or after a space
        for (int i = 1; i < q.length(); i++) {
            if (q.charAt(i) == '-'
                    && (Character.isDigit(q.charAt(i - 1)) || q.charAt(i - 1) == ' '))
                return true;
        }
        String lower = q.toLowerCase();
        return contains(lower, "fraction") || contains(lower, "percent")
            || contains(lower, "lcm")      || contains(lower, "hcf")
            || contains(lower, "ratio")    || contains(lower, "decimal")
            || contains(lower, "mean")     || contains(lower, "mode")
            || contains(lower, "range")    || contains(lower, "area")
            || contains(lower, "perimeter")|| contains(lower, "volume")
            || contains(lower, "average")  || contains(lower, "calculat")
            || contains(lower, "multiply") || contains(lower, "divide");
    }

    private boolean isScienceQuery(String q) {
        String lower = q.toLowerCase();
        return contains(lower, "plant")     || contains(lower, "animal")
            || contains(lower, "photo")     || contains(lower, "transpir")
            || contains(lower, "root")      || contains(lower, "leaf")
            || contains(lower, "leaves")    || contains(lower, "seed")
            || contains(lower, "flower")    || contains(lower, "pollin")
            || contains(lower, "germinat")  || contains(lower, "blood")
            || contains(lower, "heart")     || contains(lower, "pulse")
            || contains(lower, "vein")      || contains(lower, "artery")
            || contains(lower, "lung")      || contains(lower, "breath")
            || contains(lower, "digest")    || contains(lower, "stomach")
            || contains(lower, "bone")      || contains(lower, "muscle")
            || contains(lower, "insect")    || contains(lower, "spider")
            || contains(lower, "bacteria")  || contains(lower, "virus")
            || contains(lower, "soil")      || contains(lower, "erosion")
            || contains(lower, "weather")   || contains(lower, "cloud")
            || contains(lower, "ecosystem") || contains(lower, "food chain")
            || contains(lower, "reproduct") || contains(lower, "adolescen")
            || contains(lower, "vertebrat") || contains(lower, "mammal")
            || contains(lower, "reptile")   || contains(lower, "amphibian")
            || contains(lower, "lever")     || contains(lower, "pulley")
            || contains(lower, "matter")    || contains(lower, "solid")
            || contains(lower, "liquid")    || contains(lower, "gas")
            || contains(lower, "living")    || contains(lower, "organism")
            || contains(lower, "chlorophyl")|| contains(lower, "stomata");
    }

    /**
     * Inject domain prefix only when the query is genuinely ambiguous.
     * A query that already has clear math or science signals is routed directly
     * without forcing the previous session's context onto it.
     */
    private String injectContext(String q) {
        boolean math    = isMathQuery(q);
        boolean science = isScienceQuery(q);

        // Query has its own clear signal — use that, not the session history
        if (math && !science) {
            StringBuffer sb = new StringBuffer("math "); sb.append(q); return sb.toString();
        }
        if (science && !math) {
            StringBuffer sb = new StringBuffer("science "); sb.append(q); return sb.toString();
        }

        // Ambiguous (both or neither) — fall back to session context
        if (lastSuccessfulIntent == 0) {
            StringBuffer sb = new StringBuffer("math ");    sb.append(q); return sb.toString();
        }
        if (lastSuccessfulIntent == 1) {
            StringBuffer sb = new StringBuffer("science "); sb.append(q); return sb.toString();
        }
        return q;
    }

    /** Replace every occurrence of 'from' with 'to' in 'text' (case-sensitive). */
    private String replaceStr(String text, String from, String to) {
        int idx = text.indexOf(from);
        if (idx < 0) return text;
        StringBuffer sb = new StringBuffer();
        int start = 0;
        while (idx >= 0) {
            sb.append(text.substring(start, idx));
            sb.append(to);
            start = idx + from.length();
            idx = text.indexOf(from, start);
        }
        sb.append(text.substring(start));
        return sb.toString();
    }

    /**
     * Expand common student abbreviations, Kiswahili terms, and vowel-dropped shortcuts.
     */
    private String normalizeQuery(String q) {
        String lower = q.toLowerCase();

        // Kiswahili question words
        lower = replaceStr(lower, "ni nini",   "what is");
        lower = replaceStr(lower, "nini",      "what");
        lower = replaceStr(lower, "jinsi",     "how");
        lower = replaceStr(lower, "kwa nini",  "why");
        lower = replaceStr(lower, "eleza",     "explain");
        lower = replaceStr(lower, "aina za",   "types of");
        lower = replaceStr(lower, "sehemu za", "parts of");
        lower = replaceStr(lower, "kazi za",   "functions of");
        lower = replaceStr(lower, "taja",      "name");

        // Kiswahili science
        lower = replaceStr(lower, "usanisinuru",       "photosynthesis");
        lower = replaceStr(lower, "mmea",              "plant");
        lower = replaceStr(lower, "mnyama",            "animal");
        lower = replaceStr(lower, "damu",              "blood");
        lower = replaceStr(lower, "moyo",              "heart");
        lower = replaceStr(lower, "mapafu",            "lungs");
        lower = replaceStr(lower, "kupumua",           "breathing");
        lower = replaceStr(lower, "mfupa",             "bone");
        lower = replaceStr(lower, "misuli",            "muscle");
        lower = replaceStr(lower, "chakula",           "food");
        lower = replaceStr(lower, "udongo",            "soil");
        lower = replaceStr(lower, "hali ya hewa",      "weather");
        lower = replaceStr(lower, "mashine",           "machine");
        lower = replaceStr(lower, "nguvu",             "energy");
        lower = replaceStr(lower, "mwanga",            "light");
        lower = replaceStr(lower, "sauti",             "sound");
        lower = replaceStr(lower, "viumbe vidogo",     "microorganism");
        lower = replaceStr(lower, "ugonjwa",           "disease");
        lower = replaceStr(lower, "mzunguko wa damu",  "circulatory");
        lower = replaceStr(lower, "uzazi",             "reproduction");
        lower = replaceStr(lower, "balehe",            "adolescence");
        lower = replaceStr(lower, "mimea",             "plants");
        lower = replaceStr(lower, "wanyama",           "animals");
        lower = replaceStr(lower, "mchwa",             "insect");
        lower = replaceStr(lower, "buibui",            "spider");
        lower = replaceStr(lower, "chura",             "amphibian");

        // Kiswahili math
        lower = replaceStr(lower, "hesabu",   "math");
        lower = replaceStr(lower, "sehemu",   "fraction");
        lower = replaceStr(lower, "asilimia", "percentage");
        lower = replaceStr(lower, "uwiano",   "ratio");
        lower = replaceStr(lower, "eneo",     "area");
        lower = replaceStr(lower, "mzingo",   "perimeter");
        lower = replaceStr(lower, "wastani",  "mean");

        // English science abbreviations
        lower = replaceStr(lower, "plnt",     "plant");
        lower = replaceStr(lower, "anml",     "animal");
        lower = replaceStr(lower, "lvng",     "living");
        lower = replaceStr(lower, "phtsyn",   "photosynthesis");
        lower = replaceStr(lower, "photsyn",  "photosynthesis");
        lower = replaceStr(lower, "stmta",    "stomata");
        lower = replaceStr(lower, "chlorph",  "chlorophyll");
        lower = replaceStr(lower, "transp",   "transpiration");
        lower = replaceStr(lower, "polln",    "pollination");
        lower = replaceStr(lower, "invrtbr",  "invertebrate");
        lower = replaceStr(lower, "vertbr",   "vertebrate");
        lower = replaceStr(lower, "circltn",  "circulation");
        lower = replaceStr(lower, "reprdct",  "reproduction");
        lower = replaceStr(lower, "adolscn",  "adolescence");
        lower = replaceStr(lower, "mnstrtn",  "menstruation");
        lower = replaceStr(lower, "cnservtn", "conservation");
        // general shortcuts
        lower = replaceStr(lower, "wht",  "what");
        lower = replaceStr(lower, "hw",   "how");
        lower = replaceStr(lower, "dfn",  "definition");
        lower = replaceStr(lower, "expl", "explain");
        return lower;
    }

    private boolean contains(String str, String sub) {
        return str.indexOf(sub) != -1;
    }

    private String removeSpaces(String str) {
        char[] chars = new char[str.length()];
        int j = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c != ' ') chars[j++] = c;
        }
        return new String(chars, 0, j);
    }

    // ── Fraction arithmetic helpers ──────────────────────────────────────────
    private int gcd(int a, int b) {
        if (a < 0) a = -a;
        if (b < 0) b = -b;
        while (b != 0) { int t = b; b = a % b; a = t; }
        return a == 0 ? 1 : a;
    }

    /** Parse "3/4" → {3,4}  or  "5" → {5,1} */
    private int[] parseFrac(String s) throws NumberFormatException {
        int sl = s.indexOf('/');
        if (sl > 0) {
            return new int[]{ Integer.parseInt(s.substring(0, sl)),
                              Integer.parseInt(s.substring(sl + 1)) };
        }
        return new int[]{ Integer.parseInt(s), 1 };
    }

    /** Format {num,den} as "3/4" or "1 1/4" or "5" */
    private String fmtFrac(int num, int den) {
        if (den < 0) { num = -num; den = -den; }
        int g = gcd(num < 0 ? -num : num, den);
        num /= g; den /= g;
        if (den == 1) return String.valueOf(num);
        if (num > den && den > 1) {
            int whole = num / den;
            int rem   = num % den;
            if (rem == 0) return String.valueOf(whole);
            return new StringBuffer().append(whole).append(" ")
                   .append(rem).append("/").append(den).toString();
        }
        return new StringBuffer().append(num).append("/").append(den).toString();
    }

    private String evaluateMathExpression(String expr) {
        expr = removeSpaces(expr);
        // Strip leading non-numeric prefix (e.g. "math", "science")
        int start = 0;
        while (start < expr.length() && !Character.isDigit(expr.charAt(start))
               && expr.charAt(start) != '-') start++;
        if (start > 0) expr = expr.substring(start);

        // Find operator — skip '/' that is part of a fraction (has digits on both sides)
        int plus = -1, minus = -1, mult = -1, div = -1;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '+') { plus  = i; break; }
            if (c == '*') { mult  = i; break; }
            // '-' only counts as subtraction if not at position 0
            if (c == '-' && i > 0) { minus = i; break; }
            // '/' only counts as division operator if the character AFTER it is not a digit
            // (digit/digit means fraction; digit/letter or end-of-string means division)
            if (c == '/' && i > 0) {
                boolean nextIsDigit = (i + 1 < expr.length())
                        && Character.isDigit(expr.charAt(i + 1));
                boolean prevIsDigit = Character.isDigit(expr.charAt(i - 1));
                // If prev and next are both digits this is a fraction slash — skip
                if (!(prevIsDigit && nextIsDigit)) { div = i; break; }
            }
        }
        // Also scan for operator AFTER the first fraction operand
        if (plus < 0 && minus < 0 && mult < 0 && div < 0) {
            // Might be "3/4+1/2" where '+' comes after the first slash
            for (int i = 0; i < expr.length(); i++) {
                char c = expr.charAt(i);
                if (c == '+') { plus  = i; break; }
                if (c == '*') { mult  = i; break; }
                if (c == '-' && i > 0) { minus = i; break; }
            }
        }

        try {
            if (plus > 0) {
                int[] L = parseFrac(expr.substring(0, plus));
                int[] R = parseFrac(expr.substring(plus + 1));
                return fmtFrac(L[0]*R[1] + R[0]*L[1], L[1]*R[1]);
            } else if (mult > 0) {
                int[] L = parseFrac(expr.substring(0, mult));
                int[] R = parseFrac(expr.substring(mult + 1));
                return fmtFrac(L[0]*R[0], L[1]*R[1]);
            } else if (minus > 0) {
                int[] L = parseFrac(expr.substring(0, minus));
                int[] R = parseFrac(expr.substring(minus + 1));
                return fmtFrac(L[0]*R[1] - R[0]*L[1], L[1]*R[1]);
            } else if (div > 0) {
                int[] L = parseFrac(expr.substring(0, div));
                int[] R = parseFrac(expr.substring(div + 1));
                if (R[0] == 0) return "Cannot divide by zero";
                return fmtFrac(L[0]*R[1], L[1]*R[0]);
            } else {
                return "Type an expression like 2+3, 5*4, or 3/4+1/2";
            }
        } catch (Exception e) {
            return "Error parsing numbers";
        }
    }

    private void showResponse(String response, String title) {
        Alert alert = new Alert(title);
        alert.setString(response);
        alert.setTimeout(Alert.FOREVER);
        alert.removeCommand(Alert.DISMISS_COMMAND);
        alert.addCommand(okCmd);
        alert.addCommand(moreCmd);
        alert.setCommandListener(this);
        display.setCurrent(alert);
    }

    /**
     * Handle a "More" tap. Progressive concentric circles:
     *   moreLevel == 1: try the cloud LLM for a deeper answer; on offline
     *                   error, surface the intent-level overview from
     *                   MicroResponses (broader CBC frame for the same topic).
     *   moreLevel >= 2: walk through MicroResponses.relatedFor(intent), each
     *                   tap re-routing through the intent handler with the
     *                   next sibling keyword. Surfaces related concepts in
     *                   the same CBC topic web.
     *   exhausted:      friendly "ask online for more depth" message.
     */
    private void handleMore() {
        if (lastQuestion == null) {
            display.setCurrent(mainMenu);
            return;
        }
        moreLevel++;
        if (moreLevel == 1) {
            askCloudWithBroaderFallback(lastQuestion, lastIntent);
            return;
        }
        String[] related = MicroResponses.relatedFor(lastIntent);
        if (related == null || relatedCursor >= related.length) {
            showResponse(
                "I have shown you everything I can offline.\n"
                + "Ask this when you are online for a deeper answer.",
                "More");
            return;
        }
        String keyword = related[relatedCursor++];
        if (lastIntent == 0) {
            handleMathQuestion(keyword);
        } else {
            handleScienceQuestion(keyword);
        }
    }

    /**
     * First-tier "More" behaviour: try the cloud, and on cloud error fall
     * back to the intent-level CBC overview (broader frame for the same
     * topic) instead of the generic STEM menu.
     */
    private void askCloudWithBroaderFallback(final String question, final int intent) {
        Alert waiting = new Alert("Cloud");
        waiting.setString("Asking the cloud AI...");
        waiting.setTimeout(Alert.FOREVER);
        display.setCurrent(waiting);

        SMSManager.sendToCloudAI(question, new CloudResponseListener() {
            public void onResponse(final String answer) {
                display.callSerially(new Runnable() {
                    public void run() { showResponse(answer, "Cloud Answer"); }
                });
            }
            public void onError(final String reason) {
                display.callSerially(new Runnable() {
                    public void run() {
                        showResponse(MicroResponses.getResponse(intent), "More");
                    }
                });
            }
        });
    }

    private void showError(String message) {
        Alert error = new Alert("Error");
        error.setString(message);
        error.setTimeout(3000);
        display.setCurrent(error, mainMenu);
    }

    public void pauseApp() {}
    public void destroyApp(boolean unconditional) {
        // Generation phase of the FL round runs entirely offline:
        // compute delta, add DP noise, quantise, enqueue. No network call.
        if (FederatedLearning.isEnabled() && aiModel != null) {
            FederatedLearning.enqueueDelta(aiModel);
        }
        EvaluationLogger.save();
        UserPreferences.saveSRS();
    }
}
