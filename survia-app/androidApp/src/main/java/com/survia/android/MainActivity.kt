package com.survia.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.platform.LocalContext
import com.survia.android.service.GemmaForegroundService
import com.survia.android.ai.GemmaModelManager
import com.survia.android.ai.ModelState
import com.survia.tools.SearchSurvivalKB
import com.survia.tools.GetGpsAndMap
import com.survia.tools.AnalyzeCameraFrame
import com.survia.tools.SurvivalToolRouter
import com.survia.tools.GeoCoords
import kotlinx.coroutines.launch

data class ChatMessage(
    val isUser: Boolean,
    val text: String,
    val isWarning: Boolean = false,
    val isThinking: Boolean = false,
    val actionButtonText: String? = null,
    val actionButtonUri: String? = null
)

class MainActivity : ComponentActivity() {

    private val searchKb = SearchSurvivalKB()
    private val gpsTool = GetGpsAndMap { GeoCoords(45.1885, 5.7245, 1410.0) }
    private val cameraTool = AnalyzeCameraFrame()
    private val router = SurvivalToolRouter(searchKb, gpsTool, cameraTool)
    private lateinit var modelManager: GemmaModelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GemmaForegroundService.startSentinel(this)
        modelManager = GemmaModelManager(applicationContext)

        setContent {
            SurvIADarkTheme {
                SurvIAMainScreen(
                    modelManager = modelManager,
                    onOpenOrganicMaps = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("om://map?ll=45.1885,5.7245&n=Point_Survie"))
                        intent.setPackage("app.organicmaps")
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:45.1885,5.7245?q=Point_Survie"))
                            startActivity(geoIntent)
                        }
                    },
                    onExecuteTool = { toolName, argsJson ->
                        router.execute(toolName, argsJson)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        modelManager.close()
    }
}

@Composable
fun SurvIADarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF090D11),
            surface = Color(0xFF111820),
            primary = Color(0xFF10B981),
            secondary = Color(0xFF38BDF8),
            error = Color(0xFFEF4444)
        ),
        content = content
    )
}

@Composable
fun SurvIAMainScreen(
    modelManager: GemmaModelManager,
    onOpenOrganicMaps: () -> Unit,
    onExecuteTool: suspend (String, String) -> com.survia.ai.ToolExecutionResult
) {
    var inputText by remember { mutableStateOf("") }
    val modelState by modelManager.state.collectAsState()
    val listState = rememberLazyListState()
    val messages = remember {
        mutableStateListOf(
            ChatMessage(false, "SurvIA initialisé. Mode 100% hors-ligne actif. Outils de survie (Base de connaissances, GPS/Cartes, Vision) prêts.")
        )
    }
    val coroutineScope = rememberCoroutineScope()

    // Défilement automatique vers le dernier message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
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
                            is ModelState.Ready -> "● Gemma On-Device actif (${s.modelTierName})"
                            is ModelState.Downloading -> "⏳ Téléchargement : ${s.progressPercent}% (${s.downloadedMb}/${s.totalMb} Mo)"
                            is ModelState.LoadingMmap -> "⚡ Chargement mmap..."
                            is ModelState.NotDownloaded -> "○ Moteur local KB actif"
                            is ModelState.Error -> "⚠️ Erreur: ${s.message}"
                            is ModelState.Checking -> "Vérification..."
                        }
                        Text(statusText, color = if (modelState is ModelState.Ready) Color(0xFF10B981) else Color(0xFF38BDF8), fontSize = 11.sp)
                    }
                    Button(
                        onClick = onOpenOrganicMaps,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2D3D)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Carte (om://)", color = Color(0xFF38BDF8), fontSize = 11.sp)
                    }
                }

                // Bandeau d'action de téléchargement automatique si pas encore téléchargé
                if (modelState is ModelState.NotDownloaded) {
                    val notDownloaded = modelState as ModelState.NotDownloaded
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                modelManager.startDownload()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text("Installer modèle local (${notDownloaded.modelTierName} ~${notDownloaded.requiredMb} Mo)", color = Color.White, fontSize = 11.sp)
                    }
                } else if (modelState is ModelState.Downloading) {
                    val downloading = modelState as ModelState.Downloading
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { downloading.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF10B981),
                        trackColor = Color(0xFF1F2D3D)
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color(0xFF111820),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Scénarios d'Urgence Rapides (Inspiré de HAVEN Sanctuary)
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val havenShortcuts = listOf(
                            "💧 Trouver Eau" to "où trouver de l'eau à proximité",
                            "🏺 Filtre Charbon" to "comment fabriquer un filtre a eau au charbon",
                            "🩹 Coupure & Tétanos" to "coupure ferraille rouillee desinfection sans produit",
                            "🩺 Triage MARCH" to "hemorragie massive garrot tourniquet",
                            "🫀 RCP Arrêt" to "arret cardiaque rcp massage",
                            "🏕️ Refuge & Froid" to "refuge abri froid hypothermie"
                        )
                        items(havenShortcuts) { (label, query) ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF1F2D3D),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.clickable {
                                    inputText = query
                                }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Pose une question ou choisis un scénario...", color = Color.Gray, fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF090D11),
                                unfocusedContainerColor = Color(0xFF090D11),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val userQ = inputText
                                    messages.add(ChatMessage(true, userQ))
                                    inputText = ""
                                    coroutineScope.launch {
                                        val thinkingIdx = messages.size
                                        messages.add(ChatMessage(false, "⏳ Analyse en cours...", isThinking = true))

                                        var finalResponse = ""
                                        var isFatal = false

                                        // ÉTAPE 1 : RAG LOCAL STRICT & EXTRACTION GÉOGRAPHIQUE
                                        val qLow = userQ.lowercase()
                                        val isGeoQuery = qLow.contains("gps") || qLow.contains("coordonnées") ||
                                                qLow.contains("où") || qLow.contains("carte") ||
                                                qLow.contains("position") || qLow.contains("eau") || qLow.contains("refuge") || qLow.contains("source")

                                        var actionBtnText: String? = null
                                        var actionBtnUri: String? = null

                                        val isWaterQuery = qLow.contains("eau") || qLow.contains("source") || qLow.contains("soif") || qLow.contains("boire") || qLow.contains("potable")
                                        val isShelterQuery = qLow.contains("refuge") || qLow.contains("abri") || qLow.contains("froid") || qLow.contains("bivouac")

                                        if (isWaterQuery) {
                                            actionBtnText = "📍 Localiser le point d'eau : Source Font-Vive (530m NE)"
                                            actionBtnUri = "om://map?ll=45.1927,5.7276&n=Source_Font_Vive"
                                        } else if (isShelterQuery) {
                                            actionBtnText = "🏕️ Localiser le refuge : Belledonne (1.2km)"
                                            actionBtnUri = "om://map?ll=45.1950,5.7310&n=Refuge_Belledonne"
                                        } else if (isGeoQuery) {
                                            actionBtnText = "🗺️ Ouvrir la carte hors-ligne (Organic Maps)"
                                            actionBtnUri = "om://map?ll=45.1885,5.7245&n=Point_Survie"
                                        }

                                        val kbResult = onExecuteTool("search_survival_kb", """{"query":"$userQ"}""")
                                        isFatal = kbResult.isFatalGuardrailTriggered
                                        finalResponse = kbResult.outputJson

                                        // Remplacement de l'indicateur d'analyse par la réponse finale
                                        if (thinkingIdx < messages.size) {
                                            messages.removeAt(thinkingIdx)
                                        }
                                        messages.add(ChatMessage(false, finalResponse, isWarning = isFatal, actionButtonText = actionBtnText, actionButtonUri = actionBtnUri))
                                    }
                                }
                            },
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
            items(messages) { msg ->
                val isThinking = msg.isThinking
                val bg = when {
                    isThinking    -> Color(0xFF1A2A1A)
                    msg.isWarning -> Color(0xFF7F1D1D)
                    msg.isUser    -> Color(0xFF1A2D45)
                    else          -> Color(0xFF111820)
                }
                val border = when {
                    isThinking    -> Color(0xFF10B981)
                    msg.isWarning -> Color(0xFFEF4444)
                    msg.isUser    -> Color(0xFF38BDF8)
                    else          -> Color(0xFF334155)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                ) {
                    val context = LocalContext.current
                    Card(
                        shape = RoundedCornerShape(
                            topStart = if (msg.isUser) 12.dp else 2.dp,
                            topEnd = if (msg.isUser) 2.dp else 12.dp,
                            bottomStart = 12.dp,
                            bottomEnd = 12.dp
                        ),
                        colors = CardDefaults.cardColors(containerColor = bg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, border),
                        modifier = Modifier.fillMaxWidth(if (msg.isUser) 0.85f else 1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = msg.text,
                                color = when {
                                    isThinking    -> Color(0xFF10B981)
                                    msg.isWarning -> Color(0xFFFECACA)
                                    else          -> Color.White
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
                                        } catch (e: Exception) {
                                            try {
                                                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("geo:45.1927,5.7276?q=Point_Survie"))
                                                context.startActivity(fallback)
                                            } catch (_: Exception) {}
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = msg.actionButtonText,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
