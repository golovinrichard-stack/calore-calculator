package com.calore.tracker.ui.about

/** A block of a simple Markdown text (the privacy policy, M10.1). */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class TableRow(val cells: List<String>, val header: Boolean) : MdBlock
    data class Code(val text: String) : MdBlock
}

/** Just enough Markdown for the policy: headings, paragraphs, bullets, quotes, tables and code blocks. */
object Markdown {

    fun parse(text: String): List<MdBlock> {
        val blocks = ArrayList<MdBlock>()
        val paragraph = StringBuilder()
        val code = StringBuilder()
        var inCode = false
        var tableRows = 0
        fun flush() {
            if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(inline(paragraph.toString().trim()))
            paragraph.clear()
        }
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            if (line.trimStart().startsWith("```")) {
                if (inCode) {
                    blocks += MdBlock.Code(code.toString().trimEnd())
                    code.clear()
                } else {
                    flush()
                }
                inCode = !inCode
                continue
            }
            if (inCode) {
                code.append(raw).append('\n')
                continue
            }
            val t = line.trim()
            if (!t.startsWith("|")) tableRows = 0
            when {
                t.isEmpty() -> flush()
                t.startsWith("#") -> {
                    flush()
                    val level = t.takeWhile { it == '#' }.length
                    blocks += MdBlock.Heading(level, inline(t.drop(level).trim()))
                }
                t.startsWith("- ") || t.startsWith("* ") -> {
                    flush()
                    blocks += MdBlock.Bullet(inline(t.drop(2)))
                }
                t.startsWith(">") -> {
                    flush()
                    blocks += MdBlock.Quote(inline(t.drop(1).trim()))
                }
                t.startsWith("|") -> {
                    flush()
                    val cells = t.trim('|').split('|').map { it.trim() }
                    if (cells.all { it.matches(Regex(":?-{3,}:?")) }) continue
                    blocks += MdBlock.TableRow(cells.map(::inline), header = tableRows == 0)
                    tableRows++
                }
                else -> paragraph.append(t).append(' ')
            }
        }
        flush()
        return blocks
    }

    /** Removes inline marks: bold, italics, code and links (their text stays). */
    fun inline(text: String): String = text
        .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
        .replace("**", "")
        .replace("`", "")
        .replace(Regex("""(?<![\w*])\*([^*\s][^*]*)\*(?![\w*])"""), "$1")
}
