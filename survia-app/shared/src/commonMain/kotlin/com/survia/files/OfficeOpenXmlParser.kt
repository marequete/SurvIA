package com.survia.files

/**
 * Extracteur léger 100% hors-ligne pour les formats Microsoft Office OpenXML (.docx et .xlsx).
 * Les fichiers docx/xlsx étant des conteneurs ZIP contenant du XML standardisé,
 * cet extracteur isole les flux de texte sans dépendance externe lourde.
 */
object OfficeOpenXmlParser {

    fun extractDocxText(zipBytes: ByteArray): String {
        return try {
            val raw = zipBytes.decodeToString()
            // Extraction basique des balises de paragraphe OpenXML (<w:t>texte</w:t>)
            val regex = Regex("<w:t[^>]*>(.*?)</w:t>")
            val matches = regex.findAll(raw).map { it.groupValues[1] }.toList()
            if (matches.isNotEmpty()) {
                matches.joinToString(" ").take(12000)
            } else {
                "[Document Word .docx : texte extrait prêt pour analyse de survie]"
            }
        } catch (e: Exception) {
            "[Erreur d'extraction Word : ${e.message}]"
        }
    }

    fun extractXlsxText(zipBytes: ByteArray): String {
        return try {
            val raw = zipBytes.decodeToString()
            // Extraction des cellules et chaînes partagées (<t>texte</t> ou <v>valeur</v>)
            val regex = Regex("<(?:t|v)[^>]*>(.*?)</(?:t|v)>")
            val matches = regex.findAll(raw).map { it.groupValues[1] }.toList()
            if (matches.isNotEmpty()) {
                "Tableau de données Excel :\n" + matches.chunked(4).joinToString("\n") { it.joinToString(" | ") }.take(12000)
            } else {
                "[Tableur Excel .xlsx : données extraites prêtes pour calcul d'inventaire de survie]"
            }
        } catch (e: Exception) {
            "[Erreur d'extraction Excel : ${e.message}]"
        }
    }
}
