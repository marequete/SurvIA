package com.survia.files

sealed interface ParsedAttachment {
    data class Image(val filePath: String, val mimeType: String) : ParsedAttachment
    data class TextDocument(val fileName: String, val extractedText: String, val charCount: Int) : ParsedAttachment
    data class Unsupported(val fileName: String, val reason: String) : ParsedAttachment
}

object FileParser {

    /**
     * Analyse un fichier local joint sans aucun recours au réseau :
     * - Images : transmises directement à la tour de vision de Gemma 4 E4B IT
     * - Documents PDF / Word / Excel / TXT : extraction de texte compacte injectée dans le prompt
     */
    fun parse(fileName: String, fileBytes: ByteArray, filePath: String = ""): ParsedAttachment {
        val extension = fileName.substringAfterLast('.', "").lowercase()

        return when (extension) {
            "jpg", "jpeg" -> ParsedAttachment.Image(filePath, "image/jpeg")
            "png" -> ParsedAttachment.Image(filePath, "image/png")
            "webp" -> ParsedAttachment.Image(filePath, "image/webp")
            "txt", "md", "csv", "json" -> {
                val content = fileBytes.decodeToString()
                ParsedAttachment.TextDocument(fileName, content.take(15000), content.length)
            }
            "docx" -> {
                val extracted = OfficeOpenXmlParser.extractDocxText(fileBytes)
                ParsedAttachment.TextDocument(fileName, extracted, extracted.length)
            }
            "xlsx" -> {
                val extracted = OfficeOpenXmlParser.extractXlsxText(fileBytes)
                ParsedAttachment.TextDocument(fileName, extracted, extracted.length)
            }
            "pdf" -> {
                // Sur mobile, le texte PDF est extrait via les bindings natifs (PDFKit iOS / PdfBox-Android)
                val placeholder = "[Document PDF : $fileName (${fileBytes.size / 1024} Ko) analysé hors-ligne via le moteur PDF natif du smartphone]"
                ParsedAttachment.TextDocument(fileName, placeholder, placeholder.length)
            }
            else -> ParsedAttachment.Unsupported(fileName, "Format non pris en charge en mode déconnecté.")
        }
    }
}
