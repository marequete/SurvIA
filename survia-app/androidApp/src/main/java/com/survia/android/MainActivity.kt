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
    val isThinking: Boolean = false
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
    val messages = remember {
        mutableStateListOf(
            ChatMessage(false, "SurvIA initialisé. Mode 100% hors-ligne actif. Outils de survie (Base de connaissances, GPS/Cartes, Vision) prêts.")
        )
    }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111820))
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SURVIA • AGENT HORS-LIGNE", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        val statusText = when (val s = modelState) {
                            is ModelState.Ready -> "● Modèle On-Device : ${s.modelTierName}"
                            is ModelState.Downloading -> "⏳ Téléchargement modèle : ${s.progressPercent}% (${s.downloadedMb}/${s.totalMb} Mo)"
                            is ModelState.LoadingMmap -> "⚡ Initialisation mmap..."
                            is ModelState.NotDownloaded -> "○ Moteur local KB actif (Gemma non installé)"
                            is ModelState.Error -> "⚠️ Erreur: ${s.message}"
                            is ModelState.Checking -> "Vérification..."
                        }
                        Text(statusText, color = if (modelState is ModelState.Ready) Color(0xFF10B981) else Color(0xFF38BDF8), fontSize = 12.sp)
                    }
                    Button(
                        onClick = onOpenOrganicMaps,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2D3D))
                    ) {
                        Text("Carte (om://)", color = Color(0xFF38BDF8), fontSize = 12.sp)
                    }
                }

                // Bandeau d'action de téléchargement automatique si pas encore téléchargé
                if (modelState is ModelState.NotDownloaded) {
                    val notDownloaded = modelState as ModelState.NotDownloaded
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                modelManager.startDownload()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Installer ${notDownloaded.modelTierName} (~${notDownloaded.requiredMb} Mo)", color = Color.White, fontSize = 12.sp)
                    }
                } else if (modelState is ModelState.Downloading) {
                    val downloading = modelState as ModelState.Downloading
                    Spacer(modifier = Modifier.height(8.dp))
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Pose une question de survie ou décris une urgence...", color = Color.Gray, fontSize = 13.sp) },
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

                                    // ÉTAPE 1 : RAG LOCAL STRICT — Extraction systématique des protocoles certifiés
                                    val qLow = userQ.lowercase()
                                    val isGeoQuery = qLow.contains("gps") || qLow.contains("coordonnées") ||
                                            qLow.contains("où") || qLow.contains("carte") ||
                                            qLow.contains("position") || qLow.contains("eau") || qLow.contains("refuge")

                                    val kbResult = onExecuteTool("search_survival_kb", """{"query":"$userQ"}""")
                                    isFatal = kbResult.isFatalGuardrailTriggered

                                    val gpsContext = if (isGeoQuery) {
                                        val gpsResult = onExecuteTool("get_gps_and_map", """{"action":"search_poi"}""")
                                        "\n### COORDONNÉES GPS & POINTS VITAUX (HORS-LIGNE) :\n${gpsResult.outputJson}\n"
                                    } else ""

                                    // ÉTAPE 2 : MÉMOIRE CONVERSATIONNELLE (Historique des 6 derniers échanges)
                                    val recentHistory = messages.filter { !it.isThinking }.takeLast(6).joinToString("\n") { msg ->
                                        if (msg.isUser) "Utilisateur: ${msg.text}" else "SurvIA: ${msg.text}"
                                    }

                                    // ÉTAPE 3 : GÉNÉRATION VIA GEMMA ON-DEVICE (ou Fallback KB si non téléchargé)
                                    if (modelManager.isReady() && !isFatal) {
                                        try {
                                            val strictGemmaPrompt = """
Tu es SurvIA, un Agent IA de Survie et Secourisme 100% Hors-Ligne propulsé par Gemma on-device.
Tu es calme, méthodique, direct et ultra-protecteur.

### PROTOCOLES CERTIFIÉS DE LA BASE LOCALE (SOURCE DE VÉRITÉ NON-ALTÉRABLE) :
${kbResult.outputJson}
$gpsContext
### DIRECTIVES ANTI-HALLUCINATION ABSOLUES :
1. Fonde tes recommandations EXCLUSIVEMENT sur les protocoles certifiés ci-dessus. N'invente aucun geste médical.
2. Si un point d'eau ou refuge GPS est mentionné, guide précisément l'utilisateur avec son azimut et distance.
3. Sois concis et utilise des puces claires pour les gestes vitaux à réaliser immédiatement.

### HISTORIQUE DU DIALOGUE :
$recentHistory

### NOUVELLE QUESTION DE L'UTILISATEUR :
$userQ

Réponse de SurvIA :
""".trimIndent()
                                            finalResponse = modelManager.generateResponse(strictGemmaPrompt)
                                        } catch (e: Exception) {
                                            // Fallback vers les faits bruts de la base en cas d'exception
                                            finalResponse = kbResult.outputJson + if (gpsContext.isNotBlank()) "\n\n$gpsContext" else ""
                                        }
                                    } else {
                                        // Mode Direct / Garde-Fou Vital : Restitution directe des protocoles certifiés
                                        finalResponse = kbResult.outputJson + if (gpsContext.isNotBlank()) "\n\n$gpsContext" else ""
                                    }

                                    // Remplacement de l'indicateur d'analyse par la réponse finale
                                    if (thinkingIdx < messages.size) {
                                        messages.removeAt(thinkingIdx)
                                    }
                                    messages.add(ChatMessage(false, finalResponse, isWarning = isFatal))
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
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090D11))
                .padding(padding)
                .padding(12.dp),
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
                        Text(
                            text = msg.text,
                            color = when {
                                isThinking    -> Color(0xFF10B981)
                                msg.isWarning -> Color(0xFFFECACA)
                                else          -> Color.White
                            },
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontFamily = FontFamily.Default,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
