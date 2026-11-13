#!/usr/bin/env python3
"""시연 GPX 생성기. 좌표는 app/.../core/location/DemoRoute.kt 와 같게 유지한다.

    python3 demo/gpx/generate_gpx.py

- gilcap_forward.gpx : 안목 → 강문 (북서쪽). 캡슐 지점을 정방향으로 지남 → 열려야 함
- gilcap_reverse.gpx : 강문 → 안목 (남동쪽). 같은 지점을 반대로 지남 → 안 열려야 함
점 간격 약 10m, 40km/h 기준 시각을 붙인다 (에뮬레이터 Routes에서 재생 속도 조절 가능).
"""
import math
import os
from datetime import datetime, timedelta, timezone

WAYPOINTS = [
    (37.7705, 128.9490),  # 안목해변
    (37.7820, 128.9370),  # 송정해변
    (37.7950, 128.9205),  # 강문해변
]
SPEED_KPH = 40.0
STEP_METERS = 10.0
START = datetime(2026, 11, 14, 21, 0, 0, tzinfo=timezone.utc)


def distance(a, b):
    r = 6371000.0
    dlat = math.radians(b[0] - a[0])
    dlng = math.radians(b[1] - a[1])
    h = math.sin(dlat / 2) ** 2 + math.cos(math.radians(a[0])) * math.cos(math.radians(b[0])) * math.sin(dlng / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def densify(points):
    out = [points[0]]
    for a, b in zip(points, points[1:]):
        n = max(1, round(distance(a, b) / STEP_METERS))
        for i in range(1, n + 1):
            t = i / n
            out.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
    return out


def write(name, points):
    mps = SPEED_KPH / 3.6
    t = START
    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<gpx version="1.1" creator="gilcap" xmlns="http://www.topografix.com/GPX/1/1">',
        f'  <trk><name>{name}</name><trkseg>',
    ]
    prev = None
    for p in points:
        if prev is not None:
            t += timedelta(seconds=distance(prev, p) / mps)
        lines.append(f'    <trkpt lat="{p[0]:.6f}" lon="{p[1]:.6f}"><time>{t.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3]}Z</time></trkpt>')
        prev = p
    lines += ['  </trkseg></trk>', '</gpx>', '']
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), f'{name}.gpx')
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))
    print(f'{path}: {len(points)} points, {(t - START).total_seconds():.0f}s')


if __name__ == '__main__':
    forward = densify(WAYPOINTS)
    write('gilcap_forward', forward)
    write('gilcap_reverse', list(reversed(forward)))
