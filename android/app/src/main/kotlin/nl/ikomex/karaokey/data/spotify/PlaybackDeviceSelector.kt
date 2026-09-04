package nl.ikomex.karaokey.data.spotify

object PlaybackDeviceSelector {
    fun select(devices: List<SpotifyDevice>, savedId: String? = null): SpotifyDevice? {
        val usable = devices.filter { !it.isRestricted && !it.id.isNullOrBlank() }
        if (usable.isEmpty()) return null

        usable.firstOrNull { it.id == savedId && !isTvDevice(it) }?.let { return it }

        return usable.firstOrNull { isComputer(it) }
            ?: usable.firstOrNull { isSpeaker(it) }
            ?: usable.firstOrNull { it.isActive && !isTvDevice(it) }
            ?: usable.firstOrNull { !isTvDevice(it) }
    }

    fun isTvDevice(device: SpotifyDevice): Boolean {
        val type = device.type.orEmpty()
        val name = device.name
        return type.equals("TV", ignoreCase = true) ||
            type.equals("Cast", ignoreCase = true) ||
            name.contains("Fire", ignoreCase = true) ||
            name.contains("Amazon", ignoreCase = true)
    }

    private fun isComputer(device: SpotifyDevice): Boolean =
        device.type.equals("Computer", ignoreCase = true)

    private fun isSpeaker(device: SpotifyDevice): Boolean =
        device.type.equals("Speaker", ignoreCase = true)
}
