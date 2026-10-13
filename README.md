# B. 옷 매칭

`matching.py`가 태그 붙이기, 체형 적합도, 추천 이유, 코디 점수를 담당한다. 설계는 팀 알고리즘 PDF 기준.

## 구조

```
matching.py        함수 모음 (load_rules, tag_item, tag_from_attributes, body_fit, reasons, outfit_score, match_item, recommend)
data/
  tag_weight.json  태그 x S/W/N 가중치 (현재 PDF 임시값, src 열에 근거를 적는다)
  keywords.json    상품명 키워드 -> 태그
  materials.json   혼용률 문구에서 섬유 읽는 규칙 (점수는 material_min_share 이상인 섬유만)
  reasons.json     태그 x 유형 -> 추천 이유 문장
  config.json      AVOID 처리 방식·기준, 이유 개수, 코디 후보 수
  candidates.json  후보 태그 26개(키워드, 어휘 근거, 가중치 근거 상태)
samples/           손으로 만든 샘플 상품, product_names.csv(가상 상품명)
tools/coverage.py  후보 태그가 상품명에 얼마나 붙는지 점검
tools/build_tags.py 시연 상품 36개의 태그·혼용률을 다시 만든다 (samples/demo_tags.json)
tests/             pytest (PDF 임시 가중치를 fixtures에 고정)
demo.py            샘플 상품에 돌려 보기
server.py          등록 -> 검증 -> 태그 -> 점수·이유 HTTP 모듈 (Flask, 앱이 붙이는 층)
tools/sensitivity.py 가중치 민감도 분석, tools/review_demo.py 리뷰 보정 전후 순위
```

## 실행

```
pip install pytest
pytest
python demo.py
python server.py                      # http://127.0.0.1:5000  (GET /api/form, POST /api/items/validate · /api/items · /api/recommend)
python tools/coverage.py [상품명.csv]   # 실제 상품명 CSV(category,name)로 바꿔서 다시 돌린다
```

## 입출력

입력
- `profile = {"ratios": {"S":.61,"W":.17,"N":.22}, "primary":"S", "secondary":"N", "is_mixed":False}` (A의 분류 결과)
- 상품 `{item_id, category, name, desc?, tags?, source?, attrs?}`. `category`는 top | bottom | outer | pants | skirt | dress.
  - `source` 없음(가져온 상품): `name`/`desc`에서 태그를 만든다. **평가용 경로다.** 다른 쇼핑몰 상품을 수집해 서비스에서 추천하는 것은 약관·저작권 문제가 있어 서비스에 넣지 않는다. `server.py`는 이런 상품(source도 tags도 없음)을 400으로 거절한다. 이미지 글자 읽기(`tools/vision_experiment.py`)도 평가용 도구다.
  - `source="direct"`(판매자 직접 등록) 또는 `"manual"`(팀이 외부 상품을 같은 양식으로 입력): `attrs`(등록 양식)가 우선한다. 양식 검증을 통과하지 못하면 `ValueError`. 양식에서 미입력인 선택 항목(실루엣·허리·디테일·소재·종류)만 `name`/`desc`에서 보충하고("해당 없음"을 고른 항목은 보충하지 않는다), 보충한 태그는 `text_added`에 따로 적는다. 핏·기장·혼용률은 글에서 보충하지 않는다.
  - `tags`를 직접 주면 그대로 쓴다.
  - `review = {"n": 답한 리뷰 수, "mismatch": 등록 정보와 다르다고 한 수}`(선택): `review_agreement = (n - mismatch + k0) / (n + k0)`(k0 = `review_prior_k` 3, 설계 가정)를 출력에 싣는다. `config.review_mode`가 `off`(기본)면 점수에 반영하지 않고, `both`·`positive_only`로 켜면 점수를 중립 쪽으로 당긴다(실험 기능, `tools/review_demo.py`). 리뷰 수집 화면은 앱 쪽 일이다.

출력: `recommend(profile, items)` -> `{"items": [...], "outfits": [...], "fit_unknown": [...]}`
- `ranking`: 카테고리 묶음(top / bottom / outer)별 추천 상품 `list_size`개. 혼합형은 `config.mixed_strategy`(blend | quota | quota_safe, 기본 blend) 또는 요청의 `mixed_strategy`로 방식을 고른다. 비교는 `tools/mixed_compare.py`.
- `items[*]`: `{item_id, category, tags, text_added, review_agreement, fit_stated, body_fit, excluded, reasons}`
  - `reasons`: 혼합 점수에 긍정적으로 기여한 태그의 문장을 큰 순서로 `reason_max`개까지 낸다. 태그 기여 = Σ 유형 비율 x 가중치이고, 기여가 0 이하인 태그는 설명하지 않는다. 단일형·혼합형 구분 없이 같은 방식이다(`is_mixed`를 보지 않는다).
- `fit_stated`: slim | regular | over | unknown. 레귤러와 정보 없음을 나눠 저장한다. 점수 태그는 slim·over만 만든다.
- `outfits[*]`: `{top, bottom, outer, score, balance}` (원피스는 코디 짝이 없어 상품 점수만 낸다)
  - 코디 밸런스 규칙은 `config.balance_scope`로 적용 범위를 고른다. `ratio`(기본)는 `is_mixed`와 무관하게 세 유형의 비율대로 가중하고, `flag`(이전 방식)는 단일형이면 1위 유형만, 혼합형이면 1·2위 유형만 비율대로 적용한다. `flag`는 `is_mixed` 경계에서 보정값이 끊긴다. 비교는 `tools/balance_compare.py`.
- `fit_unknown`: 핏 표기를 못 읽은 상품. 추천 문구는 점수가 아니라 이유 문장만 사용자에게 보인다.

## 규칙을 바꿀 때

- 가중치 최솟값·최댓값은 `tag_weight.json`에서 계산한다. 숫자 범위를 바꿔도 코드는 그대로다.
- 바꾼 뒤 `matching.validate_rules(matching.load_rules())`가 빈 리스트인지 확인한다.
- 표에 없는 태그는 점수와 태그 수 계산에서 제외한다. 태그가 하나도 없으면 중립값이 나온다.

## 알려진 한계

- 외부 상품 글에 핏 표기가 없으면 읽을 수 없다(`fit_stated="unknown"`). 직접 등록·팀 입력 양식이 이를 피하는 경로다.
- 같은 세트에서 규칙을 고친 뒤 확인한 결과이고 독립 검증이 아니다. 자세한 수치는 `reports/태깅 검증 결과.md`.
- 점수에 쓰는 태그 26종 중 근거가 약한 가중치의 크기는 설계 가정이다(`reports/골격진단 가중치 근거 조사.md`).
