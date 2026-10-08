package com.murai.gallery.util

/**
 * Natural order comparator: digit runs are compared by numeric value so
 * "IMG_2" sorts before "IMG_10". Case-insensitive; stable for ties.
 */
class NaturalOrderComparator : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        var ia = 0
        var ib = 0
        val la = a.length
        val lb = b.length
        while (ia < la && ib < lb) {
            val ca = a[ia]
            val cb = b[ib]
            val digitA = ca.isDigit()
            val digitB = cb.isDigit()
            if (digitA && digitB) {
                var ja = ia
                while (ja < la && a[ja].isDigit()) ja++
                var jb = ib
                while (jb < lb && b[jb].isDigit()) jb++
                val na = a.substring(ia, ja).trimStart('0').ifEmpty { "0" }
                val nb = b.substring(ib, jb).trimStart('0').ifEmpty { "0" }
                val cmp = if (na.length != nb.length) na.length - nb.length else na.compareTo(nb)
                if (cmp != 0) return cmp
                // shorter leading-zero run first on equal values
                val padCmp = (ja - ia) - (jb - ib)
                if (padCmp != 0) return padCmp
                ia = ja
                ib = jb
            } else {
                val lowerA = Character.toLowerCase(ca)
                val lowerB = Character.toLowerCase(cb)
                if (lowerA != lowerB) return lowerA - lowerB
                ia++
                ib++
            }
        }
        return (la - ia) - (lb - ib)
    }
}
