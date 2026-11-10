package com.gilcap.app.core.model

/** F01에서 생성, F03~F05에서 발견 */
data class Capsule(
    val id: String,                          // UUID
    val authorId: String,                    // 만든 사용자. 자기 캡슐은 자기에게 안 열림
    val messageText: String,                 // STT로 받은 말. 열 때 TTS로 읽음
    val latitude: Double,                    // 생성 버튼을 누른 지점
    val longitude: Double,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    val headingDegrees: Float,               // 생성 순간 진행 방향
    val placeName: String,                   // 도감 이름용 "해안도로"
    val timeCondition: TimeCondition,
    val vehicleCondition: VehicleCondition,
    val createdAt: Long,
) {
    /** 도감 이름: "비 오는 밤의 해안도로" */
    val dexName: String
        get() = "${vehicleCondition.label} ${timeCondition.label} $placeName"

    companion object {
        const val DEFAULT_RADIUS_METERS = 150
        const val HEADING_TOLERANCE_DEGREES = 45f
    }
}