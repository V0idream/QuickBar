package io.github.quickbar.service

/** Formats copied text as a Markdown blockquote for the current editor. */
internal object MarkdownQuoteFormatter {
    fun format(text: String): String {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim('\n')
        if (normalized.isBlank()) return ""
        return normalized.lines().joinToString("\n") { line ->
            if (line.isEmpty()) ">" else "> $line"
        } + "\n\n"
    }
}
