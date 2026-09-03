package nl.ikomex.karaokey.data.lyrics

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class LyricsContent(
    val lines: List<LyricLine>,
    val plainText: String? = null,
    val synced: Boolean
)

object LrcParser {
    private val lineRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})]\s*(.*)""")

    fun parse(lrc: String): LyricsContent {
        val lines = lrc.lineSequence()
            .mapNotNull { raw ->
                val match = lineRegex.matchEntire(raw.trim()) ?: return@mapNotNull null
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3]
                val millis = when (fraction.length) {
                    2 -> fraction.toLong() * 10
                    else -> fraction.toLong()
                }
                val timeMs = (minutes * 60_000) + (seconds * 1_000) + millis
                LyricLine(timeMs, match.groupValues[4].trim())
            }
            .sortedBy { it.timeMs }
            .toList()

        return LyricsContent(lines = lines, synced = lines.isNotEmpty())
    }

    fun lineIndexAt(lines: List<LyricLine>, progressMs: Long): Int {
        if (lines.isEmpty()) return -1
        var index = 0
        for (i in lines.indices) {
            if (lines[i].timeMs <= progressMs) {
                index = i
            } else {
                break
            }
        }
        return index
    }
}
