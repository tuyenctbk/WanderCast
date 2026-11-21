package com.example.data

import kotlinx.coroutines.flow.Flow

class EpisodeRepository(private val episodeDao: EpisodeDao) {
    val allEpisodes: Flow<List<EpisodeEntity>> = episodeDao.getAllEpisodes()
    val allWalkRoutesWithNarratives: Flow<List<WalkRouteWithNarratives>> =
        episodeDao.getAllWalkRoutesWithNarratives()
    val allWalkBadges: Flow<List<WalkBadgeEntity>> = episodeDao.getAllWalkBadges()
    val recentAppEvents: Flow<List<AppEventEntity>> = episodeDao.getRecentAppEvents()

    suspend fun insertAppEvent(event: AppEventEntity): Long {
        return episodeDao.insertAppEvent(event)
    }

    suspend fun getAppEventsSnapshot(): List<AppEventEntity> {
        return episodeDao.getAppEventsSnapshot()
    }

    suspend fun clearAppEvents() {
        episodeDao.clearAppEvents()
    }

    suspend fun countAppEvents(): Int {
        return episodeDao.countAppEvents()
    }

    suspend fun insertEpisode(episode: EpisodeEntity): Long {
        return episodeDao.insertEpisode(episode)
    }

    suspend fun toggleBookmark(episode: EpisodeEntity) {
        episodeDao.updateBookmark(episode.id, !episode.isBookmarked)
    }

    suspend fun recordNarrativePlayback(
        episodeId: Long,
        voicePersona: String,
        speechRate: Float
    ) {
        episodeDao.recordNarrativePlayback(
            id = episodeId,
            playedAt = System.currentTimeMillis(),
            voicePersona = voicePersona,
            speechRate = speechRate
        )
    }

    suspend fun deleteEpisode(id: Long) {
        episodeDao.deleteEpisodeById(id)
    }

    suspend fun saveCompletedWalkRoute(
        route: WalkRouteEntity,
        associatedEpisodeIds: List<Long>
    ): Long {
        val routeId = episodeDao.insertWalkRoute(route)
        if (associatedEpisodeIds.isNotEmpty()) {
            episodeDao.attachEpisodesToRoute(routeId, associatedEpisodeIds)
        }
        return routeId
    }

    suspend fun toggleRouteFavorite(route: WalkRouteEntity) {
        episodeDao.updateRouteFavorite(route.routeId, !route.isFavorite)
    }

    suspend fun deleteWalkRoute(routeId: Long) {
        episodeDao.deleteWalkRouteById(routeId)
    }

    suspend fun countWalkRoutes(): Int {
        return episodeDao.countWalkRoutes()
    }

    suspend fun getWalkBadgesSnapshot(): List<WalkBadgeEntity> {
        return episodeDao.getWalkBadgesSnapshot()
    }

    suspend fun upsertWalkBadges(badges: List<WalkBadgeEntity>) {
        episodeDao.upsertWalkBadges(badges)
    }

    suspend fun upsertWalkBadge(badge: WalkBadgeEntity) {
        episodeDao.upsertWalkBadge(badge)
    }

    suspend fun countWalkBadges(): Int {
        return episodeDao.countWalkBadges()
    }
}
