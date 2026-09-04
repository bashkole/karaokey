package nl.ikomex.karaokey.data.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackDeviceSelectorTest {
    private val fireStick = SpotifyDevice(
        id = "tv-1",
        name = "Fire TV Stick",
        isActive = true,
        type = "TV"
    )
    private val computer = SpotifyDevice(
        id = "pc-1",
        name = "Desktop",
        type = "Computer"
    )
    private val speaker = SpotifyDevice(
        id = "sp-1",
        name = "Living room",
        type = "Speaker"
    )

    @Test
    fun prefersComputerOverFireStick() {
        val selected = PlaybackDeviceSelector.select(listOf(fireStick, computer))
        assertEquals("pc-1", selected?.id)
    }

    @Test
    fun ignoresSavedFireStickWhenComputerExists() {
        val selected = PlaybackDeviceSelector.select(
            devices = listOf(fireStick, computer),
            savedId = "tv-1"
        )
        assertEquals("pc-1", selected?.id)
    }

    @Test
    fun keepsSavedComputer() {
        val other = computer.copy(id = "pc-2", name = "Laptop")
        val selected = PlaybackDeviceSelector.select(
            devices = listOf(other, computer, fireStick),
            savedId = "pc-1"
        )
        assertEquals("pc-1", selected?.id)
    }

    @Test
    fun prefersSpeakerWhenNoComputer() {
        val selected = PlaybackDeviceSelector.select(listOf(fireStick, speaker))
        assertEquals("sp-1", selected?.id)
    }

    @Test
    fun returnsNullWhenOnlyTvDevicesExist() {
        assertNull(PlaybackDeviceSelector.select(listOf(fireStick)))
    }
}
