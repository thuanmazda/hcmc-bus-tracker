package com.hcmcbustracker.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hcmcbustracker.core.database.BusTrackerDatabase
import com.hcmcbustracker.core.database.dao.BusStopDao
import com.hcmcbustracker.core.database.dao.FavoriteRouteDao
import com.hcmcbustracker.core.database.dao.FavoriteStopDao
import com.hcmcbustracker.core.database.dao.RouteDao
import com.hcmcbustracker.core.database.dao.RoutePathDao
import com.hcmcbustracker.core.database.dao.RouteVariantDao
import com.hcmcbustracker.core.database.dao.StaticDatasetMetadataDao
import com.hcmcbustracker.core.database.dao.SyncMetadataDao
import com.hcmcbustracker.core.database.repository.BusRepository
import com.hcmcbustracker.core.database.repository.BusRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `favorite_routes` (
                `routeId` INTEGER NOT NULL, 
                `addedAtTimestamp` INTEGER NOT NULL, 
                PRIMARY KEY(`routeId`)
            )
        """.trimIndent())
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `static_dataset_metadata` (
                `id` TEXT NOT NULL,
                `datasetVersion` TEXT NOT NULL,
                `datasetCreatedAt` INTEGER NOT NULL,
                `datasetUpdatedAt` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                `totalRoutes` INTEGER NOT NULL,
                `totalVariants` INTEGER NOT NULL,
                `totalStops` INTEGER NOT NULL,
                `totalPolylines` INTEGER NOT NULL,
                `checksum` TEXT NOT NULL,
                `lastSyncStatus` TEXT NOT NULL,
                `lastErrorMessage` TEXT,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        // Recreate route_variants with composite primary key (routeId, id) because EBMS varIds
        // are scoped per route (1, 2, ...) rather than globally unique across routes.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `route_variants_new` (
                `id` INTEGER NOT NULL,
                `routeId` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `shortName` TEXT NOT NULL,
                `routeNo` TEXT NOT NULL,
                `startStop` TEXT NOT NULL,
                `endStop` TEXT NOT NULL,
                `distanceMeters` REAL NOT NULL,
                `isOutbound` INTEGER NOT NULL,
                `runningTimeMinutes` INTEGER NOT NULL,
                PRIMARY KEY(`routeId`, `id`)
            )
        """.trimIndent())
        val tableCheck = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='route_variants'")
        val hasOldTable = tableCheck.moveToFirst()
        tableCheck.close()
        if (hasOldTable) {
            db.execSQL("""
                INSERT OR IGNORE INTO `route_variants_new` (
                    `id`, `routeId`, `name`, `shortName`, `routeNo`,
                    `startStop`, `endStop`, `distanceMeters`, `isOutbound`, `runningTimeMinutes`
                )
                SELECT `id`, `routeId`, `name`, `shortName`, `routeNo`,
                       `startStop`, `endStop`, `distanceMeters`, `isOutbound`, `runningTimeMinutes`
                FROM `route_variants`
            """.trimIndent())
            db.execSQL("DROP TABLE `route_variants`")
        }
        db.execSQL("ALTER TABLE `route_variants_new` RENAME TO `route_variants`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_variants_routeId` ON `route_variants` (`routeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_variants_routeNo` ON `route_variants` (`routeNo`)")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindBusRepository(impl: BusRepositoryImpl): BusRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BusTrackerDatabase {
        return Room.databaseBuilder(
            context,
            BusTrackerDatabase::class.java,
            BusTrackerDatabase.DATABASE_NAME
        )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()
    }

    @Provides
    fun provideRouteDao(db: BusTrackerDatabase): RouteDao = db.routeDao()

    @Provides
    fun provideRouteVariantDao(db: BusTrackerDatabase): RouteVariantDao = db.routeVariantDao()

    @Provides
    fun provideBusStopDao(db: BusTrackerDatabase): BusStopDao = db.busStopDao()

    @Provides
    fun provideRoutePathDao(db: BusTrackerDatabase): RoutePathDao = db.routePathDao()

    @Provides
    fun provideFavoriteStopDao(db: BusTrackerDatabase): FavoriteStopDao = db.favoriteStopDao()

    @Provides
    fun provideFavoriteRouteDao(db: BusTrackerDatabase): FavoriteRouteDao = db.favoriteRouteDao()

    @Provides
    fun provideSyncMetadataDao(db: BusTrackerDatabase): SyncMetadataDao = db.syncMetadataDao()

    @Provides
    fun provideStaticDatasetMetadataDao(db: BusTrackerDatabase): StaticDatasetMetadataDao = db.staticDatasetMetadataDao()
}
