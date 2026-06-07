package com.aiva.console.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AivaSettings(
    val backendUrl: String = "http://192.168.1.10:8420",
    val token: String = "",
    val refreshSec: Int = 3,
    val accentIndex: Int = 0,
    val kiosk: Boolean = true,
    val mockMode: Boolean = true,
    /** Lens calibration: "cx,cy,r;cx,cy,r" as window fractions ("" = unset). */
    val lensCal: String = "",
    /** Camera-cluster treatment: eyes | gauges | hangar | radar. */
    val lensStyle: String = "eyes",
    /** Dock design: wave (band + tabs) | rail (left rail + strip pager). */
    val dockDesign: String = "wave",
    /** Rail collapsed to a thin handle. */
    val railCollapsed: Boolean = false,
    /** Ambient clock style: 0 orbit · 1 analog · 2 terminal · 3 eclipse · 4 iphone · 5 horizon. */
    val ambientStyle: Int = 0,
    /** Ambient clock accent color index (see AMBIENT_COLORS). */
    val ambientColor: Int = 0,
    /** Timestamp of the last scheduler run we raised a notification for. */
    val lastScheduleNotified: String = "",
)

private val Context.dataStore by preferencesDataStore(name = "aiva_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val url = stringPreferencesKey("backend_url")
        val token = stringPreferencesKey("api_token")
        val refresh = intPreferencesKey("refresh_sec")
        val accent = intPreferencesKey("accent_index")
        val kiosk = booleanPreferencesKey("kiosk")
        val mock = booleanPreferencesKey("mock_mode")
        val lensCal = stringPreferencesKey("lens_cal")
        val lensStyle = stringPreferencesKey("lens_style")
        val dockDesign = stringPreferencesKey("dock_design")
        val railCollapsed = booleanPreferencesKey("rail_collapsed")
        val ambientStyle = intPreferencesKey("ambient_style")
        val ambientColor = intPreferencesKey("ambient_color")
        val lastScheduleNotified = stringPreferencesKey("last_schedule_notified")
    }

    val settings: Flow<AivaSettings> = context.dataStore.data.map { p ->
        AivaSettings(
            backendUrl = p[Keys.url] ?: AivaSettings().backendUrl,
            token = p[Keys.token] ?: "",
            refreshSec = p[Keys.refresh] ?: 3,
            accentIndex = p[Keys.accent] ?: 0,
            kiosk = p[Keys.kiosk] ?: true,
            mockMode = p[Keys.mock] ?: true,
            lensCal = p[Keys.lensCal] ?: "",
            lensStyle = p[Keys.lensStyle] ?: "eyes",
            dockDesign = p[Keys.dockDesign] ?: "wave",
            railCollapsed = p[Keys.railCollapsed] ?: false,
            ambientStyle = p[Keys.ambientStyle] ?: 0,
            ambientColor = p[Keys.ambientColor] ?: 0,
            lastScheduleNotified = p[Keys.lastScheduleNotified] ?: "",
        )
    }

    suspend fun update(s: AivaSettings) {
        context.dataStore.edit { p ->
            p[Keys.url] = s.backendUrl
            p[Keys.token] = s.token
            p[Keys.refresh] = s.refreshSec
            p[Keys.accent] = s.accentIndex
            p[Keys.kiosk] = s.kiosk
            p[Keys.mock] = s.mockMode
            p[Keys.lensCal] = s.lensCal
            p[Keys.lensStyle] = s.lensStyle
            p[Keys.dockDesign] = s.dockDesign
            p[Keys.railCollapsed] = s.railCollapsed
            p[Keys.ambientStyle] = s.ambientStyle
            p[Keys.ambientColor] = s.ambientColor
            p[Keys.lastScheduleNotified] = s.lastScheduleNotified
        }
    }
}
