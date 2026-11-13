package com.gilcap.app.feature.collection

import androidx.lifecycle.ViewModelStore
import com.gilcap.app.core.model.*
import com.gilcap.app.data.local.InMemoryCapsuleRepository
import com.gilcap.app.data.local.InMemoryDiscoveryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionViewModelTest {
    private fun capsule(id: String) = Capsule(id, "author", "내용 $id", 37.77, 128.95,
        headingDegrees = 90f, placeName = "해안도로", timeCondition = TimeCondition.NIGHT,
        vehicleCondition = VehicleCondition.RAIN, createdAt = 0)

    @Test fun `수집 기록을 캡슐과 연결하고 최신순으로 중복 없이 보여준다`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val capsules = InMemoryCapsuleRepository()
            val discoveries = InMemoryDiscoveryRepository()
            capsules.save(capsule("old")); capsules.save(capsule("new"))
            discoveries.save(CapsuleDiscovery("old", "A", 1, emptySet()))
            discoveries.save(CapsuleDiscovery("new", "A", 2, emptySet()))
            discoveries.save(CapsuleDiscovery("new", "A", 3, emptySet()))
            val vm = CollectionViewModel(MutableStateFlow("A"), capsules, discoveries)
            store.put("collection", vm)
            backgroundScope.launch { vm.state.collect() }
            runCurrent()
            val state = vm.state.value as CollectionUiState.Content
            assertEquals(listOf("new", "old"), state.items.map { it.discovery.capsuleId })
            assertEquals("내용 new", state.items.first().capsule?.messageText)
        } finally {
            store.clear()
            // ViewModel 취소 작업을 끝낸 뒤 테스트용 메인 디스패처를 해제한다.
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun `사용자를 바꾸면 해당 사용자의 수집 기록만 표시한다`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val user = MutableStateFlow("A")
            val capsules = InMemoryCapsuleRepository()
            val discoveries = InMemoryDiscoveryRepository()
            capsules.save(capsule("a")); capsules.save(capsule("b"))
            discoveries.save(CapsuleDiscovery("a", "A", 1, emptySet()))
            discoveries.save(CapsuleDiscovery("b", "B", 2, emptySet()))
            val vm = CollectionViewModel(user, capsules, discoveries)
            store.put("collection", vm)
            backgroundScope.launch { vm.state.collect() }
            runCurrent()
            assertEquals("a", (vm.state.value as CollectionUiState.Content).items.single().discovery.capsuleId)
            user.value = "B"
            runCurrent()
            val state = vm.state.value as CollectionUiState.Content
            assertEquals("B", state.userId)
            assertEquals("b", state.items.single().discovery.capsuleId)
            user.value = "C"
            runCurrent()
            assertTrue((vm.state.value as CollectionUiState.Content).items.isEmpty())
        } finally {
            store.clear()
            // ViewModel 취소 작업을 끝낸 뒤 테스트용 메인 디스패처를 해제한다.
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun `새 수집과 초기화를 반영하고 캡슐 정보가 없어도 기록을 유지한다`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val capsules = InMemoryCapsuleRepository()
            val discoveries = InMemoryDiscoveryRepository()
            val vm = CollectionViewModel(MutableStateFlow("A"), capsules, discoveries)
            store.put("collection", vm)
            backgroundScope.launch { vm.state.collect() }
            runCurrent()
            assertTrue((vm.state.value as CollectionUiState.Content).items.isEmpty())
            discoveries.save(CapsuleDiscovery("missing", "A", 1, emptySet()))
            runCurrent()
            assertNull((vm.state.value as CollectionUiState.Content).items.single().capsule)
            capsules.save(capsule("missing"))
            runCurrent()
            assertNotNull((vm.state.value as CollectionUiState.Content).items.single().capsule)
            discoveries.clear()
            runCurrent()
            assertTrue((vm.state.value as CollectionUiState.Content).items.isEmpty())
        } finally {
            store.clear()
            // ViewModel 취소 작업을 끝낸 뒤 테스트용 메인 디스패처를 해제한다.
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
