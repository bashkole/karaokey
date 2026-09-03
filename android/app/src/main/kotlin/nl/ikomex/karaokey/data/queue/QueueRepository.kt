package nl.ikomex.karaokey.data.queue

import kotlinx.coroutines.flow.Flow

class QueueRepository(
    private val queueDao: QueueDao
) {
    fun observeActiveQueue(): Flow<List<QueueItemEntity>> = queueDao.observeActiveQueue()

    suspend fun addTrack(
        spotifyUri: String,
        trackName: String,
        artistName: String,
        albumArtUrl: String?,
        durationMs: Long,
        addedBy: String
    ): QueueItemEntity {
        val position = queueDao.maxPosition() + 1
        val id = queueDao.insert(
            QueueItemEntity(
                spotifyUri = spotifyUri,
                trackName = trackName,
                artistName = artistName,
                albumArtUrl = albumArtUrl,
                durationMs = durationMs,
                addedBy = addedBy.ifBlank { "Guest" },
                position = position
            )
        )
        return QueueItemEntity(
            id = id,
            spotifyUri = spotifyUri,
            trackName = trackName,
            artistName = artistName,
            albumArtUrl = albumArtUrl,
            durationMs = durationMs,
            addedBy = addedBy.ifBlank { "Guest" },
            position = position
        )
    }

    suspend fun getNextPending(): QueueItemEntity? = queueDao.getNextPending()

    suspend fun getCurrentlyPlaying(): QueueItemEntity? = queueDao.getCurrentlyPlaying()

    suspend fun markPlaying(item: QueueItemEntity) {
        queueDao.clearPlaying()
        queueDao.updateStatus(item.id, QueueItemStatus.PLAYING.name)
    }

    suspend fun markPlayed(itemId: Long) {
        queueDao.updateStatus(itemId, QueueItemStatus.PLAYED.name)
    }

    suspend fun markSkipped(itemId: Long) {
        queueDao.updateStatus(itemId, QueueItemStatus.SKIPPED.name)
    }

    suspend fun remove(itemId: Long) {
        queueDao.delete(itemId)
    }

    suspend fun getAll(): List<QueueItemEntity> = queueDao.getAll()
}
