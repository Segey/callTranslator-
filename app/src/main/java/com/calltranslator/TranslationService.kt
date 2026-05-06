package com.calltranslator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

class TranslationService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val CHANNEL_ID = "translation_channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "TranslationService"
        private const val SOURCE_LANG = "es"
        private const val TARGET_LANG = "ru"
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isListening = false
    private var isStopping = false

    private val transcriptBuilder = StringBuilder()
    private var audioFile: File? = null
    private var sessionStartTime: Long = 0

    private lateinit var originalText: TextView
    private lateinit var translatedText: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var fullTranscriptView: TextView

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTranslation()
            ACTION_STOP -> stopTranslation()
        }
        return START_NOT_STICKY
    }

    private fun startTranslation() {
        sessionStartTime = System.currentTimeMillis()
        startForeground(NOTIFICATION_ID, buildNotification("Переводчик активен..."))
        showOverlay()
        startAudioRecording()
        startContinuousSpeechRecognition()
    }

    private fun stopTranslation() {
        isStopping = true
        isListening = false
        speechRecognizer?.destroy()
        speechRecognizer = null
        stopAudioRecording()
        removeOverlay()
        saveAndUploadSession()
        wakeLock?.release()
        stopSelf()
    }

    // ---- OVERLAY ----

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_translation, null)

        originalText = overlayView!!.findViewById(R.id.originalText)
        translatedText = overlayView!!.findViewById(R.id.translatedText)
        scrollView = overlayView!!.findViewById(R.id.scrollView)
        fullTranscriptView = overlayView!!.findViewById(R.id.fullTranscript)

        val closeBtn = overlayView!!.findViewById<ImageButton>(R.id.closeBtn)
        closeBtn.setOnClickListener { stopTranslation() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        windowManager.addView(overlayView, params)
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay", e)
            }
            overlayView = null
        }
    }

    private fun updateOverlay(original: String, translated: String) {
        overlayView?.post {
            originalText.text = "ES: $original"
            translatedText.text = "RU: $translated"

            // Append to full transcript
            val entry = "[${timeNow()}] $original\n→ $translated\n\n"
            fullTranscriptView.append(entry)
            transcriptBuilder.append(entry)

            // Auto-scroll to bottom
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    // ---- SPEECH RECOGNITION ----

    private fun startContinuousSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "SpeechRecognizer not available")
            return
        }
        isListening = true
        startListeningCycle()
    }

    private fun startListeningCycle() {
        if (isStopping || !isListening) return

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    serviceScope.launch {
                        val translated = translate(text)
                        updateOverlay(text, translated)
                    }
                }
                // Immediately restart to keep listening
                if (!isStopping) startListeningCycle()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    overlayView?.post {
                        originalText.text = "ES: $partial..."
                    }
                }
            }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "no speech"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "timeout"
                    SpeechRecognizer.ERROR_NETWORK -> "network error"
                    else -> "error $error"
                }
                Log.d(TAG, "Recognition $msg, restarting...")
                if (!isStopping) {
                    serviceScope.launch {
                        delay(300)
                        startListeningCycle()
                    }
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "${SOURCE_LANG}-ES")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "${SOURCE_LANG}-ES")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "${SOURCE_LANG}-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Allow up to 10 seconds of speech per segment
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }

        speechRecognizer?.startListening(recognizerIntent)
    }

    // ---- TRANSLATION ----

    private suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=$SOURCE_LANG|$TARGET_LANG"
            val response = URL(url).readText()
            val json = JSONObject(response)
            json.getJSONObject("responseData").getString("translatedText")
        } catch (e: Exception) {
            Log.e(TAG, "Translation error", e)
            "[ошибка перевода]"
        }
    }

    // ---- AUDIO RECORDING ----

    @Suppress("DEPRECATION")
    private fun startAudioRecording() {
        try {
            val dir = File(getExternalFilesDir(null), "recordings")
            dir.mkdirs()
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                .format(Date())
            audioFile = File(dir, "call_$timestamp.mp4")

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }
            mediaRecorder = recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
            Log.d(TAG, "Recording to: ${audioFile!!.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
        }
    }

    private fun stopAudioRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
        }
    }

    // ---- SAVE + UPLOAD ----

    private fun saveAndUploadSession() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                // Save text transcript
                val dir = File(getExternalFilesDir(null), "recordings")
                dir.mkdirs()
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                    .format(Date(sessionStartTime))
                val transcriptFile = File(dir, "transcript_$timestamp.txt")
                transcriptFile.writeText(transcriptBuilder.toString())
                Log.d(TAG, "Transcript saved: ${transcriptFile.absolutePath}")

                // TODO: Upload to Google Drive
                // Requires OAuth token — see README for setup
                // uploadToDrive(transcriptFile, audioFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save session", e)
            }
        }
    }

    // ---- NOTIFICATION ----

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TranslationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Переводчик звонков")
            .setContentText(text)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Стоп", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Переводчик звонков",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Активен во время звонков"
            lightColor = Color.BLUE
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    // ---- WAKE LOCK ----

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CallTranslator::WakeLock"
        ).apply { acquire(60 * 60 * 1000L) } // Max 1 hour
    }

    private fun timeNow(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        speechRecognizer?.destroy()
        mediaRecorder?.release()
        removeOverlay()
        wakeLock?.let { if (it.isHeld) it.release() }
    }
}
