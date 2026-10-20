# 상용 아바타 GLB 수락 기준

목표: 금박이주방 **상용 피팅** 품질. MakeHuman/`female_generic` 프로토타입은 **탈락**.

후보 파일을 Blender에서 연 뒤 아래를 **전부 PASS**해야 `app/src/main/assets/models/avatar.glb`로 교체한다.  
자동 검사: `tmp_qa_avatar_glb.py` (Blender 4.4+).

## 1. 포맷 · 로드

| 항목 | 기준 |
|------|------|
| 확장자 | `.glb` (권장) 또는 glTF + bin |
| Filament | Android gltfio로 로드 가능, 텍스처 임베드 |
| 폴리곤 | 모바일 실시간 가능 (대략 body+clothes ≤ ~100k tris 권장) |

## 2. Morph (필수)

앱 논리 이름 ↔ glTF morph 이름은 [`morph_target_map.json`](../app/src/main/assets/models/morph_target_map.json)으로 연결한다.

### 1차 최소 (소냐도르 44~77)

| 논리 이름 | 의미 | 비고 |
|-----------|------|------|
| `CHEST` | 윗가슴 / 컵 실루엣 | 흉곽만 키우는 measure-bust 단독 금지 |
| `UNDERBUST` | 밑가슴 / 밴드 | |
| `WAIST` | 허리둘레 | |
| `HIP` | 엉덩이둘레 | |
| `SHOULDER` | 어깨너비 | |
| `THIGH` | 허벅지둘레 | |
| `BODY_SLIM` | 마름 macro | 또는 동등 이름 → map |
| `BODY_HEAVY` | 통통 macro | 또는 동등 이름 → map |

### 2차 (기존 UI 전 부위)

`NECK`, `NECK_LENGTH`, `BACK_LENGTH`, `UPPER_ARM`, `UPPER_ARM_LENGTH`, `FOREARM`, `FOREARM_LENGTH`, `PELVIS`, `RISE`, `THIGH_LENGTH`, `CALF`, `CALF_LENGTH`

- 모든 morph 메시(몸·옷·헤어)는 **동일 target 이름·동일 순서**를 권장 (앱이 이름으로 인덱스를 잡음).
- `|w| ≤ 0.5`에서 피부 함몰·옷 관통·노멀 뒤집힘 **없음** (스크린샷 검수).

## 3. 룩 (상용)

| 항목 | 기준 |
|------|------|
| 톤 | 패션/마네킹. 민머리·해부학 데모 탈락 |
| 헤어 | 있거나 깔끔한 캡/번 |
| 얼굴 | 소켓 붕괴·이/혀 노출 없음 |
| 기본 의상 | 속옷 또는 심플 탑/하의 (텍스처 정상) |
| 재질 | PBR 텍스처, 금속 1.0 빈 재질 금지 |

## 4. 라이선스

- 앱 패키징 · 캡스톤 발표 · **향후 상용 배포** 가능 문구 확인
- 출처·인보이스·라이선스 PDF를 `docs/licenses/`에 보관
- DAZ 등 재배포 제한 에셋은 별도 계약 없이 사용 금지

## 5. 교체 절차

1. 후보를 `incoming/avatar_candidates/`에 둠  
2. `Blender --background --python tmp_qa_avatar_glb.py -- <path.glb>`  
3. PASS면 `avatar.glb` 덮어쓰기 + `morph_target_map.json` 이름 맞추기  
4. `tmp_build_tape_cm_lut.py`로 LUT 재생성  
5. 앱 빌드 → 소냐도르 44/55/66/77 시각 QA (`tmp_qa_sonya_dor_spans.py`)

## 6. MakeHuman 정책

- `tmp_bake_female_generic_morph.py`는 **레거시/참고만**. 상용 퀄리티용 bake 금지.
- 신규 개선은 패션 바디 morph 설계(구매·외주·팀원 4–5)로만 진행.
