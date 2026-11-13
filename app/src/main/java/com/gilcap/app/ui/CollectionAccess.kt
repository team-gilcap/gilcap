package com.gilcap.app.ui

import com.gilcap.app.core.model.Gear

/** P단이어도 음성 입력·재생 중에는 도감으로 화면을 전환하지 않는다. */
fun canOpenCollection(gear: Gear, isBusy: Boolean): Boolean = gear == Gear.P && !isBusy
