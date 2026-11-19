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
import com.survia.tools.SearchSurvivalKB
import com.survia.tools.GetGpsAndMap
import com.survia.tools.AnalyzeCameraFrame
import com.survia.tools.SurvivalToolRouter
import com.survia.tools.GeoCoords
import kotlinx.coroutines.launch

data class ChatMessage(val isUser: Boolean, val text: String, val isWarning: Boolean = false)

class MainActivity : ComponentActivity() {

    private val searchKb = SearchSurvivalKB()
    private val gpsTool = GetGpsAndMap { GeoCoords(45.1885, 5.7245, 1410.0) }
    private val cameraTool = AnalyzeCameraFrame()
    private val router = SurvivalToolRouter(searchKb, gpsTool, cameraTool)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GemmaForegroundService.startSentinel(this)

        setContent {
            SurvIADarkTheme {
                SurvIAMainScreen(
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
    onOpenOrganicMaps: () -> Unit,
    onExecuteTool: suspend (String, String) -> com.survia.ai.ToolExecutionResult
) {
    var inputText by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            ChatMessage(false, "SurvIA initialisé avec succès en mode 100% hors-ligne (Gemma 4 E4B IT via mmap). Les 3 outils natifs (Base de connaissances, GPS/Cartes, Vision) sont actifs.")
        )
    }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111820))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SURVIA • AGENT HORS-LIGNE", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        Text("● 0% Réseau • Gemma 4 E4B (INT4 mmap)", color = Color(0xFF10B981), fontSize = 12.sp)
                    }
                    Button(
                        onClick = onOpenOrganicMaps,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2D3D))
                    ) {
                        Text("Carte (om://)", color = Color(0xFF38BDF8), fontSize = 12.sp)
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = Color(0xFF111820), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Pose une question de survie ou décris une urgence...", color = Color.Gray, fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF090D11),
                            unfocusedContainerColor = Color(0xFF090D11),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
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
                                    val isEdibility = userQ.contains("champignon", true) || userQ.contains("plante", true)
                                    val toolToCall = if (isEdibility) "search_survival_kb" else if (userQ.contains("gps", true) || userQ.contains("eau", true)) "get_gps_and_map" else "search_survival_kb"
                                    val res = onExecuteTool(toolToCall, """{"query":"$userQ"}""")
                                    messages.add(ChatMessage(false, res.outputJson, isWarning = res.isFatalGuardrailTriggered))
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
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
                val bg = when {
                    msg.isWarning -> Color(0xFF7F1D1D)
                    msg.isUser -> Color(0xFF1F2D3D)
                    else -> Color(0xFF111820)
                }
                val border = if (msg.isWarning) Color(0xFFEF4444) else Color(0xFF334155)

                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = bg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg.text,
                        color = if (msg.isWarning) Color(0xFFFECACA) else Color.White,
                        fontSize = 13.sp,
                        fontFamily = if (!msg.isUser) FontFamily.Monospace else FontFamily.Default,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
