package com.survia.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.survia.android.ai.GemmaModelManager
import com.survia.android.ai.ModelState
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
                SurvIAMainScreen(
                    modelManager = modelManager,
                    poiStore = poiStore,
                    getCurrentCoords = { lastKnownCoords },
                    onOpenOrganicMaps = { openOrganicMaps(lastKnownCoords) },
                    onExecuteTool = { toolName, argsJson -> router.execute(toolName, argsJson) },
                    onAddPoi = { poi -> poiStore.addPoi(poi) }
                )
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

    // Auto-scroll au dernier message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            SurvIATopBar(
                modelState = modelState,
                onOpenOrganicMaps = onOpenOrganicMaps,
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

                            // Bouton GPS : priorité au résultat du GPS tool pour les requêtes géo
                            var actionBtnText = kbResult.actionButtonText
                            var actionBtnUri  = kbResult.actionButtonUri

                            if (isGeoQuery && actionBtnUri == null) {
                                val gpsResult = onExecuteTool("get_gps_and_map", """{"query":"$userQ"}""")
                                // Afficher le texte GPS dans la réponse seulement si KB n'a pas répondu spécifiquement
                                actionBtnText = gpsResult.actionButtonText ?: actionBtnText
                                actionBtnUri  = gpsResult.actionButtonUri  ?: actionBtnUri
                            }

                            // Bouton GPS hardcodé de fallback (si aucun ne remonte)
                            if (actionBtnUri == null) {
                                when {
                                    isWaterQuery -> {
                                        actionBtnText = "📍 Localiser la source d'eau la plus proche"
                                        actionBtnUri  = "om://map?ll=45.1927,5.7276&n=Source_Eau_Potable"
                                    }
                                    isShelterQuery -> {
                                        actionBtnText = "🏕️ Localiser le refuge le plus proche"
                                        actionBtnUri  = "om://map?ll=45.1950,5.7310&n=Refuge_Belledonne"
                                    }
                                    isGeoQuery -> {
                                        actionBtnText = "🗺️ Ouvrir la carte hors-ligne"
                                        actionBtnUri  = "om://map?ll=45.1885,5.7245&n=Point_Survie"
                                    }
                                }
                            }

                            if (thinkingIdx < messages.size) messages.removeAt(thinkingIdx)
                            messages.add(ChatMessage(
                                isUser = false,
                                text = kbResult.outputJson,
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
            items(messages) { msg -> ChatBubble(msg) }
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
                    is ModelState.Error       -> "⚠️ Erreur: ${s.message}"
                    is ModelState.Checking    -> "Vérification..."
                }
                Text(statusText,
                    color = if (modelState is ModelState.Ready) Color(0xFF10B981) else Color(0xFF38BDF8),
                    fontSize = 11.sp)
            }
            Button(
                onClick = onOpenOrganicMaps,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2D3D)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Carte (om://)", color = Color(0xFF38BDF8), fontSize = 11.sp)
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
fun ChatBubble(msg: ChatMessage) {
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
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(msg.actionButtonUri))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:45.1927,5.7276?q=Point_Survie")))
                                } catch (_: Exception) {}
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

// ─── Utils ────────────────────────────────────────────────────────────────────

private fun containsAny(text: String, keywords: List<String>): Boolean =
    keywords.any { text.contains(it) }
