package com.survia.tools

import com.survia.ai.ToolExecutionResult

class SearchSurvivalKB {

    fun execute(argsJson: String): ToolExecutionResult {
        val q = argsJson.lowercase()

        // ── GARDE-FOU VITAL : végétaux & champignons ──────────────────────────
        val isEdibility = q.contains("champignon") || q.contains("plante") ||
                q.contains("manger") || q.contains("comestible") ||
                q.contains("baie") || q.contains("fruit") || q.contains("cueillir") ||
                q.contains("is_edibility_check\": true")

        if (isEdibility) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "⚠️ DANGER VITAL — Identification de plantes/champignons",
                    listOf(
                        "Je ne peux PAS confirmer qu'une plante ou un champignon est comestible sur photo ou description — des erreurs peuvent être mortelles.",
                        "🍄 Sosies mortels fréquents :",
                        "  • Amanite phalloïde : détruit le foie en 6-24h, mortelle même cuite.",
                        "  • Grande ciguë : paralysie respiratoire rapide.",
                        "  • Cortinaire roux : insuffisance rénale retardée (3 à 14 jours).",
                        "✅ Principe de précaution absolu : en cas de doute, ne consomme rien.",
                        "Si tu as ingéré quelque chose de suspect → appelle le 15 / 112 dès que possible."
                    )
                ),
                isFatalGuardrailTriggered = true
            )
        }

        // ── TRIAGE MÉDICAL MARCH ───────────────────────────────────────────────
        if (q.contains("saignement") || q.contains("blessure") || q.contains("hémorragie") ||
            q.contains("triage") || q.contains("brûlure") || q.contains("fracture") ||
            q.contains("inconscient") || q.contains("urgence médicale")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "🩺 Protocole MARCH — Triage d'urgence",
                    listOf(
                        "M — Massive Bleeding : Pose un garrot 5-7 cm au-dessus de la plaie. Pansement compressif si garrot impossible.",
                        "A — Airway : Libère les voies respiratoires. Subluxation mandibulaire si suspicion rachis.",
                        "R — Respiration : Cherche un pneumothorax (souffle, déviation trachée). Pansement 3 côtés.",
                        "C — Circulation : Pouls radial, recoloration cutanée < 2s, couverture thermique immédiate.",
                        "H — Hypothermia/Head : Protège du froid (maintient la coagulation). Évalue la conscience (AVPU).",
                        "⛔ NE JAMAIS retirer un objet fiché dans le corps.",
                        "⛔ NE PAS faire boire un inconscient ou un patient en état de choc."
                    )
                )
            )
        }

        // ── EAU & HYDRATATION ─────────────────────────────────────────────────
        if (q.contains("eau") || q.contains("hydrat") || q.contains("soif") ||
            q.contains("purif") || q.contains("potable") || q.contains("rivière") || q.contains("source")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "💧 Eau — Collecte et purification",
                    listOf(
                        "Priorité absolue : sans eau potable, tu as 3 jours maximum.",
                        "Sources à privilégier : sources de montagne (en amont de toute habitation), rosée du matin collectée sur feuilles propres, pluie.",
                        "Purification par ébullition : fais bouillir à gros bouillons pendant 1 min (+ 1 min par 1000m d'altitude).",
                        "Pré-filtration : filtre l'eau trouble à travers un tissu fin avant d'ébullitionner.",
                        "Désinfection chimique (si dispo) : 2 gouttes de javel à 8° pour 1L d'eau claire, attends 30 min.",
                        "⚠️ Évite : l'eau stagnante, l'eau près des zones agricoles (pesticides), l'eau de mer (aggrave la déshydratation)."
                    )
                )
            )
        }

        // ── ABRI & THERMIQUE ──────────────────────────────────────────────────
        if (q.contains("abri") || q.contains("froid") || q.contains("hypothermie") ||
            q.contains("bivouac") || q.contains("nuit") || q.contains("température") ||
            q.contains("chaleur") || q.contains("hyperthermie")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "🏕️ Abri — Protection thermique d'urgence",
                    listOf(
                        "Sans abri thermique, tu as 3 heures en conditions froides/humides/venteuses.",
                        "Règle n°1 : isole-toi du sol — la conduction te vole plus de chaleur que l'air froid. Accumule 30 cm de feuilles sèches/branches sous toi.",
                        "Localisation : à l'abri du vent, jamais au fond d'un vallon (humidité, gel), évite les zones de crues.",
                        "Construction rapide : lean-to (branches appuyées contre un arbre couché), creuse si terrain meuble.",
                        "Couverture de survie (si dispo) : face dorée vers l'intérieur pour réfléchir la chaleur corporelle.",
                        "Hypothermie légère : fais bouger la victime doucement, réchauffement progressif (jamais de frottement vigoureux).",
                        "Hyperthermie : mets la victime à l'ombre, mouille la peau, ventile, hydrate si conscient."
                    )
                )
            )
        }

        // ── FEU ───────────────────────────────────────────────────────────────
        if (q.contains("feu") || q.contains("allumer") || q.contains("briquets") ||
            q.contains("silex") || q.contains("tinder") || q.contains("amadou")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "🔥 Feu — Allumage en conditions dégradées",
                    listOf(
                        "Triangle du feu : combustible (amadou fin) + comburant (air, ne pas étouffer) + énergie d'allumage.",
                        "Amadou naturel : écorce de bouleau sèche, duvet de quenouilles, mousse sèche, aiguilles de conifère.",
                        "Méthode briquet/silex : prépare d'abord ton nid d'amadou, puis crée l'étincelle dessus.",
                        "Feu par friction (archet) : bois sec impératif. Planche : saule/lierre. Broche : noisette/saule.",
                        "Protection du feu : creuse un trou ou fais un mur de pierres contre le vent.",
                        "Signalisation : feu avec de la fumée (branche verte, plastique) = signal de détresse universel."
                    )
                )
            )
        }

        // ── NAVIGATION & ORIENTATION ──────────────────────────────────────────
        if (q.contains("orient") || q.contains("boussole") || q.contains("nord") ||
            q.contains("navigat") || q.contains("perdu") || q.contains("chemin") ||
            q.contains("carte") || q.contains("itinéraire")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "🧭 Navigation — S'orienter sans GPS",
                    listOf(
                        "Si tu es perdu : STOP — Stop, Think, Observe, Plan. Ne panique pas, reste visible.",
                        "Trouver le nord sans boussole : pose une brindille verticale, marque l'ombre toutes les 15 min — la ligne entre les deux pointes est l'axe Est-Ouest.",
                        "Étoile polaire (nuit) : repère la Grande Ourse, l'étoile en bout du 'manche' de la Petite Ourse = Nord.",
                        "Suivre l'eau : un ruisseau mène toujours vers une vallée, puis une habitation ou une route.",
                        "Signalisation au sol : forme un X visible du ciel avec des branches, des pierres ou des vêtements.",
                        "Priorité : reste visible pour les secours plutôt que de tenter de te déplacer seul.",
                        "Utilise l'outil GPS de l'app pour obtenir tes coordonnées exactes et les communiquer aux secours."
                    )
                )
            )
        }

        // ── SURVIE URBAINE ────────────────────────────────────────────────────
        if (q.contains("urbain") || q.contains("ville") || q.contains("catastrophe") ||
            q.contains("tremblement") || q.contains("inondation") || q.contains("évacuat") ||
            q.contains("coupure") || q.contains("électricité") || q.contains("gaz")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "🏙️ Survie urbaine — Protocoles d'urgence",
                    listOf(
                        "Évacuation : prends le sac d'urgence (eau 2L/pers, nourriture 3 jours, médicaments, documents, lampe, radio).",
                        "Coupure de gaz : ne rallume JAMAIS toi-même. Coupe le compteur, ouvre les fenêtres, sors du bâtiment.",
                        "Tremblement de terre : Triangle de vie contre un mur porteur ou sous un bureau solide. Ne cours pas vers la sortie pendant les secousses.",
                        "Inondation : monte vers les étages supérieurs. Ne tente pas de traverser une rue inondée (30 cm suffisent à renverser un adulte).",
                        "Coupure électrique longue : les aliments du congélateur tiennent 24-48h sans l'ouvrir. Protège-toi du froid la nuit.",
                        "Communication : SMS passe quand la voix échoue. Radio FM = source officielle d'information d'urgence.",
                        "Point de ralliement : conviens avec ta famille d'un point de rencontre si les téléphones sont coupés."
                    )
                )
            )
        }

        // ── NOURRITURE (hors végétaux suspects) ──────────────────────────────
        if (q.contains("nourrit") || q.contains("faim") || q.contains("manger") ||
            q.contains("chasse") || q.contains("pêche") || q.contains("piège") || q.contains("calorie")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "🍖 Alimentation — Trouver de la nourriture en survie",
                    listOf(
                        "Priorité : sans nourriture, tu as 3 semaines. L'eau et l'abri restent plus urgents.",
                        "Protéines sûres sans connaissance botanique : vers de terre (riches en protéines, fais-les jeûner 24h), insectes (grillons, larves de bois — retire la tête).",
                        "Pêche : un hameçon improvisé (épingle, fil) est plus fiable que la chasse pour un débutant.",
                        "Pièges à collet : fil autour d'un sentier de passage, hauteur poing fermé du sol.",
                        "Cuire impérativement : toute protéine animale sauvage doit être cuite à cœur pour éliminer parasites.",
                        "Végétaux : je ne peux pas identifier de plantes comestibles par description — risque mortel.",
                        "Rationne les efforts : chasser brûle plus de calories que ça n'en apporte souvent."
                    )
                )
            )
        }

        // ── RÉPONSE GÉNÉRALE / RÈGLE DES 3 ───────────────────────────────────
        return ToolExecutionResult(
            toolName = "search_survival_kb",
            outputJson = buildNaturalResponse(
                "🧭 SurvIA — Agent de survie hors-ligne",
                listOf(
                    "Je suis SurvIA, ton agent de survie 100% hors-ligne. Dis-moi ce dont tu as besoin.",
                    "",
                    "📋 Règle des 3 — Priorités de survie :",
                    "  • 3 minutes sans oxygène ou avec hémorragie massive → arrêt cardiaque",
                    "  • 3 heures sans abri thermique par temps froid/humide",
                    "  • 3 jours sans eau potable",
                    "  • 3 semaines sans nourriture",
                    "",
                    "🛠️ Je peux t'aider sur : premiers secours, eau, feu, abri, orientation, survie urbaine.",
                    "Pose ta question ou décris ton urgence !"
                )
            )
        )
    }

    // ── Formateur de réponse en langage naturel ───────────────────────────────
    private fun buildNaturalResponse(title: String, lines: List<String>): String {
        return buildString {
            appendLine(title)
            appendLine()
            lines.forEach { line ->
                if (line.isEmpty()) appendLine()
                else appendLine(line)
            }
        }.trimEnd()
    }
}
