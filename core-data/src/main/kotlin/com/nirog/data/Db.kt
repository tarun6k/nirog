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
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: PlotEntity)
}

@Dao
interface ScanDao {
    @Insert suspend fun insertSession(row: ScanSessionEntity)
    @Insert suspend fun insertDiagnosis(row: DiagnosisEntity)
    @Insert suspend fun insertContext(row: ContextSnapshotEntity)
    @Query("SELECT * FROM scan_session WHERE plotId = :plotId ORDER BY createdAt DESC")
    fun sessionsForPlot(plotId: String): Flow<List<ScanSessionEntity>>
}

@Database(
    version = 1,
    exportSchema = true,
    entities = [
        FarmerEntity::class, PlotEntity::class, ScanSessionEntity::class,
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

    companion object {
        fun create(context: Context): NirogDb =
            Room.databaseBuilder(context.applicationContext, NirogDb::class.java, "nirog.db").build()
    }
}
