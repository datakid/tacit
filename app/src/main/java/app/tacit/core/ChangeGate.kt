package app.tacit.core

class ChangeGate {

    private var seen = Int.MIN_VALUE

    fun changed(current: Int): Boolean {
        if (current == seen) return false
        seen = current
        return true
    }
}
