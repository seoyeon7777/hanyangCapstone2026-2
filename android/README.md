# avatar_input_info

캡스톤 **금박이주방** — 3D 아바타 기반 가상피팅을 위한 Android 클라이언트  
(신체 입력 · 아바타 미리보기 · 부위별 치수 조정)

패키지: `com.example.capstone`  
minSdk 24 · Java 11 · Filament(gltfio) · OpenGL ES 3.0

**2026-09 기준 한 줄:**  
MakeHuman bake는 중단. 현재 `avatar.glb`는 **SMPL-X female 임시 연동**(안드 스모크 테스트용).  
앱은 morph **이름 매핑** + 줄자 LUT + 소냐도르 목표 cm로 슬라이더를 돌린다. 사진→AI 추정은 팀원 담당(미연동).

---

## 1. 프로젝트 목표

온라인 의류 구매 시 사이즈·핏 불확실성을 줄이기 위해:

1. 전신 사진/영상 + 키로 기본 아바타를 세팅하고  
2. 사용자가 부위별 치수를 확인하고 보정하며  
3. (이후) 의류 3D를 입혀 핏을 확인한다.

차별점: **부위 터치 → 줌 → 슬라이더/직접입력**으로 AI 오차를 사용자가 보정.

역할(개발계획서 기준):

| 담당 | 내용 |
|------|------|
| 팀원 1, 2 | 전용 촬영 · AI 신체 치수 추정 · 아바타 자동 생성 연동 |
| **이 저장소(팀원 3 쪽)** | Filament 아바타 · 360° · 부위별 조정 UI/로직 |
| 팀원 4, 5 | 의류 3D 모델링 자동화 |

---

## 2. 서비스 / 앱 흐름

```
쇼핑몰(Main / Wish / Cart / Login…)
        │  하단 「피팅」
        ▼
FittingActivity     키 + 전신 사진(정·측·후) 또는 영상
        │  시작 / 아바타 만들기
        ▼
AvatarPreviewActivity
        · 키 스케일
        · 체형 마름 / 표준 / 통통
        · Region 칩 · 부위 슬라이더 · 연동
        · (이후) 의류 착용
```

사진·영상 → **AI 치수 추정**은 아직 연결되어 있지 않다.  
현재는 키(+수동 치수)로 공통 `avatar.glb` morph를 돌린다.  
설계상 AI는 **새 GLB를 매번 만들기보다**, 추정 cm를 이 아바타에 넣는 쪽이 맞다.

---

## 3. 지금까지 개발 내용

### 3.1 앱 UI / 쇼핑몰 연동

- 하단 네비: 홈 / 찜 / 피팅 / 장바구니 / 마이
- `FittingActivity`: 키, 사진·영상 입력 → `AvatarPreviewActivity` (`extra_height_cm`)
- 팀원 로그인·마이페이지·쿠폰·문의 등 화면 일부 병합 (전량 덮어쓰기 아님)
- Filament `ModelViewer`로 `assets/models/avatar.glb` 렌더, 드래그 오빗

### 3.2 아바타 morph 파이프라인

**정책 (상용):** MakeHuman/`female_generic` 미세 bake는 **중단**.  
최종 목표는 [docs/AVATAR_ASSET_CHECKLIST.md](docs/AVATAR_ASSET_CHECKLIST.md) PASS인 **패션 morph GLB**.  
조달: [docs/AVATAR_ACQUISITION.md](docs/AVATAR_ACQUISITION.md) · 후보: `incoming/avatar_candidates/`.  
진행 상태: [docs/AVATAR_SWAP_STATUS.md](docs/AVATAR_SWAP_STATUS.md).

**현재 런타임 바디 (임시):** SMPL-X female → `tmp_export_smplx_to_app.py`로 `avatar.glb` 반영.  
morph는 `Shape000`–`Shape019`(PCA). `morph_target_map.json`은 BodyPart→Shape **임시 매핑**(해부학적으로 정확하지 않음).  
줄자 LUT는 **아직 MH 시절 값일 수 있음** → 새 GLB 기준 재생성 필요. 룩(텍스처·옷·헤어) 폴리시는 파이프라인 검증 후.

| 항목 | 내용 |
|------|------|
| 런타임 | Filament `setMorphWeights` — **이름 기반** 인덱스 (`MorphTargetMap`) |
| 이름 매핑 | `assets/models/morph_target_map.json` (SMPL-X Shape 임시) |
| 줄자 LUT | `tmp_build_tape_cm_lut.py` → `morph_cm_lut.json` (**재생성 대기**) |
| SMPL-X export | `tmp_export_smplx_to_app.py` → `avatar.glb` + blend `incoming/avatar_candidates/smplx_female_export.blend` |
| QA | `tmp_qa_avatar_glb.py`, `tmp_qa_sonya_dor_spans.py` |
| 레거시 bake | `tmp_bake_female_generic_morph.py` (**deprecated**, 상용 금지) |

**BodyPart (슬라이더) 17개** + `BODY_SLIM` / `BODY_HEAVY`

| Region | 치수 |
|--------|------|
| 목 | 목둘레, 목길이 |
| 어깨 | 어깨너비 |
| 가슴·등 | 윗가슴둘레, 밑가슴둘레, 등길이 |
| 허리 | 허리둘레 |
| 상완 | 상완둘레, 상완길이 |
| 팔뚝 | 팔뚝둘레, 팔뚝길이 |
| 골반 | 골반너비 |
| 엉덩이 | 엉덩이둘레, 밑위 |
| 허벅지 | 허벅지둘레, 허벅지길이 |
| 종아리 | 종아리둘레, 종아리길이 |

**체형 프리셋:** `BODY_SLIM` / `BODY_HEAVY` morph (이름 매핑) → 마름 / 표준 / 통통.

### 3.3 cm ↔ morph weight

- 표시 cm ≈ **인체비율 기본값(키×ratio)** × **줄자 비율(tape(w)/tape(0))**
- `MorphCmLut`가 `morph_cm_lut.json` 로드
- 슬라이더는 **안전한 |w|만** 노출 (끝단 메쉬 함몰 방지, 둘레 대략 ≤0.55)
- 기본 모습은 **w=0(원본)** = 슬라이더 중앙 근처

### 3.4 부위 연동 (2026-09)

허벅지만 움직이면 안쪽만 파이는 비현실성을 줄이기 위해, **인접 둘레를 약하게 같이 이동**.

- 데이터: AI Hub 「한국인 전신 형상 및 치수 측정 데이터(성인)」 **신체정보 CSV** (여성 500명)
- 방법: 키 통제(residual) OLS → 감쇠 0.45 · |scale|≤0.45 · 인접 부위만
- 자산: `part_coupling.json` · 로더 `PartCoupling.java`
- 예: 허벅지 +1 cm → 엉덩이 ≈ +0.39, 종아리 ≈ +0.16

원본 이미지·3D zip(수백 GB)은 앱에 넣지 않음. **치수 CSV만** 분석에 사용.

### 3.5 기준 사이즈표 (소냐도르 표준표)

피팅 목표 cm 레인지는 **소냐도르(Sonya Dor) 사이즈표**를 참고한다.  
상의 수치는 옷 실측(여유분 포함)일 수 있어 아바타 신체 cm와 1:1은 아니다.  
1차 목표는 **44~77(가슴·허리·엉덩이)** , 2차로 **88~99 가슴**까지 확장.

**상의 (호칭 44=XS … 99=XXL)**

| 사이즈 | 어깨단면 | 소매길이 | 가슴둘레 | 총장 |
|--------|----------|----------|----------|------|
| 44 (XS/85) | 35 | 54 | **85** | 60 |
| 55 (S/90) | 36 | 55 | 90 | 61 |
| 66 (M/95) | 37 | 56 | 95 | 62 |
| 77 (L/100) | 38 | 57 | **100** | 63 |
| 88 (XL/105) | 39 | 58 | 105 | 64 |
| 99 (XXL/110) | 40 | 59 | **110** | 65 |

**하의 (호칭 44~77, 표기 cm 구간)**

| 사이즈 | 허리 | 엉덩이 | 허벅지 | 기장 |
|--------|------|--------|--------|------|
| 44 | 61~64 | 86~88 | 50.5~52 | 88~89 |
| 55 | 66~69 | 90~92 | 54~55.5 | 90~91 |
| 66 | 71~74 | 96~98 | 58~59.5 | 92~93 |
| 77 | 76~79 | 100~102 | 62.5~64 | 94~95 |

아바타 슬라이더 목표(신체 근사):

| 부위 | 표 기준 min~max | 비고 |
|------|-----------------|------|
| 윗가슴 | 85~110 (1차 85~100) | 컵 위주·**약한 amp**(깨짐 방지). 큰 컵 Exact는 커스텀 메시/옷 재토폴 필요 |
| 허리 | 61~79 | 현재 안전 구간은 중간~큰 쪽 위주 |
| 엉덩이 | 86~102 | 비교적 근접 |
| 어깨 | 35~40 | 비교적 근접 |
| 허벅지 | ~50~64 | 큰 사이즈 쪽 확장 필요 |

### 3.6 알려진 한계

- **현재 assets의 MakeHuman GLB는 상용 품질 미달** → 패션 바디 교체 필수 (체크리스트 FAIL이 정상)
- morph 안전 범위 안에서는 **극단 체형** 표현 제한
- MakeHuman measure ≠ 실측 cm 1:1 (레거시 LUT만 해당)
- 소냐도르 표 Exact는 **설계된 morph 스팬**이 있는 새 바디에서 재검증
- 사진 AI → cm 자동 주입은 미연동

---

## 4. 주요 파일

| 경로 | 역할 |
|------|------|
| `ui/FittingActivity.java` | 키·사진 입력 |
| `ui/AvatarPreviewActivity.java` | 3D, 체형, 슬라이더, 연동 |
| `avatar/MorphCmLut.java` | cm ↔ weight LUT |
| `avatar/MorphTargetMap.java` | 논리 morph 이름 ↔ glTF 이름 |
| `assets/models/avatar.glb` | 현재 SMPL-X female 임시 (상용 룩 아님) |
| `assets/models/morph_target_map.json` | BodyPart ↔ glTF morph 이름 (임시 Shape PCA) |
| `assets/models/morph_cm_lut.json` | 줄자 실측 LUT (SMPL-X 기준 재생성 필요) |
| `assets/models/part_coupling.json` | 연동 비율 |
| `docs/AVATAR_ASSET_CHECKLIST.md` | 상용 GLB 수락 기준 |
| `docs/AVATAR_ACQUISITION.md` | 조달 RFQ |
| `tmp_build_tape_cm_lut.py` | LUT 빌드 |
| `tmp_qa_avatar_glb.py` | 후보 GLB QA |
| `tmp_qa_sonya_dor_spans.py` | 소냐도르 커버 리포트 |
| `tmp_bake_female_generic_morph.py` | **deprecated** MH bake |
| `login/*` | 팀원 로그인·마이 등 (일부) |

Blender 원본 `.blend`·대용량 AI Hub 원천데이터는 **저장소 밖** (`C:\DataSet\Capstone\데이터\…`).

---

## 5. 빌드 / 실행

1. Android Studio Sync  
2. Run → 하단 **피팅** → 키 입력 → 아바타  
3. 미리보기: 체형 칩, 부위 칩, 슬라이더  

미리보기만: `AvatarPreviewActivity` + `extra_height_cm` (없으면 prefs / 170 cm).

Bake (참고):

```text
Blender 4.4 + standard_model.blend
  --python tmp_bake_female_generic_morph.py
Blender --background --python tmp_build_tape_cm_lut.py
```

---

## 6. 참고 자료 · 크레딧

### 데이터

- **한국지능정보사회진흥원(NIA) AI Hub**  
  [한국인 전신 형상 및 치수 측정 데이터(성인)](https://aihub.or.kr/aihubdata/data/view.do?dataSetSn=560)  
  - 용도: 부위 상관·연동 비율 참고(학습/분석). 원본 mesh·이미지를 앱 자산으로 배포하지 않음.  
  - 2차 결과물·발표 시 NIA AI Hub 사업 결과임을 표기.
- (참고) AI Hub 의류 가상착용 3D, 한국인 신체 3D 스캐닝 등 — 촬영·인식 팀 후보
- 소냐도르(Sonya Dor) 사이즈표 — 피팅 목표 cm 레퍼런스(상의는 옷 실측일 수 있음)

### 툴 · 에셋

- [MakeHuman Community](http://www.makehumancommunity.org/) / [MPFB2](https://github.com/makehumancommunity/mpfb2) — 인체 베이스·measure/macro target (CC0 타깃 포함)
- [Google Filament](https://github.com/google/filament) — Android glTF 렌더
- Mixamo 본 이름 규약 (스케일 본 매핑용; 현재 주 변형은 morph)

### 제품 · 벤치마크 (계획서)

- 피팅북, Style.me, FittingBox, MeThreeSixty, SUL:FIT, StyleChain 등  
- 차별: 부위별 터치·줌·수동 치수 보정

### 관련 연구 (참고)

- K-SMPL 등 Size Korea 기반 parametric body 연구 — 한국인 치수·SMPL 계열 참고용  
- 현재 실험: **SMPL-X** shape key를 glTF morph로 넣어 Filament에서 구동 (beta 직접 API가 아니라 로컬 morph 이름 매핑)

---

## 7. 라이선스 · 주의

- AI Hub 데이터: **학습·분석용**. 원본 재배포·판매 금지. 앱에는 통계에서 뽑은 연동 비율만 포함.
- MakeHuman/MPFB 타깃·베이스: 각 프로젝트 라이선스(다수 CC0) 확인.
- 상업 배포 시 DAZ 등 상용 에셋은 별도 라이선스.

---

## 8. 다음에 할 일 (후보)

- [x] MakeHuman 상용 bake 중단 · 수락 기준/조달 문서 · 이름 매핑
- [x] SMPL-X female → `avatar.glb` 임시 연동 (안드 스모크 테스트)
- [ ] Android에서 스케일·카메라·슬라이더 동작 확인
- [ ] SMPL-X용 **부위별 morph bake** + `morph_target_map.json` 정식화 (임시 Shape PCA 폐기)
- [ ] `morph_cm_lut.json` 재생성 · 소냐도르 44~77 시각 QA
- [ ] 룩 폴리시(텍스처·기본 의상·헤어) 또는 패션 morph GLB로 교체
- [ ] 팀원 AI 추정 cm → `AvatarPreview` / `PartCoupling` 스키마 연동
- [ ] 의류 GLB 착용 · 카테고리 확장
- [ ] 실측 소수 표본으로 예측-실측 MAE 검증

---

## 9. Git / 환경

- `.gitignore`: `.gradle/`, `build/`, `.idea/`, `local.properties`, 키스토어  
- 대용량 AI Hub zip·원본 blend는 커밋하지 않음  
- Wrapper·소스·`assets/models/*`만 맞으면 머신 간 이어서 작업 가능
