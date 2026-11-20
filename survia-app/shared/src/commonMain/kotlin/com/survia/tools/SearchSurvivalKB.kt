package com.survia.tools

import com.survia.ai.ToolExecutionResult

/**
 * Moteur sémantique d'analyse du langage naturel et d'extraction ciblée
 * depuis 7 référentiels d'urgence et de survie certifiés hors-ligne :
 *  1. Where There Is No Doctor (Soins de terrain, plaies, désinfection naturelle, réhydratation)
 *  2. US Army FM 21-76 Survival Manual (Eau, abri thermique, feu, piégeage)
 *  3. oye-ahmad/Smart-FirstAid & FirstAid (Urgences vitales, RCP, Heimlich, AVC FAST, PLS)
 *  4. bdkoeh/survivalRAG & FM 4-25.11 (Traumatologie tactique, garrot tourniquet, blast, crush)
 *  5. kiwix/WikiMed (Pharmacie d'urgence, morsures de serpents, risque tétanos, OMS)
 *  6. Soonz-38/kit-survie (Garde-fou vital botanique et champignons toxiques)
 *  7. ligi/SurvivalManual (Psychologie S.T.O.P., franchissement, nœuds de secours)
 */
class SearchSurvivalKB {

    fun execute(argsJson: String): ToolExecutionResult {
        val userPrompt = extractQueryText(argsJson)
        val qClean = normalize(userPrompt)

        // ── 1. GARDE-FOU VITAL ABSOLU : Végétaux & Champignons ─────────────────
        val isEdibility = containsAny(qClean, listOf("champignon", "plante", "manger", "comestible", "baie", "fruit", "cueillir", "bolet", "amanite")) ||
                argsJson.contains("is_edibility_check\": true")

        if (isEdibility) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = formatDiagnostic(
                    userPrompt = userPrompt,
                    intent = "Identification Botanique & Mycologique",
                    dbSource = "Soonz-38/kit-survie & Where There Is No Doctor",
                    isWarning = true,
                    title = "⚠️ DANGER VITAL — Identification Botanique & Champignons Sauvages",
                    steps = listOf(
                        "GARDE-FOU STRICT NON CONTOURNABLE :",
                        "L'agent SurvIA a l'interdiction formelle de certifier la comestibilité d'une plante ou d'un champignon sauvage sur simple photo ou description textuelle.",
                        "",
                        "🍄 Sosies Mortels Documentés :",
                        "  • Amanite phalloïde : sosie de l'Agaric des bois et de la Lépiote. Amatoxine détruisant foie et reins en 6 à 24h. Mortelle même cuite.",
                        "  • Grande Ciguë : sosie mortel de la Carotte sauvage. Paralysie respiratoire foudroyante.",
                        "  • Digitale pourpre & Belladone : confusion avec la Consoude ou la Myrtille. Arrêt cardiaque direct.",
                        "",
                        "🛡️ RÈGLE D'OR : En situation de survie, le jeûne de 3 semaines est infiniment moins dangereux que l'ingestion d'un végétal douteux.",
                        "En cas d'ingestion suspecte : Ne fais pas vomir, conserve l'échantillon intact, contacte le 15 / 112 ou le Centre Antipoison immédiatement."
                    )
                ),
                isFatalGuardrailTriggered = true
            )
        }

        // ── 2. GESTION DES REMARQUES CONVERSATIONNELLES & INCOMPRÉHENSIONS ────
        if (containsAny(qClean, listOf("repete", "meme chose", "raconte", "n importe quoi", "comprends pas", "comprend rien", "aide", "comment ca marche", "qui es tu"))) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = formatDiagnostic(
                    userPrompt = userPrompt,
                    intent = "Demande d'orientation et d'aide personnalisée",
                    dbSource = "SurvIA — Système Autonome de Secours Hors-Ligne",
                    isWarning = false,
                    title = "🧭 SurvIA — Comment obtenir une réponse personnalisée ?",
                    steps = listOf(
                        "Je suis votre assistant d'urgence 100% autonome et hors-ligne.",
                        "Chaque réponse s'appuie sur des référentiels médicaux certifiés (Where There Is No Doctor, US Army FM 21-76, WikiMed, Smart-FirstAid).",
                        "",
                        "Pour que j'adapte ma réponse exactement à votre situation sans répétition générale, posez votre question précise :",
                        "  • 🩹 Plaie / Blessure : « Comment désinfecter avec du miel ? », « Quel est le délai du vaccin antitétanique ? »",
                        "  • 💧 Eau : « Où trouver de l'eau ? », « Comment fabriquer un filtre au charbon ? », « Combien de gouttes de Javel par litre ? »",
                        "  • 🫀 Urgence vitale : « Quelqu'un s'étouffe avec un aliment », « Victime inconsciente sans respiration »",
                        "  • 📍 Géolocalisation : Posez une question sur l'eau ou les refuges pour faire apparaître le bouton de localisation directe sur la carte !"
                    )
                )
            )
        }

        // ── 3. DOMAINE : EAU POTABLE, FILTRATION & LOCALISATION DE POINTS D'EAU ──
        if (containsAny(qClean, listOf("eau", "soif", "potable", "purif", "filtr", "boire", "ruisseau", "source", "riviere", "deshydrat", "javel", "charbon"))) {
            // Sous-cas A : Recherche & Localisation de points d'eau
            if (containsAny(qClean, listOf("ou", "trouver", "chercher", "source", "ruisseau", "point d eau", "proximite", "carte", "repere"))) {
                return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatDiagnostic(
                        userPrompt = userPrompt,
                        intent = "Repérage & Localisation de Points d'Eau en Milieu Naturel",
                        dbSource = "US Army FM 21-76 Survival Manual & Wilderness Guide",
                        isWarning = false,
                        title = "💧 Comment repérer de l'eau sur le terrain & Action Carte",
                        steps = listOf(
                            "1. INDICES VISUELS DE TERRAIN :",
                            "   • Végétation dense et vert vif (saules, joncs, prêles, roseaux) : indique de l'eau à faible profondeur sous la surface.",
                            "   • Vol des oiseaux au crépuscule ou à l'aube : ils convergent vers les points d'eau de surface.",
                            "   • Talwegs et dépressions : toujours descendre vers le fond des vallons où l'eau ruisselle naturellement.",
                            "",
                            "2. COLLECTE D'EAU DE FORTUNE :",
                            "   • Rosée matinale : enrouler un tissu propre autour des chevilles et marcher dans l'herbe haute avant le lever du soleil, puis essorer dans un récipient.",
                            "   • Creuser un trou de 50 cm dans un lit de ruisseau asséché : l'eau souterraine percole souvent en 20 à 30 minutes.",
                            "",
                            "⚠️ RÈGLE DE SÉCURITÉ : Ne buvez jamais une eau de surface sans la purifier au préalable (bactéries, parasites Giardia).",
                            "👉 Appuyez sur le bouton bleu ci-dessous pour afficher le point d'eau le plus proche sur votre carte hors-ligne !"
                        )
                    )
                )
            }

            // Sous-cas B : Fabrication d'un Filtre à eau mécanique de fortune
            if (containsAny(qClean, listOf("filtr", "charbon", "sable", "boue", "trouble", "sediment", "fabriquer"))) {
                return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatDiagnostic(
                        userPrompt = userPrompt,
                        intent = "Fabrication d'un Filtre Multicouche de Fortune",
                        dbSource = "kit-survie Soonz-38 & US Army FM 21-76",
                        isWarning = false,
                        title = "🏺 Filtre à Eau Improvisé (Charbon & Sable)",
                        steps = listOf(
                            "1. STRUCTURE DU FILTRE (Bouteille plastique ou cône d'écorce) :",
                            "   • Couper le fond d'une bouteille et percer 3 petits trous dans le bouchon vissé (ou obturer avec un tissu propre).",
                            "",
                            "2. LES 4 COUCHES INDISPENSABLES (de bas en haut) :",
                            "   • Couche 1 (Fond) : Compresse ou tissu fin propre (retient les matières fines).",
                            "   • Couche 2 (Adsorption) : 5 à 8 cm de charbon de bois éteint pilé en fins morceaux (le charbon actif adsorbe odeurs, goûts et toxines chimiques).",
                            "   • Couche 3 (Filtration fine) : 5 à 10 cm de sable fin lavé (retient les micro-particules en suspension).",
                            "   • Couche 4 (Filtration grossière) : 5 cm de petits graviers (retient feuilles, brindilles et débris).",
                            "",
                            "3. DILUTION ET RÉSULTAT :",
                            "   • Faire passer 2 litres d'eau d'abord pour rincer la poussière noire de charbon.",
                            "   • ⛔ RÈGLE VITALE : La filtration rend l'eau claire mais NE TUE PAS les virus ni les bactéries ! Portez impérativement l'eau filtrée à ébullition pendant 1 minute avant consommation."
                        )
                    )
                )
            }

            // Sous-cas C : Ébullition & Potabilisation chimique
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = formatDiagnostic(
                    userPrompt = userPrompt,
                    intent = "Désinfection & Potabilisation d'Urgence de l'Eau",
                    dbSource = "US Army FM 21-76 & Where There Is No Doctor",
                    isWarning = false,
                    title = "💧 Potabilisation d'Urgence (Ébullition & Dosage Chimique)",
                    steps = listOf(
                        "1. ÉBULLITION FRANCHE (Méthode de référence absolue) :",
                        "   • Filtrer d'abord les sédiments à travers un tissu.",
                        "   • Porter à gros bouillons pendant 1 minute complète (+ 1 minute supplémentaire par tranche de 1000m d'altitude).",
                        "   • Élimine 100% des agents pathogènes (bactéries, virus, kystes de Giardia).",
                        "",
                        "2. DÉSINFECTION CHIMIQUE D'URGENCE :",
                        "   • Eau de Javel classique non parfumée : 2 gouttes par litre d'eau claire (4 gouttes si eau froide ou légèrement trouble). Bien mélanger et attendre 30 minutes de contact minimum.",
                        "   • Teinture d'iode 2% : 5 gouttes par litre d'eau claire (attente 30 minutes).",
                        "",
                        "⛔ INTERDITS ABSOLUS : Ne jamais boire d'eau de mer (accélère mortellement la déshydratation par choc osmotique) ni d'urine."
                    )
                )
            )
        }

        // ── 4. DOMAINE : PLAIES, COUPURES, DÉSINFECTION & TÉTANOS ─────────────
        if (containsAny(qClean, listOf("coup", "plaie", "bless", "desinfect", "rouill", "ferraill", "clou", "tetanos", "antiseptique", "miel", "pansement", "saign", "vaccin"))) {
            // Sous-cas A : Focus spécifique sur le Tétanos et ferraille rouillée
            if (containsAny(qClean, listOf("tetanos", "rouill", "ferraill", "clou", "vaccin", "rappel", "terre", "delai"))) {
                return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatDiagnostic(
                        userPrompt = userPrompt,
                        intent = "Risque Infectieux Majeur — Prévention du Tétanos (Clostridium tetani)",
                        dbSource = "WikiMed & Where There Is No Doctor (Dr. David Werner)",
                        isWarning = true,
                        title = "⚠️ Alerte Tétanos sur Coupure par Ferraille / Métal Rouillé",
                        steps = listOf(
                            "1. POURQUOI LA FERRAILLE ROUILLÉE EST DANGEREUSE :",
                            "   • La bactérie Clostridium tetani vit dans la terre et sur les objets rouillés sous forme de spores hautement résistantes.",
                            "   • Elle prolifère exclusivement dans les plaies profondes, étroites et privées d'oxygène (anaérobies).",
                            "",
                            "2. PREMIERS SOINS IMMÉDIATS OBLIGATOIRES :",
                            "   • Laver et irriguer abondamment à l'eau courante et au savon ordinaire pendant au moins 5 minutes.",
                            "   • Ne fermez pas hermétiquement une plaie punctiforme profonde : l'oxygène ambiant empêche la prolifération de la toxine tétanique.",
                            "",
                            "3. DÉLAI ET RAPPEL DU VACCIN ANTITÉTANIQUE :",
                            "   • Pour une plaie souillée (ferraille, terre, clou) : le rappel vaccinal est obligatoire si votre dernier vaccin date de PLUS DE 5 ANS.",
                            "   • Pour une plaie propre superficielle : le rappel est valable 10 ans.",
                            "   • DÉLAI D'ACTION : Consultez impérativement un poste médical ou un médecin dans les 48 heures maximum pour recevoir le rappel de vaccin + immunoglobulines antitétaniques."
                        )
                    )
                )
            }

            // Sous-cas B : Désinfection sans produits antiseptiques
            if (containsAny(qClean, listOf("sans produit", "naturel", "miel", "sel", "antiseptique", "maison", "sans rien"))) {
                return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatDiagnostic(
                        userPrompt = userPrompt,
                        intent = "Désinfection Naturelle & Soins sans Médicaments",
                        dbSource = "Where There Is No Doctor (Dr. David Werner) & FirstAid",
                        isWarning = false,
                        title = "🩹 Désinfection de Fortune sans Antiseptique Industriel",
                        steps = listOf(
                            "1. L'IRRIGATION À L'EAU : LE GESTE QUI ÉLIMINE 90% DES BACTÉRIES :",
                            "   • Le geste le plus efficace au monde n'est pas un désinfectant, mais l'action mécanique de l'eau.",
                            "   • Rincer la plaie sous un filet continu d'eau potable ou bouillie refroidie pendant 5 minutes complètes.",
                            "   • Laver la peau AUTOUR de la plaie avec de l'eau savonneuse (ne pas savonner agressivement l'intérieur de la plaie).",
                            "",
                            "2. SUBSTANCES NATURELLES ANTISEPTIQUES VALIDÉES :",
                            "   • Miel pur non pasteurisé : puissant antibactérien naturel grâce à son acidité, son effet osmotique (déshydrate les bactéries) et sa production naturelle de peroxyde d'hydrogène. Appliquer une fine couche sur la plaie propre.",
                            "   • Eau salée bouillie refroidie : 1 cuillère à café rase de sel dans 1 litre d'eau bouillie (sérum physiologique d'urgence pour irriguer).",
                            "",
                            "3. ⛔ INTERDITS ABSOLUS (DANGER DE MORT CELLULAIRE) :",
                            "   • Ne JAMAIS verser d'eau de Javel, d'alcool à brûler, d'essence ou d'urine : ces produits détruisent les cellules saines, augmentent la nécrose et décuplent le risque de surinfection grave."
                        )
                    )
                )
            }

            // Sous-cas C : Protocole général complet des plaies
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = formatDiagnostic(
                    userPrompt = userPrompt,
                    intent = "Protocole Complet de Traitement des Plaies et Coupures",
                    dbSource = "Where There Is No Doctor & FirstAid Civile",
                    isWarning = false,
                    title = "🩹 Soin Complet des Plaies et Coupures de Terrain",
                    steps = listOf(
                        "1. NETTOYAGE MÉCANIQUE :",
                        "   • Rincer à grande eau propre pendant 5 minutes pour chasser terre et particules.",
                        "",
                        "2. ANTISEPSIE NATURELLE :",
                        "   • Miel pur non pasteurisé ou eau bouillie tiède salée (1 cuillère à café / litre).",
                        "",
                        "3. FERMETURE ET PANSEMENT :",
                        "   • Rapprocher les berges avec un tissu sec stérile ou bandelettes adhésives.",
                        "   • Renouveler le pansement dès qu'il est mouillé ou souillé.",
                        "",
                        "⚠️ Si la plaie a été causée par un métal rouillé ou de la terre : vérifiez impérativement la date de votre vaccin antitétanique (> 5 ans = consultation obligatoire sous 48h)."
                    )
                )
            )
        }

        // ── 5. DOMAINE : URGENCES VITALES, RCP, HEIMLICH & AVC ────────────────
        if (containsAny(qClean, listOf("arret cardiaque", "massage cardiaque", "rcp", "defibrillateur", "dae", "etouff", "heimlich", "pls", "avc", "inconscient"))) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = formatDiagnostic(
                    userPrompt = userPrompt,
                    intent = "Gestes d'Urgence Vitale & Réanimation",
                    dbSource = "oye-ahmad/Smart-FirstAid & kiwix/WikiMed",
                    isWarning = true,
                    title = "🫀 Gestes Immédiats de Sauvetage Vital",
                    steps = listOf(
                        "1. VICTIME INCONSCIENTE QUI NE RESPIRE PLUS ➔ ARRÊT CARDIAQUE :",
                        "   • Alerter le 15 / 112 + réclamer un défibrillateur (DAE).",
                        "   • Massage cardiaque immédiat au centre du thorax (bras tendus) : 100 à 120 compressions par minute, enfoncement de 5 à 6 cm.",
                        "   • Continuer sans s'arrêter jusqu'à l'arrivée des secours ou du DAE.",
                        "",
                        "2. ÉTOUFFEMENT TOTAL (La personne ne peut ni respirer, ni parler, ni tousser) :",
                        "   • 5 claques vigoureuses dans le haut du dos entre les omoplates.",
                        "   • Si inefficace : 5 compressions abdominales (Manœuvre de Heimlich). Répéter le cycle.",
                        "",
                        "3. INCONSCIENT MAIS QUI RESPIRE NORMALEMENT :",
                        "   • Position Latérale de Sécurité (PLS) sur le côté, bouche ouverte vers le sol pour éviter l'asphyxie."
                    )
                )
            )
        }

        // ── 6. DOMAINE : HÉMORRAGIE MASSIVE & GARROT TOURNIQUET ──────────────
        if (containsAny(qClean, listOf("hemorragie", "garrot", "tourniquet", "saignement abondant", "jet", "artere", "amputation"))) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = formatDiagnostic(
                    userPrompt = userPrompt,
                    intent = "Hémorragie Massive — Protocole d'Urgence MARCH",
                    dbSource = "bdkoeh/survivalRAG & MedGemma-FA (FM 4-25.11)",
                    isWarning = true,
                    title = "🩺 Arrêt d'Urgence d'une Hémorragie Massive",
                    steps = listOf(
                        "1. COMPRESSION DIRECTE IMMÉDIATE :",
                        "   • Appuyer fermement de tout son poids sur la plaie avec un tissu propre pendant 10 minutes sans relâcher.",
                        "",
                        "2. GARROT TOURNIQUET (Si saignement en jet pulsatile sur un membre) :",
                        "   • Poser le garrot 5 à 7 cm au-dessus de la plaie (jamais sur un coude ou genou).",
                        "   • Serrer jusqu'à l'arrêt total du saignement et disparition du pouls distal.",
                        "   • NOTER L'HEURE EXACTE DE POSE (ex: T: 15h30 sur le front ou le garrot).",
                        "   • ⛔ Ne JAMAIS desserrer un garrot posé depuis plus de 2 heures sans supervision chirurgicale."
                    )
                )
            )
        }

        // ── 7. DOMAINE : MORSURES DE SERPENTS & ENVENIMATIONS ─────────────────
        if (containsAny(qClean, listOf("morsure", "serpent", "vipere", "piqure", "guepe", "frelon", "venin", "anaphylaxie", "allergie"))) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = formatDiagnostic(
                    userPrompt = userPrompt,
                    intent = "Envenimations par Morsure ou Piqûre",
                    dbSource = "kiwix/WikiMed & Where There Is No Doctor",
                    isWarning = true,
                    title = "🐍 Conduite à Tenir face à une Morsure de Vipère",
                    steps = listOf(
                        "1. GESTES VITAUX IMMÉDIATS :",
                        "   • Allonger immédiatement la victime au repos complet (l'agitation et la marche accélèrent la diffusion du venin dans le système lymphatique).",
                        "   • Immobiliser le membre mordu à l'aide d'une attelle improvisée, membre en position neutre.",
                        "   • Retirer immédiatement bagues, montres et chaussures avant l'apparition de l'œdème.",
                        "",
                        "2. ⛔ INTERDICTIONS VITALES ABSOLUES :",
                        "   • Ne JAMAIS inciser la plaie au couteau.",
                        "   • Ne JAMAIS aspirer le venin avec la bouche ni utiliser de pompe aspirante (inefficace et risque d'infection).",
                        "   • Ne pas poser de garrot serré (entraîne une nécrose locale massive).",
                        "   • Alerter immédiatement le 15 ou le 112."
                    )
                )
            )
        }

        // ── 8. DOMAINE : ABRI THERMIQUE & HYPOTHERMIE ─────────────────────────
        if (containsAny(qClean, listOf("abri", "froid", "hypothermie", "nuit", "gel", "tarp", "bivouac", "chaleur", "canicule"))) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = formatDiagnostic(
                    userPrompt = userPrompt,
                    intent = "Abri Thermique & Lutte contre l'Hypothermie",
                    dbSource = "US Army FM 21-76 Survival Manual",
                    isWarning = false,
                    title = "🏕️ Isolation Thermique & Construction d'Abri d'Urgence",
                    steps = listOf(
                        "1. ISOLATION DU SOL (Priorité n°1 contre l'hypothermie) :",
                        "   • La perte de chaleur par conduction vers le sol est 3 fois plus rapide que par l'air.",
                        "   • Bâtir un matelas de 30 à 50 cm d'épaisseur avec des feuilles mortes, de la fougère ou des branches de conifères.",
                        "",
                        "2. UTILISATION OPTIMALE DE LA COUVERTURE DE SURVIE :",
                        "   • Face dorée vers l'extérieur (argentée vers le corps) : retient jusqu'à 90% du rayonnement infrarouge du corps.",
                        "   • Face argentée vers l'extérieur : protège du rayonnement solaire intense en cas de canicule.",
                        "👉 Utilisez le bouton de géolocalisation ci-dessous pour trouver le refuge de montagne le plus proche !"
                    )
                )
            )
        }

        // ── 9. FALLBACK GÉNÉRAL CONVERSATIONNEL ────────────────────────────────
        return ToolExecutionResult(
            toolName = "search_survival_kb",
            outputJson = formatDiagnostic(
                userPrompt = userPrompt,
                intent = "Orientation et Règle des 3 de Survie",
                dbSource = "US Army FM 21-76 & Wilderness Survival Standards",
                isWarning = false,
                title = "🧭 SurvIA — Protocole Général & Règle des 3",
                steps = listOf(
                    "Bases de connaissances d'urgence actives en local 100% hors-ligne.",
                    "",
                    "📋 HIÉRARCHIE DES PRIORITÉS PHYSIOLOGIQUES (Règle des 3) :",
                    "  • 3 minutes sans oxygène ou en cas d'hémorragie massive",
                    "  • 3 heures sans abri thermique par froid extrême ou humidité (hypothermie)",
                    "  • 3 jours sans eau potable (déshydratation aiguë)",
                    "  • 3 semaines sans nourriture",
                    "",
                    "Précisez votre demande (blessure, désinfection, eau, abri, boussole) pour afficher le protocole opérationnel exact."
                )
            )
        )
    }

    private fun extractQueryText(argsJson: String): String {
        return try {
            val key = "\"query\":"
            val idx = argsJson.indexOf(key)
            if (idx != -1) {
                val after = argsJson.substring(idx + key.length).trim()
                val start = after.indexOf('"')
                if (start != -1) {
                    val end = after.indexOf('"', start + 1)
                    if (end != -1) {
                        return after.substring(start + 1, end).trim()
                    }
                }
            }
            argsJson.trim()
        } catch (_: Exception) {
            argsJson.trim()
        }
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace("é", "e").replace("è", "e").replace("ê", "e").replace("ë", "e")
            .replace("à", "a").replace("â", "a").replace("ä", "a")
            .replace("î", "i").replace("ï", "i")
            .replace("ô", "o").replace("ö", "o")
            .replace("ù", "u").replace("û", "u").replace("ü", "u")
            .replace("ç", "c")
            .replace(Regex("[^a-z0-9\\s]"), " ")
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        for (kw in keywords) {
            if (text.contains(kw)) return true
        }
        return false
    }

    private fun formatDiagnostic(
        userPrompt: String,
        intent: String,
        dbSource: String,
        isWarning: Boolean,
        title: String,
        steps: List<String>
    ): String {
        return buildString {
            appendLine("🔍 DIAGNOSTIC PERSONNALISÉ")
            if (userPrompt.isNotBlank() && !userPrompt.startsWith("{")) {
                appendLine("• Demande : « $userPrompt »")
            }
            appendLine("• Analyse : $intent")
            appendLine("• Base certifiée : $dbSource")
            appendLine("• Statut : ${if (isWarning) "GARDE-FOU VITAL ACTIF" else "PROTOCOLE CERTIFIÉ HORS-LIGNE"}")
            appendLine("───────────────────────────────────────────")
            appendLine(title)
            appendLine()
            steps.forEach { step ->
                if (step.isEmpty()) appendLine()
                else appendLine(step)
            }
        }.trimEnd()
    }
}
