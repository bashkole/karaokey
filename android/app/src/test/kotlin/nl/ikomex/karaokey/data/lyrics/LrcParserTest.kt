package nl.ikomex.karaokey.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {
    @Test
    fun parseSyncedLines() {
        val content = LrcParser.parse(
            """
            [00:12.00]First line
            [00:18.50]Second line
            """.trimIndent()
        )

        assertEquals(2, content.lines.size)
        assertEquals(12_000, content.lines[0].timeMs)
        assertEquals("First line", content.lines[0].text)
        assertEquals(true, content.synced)
    }

    @Test
    fun lineIndexAtProgress() {
        val lines = listOf(
            LyricLine(0, "A"),
            LyricLine(10_000, "B"),
            LyricLine(20_000, "C")
        )

        assertEquals(0, LrcParser.lineIndexAt(lines, 5000))
        assertEquals(1, LrcParser.lineIndexAt(lines, 15_000))
        assertEquals(2, LrcParser.lineIndexAt(lines, 25_000))
    }
}
