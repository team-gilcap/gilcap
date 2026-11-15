# 길캡 (길 위의 캡슐)

> 같은 길, 같은 방향, 같은 시간과 날씨에만 열리는 음성 캡슐. 드라이브판 포켓몬고.

2026 현대자동차·기아 차량용 앱 개발 공모전 출품작 · Pleos Connect

---

## 한 줄 소개
운전자가 달리던 길에서 목소리로 남긴 캡슐이, **같은 길을 같은 방향으로, 같은 시간대와 날씨에 지나가는 다른 운전자에게만** 열려 음성으로 재생되고 도감에 수집됩니다.

## 핵심 기능 (MVP)
| 기능 | 내용 | 사용 SDK |
|---|---|---|
| 캡슐 남기기 | 주행 중 버튼 1번 → 음성으로 말하기 → "등록 / 다시 / 취소" 음성 확인 | SpeechToText, TextToSpeech |
| 조건 저장 | 남기는 순간의 **위치, 진행 방향, 시간대, 와이퍼 상태**를 함께 저장 | FusedLocation, Vehicle |
| 캡슐 열기 | 4가지 조건이 **모두** 맞을 때만 열림 → 음성 재생 + 자막 | FusedLocation, Vehicle, TextToSpeech |
| 도감 | 열어본 캡슐을 사용자별로 수집. **P단에서만** 표시 | Vehicle (기어) |

### 캡슐이 열리는 4가지 조건
| 조건 | 기준 |
|---|---|
| 위치 | 캡슐 지점 반경 150m 안 |
| 방향 | 캡슐 방향 ±45° |
| 시간대 | 낮(06~19시) / 밤(19~04시) / 새벽(04~06시) |
| 날씨 (차량) | 와이퍼 OFF = 맑음, AUTO·LOW = 비, HIGH = 폭우 |

> 차량 데이터가 없으면 캡슐이 열리지 않습니다. 같은 자리라도 반대 방향이거나 날씨가 다르면 열리지 않습니다.

---

## APK
- **위치:** [`apk/TEAM_길캡_release.apk`](apk/) (release 빌드)
- 용량 초과 시: [GitHub Releases](../../releases)

---

## 테스트 환경

| 항목 | 값 |
|---|---|
| Pleos Connect 에뮬레이터 | **Pleos Connect v2.1.0** (Android 14, API 34, Automotive, arm64-v8a) |
| 화면 규격 | **{가로}×{세로} px, {density} dpi** |
| Android Emulator | {버전} |
| AVD 설정 | RAM 4GB, 내부 저장소 6GB, Cold Boot |
| 개발 환경 | Android Studio, macOS (Apple Silicon) |
| 앱 | minSdk 28, targetSdk 34 |

### 사용 Pleos Connect SDK
| SDK | 버전 | 용도 |
|---|---|---|
| Vehicle | 2.3.2 | 기어, 와이퍼 단계 |
| FusedLocation | 1.1.3 | 위치, 진행 방향 |
| SpeechToText | 3.3.1 | 캡슐 메시지, 등록/다시/취소 인식 (ON_DEVICE) |
| TextToSpeech | 3.3.1 | 안내 음성, 캡슐 재생 (ON_DEVICE) |

API 키 없이 ON_DEVICE 모드로 동작합니다.

---

## 실행 방법

### 1. 설치
Pleos Connect Emulator v2.1.0을 **Cold Boot**로 실행한 뒤:
```bash
adb install -r apk/TEAM_길캡_release.apk
```
소스에서 빌드할 경우:
```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

### 2. 필요한 설정

| 설정 | 방법 | 용도 |
|---|---|---|
| Virtual Console | `adb forward tcp:8088 tcp:8088` → 브라우저 `http://127.0.0.1:8088` | 기어, 와이퍼 변경 |
| 시연 경로 | 에뮬레이터 Extended Controls(⋯) → **Location → Routes → IMPORT GPX/KML** → [`demo/gpx/gilcap_forward.gpx`](demo/gpx/), [`gilcap_reverse.gpx`](demo/gpx/) | 위치, 진행 방향 |
| 마이크 | Extended Controls → **Microphone → Virtual microphone uses host audio input** 켜기 | 음성 입력 |
| 에뮬레이터 오디오 | AVD `config.ini`에 `hw.audioInput=yes`, `hw.audioOutput=yes` | 음성 입력·출력 |

- 에뮬레이터를 다시 켜면 `adb forward`와 마이크 설정을 다시 해 주세요.
- 위치 경로는 강릉 해안도로(안목 → 송정 → 강문, 약 3.7km)입니다. 정방향은 북서쪽, 역방향은 남동쪽으로 진행합니다.

### 3. 시연 패널 (DEBUG)
에뮬레이터 한 대로 "캡슐을 남기는 사람(A)"과 "여는 사람(B)"을 보여주기 위한 패널입니다.

- 화면의 **DEBUG** 버튼으로 엽니다. release 빌드에서는 **P단에서만** 표시되고, 주행(D)으로 바꾸면 자동으로 닫힙니다.
- 사용자 A / B 전환, 시간대 덮어쓰기(실제/낮/밤/새벽), 현재 차량 상태와 판정 조건, 전체 초기화를 제공합니다.

---

## 시연 순서

### 바로 확인: 미리 심어둔 캡슐 열기 (약 1분)
앱에는 심사용 캡슐 1개(작성자 B, **맑음**, 정방향)가 들어 있습니다.
1. Virtual Console: **Gear → D**, **Wiper → Off**
2. Routes에서 `gilcap_forward` 재생
3. 안목 → 송정 구간 중간에서 "누군가 남긴 캡슐이 있어요" → 음성 재생 → 도감 수집
4. Routes 정지 → **Gear → P** → 도감 확인

같은 방법으로 `gilcap_reverse`를 재생하거나 와이퍼를 켜고 지나가면 **열리지 않습니다.**

### 전체 흐름: 남기기 → 조건 불일치 → 열기
| 장면 | 조작 | 기대 결과 |
|---|---|---|
| 1. A가 남기기 | P단에서 DEBUG → 사용자 **A** → Gear **D**, Wiper **Low** → `gilcap_forward` 재생 → 구간 중간에서 일시정지 → **[캡슐 남기기]** → 말하기 → **"등록"** | "비 오는 ○의 해안도로에 캡슐을 남겼어요" |
| 2. 반대 방향 | P단에서 사용자 **B** → Gear D, Wiper Low → `gilcap_reverse` 재생 | 열리지 않음 (방향 불일치) |
| 3. 맑은 날 | Wiper **Off** → `gilcap_forward` 처음부터 재생 | 열리지 않음 (날씨 불일치) |
| 4. 같은 조건 | Wiper **Low** → `gilcap_forward` 처음부터 재생 | **열림** → A의 메시지 재생 → 도감 수집 |
| 5. 도감 | Gear **P** → 도감 | "비 오는 ○의 해안도로" 캡슐 표시. D단에서는 숨김 |

시간대는 실제 시각 기준입니다. 장면 1과 4의 시간대가 달라지면 DEBUG 패널에서 같은 시간대로 맞춰 주세요.

---

## 안전 설계
- 주행 중 터치는 **[캡슐 남기기] 버튼 1번**만. 나머지는 음성으로 진행
- 캡슐은 한 번에 하나만 재생. 캡슐을 남기는 동안에는 발견을 멈춤
- 이미 수집한 캡슐과 자기 캡슐은 다시 열리지 않음
- 도감과 시연 패널은 **P단에서만** 표시
- 위치 등 민감한 로그는 debug 빌드에서만 기록

---

## 프로젝트 구조
```
app/src/main/java/com/gilcap/app/
├─ core/       model, vehicle(Vehicle SDK), location(FusedLocation SDK), audio(STT·TTS SDK)
├─ domain/     matching(4조건 판정), repository
├─ data/       local(메모리 저장소)
├─ feature/    capsulecreate(음성 생성), capsulediscovery(발견·재생), collection(도감)
├─ ui/         주행 화면, 공통 컴포넌트
├─ debug/      시연 패널
└─ di/         조립
demo/gpx/      시연 경로 GPX
```
설계, 데이터 모델, 인터페이스, 기능 우선순위(P0/P1/P2)는 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)에 있습니다.

### 테스트
```bash
./gradlew testDebugUnitTest
```
4조건 매칭(경계값, 방향 350°↔10°), 생성 흐름(등록/다시/취소/무응답), 발견 흐름, 시연 시나리오 단위 테스트를 포함합니다.

---

## 향후 계획 (P1)
- 희귀도: 조건이 드문 정도로 일반 / 레어 / 에픽 / 레전드 (예: 폭우 내리는 새벽)
- 흔적 발견: 위치·방향은 맞고 조건 하나가 다르면 "비 오는 밤에만 열리는 캡슐이 있어요" 힌트, 도감에 실루엣
- 차량 환경 재현: 캡슐을 남긴 순간의 공조 온도를 승인 후 적용
- 실제 목소리 캡슐, 서버 동기화
