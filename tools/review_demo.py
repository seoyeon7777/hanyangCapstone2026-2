"""리뷰 일치도 보정 전후의 순위 비교 시연.  python tools/review_demo.py
review_mode: off(표시만) / both(중립 쪽으로 양방향) / positive_only(높은 점수만 당김). 숫자는 시연용 가정이다."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import matching  # noqa: E402

profile = {"ratios": {"S": 0.61, "W": 0.17, "N": 0.22}, "primary": "S", "is_mixed": False, "secondary": "N"}
base = matching.load_rules()
items = [
    {"item_id": "A 슬림+일자 (리뷰 없음)", "tags": ["fit:slim", "sil:IH"]},
    {"item_id": "B 슬림+일자 (10명 중 8명 다름)", "tags": ["fit:slim", "sil:IH"], "review": {"n": 10, "mismatch": 8}},
    {"item_id": "C 오버핏 (리뷰 없음)", "tags": ["fit:over"]},
    {"item_id": "D 오버핏 (10명 중 8명 다름)", "tags": ["fit:over"], "review": {"n": 10, "mismatch": 8}},
    {"item_id": "E 태그 없음", "tags": []},
]
for mode in ("off", "both", "positive_only"):
    rules = matching.Rules(**{**base.__dict__, "config": {**base.config, "review_mode": mode}})
    rows = sorted((matching.match_item(it, profile, rules) for it in items), key=lambda r: -r["body_fit"])
    print(f"[review_mode={mode}]")
    for r in rows:
        print(f'  {r["item_id"]:<28} 점수 {r["body_fit"]:.3f}  일치도 {r["review_agreement"]:.2f}')
