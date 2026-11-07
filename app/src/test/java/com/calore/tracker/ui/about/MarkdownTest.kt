package com.calore.tracker.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MarkdownTest {

    @Test
    fun parsesTheBlocksThePolicyUses() {
        val text = """
            # Title

            Some **bold** text with a [link](https://example.com)
            on two lines.

            ## Section
            - one `code`
            - *two*
            > quoted

            | A | B |
            |---|---|
            | 1 | 2 |

            ```json
            { "a": 1 }
            ```
        """.trimIndent()
        assertEquals(
            listOf(
                MdBlock.Heading(1, "Title"),
                MdBlock.Paragraph("Some bold text with a link on two lines."),
                MdBlock.Heading(2, "Section"),
                MdBlock.Bullet("one code"),
                MdBlock.Bullet("two"),
                MdBlock.Quote("quoted"),
                MdBlock.TableRow(listOf("A", "B"), header = true),
                MdBlock.TableRow(listOf("1", "2"), header = false),
                MdBlock.Code("{ \"a\": 1 }"),
            ),
            Markdown.parse(text),
        )
    }

    @Test
    fun theAppShipsTheSamePolicyAsTheDocs() {
        val docs = File("../docs/PRIVACY_POLICY.md").readText()
        val asset = File("src/main/assets/legal/privacy-policy-en.md").readText()
        assertEquals("Copy docs/PRIVACY_POLICY.md to the asset after changing it", docs, asset)
        assertTrue(Markdown.parse(asset).any { it is MdBlock.Heading && it.text.startsWith("Appendix A") })
    }
}
