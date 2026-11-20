package com.survia.android.ai

import android.app.ActivityManager
import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface ModelState {
    data object Checking : ModelState
    data class NotDownloaded(val requiredMb: Long, val modelTierName: String) : ModelState
    data class Downloading(val progressPercent: Int, val downloadedMb: Long, val totalMb: Long) : ModelState
    data object LoadingMmap : ModelState
    data class Ready(val modelTierName: String) : ModelState
    data class Error(val message: String) : ModelState
}

class GemmaModelManager(private val context: Context) {

    private val _state = MutableStateFlow<ModelState>(ModelState.Checking)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private var llmInference: LlmInference? = null
    private val modelsDir = File(context.filesDir, "models")
    private val modelFile = File(modelsDir, "gemma_ondevice.bin")

    // Détection de la RAM pour sélection du modèle
    val totalRamGb: Double by lazy {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
    }

    val isHighEndDevice: Boolean
        get() = totalRamGb >= 7.5

    val modelTierName: String
        get() = if (isHighEndDevice) "Gemma 4B Multimodal (Haut de Gamme)" else "Gemma 2B INT4 (Milieu de Gamme)"

    // URL de téléchargement selon le tier matériel
    private val modelDownloadUrl: String
        get() = if (isHighEndDevice) {
            "https://huggingface.co/google/gemma-2b-it-gpu-int4/resolve/main/gemma-2b-it-gpu-int4.bin"
        } else {
            "https://huggingface.co/google/gemma-2b-it-cpu-int4/resolve/main/gemma-2b-it-cpu-int4.bin"
        }

    val estimatedSizeMb: Long
        get() = if (isHighEndDevice) 2200L else 1350L

    init {
        checkLocalModel()
    }

    fun checkLocalModel() {
        if (modelFile.exists() && modelFile.length() > 50_000_000L) { // Fichier présent et valide (>50Mo)
            initLlmInference()
        } else {
            _state.value = ModelState.NotDownloaded(estimatedSizeMb, modelTierName)
        }
    }

    suspend fun startDownload() = withContext(Dispatchers.IO) {
        if (!modelsDir.exists()) modelsDir.mkdirs()
        val tempFile = File(modelsDir, "gemma_download.tmp")

        try {
            _state.value = ModelState.Downloading(0, 0, estimatedSizeMb)
            val url = URL(modelDownloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "SurvIA-Android-Agent")
            }

            val totalBytes = if (connection.contentLengthLong > 0) connection.contentLengthLong else estimatedSizeMb * 1024 * 1024
            val totalMb = totalBytes / (1024 * 1024)

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalDownloaded = 0L
                    var lastReportTime = System.currentTimeMillis()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 300) { // Throttle UI updates à ~3x par seconde
                            val percent = ((totalDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                            val downloadedMb = totalDownloaded / (1024 * 1024)
                            _state.value = ModelState.Downloading(percent, downloadedMb, totalMb)
                            lastReportTime = now
                        }
                    }
                    output.flush()
                }
            }

            // Renommer le fichier temporaire en fichier final
            if (modelFile.exists()) modelFile.delete()
            tempFile.renameTo(modelFile)

            // Initialiser LlmInference
            initLlmInference()

        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            _state.value = ModelState.Error("Erreur téléchargement: ${e.localizedMessage ?: "Réseau indisponible"}")
        }
    }

    private fun initLlmInference() {
        _state.value = ModelState.LoadingMmap
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            _state.value = ModelState.Ready(modelTierName)
        } catch (e: Exception) {
            _state.value = ModelState.Error("Erreur initialisation modèle: ${e.message}")
        }
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.Default) {
        val inference = llmInference ?: throw IllegalStateException("Modèle non initialisé")
        inference.generateResponse(prompt)
    }

    fun isReady(): Boolean = _state.value is ModelState.Ready

    fun close() {
        try {
            llmInference?.close()
            llmInference = null
        } catch (_: Exception) {}
    }
}
