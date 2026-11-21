package com.example.data

import androidx.annotation.StringRes
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.R

/**
 * Stores generated audio narrative metadata and script content for a localized block documentary.
 * Optionally linked to a completed [WalkRouteEntity] via [walkRouteId].
 */
@Entity(
    tableName = "walk_episodes",
    indices = [Index(value = ["walkRouteId"]), Index(value = ["latitude", "longitude"])]
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val walkRouteId: Long? = null,
    val title: String,
    val stationCallSign: String,
    val locationLabel: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val hookLine: String,
    val narrativeScript: String,
    val fieldFactsJoined: String,
    val explorerPrompt: String,
    val durationSeconds: Int = 60,
    val voicePersona: String = "Baritone Host",
    val speechRate: Float = 1.0f,
    val aiModelUsed: String = "gemini-3.8-flash",
    val wordCount: Int = 140,
    val playCount: Int = 1,
    val lastPlayedTimestamp: Long = System.currentTimeMillis(),
    val isBookmarked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    val fieldFacts: List<String>
        get() = fieldFactsJoined.split("||").map { it.trim() }.filter { it.isNotEmpty() }
}

data class RouteWaypoint(
    val latitude: Double,
    val longitude: Double,
    val label: String
)

/**
 * Stores a completed or saved walk route with start/end coordinates, breadcrumb waypoints,
 * total distance, duration, and Google Maps walking directions link.
 */
@Entity(tableName = "walk_routes")
data class WalkRouteEntity(
    @PrimaryKey(autoGenerate = true) val routeId: Long = 0,
    val routeTitle: String,
    val neighborhoodName: String,
    val cityRegion: String,
    val startBlockLabel: String,
    val endBlockLabel: String,
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double,
    val endLongitude: Double,
    val waypointsEncoded: String,
    val totalDistanceMeters: Int,
    val durationSeconds: Int,
    val blocksVisitedCount: Int,
    val narrativesGeneratedCount: Int,
    val editorialLens: String,
    val googleMapsUrl: String,
    val isFavorite: Boolean = false,
    val startedAtTimestamp: Long,
    val completedAtTimestamp: Long = System.currentTimeMillis()
) {
    val waypoints: List<RouteWaypoint>
        get() = waypointsEncoded.split("||").mapNotNull { token ->
            val parts = token.split("|")
            if (parts.size >= 3) {
                val lat = parts[0].toDoubleOrNull()
                val lng = parts[1].toDoubleOrNull()
                val label = parts.drop(2).joinToString("|")
                if (lat != null && lng != null) {
                    RouteWaypoint(lat, lng, label)
                } else null
            } else null
        }

    companion object {
        fun encodeWaypoints(points: List<RouteWaypoint>): String {
            return points.joinToString("||") { "${it.latitude}|${it.longitude}|${it.label}" }
        }
    }
}

/**
 * Relational join connecting a completed [WalkRouteEntity] with all [EpisodeEntity] audio
 * narratives triggered or recorded during that walk.
 */
data class WalkRouteWithNarratives(
    @Embedded val route: WalkRouteEntity,
    @Relation(
        parentColumn = "routeId",
        entityColumn = "walkRouteId"
    )
    val narratives: List<EpisodeEntity>
)

/**
 * Stores digital rewards & explorer badges (e.g. 'Early Bird', 'Marathoner') unlocked when
 * users complete historical tours, trigger GPS geofence milestones, or explore neighborhood lore.
 */
@Entity(tableName = "walk_badges")
data class WalkBadgeEntity(
    @PrimaryKey val badgeId: String,
    val category: String,
    val iconType: String,
    val xpReward: Int,
    val currentProgress: Int,
    val targetProgress: Int,
    val progressUnit: String,
    val isUnlocked: Boolean,
    val unlockedAtTimestamp: Long? = null,
    val unlockedTourTitle: String? = null,
    val sortOrder: Int = 0
) {
    val progressFraction: Float
        get() = if (targetProgress > 0) {
            (currentProgress.toFloat() / targetProgress.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

enum class DocumentaryLens(
    val label: String,
    @StringRes val labelRes: Int,
    val frequencyTag: String,
    val promptFocus: String
) {
    ALL_ROUNDER(
        label = "Street Documentary",
        labelRes = R.string.lens_all_rounder,
        frequencyTag = "99.1 FM • URBAN",
        promptFocus = "a rich blend of neighborhood history, architectural details, and surprising local lore"
    ),
    ARCHITECTURE(
        label = "Architecture & Facades",
        labelRes = R.string.lens_architecture,
        frequencyTag = "91.5 FM • DESIGN",
        promptFocus = "hidden architectural details, building materials, street grid evolution, cornices, churches, and urban design secrets"
    ),
    LOCAL_TRIVIA(
        label = "Diners & Local Trivia",
        labelRes = R.string.lens_local_trivia,
        frequencyTag = "94.7 FM • CULTURE",
        promptFocus = "quirky neighborhood trivia, classic local diners, corner storefronts, cultural characters, and everyday street life"
    ),
    BRIDGES_INFRA(
        label = "Bridges & Hidden Grid",
        labelRes = R.string.lens_bridges_infra,
        frequencyTag = "88.5 FM • INFRA",
        promptFocus = "bridges, buried creeks, transit lines, utility markers, typography on manhole covers, and civil engineering marvels"
    ),
    DEEP_HISTORY(
        label = "Deep Block History",
        labelRes = R.string.lens_deep_history,
        frequencyTag = "101.3 FM • ARCHIVE",
        promptFocus = "what stood on this exact block 50 to 150 years ago, historical markers, forgotten events, and how the neighborhood transformed"
    )
}

/**
 * Stores structured app analytics & user engagement events in Room (`app_events`) to power
 * the Peak-Delight & Anti-Fatigue Smart Auto-Popup Algorithm (Update, Share App, Rate App).
 */
@Entity(
    tableName = "app_events",
    indices = [Index(value = ["eventType"]), Index(value = ["timestamp"])]
)
data class AppEventEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val eventType: String,
    val eventCategory: String,
    val screenName: String,
    val metadataSummary: String,
    val delightScoreDelta: Int = 0,
    val sessionNumber: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)

