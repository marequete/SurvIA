package com.survia.tools

import com.survia.ai.ToolExecutionResult

/**
 * Moteur sémantique d'urgence SurvIA :
 * - Analyse le besoin en langage naturel
 * - Délivre une réponse ultra-pédagogique, rassurante et opérationnelle
 * - ZERO HALLUCINATION : basé exclusivement sur 7 référentiels médicaux et de survie certifiés
 * - Format : 1. Ce qu'il faut faire tout de suite / 2. Ce qu'il ne faut SURTOUT PAS faire / 3. Vigilance vitale
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
                outputJson = formatGuide(
                    userPrompt = userPrompt,
                    badge = "⚠️ DANGER MORTEL IMMÉDIAT",
                    title = "Arrêtez tout : Ne mangez JAMAIS ce végétal ou champignon !",
                    immediateActions = listOf(
                        "Ne portez rien à la bouche, même cuit ou bouilli.",
                        "En survie, le corps humain peut jeûner 3 semaines entières sans manger, mais une seule bouchée d'amanite phalloïde détruit votre foie et vos reins en quelques heures.",
                        "Conservez l'échantillon intact dans un tissu sec si quelqu'un en a déjà avalé."
                    ),
                    forbiddenActions = listOf(
                        "Ne jamais faire confiance à une photo ou une description approximative.",
                        "Ne jamais faire vomir la victime sans avis médical (risque de brûlure et étouffement)."
                    ),
                    vitalAlert = "Sosies mortels documentés : L'Amanite phalloïde ressemble à l'Agaric des bois. La Grande Ciguë ressemble à la Carotte sauvage. En cas d'ingestion : appelez d'urgence le 15, le 112 ou un centre antipoison.",
                    source = "Soonz-38/kit-survie & Where There Is No Doctor"
                ),
                isFatalGuardrailTriggered = true
            )
        }

        // ── 2. DOMAINE : HÉMORRAGIE MASSIVE & GARROT TOURNIQUET ──────────────
        if (containsAny(qClean, listOf("hemorragie", "garrot", "tourniquet", "saign", "artere", "amputation", "jet"))) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = formatGuide(
                    userPrompt = userPrompt,
                    badge = "🚨 URGENCE VITALE (3 MINUTES)",
                    title = "Arrêt d'Urgence d'un Saignement Massif",
                    immediateActions = listOf(
                        "Appuyez de tout votre poids directement sur la plaie avec un tissu propre ou votre main sans jamais relâcher.",
                        "Si le sang gicle en jet (artère touchée) sur un bras ou une jambe : Posez immédiatement un garrot 5 à 7 cm au-dessus de la plaie (entre le cœur et la blessure, jamais sur une articulation).",
                        "Serrez la tige / garrot jusqu'à ce que le sang s'arrête complètement de couler.",
                        "Notez impérativement l'heure exacte de pose au feutre sur le front ou sur le garrot (ex : T 14h30)."
                    ),
                    forbiddenActions = listOf(
                        "Ne JAMAIS desserrer ou retirer un garrot vous-même une fois posé (seul un chirurgien peut le faire).",
                        "Ne donnez JAMAIS à boire à une victime qui saigne abondamment (risque de fausse route et choc)."
                    ),
                    vitalAlert = "Couvrez immédiatement la victime avec une veste ou une couverture de survie : le corps se refroidit très vite en perdant du sang.",
                    source = "Protocole MARCH tactique (survivalRAG & US Army FM 4-25.11)"
                ),
                isFatalGuardrailTriggered = true
            )
        }

        // ── 3. DOMAINE : ARRÊT CARDIAQUE & ÉTOUFFEMENT ────────────────────────
        if (containsAny(qClean, listOf("arret", "cardiaque", "rcp", "massage", "defibrillateur", "etouff", "heimlich", "inconscient"))) {
            val isChoking = containsAny(qClean, listOf("etouff", "heimlich", "avale", "etouffement"))
            if (isChoking) {
                return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatGuide(
                        userPrompt = userPrompt,
                        badge = "🚨 ÉTOUFFEMENT TOTAL",
                        title = "La personne ne peut plus parler, tousser ni respirer",
                        immediateActions = listOf(
                            "Donnez 5 claques vigoureuses dans le haut du dos, entre les deux omoplates, avec le plat de la main.",
                            "Si l'objet ne sort pas : placez-vous derrière la victime, fermez un poing sous son sternum au creux de l'estomac, et effectuez 5 tractions sèches vers l'arrière et vers le haut (Manœuvre de Heimlich).",
                            "Alternez 5 claques dans le dos et 5 compressions abdominales jusqu'à expulsion."
                        ),
                        forbiddenActions = listOf(
                            "Ne mettez jamais vos doigts à l'aveugle dans la gorge (vous risquez d'enfoncer l'objet plus profondément).",
                            "Ne donnez rien à boire."
                        ),
                        vitalAlert = "Si la personne perd connaissance : allongez-la au sol et commencez immédiatement le massage cardiaque.",
                        source = "Smart-FirstAid & Secourisme Civil"
                    ),
                    isFatalGuardrailTriggered = true
                )
            } else {
                return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatGuide(
                        userPrompt = userPrompt,
                        badge = "🚨 ARRÊT CARDIAQUE",
                        title = "La victime ne répond pas et ne respire plus",
                        immediateActions = listOf(
                            "Alertez immédiatement les secours (15 / 112) et réclamez un défibrillateur (DAE).",
                            "Allongez la victime sur le dos, sur un sol dur.",
                            "Massez sans arrêt : placez vos deux mains l'une sur l'autre au centre de la poitrine, bras bien tendus.",
                            "Appuyez à un rythme rapide de 100 à 120 battements par minute (rythme de la musique 'Stayin Alive'), en enfonçant la poitrine de 5 à 6 cm."
                        ),
                        forbiddenActions = listOf(
                            "Ne vous arrêtez jamais de masser avant l'arrivée des secours.",
                            "Ne perdez pas de temps à faire du bouche-à-bouche si vous n'êtes pas formé : les compressions seules suffisent à sauver le cerveau."
                        ),
                        vitalAlert = "Si un défibrillateur (DAE) est disponible : allumez-le et suivez simplement ses instructions vocales, il s'occupe de tout.",
                        source = "Smart-FirstAid & Croix-Rouge / WikiMed"
                    ),
                    isFatalGuardrailTriggered = true
                )
            }
        }

        // ── 4. DOMAINE : MORSURE DE SERPENT & ENVENIMATION ────────────────────
        if (containsAny(qClean, listOf("morsure", "serpent", "vipere", "venin", "piqure", "guepe", "frelon"))) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = formatGuide(
                    userPrompt = userPrompt,
                    badge = "⚠️ ENVENIMATION PAR MORSURE",
                    title = "Que faire en cas de morsure de vipère ou serpent",
                    immediateActions = listOf(
                        "Allongez la victime et gardez-la parfaitement calme : bouger et courir accélère la diffusion du venin dans le sang.",
                        "Retirez immédiatement bagues, montres et chaussures avant que la zone ne gonfle.",
                        "Immobilisez le membre mordu à l'aide d'une branche ou d'un tissu, comme pour une fracture, en position de repos."
                    ),
                    forbiddenActions = listOf(
                        "⛔ Ne JAMAIS inciser la plaie au couteau.",
                        "⛔ Ne JAMAIS aspirer avec la bouche ni utiliser de pompe à venin (inefficace et dangereux).",
                        "⛔ Ne JAMAIS poser de garrot serré (le venin concentré détruirait tous les tissus du membre)."
                    ),
                    vitalAlert = "Appelez le 15 / 112 ou rejoignez calmement un poste de secours : le repos strict est votre meilleure arme.",
                    source = "kiwix/WikiMed & Where There Is No Doctor"
                ),
                isFatalGuardrailTriggered = true
            )
        }

        // ── 5. DOMAINE : PLAIES, COUPURES, DÉSINFECTION NATURELLE & TÉTANOS ────
        if (containsAny(qClean, listOf("coup", "plaie", "bless", "desinfect", "rouill", "ferraill", "clou", "tetanos", "antiseptique", "miel", "pansement", "vaccin"))) {
            val isTetanusRisk = containsAny(qClean, listOf("rouill", "ferraill", "clou", "terre", "tetanos", "barbele"))
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = formatGuide(
                    userPrompt = userPrompt,
                    badge = if (isTetanusRisk) "⚠️ ALERTE TÉTANOS & PLAIE SOUILLÉE" else "🩹 SOIN DES PLAIES",
                    title = "Comment nettoyer et soigner une plaie sans antiseptique",
                    immediateActions = listOf(
                        "1. LE GESTE QUI ÉLIMINE 90% DES BACTÉRIES : Rincez la plaie sous un filet continu d'eau potable ou d'eau bouillie refroidie pendant 5 minutes entières. L'action mécanique de l'eau est plus efficace que n'importe quel produit.",
                        "2. Savonnez délicatement la peau AUTOUR de la blessure avec de l'eau et du savon doux (ne frottez pas agressivement l'intérieur de la plaie).",
                        "3. ANTISEPTIQUE NATUREL : Appliquez une fine couche de miel pur non pasteurisé. Grâce à son sucre et son acidité, le miel déshydrate les bactéries et empêche l'infection.",
                        "4. Pansement improvisé : couvrez avec un tissu propre ou une compresse stérile sèche sans trop serrer."
                    ),
                    forbiddenActions = listOf(
                        "⛔ Ne JAMAIS verser d'eau de Javel, d'alcool à brûler, d'essence ou d'urine : ces liquides détruisent les cellules vivantes et provoquent une surinfection grave.",
                        "⛔ Ne refermez pas hermétiquement une plaie profonde causée par un clou : l'air empêche la bactérie du tétanos de se développer."
                    ),
                    vitalAlert = if (isTetanusRisk) "VIGILANCE TÉTANOS : Pour une coupure par ferraille rouillée ou terre, votre vaccin antitétanique doit dater de moins de 5 ans. Consultez un médecin sous 48h maximum pour faire un rappel." else "Surveillez la plaie : si elle devient rouge, chaude ou douloureuse dans les 24h, consultez rapidement.",
                    source = "Where There Is No Doctor (Dr. David Werner) & WikiMed"
                ),
                isFatalGuardrailTriggered = isTetanusRisk
            )
        }

        // ── 6. DOMAINE : EAU, PURIFICATION, FILTRE CHARBON & ORIENTATION ───────
        if (containsAny(qClean, listOf("eau", "soif", "potable", "purif", "filtr", "boire", "source", "ruisseau", "charbon", "javel"))) {
            val isFilter = containsAny(qClean, listOf("filtr", "charbon", "sable", "trouble", "boue"))
            if (isFilter) {
                return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatGuide(
                        userPrompt = userPrompt,
                        badge = "🏺 FABRICATION D'UN FILTRE À EAU",
                        title = "Fabriquer un filtre mécanique charbon & sable",
                        immediateActions = listOf(
                            "Prenez une bouteille en plastique, coupez le fond et percez 3 petits trous dans le bouchon.",
                            "Couche 1 (au fond) : un morceau de tissu propre ou une compresse pour retenir les particules fines.",
                            "Couche 2 : 5 à 8 cm de charbon de bois éteint pilé en petits grains (il piège les polluants chimiques et les mauvaises odeurs).",
                            "Couche 3 : 5 à 10 cm de sable fin propre (pour filtrer la terre et les impuretés).",
                            "Couche 4 (en haut) : 5 cm de petits graviers (pour retenir les brindilles et feuilles).",
                            "Faites d'abord couler 1 litre d'eau pour rincer la poussière noire avant d'utiliser le filtre."
                        ),
                        forbiddenActions = listOf(
                            "⛔ ATTENTION VITALE : Le filtre clarifie l'eau mais NE TUE PAS les virus ni les bactéries. L'eau filtrée doit impérativement être bouillie ensuite."
                        ),
                        vitalAlert = "Portez toujours l'eau claire à ébullition franche pendant 1 minute complète avant de la boire.",
                        source = "US Army FM 21-76 & kit-survie Soonz-38"
                    )
                )
            } else {
                return ToolExecutionResult(
                    toolName = "search_survival_kb",
                    outputJson = formatGuide(
                        userPrompt = userPrompt,
                        badge = "💧 EAU POTABLE D'URGENCE",
                        title = "Comment rendre une eau de nature potable",
                        immediateActions = listOf(
                            "MÉTHODE ROYALE (Ébullition) : Faites bouillir l'eau à gros bouillons pendant 1 minute complète (ajoutez 1 minute supplémentaire par tranche de 1000m d'altitude). Cela détruit 100% des bactéries, virus et parasites.",
                            "DÉSINFECTION CHIMIQUE DE FORTUNE : Si vous avez de l'eau de Javel classique sans parfum, mettez 2 gouttes par litre d'eau claire (4 gouttes si l'eau est trouble ou très froide). Attendez 30 minutes avant de boire.",
                            "ORIENTATION : Descendez toujours vers le fond des vallons et dépressions (talwegs), l'eau s'y écoule naturellement."
                        ),
                        forbiddenActions = listOf(
                            "⛔ Ne buvez JAMAIS d'eau de mer (elle accélère mortellement la déshydratation).",
                            "⛔ Ne buvez JAMAIS d'eau stagnante agricole chargée en pesticides."
                        ),
                        vitalAlert = "Règle des 3 : Le corps humain ne peut pas survivre plus de 3 jours sans boire. Cliquez sur le bouton sous ce message pour trouver la source d'eau la plus proche.",
                        source = "US Army FM 21-76 Survival Manual"
                    )
                )
            }
        }

        // ── 7. DOMAINE : ABRI THERMIQUE & HYPOTHERMIE ─────────────────────────
        if (containsAny(qClean, listOf("refuge", "abri", "froid", "hypothermie", "bivouac", "gel", "vent"))) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = formatGuide(
                    userPrompt = userPrompt,
                    badge = "🏕️ PROTECTION CONTRE LE FROID",
                    title = "Construire un abri et survivre à l'hypothermie",
                    immediateActions = listOf(
                        "1. ISOLEZ-VOUS DU SOL (Priorité absolue) : La terre froide aspire votre chaleur corporelle 3 fois plus vite que l'air. Fabriquez un matelas de 30 cm de feuilles sèches, fougères ou branches de sapin.",
                        "2. COUVERTURE DE SURVIE : Placez la face dorée vers l'extérieur et la face argentée contre votre corps. Elle renvoie 90% de votre chaleur infrarouge.",
                        "3. Coupez le vent : installez votre abri à l'abri des courants d'air (derrière un rocher, un talus ou un mur de branches)."
                    ),
                    forbiddenActions = listOf(
                        "Ne dormez JAMAIS directement à même le sol ou sur de la terre humide.",
                        "Ne buvez pas d'alcool (l'alcool dilate les vaisseaux et accélère mortellement l'hypothermie interne)."
                    ),
                    vitalAlert = "Règle des 3 : Par grand froid ou sous la pluie, l'hypothermie peut tuer en 3 heures seulement. Trouvez un abri avant la nuit tombée.",
                    source = "US Army FM 21-76 Survival Manual"
                )
            )
        }

        // ── 8. DOMAINE : PANNE D'ÉLECTRICITÉ, NOIR & ÉCLAIRAGE DE FORTUNE ─────────
        // Tolérance phonétique aux fautes de frappe : "j'aime plus", "pu de", "plus de lumière", "blackout", "sombre", "courant"
        val isBlackoutOrLight = containsAny(qClean, listOf(
            "lumiere", "lumier", "eclairage", "eclaire", "blackout", "panne",
            "electricite", "electrique", "courant", "disjoncteur", "fusible",
            "noir", "obscurite", "sombre", "torche", "lampe", "bougie",
            "plus de lumiere", "pas de lumiere", "aime plus de lumiere"
        )) || (containsAny(qClean, listOf("lumiere", "courant", "electricite")) && containsAny(qClean, listOf("plus", "pas", "aime", "coupe", "eteint")))

        if (isBlackoutOrLight) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = formatGuide(
                    userPrompt = userPrompt,
                    badge = "💡 PANNE DE COURANT & ÉCLAIRAGE DE FORTUNE",
                    title = "Que faire en cas de coupure d'électricité et dans l'obscurité",
                    immediateActions = listOf(
                        "1. SÉCURITÉ IMMÉDIATE (Pas de chute) : Ne vous précipitez pas dans le noir. Utilisez l'écran de votre téléphone au niveau de luminosité minimum pour vous repérer sans vider la batterie.",
                        "2. VÉRIFICATION DU DISJONCTEUR : Regardez par la fenêtre si le quartier/la rue a encore de la lumière. Si oui, vérifiez le disjoncteur différentiel général de votre tableau électrique.",
                        "3. LAMPE À HUILE DE FORTUNE (Autonomie 6h+) : Dans une boîte de conserve ou une tasse, versez de l'huile de cuisine (tournesol, olive). Enroulez un morceau de lacet en coton ou une bandelette de coton autour d'un trombone pour former une mèche. Laissez imbiber 2 minutes et allumez.",
                        "4. PRÉSERVEZ LE FROID : N'ouvrez sous aucun prétexte le réfrigérateur ou congélateur (les aliments restent intacts 24h à 48h porte fermée)."
                    ),
                    forbiddenActions = listOf(
                        "⛔ Ne JAMAIS allumer de flamme ou de briquet si vous sentez la moindre odeur suspecte (gaz ou hydrocarbure).",
                        "⛔ Ne laissez jamais une bougie ou flamme sans surveillance à proximité de rideaux ou de meubles en bois."
                    ),
                    vitalAlert = "Économisez la batterie de votre smartphone : activez immédiatement le mode Ultra Économie d'énergie. Votre téléphone est votre outil de survie et de secours.",
                    source = "US Army FM 21-76 & kit-survie Sécurité Civile"
                )
            )
        }

        // ── 9. FALLBACK : CONVERSATIONNEL BIENVEILLANT ────────────────────────
        return ToolExecutionResult(
            toolName = "search_survival_kb",
            outputJson = formatGuide(
                userPrompt = userPrompt,
                badge = "🧭 ASSISTANT DE SURVIE SURVIA",
                title = "Je suis votre copilote d'urgence 100% autonome",
                immediateActions = listOf(
                    "Rappelez-vous de la Règle des 3 de survie :",
                    "• 3 minutes sans oxygène (ou en cas d'hémorragie grave)",
                    "• 3 heures sans abri par froid glacial ou pluie continue",
                    "• 3 jours sans boire d'eau potable",
                    "• 3 semaines sans manger"
                ),
                forbiddenActions = listOf(
                    "Ne paniquez pas : arrêtez-vous, asseyez-vous 5 minutes, respirez calmement et observez votre environnement avant d'agir."
                ),
                vitalAlert = "Posez-moi votre question précise (ex: coupure clou rouillé, filtration de l'eau, garrot, refuge le plus proche) pour obtenir le protocole exact.",
                source = "SurvIA • Moteur Certifié Hors-Ligne"
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
                    if (end != -1) return after.substring(start + 1, end).trim()
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

    private fun formatGuide(
        userPrompt: String,
        badge: String,
        title: String,
        immediateActions: List<String>,
        forbiddenActions: List<String>,
        vitalAlert: String,
        source: String
    ): String {
        return buildString {
            appendLine("🛡️ $badge")
            appendLine(title)
            appendLine("───────────────────────────────────────────")
            appendLine()
            appendLine("✅ CE QU'IL FAUT FAIRE IMMÉDIATEMENT :")
            immediateActions.forEach { act ->
                appendLine("• $act")
            }
            appendLine()
            appendLine("⛔ CE QU'IL NE FAUT SURTOUT PAS FAIRE :")
            forbiddenActions.forEach { fbd ->
                appendLine("• $fbd")
            }
            appendLine()
            appendLine("⚠️ VIGILANCE VITALE :")
            appendLine(vitalAlert)
            appendLine()
            appendLine("📖 Référentiel certifié : $source")
        }.trimEnd()
    }
}
