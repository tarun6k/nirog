package com.nirog.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Room
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT * FROM product") suspend fun products(): List<ProductEntity>
    @Query("SELECT * FROM label_claim") suspend fun labelClaims(): List<LabelClaimEntity>
    @Query("SELECT * FROM banned_active") suspend fun bannedActives(): List<BannedActiveEntity>
    @Query("SELECT * FROM etl_threshold") suspend fun etlThresholds(): List<EtlThresholdEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProducts(rows: List<ProductEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLabelClaims(rows: List<LabelClaimEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBannedActives(rows: List<BannedActiveEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEtlThresholds(rows: List<EtlThresholdEntity>)

    @Query("SELECT * FROM product WHERE id = :id") suspend fun product(id: String): ProductEntity?

    @Query(
        "DELETE FROM label_claim WHERE productId = :productId AND cropId = :cropId " +
            "AND pestId = :pestId AND effectiveFrom = :effectiveFrom",
    )
    suspend fun deleteLabelClaim(productId: String, cropId: String, pestId: String, effectiveFrom: Long)
}

@Dao
interface OutbreakDao {
    @Query(
        "SELECT COUNT(*) FROM outbreak_report WHERE geohash5 = :geohash5 AND cropId = :cropId AND createdAt >= :sinceMillis",
    )
    suspend fun countSince(geohash5: String, cropId: String, sinceMillis: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(row: OutbreakReportEntity)

    @Query("SELECT * FROM outbreak_report WHERE synced = 0") suspend fun pending(): List<OutbreakReportEntity>

    @Query("UPDATE outbreak_report SET synced = 1 WHERE geohash5 = :geohash5 AND createdAt = :createdAt")
    suspend fun markSynced(geohash5: String, createdAt: Long)

    @Query("SELECT * FROM nearby_outbreak WHERE cropId = :cropId")
    suspend fun nearby(cropId: String): List<NearbyOutbreakEntity>

    @Query("DELETE FROM nearby_outbreak WHERE cropId = :cropId")
    suspend fun clearNearby(cropId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNearby(rows: List<NearbyOutbreakEntity>)
}

@Dao
interface EscalationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(row: EscalationTicketEntity)

    @Query("SELECT * FROM escalation_ticket WHERE uploadConsent = 1 AND synced = 0")
    suspend fun pendingUploads(): List<EscalationTicketEntity>

    @Query("SELECT COUNT(*) FROM escalation_ticket WHERE uploadConsent = 1 AND synced = 0")
    suspend fun pendingUploadCount(): Int

    @Query("UPDATE escalation_ticket SET uploadConsent = 1 WHERE scanId = :scanId")
    suspend fun grantConsent(scanId: String)

    @Query("UPDATE escalation_ticket SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}

@Dao
interface FarmerDao {
    @Query("SELECT * FROM farmer LIMIT 1") suspend fun current(): FarmerEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: FarmerEntity)
}

@Dao
interface DiaryDao {
    @Query("SELECT * FROM spray_log WHERE plotId = :plotId ORDER BY date DESC")
    fun sprayLogsForPlot(plotId: String): Flow<List<SprayLogEntity>>

    @Query("SELECT * FROM spray_log WHERE plotId = :plotId ORDER BY date DESC LIMIT 2")
    suspend fun lastTwoSprays(plotId: String): List<SprayLogEntity>

    @Insert suspend fun insertSprayLog(row: SprayLogEntity)
}

@Dao
interface PlotDao {
    @Query("SELECT * FROM plot WHERE farmerId = :farmerId") fun plots(farmerId: String): Flow<List<PlotEntity>>
    @Query("SELECT * FROM plot WHERE id = :id") suspend fun plot(id: String): PlotEntity?
    @Query("SELECT * FROM plot") suspend fun allPlots(): List<PlotEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: PlotEntity)
}

@Dao
interface ScanDao {
    @Insert suspend fun insertSession(row: ScanSessionEntity)
    @Insert suspend fun insertDiagnosis(row: DiagnosisEntity)
    @Insert suspend fun insertContext(row: ContextSnapshotEntity)
    @Query("SELECT * FROM scan_session WHERE plotId = :plotId ORDER BY createdAt DESC")
    fun sessionsForPlot(plotId: String): Flow<List<ScanSessionEntity>>

    @Query("SELECT * FROM scan_session WHERE id = :id") suspend fun session(id: String): ScanSessionEntity?
}

@Database(
    version = 1,
    exportSchema = true,
    entities = [
        FarmerEntity::class, PlotEntity::class, ScanSessionEntity::class, NearbyOutbreakEntity::class,
        ContextSnapshotEntity::class, DiagnosisEntity::class, ProductEntity::class,
        LabelClaimEntity::class, BannedActiveEntity::class, EtlThresholdEntity::class,
        SprayLogEntity::class, EscalationTicketEntity::class, OutbreakReportEntity::class,
    ],
)
abstract class NirogDb : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun diaryDao(): DiaryDao
    abstract fun plotDao(): PlotDao
    abstract fun scanDao(): ScanDao
    abstract fun outbreakDao(): OutbreakDao
    abstract fun escalationDao(): EscalationDao
    abstract fun farmerDao(): FarmerDao

    companion object {
        @Volatile private var instance: NirogDb? = null

        /** Process-wide singleton so workers and DI share one connection. */
        fun create(context: Context): NirogDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NirogDb::class.java, "nirog.db")
                .build().also { instance = it }
        }
    }
}
