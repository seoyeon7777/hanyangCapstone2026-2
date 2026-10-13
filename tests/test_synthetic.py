"""가상 판매자 샘플 100개(samples/synthetic_sellers.json)가 설계대로 등록·거절되는지 고정한다. 규칙을 바꿔서 어긋나면 의도한 변경인지 확인할 것.
기대값은 설계 문서에서 정한 것이라 실제 상품의 정확도를 뜻하지 않는다."""
import json
from pathlib import Path

import matching
import server
from tools import run_synthetic


def test_synthetic_sellers_match_expectations(capsys):
    assert run_synthetic.main() == 0, capsys.readouterr().out


def test_synthetic_sample_is_100_items_in_11_groups():
    data = json.load(open(Path(__file__).parent.parent / "samples" / "synthetic_sellers.json", encoding="utf-8"))["items"]
    assert len(data) == 100 and len({d["group"] for d in data}) == 11
