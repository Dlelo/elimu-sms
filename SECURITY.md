# Threat Model and Security Posture

This document is the load-bearing security artefact for the ElimuSMS /
STEMEdge3310 deployment. It is referenced from the four papers and the
Beamer presentation. Update this file whenever the implementation changes
in a way that adds or removes a defence.

## Assets and adversarial goals

Three assets matter:

1. **The student's interaction history** — what questions a particular
   child asked, what they got wrong on quizzes, when they used the app.
2. **The on-device classifier weights** — the local copy of the AI
   model, which adapts to the individual student.
3. **The shared global model** — what the cloud aggregates from all
   participating devices.

An adversary's goals (in order of plausibility for our deployment):

- **Linkability:** infer that a specific child sent a specific question.
- **Membership inference:** determine that a specific question was in a
  device's training set.
- **Gradient inversion:** reconstruct sensitive content from a single
  device's weight delta.
- **Model poisoning:** corrupt the shared global so it gives wrong
  answers to other children.
- **Service disruption:** prevent the system from working at all.

We deliberately exclude **adversaries with physical access to a
student's family phone** (parental, opportunistic) from the *secrecy*
guarantees — that threat is out of scope, and the system minimises its
on-device data footprint to limit harm.

## STRIDE-style analysis

| # | Threat | Mechanism | Mitigation in code | Residual risk |
|---|---|---|---|---|
| T1 | Lost SD card containing a queued FL delta | Sensitive student-derived weights leak | DP noise (`σ=0.05` Gaussian) added in `FederatedLearning.enqueueDelta` *before* RMS write; SD-card mirror is also already-noisy | **Material.** With deployed `(σ=0.05, clip=0.10)` per-round ε≈200 (see `cloud-server/privacy.py`); the delta is noisier than vanilla SGD but does not give textbook ε=0.3-DP. Tighten σ to 33+ for nominal ε=0.3. |
| T2 | Network MITM on HTTPS upload (compromised carrier, hostile WiFi) | Same as T1, plus integrity violation on global download | TLS in transit (operator responsibility); DP at source bounds the worst case even with TLS broken | Low for confidentiality (DP at source); medium for integrity — see T5. |
| T2a | **Carrier reads SMS body** (SMS is plain-text on the SS7 / SMSC path; lawful intercept reads it) | Same content-leakage class as T1/T2, applied to the SMS-primary FL transport | DP noise added at source before the bytes ever leave the device; carrier sees a noisy parameter vector that bounds membership inference per round | Same as T1: material under deployed `(σ=0.05, clip=0.10)`. The SMS path adds no new confidentiality risk over HTTPS because both carry the same already-noisy payload. |
| T2b | **SMS forgery against `/sms/inbound`** (attacker injects fake SMS at the gateway with a spoofed sender) | Adversary's bytes flow into FedAvg as if from a real device | (i) Robust aggregation (`trimmed_mean` / `Krum`) bounds the impact even when the adversary's payload passes parsing. (ii) Per-device HMAC pre-shared key + monotonic sequence counter is **planned future work**; not yet in code. | Medium until HMAC ships. Acceptable for the 100-device research deployment because the gateway shortcode is private; would need to be hardened before public rollout. |
| T2c | **SMS gateway billing-flood DoS** (attacker triggers high-cost SMS traffic against the gateway) | Operational cost spike; possible degradation of legitimate FL flow | Per-device rate limit on the gateway (Africa's Talking, Twilio, Kannel all support this); inbound message volume is observable in real time | Low. Rate-limit configuration is a deployment-time setting, not a code change. |
| T2d | **Gateway metadata leakage** (carrier or gateway log links a sender phone number to the FL participation pattern) | Re-identification of an individual student's contribution timing | Anonymous device IDs in payload (already implemented); shared shortcode with other apps; randomised upload timing (future) | **Material residual risk.** This is the strongest argument against SMS as transport, and is the genuine privacy-vs-availability trade-off the thesis must report honestly. |
| T3 | Malicious device sends crafted delta to dominate FedAvg | One device's parameters become the cohort global | `FL_AGGREGATOR=trimmed_mean` or `krum` in `cloud-server/fl.py` defends against up to 20% byzantine clients; verified by `tests/test_fl_wire.py::test_*_resists_byzantine` | Low when robust aggregator is enabled. **High if `FL_AGGREGATOR=mean` (the default) is left on for the field study** — switch to `trimmed_mean` for production. |
| T4 | Compromised teacher collector device replays old SD-card deltas | Replay attack inflates one device's contribution | `STALE_ANCHOR_TOLERANCE=3` rounds in `fl.py` rejects deltas targeting ancient anchors; per-device latest-delta-wins file naming caps amplification to 1× per device per round | Medium. A determined attacker can still replay within the 3-round window. |
| T5 | Server breach exfiltrates the global model | Adversary obtains the shared classifier (~1.6 KB float32) | The global is intentionally non-secret — it is what we ship to all phones via the OTA path. No defence needed. | None — the global is a public artefact by design. |
| T6 | Server breach exfiltrates the per-device delta queue (after upload, before aggregation) | Adversary obtains noisy deltas keyed by anonymous device ID | Anonymous device IDs (16 random bytes generated in `FederatedLearning.loadOrGenerateDeviceId`) bound linkability to a single device's history; DP noise bounds membership inference per round | Medium. Deltas are noisy but are linkable to the device-ID hex. Rotating device IDs across rounds is future work. |
| T7 | JAD tampering during sideload (a teacher edits `Elimu-CloudURL` to a malicious endpoint) | Cloud answers and FL uploads go to attacker | None on-device; we trust the deployment chain. **A signed JAR + signed JAD is the only real defence**; see "Signing" below. | High during deployment phase. Mitigated by physically supervising sideload and verifying the JAD URL field at install time. |
| T8 | Confused / playful student "Wrong" taps poison the on-device model | Local classifier drifts from utility | The "Wrong?" UI was *removed* in the OK/More redesign; the only learning trigger is the cloud-arbitrated label, which the student cannot inject directly | Low. Implicit feedback architecture (Section: Two-Button Interaction) eliminates this attack surface. |
| T9 | Cloud LLM returns biased / incorrect intent labels | Supervision signal corrupts every device's classifier over time | Server-side intent classifier (`classify.py`) is keyword-based and deterministic, not LLM-derived; this is a quality / coverage tradeoff but isolates the supervision from LLM drift | Low for the deployed path. **Move to LLM-derived intents only after offline agreement-rate validation against gold teacher labels.** |
| T10 | DoS against the cloud server | Children can't get cloud answers; FL stalls | The system's entire offline-first design is a defence: the device works without the cloud, FL via SD-card sneakernet works without the cloud | Low. The cloud is a degradable enhancement, not a critical path. |
| T11 | Side-channel leakage from RMS on a shared phone | Sibling / parent reads the queued deltas | DP noise at write time bounds membership inference; nothing else leaks (no raw queries are stored, only weight deltas) | Low. The on-device security boundary is the noise-at-source step. |
| T12 | Model extraction via repeated query | Attacker replicates the classifier by probing | The classifier is small (204 bytes packed, intentionally extractable from the JAR); we **do not** treat it as confidential | None — by design. |

## SMS-FL transport notes

The decision to make **SMS the primary FL transport**, rather than HTTPS,
materially changes the security posture in three ways:

1. **It expands the carrier's view of FL traffic.** Every FL upload is now
   visible to the operator's SMSC, where HTTPS was at least encrypted
   on the wire. We accept this because DP noise is added at source — the
   carrier sees a noisy vector, not raw queries — but this is exactly
   why the per-round ε number must be honest (see "Privacy budget
   reporting" below). If the deployed σ is too small, the SMS path
   leaks more than the proposal claims.

2. **It introduces a billable side-channel.** The SMS gateway operator
   knows which short-code received traffic, when, and from which
   sender MSISDN. Anonymisation in the payload does not anonymise the
   bearer-channel metadata. Mitigation in code: anonymous device IDs
   in the payload are decoupled from the MSISDN seen by the gateway.
   Mitigation in deployment: route through a shared shortcode so
   timing alone does not identify FL traffic.

3. **It removes the courier dependency.** The teacher-courier model in
   earlier drafts of this document is no longer load-bearing. SD-card
   storage is now strictly a local persistence overflow layer on the
   device itself, not a transport. T2a–T2d above replace the courier
   threats; if you still need to model "lost SD card", T1 covers it.

## What is and isn't load-bearing

- **Load-bearing:** DP noise added in `FederatedLearning.enqueueDelta` *before*
  the bytes touch RMS or SD card. This is the entire confidentiality
  guarantee. Verified by `cloud-server/privacy.py` budget accountant;
  honest current value: ε per round ≈ 200 with default parameters,
  cumulative across an 8-week study ≈ 1600 (basic composition).
- **Load-bearing:** Robust aggregator (`trimmed_mean` or `krum`) when
  enabled. Vanilla mean is *not* load-bearing — see T3 residual risk.
- **Not load-bearing:** Cryptographic secure aggregation (not
  implemented; infeasible on CLDC 1.1 24 KB heap; the threat model
  does not require it).
- **Not load-bearing:** TLS. We assume the carrier is honest-but-curious
  at most; DP at source covers a fully compromised network.

## Signing (open question)

The strongest threat we cannot mitigate in code is **T7 (JAD/JAR
tampering during sideload)**. A signed MIDlet (manufacturer-issued or
domain-bound code signing certificate) would close it, but such
certificates are expensive and not always available for J2ME
deployments in 2026. The deployment plan is to physically supervise
sideload during the 8-week study and verify `Elimu-CloudURL` on each
device at install time. Production deployment outside the study would
require revisiting this.

## Privacy budget reporting

The deployed parameters yield:

```
sensitivity (L2)     = 0.10 * sqrt(428)   ≈ 2.07
sigma (Gaussian std) = 0.05
delta                = 1e-5
epsilon per round    = 2.07 * sqrt(2 ln(1.25/1e-5)) / 0.05  ≈ 200.5
```

After 8 rounds (one round per week of study) under basic composition,
worst-case cumulative ε ≈ 1604 per device. To reach the proposal's
nominal ε = 0.3 per round, σ must be raised to ≥ 33.4, at which point
noise dominates the signal and convergence collapses. **The honest
report:** the deployed configuration is *better than vanilla SGD* (no
DP noise) but does not satisfy textbook ε = 0.3 differential privacy.
This trade-off is the substantive finding for the H₅ chapter, not a
weakness of the implementation.

## Updating this document

When you add a defence: add a row, mark the residual risk, link the
mitigation to a file/line. When you remove or weaken a defence: bump
the residual risk and note it in the next paper revision.
