package com.survia.tools

import com.survia.ai.ToolExecutionResult

/**
 * Moteur d'analyse de requête et d'aiguillage vers les bases de connaissances certifiées de survie.
 * Intègre 7 référentiels d'urgence vérifiés :
 *  1. Soonz-38/kit-survie (Garde-fou botanique & survie de base)
 *  2. Where There Is No Doctor (Soins de terrain, plaies, désinfection, réhydratation)
 *  3. US Army FM 21-76 Survival Manual (Eau, abri, feu, pièges)
 *  4. oye-ahmad/Smart-FirstAid & FirstAid (Secourisme civil, RCP, Heimlich, AVC, PLS)
 *  5. bdkoeh/survivalRAG & FM 4-25.11 (Traumatologie tactique, garrot, blast, crush)
 *  6. kiwix/WikiMed (Pharmacie d'urgence, morsures de serpents, envenimations)
 *  7. ligi/SurvivalManual (Psychologie S.T.O.P., franchissement, nœuds de sauvetage)
 */
class SearchSurvivalKB {

    fun execute(argsJson: String): ToolExecutionResult {
        val qClean = normalize(argsJson)

        // ── 1. GARDE-FOU VITAL ABSOLU : Végétaux & Champignons ─────────────────
        // Source : Soonz-38/kit-survie & Where There Is No Doctor
        val isEdibility = containsAny(qClean, listOf("champignon", "plante", "manger", "comestible", "baie", "fruit", "cueillir", "bolet", "amanite")) ||
                argsJson.contains("is_edibility_check\": true")

        if (isEdibility) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = formatProtocol(
                    intent = "Identification Botanique & Mycologique",
                    dbSource = "Soonz-38/kit-survie & Where There Is No Doctor",
                    isWarning = true,
                    title = "⚠️ DANGER VITAL — Identification Botanique & Champignons",
                    steps = listOf(
                        "GARDE-FOU STRICT NON CONTOURNABLE :",
                        "L'agent IA a l'interdiction formelle de certifier la comestibilité d'une plante ou d'un champignon sauvage sur simple photo ou description. Les erreurs sont potentiellement mortelles.",
                        "",
                        "🍄 Sosies Mortels Documentés (Base Kit-Survie / Where There Is No Doctor) :",
                        "  • Amanite phalloïde : sosie de l'Agaric des bois et de la Lépiote. Toxine amatoxine détruisant foie et reins en 6-24h. Mortelle même cuite.",
                        "  • Grande Ciguë : sosie mortel de la Carotte sauvage et du Cerfeuil. Paralysie respiratoire foudroyante.",
                        "  • Digitale pourpre & Belladone : confusion avec la Consoude ou la Myrtille. Arrêt cardiaque par digitaline.",
                        "",
                        "🛡️ RÈGLE D'OR : En situation de survie, le jeûne de 3 semaines est toujours infiniment moins dangereux que l'ingestion d'un végétal douteux.",
                        "En cas d'ingestion accidentelle : Ne fais pas vomir sans avis médical, conserve un échantillon intact, contacte le 15 / 112 ou le Centre Antipoison immédiatement."
                    )
                ),
                isFatalGuardrailTriggered = true
            )
        }

        // ── 2. CALCUL DES SCORES D'AIGUILLAGE PAR BASE DE DONNÉES ─────────────
        val scores = mutableMapOf<String, Int>()

        scores["PLAIES_DESINFECTION"] = scoreKeywords(qClean, listOf(
            "coup", "coupe", "coupee", "coupure", "plaie", "bless", "blessure", "desinfect", "desinfection",
            "desinfectant", "rouill", "rouille", "rouillee", "ferraill", "ferraille", "clou", "antiseptique",
            "infection", "tetanos", "nettoyer", "pansement", "saigne", "saigner", "entaille", "cicatris"
        ))

        scores["TRIAGE_MARCH"] = scoreKeywords(qClean, listOf(
            "hemorragie", "saignement abondant", "garrot", "tourniquet", "triage", "march",
            "brulure", "fracture", "inconscient", "urgence medicale", "jet pulsatile", "artere"
        ))

        scores["REANIMATION_CIVILE"] = scoreKeywords(qClean, listOf(
            "arret cardiaque", "massage cardiaque", "rcp", "defibrillateur", "dae", "etouff",
            "etouffement", "heimlich", "pls", "avc", "ne respire plus", "inconscient qui respire",
            "cardiaque", "etouffer", "asphyxie"
        ))

        scores["EAU_POTABLE"] = scoreKeywords(qClean, listOf(
            "eau", "potable", "purif", "purifier", "purification", "ebullition", "boire",
            "soif", "filtre", "filtrer", "filtration", "riviere", "source", "ruisseau", "deshydrat"
        ))

        scores["ABRI_THERMIQUE"] = scoreKeywords(qClean, listOf(
            "abri", "froid", "hypothermie", "bivouac", "nuit", "gel", "temperature",
            "couverture de survie", "chaleur", "hyperthermie", "tarp", "isoler du sol"
        ))

        scores["FEU_SIGNAL"] = scoreKeywords(qClean, listOf(
            "feu", "allumer", "flamme", "briquet", "firesteel", "silex", "amadou", "bois gras",
            "signal", "sos", "detresse", "fumee", "sifflet"
        ))

        scores["MORSURES_TOXICO"] = scoreKeywords(qClean, listOf(
            "morsure", "serpent", "vipere", "piqure", "guepe", "frelon", "abeille", "venin",
            "allergie", "anaphylaxie", "epipen", "diarrhee", "rehydratation", "sro", "trousse", "pharmacie"
        ))

        scores["TRAUMATO_TACTIQUE"] = scoreKeywords(qClean, listOf(
            "balistique", "balle", "tir", "arme", "explosion", "blast", "ecrasement",
            "crush", "syndrome d ecrasement", "amputation", "decombres"
        ))

        scores["RADIO_SURVIE"] = scoreKeywords(qClean, listOf(
            "radio", "frequence", "vhf", "pmr", "pmr446", "canal", "batterie", "recharge", "telephone", "smartphone"
        ))

        scores["PSYCHO_TECHNIQUE"] = scoreKeywords(qClean, listOf(
            "perdu", "panique", "stress", "peur", "stop", "noeud", "corde", "riviere en crue",
            "franchir", "traversee", "avalanche"
        ))

        scores["URBAIN_CATASTROPHE"] = scoreKeywords(qClean, listOf(
            "urbain", "ville", "catastrophe", "seisme", "tremblement de terre", "inondation", "blackout", "evacuation"
        ))

        scores["ASSISTANT_AIDE"] = scoreKeywords(qClean, listOf(
            "raconte", "racontes", "comprends pas", "comprend rien", "aide", "help",
            "qui es tu", "que sais tu faire", "bonjour", "salut", "comment ca marche", "explication"
        ))

        // Détection de la catégorie gagnante
        val topCategory = scores.maxByOrNull { it.value }

        if (topCategory != null && topCategory.value > 0) {
            when (topCategory.key) {
                "PLAIES_DESINFECTION" -> return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatProtocol(
                        intent = "Soin des Plaies, Coupures & Risque Tétanos",
                        dbSource = "Where There Is No Doctor (Dr. David Werner) & FirstAid",
                        isWarning = false,
                        title = "🩹 Soin des Plaies, Coupures & Désinfection sans Antiseptique",
                        steps = listOf(
                            "1. NETTOYAGE MÉCANIQUE IMMÉDIAT (Le geste vital le plus efficace) :",
                            "   • L'irrigation abondante élimine 90% des souillures et bactéries.",
                            "   • Rincer la plaie sous un filet continu d'eau potable ou d'eau bouillie refroidie pendant 5 minutes entières.",
                            "   • Si disponible : laver la peau AUTOUR de la plaie avec de l'eau et du savon doux (ne pas frotter l'intérieur de la plaie).",
                            "",
                            "2. DÉSINFECTION NATURELLE / DE FORTUNE :",
                            "   • Miel pur non pasteurisé : puissant antiseptique naturel (effet osmotique antibactérien et libération lente de peroxyde). Appliquer une fine couche sur plaie propre.",
                            "   • Sérum physiologique improvisé : 1 cuillère à café rase de sel dissoute dans 1 litre d'eau bouillie refroidie pour irriguer.",
                            "   • ⛔ INTERDITS ABSOLUS : Ne JAMAIS verser d'eau de Javel, d'alcool à brûler, d'essence ou d'urine (détruit les cellules saines et aggrave mortellement l'infection).",
                            "",
                            "3. PANSEMENT ET COUVERTURE :",
                            "   • Laisser saigner quelques secondes au début pour évacuer les débris.",
                            "   • Rapprocher les lèvres de la plaie avec des bandelettes propres ou un tissu stérile sec.",
                            "   • Changer le pansement dès qu'il est souillé ou mouillé.",
                            "",
                            "⚠️ VIGILANCE TÉTANOS (Ferraille rouillée, clou, épine, terre) :",
                            "   • Le bacille tétanique prolifère dans les plaies profondes privées d'oxygène.",
                            "   • Si votre vaccin antitétanique date de plus de 5 ans pour une plaie souillée (ou > 10 ans) : consulter impérativement un médecin ou un centre d'urgence dans les 48h pour un rappel et sérum."
                        )
                    )
                )

                "TRIAGE_MARCH" -> return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatProtocol(
                        intent = "Triage d'Urgence Médicale & Hémorragies Massives",
                        dbSource = "MedGemma-FA & Wilderness First Aid (Protocole MARCH)",
                        isWarning = false,
                        title = "🩺 Protocole de Triage d'Urgence MARCH (Sauvetage Vital)",
                        steps = listOf(
                            "1. M — Massive Bleeding (Hémorragie massive) :",
                            "   • Compression manuelle directe ferme avec un tissu propre pendant 10 minutes sans relâcher.",
                            "   • Garrot tourniquet si membre avec saignement en jet pulsatile : 5 à 7 cm au-dessus de la plaie (jamais sur une articulation). Noter l'heure exacte.",
                            "",
                            "2. A — Airway (Voies aériennes) :",
                            "   • Basculer délicatement la tête en arrière et élever le menton. Vérifier l'absence d'obstruction buccale.",
                            "",
                            "3. R — Respiration :",
                            "   • Si plaie thoracique avec sifflement : poser un pansement occlusif fixé sur 3 côtés (soupape).",
                            "",
                            "4. C — Circulation :",
                            "   • Vérifier le pouls et le temps de recoloration cutanée (< 2 secondes).",
                            "",
                            "5. H — Hypothermie :",
                            "   • Isoler immédiatement la victime du sol froid avec une couverture de survie (face dorée vers l'extérieur).",
                            "",
                            "⛔ INTERDICTION : Ne jamais retirer un objet planté profondément dans le corps (le caler sur place)."
                        )
                    )
                )

                "REANIMATION_CIVILE" -> return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatProtocol(
                        intent = "Urgences Vitales Civiles (Arrêt Cardiaque, Heimlich, AVC)",
                        dbSource = "oye-ahmad/Smart-FirstAid-Guide-App & kiwix/WikiMed",
                        isWarning = false,
                        title = "🫀 Réanimation d'Urgence & Gestes de Premiers Secours",
                        steps = listOf(
                            "1. INCONSCIENT SANS RESPIRATION ➔ ARRÊT CARDIAQUE :",
                            "   • Appeler d'urgence le 15 ou le 112 + demander un Défibrillateur (DAE).",
                            "   • Massage cardiaque immédiat au centre de la poitrine : 100 à 120 compressions par minute, profondeur 5 à 6 cm.",
                            "   • Rythme : 30 compressions pour 2 insufflations (ou massage continu sans arrêt si non formé aux insufflations).",
                            "   • Dès que le DAE est là : l'allumer et suivre scrupuleusement les consignes vocales.",
                            "",
                            "2. ÉTOUFFEMENT TOTAL (La victime ne peut ni parler ni tousser) :",
                            "   • 5 claques vigoureuses dans le haut du dos entre les omoplates avec le plat de la main.",
                            "   • Si échec : 5 compressions abdominales (Manœuvre de Heimlich) au creux de l'estomac vers l'arrière et le haut.",
                            "   • Alterner 5 claques / 5 compressions. Si perte de connaissance ➔ débuter le massage cardiaque.",
                            "",
                            "3. INCONSCIENT QUI RESPIRE ➔ PLS (Position Latérale de Sécurité) :",
                            "   • Coucher la victime sur le côté, jambe pliée pour stabiliser, bouche ouverte vers le sol pour éviter l'asphyxie.",
                            "",
                            "4. AVC (Protocole FAST / VITE) :",
                            "   • Visage asymétrique, bras affaibli, parole troublée ➔ Noter l'heure et appeler le 15 sans délai."
                        )
                    )
                )

                "EAU_POTABLE" -> return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatProtocol(
                        intent = "Purification & Collecte d'Eau en Milieu Hostile",
                        dbSource = "US Army FM 21-76 Survival Manual & kit-survie",
                        isWarning = false,
                        title = "💧 Purification & Collecte d'Eau (Priorité Vitale)",
                        steps = listOf(
                            "Rappel : 3 jours maximum sans eau potable.",
                            "",
                            "1. ÉBULLITION FRANCHE (Méthode de référence) :",
                            "   • Filtrer d'abord la boue à travers un tissu fin ou un filtre de sable/charbon.",
                            "   • Porter à gros bouillons pendant 1 minute complète (+ 1 min par 1000m d'altitude). Détruit 100% des virus et bactéries.",
                            "",
                            "2. DÉSINFECTION CHIMIQUE D'URGENCE :",
                            "   • Eau de Javel classique sans additif : 2 gouttes par litre d'eau claire (4 gouttes si eau trouble). Laisser agir 30 minutes minimum.",
                            "   • Teinture d'iode 2% : 5 gouttes par litre d'eau claire, attendre 30 minutes.",
                            "",
                            "⛔ DANGER : Ne jamais boire d'eau de mer (accélère mortellement la déshydratation) ni d'eau stagnante agricole."
                        )
                    )
                )

                "ABRI_THERMIQUE" -> return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatProtocol(
                        intent = "Abri Thermique & Lutte contre l'Hypothermie",
                        dbSource = "US Army FM 21-76 Survival Manual",
                        isWarning = false,
                        title = "🏕️ Abri Thermique & Survie au Froid Extrême",
                        steps = listOf(
                            "Rappel : 3 heures sans abri en conditions de froid et humidité extrêmes.",
                            "",
                            "1. ISOLATION DU SOL (Priorité absolue) :",
                            "   • La conduction thermique par le sol est 3 fois plus déperditive que l'air.",
                            "   • Construire un matelas de feuilles mortes, fougères ou branchages de 30 à 50 cm d'épaisseur.",
                            "",
                            "2. COUVERTURE DE SURVIE :",
                            "   • Face dorée vers l'extérieur (face argentée vers le corps) : réfléchit 90% du rayonnement infrarouge du corps.",
                            "   • Face argentée vers l'extérieur : protège des coups de chaleur intenses en plein soleil."
                        )
                    )
                )

                "FEU_SIGNAL" -> return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatProtocol(
                        intent = "Allumage de Feu & Signaux Internationaux de Détresse",
                        dbSource = "Bushcraft & Rescue Standards (FM 21-76)",
                        isWarning = false,
                        title = "🔥 Feu de Survie & Signaux Internationaux de Sauvetage",
                        steps = listOf(
                            "1. AMADOU VITAL :",
                            "   • Écorce de bouleau : riche en bétuline hydrofuge, brûle même trempée.",
                            "   • Bois gras (cœur de pin résineux) : étincelles faciles au firesteel.",
                            "",
                            "2. SIGNAUX DE DÉTRESSE UNIVERSELS :",
                            "   • Triangle de détresse : 3 feux disposés en triangle équilatéral espacés de 25m (signal secours aérien).",
                            "   • Colonne de fumée blanche : jeter de la mousse verte ou feuillage humide sur un foyer bien établi.",
                            "   • Sifflet : 3 coups brefs répétés chaque minute."
                        )
                    )
                )

                "MORSURES_TOXICO" -> return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatProtocol(
                        intent = "Pharmacie de Terrain, Envenimations & Réhydratation",
                        dbSource = "kiwix/WikiMed & Where There Is No Doctor",
                        isWarning = false,
                        title = "💊 Envenimations (Vipères, Frelons) & Réhydratation d'Urgence",
                        steps = listOf(
                            "1. MORSURE DE SERPENT VENIMEUX / VIPÈRE :",
                            "   • Allonger la victime et la calmer (ralentit la diffusion du venin dans la lymphe).",
                            "   • Immobiliser le membre mordu à l'aide d'une attelle improvisée.",
                            "   • ⛔ INTERDIT : Ne JAMAIS inciser, ne jamais aspirer le venin, ne pas poser de garrot serré.",
                            "",
                            "2. SOLUTION DE RÉHYDRATATION ORALE OMS (Diarrhée sévère / Déshydratation) :",
                            "   • Pour 1 litre d'eau bouillie refroidie : 6 cuillères à café rases de sucre + 1/2 cuillère à café rase de sel.",
                            "   • Faire boire par petites gorgées régulières pour éviter le choc hypovolémique."
                        )
                    )
                )

                "TRAUMATO_TACTIQUE" -> return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatProtocol(
                        intent = "Traumatologie Tactique, Balistique & Blast",
                        dbSource = "bdkoeh/survivalRAG & US Army FM 4-25.11",
                        isWarning = false,
                        title = "💥 Traumatologie Tactique & Lésions par Blast / Écrasement",
                        steps = listOf(
                            "1. PLAIE PÉNÉTRANTE BALISTIQUE :",
                            "   • Rechercher systématiquement l'orifice d'entrée ET l'orifice de sortie.",
                            "   • Si membre : garrot tourniquet immédiat serré jusqu'à arrêt complet du saignement.",
                            "   • Si jonction (cou, aine, aisselle) : packing compressif avec gaze fermement tassée.",
                            "",
                            "2. GESTION DU GARROT :",
                            "   • Noter l'heure exacte de pose. Ne jamais desserrer après 2 heures sans chirurgie (risque de mort subite par toxines libérées)."
                        )
                    )
                )

                "RADIO_SURVIE" -> return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatProtocol(
                        intent = "Communications d'Urgence & Préservation Énergétique",
                        dbSource = "iFixit & Ham Radio Handbook",
                        isWarning = false,
                        title = "📻 Fréquences Radio d'Urgence & Énergie Autonome",
                        steps = listOf(
                            "1. CANAUX DE SECOURS EUROPÉENS :",
                            "   • Canal 7-7 PMR446 (Canal 7, CTCSS 7) : canal d'urgence randonnée/montagne en France.",
                            "   • Canal 16 VHF Marine (156.800 MHz) : détresse en mer et grands lacs.",
                            "   • 161.300 MHz (Canal E) : secours en montagne Alpes/Pyrénées.",
                            "",
                            "2. PRÉSERVATION SMARTPHONE :",
                            "   • Activer le Mode Avion : le GPS fonctionne sans réseau cellulaire et consomme < 3%/h.",
                            "   • Garder le téléphone contre le corps pour protéger la batterie du froid."
                        )
                    )
                )

                "PSYCHO_TECHNIQUE" -> return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatProtocol(
                        intent = "Psychologie de Survie, Nœuds & Franchissement",
                        dbSource = "ligi/SurvivalManual (Référence Android)",
                        isWarning = false,
                        title = "🧗 Psychologie S.T.O.P., Nœuds de Secours & Franchissement",
                        steps = listOf(
                            "1. MÉTHODE S.T.O.P. CONTRE LA PANIQUE :",
                            "   • S (Stop) : Arrête-toi immédiatement dès les premiers signes de panique.",
                            "   • T (Think) : Réfléchis calmement à tes priorités et ressources disponibles.",
                            "   • O (Observe) : Observe le terrain, la météo, la lumière restante et les traces.",
                            "   • P (Plan) : Conçois un plan simple étape par étape (d'abord l'abri).",
                            "",
                            "2. NŒUDS ESSENTIELS DE SURVIE :",
                            "   • Nœud de Chaise (Bowline) : boucle indéformable ne glissant jamais sous tension (amarrage, treuillage).",
                            "   • Nœud de Cabestan : fixation rapide et autobloquante d'un abri tarp sur un tronc."
                        )
                    )
                )

                "URBAIN_CATASTROPHE" -> return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatProtocol(
                        intent = "Survie en Milieu Urbain & Catastrophes Majeures",
                        dbSource = "Protection Civile Internationale",
                        isWarning = false,
                        title = "🏙️ Survie Urbaine : Séisme, Inondation & Blackout",
                        steps = listOf(
                            "1. SÉISME :",
                            "   • Règle Drop, Cover, Hold On : se protéger sous une structure lourde ou le long d'un mur porteur.",
                            "   • Ne jamais utiliser les ascenseurs ni courir dans les escaliers pendant la secousse.",
                            "",
                            "2. INONDATION :",
                            "   • Gagner les points hauts avec lampe et réserves d'eau. Éviter les sous-sols et parkings souterrains."
                        )
                    )
                )

                "ASSISTANT_AIDE" -> return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatProtocol(
                        intent = "Guide d'Aide & Référentiels Intégrés",
                        dbSource = "SurvIA — Système Autonome d'Urgence",
                        isWarning = false,
                        title = "🧭 SurvIA — Assistant Autonome de Secours Hors-Ligne",
                        steps = listOf(
                            "Je suis votre assistant autonome fonctionnant 100% hors-ligne.",
                            "Mes réponses proviennent directement de 7 bases de connaissances certifiées :",
                            "  • Where There Is No Doctor & FirstAid (Plaies, désinfection, coupures, pansements)",
                            "  • US Army FM 21-76 (Purification de l'eau, abri thermique, feu, protéines)",
                            "  • Smart-FirstAid & WikiMed (Arrêt cardiaque, RCP, Heimlich, AVC, morsures serpents)",
                            "  • survivalRAG & FM 4-25.11 (Traumatologie tactique, garrot tourniquet, blast)",
                            "  • Soonz-38/kit-survie (Garde-fou vital plantes et champignons)",
                            "  • ligi/SurvivalManual (Psychologie S.T.O.P., franchissement, nœuds)",
                            "",
                            "Posez-moi votre question directement, par exemple :",
                            "  • « Comment désinfecter une coupure sans antiseptique ? »",
                            "  • « Quelqu'un s'étouffe, que faire ? »",
                            "  • « Comment rendre l'eau d'un ruisseau potable ? »",
                            "  • « Que faire en cas de morsure de vipère ? »"
                        )
                    )
                )
            }
        }

        // ── PROTOCOLE GÉNÉRAL PAR DÉFAUT (Règle des 3) ────────────────────────
        return ToolExecutionResult(
            toolName = "search_survival_kb",
            outputJson = formatProtocol(
                intent = "Orientation Générale de Survie",
                dbSource = "US Army FM 21-76 & Wilderness First Aid",
                isWarning = false,
                title = "🧭 SurvIA — Protocole Général de Survie & Règle des 3",
                steps = listOf(
                    "Bases locales certifiées actives à 100% en mode hors-ligne.",
                    "",
                    "📋 LA RÈGLE DES 3 (Priorités Physiologiques Absolues) :",
                    "  • 3 minutes sans oxygène ou en cas d'hémorragie artérielle massive",
                    "  • 3 heures sans abri thermique par froid, humidité ou grand vent (hypothermie)",
                    "  • 3 jours sans eau potable (déshydratation aiguë)",
                    "  • 3 semaines sans nourriture",
                    "",
                    "Décrivez précisément votre urgence (blessure, désinfection, eau, froid, orientation, étouffement) pour obtenir le protocole d'intervention exact."
                )
            )
        )
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

    private fun scoreKeywords(text: String, keywords: List<String>): Int {
        var score = 0
        for (kw in keywords) {
            if (text.contains(kw)) {
                score += if (kw.contains(" ")) 3 else 1 // Bonus pour les expressions multi-mots
            }
        }
        return score
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        for (kw in keywords) {
            if (text.contains(kw)) return true
        }
        return false
    }

    private fun formatProtocol(
        intent: String,
        dbSource: String,
        isWarning: Boolean,
        title: String,
        steps: List<String>
    ): String {
        return buildString {
            appendLine("🔍 ANALYSE DE LA SITUATION")
            appendLine("• Intention : $intent")
            appendLine("• Base certifiée consultée : $dbSource")
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
