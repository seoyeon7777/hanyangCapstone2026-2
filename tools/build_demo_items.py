"""A가 고른 상품 목록(CSV)으로 서버가 읽을 시연 상품 파일(JSON)을 만든다.

CSV는 첫 줄이 열 이름이다 (엑셀에서 "CSV UTF-8"로 저장).
    product_id    필수. ETRI 아이템 id (예: BL-001). 사진 파일 이름(BL-001.jpg)과 같다
    price         필수. 시연용 가격(원). 0은 앱에서 담기·구매가 막혀 받지 않는다
    popularity    선택. 인기도 0~1 (비우면 0.5)
    is_new        선택. 신상이면 1 (비우면 아님)
    product_name  선택. 비우면 "색 + 무늬 + 세부 종류" (예: 하늘색 블라우스)
    brand         선택. 비우면 "시연용 브랜드"
    colors        선택. 옵션에서 고를 색을 띄어 씀. 비우면 사진 속 색 + 임시 색으로 4가지 (색 정보가 없으면 임시 색 4가지)
    seasons       선택. spring summer fall winter 중에서 띄어 씀 (비우면 네 계절 모두. 원본에 계절 정보가 없다)
설명(tools/etri_describe.py)·태그(tools/etri_tagging.py → tagging.py)·사진 연결은 이 스크립트가 붙인다.

    python tools/build_demo_items.py <상품 목록.csv> <정리한 TSV> <사진 폴더> <저장할 products.json>

만든 파일은 라이선스(ETRI) 때문에 저장소에 넣지 않는다. 서버는 저장소 맨 위 items_file.txt에 그 경로를 적으면 읽는다.
"""
import csv
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import matching  # noqa: E402
from etri_describe import KINDS, describe, load_items, option_colors, pick_subtype, product_name  # noqa: E402
from etri_tagging import etri_tags  # noqa: E402

SEASONS = ("spring", "summer", "fall", "winter")
DEFAULT_BRAND = "시연용 브랜드"
DEFAULT_POPULARITY = 0.5
# (임시) 옵션 창에서 색을 고를 수 있게 사진 속 색에 더하는 시연용 색. 실제로 파는 색이 아니다
TEMP_COLORS = ("블랙", "화이트", "그레이", "네이비")
COLOR_CHOICES = 4
TRUE_WORDS = ("1", "y", "yes", "true", "o", "신상")
FALSE_WORDS = ("", "0", "n", "no", "false", "x")


def demo_colors(attrs: dict) -> list[str]:
    """사진 속 색(있으면 맨 앞) + 임시 색 → 선택지 COLOR_CHOICES개"""
    colors = option_colors(attrs)
    colors += [c for c in TEMP_COLORS if c not in colors]
    return colors[:COLOR_CHOICES]


def build_item(row: dict, source: dict, rules) -> dict:
    """CSV 한 줄 + 그 아이템의 속성 → 서버 상품 하나. 값이 잘못되면 ValueError."""
    item_id = row["product_id"]
    kind, attrs = source["kind"], source["attrs"]
    try:
        price = int(row.get("price", "").replace(",", ""))
    except ValueError:
        price = 0
    if price <= 0:
        raise ValueError("price는 1 이상의 숫자여야 합니다")
    try:
        popularity = float(row.get("popularity") or DEFAULT_POPULARITY)
    except ValueError:
        popularity = -1.0
    if not 0 <= popularity <= 1:
        raise ValueError("popularity는 0~1이어야 합니다")
    is_new = (row.get("is_new") or "").lower()
    if is_new not in TRUE_WORDS + FALSE_WORDS:
        raise ValueError("is_new는 1 또는 0이어야 합니다")
    seasons = (row.get("seasons") or "").split() or list(SEASONS)
    if any(s not in SEASONS for s in seasons):
        raise ValueError(f"seasons는 {' '.join(SEASONS)} 중에서 띄어 써야 합니다")
    name = row.get("product_name") or product_name(kind, attrs)
    # 태그에는 색을 붙인 이름 대신 세부 종류를 넣는다 (색·무늬 낱말이 태그로 읽히지 않게). 직접 적은 이름은 그대로 읽는다
    tag_name = row.get("product_name") or pick_subtype(attrs.get("F", []), KINDS[kind][0])
    return {
        "product_id": item_id, "product_name": name, "clothing_type": KINDS[kind][1],
        "tags": etri_tags(tag_name, attrs, KINDS[kind][1], rules), "seasons": seasons, "popularity": popularity,
        "price": price, "brand": row.get("brand") or DEFAULT_BRAND, "is_new": is_new in TRUE_WORDS,
        "description": describe(kind, attrs), "image_file": f"{item_id}.jpg",
        "colors": (row.get("colors") or "").split() or demo_colors(attrs),
        "data_source": "etri",   # 앱이 상품 상세 하단에 ETRI 출처 문구를 보여 주는 표시
    }


def build(rows: list[dict], sources: dict, image_dir: str, rules) -> tuple[list[dict], list[str]]:
    """→ (상품 목록, 오류 목록). 오류는 줄 번호와 함께 모두 모은다."""
    items, errors, seen = [], [], set()
    for number, raw in enumerate(rows, start=2):      # 1번 줄은 열 이름
        row = {k.strip(): (v or "").strip() for k, v in raw.items() if k}
        item_id = row.get("product_id", "")
        if not item_id:
            if any(row.values()):
                errors.append(f"{number}번 줄: product_id가 비었습니다")
            continue
        if item_id in seen:
            errors.append(f"{number}번 줄 {item_id}: 같은 product_id가 두 번 있습니다")
            continue
        seen.add(item_id)
        if item_id not in sources:
            errors.append(f"{number}번 줄 {item_id}: 속성이 없거나 의류가 아닌 id입니다")
            continue
        if not os.path.isfile(os.path.join(image_dir, f"{item_id}.jpg")):
            errors.append(f"{number}번 줄 {item_id}: 사진 파일이 없습니다")
            continue
        try:
            items.append(build_item(row, sources[item_id], rules))
        except ValueError as e:
            errors.append(f"{number}번 줄 {item_id}: {e}")
    return items, errors


def main(argv: list[str]) -> int:
    if len(argv) != 5:
        print("사용법: python tools/build_demo_items.py <상품 목록.csv> <정리한 TSV> <사진 폴더> <저장할 products.json>",
              file=sys.stderr)
        return 2
    list_path, tsv_path, image_dir, out_path = argv[1:]
    out = Path(out_path).resolve()
    if out.is_relative_to(ROOT):
        print("ETRI 데이터는 저장소에 넣지 않습니다. 저장소 밖 경로를 지정해 주십시오", file=sys.stderr)
        return 2
    with open(list_path, encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    items, errors = build(rows, load_items(tsv_path), image_dir, matching.load_rules())
    if errors or not items:
        print("\n".join(errors) or "상품이 한 개도 없습니다", file=sys.stderr)
        print("파일을 만들지 않았습니다", file=sys.stderr)
        return 1
    try:
        # 상품 파일과 사진 폴더를 함께 옮겨도 되게 상대 경로로 적는다 (드라이브가 다르면 전체 경로)
        stored_dir = os.path.relpath(Path(image_dir).resolve(), out.parent)
    except ValueError:
        stored_dir = str(Path(image_dir).resolve())
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"image_dir": stored_dir, "items": items}, f, ensure_ascii=False, indent=2)
    by_type = {}
    for item in items:
        by_type[item["clothing_type"]] = by_type.get(item["clothing_type"], 0) + 1
    print(f"상품 {len(items)}개를 저장했습니다: {out}")
    print("  종류별: " + ", ".join(f"{k} {v}" for k, v in sorted(by_type.items())))
    names = [i["product_name"] for i in items]
    repeated = sorted({n for n in names if names.count(n) > 1})
    if repeated:
        print(f"  같은 상품명 {len(repeated)}종: {', '.join(repeated)} (product_name 열로 바꿀 수 있습니다)")
    untagged = [i["product_id"] for i in items if not i["tags"]]
    if untagged:
        print(f"  태그가 없는 상품 {len(untagged)}개: {', '.join(untagged)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
