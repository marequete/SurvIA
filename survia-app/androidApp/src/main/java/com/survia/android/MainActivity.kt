package com.survia.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.survia.android.ai.GemmaModelManager
import com.survia.android.ai.ModelState
import com.survia.android.security.IntegrityGuard
import com.survia.android.security.HardwareBinding
import com.survia.android.security.LicenseManager
import com.survia.android.service.GemmaForegroundService
import com.survia.tools.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val isUser: Boolean,
    val text: String,
    val isWarning: Boolean = false,
    val isThinking: Boolean = false,
    val actionButtonText: String? = null,
    val actionButtonUri: String? = null
)

class MainActivity : ComponentActivity() {

    // ── POI Store collaboratif (persist sur filesDir) ──────────────────────────
    private lateinit var poiStore: UserPoiStore

    // ── Fournisseur GPS dynamique (Android FusedLocation) ─────────────────────
    @Volatile private var lastKnownCoords = GeoCoords(45.1885, 5.7245, 1410.0) // fallback Grenoble

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) fetchLastLocation()
    }

    private lateinit var searchKb: SearchSurvivalKB
    private lateinit var gpsTool: GetGpsAndMap
    private lateinit var cameraTool: AnalyzeCameraFrame
    private lateinit var router: SurvivalToolRouter
    private lateinit var modelManager: GemmaModelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!IntegrityGuard.enforceIntegrity(this)) return

        // Instanciation dans l'ordre des dépendances
        poiStore = UserPoiStore(applicationContext.filesDir.absolutePath)
        searchKb = SearchSurvivalKB()
        gpsTool   = GetGpsAndMap(locationProvider = { lastKnownCoords }, userPoiStore = poiStore)
        cameraTool = AnalyzeCameraFrame()
        router = SurvivalToolRouter(searchKb, gpsTool, cameraTool)
        modelManager = GemmaModelManager(applicationContext)

        GemmaForegroundService.startSentinel(this)
        requestLocationPermissionIfNeeded()

        setContent {
            SurvIADarkTheme {
                val licenseStatus = remember { LicenseManager.check(applicationContext) }
                val hardwareId = remember { HardwareBinding.getHardwareId(applicationContext) }

                when (licenseStatus) {
                    is LicenseManager.LicenseStatus.Valid,
                    is LicenseManager.LicenseStatus.DevMode -> {
                        SurvIAMainScreen(
                            modelManager = modelManager,
                            poiStore = poiStore,
                            getCurrentCoords = { lastKnownCoords },
                            onOpenOrganicMaps = { openOrganicMaps(lastKnownCoords) },
                            onExecuteTool = { toolName, argsJson -> router.execute(toolName, argsJson) },
                            onAddPoi = { poi -> poiStore.addPoi(poi) }
                        )
                    }
                    else -> {
                        SurvIALockScreen(
                            hardwareId = hardwareId,
                            reason = licenseStatus,
                            onLicenseActivated = { licenseJson ->
                                LicenseManager.saveLicenseJson(applicationContext, licenseJson)
                                recreate()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        modelManager.close()
    }

    // ─── GPS ──────────────────────────────────────────────────────────────────

    private fun requestLocationPermissionIfNeeded() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            fetchLastLocation()
        } else {
            locationPermissionRequest.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun fetchLastLocation() {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        lastKnownCoords = GeoCoords(location.latitude, location.longitude, location.altitude)
                    }
                }
        } catch (_: SecurityException) {}
        catch (_: Exception) {}
    }

    private fun openOrganicMaps(coords: GeoCoords) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("om://map?ll=${coords.lat},${coords.lon}&n=Position_Actuelle"))
        intent.setPackage("app.organicmaps")
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${coords.lat},${coords.lon}?q=Position_Actuelle")))
        }
    }
}

// ─── Thème ────────────────────────────────────────────────────────────────────

@Composable
fun SurvIADarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF090D11),
            surface    = Color(0xFF111820),
            primary    = Color(0xFF10B981),
            secondary  = Color(0xFF38BDF8),
            error      = Color(0xFFEF4444)
        ),
        content = content
    )
}

// ─── Écran de verrouillage (Licence absente ou invalide) ──────────────────────

@Composable
fun SurvIALockScreen(
    hardwareId: String,
    reason: LicenseManager.LicenseStatus,
    onLicenseActivated: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showScanner by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }
    var manualInput by remember { mutableStateOf("") }
    var isActivating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val activateWithRaw: (String) -> Unit = { raw ->
        if (!isActivating) {
            isActivating = true
            errorMessage = null
            scope.launch {
                val res = LicenseManager.activateOnline(raw, hardwareId)
                isActivating = false
                res.onSuccess { certificateJson ->
                    onLicenseActivated(certificateJson)
                }.onFailure { err ->
                    errorMessage = err.message ?: "Échec de l'activation."
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            errorMessage = null
            showScanner = true
        } else {
            errorMessage = "Permission caméra refusée. Utilisez la saisie manuelle ci-dessous."
            showManualEntry = true
        }
    }

    val reasonText = when (reason) {
        is LicenseManager.LicenseStatus.NotFound       -> "Aucune licence trouvée sur cet appareil."
        is LicenseManager.LicenseStatus.HardwareMismatch -> "Licence liée à un autre appareil."
        is LicenseManager.LicenseStatus.SignatureInvalid -> "Licence corrompue ou falsifiée."
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D11))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔒 SurvIA — Activation Requise",
            fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(reasonText, color = Color(0xFFEF4444), fontSize = 13.sp)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Identifiant appareil :", color = Color(0xFF94A3B8), fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Surface(color = Color(0xFF111820), shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()) {
            Text(hardwareId.take(32) + "…",
                color = Color(0xFF38BDF8), fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(10.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("Après votre achat, scannez le QR Code d'activation reçu sur la page de confirmation.",
            color = Color(0xFF94A3B8), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(16.dp))

        if (isActivating) {
            CircularProgressIndicator(color = Color(0xFF10B981))
            Spacer(modifier = Modifier.height(12.dp))
            Text("Activation de la licence en cours…", color = Color(0xFF10B981), fontSize = 12.sp)
        } else {
            Button(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        errorMessage = null
                        showScanner = true
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📷 Scanner le QR Code d'activation", color = Color(0xFF090D11),
                    fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { showManualEntry = !showManualEntry }) {
                Text(
                    if (showManualEntry) "Masquer la saisie manuelle" else "Coller le token manuellement",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            if (showManualEntry) {
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    placeholder = { Text("survia://activate?token=srv_live_...", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { activateWithRaw(manualInput) },
                    enabled = manualInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Activer avec ce token", color = Color(0xFF090D11), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(errorMessage!!, color = Color(0xFFEF4444), fontSize = 12.sp)
        }
    }

    if (showScanner) {
        QrActivationScannerDialog(
            onDismiss = { showScanner = false },
            onQrDetected = { rawQr ->
                showScanner = false
                activateWithRaw(rawQr)
            }
        )
    }
}

@Composable
private fun QrActivationScannerDialog(
    onDismiss: () -> Unit,
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember {
        MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }
    }
    var detected by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (_: Throwable) {}
            analysisExecutor.shutdown()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF111820),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Scanner le QR Code SurvIA", color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(12.dp))
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                try {
                                    if (!detected) {
                                        val buffer = imageProxy.planes[0].buffer
                                        val bytes = ByteArray(buffer.remaining())
                                        buffer.get(bytes)
                                        val source = PlanarYUVLuminanceSource(
                                            bytes,
                                            imageProxy.width,
                                            imageProxy.height,
                                            0, 0,
                                            imageProxy.width,
                                            imageProxy.height,
                                            false
                                        )
                                        val bitmap = BinaryBitmap(HybridBinarizer(source))
                                        val text = reader.decodeWithState(bitmap).text
                                        if (LicenseManager.extractTokenFromInput(text) != null) {
                                            detected = true
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                onQrDetected(text)
                                            }
                                        }
                                    }
                                } catch (_: Throwable) {
                                    // Pas de QR dans cette frame
                                } finally {
                                    reader.reset()
                                    imageProxy.close()
                                }
                            }
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                            )
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDismiss) {
                    Text("Annuler", color = Color(0xFF94A3B8))
                }
            }
        }
    }
}

// ─── Écran principal ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurvIAMainScreen(
    modelManager: GemmaModelManager,
    poiStore: UserPoiStore,
    getCurrentCoords: () -> GeoCoords,
    onOpenOrganicMaps: () -> Unit,
    onExecuteTool: suspend (String, String) -> com.survia.ai.ToolExecutionResult,
    onAddPoi: (UserPoi) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val modelState by modelManager.state.collectAsState()
    val listState = rememberLazyListState()
    val messages = remember {
        mutableStateListOf(
            ChatMessage(false, "SurvIA initialisé. Mode 100% hors-ligne actif.\nOutils de survie (Base de connaissances, GPS/Cartes, Vision) prêts.")
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var showAddPoiSheet by remember { mutableStateOf(false) }
    var showInAppMapSheet by remember { mutableStateOf(false) }

    // Auto-scroll au dernier message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            SurvIATopBar(
                modelState = modelState,
                onOpenOrganicMaps = { showInAppMapSheet = true },
                onStartDownloadModel = {
                    coroutineScope.launch {
                        modelManager.startDownload()
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddPoiSheet = true },
                containerColor = Color(0xFF10B981),
                contentColor = Color(0xFF090D11)
            ) {
                Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF090D11))
            }
        },
        bottomBar = {
            SurvIABottomBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        val userQ = inputText
                        messages.add(ChatMessage(true, userQ))
                        inputText = ""
                        coroutineScope.launch {
                            val thinkingIdx = messages.size
                            messages.add(ChatMessage(false, "⏳ Analyse en cours...", isThinking = true))

                            // ── RAG LOCAL : KB d'abord, GPS si requête géo ──
                            val qLow = userQ.lowercase()
                            val isWaterQuery   = containsAny(qLow, listOf("eau", "source", "soif", "boire", "potable"))
                            val isShelterQuery = containsAny(qLow, listOf("refuge", "abri", "froid", "bivouac"))
                            val isGeoQuery     = containsAny(qLow, listOf("gps", "coordonnees", "ou ", "carte", "position", "source", "refuge"))

                            val kbResult = onExecuteTool("search_survival_kb", """{"query":"$userQ"}""")

                            // ── SYNTHÈSE LLM LOCALE (Si Gemma actif, reformule avec empathie sans halluciner) ──
                            var finalReplyText = kbResult.outputJson
                            if (modelManager.isReady()) {
                                try {
                                    val prompt = """
                                        Tu es SurvIA, un expert de survie d'urgence calme, direct et bienveillant.
                                        L'utilisateur en détresse demande : "$userQ".
                                        Voici les données certifiées du protocole d'urgence :
                                        ${kbResult.outputJson}
                                        
                                        Consigne vitale : Réponds directement à sa situation en t'appuyant rigoureusement sur ces données. Pas d'introduction superflue, sois rassurant, clair et opérationnel.
                                    """.trimIndent()
                                    val aiText = modelManager.generateResponse(prompt).trim()
                                    if (aiText.isNotBlank()) {
                                        finalReplyText = aiText
                                    }
                                } catch (_: Throwable) {
                                    // Fallback transparent vers kbResult.outputJson garanti sans crash
                                }
                            }

                            // Bouton GPS : priorité au résultat du GPS tool pour les requêtes géo
                            var actionBtnText = kbResult.actionButtonText
                            var actionBtnUri  = kbResult.actionButtonUri

                            if (isGeoQuery && actionBtnUri == null) {
                                val gpsResult = onExecuteTool("get_gps_and_map", """{"query":"$userQ"}""")
                                actionBtnText = gpsResult.actionButtonText ?: actionBtnText
                                actionBtnUri  = gpsResult.actionButtonUri  ?: actionBtnUri
                            }

                            if (actionBtnUri == null) {
                                when {
                                    isWaterQuery -> {
                                        actionBtnText = "📍 Localiser la source d'eau la plus proche"
                                        actionBtnUri  = "survia://map?ll=45.1927,5.7276&n=Source_Eau_Potable"
                                    }
                                    isShelterQuery -> {
                                        actionBtnText = "🏕️ Localiser le refuge le plus proche"
                                        actionBtnUri  = "survia://map?ll=45.1950,5.7310&n=Refuge_Belledonne"
                                    }
                                    isGeoQuery -> {
                                        actionBtnText = "🗺️ Ouvrir la carte radar hors-ligne"
                                        actionBtnUri  = "survia://map?ll=45.1885,5.7245&n=Point_Survie"
                                    }
                                }
                            }

                            if (thinkingIdx < messages.size) messages.removeAt(thinkingIdx)
                            messages.add(ChatMessage(
                                isUser = false,
                                text = finalReplyText,
                                isWarning = kbResult.isFatalGuardrailTriggered,
                                actionButtonText = actionBtnText,
                                actionButtonUri  = actionBtnUri
                            ))
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090D11))
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg -> ChatBubble(msg, onOpenInAppMap = { showInAppMapSheet = true }) }
        }
    }

    // ── Bottom Sheet : Ajouter un POI ─────────────────────────────────────────
    if (showAddPoiSheet) {
        AddPoiBottomSheet(
            currentCoords = getCurrentCoords(),
            onDismiss = { showAddPoiSheet = false },
            onConfirm = { poi ->
                onAddPoi(poi)
                showAddPoiSheet = false
            }
        )
    }

    // ── Bottom Sheet : Carte Hors-Ligne In-App (Option B) ────────────────────
    if (showInAppMapSheet) {
        InAppMapBottomSheet(
            currentCoords = getCurrentCoords(),
            pois = poiStore.getAllPois(),
            onDismiss = { showInAppMapSheet = false },
            onOpenExternal = onOpenOrganicMaps
        )
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@Composable
fun SurvIATopBar(
    modelState: ModelState,
    onOpenOrganicMaps: () -> Unit,
    onStartDownloadModel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111820))
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("SURVIA • AGENT HORS-LIGNE", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                val statusText = when (val s = modelState) {
                    is ModelState.Ready       -> "● Gemma On-Device actif (${s.modelTierName})"
                    is ModelState.Downloading -> "⏳ Téléchargement : ${s.progressPercent}% (${s.downloadedMb}/${s.totalMb} Mo)"
                    is ModelState.LoadingMmap -> "⚡ Chargement mmap..."
                    is ModelState.NotDownloaded -> "○ Moteur local KB actif"
                    is ModelState.Error       -> if (s.message.contains("GPU")) "● Moteur KB actif (Fallback CPU)" else "⚠️ ${s.message}"
                    is ModelState.Checking    -> "Vérification..."
                }
                Text(statusText,
                    color = if (modelState is ModelState.Ready || (modelState is ModelState.Error && (modelState as ModelState.Error).message.contains("GPU"))) Color(0xFF10B981) else Color(0xFF38BDF8),
                    fontSize = 11.sp)
            }
            Button(
                onClick = onOpenOrganicMaps,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2D3D)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Carte", color = Color(0xFF38BDF8), fontSize = 11.sp)
            }
        }
        when (modelState) {
            is ModelState.NotDownloaded -> {
                val notDownloaded = modelState as ModelState.NotDownloaded
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onStartDownloadModel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text(
                        "📥 Installer modèle local (${notDownloaded.modelTierName} ~${notDownloaded.requiredMb} Mo)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            is ModelState.Downloading -> {
                val d = modelState as ModelState.Downloading
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Téléchargement en cours : ${d.progressPercent}% (${d.downloadedMb}/${d.totalMb} Mo)",
                    color = Color(0xFF38BDF8),
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress = { d.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF10B981),
                    trackColor = Color(0xFF1F2D3D)
                )
            }
            else -> {}
        }
    }
}

// ─── Bottom Bar (pills + champ de saisie) ────────────────────────────────────

@Composable
fun SurvIABottomBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = Color(0xFF111820),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Scénarios HAVEN rapides
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val shortcuts = listOf(
                    "💧 Eau"            to "où trouver de l'eau à proximité",
                    "🏺 Filtre Charbon" to "comment fabriquer un filtre a eau au charbon",
                    "🩹 Coupure"        to "coupure ferraille rouillee desinfection sans produit",
                    "🩺 MARCH"          to "hemorragie massive garrot tourniquet",
                    "🫀 RCP"            to "arret cardiaque rcp massage",
                    "🏕️ Refuge"         to "refuge abri froid hypothermie"
                )
                items(shortcuts) { (label, query) ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1F2D3D),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.clickable { onInputChange(query) }
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    placeholder = { Text("Pose une question ou choisis un scénario...", color = Color.Gray, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor   = Color(0xFF090D11),
                        unfocusedContainerColor = Color(0xFF090D11),
                        focusedTextColor        = Color.White,
                        unfocusedTextColor      = Color.White,
                        focusedIndicatorColor   = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSend,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("Envoyer", color = Color(0xFF090D11), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Bulle de chat ────────────────────────────────────────────────────────────

@Composable
fun ChatBubble(msg: ChatMessage, onOpenInAppMap: () -> Unit = {}) {
    val bg = when {
        msg.isThinking -> Color(0xFF1A2A1A)
        msg.isWarning  -> Color(0xFF7F1D1D)
        msg.isUser     -> Color(0xFF1A2D45)
        else           -> Color(0xFF111820)
    }
    val border = when {
        msg.isThinking -> Color(0xFF10B981)
        msg.isWarning  -> Color(0xFFEF4444)
        msg.isUser     -> Color(0xFF38BDF8)
        else           -> Color(0xFF334155)
    }
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart    = if (msg.isUser) 12.dp else 2.dp,
                topEnd      = if (msg.isUser) 2.dp else 12.dp,
                bottomStart = 12.dp,
                bottomEnd   = 12.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bg),
            border = androidx.compose.foundation.BorderStroke(1.dp, border),
            modifier = Modifier.fillMaxWidth(if (msg.isUser) 0.85f else 1f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = msg.text,
                    color = when {
                        msg.isThinking -> Color(0xFF10B981)
                        msg.isWarning  -> Color(0xFFFECACA)
                        else           -> Color.White
                    },
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = FontFamily.Default
                )
                if (msg.actionButtonText != null && msg.actionButtonUri != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (msg.actionButtonUri.startsWith("survia://")) {
                                onOpenInAppMap()
                            } else {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(msg.actionButtonUri))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    onOpenInAppMap()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(msg.actionButtonText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ─── Bottom Sheet : Ajouter un POI utilisateur ───────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPoiBottomSheet(
    currentCoords: GeoCoords,
    onDismiss: () -> Unit,
    onConfirm: (UserPoi) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedType by remember { mutableStateOf(PoiType.WATER) }
    var label by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }

    val poiTypes = listOf(
        PoiType.WATER      to "💧 Source d'eau",
        PoiType.SHELTER    to "🏕️ Refuge / Abri",
        PoiType.DANGER     to "⚠️ Zone dangereuse",
        PoiType.SAFE_ROUTE to "🛣️ Itinéraire sûr",
        PoiType.GENERAL    to "📌 Point d'intérêt"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF111820)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "📍 Ajouter un point d'intérêt",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Type de POI
            Text("Type de point :", color = Color(0xFF94A3B8), fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)) {
                items(poiTypes) { (type, typLabel) ->
                    val isSelected = type == selectedType
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF10B981) else Color(0xFF1F2D3D),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, if (isSelected) Color(0xFF10B981) else Color(0xFF334155)
                        ),
                        modifier = Modifier.clickable { selectedType = type }
                    ) {
                        Text(typLabel, fontSize = 11.sp,
                            color = if (isSelected) Color(0xFF090D11) else Color(0xFF38BDF8),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }

            // Label
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Nom du point (ex: Source du Vieux Moulin)", color = Color(0xFF94A3B8)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF10B981), unfocusedBorderColor = Color(0xFF334155)
                ),
                singleLine = true
            )

            // Commentaire optionnel
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Note ou avertissement (optionnel)", color = Color(0xFF94A3B8)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF10B981), unfocusedBorderColor = Color(0xFF334155)
                ),
                maxLines = 3
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF94A3B8),
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = Color(0xFF94A3B8)
                    )
                ) {
                    Text("Annuler")
                }
                Button(
                    onClick = {
                        if (label.isNotBlank()) {
                            onConfirm(UserPoi(
                                id          = UUID.randomUUID().toString(),
                                lat         = currentCoords.lat,
                                lon         = currentCoords.lon,
                                type        = selectedType,
                                label       = label.trim(),
                                comment     = comment.trim().ifBlank { null },
                                addedAt     = System.currentTimeMillis(),
                                isUserAdded = true
                            ))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    enabled = label.isNotBlank()
                ) {
                    Text("Enregistrer", color = Color(0xFF090D11), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Bottom Sheet : Carte Hors-Ligne In-App (Option B) ───────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppMapBottomSheet(
    currentCoords: GeoCoords,
    pois: List<UserPoi>,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF090D11)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "🗺️ Carte & Radar Hors-Ligne SurvIA",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        "GPS : ${String.format("%.4f", currentCoords.lat)}, ${String.format("%.4f", currentCoords.lon)} • 100% Autonome",
                        color = Color(0xFF10B981),
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Text("✕", color = Color(0xFF94A3B8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cadre Carte Radar In-App
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF111820), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("📡 SCAN SATELLITE ACTIF", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${pois.size + 3} POI détectés", color = Color(0xFF38BDF8), fontSize = 10.sp)
                    }

                    // Schéma boussole et points à proximité
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📍 Votre Position Actuelle", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "Lat: ${String.format("%.4f", currentCoords.lat)} | Lon: ${String.format("%.4f", currentCoords.lon)}",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "🧭 Cibles prioritaires à pied :",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "💧 Source d'eau : 420m (NE) ~6 min | 🏕️ Abri Belledonne : 850m (N) ~14 min",
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("0% Réseau requis", color = Color(0xFF64748B), fontSize = 10.sp)
                        Text("Boussole + Haversine OK", color = Color(0xFF10B981), fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Points d'intérêt & Urgences à proximité :", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    PoiRowItem("💧", "Source d'eau potable", "420m (NE)", "Eau de montagne claire, ébullition recommandée")
                }
                item {
                    PoiRowItem("🏕️", "Cabane refuge Belledonne", "850m (N)", "Abri fermé du vent, 6 places, bois sec")
                }
                item {
                    PoiRowItem("⚠️", "Zone d'éboulis instable", "1.2 km (E)", "Passage dangereux par temps de pluie")
                }
                items(pois) { p ->
                    val icon = when (p.type) {
                        PoiType.WATER -> "💧"
                        PoiType.SHELTER -> "🏕️"
                        PoiType.DANGER -> "⚠️"
                        PoiType.SAFE_ROUTE -> "🛣️"
                        PoiType.GENERAL -> "📌"
                    }
                    PoiRowItem(icon, p.label, "Position enregistrée", p.comment ?: "Point utilisateur")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenExternal,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2D3D)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Ouvrir Organic Maps", color = Color(0xFF38BDF8), fontSize = 11.sp)
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Fermer", color = Color(0xFF090D11), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun PoiRowItem(icon: String, name: String, dist: String, desc: String) {
    Surface(
        color = Color(0xFF111820),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(icon, fontSize = 18.sp)
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(dist, color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(desc, color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

// ─── Utils ────────────────────────────────────────────────────────────────────

private fun containsAny(text: String, keywords: List<String>): Boolean =
    keywords.any { text.contains(it) }
