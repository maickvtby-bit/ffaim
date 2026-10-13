package com.jbtoai.ffaim

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.security.SecureRandom

class Config(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.getSharedPreferences("ffaim", Context.MODE_PRIVATE)

    var crossSize: Int
        get() = sp.getInt("cross_size", 90)
        set(v) = sp.edit().putInt("cross_size", v).apply()

    var alpha: Int
        get() = sp.getInt("alpha", 200)
        set(v) = sp.edit().putInt("alpha", v).apply()

    var autoHide: Boolean
        get() = sp.getBoolean("auto_hide", true)
        set(v) = sp.edit().putBoolean("auto_hide", v).apply()

    var fpsOverlay: Boolean
        get() = sp.getBoolean("fps", false)
        set(v) = sp.edit().putBoolean("fps", v).apply()
}

object AntiBan {
    private val rng = SecureRandom()

    private val rootPaths = listOf(
        "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
        "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
        "/data/local/su", "/su/bin/su", "/magisk/.core/bin/su",
        "/sbin/.magisk", "/dev/.magisk"
    )

    private val riskyPkgs = listOf(
        "com.topjohnwu.magisk",
        "de.robv.android.xposed.installer",
        "org.meowcat.edxposed.manager",
        "com.vphonegaga.titan",
        "io.virtualapp",
        "com.lbe.parallel",
        "com.f1vm.sandbox",
        "com.x8.sandbox"
    )

    fun scan(ctx: Context): List<String> {
        val w = mutableListOf<String>()
        if (isRooted()) w += "Máy có dấu hiệu root/Magisk"
        for (p in riskyPkgs) if (isInstalled(ctx, p)) w += "Có app nguy cơ: $p"

        val dev = Settings.Global.getInt(
            ctx.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) == 1
        if (dev) w += "Developer options đang bật"

        val adb = Settings.Global.getInt(
            ctx.contentResolver, Settings.Global.ADB_ENABLED, 0
        ) == 1
        if (adb) w += "USB debugging đang bật"

        if (w.isEmpty()) w += "✓ Không thấy dấu hiệu nguy cơ"
        w += "• App không hook/mod FF → risk từ app = 0"
        return w
    }

    private fun isRooted(): Boolean {
        for (p in rootPaths) if (File(p).exists()) return true
        val tags = Build.TAGS ?: ""
        if (tags.contains("test-keys")) return true
        System.getenv("PATH")?.split(":")?.forEach {
            if (File(it, "su").exists()) return true
        }
        return false
    }

    private fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: Exception) { false }

    fun jitter(base: Long): Long {
        val d = (base * 0.3).toLong()
        return base + rng.nextLong(-d, d + 1)
    }

    fun scrub(ctx: Context) {
        runCatching {
            ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
    }
}

class CrosshairView(ctx: Context, private val cfg: Config) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
        color = Color.argb(cfg.alpha, 0, 255, 120)
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(cfg.alpha, 255, 40, 40)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = cfg.crossSize.toFloat(); val gap = r * 0.3f
        canvas.drawCircle(cx, cy, r, paint)
        canvas.drawLine(cx - r, cy, cx - gap, cy, paint)
        canvas.drawLine(cx + gap, cy, cx + r, cy, paint)
        canvas.drawLine(cx, cy - r, cx, cy - gap, paint)
        canvas.drawLine(cx, cy + gap, cx, cy + r, paint)
        canvas.drawCircle(cx, cy, 3f, dot)
    }

    override fun onMeasure(w: Int, h: Int) {
        val s = cfg.crossSize * 2 + 40
        setMeasuredDimension(s, s)
    }
}

class RecoilGuideView(ctx: Context, private val cfg: Config) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f
        color = Color.argb(150, 255, 200, 0)
        strokeCap = Paint.Cap.ROUND
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255); textSize = 26f
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f; val x0 = 80f; val s = 40f
        val path = Path().apply {
            moveTo(x0, cy)
            lineTo(x0 + s, cy + 20)
            lineTo(x0 + s * 2, cy + 45)
            lineTo(x0 + s * 3, cy + 60)
            lineTo(x0 + s * 4, cy + 55)
        }
        canvas.drawPath(path, paint)
        canvas.drawText("recoil pattern", x0, cy - 40, txt)
    }

    override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(w, 240) }
}

class FpsView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 30f
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }
    private var last = SystemClock.uptimeMillis()
    private var frames = 0
    private var fps = 0

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis(); frames++
        if (now - last >= 1000) { fps = frames; frames = 0; last = now }
        canvas.drawText("$fps fps", 20f, 40f, p)
        postInvalidateOnAnimation()
    }

    override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(240, 80) }
}

class MainActivity : AppCompatActivity() {

    private lateinit var cfg: Config

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cfg = Config(this)

        val btnOverlay = findViewById<Button>(R.id.btnOverlay)
        val btnScan = findViewById<Button>(R.id.btnScan)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val seekCross = findViewById<SeekBar>(R.id.seekCross)
        val seekAlpha = findViewById<SeekBar>(R.id.seekAlpha)
        val swAutoHide = findViewById<Switch>(R.id.swAutoHide)
        val swFps = findViewById<Switch>(R.id.swFps)
        val txtScan = findViewById<TextView>(R.id.txtScan)
        val txtInfo = findViewById<TextView>(R.id.txtInfo)

        seekCross.progress = cfg.crossSize
        seekAlpha.progress = cfg.alpha
        swAutoHide.isChecked = cfg.autoHide
        swFps.isChecked = cfg.fpsOverlay

        seekCross.setOnSeekBarChangeListener(simple { _, v, _ -> cfg.crossSize = v })
        seekAlpha.setOnSeekBarChangeListener(simple { _, v, _ -> cfg.alpha = v })
        swAutoHide.setOnCheckedChangeListener { _, v -> cfg.autoHide = v }
        swFps.setOnCheckedChangeListener { _, v -> cfg.fpsOverlay = v }

        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(this)) {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ))
            } else toast("Đã có quyền overlay")
        }

        btnScan.setOnClickListener {
            txtScan.text = AntiBan.scan(this).joinToString("\n")
        }

        btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                toast("Cần quyền overlay trước"); return@setOnClickListener
            }
            startForegroundService(Intent(this, OverlayService::class.java))
            startForegroundService(Intent(this, ProcessWatcher::class.java))
            toast("Đã bật aim assist + watcher")
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            stopService(Intent(this, ProcessWatcher::class.java))
            AntiBan.scrub(this)
            toast("Đã tắt")
        }

        txtInfo.text = """
            FF Aim Assist + AntiBan — JBtoAI
            • Crosshair + recoil guide + FPS overlay
            • Anti-ban: root/risky-app/dev-opts/ADB scan
            • Watcher: auto show/hide theo FF
            • Không hook, không mod, không tự bắn
        """.trimIndent()

        txtScan.text = AntiBan.scan(this).joinToString("\n")
    }

    private fun simple(block: (SeekBar, Int, Boolean) -> Unit)
        = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) =
            block(sb!!, p, f)
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }

    private fun toast(s: String) =
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    class OverlayService : Service() {
        companion object {
            const val ACTION_SHOW = "show"
            const val ACTION_HIDE = "hide"
        }

        private lateinit var wm: WindowManager
        private lateinit var cfg: Config
        private var cross: CrosshairView? = null
        private var guide: RecoilGuideView? = null
        private var fps: FpsView? = null
        private var attached = false

        override fun onBind(i: Intent?): IBinder? = null

        override fun onCreate() {
            super.onCreate()
            cfg = Config(this)
            wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            startForeground(1, notif())
            AntiBan.scrub(this)
            attach()
        }

        override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
            when (i?.action) {
                ACTION_HIDE -> detach()
                ACTION_SHOW -> attach()
            }
            return START_STICKY
        }

        private fun attach() {
            if (attached) return
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE

            val cp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }

            cross = CrosshairView(this, cfg).also {
                runCatching { wm.addView(it, cp) }
            }

            val gp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM; y = 120 }

            guide = RecoilGuideView(this, cfg).also {
                runCatching { wm.addView(it, gp) }
            }

            if (cfg.fpsOverlay) {
                val fp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply { gravity = Gravity.TOP or Gravity.END; x = 20; y = 60 }
                fps = FpsView(this).also {
                    runCatching { wm.addView(it, fp) }
                }
            }

            attached = true
        }

        private fun detach() {
            if (!attached) return
            cross?.let { runCatching { wm.removeView(it) } }
            guide?.let { runCatching { wm.removeView(it) } }
            fps?.let { runCatching { wm.removeView(it) } }
            cross = null; guide = null; fps = null
            attached = false
        }

        private fun notif(): Notification {
            val ch = "ffaim"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val c = NotificationChannel(
                    ch, "FF Aim", NotificationManager.IMPORTANCE_LOW
                )
                (getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager).createNotificationChannel(c)
            }
            return Notification.Builder(this, ch)
                .setContentTitle("FF Aim Assist")
                .setContentText("Overlay + AntiBan active")
                .setSmallIcon(android.R.drawable.ic_menu_myplaces)
                .setOngoing(true)
                .build()
        }

        override fun onDestroy() { detach(); super.onDestroy() }
    }

    class ProcessWatcher : Service() {
        private val handler = Handler(Looper.getMainLooper())
        private lateinit var cfg: Config
        private var lastFf = false

        private val tick = object : Runnable {
            override fun run() {
                check()
                handler.postDelayed(this, AntiBan.jitter(3000L))
            }
        }

        override fun onBind(i: Intent?): IBinder? = null

        override fun onCreate() {
            super.onCreate()
            cfg = Config(this)
            startForeground(2, notif())
            handler.post(tick)
        }

        private fun check() {
            val am = getSystemService(Context.ACTIVITY_SERVICE)
                    as ActivityManager
            @Suppress("DEPRECATION")
            val procs = am.runningAppProcesses ?: return
            var ff = false
            for (p in procs) {
                if (p.importance ==
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    if (p.processName.contains("com.dts.freefire")) {
                        ff = true; break
                    }
                }
            }
            if (ff != lastFf) {
                lastFf = ff
                val i = Intent(this, OverlayService::class.java).apply {
                    action = if (ff) OverlayService.ACTION_SHOW
                             else OverlayService.ACTION_HIDE
                }
                runCatching { startService(i) }
            }
        }

        private fun notif(): Notification {
            val ch = "ffwatch"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val c = NotificationChannel(
                    ch, "FF Watch", NotificationManager.IMPORTANCE_MIN
                )
                (getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager).createNotificationChannel(c)
            }
            return Notification.Builder(this, ch)
                .setContentTitle("FF Watcher")
                .setContentText("Theo dõi FF foreground")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()
        }

        override fun onDestroy() {
            handler.removeCallbacks(tick); super.onDestroy()
        }
    }
}