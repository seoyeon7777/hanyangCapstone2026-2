# 상용 피팅 아바타 조달 요청서 (RFQ)

## 배경

Android 가상피팅 앱(금박이주방)에 넣을 **여성 패션 아바타**가 필요합니다.  
현재 MakeHuman 기반 GLB는 프로토타입용이며 **상용 UI 품질 미달**로 교체합니다.

## 납품물

1. `avatar.glb` — Filament(gltfio) 로드, 텍스처 포함, morph/blendshape 포함  
2. morph 이름 목록 (텍스트)  
3. 라이선스 문서 (앱·상용 배포 가능)  
4. (권장) Blender `.blend` 소스

## 필수 morph (1차)

논리 이름(앱): `CHEST`, `UNDERBUST`, `WAIST`, `HIP`, `SHOULDER`, `THIGH`, `BODY_SLIM`, `BODY_HEAVY`  
→ glTF target 이름이 달라도 됨. 매핑표 제공.

`CHEST`는 **컵/가슴 실루엣** 위주. 갈비통만 키우는 둘레 스케일 단독은 불가.

## 품질

- 패션/이커머스 피팅 톤 (헤어·얼굴·기본 의상)
- morph `|w|≤0.5`에서 메쉬·옷 붕괴 없음
- 소냐도르 사이즈표 44~77 핵심 둘레(가슴·허리·엉덩이·허벅지)를 슬라이더로 표현 가능할 정도의 스팬

상세: [AVATAR_ASSET_CHECKLIST.md](AVATAR_ASSET_CHECKLIST.md)

## 후보 확보 경로

| 경로 | 비고 |
|------|------|
| 상용 마켓 / 피팅 SDK 바디 | morph export·라이선스 필수 확인 |
| Character Creator 등 | glTF morph export 파이프라인 검증 후 |
| 팀원 4–5 · 외주 | 위 RFQ로 제작 |

## 납품 위치

```text
incoming/avatar_candidates/<이름>.glb
incoming/avatar_candidates/<이름>_license.txt
```

검수 PASS 후 엔지니어가 `app/src/main/assets/models/avatar.glb`로 반영.
