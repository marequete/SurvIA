package com.survia.ai

import kotlinx.coroutines.flow.Flow

sealed interface SessionState {
    data object Uninitialized : SessionState
    data class Loading(val progressPercent: Int, val message: String) : SessionState
    data object Ready : SessionState
    data class Generating(val partialText: String) : SessionState
    data class ToolCallDetected(val toolName: String, val argumentsJson: String) : SessionState
    data class Error(val error: String) : SessionState
}

data class ToolExecutionResult(
    val toolName: String,
    val outputJson: String,
    val isFatalGuardrailTriggered: Boolean = false,
    val actionButtonText: String? = null,
    val actionButtonUri: String? = null
)

/**
 * Interface unifiée de l'agent IA on-device (Android LiteRT-LM & iOS Metal/CoreML).
 */
interface AIProvider {
    val state: Flow<SessionState>
    
    /**
     * Initialise la session via Memory Mapping (mmap) directement depuis le fichier local.
     * Temps attendu : < 2 secondes.
     */
    suspend fun initializeWithMmap(modelPath: String, useGpu: Boolean = true): Boolean
    
    /**
     * Envoie un prompt textuel ou multimodal et streame les fragments de réponse (tokens).
     */
    fun sendMessageStream(
        userMessage: String,
        attachedImagePaths: List<String> = emptyList(),
        attachedDocumentsText: String? = null
    ): Flow<String>

    /**
     * Injecte le résultat de l'exécution d'un outil pour que Gemma 4 produise sa réponse finale.
     */
    fun resumeWithToolResult(toolResult: ToolExecutionResult): Flow<String>

    /**
     * Libère les ressources lors de la fermeture de l'application.
     */
    suspend fun closeSession()
}
