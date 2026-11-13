# 시연 GPX (③ 위치·발견)

| 파일 | 경로 | 기대 결과 |
|---|---|---|
| `gilcap_forward.gpx` | 강릉 해안도로 안목 → 송정 → 강문 (북서쪽, 약 3.7km, 40km/h 기준 5분 33초) | 캡슐 지점에서 **한 번만** 열림 |
| `gilcap_reverse.gpx` | 같은 길 반대 방향 (남동쪽) | 안 열림 |

캡슐 지점: `37.77625, 128.9430` (안목 → 송정 구간 가운데), 정방향 진행 방향 약 320°.
좌표는 `app/.../core/location/DemoRoute.kt`와 같다. 바꾸면 둘 다 고치고 다시 생성한다.

```bash
python3 demo/gpx/generate_gpx.py
```

## 에뮬레이터에서 재생
1. Extended Controls(`…`) → **Location** → **Routes** → **Import GPX/KML** → `gilcap_forward.gpx`
2. 재생 속도는 오른쪽 아래 Playback speed에서 조절 (1x = 40km/h)
3. 캡슐 만들기(사용자 A): 캡슐 지점 근처까지 재생 → 일시정지 → [캡슐 남기기]
4. 열기(사용자 B): 디버그 패널에서 B로 전환 → 처음부터 다시 재생 → 캡슐 지점 150m 전부터 열림
5. 역방향 확인: `gilcap_reverse.gpx`로 재생하면 같은 지점을 지나도 안 열림

시간대·와이퍼는 캡슐을 만들 때와 같아야 한다 (디버그 패널 시간대 덮어쓰기, Virtual Console 와이퍼).
