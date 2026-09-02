package com.nirog.data

import com.nirog.model.Weather14d
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface WeatherProvider {
    /** Last-14-days aggregate, or null when unavailable (offline, API down). Never throws. */
    suspend fun last14d(lat: Double, lon: Double): Weather14d?
}

/** Open-Meteo: free, no API key, no farmer identifier sent — only coordinates. */
class OpenMeteoWeatherProvider : WeatherProvider {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun last14d(lat: Double, lon: Double): Weather14d? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&daily=precipitation_sum,temperature_2m_max,temperature_2m_min,relative_humidity_2m_mean" +
                "&past_days=14&forecast_days=1&timezone=UTC"
            val body = client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string() ?: return@withContext null
            }
            val daily = JSONObject(body).getJSONObject("daily")
            fun series(name: String): List<Double> {
                val arr = daily.getJSONArray(name)
                // take the 14 past days, excluding today's (last) partial entry
                return (0 until arr.length() - 1).map { arr.optDouble(it, Double.NaN) }.filter { !it.isNaN() }
            }
            val rain = series("precipitation_sum")
            val tmax = series("temperature_2m_max")
            val tmin = series("temperature_2m_min")
            val rh = series("relative_humidity_2m_mean")
            if (rain.isEmpty() || tmax.isEmpty() || tmin.isEmpty() || rh.isEmpty()) return@withContext null
            Weather14d(
                rainMmTotal = rain.sum(),
                avgRhPct = rh.average(),
                avgTMaxC = tmax.average(),
                avgTMinC = tmin.average(),
            )
        }.getOrNull()
    }
}

/**
 * IMD (India Meteorological Department) requires IP whitelisting we don't have
 * yet. Wire the real endpoint here when access is granted; the interface is the
 * contract, callers never know which provider answered.
 */
class ImdWeatherProvider : WeatherProvider {
    override suspend fun last14d(lat: Double, lon: Double): Weather14d? = null
}
