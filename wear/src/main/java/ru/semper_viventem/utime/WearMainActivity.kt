package ru.semper_viventem.utime

import android.os.Bundle
import android.support.wearable.activity.WearableActivity

class WearMainActivity : WearableActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}