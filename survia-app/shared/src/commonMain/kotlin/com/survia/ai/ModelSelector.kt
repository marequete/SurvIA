package com.survia.ai

enum class ModelTier {
    GEMMA_4_E4B_IT,  // 8 Go+ RAM : Multimodal complet (Texte + Vision + Outils) ~2.5 Go INT4
    GEMMA_4_E2B_IT,  // 6-7 Go RAM : Modèle allégé ~1.3 Go INT4
    UNSUPPORTED       // < 6 Go RAM : Appareil insuffisant pour l'inférence locale
}

data class DeviceHardwareProfile(
    val totalRamGb: Double,
    val freeStorageGb: Double,
    val hasGpuAcceleration: Boolean,
    val isRunningOnBatterySaving: Boolean
)

object ModelSelector {

    /**
     * Détermine automatiquement le meilleur modèle et les paramètres de quantification
     * selon les capacités réelles du smartphone.
     */
    fun resolveTargetModel(profile: DeviceHardwareProfile): ModelTier {
        return when {
            profile.totalRamGb >= 7.5 -> ModelTier.GEMMA_4_E4B_IT
            profile.totalRamGb >= 5.5 -> ModelTier.GEMMA_4_E2B_IT
            else -> ModelTier.UNSUPPORTED
        }
    }

    /**
     * Calcule la stratégie de memory-mapping et de cache de contexte (KV Cache).
     */
    fun getMmapConfig(tier: ModelTier, profile: DeviceHardwareProfile): Map<String, Any> {
        val maxContextTokens = if (profile.totalRamGb >= 11.5) 8192 else 4096
        return mapOf(
            "tier" to tier.name,
            "use_mmap" to true,
            "prefer_gpu_delegate" to profile.hasGpuAcceleration,
            "multi_token_prediction_enabled" to profile.hasGpuAcceleration,
            "max_context_tokens" to maxContextTokens,
            "lazy_vision_tower_load" to true // Charge l'encodeur de vision uniquement au premier appel photo
        )
    }
}
