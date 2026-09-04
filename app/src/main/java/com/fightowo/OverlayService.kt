package com.fightowo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class OverlayService : Service() {
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var running = false
    private val activeViews = mutableListOf<View>()
    private val heart = AtomicInteger(0)
    private var spawnInterval = 3000L
    private var tapsRequiredBase = 5
    private var vibrationEnabled = true
    private var uiGravity = Gravity.TOP or Gravity.END
    private var difficulty = "Normal"
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // Load settings
        val prefs = getSharedPreferences("fightowo_prefs", Context.MODE_PRIVATE)
        vibrationEnabled = prefs.getBoolean("vibration", true)
        uiGravity = prefs.getInt("ui_gravity", (Gravity.TOP or Gravity.END))
        difficulty = prefs.getString("difficulty", "Normal") ?: "Normal"
        spawnInterval = when (difficulty) {
            "Easy" -> 3500L
            "Hard" -> 1500L
            else -> 2500L
        }
        tapsRequiredBase = when (difficulty) {
            "Easy" -> 3
            "Hard" -> 7
            else -> 5
        }

        startForegroundServiceNotification()
        showHeartMeter()
        startSpawning()
    }

    private fun startForegroundServiceNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "fightowo_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Fightowo Service", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Fightowo running")
            .setContentText("Tap images to clear them")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, notif)
    }

    private fun showHeartMeter() {
        val inflater = LayoutInflater.from(this)
        val meter = inflater.inflate(R.layout.overlay_heart, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = uiGravity
        wm.addView(meter, params)
        // meter updates handled by coroutine
        scope.launch {
            val progress = meter.findViewById<ProgressBar>(R.id.heart_progress)
            val label = meter.findViewById<TextView>(R.id.heart_label)
            while (true) {
                val v = heart.get()
                progress.progress = v
                label.text = "Heart: $v/100"
                delay(300L)
            }
        }
    }

    private fun startSpawning() {
        running = true
        scope.launch {
            while (running) {
                spawnImage()
                adjustSpawnBasedOnHeart()
                delay(spawnInterval)
            }
        }
    }

    private fun adjustSpawnBasedOnHeart() {
        val h = heart.get()
        // Heart increases based on number of active views
        val count = activeViews.size
        // scale heart roughly: (count / 5) * 10
        heart.set((count * 10).coerceIn(0, 100))
        // As heart grows, reduce spawn interval
        spawnInterval = when {
            h < 30 -> spawnInterval.coerceAtLeast(800L)
            h < 60 -> (spawnInterval * 0.8).toLong().coerceAtLeast(400L)
            h < 90 -> (spawnInterval * 0.6).toLong().coerceAtLeast(200L)
            else -> { // maxed
                onHeartMaxed()
                200L
            }
        }
    }

    private var overloadTriggered = false
    private fun onHeartMaxed() {
        if (overloadTriggered) return
        overloadTriggered = true
        // Heart overload effect: spawn a short burst of many images and increase taps required
        scope.launch {
            for (i in 0 until 12) {
                spawnImage(requireMoreTaps = true)
                delay(80L)
            }
            // after burst, reset overload flag after 8s
            delay(8000L)
            overloadTriggered = false
        }
    }

    private fun spawnImage(requireMoreTaps: Boolean = false) {
        val inflater = LayoutInflater.from(this)
        val imageView = ImageView(this)
        val sizeDp = Random.nextInt(80, 220)
        val sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sizeDp.toFloat(), resources.displayMetrics).toInt()
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.x = Random.nextInt(0, resources.displayMetrics.widthPixels - sizePx)
        params.y = Random.nextInt(0, resources.displayMetrics.heightPixels - sizePx)
        params.gravity = Gravity.TOP or Gravity.START

        val tapsNeeded = (tapsRequiredBase + if (requireMoreTaps) 2 else 0)
        val tapCounter = AtomicInteger(0)

        imageView.setOnTouchListener(object: View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event?.action == MotionEvent.ACTION_DOWN) {
                    val tapped = tapCounter.incrementAndGet()
                    if (v != null) v.alpha = 1f - (tapped.toFloat() / (tapsNeeded + 2))
                    if (tapped >= tapsNeeded) {
                        try { wm.removeView(imageView) } catch (e: Exception) {}
                        activeViews.remove(imageView)
                    }
                    if (vibrationEnabled) {
                        try { // simple vibrate
                            val vib = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                            if (vib.hasVibrator()) vib.vibrate(20)
                        } catch (e: Exception) {}
                    }
                }
                return true
            }
        })

        // load image URL from e621
        scope.launch(Dispatchers.IO) {
            try {
                val prefs = getSharedPreferences("fightowo_prefs", Context.MODE_PRIVATE)
                val tags = prefs.getString("tags", "") ?: ""
                val safeTags = if (tags.isBlank()) "order:random" else tags + "+order:random"
                val url = "https://e621.net/posts.json?limit=1&tags=${java.net.URLEncoder.encode(safeTags, "utf-8") }"
                val req = Request.Builder().url(url).header("User-Agent", "Fightowo/0.1 (by therealandbigjj-eng)").build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string()
                if (body != null) {
                    val jo = JSONObject(body)
                    val posts = jo.getJSONArray("posts")
                    if (posts.length() > 0) {
                        val post = posts.getJSONObject(0)
                        val file = post.getJSONObject("file")
                        val imageUrl = file.getString("url")
                        // load on main
                        handler.post {
                            Glide.with(this@OverlayService).load(imageUrl).into(imageView)
                            try { wm.addView(imageView, params); activeViews.add(imageView) } catch (e: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore network errors
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        running = false
        try {
            for (v in activeViews) wm.removeView(v)
        } catch (e: Exception) {}
    }
}
