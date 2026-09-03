package nl.ikomex.karaokey.server

import fi.iki.elonen.NanoHTTPD.IHTTPSession
import java.net.URLDecoder

internal object HttpRequestUtils {
    fun queryParam(session: IHTTPSession, key: String): String {
        session.parameters[key]?.firstOrNull()?.trim()?.let { value ->
            if (value.isNotEmpty()) return value
        }

        @Suppress("DEPRECATION")
        session.parms[key]?.trim()?.let { value ->
            if (value.isNotEmpty()) return value
        }

        val queryString = session.queryParameterString?.trim().orEmpty()
            .ifBlank { session.uri.substringAfter('?', "").trim() }

        if (queryString.isEmpty()) return ""

        return queryString.split("&").mapNotNull { part ->
            val pieces = part.split("=", limit = 2)
            if (pieces.size == 2 && pieces[0] == key) {
                URLDecoder.decode(pieces[1], Charsets.UTF_8.name())
            } else {
                null
            }
        }.firstOrNull().orEmpty().trim()
    }
}
