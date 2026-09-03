package nl.ikomex.karaokey.data.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PartySettings {
    private val _queueLocked = MutableStateFlow(false)
    val queueLocked: StateFlow<Boolean> = _queueLocked.asStateFlow()

    fun setQueueLocked(locked: Boolean) {
        _queueLocked.value = locked
    }

    fun toggleQueueLock() {
        _queueLocked.value = !_queueLocked.value
    }
}
