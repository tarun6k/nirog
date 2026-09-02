package com.nirog.ml

import android.content.Context
import java.io.File

/**
 * Resolves model and label files. Precedence: files under
 * filesDir/models/ (delivered by OTA model sync, no Play Store release needed)
 * beat the copies bundled in assets/models/. Model names:
 *   crop_router.tflite + crop_router.labels
 *   disease_<cropId>.tflite + disease_<cropId>.labels
 */
class ModelStore(private val context: Context) {

    private val updatedDir = File(context.filesDir, "models")

    /** Returns a readable file for [name], or throws [ModelNotAvailableException]. */
    fun resolve(name: String): File {
        val updated = File(updatedDir, name)
        if (updated.exists()) return updated
        // Assets can't be memory-mapped by path; copy once into cache.
        val cached = File(context.cacheDir, "models/$name")
        if (cached.exists()) return cached
        return runCatching {
            cached.parentFile!!.mkdirs()
            context.assets.open("models/$name").use { input ->
                cached.outputStream().use { input.copyTo(it) }
            }
            cached
        }.getOrElse { throw ModelNotAvailableException(name) }
    }

    fun labels(name: String): List<String> = resolve(name).readLines().filter { it.isNotBlank() }

    /** Where OTA sync (Phase 6/7) writes downloaded models. */
    fun updatedModelFile(name: String): File = File(updatedDir.apply { mkdirs() }, name)

    fun inferenceConfigText(): String =
        context.assets.open("config/inference.properties").use { it.readBytes().decodeToString() }
}
