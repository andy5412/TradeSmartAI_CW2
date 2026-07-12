package com.andysin.tradesmartai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val GEMINI_API_KEY ="AIzaSyApe45hVRkZDpmAAZyIeTKl5LE6_f9LQjA"

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private lateinit var db: AppDatabase

    // 🌟 宣告 RecyclerView 同 Adapter
    private lateinit var recyclerViewHistory: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        viewFinder.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        // 🌟 綁定 UI 並初始化 RecyclerView
        recyclerViewHistory = findViewById(R.id.recyclerViewHistory)
        recyclerViewHistory.layoutManager = LinearLayoutManager(this)
        historyAdapter = HistoryAdapter(emptyList())
        recyclerViewHistory.adapter = historyAdapter

        db = AppDatabase.getDatabase(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        loadRoomHistoryData()

        // 🌟 1. 啟動時強制要求指紋解鎖！
        showBiometricPrompt()

        findViewById<Button>(R.id.btnCapture).setOnClickListener {
            takePhotoAndAnalyze()
        }
    }

    // ==========================================
    // 🧠 感測器 1：指紋解鎖 (Biometrics)
    // ==========================================
    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(applicationContext, "🔓 身份驗證成功！", Toast.LENGTH_SHORT).show()
                    // 指紋成功先去檢查相機同 GPS 權限
                    checkPermissionsAndStart()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "需解鎖才能使用: $errString", Toast.LENGTH_LONG).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("TradeSmart AI 系統")
            .setSubtitle("請使用指紋/面容解鎖以進行估價")
            .setNegativeButtonText("取消")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    // 檢查相機 + GPS 權限
    private fun checkPermissionsAndStart() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, permissions, 10)
        }
    }

    // 感測器 2：相機 (CameraX)
    private fun startCamera() {
        viewFinder.post {
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(this@MainActivity)
                cameraProviderFuture.addListener({
                    val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(this@MainActivity, cameraSelector, preview)
                    } catch (exc: Exception) {
                        exc.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(this@MainActivity))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ==========================================
    // 🧠 感測器 3：GPS 定位 (Location)
    // ==========================================
    private fun getCurrentLocation(): String {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)

                return if (location != null) {
                    "Lat: ${String.format("%.4f", location.latitude)}, Lng: ${String.format("%.4f", location.longitude)}"
                } else {
                    "無法獲取準確位置"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "無定位權限"
    }

    private fun takePhotoAndAnalyze() {
        val bitmap = viewFinder.bitmap
        if (bitmap == null) {
            Toast.makeText(this, "相機未準備好", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "📸 正在分析畫面...", Toast.LENGTH_SHORT).show()
        val base64Image = bitmapToBase64(bitmap)

        CoroutineScope(Dispatchers.IO).launch {
            callGeminiApi(base64Image)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private suspend fun callGeminiApi(base64Image: String) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY"

        val jsonPayload = """
        {
          "contents": [{
            "parts": [
              {"text": "你是一個專業的二手估價師。請分析圖片中的物品，並嚴格按照以下格式回覆（絕對不要加入任何其他廢話、不要換行）：物品名稱 | 預估二手價(HKD) | 一句簡短分析描述"},
              {
                "inline_data": {
                  "mime_type": "image/jpeg",
                  "data": "$base64Image"
                }
              }
            ]
          }]
        }
        """.trimIndent()

        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        try {
            client.newCall(request).execute().use { response ->
                val responseData = response.body?.string() ?: ""
                if (!response.isSuccessful) return@use

                val jsonObject = JSONObject(responseData)
                val aiText = jsonObject.getJSONArray("candidates")
                    .getJSONObject(0).getJSONObject("content")
                    .getJSONArray("parts").getJSONObject(0).getString("text")

                val cleanText = aiText.replace("\n", "").replace("*", "").trim()
                val parts = cleanText.split("|").map { it.trim() }

                if (parts.size >= 3) {
                    // 🌟 估價成功嗰刻，即刻抽 GPS 座標！
                    val currentLocation = getCurrentLocation()

                    val newItem = ItemEntity(
                        itemName = parts[0],
                        aiEstimatedPrice = parts[1],
                        aiDescription = parts[2],
                        imagePath = "/ai/cloud/scanned",
                        location = currentLocation // 寫入資料庫
                    )
                    db.itemDao().insertItem(newItem)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "✅ 估價與定位完成！", Toast.LENGTH_SHORT).show()
                        loadRoomHistoryData()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 🌟 升級版：用 RecyclerView Adapter 更新畫面
    private fun loadRoomHistoryData() {
        CoroutineScope(Dispatchers.IO).launch {
            val itemList = db.itemDao().getAllItems()
            withContext(Dispatchers.Main) {
                // 將資料一次過傳畀 Adapter，自動刷新 UI
                historyAdapter.updateData(itemList)
                if (itemList.isEmpty()) {
                    Toast.makeText(this@MainActivity, "暫無估價紀錄", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

// 📦 Room 本地數據庫 (Version 2)
@Entity(tableName = "scanned_items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemName: String,
    val aiEstimatedPrice: String,
    val aiDescription: String,
    val imagePath: String,
    val location: String = "未知", // 🌟 新增：存放 GPS
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertItem(item: ItemEntity)

    @Query("SELECT * FROM scanned_items ORDER BY timestamp DESC")
    fun getAllItems(): List<ItemEntity>
}

@Database(entities = [ItemEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tradesmart_db"
                )
                    .fallbackToDestructiveMigration() // 🌟 自動清舊資料升級
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}