package io.github.dot166.aconfig

class AConfig(@JvmField val packageName: String, @JvmField val flags: MutableList<Flag>) {
    class Flag(val name: String) {
        var value: Boolean? = null
    }
}


