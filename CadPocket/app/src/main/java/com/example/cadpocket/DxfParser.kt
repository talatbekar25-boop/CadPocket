package com.example.cadpocket

object DxfParser {
    data class PairCode(val code: Int, val value: String)

    fun parse(text: String): Drawing {
        val lines = text.replace("\r", "").split("\n")
        val pairs = mutableListOf<PairCode>()
        var i = 0
        while (i + 1 < lines.size) {
            val code = lines[i].trim().toIntOrNull()
            if (code != null) pairs += PairCode(code, lines[i + 1].trim())
            i += 2
        }

        val out = mutableListOf<Entity>()
        var p = 0
        while (p < pairs.size) {
            if (pairs[p].code == 0) {
                val type = pairs[p].value.uppercase()
                val start = p + 1
                var end = start
                while (end < pairs.size && pairs[end].code != 0) end++
                val block = pairs.subList(start, end)
                when (type) {
                    "LINE" -> parseLine(block)?.let(out::add)
                    "CIRCLE" -> parseCircle(block)?.let(out::add)
                    "ARC" -> parseArc(block)?.let(out::add)
                    "LWPOLYLINE" -> parseLwPolyline(block)?.let(out::add)
                }
                p = end
            } else p++
        }
        if (out.isEmpty()) {
            throw IllegalArgumentException("Desteklenen DXF nesnesi bulunamadı. ASCII DXF deneyin.")
        }
        return Drawing(out)
    }

    private fun first(block: List<PairCode>, code: Int): Float? =
        block.firstOrNull { it.code == code }?.value?.toFloatOrNull()

    private fun layer(block: List<PairCode>): String =
        block.firstOrNull { it.code == 8 }?.value?.ifBlank { "0" } ?: "0"

    private fun parseLine(b: List<PairCode>): Entity.Line? {
        val x1 = first(b, 10) ?: return null
        val y1 = first(b, 20) ?: return null
        val x2 = first(b, 11) ?: return null
        val y2 = first(b, 21) ?: return null
        return Entity.Line(Pt(x1, y1), Pt(x2, y2), layer(b))
    }

    private fun parseCircle(b: List<PairCode>): Entity.Circle? {
        val x = first(b, 10) ?: return null
        val y = first(b, 20) ?: return null
        val r = first(b, 40) ?: return null
        return Entity.Circle(Pt(x, y), r, layer(b))
    }

    private fun parseArc(b: List<PairCode>): Entity.Arc? {
        val x = first(b, 10) ?: return null
        val y = first(b, 20) ?: return null
        val r = first(b, 40) ?: return null
        val a1 = first(b, 50) ?: return null
        val a2 = first(b, 51) ?: return null
        return Entity.Arc(Pt(x, y), r, a1, a2, layer(b))
    }

    private fun parseLwPolyline(b: List<PairCode>): Entity.Polyline? {
        val pts = mutableListOf<Pt>()
        var x: Float? = null
        for (pc in b) {
            when (pc.code) {
                10 -> x = pc.value.toFloatOrNull()
                20 -> {
                    val y = pc.value.toFloatOrNull()
                    if (x != null && y != null) pts += Pt(x!!, y)
                    x = null
                }
            }
        }
        if (pts.size < 2) return null
        val flags = b.firstOrNull { it.code == 70 }?.value?.toIntOrNull() ?: 0
        return Entity.Polyline(pts, flags and 1 == 1, layer(b))
    }
}
