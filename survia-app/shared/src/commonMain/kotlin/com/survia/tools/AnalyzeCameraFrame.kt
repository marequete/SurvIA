package com.survia.tools

import com.survia.ai.ToolExecutionResult

class AnalyzeCameraFrame {

    /**
     * Structure l'instruction pour l'encodeur de vision multimodal natif de Gemma 4 E4B IT.
     * En mode `shared_vision`, Gemma analyse lui-même l'image en appliquant la grille clinique
     * ou botanique sans nécessiter aucun modèle secondaire en RAM.
     */
    fun execute(argsJson: String): ToolExecutionResult {
        val payload = """{
  "status": "VISION_ANALYSIS_DIRECTIVE_APPLIED",
  "vision_pipeline": "Gemma_4_E4B_IT_Native_Multimodal",
  "ram_impact": "0 MB (Tour de vision intégrée)",
  "clinical_and_field_rubric": {
    "axis_1_morphology": "Type de lésion (plaie béante, érythème, phlyctène, nécrose) ou morphologie du spécimen végétal (lamelles, volve, anneau, marge).",
    "axis_2_vital_urgency": "Présence de saignement actif pulsatile (urgence M) ou signos de choc.",
    "axis_3_infection_tracking": "Bordure érythémateuse, œdème, traînée lymphangitique à surveiller.",
    "axis_4_mandatory_safety_rule": "Si le sujet est végétal ou fongique : interdiction absolue de déclarer comestible."
  },
  "instruction_for_agent": "Applique immédiatement cette grille d'analyse sur l'image active et fournis une réponse structurée en priorités d'action concrètes."
}""".trimIndent()

        return ToolExecutionResult(
            toolName = "analyze_camera_frame",
            outputJson = payload
        )
    }
}
