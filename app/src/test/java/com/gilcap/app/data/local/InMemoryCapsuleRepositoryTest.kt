package com.gilcap.app.data.local

import com.gilcap.app.core.model.Capsule
import com.gilcap.app.core.model.TimeCondition
import com.gilcap.app.core.model.VehicleCondition
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryCapsuleRepositoryTest {

    private fun capsuleAt(id: String, lat: Double) = Capsule(
        id = id, authorId = "user-a", messageText = "테스트",
        latitude = lat, longitude = 128.9500, headingDegrees = 90f, placeName = "해안도로",
        timeCondition = TimeCondition.NIGHT, vehicleCondition = VehicleCondition.RAIN, createdAt = 0L,
    )

    @Test fun `반경 안의 캡슐만 찾는다`() = runTest {
        val repo = InMemoryCapsuleRepository()
        repo.save(capsuleAt("near", 37.7700))   // 같은 지점
        repo.save(capsuleAt("far", 37.7800))    // 약 1.1km 북쪽

        val found = repo.findNearby(37.7705, 128.9500)

        assertEquals(listOf("near"), found.map { it.id })
    }
}