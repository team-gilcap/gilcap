package com.gilcap.app.core.demo

import com.gilcap.app.core.model.DemoUsers
import com.gilcap.app.core.model.TimeCondition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 시연용 설정. 디버그 패널(②)이 바꾸고, 생성·발견 흐름(③④)이 읽는다 */
class DemoSettings {
    private val _currentUserId = MutableStateFlow(DemoUsers.A)
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    private val _timeOverride = MutableStateFlow<TimeCondition?>(null)
    val timeOverride: StateFlow<TimeCondition?> = _timeOverride.asStateFlow()

    fun switchUser(userId: String) { _currentUserId.value = userId }
    fun overrideTime(time: TimeCondition?) { _timeOverride.value = time }   // null = 실제 시각
}