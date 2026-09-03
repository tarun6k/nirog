package com.nirog.app

import android.app.Application
import android.content.Context
import com.nirog.data.ContextBuilder
import com.nirog.data.NirogDb
import com.nirog.data.OpenMeteoWeatherProvider
import com.nirog.data.WeatherProvider
import com.nirog.engine.InferenceThresholds
import com.nirog.feature.diagnosis.DiagnosisPipeline
import com.nirog.feature.scan.ScanStore
import com.nirog.ml.ModelStore
import com.nirog.ml.InferenceEngine
import com.nirog.ml.LiteRtInferenceEngine
import com.nirog.ml.StubInferenceEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@HiltAndroidApp
class NirogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Drain any queued escalations/outbreak reports from previous runs.
        com.nirog.data.Sync.enqueue(this)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun db(@ApplicationContext context: Context): NirogDb = NirogDb.create(context)

    @Provides
    @Singleton
    fun scanStore(@ApplicationContext context: Context, db: NirogDb): ScanStore =
        ScanStore(context, db.scanDao())

    @Provides
    @Singleton
    fun weatherProvider(): WeatherProvider = OpenMeteoWeatherProvider()

    @Provides
    @Singleton
    fun modelStore(@ApplicationContext context: Context): ModelStore = ModelStore(context)

    @Provides
    @Singleton
    fun diagnosisPipeline(
        engine: InferenceEngine,
        weather: WeatherProvider,
        db: NirogDb,
        store: ModelStore,
    ): DiagnosisPipeline = DiagnosisPipeline(
        inference = engine,
        contextBuilder = ContextBuilder(weather, db),
        db = db,
        thresholds = InferenceThresholds.fromProperties(store.inferenceConfigText()),
        priorRules = com.nirog.engine.PriorAdjuster.parseRules(store.priorRulesText()),
    )

    @Provides
    @Singleton
    fun inferenceEngine(@ApplicationContext context: Context): InferenceEngine =
        // Stub is the debug default per the brief; real models load in release
        // (and still fail soft into ModelNotAvailableException until they exist).
        if (BuildConfig.DEBUG) StubInferenceEngine() else LiteRtInferenceEngine(context)
}
