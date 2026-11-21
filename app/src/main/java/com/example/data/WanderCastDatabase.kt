package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        EpisodeEntity::class,
        WalkRouteEntity::class,
        WalkBadgeEntity::class,
        AppEventEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class WanderCastDatabase : RoomDatabase() {
    abstract fun episodeDao(): EpisodeDao

    companion object {
        @Volatile
        private var INSTANCE: WanderCastDatabase? = null

        fun getInstance(context: Context): WanderCastDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WanderCastDatabase::class.java,
                    "wandercast_archive.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
