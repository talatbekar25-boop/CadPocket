package com.example.cadpocket

data class Pt(val x: Float, val y: Float)

sealed class Entity {
    data class Line(val a: Pt, val b: Pt): Entity()
    data class Circle(val c: Pt, val r: Float): Entity()
    data class Arc(val c: Pt, val r: Float, val startDeg: Float, val endDeg: Float): Entity()
    data class Polyline(val pts: List<Pt>, val closed: Boolean): Entity()
}

data class Drawing(val entities: List<Entity>)
