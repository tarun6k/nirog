package com.nirog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nirog.data.DiagnosisEntity
import com.nirog.data.NirogDb
import com.nirog.data.PlotEntity
import com.nirog.data.toDomain
import com.nirog.engine.Geohash
import com.nirog.engine.RecommendationResult
import com.nirog.feature.diagnosis.AnalysingScreen
import com.nirog.feature.diagnosis.AnalysisStep
import com.nirog.feature.diagnosis.DiagnosisPipeline
import com.nirog.feature.diagnosis.EscalatedScreen
import com.nirog.feature.diagnosis.ResultAmbiguousScreen
import com.nirog.feature.diagnosis.ResultConfidentScreen
import com.nirog.feature.diagnosis.pestNameHi
import com.nirog.feature.scan.GuidedCaptureScreen
import com.nirog.feature.scan.ScanStore
import com.nirog.feature.treatment.RecommendationLoader
import com.nirog.feature.treatment.TreatmentLadderScreen
import com.nirog.model.Product
import com.nirog.model.Verdict
import com.nirog.ui.NirogTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

private sealed interface Screen {
    data object Home : Screen
    data object Capture : Screen
    data class Analysing(val sessionId: String) : Screen
    data class Result(val diagnosis: DiagnosisEntity, val notes: List<String>) : Screen
    data class Ladder(
        val result: RecommendationResult,
        val pestId: String,
        val severityPct: Double,
        val products: Map<String, Product>,
    ) : Screen
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var db: NirogDb
    @Inject lateinit var scanStore: ScanStore
    @Inject lateinit var pipeline: DiagnosisPipeline

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NirogTheme { Root() } }
    }

    @Composable
    private fun Root() {
        var screen by remember { mutableStateOf<Screen>(Screen.Home) }
        var plot by remember { mutableStateOf<PlotEntity?>(null) }
        var outbreaks by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            var p = db.plotDao().plots("demo-farmer").firstOrNull()?.firstOrNull()
            if (p == null && BuildConfig.DEBUG) {
                // Debug-only sample plot so screens work before onboarding exists (artboard 12).
                p = PlotEntity(
                    id = UUID.randomUUID().toString(), farmerId = "demo-farmer", label = "रोहतक",
                    cropId = "wheat", variety = null, areaValue = 2.5, areaUnit = "ACRE",
                    sowingDate = LocalDate.now().minusDays(62).toEpochDay(),
                    lat = 28.8955, lon = 76.6066, pincode = "124001",
                    plannedHarvestDate = LocalDate.now().plusDays(60).toEpochDay(),
                    organicStatus = "NONE", state = "HARYANA",
                )
                db.plotDao().upsert(p)
            }
            plot = p
            if (p?.lat != null && p.lon != null) {
                outbreaks = db.outbreakDao().countSince(
                    Geohash.encode(p.lat!!, p.lon!!, 5), p.cropId,
                    Instant.now().minus(14, ChronoUnit.DAYS).toEpochMilli(),
                )
            }
        }

        BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

        when (val s = screen) {
            Screen.Home -> HomeScreen(plot, outbreaks) { screen = Screen.Capture }

            Screen.Capture -> GuidedCaptureScreen(
                plotId = plot?.id ?: return,
                store = scanStore,
            ) { sessionId -> screen = Screen.Analysing(sessionId) }

            is Screen.Analysing -> {
                AnalysingScreen(AnalysisStep.WEATHER)
                LaunchedEffect(s.sessionId) {
                    val session = db.scanDao().sessionsForPlot(plot!!.id).firstOrNull()
                        ?.firstOrNull { it.id == s.sessionId } ?: return@LaunchedEffect
                    val result = pipeline.run(session)
                    screen = Screen.Result(result.diagnosis, result.noteKeys)
                }
            }

            is Screen.Result -> ResultScreen(s) { screen = it }

            is Screen.Ladder -> TreatmentLadderScreen(
                result = s.result,
                pestNameHi = pestNameHi(s.pestId),
                severityPct = s.severityPct,
                products = s.products,
                onDose = { /* dose calculator lands in Phase 5 */ },
            )
        }
    }

    @Composable
    private fun ResultScreen(s: Screen.Result, navigate: (Screen) -> Unit) {
        val loader = remember { RecommendationLoader(db) }
        suspend fun toLadder(diagnosis: DiagnosisEntity) {
            val plotId = plotIdOf(diagnosis) ?: return
            val result = loader.recommend(diagnosis, plotId)
            val products = db.catalogDao().products().associate { it.id to it.toDomain() }
            navigate(Screen.Ladder(result, topPest(diagnosis), diagnosis.severityPct, products))
        }
        var pending by remember { mutableStateOf<DiagnosisEntity?>(null) }
        LaunchedEffect(pending) { pending?.let { toLadder(it) } }

        when (Verdict.valueOf(s.diagnosis.verdict)) {
            Verdict.CONFIDENT -> ResultConfidentScreen(
                pestId = topPest(s.diagnosis),
                severityPct = s.diagnosis.severityPct,
                noteKeys = s.notes,
                onTreatment = { pending = s.diagnosis },
            )
            Verdict.AMBIGUOUS -> ResultAmbiguousScreen(
                candidates = s.diagnosis.toDomain().candidates,
                noteKeys = s.notes,
                onPick = { pestId ->
                    // The farmer's physical differential test is a human confirmation.
                    pending = s.diagnosis.copy(
                        candidates = "$pestId:1.0",
                        verdict = Verdict.CONFIDENT.name,
                        source = com.nirog.model.DiagnosisSource.HUMAN.name,
                    )
                },
                onEscalate = { navigate(Screen.Result(s.diagnosis.copy(verdict = Verdict.ABSTAIN.name), s.notes)) },
            )
            Verdict.ABSTAIN -> EscalatedScreen(s.notes) { navigate(Screen.Home) }
        }
    }

    private fun topPest(d: DiagnosisEntity): String =
        d.candidates.split(';').firstOrNull()?.substringBefore(':') ?: ""

    private suspend fun plotIdOf(d: DiagnosisEntity): String? =
        db.plotDao().plots("demo-farmer").firstOrNull()?.firstOrNull()?.id
}
