package com.nirog.app

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirog.ui.MicButton
import com.nirog.ui.Palette
import com.nirog.ui.PaperScreen
import com.nirog.ui.PrimaryButton
import com.nirog.ui.R as UiR

/** Live connectivity as Compose state (validated-internet default network). */
@Composable
fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    var online by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        online = cm.activeNetwork
            ?.let { cm.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } == true
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                online = true
            }

            override fun onLost(network: Network) {
                online = cm.activeNetwork != null && cm.activeNetwork != network
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return online
}

/**
 * Artboard 13: leads with what still works in green checked cards; the missing
 * pieces sit in one dashed "later" card. Capture never waits for signal.
 */
@Composable
fun OfflineScreen(queuedPhotos: Int, onScan: () -> Unit) {
    PaperScreen {
        // Dark header — the only dark band on a paper screen
        Row(
            Modifier.fillMaxWidth().background(Palette.Ink).padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("📵", fontSize = 32.sp)
            Column {
                Text(
                    stringResource(UiR.string.offline_title),
                    fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Paper,
                )
                Text(
                    stringResource(UiR.string.offline_sub),
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.Hairline, lineHeight = 24.sp,
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                stringResource(UiR.string.offline_still_works),
                fontSize = 14.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = Palette.GreenPressed,
            )
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StillWorksCard("📷", UiR.string.offline_capture, UiR.string.offline_capture_sub)
                StillWorksCard("📔", UiR.string.offline_diary, UiR.string.offline_diary_sub)
                StillWorksCard("🌿", UiR.string.offline_recognition, UiR.string.offline_recognition_sub)
            }
            Text(
                stringResource(UiR.string.offline_when_signal),
                fontSize = 14.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = Palette.Stone,
                modifier = Modifier.padding(top = 16.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(Palette.DisabledBg)
                    .drawBehind {
                        drawRect(
                            Palette.Disabled,
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                        )
                    }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("⬆", fontSize = 22.sp, color = Palette.Stone)
                Text(
                    if (queuedPhotos > 0) {
                        stringResource(UiR.string.offline_queued, queuedPhotos)
                    } else {
                        stringResource(UiR.string.offline_queued_none)
                    },
                    fontSize = 16.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextSecondary,
                )
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(
                stringResource(UiR.string.offline_scan_now),
                Modifier.weight(1f).height(70.dp),
                onClick = onScan,
            )
            MicButton()
        }
    }
}

@Composable
private fun StillWorksCard(icon: String, titleRes: Int, subRes: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.TintGreen)
            .border(1.5.dp, Palette.Green)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(icon, fontSize = 24.sp)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(titleRes),
                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.GreenPressed,
            )
            Text(
                stringResource(subRes),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Green,
            )
        }
        Text("✓", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Palette.GreenPressed)
    }
}
