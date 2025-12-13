package io.github.dot166.aconfig

object ListUtils {
    @JvmStatic
    fun containsStr(list: MutableList<AConfig>, str: String): Boolean {
        var contains = false
        for (i in 0 until list.size) {
            if (list.get(i).packageName == str) {
                contains = true
            }
        }
        return contains
    }
}