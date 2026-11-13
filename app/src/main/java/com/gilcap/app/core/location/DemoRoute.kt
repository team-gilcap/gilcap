package com.gilcap.app.core.location

/**
 * 시연 경로: 강릉 해안도로 (안목 → 송정 → 강문, 북서쪽 진행, 약 3.7km).
 *
 * 좌표는 지도에서 대략 잡은 값이다. 바꾸면 `demo/gpx/generate_gpx.py`의 같은 값도 바꾸고 GPX를 다시 만든다.
 * - 정방향 GPX(`gilcap_forward.gpx`): 캡슐 지점을 북서쪽(약 320°)으로 지난다 → 열림
 * - 역방향 GPX(`gilcap_reverse.gpx`): 같은 지점을 남동쪽(약 140°)으로 지난다 → 안 열림
 */
object DemoRoute {
    const val PLACE_NAME = "해안도로"

    /** 경유지 (위도, 경도). 정방향 순서 */
    val waypoints: List<Pair<Double, Double>> = listOf(
        37.7705 to 128.9490,   // 안목해변
        37.7820 to 128.9370,   // 송정해변
        37.7950 to 128.9205,   // 강문해변
    )

    /** 시연 캡슐 지점: 안목 → 송정 구간 가운데 */
    const val CAPSULE_LAT = 37.77625
    const val CAPSULE_LNG = 128.9430

    /** 정방향으로 캡슐 지점을 지날 때 진행 방향 */
    const val FORWARD_HEADING = 320.5f
}
