package com.survia.tools

import com.survia.ai.ToolExecutionResult

class SurvivalToolRouter(
    private val searchKbTool: SearchSurvivalKB,
    private val gpsTool: GetGpsAndMap,
    private val cameraVisionTool: AnalyzeCameraFrame
) {
    suspend fun execute(toolName: String, rawArgumentsJson: String): ToolExecutionResult {
        return try {
            when (toolName) {
                "search_survival_kb" -> searchKbTool.execute(rawArgumentsJson)
                "get_gps_and_map" -> gpsTool.execute(rawArgumentsJson)
                "analyze_camera_frame" -> cameraVisionTool.execute(rawArgumentsJson)
                else -> ToolExecutionResult(
                    toolName = toolName,
                    outputJson = """{"status":"error","error":"Outil inconnu : $toolName"}"""
                )
            }
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = toolName,
                outputJson = """{"status":"error","error":"Exception lors de l'exécution : ${e.message}"}"""
            )
        }
    }
}
