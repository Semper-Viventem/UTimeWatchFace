package ru.semper_viventem.utime

import android.graphics.Color
import android.support.wearable.activity.WearableActivity
import android.view.View

class WearMainActivity : WearableActivity() {

    override fun setContentView(view: View?) {
        view?.setBackgroundColor(Color.WHITE)
        super.setContentView(view)
    }
}