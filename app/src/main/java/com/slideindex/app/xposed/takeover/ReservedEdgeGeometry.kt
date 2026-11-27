package com.slideindex.app.xposed.takeover

internal object ReservedEdgeGeometry {
  fun intervals(start: Float, end: Float, occupied: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    if (!start.isFinite() || !end.isFinite()) return emptyList()
    val top = start.coerceIn(0f, 1f)
    val bottom = end.coerceIn(0f, 1f)
    if (top >= bottom) return emptyList()
    var reserved = listOf(top to bottom)
    for ((blockedTop, blockedBottom) in occupied) {
      if (!(blockedTop < blockedBottom)) continue
      reserved = reserved.flatMap { (rangeTop, rangeBottom) ->
        if (blockedBottom <= rangeTop || blockedTop >= rangeBottom) {
          listOf(rangeTop to rangeBottom)
        } else {
          buildList {
            if (rangeTop < blockedTop) add(rangeTop to blockedTop)
            if (blockedBottom < rangeBottom) add(blockedBottom to rangeBottom)
          }
        }
      }
    }
    return reserved
  }
}
