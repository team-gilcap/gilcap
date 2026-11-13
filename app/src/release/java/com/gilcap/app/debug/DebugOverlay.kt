package com.gilcap.app.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gilcap.app.di.AppContainer

/**
 * release 빌드용 빈 구현 (no-op)
 * - 아무것도 그리지 않도록 구성
 * - 사용자 전환, 시간대 덮어쓰기, 전체 초기화 기능과 코드가 release APK에 포함되지 않게 하기 위함
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun DebugOverlay(container: AppContainer, modifier: Modifier = Modifier) = Unit
