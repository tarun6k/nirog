package com.nirog.data

import android.content.Context
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * DPDP "delete everything about me": every Room table, every captured image,
 * and any queued upload work. Models and config stay — they are not personal data.
 * Wired to a settings action in Phase 8; the path exists and cascades now.
 */
suspend fun deleteEverything(context: Context, db: NirogDb) = withContext(Dispatchers.IO) {
    WorkManager.getInstance(context).cancelUniqueWork(Sync.WORK_NAME)
    db.clearAllTables()
    File(context.filesDir, "scans").deleteRecursively()
}
