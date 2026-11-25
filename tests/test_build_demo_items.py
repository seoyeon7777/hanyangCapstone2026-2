"""tools/build_demo_items.py: 고른 상품 목록 → 서버용 상품 파일 (데이터셋 없이 가짜 속성으로 확인)"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))

import build_demo_items as tool  # noqa: E402

ROWS = ["BL-001\tB\tBL\tF\t여유로운 핏", "BL-001\tB\tBL\tF\t러플 장식", "BL-001\tB\tBL\tM\t신축성 없음"]


def prepare(tmp_path, csv_text):
    (tmp_path / "meta.tsv").write_text("\n".join(ROWS) + "\n", encoding="utf-8")
    (tmp_path / "img").mkdir()
    (tmp_path / "img" / "BL-001.jpg").write_bytes(b"photo")
    (tmp_path / "list.csv").write_text(csv_text, encoding="utf-8-sig")
    out = tmp_path / "out" / "products.json"
    code = tool.main(["x", str(tmp_path / "list.csv"), str(tmp_path / "meta.tsv"), str(tmp_path / "img"), str(out)])
    return code, out


def test_builds_server_items(tmp_path):
    code, out = prepare(tmp_path, 'product_id,price,popularity,is_new\nBL-001,"32,000",0.8,1\n')
    assert code == 0
    data = json.loads(out.read_text(encoding="utf-8"))
    assert Path(out.parent, data["image_dir"]).resolve() == (tmp_path / "img").resolve()
    item = data["items"][0]
    assert (item["product_id"], item["clothing_type"], item["image_file"]) == ("BL-001", "top", "BL-001.jpg")
    assert (item["price"], item["popularity"], item["is_new"], item["brand"]) == (32000, 0.8, True, "시연용 브랜드")
    assert item["seasons"] == ["spring", "summer", "fall", "winter"]
    assert item["data_source"] == "etri"
    assert item["colors"] == ["블랙", "화이트", "그레이", "네이비"]   # 색 속성이 없으면 임시 색 4가지
    assert "det:ruffle" in item["tags"] and item["description"].startswith("블라우스예요.")


def test_temp_colors_follow_the_photo_color():
    assert tool.demo_colors({"C": ["하늘색"]}) == ["하늘색", "블랙", "화이트", "그레이"]
    assert tool.demo_colors({"C": ["화이트"]}) == ["화이트", "블랙", "그레이", "네이비"]   # 같은 색은 한 번만


def test_all_errors_reported_and_nothing_written(tmp_path, capsys):
    code, out = prepare(tmp_path, "product_id,price\nBL-001,0\nBL-001,1000\nZZ-9,1000\n")
    assert code == 1 and not out.exists()
    printed = capsys.readouterr().err
    assert "2번 줄 BL-001: price" in printed and "3번 줄 BL-001: 같은" in printed and "4번 줄 ZZ-9" in printed


def test_refuses_to_write_inside_repository():
    inside = tool.ROOT / "recommend" / "data" / "products.json"
    assert tool.main(["x", "list.csv", "meta.tsv", "img", str(inside)]) == 2
    assert not inside.exists()
