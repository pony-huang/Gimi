package github.ponyhuang.asssistantai.domain.speech.usecase

/** Converts the reply's Markdown source to text suitable for speech synthesis. */
fun markdownToSpeechText(markdown: String): String {
    var text = markdown
        .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("(?m)^\\s*(```|~~~)[^\\n]*$"), "")
        .replace(Regex("(?m)^\\s{0,3}(#{1,6}|>|[-+*]|\\d+[.)])\\s+"), "")
        .replace(Regex("(`{1,2}|\\*{1,3}|_{1,3}|~~)"), "")
    text = text
        .lines()
        .joinToString("\n") { it.trim() }
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
    return text.trim()
}
