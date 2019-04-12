package ru.semper_viventem.utime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.text.format.DateFormat
import android.view.SurfaceHolder
import android.view.WindowInsets

import java.lang.ref.WeakReference
import java.util.*

class MyWatchFace : CanvasWatchFaceService() {

    companion object {
        private val NORMAL_TYPEFACE = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        private const val INTERACTIVE_UPDATE_RATE_MS = 1000
        private const val MSG_UPDATE_TIME = 0

        private val DEFAULT_SUBTITLE_COLOR: Int = Color.argb(0x88, 0, 0, 0)
        private val DEFAULT_SUBTITLE_COLOR_REWERT: Int = Color.argb(0x88, 0xFF, 0xFF, 0xFF)
        private const val RETURN_UTIME_DELAY = 3000L
        private const val TIME_FORMAT = "hh:mm:ss"
        private const val DATE_FORMAT = "dd.MM.yy"
    }

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: MyWatchFace.Engine) : Handler() {
        private val mWeakReference: WeakReference<MyWatchFace.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {

        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false
        private var mCenterX: Float = 0F
        private var mCenterY: Float = 0F
        private var width: Float = 0F
        private var height: Float = 0F

        private var uTimeText: String = "0000000000"
        private lateinit var uTimePaint: Paint

        private var subTimeText: String = "0000"
        private lateinit var subTimePaint: Paint

        private var backgroundColor: Int = Color.WHITE
        private var backgroundGrayColor: Int = Color.WHITE

        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false
        private var isUnixTime: Boolean = true

        private val returnUTime = Handler()

        private val mUpdateTimeHandler = EngineHandler(this)
        private val refreshIsUnixTime = Runnable {
            isUnixTime = true
            invalidate()
        }

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@MyWatchFace)
                    .setAcceptsTapEvents(true)
                    .build()
            )

            mCalendar = Calendar.getInstance()

            initializeWatchFace()
        }

        private fun initializeWatchFace() {

            uTimePaint = Paint().apply {
                typeface = NORMAL_TYPEFACE
                color = Color.BLACK
                isAntiAlias = true
            }

            subTimePaint = Paint().apply {
                typeface = NORMAL_TYPEFACE
                color = DEFAULT_SUBTITLE_COLOR
                isAntiAlias = true
            }
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false
            )
            mBurnInProtection = properties.getBoolean(
                WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false
            )
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            updateWatchHandStyle()

            updateTimer()
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                uTimePaint.alpha = if (inMuteMode) 100 else 0xFF
                uTimePaint.alpha = if (inMuteMode) 50 else 0x88
                invalidate()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            mCenterX = width / 2f
            mCenterY = height / 2f

            this.width = width.toFloat()
            this.height = height.toFloat()

            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap()
            }
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)

            val resources = this@MyWatchFace.resources
            val isRound = insets.isRound

            val textSize = resources.getDimension(
                if (isRound)
                    R.dimen.digital_text_size_round
                else
                    R.dimen.digital_text_size
            )

            uTimePaint.textSize = textSize
            subTimePaint.textSize = textSize
        }

        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // do nothing
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // do nothing
                }
                WatchFaceService.TAP_TYPE_TAP -> {
                    isUnixTime = !isUnixTime
                    returnUTime.removeCallbacks(refreshIsUnixTime)
                    returnUTime.postDelayed(refreshIsUnixTime, RETURN_UTIME_DELAY)
                }
            }
            invalidate()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            updateTimer()
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now

            if (isUnixTime) {
                uTimeText = (now / 1000).toString()
                subTimeText = getNextHourDiff(uTimeText)
            } else {
                val date = Date(now)
                uTimeText = DateFormat.format(TIME_FORMAT, date).toString()
                subTimeText = DateFormat.format(DATE_FORMAT, date).toString()
            }

            drawBackground(canvas)
            drawWatchFace(canvas)
        }

        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }

        private fun initGrayBackgroundBitmap() {
            val greyColor = 0x10
            backgroundGrayColor = Color.rgb(greyColor, greyColor, greyColor)
            val canvas = Canvas()
            canvas.drawColor(backgroundGrayColor)
        }

        private fun updateWatchHandStyle() {
            if (mAmbient) {
                subTimePaint.color = DEFAULT_SUBTITLE_COLOR_REWERT

                uTimePaint.color = Color.WHITE

            } else {
                subTimePaint.color = DEFAULT_SUBTITLE_COLOR

                uTimePaint.color = Color.BLACK
            }
        }

        private fun drawBackground(canvas: Canvas) {

            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK)
            } else if (mAmbient) {
                canvas.drawColor(backgroundGrayColor)
            } else {
                canvas.drawColor(backgroundColor)
            }
        }

        private fun drawWatchFace(canvas: Canvas) {

            canvas.save()

            val uTextWidth = uTimePaint.measureText(uTimeText)
            val uTextX = mCenterX - (uTextWidth / 2)
            val uTextY = mCenterY
            canvas.drawText(uTimeText, uTextX, uTextY, uTimePaint)

            val sTextWidth = subTimePaint.measureText(subTimeText)
            val sTextX = ((mCenterX + (uTextWidth / 2)) - sTextWidth)
            val sTextY = mCenterY + subTimePaint.textSize
            canvas.drawText(subTimeText, sTextX, sTextY, subTimePaint)

            /* Restore the canvas' original orientation. */
            canvas.restore()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@MyWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

        private fun getNextHourDiff(currentTime: String): String {
            val nextOurCalendar = Calendar.getInstance().apply {
                add(Calendar.HOUR, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val nextHour = (nextOurCalendar.timeInMillis / 1000).toString()

            val subTitleTextBuffer = StringBuffer()
            nextHour.forEachIndexed { i, c ->
                if ((currentTime.lastIndex >= i && c != currentTime[i]) || subTitleTextBuffer.isNotEmpty()) {
                    subTitleTextBuffer.append(c)
                }
            }

            return subTitleTextBuffer.toString()
        }
    }
}


