package app.tacit.files

sealed class ScanState {
    object Idle : ScanState()
    class Scanning(val found: Int, val startedAt: Long) : ScanState()
    class Done(val count: Int, val at: Long, val truncated: Boolean) : ScanState()
}
