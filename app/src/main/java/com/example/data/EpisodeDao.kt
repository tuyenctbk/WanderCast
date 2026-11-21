package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    // --- Audio Narrative (EpisodeEntity) Queries ---
    @Query("SELECT * FROM walk_episodes ORDER BY timestamp DESC")
    fun getAllEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM walk_episodes WHERE walkRouteId = :routeId ORDER BY timestamp ASC")
    fun getEpisodesForRoute(routeId: Long): Flow<List<EpisodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: EpisodeEntity): Long

    @Query("UPDATE walk_episodes SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun updateBookmark(id: Long, bookmarked: Boolean)

    @Query(
        "UPDATE walk_episodes SET playCount = playCount + 1, lastPlayedTimestamp = :playedAt, " +
            "voicePersona = :voicePersona, speechRate = :speechRate WHERE id = :id"
    )
    suspend fun recordNarrativePlayback(
        id: Long,
        playedAt: Long,
        voicePersona: String,
        speechRate: Float
    )

    @Query("UPDATE walk_episodes SET walkRouteId = :routeId WHERE id IN (:episodeIds)")
    suspend fun attachEpisodesToRoute(routeId: Long, episodeIds: List<Long>)

    @Query("DELETE FROM walk_episodes WHERE id = :id")
    suspend fun deleteEpisodeById(id: Long)

    // --- Completed Walk Routes & Relational Tour History Queries ---
    @Transaction
    @Query("SELECT * FROM walk_routes ORDER BY completedAtTimestamp DESC")
    fun getAllWalkRoutesWithNarratives(): Flow<List<WalkRouteWithNarratives>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWalkRoute(route: WalkRouteEntity): Long

    @Query("UPDATE walk_routes SET isFavorite = :favorite WHERE routeId = :routeId")
    suspend fun updateRouteFavorite(routeId: Long, favorite: Boolean)

    @Query("DELETE FROM walk_routes WHERE routeId = :routeId")
    suspend fun deleteWalkRouteById(routeId: Long)

    @Query("SELECT COUNT(*) FROM walk_routes")
    suspend fun countWalkRoutes(): Int

    // --- Walk History Digital Badges & Rewards (WalkBadgeEntity) Queries ---
    @Query("SELECT * FROM walk_badges ORDER BY sortOrder ASC")
    fun getAllWalkBadges(): Flow<List<WalkBadgeEntity>>

    @Query("SELECT * FROM walk_badges ORDER BY sortOrder ASC")
    suspend fun getWalkBadgesSnapshot(): List<WalkBadgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWalkBadges(badges: List<WalkBadgeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWalkBadge(badge: WalkBadgeEntity)

    @Query("SELECT COUNT(*) FROM walk_badges")
    suspend fun countWalkBadges(): Int

    // --- App Event Logging & Telemetry (AppEventEntity) Queries ---
    @Query("SELECT * FROM app_events ORDER BY timestamp DESC LIMIT 80")
    fun getRecentAppEvents(): Flow<List<AppEventEntity>>

    @Query("SELECT * FROM app_events ORDER BY timestamp DESC LIMIT 120")
    suspend fun getAppEventsSnapshot(): List<AppEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppEvent(event: AppEventEntity): Long

    @Query("SELECT COUNT(*) FROM app_events")
    suspend fun countAppEvents(): Int

    @Query("DELETE FROM app_events")
    suspend fun clearAppEvents()
}

