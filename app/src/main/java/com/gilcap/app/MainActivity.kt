package com.gilcap.app

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var vehicle: Vehicle

    @RequiresPermission("pleos.car.permission.CAR_DOORS")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "길캡 - SDK 연결 테스트"
            textSize = 32f
        })

        vehicle = Vehicle(this)
        vehicle.initialize()
        val door = vehicle.getDoor()
        door.getCurrentDoorPosition(
            area = DoorArea(DoorValue.ROW_1_LEFT),
            onComplete = { pos: Int? ->
                Log.d("GILCAP", "운전석 문 위치: $pos")
            },
            onFailed = { e: Exception ->
                Log.e("GILCAP", "문 조회 실패: ${e.message}", e)
            }
        )
    }

    override fun onDestroy() {
        vehicle.release()
        super.onDestroy()
    }
}