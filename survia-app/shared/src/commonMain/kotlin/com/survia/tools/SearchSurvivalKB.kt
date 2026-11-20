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
        if (q.contains("saignement") || q.contains("hémorragie") || q.contains("garrot") ||
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

        // ── COUPURES, PLAIES, DÉSINFECTION & TÉTANOS (Where There Is No Doctor / FirstAid) ──
        if (q.contains("coup") || q.contains("plaie") || q.contains("blessure") ||
            q.contains("désinfect") || q.contains("rouille") || q.contains("ferraille") ||
            q.contains("antiseptique") || q.contains("infection") || q.contains("tétanos") || q.contains("nettoyer")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "🩹 Soin des Plaies, Coupures & Désinfection de Fortune (Where There Is No Doctor)",
                    listOf(
                        "1. NETTOYAGE MÉCANIQUE IMMÉDIAT (Le geste le plus important) :",
                        "   • L'irrigation abondante élimine 90% des débris et bactéries.",
                        "   • Rincer la plaie sous un filet continu d'eau potable ou d'eau bouillie refroidie pendant au moins 5 minutes.",
                        "   • Si disponible : laver le contour de la plaie avec de l'eau et du savon ordinaire (ne pas frotter vigoureusement l'intérieur de la plaie).",
                        "",
                        "2. DÉSINFECTION SANS PRODUITS PHARMACEUTIQUES :",
                        "   • Miel pur non pasteurisé : puissant antibactérien naturel (effet osmotique et peroxyde naturel). Appliquer une fine couche sur la plaie propre.",
                        "   • Eau salée bouillie refroidie (sérum physiologique improvisé) : 1 cuillère à café rase de sel par litre d'eau bouillie.",
                        "   • ⛔ INTERDIT FORMEL : Ne JAMAIS verser d'alcool à brûler, d'eau de Javel, d'essence ou d'urine (détruit les tissus sains et aggrave mortellement l'infection).",
                        "",
                        "3. FERMETURE ET PANSEMENT :",
                        "   • Laisser saigner quelques secondes au début pour évacuer les impuretés.",
                        "   • Rapprocher les berges avec un tissu propre ou des bandelettes adhésives improvisées.",
                        "   • Couvrir d'un pansement sec et propre. Changer dès qu'il est humide.",
                        "",
                        "⚠️ RISQUE TÉTANOS (Ferraille rouillée, épines, terre) :",
                        "   • La bactérie du tétanos adore les plaies profondes peu oxygénées.",
                        "   • Si le vaccin antitétanique date de plus de 10 ans (ou > 5 ans pour plaie souillée) : consulter impérativement un médecin ou poste de secours pour une injection de rappel + sérum."
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

        // ── ARRÊT CARDIAQUE, RCP, ÉTOUFFEMENT & AVC (Smart-FirstAid & FirstAid) ──
        if (q.contains("arrêt cardiaque") || q.contains("massage cardiaque") || q.contains("rcp") ||
            q.contains("défibrillateur") || q.contains("dae") || q.contains("étouffement") ||
            q.contains("heimlich") || q.contains("pls") || q.contains("avc") || q.contains("respiration") ||
            q.contains("inconscient qui respire") || q.contains("ne respire plus") || q.contains("cardiaque")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "🫀 Réanimation d'Urgence & Gestes de Premiers Secours Civils (Smart-FirstAid)",
                    listOf(
                        "1. VICTIME INCONSCIENTE QUI NE RESPIRE PLUS ➔ ARRÊT CARDIAQUE :",
                        "   • Alerte immédiate (15/112) + réclamer un DAE.",
                        "   • Masser immédiatement au centre de la poitrine (talon de la main, bras tendus) : 100 à 120 compressions/minute, profondeur 5 à 6 cm.",
                        "   • Alterner 30 compressions thoraciques et 2 insufflations (bouche-à-bouche si formé, sinon masser en continu sans s'arrêter).",
                        "   • Dès que le DAE arrive : l'allumer, coller les électrodes sur torse nu et suivre strictement les instructions vocales.",
                        "",
                        "2. ÉTOUFFEMENT TOTAL (La victime ne peut ni parler, ni tousser, mains au cou) :",
                        "   • Donner 5 claques vigoureuses entre les omoplates avec le plat de la main.",
                        "   • Si échec : 5 compressions abdominales (Manœuvre de Heimlich) : poing placé entre le nombril et le sternum, tirer d'un coup sec vers l'arrière et le haut.",
                        "   • Alterner 5 claques / 5 compressions jusqu'au déblocage. Si perte de connaissance ➔ démarrer la RCP.",
                        "",
                        "3. INCONSCIENT QUI RESPIRE ➔ POSITION LATÉRALE DE SÉCURITÉ (PLS) :",
                        "   • Allonger sur le côté, jambe du dessus fléchie à 90° pour caler le corps, bouche ouverte vers le sol pour éviter l'étouffement par vomissement, tête en arrière.",
                        "",
                        "4. SUSPICION D'AVC (Protocole FAST / VITE) :",
                        "   • F (Face) : Visage affaissé d'un côté ? A (Arm) : Incapacité à lever un bras ? S (Speech) : Troubles de l'élocution ? T (Time) : Noter l'heure exacte et appeler d'urgence le 15."
                    )
                )
            )
        }

        // ── TRAUMATOLOGIE LOURDE, BALISTIQUE & BLAST (survivalRAG / FM 4-25.11) ─
        if (q.contains("balistique") || q.contains("balle") || q.contains("tir") ||
            q.contains("explosion") || q.contains("blast") || q.contains("écrasement") ||
            q.contains("crush") || q.contains("garrot prolongé") || q.contains("amputation")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "💥 Traumatologie Tactique & Lésions de Blast (survivalRAG / US Army FM 4-25.11)",
                    listOf(
                        "1. TRAUMATISME BALISTIQUE PÉNÉTRANT :",
                        "   • Rechercher systématiquement l'orifice d'entrée ET l'orifice de sortie (souvent plus large et délabrant).",
                        "   • Plaie de membre : garrot tourniquet immédiat serré à bloc.",
                        "   • Plaie à la jonction (aine, aisselle, cou) : packing hémostatique (bourrage ferme de gaze hémostatique dans la plaie avec pression continue 3 minutes).",
                        "",
                        "2. GESTION DU GARROT PROLONGÉ EN ZONE ISOLÉE :",
                        "   • Noter impérativement l'heure de pose sur le garrot ou le front (T + HH:MM).",
                        "   • Tolérance sans séquelles majeures : jusqu'à 2 heures. Ne JAMAIS desserrer un garrot posé depuis plus de 2 heures sans présence chirurgicale (risque de choc toxique et arrêt cardiaque immédiat par libération de potassium).",
                        "",
                        "3. CRUSH SYNDROME (Syndrome d'écrasement sous décombres) :",
                        "   • Si un membre est écrasé depuis plus de 2 heures, ne pas lever la charge avant d'avoir posé un garrot en amont pour bloquer les toxines nécrotiques (myoglobine). Hydrater massivement dès que possible."
                    )
                )
            )
        }

        // ── PHARMACIE DE SURVIE, MORSURES & RÉHYDRATATION (Kiwix / WikiMed) ───
        if (q.contains("morsure") || q.contains("vipère") || q.contains("serpent") ||
            q.contains("piqûre") || q.contains("guêpe") || q.contains("frelon") ||
            q.contains("allergie") || q.contains("choc") || q.contains("médicament") ||
            q.contains("diarrhée") || q.contains("réhydratation") || q.contains("trousse") || q.contains("pharmacie")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "💊 Pharmacie d'Urgence & Toxicologie de Terrain (WikiMed & Where There Is No Doctor)",
                    listOf(
                        "1. MORSURE DE VIPÈRE / SERPENT VENIMEUX :",
                        "   • Calmer la victime et l'allonger (ralentit la propagation lymphatique du venin). Immobiliser le membre mordu avec une attelle improvisée.",
                        "   • ⛔ INTERDICTIONS VITALES : Ne JAMAIS inciser, ne jamais aspirer le venin à la bouche, ne pas poser de garrot serré (aggrave la nécrose locale), pas d'alcool.",
                        "   • Bandage élastique modérément compressif (type bande crêpe) du bout du membre vers la racine.",
                        "",
                        "2. CHOC ANAPHYLACTIQUE (Allergie grave après piqûre ou aliment) :",
                        "   • Signes d'alerte : gonflement des lèvres/gorge, œdème de Quincke, urticaire géant, sifflement respiratoire, chute de tension.",
                        "   • Injection immédiate d'adrénaline auto-injectable (Epipen / Anapen 0.3 mg adulte) sur la face antéro-latérale de la cuisse à travers le pantalon.",
                        "",
                        "3. SOLUTION DE RÉHYDRATATION ORALE OMS (Diarrhée sévère / Choléra) :",
                        "   • Pour 1 litre d'eau bouillie refroidie : 6 cuillères à café rases de sucre blanc + 1/2 cuillère à café rase de sel.",
                        "   • Faire boire par petites gorgées fréquentes pour stopper le collapsus hypovolémique."
                    )
                )
            )
        }

        // ── PSYCHOLOGIE, NŒUDS CRITIQUES & FRANCHISSEMENT (ligi/SurvivalManual) ─
        if (q.contains("stress") || q.contains("panique") || q.contains("peur") ||
            q.contains("nœud") || q.contains("corde") || q.contains("rivière") ||
            q.contains("franchir") || q.contains("avalanche") || q.contains("neige") || q.contains("psychologie")
        ) {
            return ToolExecutionResult(
                toolName = "search_survival_kb",
                outputJson = buildNaturalResponse(
                    "🧗 Survie en Milieu Hostile & Franchissement (ligi/SurvivalManual)",
                    listOf(
                        "1. PSYCHOLOGIE ET RÈGLE S.T.O.P. :",
                        "   • S (Stop) : Arrête-toi immédiatement dès que tu réalises que tu es perdu ou en panique.",
                        "   • T (Think) : Réfléchis calmement, analyse tes réserves d'eau et de calories.",
                        "   • O (Observe) : Observe le terrain, la météo, le vent, la lumière restante, les traces.",
                        "   • P (Plan) : Établis un plan d'action d'urgence (d'abord l'abri avant la nuit).",
                        "",
                        "2. LES 3 NŒUDS ESSENTIELS DE SURVIE :",
                        "   • Nœud de Chaise (Bowline) : boucle indéformable qui ne glisse jamais sous forte tension (hélitreuillage, amarrage de sécurité).",
                        "   • Nœud de Cabestan (Clove Hitch) : bloque une corde sur un piquet ou un tronc pour monter un abri tarp.",
                        "   • Nœud Plat (Reef Knot) : relie deux cordes de même diamètre.",
                        "",
                        "3. FRANCHISSEMENT D'UNE RIVIÈRE EN CRUE :",
                        "   • Ne jamais traverser pieds nus (perte d'adhérence et blessures sur rochers). Conserver ses chaussures sans les chaussettes.",
                        "   • Détacher la sangle ventrale du sac à dos pour pouvoir s'en libérer immédiatement si emporté.",
                        "   • Traverser en diagonale vers l'aval, appuyé sur un bâton tripode solide."
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

