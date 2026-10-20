# Avatar swap status

| Item | Status |
|------|--------|
| Current morph | region-masked-soft-v2 (wide falloff, shoulder carries arm, distal length) |
| Map | v9 |

## Notes
- Soft overlapping masks (not hard ROI rings)
- SHOULDER spreads whole arm chain laterally
- LENGTH pushes distal (foot/hand/head move; not mid-stretch vs fixed ends)

`Blender --background --python tmp_bake_mujer_region_masked.py`
