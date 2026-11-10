# 길 위의 캡슐

> 같은 길, 같은 방향, 같은 시간과 날씨에만 열리는 음성 캡슐. 드라이브판 포켓몬고.
>

## 1. 핵심 MVP

### 15일 전까지 구현
1. 운전자 A가 특정 도로에서 **음성으로** 캡슐을 만든다
2. 캡슐에 **위치·방향·시간대·차량 상태**가 함께 저장된다
3. 운전자 B가 **같은 방향으로** 그 도로를 지나간다
4. 지금 차량 상태와 캡슐 조건이 **맞는지** 판단한다
5. 맞으면 캡슐이 열리고 **음성이 재생**된다
6. 열어본 캡슐이 B의 **도감**에 수집된다

## 2. 기능 우선순위

### P0: 이번 주 반드시 구현

| ID | 기능 | MVP 구현 범위 |
|---|---|---|
| F01 | 캡슐 생성 | 버튼 1탭 → STT로 말 입력 → TTS로 확인 → 등록 / 다시 / 취소 |
| F02 | 차량 상태 수집 | 위치, 진행 방향, 속도, 기어, 와이퍼 |
| F03 | 구간 진입 감지 | 캡슐 반경 안 + 진행 방향 일치 |
| F04 | 조건 매칭 | 위치 + 방향 + 시간대 + 차량 상태. **4개 모두 맞아야 개봉** |
| F05 | 캡슐 발견·재생 | 4개 충족 → "캡슐이 있어요" → TTS 재생 + 자막 |
| F06 | 캡슐 수집 | 열어본 캡슐을 도감에 저장 |
| F07 | 기본 안전 제어 | 주행 중 터치는 시작 버튼 1번만, 나머지는 음성. 중복 재생 방지, 한 번에 하나만 |


### P1: P0 완성 후

| 기능 | 범위 |
|---|---|
| 희귀도 | 조건이 드문 정도로 일반 / 레어 / 에픽 / 레전드 (아래 기준). `Capsule.rarity` 필드 추가 |
| 차량 환경 재현 | 공조 온도 프리셋 1종 |
| 흔적 발견 | 위치·방향 + 시간 또는 차량 중 3개 충족 → "비 오는 밤에만 열리는 캡슐이 있어요" 힌트, 도감에 실루엣 등록 |
| 차가 먼저 제안 | 조건이 좋은 구간에서 "여기 캡슐 남길까요?" |
| 좋아요 | 캡슐 평가 |
| 서버 동기화 | 다른 사용자와 캡슐 공유 (`data/remote`) |

---

#### 희귀도 기준 (P1 구현 시 사용)
희귀도는 **그 조건이 실제로 얼마나 드문지**로 정한다. 캡슐을 만들 때 계산해서 저장한다.

| 시간대 | 점수 | 차량 상태 (와이퍼) | 점수 |
|---|---|---|---|
| 낮 | 0 | 맑음 (OFF) | 0 |
| 밤 (19~04시) | 1 | 비 (AUTO / LOW) | 1 |
| 새벽 (04~06시) | 2 | 폭우 (HIGH) | 2 |

| 합계 | 희귀도 | 예시 |
|---|---|---|
| 0 | 일반 | 맑은 낮 |
| 1 | 레어 | 맑은 밤, 비 오는 낮 |
| 2~3 | 에픽 | 비 오는 밤, 폭우 내리는 밤 |
| 4 | 레전드 | 폭우 내리는 새벽 |

---

## 3. 핵심 플로우

### F01 캡슐 생성 (주행 중, 음성)
```
[캡슐 남기기] 버튼 1탭
  → 그 순간의 VehicleSnapshot 고정 (말하는 동안 이동해도 누른 지점이 기준)
  → 차: "남길 말을 해주세요"                         TTS
  → 사용자 발화 (최대 30초)                           STT
  → 차: "'○○○' 이렇게 남길까요? 등록, 다시, 취소"   TTS
      ├ 등록 → 저장 → "비 오는 밤의 해안도로에 캡슐을 남겼어요"
      ├ 다시 → 발화 단계로 (최대 2회)
      └ 취소 / 8초 무응답 → 저장 안 함
```
녹음 방식: **말 → 글자(STT) → 저장 → 열 때 TTS로 읽기.** 

### F03~F06 캡슐 발견 → 재생 → 수집
```
VehicleSnapshot 갱신 (1초마다)
  → 반경 안 + 같은 방향인 캡슐이 있나?            F03
  → 시간대·차량 상태까지 4개 모두 맞나?           F04
  → 이 사용자가 이미 수집한 캡슐인가? 재생 중인가?   F07
  → 차: "누군가 남긴 캡슐이 있어요"               F05
  → TTS 재생 + 자막
  → 도감에 수집                              F06
```

### 캡슐이 열리는 규칙 (F04)

**4개 조건이 모두 맞아야** 열린다.

| 조건 | 판정 | 데이터 출처 |
|---|---|---|
| 위치 | 캡슐 좌표에서 `radiusMeters` 안 | Pleos FusedLocation |
| 방향 | 캡슐 `headingDegrees` ±45° | Pleos FusedLocation |
| 시간대 | 캡슐 `timeCondition`과 같음 | 시스템 시계 |
| 차량 상태 | 캡슐 `vehicleCondition`과 같음 | **Pleos Vehicle SDK (Wiper)** |

예시: 비 오는 밤의 캡슐
```
위치  해안도로 지점 반경 150m
방향  동쪽(90°) ±45°
시간  야간
차량  와이퍼 작동
```
| 예시 상황 | 결과 |
|---|---|
| 같은 방향, 밤, 와이퍼 ON | **개봉** → 재생 → 수집 |
| 같은 방향, 맑은 밤 | 안 열림 |
| 같은 방향, 비 오는 낮 | 안 열림 |
| 반대 방향 | 안 열림 |
| 이미 수집한 캡슐 | 안 열림 |


### 안전 설계 (F07)
- 주행 중 화면 터치는 **[캡슐 남기기] 버튼 1번만**. 확인·등록은 음성
- 한 번에 캡슐 하나만 재생, 재생 중에는 새 캡슐을 발견하지 않음
- 이미 수집한 캡슐은 다시 열리지 않음
- 주행 중 화면은 상태 한 줄과 자막만. 도감은 P단에서만
---


## 4. 프로젝트 구조

단일 `app` 모듈 안에서 **패키지**로 나눈다. Gradle 모듈 분리는 이번 주에는 하지 않는다.

```
app/src/main/java/com/gilcap/app/
├─ core/                     ← 외부 세계(SDK)와 연결
│  ├─ model/                 공통 데이터 모델 (Capsule, VehicleSnapshot ...)
│  ├─ vehicle/               Pleos Vehicle SDK: 기어, 와이퍼
│  ├─ location/              Pleos FusedLocation: 위치, 방향, 속도
│  └─ audio/                 Pleos STT, TTS
├─ domain/                   ← 앱의 규칙 (Android 의존 없음, 단위 테스트 대상)
│  ├─ matching/              조건 매칭
│  └─ repository/            저장소 인터페이스
├─ data/                     ← 저장소 구현
│  ├─ local/                 MVP: 메모리 저장 (P1: Room)
│  └─ remote/                P1: 서버 동기화 (이번 주 비움)
├─ feature/                  ← 사용자 기능 단위 (흐름 + Compose 화면)
│  ├─ capsulecreate/         F01 음성 생성 흐름
│  ├─ capsulediscovery/      F03~F05 발견·재생
│  └─ collection/            F06 도감
├─ debug/                    ← 시연용 디버그 패널 (사용자 A/B 전환, 시간 바꾸기, 초기화)
└─ di/                       ← 조립 (어떤 구현을 쓸지 연결)
```

### 의존 방향
```
feature ──▶ domain ◀── data
   │          ▲
   └──▶ core ─┘ (core/model은 모두가 사용)
```
- `domain`은 Android를 모른다 → 조건 매칭을 JUnit으로 바로 테스트할 수 있다
- `feature`는 인터페이스만 알고, 실제 구현은 `di`에서 연결한다
---
## 5. 데이터 모델 (`core/model`)

### Capsule: 길 위에 남긴 캡슐

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | String | UUID |
| `authorId` | String | 만든 사용자. 자기 캡슐은 자기에게 안 열림 *(추가)* |
| `messageText` | String | STT로 받은 말. 열 때 TTS로 읽음 |
| `latitude` | Double | 생성 버튼을 누른 지점 |
| `longitude` | Double | |
| `radiusMeters` | Int | 열리는 반경. 기본 150 |
| `headingDegrees` | Float | 생성 순간 진행 방향 (0=북, 90=동). ±45° 허용 |
| `placeName` | String | 도감 이름용 "해안도로" *(추가, MVP는 미리 정한 지점 이름)* |
| `timeCondition` | TimeCondition | `DAY`, `NIGHT`, `DAWN` |
| `vehicleCondition` | VehicleCondition | `CLEAR`, `RAIN`, `HEAVY_RAIN` |
| `createdAt` | Long | epoch millis |

### VehicleSnapshot: 지금 차 상태 한 장

| 필드 | 타입 | 설명 |
|---|---|---|
| `latitude` | Double | |
| `longitude` | Double | |
| `headingDegrees` | Float | 진행 방향 |
| `speed` | Float | km/h |
| `gear` | Gear | `P`, `R`, `N`, `D`, `UNKNOWN` |
| `wiper` | WiperState | `OFF`, `AUTO`, `LOW`, `HIGH` *(추가: 차량 조건 판정에 필요)* |
| `timestamp` | Long | 스냅샷 시각 *(추가: 시간대 판정에 필요)* |

### CapsuleDiscovery: 도감 한 칸

| 필드 | 타입 | 설명 |
|---|---|---|
| `capsuleId` | String | |
| `userId` | String | 수집한 사용자 *(추가: A/B 도감 구분)* |
| `discoveredAt` | Long | |
| `matchedConditions` | Set\<MatchedCondition\> | 열릴 때 맞은 조건. `LOCATION`, `HEADING`, `TIME`, `VEHICLE` |

### 조건 값 → 판정 기준

| 값 | 판정 |
|---|---|
| `TimeCondition.DAY` | 06~19시 |
| `TimeCondition.NIGHT` | 19~04시 |
| `TimeCondition.DAWN` | 04~06시 |
| `VehicleCondition.CLEAR` | 와이퍼 OFF |
| `VehicleCondition.RAIN` | 와이퍼 AUTO 또는 LOW |
| `VehicleCondition.HEAVY_RAIN` | 와이퍼 HIGH |

캡슐을 만들 때 **그 순간의 시간대와 와이퍼 상태가 그대로 조건이 된다.**
 
---

## 6. 인터페이스

### core: SDK 연결 (구현은 각 담당)

| 인터페이스 | 패키지 | 제공 | SDK | 기능 |
|---|---|---|---|---|
| `VehicleStateSource` | core/vehicle | `gear`, `wiper`를 계속 갱신 (`StateFlow`) | Pleos Vehicle | F02 |
| `LocationSource` | core/location | 위치·방향·속도를 계속 갱신 (`StateFlow`) | Pleos FusedLocation | F02 |
| `VehicleSnapshotProvider` | core/vehicle | 위 둘 + 시각을 합친 `StateFlow<VehicleSnapshot>` | — | F02 |
| `SpeechListener` | core/audio | `listen(timeout): String?` 한 번 듣기 | Pleos SpeechToText | F01 |
| `SpeechSpeaker` | core/audio | `speak(text)` 끝날 때까지 대기, `stop()` | Pleos TextToSpeech | F01, F05 |

### domain: 규칙 (Android 의존 없음)

| 인터페이스 / 객체 | 패키지 | 역할 | 기능 |
|---|---|---|---|
| `CapsuleMatcher` | domain/matching | `match(capsule, snapshot) → MatchResult(isMatched, matchedConditions)`. 4개 모두 맞으면 `isMatched = true` | F03, F04 |
| `ConditionClassifier` | domain/matching | 스냅샷 → `TimeCondition`, `VehicleCondition` | F01, F04 |
| `CapsuleRepository` | domain/repository | `save`, `findNearby(lat, lng)`, `observeAll`, `clear` | F01, F03 |
| `DiscoveryRepository` | domain/repository | `save`, `isDiscovered(capsuleId, userId)`, `observeByUser(userId)` | F06, F07 |

### feature: 흐름

| 클래스 | 패키지 | 역할 | 화면 상태 |
|---|---|---|---|
| `CapsuleCreateFlow` | feature/capsulecreate | 버튼 → 스냅샷 고정 → 듣기 → 확인 → 저장 | `Listening`, `Confirming(text)`, `Created` |
| `CapsuleDiscoveryFlow` | feature/capsulediscovery | 스냅샷 감시 → 매칭 → 재생 → 수집 | `Discovered`, `Playing`, `Collected` |
| `CollectionViewModel` | feature/collection | 사용자별 도감 목록 | — |

### data: 구현

| 클래스 | 패키지 | 구현 대상 |
|---|---|---|
| `InMemoryCapsuleRepository` | data/local | `CapsuleRepository` (MVP) |
| `InMemoryDiscoveryRepository` | data/local | `DiscoveryRepository` (MVP) |
 
---

## 7. 시연 지원 (`debug/`)

| 도구 | 용도 |
|---|---|
| 사용자 A / B 전환 | 만드는 사람과 여는 사람을 번갈아 시연 |
| 시간대 덮어쓰기 | 낮에 촬영하면서 밤 캡슐 시연 |
| 전체 초기화 | 시연 반복 |
| 상태 표시 | 현재 스냅샷, 판정된 조건, 근처 캡슐 |

차량 상태는 **Pleos Virtual Console**로 바꾼다 (와이퍼, 기어). 위치는 에뮬레이터 **Extended Controls → Location → Routes**에서 GPX 경로를 재생한다.
 
---

## 8. 담당

### 나누는 기준
1. **인터페이스 경계로 나눔**
2. **SDK는 한 사람당 하나.**
3. **자기 SDK를 쓰는 기능까지 끝까지 맡는다.** 그 SDK가 쓰이는 화면 동작까지 확인.
   

| 담당 | 패키지 | 한 줄 요약 | SDK |
|---|---|---|---|
| ① (정인 구현) | `core/model`, `domain/`, `data/local`, `di/` | 모델·조건 매칭·저장소·조립, PR 리뷰와 통합 | — |
| ② 차량 (해린 구현) | `core/vehicle`, `debug/` | 기어·와이퍼 읽기, 스냅샷 합치기, 시연 디버그 패널 | Vehicle |
| ③ 위치·발견 (재현 구현) | `core/location`, `feature/capsulediscovery` | 위치·방향 읽기, 시연 GPX, 발견 → 재생 → 수집 흐름 | FusedLocation |
| ④ 음성·생성 (소영 구현) | `core/audio`, `feature/capsulecreate` | STT·TTS, 음성 생성 흐름 | SpeechToText, TextToSpeech |
| ⑤ 화면·제출 (승민 구현) | `ui/`, `feature/collection` | 공통 화면, 주행 화면, 도감, README 실행 방법, APK | — |
---

### 담당별 상세

#### ① 모델·규칙·통합 (정인)
| 항목 | 내용 |
|---|---|
| 만드는 것 | `core/model` 전체, `CapsuleMatcher`, `ConditionClassifier`, `CapsuleRepository`·`DiscoveryRepository` 인터페이스와 메모리 구현, `di/` 조립 |
| 할 일 | 모델·인터페이스 PR, **모든 인터페이스의 Fake 구현** (`FakeVehicleStateSource`, `FakeLocationSource`, `FakeSpeechListener`, `FakeSpeechSpeaker`) |
| 기능 ID | F04, F06(저장), F07(규칙) |
| 완료 기준 | 조건 매칭 단위 테스트 통과 (방향 350°↔10° 포함), Fake만으로 생성 → 발견 → 수집이 한 바퀴 돎 |

#### ② 차량: Vehicle SDK + 시연 조건 (해린)
| 항목 | 내용 |
|---|---|
| 만드는 것 | `VehicleStateSource`(기어, 와이퍼 4단계), `VehicleSnapshotProvider`(차량 + 위치 + 시각 → `VehicleSnapshot`), `debug/` 패널 |
| 디버그 패널 | 사용자 A/B 전환, 시간대 덮어쓰기(낮/밤/새벽), 전체 초기화, 현재 스냅샷·판정 조건 표시 |
| 기능 ID | F02 |
| 완료 기준 | Virtual Console에서 와이퍼·기어를 바꾸면 1초 안에 스냅샷에 반영됨. 디버그 패널로 시간대를 바꾸면 판정 조건이 바뀜 |

#### ③ 위치·발견: FusedLocation + 발견 흐름 (재현)
| 항목 | 내용 |
|---|---|
| 만드는 것 | `LocationSource`(위치, 진행 방향, 속도), 시연 GPX 경로, `CapsuleDiscoveryFlow` |
| 발견 흐름 | 스냅샷 감시 → 근처 캡슐 찾기 → `CapsuleMatcher`로 4조건 판정 → "캡슐이 있어요" → TTS 재생 → 도감 저장 |
| 기능 ID | F02, F03, F05 |
| 완료 기준 | 에뮬레이터에서 GPX를 재생하면 캡슐 지점에서 한 번만 열림. 반대 방향 GPX에서는 안 열림 |
| 먼저 볼 것 | Pleos FusedLocation SDK, 에뮬레이터 Extended Controls → Location → Routes |
| 처음엔 | 음성은 `FakeSpeechSpeaker`로 진행, ④가 완성하면 교체 |

#### ④ 음성·생성: STT·TTS + 생성 흐름 (소영)
| 항목 | 내용 |
|---|---|
| 만드는 것 | `SpeechListener`(STT, 최대 10초), `SpeechSpeaker`(TTS, 끝날 때까지 대기), `CapsuleCreateFlow` |
| 생성 흐름 | 버튼 → 스냅샷 고정 → "남길 말을 해주세요" → 듣기 → "이렇게 남길까요?" → 등록 / 다시(최대 2회) / 취소(8초 무응답 포함) |
| 기능 ID | F01, F05(TTS) |
| 완료 기준 | 에뮬레이터 마이크로 말한 문장이 캡슐로 저장되고, "등록·다시·취소"가 모두 동작함 |

#### ⑤ 화면·제출: Compose + 도감 + 제출물 (승민)
| 항목 | 내용 |
|---|---|
| 만드는 것 | `ui/` 공통 테마, 주행 화면(상태 한 줄 + 자막 + [캡슐 남기기] 큰 버튼), `feature/collection` 도감 화면 |
| 화면 상태 | `Listening`, `Confirming(text)`, `Created`, `Discovered`, `Playing`, `Collected`를 받아서 그리기 |
| 기능 ID | F06(화면), F07(주행 중 화면 최소화, 도감은 P단에서만) |
| 완료 기준 | 상태마다 화면이 바뀌고, D단에서는 도감이 안 열림.|
| 처음엔 | 가짜 상태 값으로 화면부터 만들고, 흐름이 완성되면 연결 |


### 의존 관계
```
① model·인터페이스·Fake  ─▶  ②③④⑤ 모두 시작 가능
② VehicleSnapshotProvider ─▶  ③ 발견 흐름, ④ 생성 흐름 (그 전까진 Fake)
④ SpeechSpeaker           ─▶  ③ 발견 재생 (그 전까진 Fake)
③④ 흐름 상태              ─▶  ⑤ 화면 연결 (그 전까진 가짜 상태)
```

### 코드 외 업무
 
| 일 | 담당 | 마감 |
|---|---|---|
| 기획서 최종본 | ① + ③ | 11/14 |
| 서명 동의서 취합 (전원 개별 서명) | ① | 11/14 |
| 시연 대사 원고 (생성·발견 멘트) | ④ | 11/12 |
| README 실행 방법, APK | ⑤ | 11/15 |
| 시연 영상 (여유 있으면) | ② + ⑤ | — |


## 9. 협업 규칙

- `main`에 직접 push 금지. 브랜치 → PR → 리뷰 1명 → merge
- 브랜치: `feat/F01-voice-create`, `fix/...`, `chore/...`
- `core/model`과 인터페이스 변경은 팀 합의 후
- 권한(`AndroidManifest.xml`) 추가는 PR에 이유 적기
- import는 `ai.pleos...`인지 확인 (이름이 같은 구글 지도·AAOS 클래스 주의)
---


## 10. 개발 환경

| 항목 | 값 |
|---|---|
| Android Studio | 최신 |
| compileSdk / targetSdk / minSdk | 37 / 34 / 28 |
| 에뮬레이터 | Pleos Connect v2.1.0 (API 34, Automotive), Cold boot, RAM 4GB, 내부 저장소 6GB |
| UI | Jetpack Compose (각 `feature/` 패키지 안에 `ui/`) |
| Pleos SDK | Vehicle 2.3.2 (나머지는 `gradle/libs.versions.toml` 참고) |
| SDK 버전 확인 | `curl -s https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/<SDK>/maven-metadata.xml` |

