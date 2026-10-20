# Morph part specification (accuracy-first)

Single source of truth for slider cm, mesh tape, and morph behaviour.
Machine-readable twin: `app/src/main/assets/models/morph_part_spec.json`.

## Contract

1. Each `BodyPart` has one **tape definition** (SizeKorea / AI Hub label).
2. Morph at `w=+1` must change **that tape** by at least `minDeltaCm`, and must not change **forbidden** tapes beyond `maxLeakCm`.
3. Vertex **masks are frozen at rest** (`part_masks.json`). Re-bake never re-cuts by live z%.
4. Display cm comes from mesh tape (LUT). Population calibration may scale so `w=0` matches population median; **do not clamp morph span**.

## QA tolerances

| Check | Tolerance |
|-------|-----------|
| Target tape at `w=1` | `Δ ≥ minDeltaCm` |
| Forbidden tape | `|Δ| ≤ maxLeakCm` |
| Display vs tape (after LUT) | `|Δdisplay − Δtape| ≤ 0.5 cm` over safe `w` |
| Distal length rigid plate | per-side `std(displacement) ≤ 0.35 cm` |
| Foot/hand XY shear on leg/arm length | `max XY ≤ 0.30 cm` |

## Parts

See `morph_part_spec.json` for full fields (`measure`, `mask`, `morph`, `forbid`, `minDeltaCm`).

Summary:

| Part | Tape | Morph |
|------|------|-------|
| NECK | 목둘레 | neck cylinder radial |
| NECK_LENGTH | 목 세로 | distal +Z rigid |
| SHOULDER | 어깨사이너비 | ± X |
| CHEST | 젖가슴둘레 | bust band radial |
| UNDERBUST | 밑가슴둘레 | underbust band radial |
| BACK_LENGTH | 목뒤−허리 높이 | torso distal +Z |
| WAIST | 허리둘레 | waist band radial |
| PELVIS | 골반너비 | bi-iliac ±X |
| HIP | 엉덩이둘레 | buttock band radial |
| RISE | 허리높이−샅높이 | crotch↔waist Z sheet |
| UPPER_ARM | 편위팔둘레 | upper-arm soft tissue |
| UPPER_ARM_LENGTH | 위팔길이 | elbow+forearm+hand rigid |
| FOREARM | 전완둘레 | forearm soft tissue |
| FOREARM_LENGTH | 전완길이 | hand distal rigid |
| THIGH | 넙다리둘레 | thigh soft tissue (hull tape) |
| THIGH_LENGTH | 샅높이−무릎높이 | below-knee −Z rigid |
| CALF | 장딴지둘레 | calf soft tissue |
| CALF_LENGTH | 무릎→발목 | foot −Z rigid |

## Pipeline

```
tmp_build_part_masks.py  → part_masks.json   (frozen rest weights)
tmp_regional_bake.py     → shape keys + avatar.glb
tmp_iso_qa.py            → tape isolation report (must pass)
tmp_build_tape_cm_lut.py → morph_cm_lut.json (+ population w=0 center scale, no span clamp)
```

Shared code: `morph_accuracy_lib.py`