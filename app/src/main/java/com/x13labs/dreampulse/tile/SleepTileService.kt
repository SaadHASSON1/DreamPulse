package com.x13labs.dreampulse.tile

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.x13labs.dreampulse.R
import com.x13labs.dreampulse.data.local.PreferencesManager
import com.x13labs.dreampulse.ui.MainActivity
import com.x13labs.dreampulse.ui.components.formatClock
import com.x13labs.dreampulse.ui.components.formatDuration
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future

/**
 * Tile (swipe from the watch face): shows the sleep goal with a start button when idle,
 * "waiting for sleep" once started, and the alarm time once asleep. Tapping opens the app;
 * starting itself happens in the app, which owns the permission checks.
 */
class SleepTileService : TileService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TileEntryPoint {
        fun preferencesManager(): PreferencesManager
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        scope.future {
            val prefs = EntryPointAccessors.fromApplication(applicationContext, TileEntryPoint::class.java).preferencesManager()
            val tracking = prefs.isTrackingActive.first() && prefs.serviceStartTime.first() > 0
            val confirmed = prefs.isSleepConfirmed.first()
            val target = prefs.targetWakeTime.first()
            val duration = prefs.sleepDuration.first()

            val content = when {
                tracking && confirmed && target > 0 -> column(
                    text(getString(R.string.tile_alarm), 13f, MUTED),
                    text(formatClock(this@SleepTileService, target), 34f, MOON),
                )
                tracking -> column(
                    text(getString(R.string.waiting_title), 15f, LIGHT),
                    text(if (target > 0) getString(R.string.latest_alarm, formatClock(this@SleepTileService, target)) else "", 12f, MUTED),
                )
                else -> column(
                    text(getString(R.string.sleep_goal), 12f, MUTED),
                    text(formatDuration(duration), 32f, LIGHT),
                    spacer(8f),
                    pill(getString(R.string.tile_start)),
                )
            }

            val root = LayoutElementBuilders.Box.Builder()
                .setWidth(expand()).setHeight(expand())
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(openApp())
                        .setBackground(ModifiersBuilders.Background.Builder().setColor(argb(SKY)).build())
                        .build()
                )
                .addContent(content)
                .build()

            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun openApp(): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(ActionBuilders.launchAction(ComponentName(this, MainActivity::class.java)))
            .build()

    private fun column(vararg items: LayoutElementBuilders.LayoutElement): LayoutElementBuilders.Column {
        val b = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        items.forEach { b.addContent(it) }
        return b.build()
    }

    private fun text(value: String, size: Float, color: Int): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(1)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder().setSize(sp(size)).setColor(argb(color)).build()
            )
            .build()

    private fun spacer(height: Float): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(height)).build()

    private fun pill(label: String): LayoutElementBuilders.Box =
        LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(MOON_DEEP))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(20f)).build())
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(dp(16f)).setEnd(dp(16f)).setTop(dp(8f)).setBottom(dp(8f))
                            .build()
                    )
                    .build()
            )
            .addContent(text(label, 14f, LIGHT))
            .build()

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val SKY = 0xFF0B1026.toInt()
        private const val LIGHT = 0xFFEEEDFE.toInt()
        private const val MOON = 0xFFAFA9EC.toInt()
        private const val MOON_DEEP = 0xFF534AB7.toInt()
        private const val MUTED = 0xFF8A8FB0.toInt()

        /** Ask the system to refresh the tile after the session state changes. */
        fun requestUpdate(context: Context) {
            try {
                getUpdater(context).requestUpdate(SleepTileService::class.java)
            } catch (e: Exception) {
                android.util.Log.w("SleepTile", "Tile update request failed", e)
            }
        }
    }
}
