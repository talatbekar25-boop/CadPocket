package com.example.cadpocket

data class Pt(val x: Float, val y: Float)

sealed class Entity(open val layer: String) {
    data class Line(val a: Pt, val b: Pt, override val layer: String = "0") : Entity(layer)
    data class Circle(val c: Pt, val r: Float, override val layer: String = "0") : Entity(layer)
    data class Arc(
        val c: Pt,
        val r: Float,
        val startDeg: Float,
        val endDeg: Float,
        override val layer: String = "0"
    ) : Entity(layer)
    data class Polyline(val pts: List<Pt>, val closed: Boolean, override val layer: String = "0") : Entity(layer)
}

data class Drawing(val entities: List<Entity>) {
    val layers: List<String>
        get() = entities.map { it.layer.ifBlank { "0" } }.distinct().sorted()
}
