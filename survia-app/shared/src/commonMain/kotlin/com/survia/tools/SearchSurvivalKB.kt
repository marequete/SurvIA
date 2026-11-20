package com.survia.tools

import com.survia.ai.ToolExecutionResult

class SearchSurvivalKB {

    fun execute(argsJson: String): ToolExecutionResult {
        val q = argsJson.lowercase()

        // ── GARDE-FOU VITAL ABSOLU : Végétaux & Champignons (Soonz-38/kit-survie) ─────
        val isEdibility = q.contains("champignon") || q.contains("plante") ||
                q.contains("manger") || q.contains("comestible") ||
                q.contains("baie") || q.contains("fruit") || q.contains("cueillir") ||
                q.contains("is_edibility_check\": true")

        if (isEdibility) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "⚠️ DANGER VITAL — Identification Botanique & Mycologique",
                    listOf(
                        "GARDE-FOU STRICT NON CONTOURNABLE :",
                        "L'agent IA a l'interdiction formelle de certifier la comestibilité d'une plante ou d'un champignon sauvage sur simple photo ou description. Les erreurs sont potentiellement létales.",
                        "",
                        "🍄 Sosies Mortels Documentés (Base Kit-Survie / Where There Is No Doctor) :",
                        "  • Amanite phalloïde (Amanita phalloides) : sosie de l'Agaric des bois et de la Lépiote. Toxine amatoxine détruisant foie et reins en 6-24h. Mortelle même cuite à 100°C.",
                        "  • Grande Ciguë (Conium maculatum) : sosie de la Carotte sauvage et du Cerfeuil. Paralysie neuromusculaire ascendante et asphyxie respiratoire foudroyante.",
                        "  • Digitale pourpre & Belladone : confusion fréquente avec la Grande Consoude et la Myrtille. Arrêt cardiaque direct (digitaline) ou choc toxique.",
                        "",
                        "🛡️ RÈGLE D'OR : En situation de survie, le jeûne de 3 semaines est toujours infiniment moins risqué que l'ingestion d'un végétal douteux.",
                        "En cas d'ingestion accidentelle : Conserve l'échantillon intact, ne fais pas vomir sans avis médical, contacte le 15 / 112 ou le Centre Antipoison immédiatement."
                    )
                ),
                isFatalGuardrailTriggered = true
            )
        }

        // ── TRIAGE MÉDICAL D'URGENCE : Protocole MARCH (MedGemma-FA) ──────────
        if (q.contains("saignement") || q.contains("blessure") || q.contains("hémorragie") ||
            q.contains("triage") || q.contains("brûlure") || q.contains("fracture") ||
            q.contains("inconscient") || q.contains("urgence médicale") || q.contains("secours")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "🩺 Protocole de Triage d'Urgence MARCH (MedGemma-FA / Wilderness First Aid)",
                    listOf(
                        "Hiérarchie vitale absolue à appliquer immédiatement :",
                        "",
                        "1. M — Massive Bleeding (Hémorragie massive) :",
                        "   • Compression directe ferme avec compresse stérile ou tissu propre pendant 10 min sans relâcher.",
                        "   • Garrot tourniquet si membre avec saignement en jet pulsatile : 5 à 7 cm au-dessus de la plaie (jamais sur une articulation). Noter l'heure de pose.",
                        "",
                        "2. A — Airway (Voies aériennes) :",
                        "   • Basculer délicatement la tête en arrière et élever le menton (subluxation mandibulaire sans torsion si suspicion rachis). Retirer tout corps étranger visible dans la bouche.",
                        "",
                        "3. R — Respiration :",
                        "   • Détecter un pneumothorax ouvert (bruit de succion thoracique) : poser un pansement occlusif 3 côtés pour faire office de valve unidirectionnelle.",
                        "",
                        "4. C — Circulation :",
                        "   • Vérifier le pouls radial et le temps de recoloration cutanée (< 2 secondes). Couvrir d'urgence pour maintenir la volémie.",
                        "",
                        "5. H — Hypothermia / Head (Prévention hypothermie) :",
                        "   • Tout blessé en état de choc perd sa thermorégulation : isoler immédiatement du sol et envelopper dans une couverture de survie (face dorée vers l'extérieur pour conserver la chaleur).",
                        "",
                        "⛔ INTERDICTIONS VITALES :",
                        "  • NE JAMAIS retirer un objet fiché profondément dans le corps (stabiliser l'objet avec des pansements autour).",
                        "  • NE PAS donner à boire à une victime inconsciente ou en état de choc."
                    )
                )
            )
        }

        // ── EAU & PURIFICATION (US Army FM 21-76 & Where There Is No Doctor) ──
        if (q.contains("eau") || q.contains("hydrat") || q.contains("soif") ||
            q.contains("purif") || q.contains("potable") || q.contains("rivière") || q.contains("source")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "💧 Purification & Collecte d'Eau en Milieu Dégradé (US Army FM 21-76)",
                    listOf(
                        "Priorité vitale : 3 jours maximum sans eau potable.",
                        "",
                        "1. ÉBULLITION FRANCHE (Méthode certifiée n°1) :",
                        "   • Filtrer d'abord les sédiments à travers un tissu fin ou un filtre improvisé (sable fin, charbon de bois éteint).",
                        "   • Porter à ébullition franche à gros bouillons pendant 1 minute (+ 1 minute supplémentaire par tranche de 1000m d'altitude).",
                        "   • Élimine 100% des bactéries, virus et kystes de protozoaires (Giardia, Cryptosporidium).",
                        "",
                        "2. DÉSINFECTION CHIMIQUE D'URGENCE :",
                        "   • Eau de Javel non parfumée (sans additif) : 2 gouttes par litre d'eau claire (4 gouttes si eau froide ou trouble). Mélanger et attendre 30 minutes minimum.",
                        "   • Teinture d'iode 2% : 5 gouttes par litre d'eau claire, temps d'attente 30 minutes.",
                        "",
                        "3. COLLECTE HORS-LIGNE :",
                        "   • Rosée matinale collectée avec un tissu propre sur l'herbe avant l'aube.",
                        "   • Eau de pluie captée directement sans ruissellement sur toiture polluée.",
                        "⚠️ DANGER : Ne jamais boire d'eau de mer (accélère mortellement la déshydratation) ni d'eau stagnante proche de champs cultivés (pesticides non éliminés par l'ébullition)."
                    )
                )
            )
        }

        // ── ABRI & PROTECTION THERMIQUE (Règle des 3 de Survie) ────────────────
        if (q.contains("abri") || q.contains("froid") || q.contains("hypothermie") ||
            q.contains("bivouac") || q.contains("nuit") || q.contains("température") ||
            q.contains("chaleur") || q.contains("hyperthermie")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "🏕️ Abri Thermique & Survie au Froid Extrême (US Army FM 21-76)",
                    listOf(
                        "Priorité vitale : 3 heures sans abri thermique par conditions froides, humides ou venteuses.",
                        "",
                        "1. ISOLATION DU SOL (Le principe physique n°1) :",
                        "   • La perte de chaleur par conduction vers le sol est 3 fois plus rapide que par l'air.",
                        "   • Créer un matelas de feuilles mortes sèches, fougères ou branches de conifères d'au moins 30 à 50 cm d'épaisseur avant de se coucher.",
                        "",
                        "2. EMPLACEMENT STRATÉGIQUE :",
                        "   • Jamais au fond d'une cuvette (accumulation d'air froid et risque de crue soudaine).",
                        "   • Éviter de se placer sous des branches mortes suspendues (widowmakers).",
                        "   • S'abriter face au vent dominant avec un réflecteur (muret de pierres ou rondins derrière le feu).",
                        "",
                        "3. COUVERTURE DE SURVIE :",
                        "   • Face dorée vers l'extérieur (face argentée vers le corps) : piège les infrarouges et réfléchit jusqu'à 90% du rayonnement thermique corporel.",
                        "   • Face argentée vers l'extérieur : protège du rayonnement solaire intense (coup de chaud)."
                    )
                )
            )
        }

        // ── FEU & SIGNAUX DE DÉTRESSE (Bushcraft & Rescue Standards) ───────────
        if (q.contains("feu") || q.contains("allumer") || q.contains("briquet") ||
            q.contains("silex") || q.contains("tinder") || q.contains("amadou") || q.contains("sos") || q.contains("signal")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "🔥 Allumage de Feu en Conditions Dégradées & Signaux Internationaux",
                    listOf(
                        "1. AMADOU VITAL :",
                        "   • Écorce de bouleau : contient de la bétuline hydrofuge, s'enflamme même sous la pluie.",
                        "   • Bois gras (fatwood / cœur de résineux saturé de résine) : inflammable à l'étincelle de firesteel.",
                        "   • Fendre le bois mort sur pied pour atteindre le cœur parfaitement sec.",
                        "",
                        "2. SIGNAL DE DÉTRESSE UNIVERSEL (Reconnu par les secours aériens) :",
                        "   • Triangle de détresse : 3 feux disposés en triangle équilatéral espacés de 25 mètres.",
                        "   • Fumée dense de jour : jeter des branches fraîches à feuilles vertes ou de la mousse humide sur un foyer bien chaud pour générer une colonne blanche visible à des kilomètres.",
                        "   • Signal sonore / sifflet : 3 coups brefs, répétés toutes les minutes (réponse des secours : 2 coups brefs)."
                    )
                )
            )
        }

        // ── RADIO D'URGENCE, ÉNERGIE SOLAIRE & BATTERIES ──────────────────────
        if (q.contains("radio") || q.contains("fréquence") || q.contains("vhf") ||
            q.contains("pmr") || q.contains("canal") || q.contains("solaire") || q.contains("batterie")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "📻 Radio d'Urgence HF/VHF & Énergie Autonome (iFixit & Ham Handbook)",
                    listOf(
                        "1. FRÉQUENCES RADIO DE SECOURS EUROPÉENNES :",
                        "   • Canal 7-7 PMR446 (Canal 7, sous-ton CTCSS 7 ou 85.4 Hz) : canal d'urgence randonneurs/montagne en France.",
                        "   • Canal 16 VHF Marine (156.800 MHz) : détresse côtière et lacustre (MAYDAY).",
                        "   • 161.300 MHz (Canal E) : fréquence sécurité secours en montagne (Alpes/Pyrénées).",
                        "",
                        "2. GESTION DE BATTERIE DU SMARTPHONE :",
                        "   • Mode avion permanent avec GPS seul activé (le GPS matériel ne consomme que 2-4% de batterie/heure sans antenne cellulaire).",
                        "   • Conserver le smartphone contre le torse (la chaleur corporelle évite la décharge brutale des cellules Lithium par grand froid).",
                        "   • Ne jamais recharger une batterie Lithium gelée sous 0°C (risque de court-circuit interne permanent)."
                    )
                )
            )
        }

        // ── SURVIE URBAINE & CATASTROPHES ─────────────────────────────────────
        if (q.contains("urbain") || q.contains("ville") || q.contains("catastrophe") ||
            q.contains("séisme") || q.contains("inondation") || q.contains("blackout") || q.contains("évacuation")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "🏙️ Survie Urbaine & Catastrophes Majeures (Protection Civile)",
                    listOf(
                        "1. SÉISME :",
                        "   • Règle Drop, Cover, Hold On : se protéger sous un meuble lourd ou près d'un mur porteur (triangle de vie).",
                        "   • Ne jamais emprunter d'ascenseur ni se précipiter dans les cages d'escalier pendant les secousses.",
                        "",
                        "2. INONDATION :",
                        "   • Monter dans les étages avec une lampe et de l'eau. Ne pas descendre dans les sous-sols ni garages.",
                        "   • 30 cm d'eau en mouvement suffisent à emporter une voiture.",
                        "",
                        "3. BLACKOUT TOTAL :",
                        "   • Couper le disjoncteur général pour éviter les surtensions au rétablissement.",
                        "   • Le congélateur plein reste à température sûre pendant 48 heures s'il n'est pas ouvert."
                    )
                )
            )
        }

        // ── ALIMENTATION (Protéines Sûres sans Botanique Douteuse) ────────────
        if (q.contains("nourrit") || q.contains("faim") || q.contains("manger") ||
            q.contains("pêche") || q.contains("piège") || q.contains("calorie")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "🍖 Apport Calorique & Protéines Sûres en Survie",
                    listOf(
                        "Rappel des priorités : 3 semaines de réserve sans nourriture. Ne gaspille pas ton énergie.",
                        "",
                        "1. PROTÉINES SÛRES SANS RISQUE BOTANIQUE :",
                        "   • Vers de terre : très riches en protéines complètes. Les faire dégorger 24h dans de l'eau propre puis cuire impérativement à cœur.",
                        "   • Grillons, sauterelles : retirer la tête, les pattes et les ailes épineuses, griller à cœur pour détruire les parasites intestinaux.",
                        "   • Escargots : faire jeûner plusieurs jours (ils peuvent avoir ingéré des végétaux toxiques) et bouillir longuement.",
                        "",
                        "2. CHASSE vs DÉPENSE ÉNERGÉTIQUE :",
                        "   • Courir après du gibier consomme plus de calories que cela n'en rapporte. Privilégier la pêche statique et les pièges passifs en collet posés le long des coulées d'animaux."
                    )
                )
            )
        }

        // ── PROTOCOLE PAR DÉFAUT / RÈGLE DES 3 ────────────────────────────────
        return ToolExecutionResult(
            toolName = "search_survival_kb",
            outputJson = buildNaturalResponse(
                "🧭 SurvIA — Protocole Général de Survie Hors-Ligne",
                listOf(
                    "Base locale US Army FM 21-76 & MedGemma-FA active à 100%.",
                    "",
                    "📋 LA RÈGLE DES 3 (Priorités Physiologiques Absolues) :",
                    "  • 3 minutes sans oxygène ou en cas d'hémorragie artérielle massive",
                    "  • 3 heures sans abri thermique par conditions dégradées (hypothermie)",
                    "  • 3 jours sans eau potable (déshydratation aiguë)",
                    "  • 3 semaines sans nourriture",
                    "",
                    "Pose ta question sur une urgence, un point d'eau, un protocole médical ou un besoin d'orientation."
                )
            )
        )
    }

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

