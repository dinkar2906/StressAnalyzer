package com.example.finalstress

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

// ══════════════════════════════════════════════════════════════════════════════
// ⚠️  UPDATE this every time ngrok restarts (URL changes on free plan)
// ══════════════════════════════════════════════════════════════════════════════
const val SERVER_URL = "https://doddering-unvenially-ingrid.ngrok-free.dev"

// ── Palette ────────────────────────────────────────────────────────────────────
private val Navy      = Color(0xFF0D1B2A)
private val DeepBlue  = Color(0xFF1B2A4A)
private val Teal      = Color(0xFF00C9B1)
private val CardBg    = Color(0xFF162033)
private val TextPri   = Color(0xFFE8F4F8)
private val TextSec   = Color(0xFF7A9BB5)
private val LowStress = Color(0xFF00E5A0)
private val MidStress = Color(0xFFFFCC00)
private val HiStress  = Color(0xFFFF5252)

fun stressColor(pct: Int): Color = when {
    pct < 40 -> LowStress
    pct < 65 -> MidStress
    else      -> HiStress
}

fun stressLabel(pct: Int): String = when {
    pct < 40 -> "Calm 😌"
    pct < 65 -> "Moderate 😐"
    else      -> "High Stress 😟"
}

// ── OkHttp with ngrok header (required on free tier) ──────────────────────────
val httpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .addInterceptor { chain ->
        val req = chain.request().newBuilder()
            .addHeader("ngrok-skip-browser-warning", "true")
            .build()
        chain.proceed(req)
    }
    .build()

// ── Theme ──────────────────────────────────────────────────────────────────────
@Composable
fun StressAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Teal, background = Navy, surface = CardBg),
        typography = Typography(
            headlineLarge  = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold,     color = TextPri),
            headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextPri),
            bodyLarge      = TextStyle(fontSize = 15.sp, color = TextPri),
            bodyMedium     = TextStyle(fontSize = 13.sp, color = TextSec)
        ),
        content = content
    )
}

// ── Activity ───────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 0)
        setContent {
            StressAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Navy) { MainScreen() }
            }
        }
    }
}

// ── Main Screen ────────────────────────────────────────────────────────────────
@Composable
fun MainScreen() {

    var voiceStress  by remember { mutableStateOf<Int?>(null) }
    var voiceEmotion by remember { mutableStateOf("") }
    var isRecording  by remember { mutableStateOf(false) }
    var recordTime   by remember { mutableStateOf(0) }
    var voiceLoading by remember { mutableStateOf(false) }
    var voiceError   by remember { mutableStateOf("") }

    var showCamera   by remember { mutableStateOf(false) }
    var faceStress   by remember { mutableStateOf(0) }
    var faceEmotion  by remember { mutableStateOf("") }
    var ecgStress    by remember { mutableStateOf(0) }
    var finalStress  by remember { mutableStateOf(0) }
    var faceLoading  by remember { mutableStateOf(false) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val scope          = rememberCoroutineScope()
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Navy, DeepBlue, Color(0xFF0A1628))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text("Stress Monitor", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(4.dp))
            Text("Voice & face analysis", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))

            StepRow(
                step1Done   = voiceStress != null,
                step2Done   = showCamera,
                step3Done   = voiceStress != null && faceStress > 0
            )
            Spacer(Modifier.height(24.dp))

            // ── VOICE CARD ─────────────────────────────────────────────────────
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎤", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Voice Analysis", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.height(16.dp))

                if (voiceStress == null) {
                    Button(
                        onClick = {
                            scope.launch {
                                voiceError  = ""
                                isRecording = true
                                recordTime  = 0
                                val audioFile = File(context.cacheDir, "voice.3gp")
                                val recorder  = startRecording(context, audioFile)
                                repeat(5) { i -> delay(1000); recordTime = i + 1 }
                                recorder?.stop()
                                recorder?.release()
                                isRecording  = false
                                voiceLoading = true
                                try {
                                    val result   = sendAudioToServer(audioFile)
                                    voiceStress  = result.first
                                    voiceEmotion = result.second
                                } catch (e: Exception) {
                                    voiceError = "❌ Server error — is ngrok running?"
                                }
                                voiceLoading = false
                            }
                        },
                        enabled  = !isRecording && !voiceLoading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Teal)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Navy)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                isRecording  -> "Recording… ${recordTime}s / 5s"
                                voiceLoading -> "Analyzing…"
                                else         -> "Record 5 seconds"
                            },
                            color = Navy, fontWeight = FontWeight.Bold
                        )
                    }

                    if (isRecording) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress   = { recordTime / 5f },
                            modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color      = Teal,
                            trackColor = CardBg
                        )
                    }

                    if (voiceLoading) {
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator(color = Teal, modifier = Modifier.size(28.dp))
                    }

                    if (voiceError.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(voiceError, color = HiStress, fontSize = 13.sp)
                    }

                } else {
                    AnimatedVisibility(visible = true, enter = fadeIn(tween(600))) {
                        Column {
                            StressResultRow(
                                label = "Voice — ${voiceEmotion.replaceFirstChar { it.uppercase() }}",
                                pct   = voiceStress!!
                            )
                            Spacer(Modifier.height(10.dp))
                            TextButton(onClick = {
                                voiceStress = null; voiceEmotion = ""
                                showCamera  = false; faceStress = 0; finalStress = 0
                            }) {
                                Text("↺  Re-record", color = TextSec, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── FACE CARD ──────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = voiceStress != null,
                enter   = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 2 }
            ) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📷", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("Face Analysis", style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(Modifier.height(16.dp))

                    if (!showCamera) {
                        Button(
                            onClick  = { showCamera = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Teal)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Navy)
                            Spacer(Modifier.width(8.dp))
                            Text("Open Camera", color = Navy, fontWeight = FontWeight.Bold)
                        }
                    } else {

                        val previewView = remember { PreviewView(context) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                        }

                        LaunchedEffect(Unit) {
                            val future = ProcessCameraProvider.getInstance(context)
                            future.addListener({
                                val provider = future.get()
                                val preview  = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .build()
                                imageCapture = capture
                                try {
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_FRONT_CAMERA,
                                        preview,
                                        capture
                                    )
                                } catch (e: Exception) { e.printStackTrace() }
                            }, ContextCompat.getMainExecutor(context))
                        }

                        // Take a photo + send every 3 seconds
                        LaunchedEffect(imageCapture) {
                            while (true) {
                                delay(3000)
                                val capture   = imageCapture ?: continue
                                faceLoading   = true
                                val photoFile = File(context.cacheDir, "face.jpg")
                                val options   = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                capture.takePicture(
                                    options,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(out: ImageCapture.OutputFileResults) {
                                            scope.launch {
                                                try {
                                                    val result  = sendImageToServer(photoFile)
                                                    faceStress  = result.first
                                                    faceEmotion = result.second
                                                    ecgStress   = (40..60).random()
                                                    finalStress = (
                                                            0.40 * (voiceStress ?: 50) +
                                                                    0.40 * faceStress +
                                                                    0.20 * ecgStress
                                                            ).toInt()
                                                } catch (_: Exception) {
                                                } finally { faceLoading = false }
                                            }
                                        }
                                        override fun onError(e: ImageCaptureException) { faceLoading = false }
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        val faceAnim by animateFloatAsState(faceStress / 100f, tween(800), label = "faceAnim")
                        val ecgAnim  by animateFloatAsState(ecgStress  / 100f, tween(800), label = "ecgAnim")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StressMeter("Face", faceStress, faceEmotion, faceAnim)
                            StressMeter("ECG (sim)", ecgStress, "", ecgAnim)
                        }

                        if (faceLoading) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = Teal, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Analyzing face…", fontSize = 12.sp, color = TextSec)
                            }
                        }
                    }
                }
            }

            // ── FINAL SCORE ────────────────────────────────────────────────────
            if (voiceStress != null && faceStress > 0) {
                Spacer(Modifier.height(16.dp))
                AnimatedVisibility(
                    visible = true,
                    enter   = fadeIn(tween(700)) + slideInVertically(tween(700)) { it / 2 }
                ) {
                    SectionCard {
                        Text("📊  Final Stress Score", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(14.dp))
                        val finalAnim by animateFloatAsState(finalStress / 100f, tween(1000), label = "finalAnim")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                                CircularProgressIndicator(
                                    progress    = { finalAnim },
                                    modifier    = Modifier.fillMaxSize(),
                                    strokeWidth = 9.dp, strokeCap = StrokeCap.Round,
                                    color       = stressColor(finalStress), trackColor = CardBg
                                )
                                Text(
                                    "$finalStress%",
                                    fontSize   = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = stressColor(finalStress)
                                )
                            }
                            Spacer(Modifier.width(20.dp))
                            Column {
                                Text(stressLabel(finalStress), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = stressColor(finalStress))
                                Spacer(Modifier.height(8.dp))
                                Text("🎤 Voice  ${voiceStress}%", style = MaterialTheme.typography.bodyMedium)
                                Text("📷 Face   ${faceStress}%",  style = MaterialTheme.typography.bodyMedium)
                                Text("❤️ ECG    ${ecgStress}%",   style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                Text("Voice 40% · Face 40% · ECG 20%", fontSize = 11.sp, color = TextSec)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// NETWORK
// ══════════════════════════════════════════════════════════════════════════════

suspend fun sendAudioToServer(file: File): Pair<Int, String> = withContext(Dispatchers.IO) {
    val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("audio", file.name, file.asRequestBody("audio/3gpp".toMediaTypeOrNull()))
        .build()
    val req = Request.Builder()
        .url("$SERVER_URL/analyze/voice")
        .addHeader("ngrok-skip-browser-warning", "true")
        .post(body)
        .build()
    val json = JSONObject(httpClient.newCall(req).execute().body?.string() ?: "{}")
    Pair(json.optInt("stress", 50), json.optString("emotion", "unknown"))
}

suspend fun sendImageToServer(file: File): Pair<Int, String> = withContext(Dispatchers.IO) {
    val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("image", file.name, file.asRequestBody("image/jpeg".toMediaTypeOrNull()))
        .build()
    val req = Request.Builder()
        .url("$SERVER_URL/analyze/face")
        .addHeader("ngrok-skip-browser-warning", "true")
        .post(body)
        .build()
    val json = JSONObject(httpClient.newCall(req).execute().body?.string() ?: "{}")
    Pair(json.optInt("stress", 50), json.optString("emotion", "unknown"))
}

// ══════════════════════════════════════════════════════════════════════════════
// RECORDING  ✅ works on API 24+ — no API 31 required
// ══════════════════════════════════════════════════════════════════════════════

fun startRecording(context: Context, outputFile: File): MediaRecorder? {
    return try {
        // API 31+ can use MediaRecorder(context), older must use no-arg constructor
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        r.setAudioSamplingRate(16000)
        r.setOutputFile(outputFile.absolutePath)
        r.prepare()
        r.start()
        r
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// COMPOSABLES
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun StressMeter(label: String, pct: Int, subtitle: String, anim: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
            CircularProgressIndicator(
                progress    = { anim },
                modifier    = Modifier.fillMaxSize(),
                strokeWidth = 9.dp, strokeCap = StrokeCap.Round,
                color       = stressColor(pct), trackColor = CardBg
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$pct%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = stressColor(pct))
                if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 10.sp, color = TextSec)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) { Column(modifier = Modifier.padding(20.dp), content = content) }
}

@Composable
fun StressResultRow(label: String, pct: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(stressLabel(pct), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = stressColor(pct))
        }
        Text("$pct%", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = stressColor(pct))
    }
}

@Composable
fun StepRow(step1Done: Boolean, step2Done: Boolean, step3Done: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepDot("1", "Voice",  step1Done)
        StepLine(step1Done)
        StepDot("2", "Face",   step2Done)
        StepLine(step2Done)
        StepDot("3", "Result", step3Done)
    }
}

@Composable
fun StepDot(number: String, label: String, done: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(50))
                .background(if (done) Teal else CardBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (done) "✓" else number,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = if (done) Navy else TextSec
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = TextSec)
    }
}

@Composable
fun StepLine(active: Boolean) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(2.dp)
            .padding(bottom = 14.dp)
            .background(if (active) Teal else CardBg)
    )
}