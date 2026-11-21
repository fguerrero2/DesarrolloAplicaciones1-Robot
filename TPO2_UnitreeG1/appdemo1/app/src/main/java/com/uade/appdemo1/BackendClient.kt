package com.uade.appdemo1

import com.uade.appdemo1.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal object BackendClient {
    private val baseUrl = BuildConfig.BACKEND_URL.trimEnd('/')

    suspend fun readRobot(): RobotSnapshot = request("GET", "/api/v1/robot")

    suspend fun configureArea(): RobotSnapshot = request(
        method = "PUT",
        path = "/api/v1/area",
        payload = JSONObject().put("width", 6).put("length", 8),
    )

    suspend fun moveTo(point: InspectionPoint): RobotSnapshot = request(
        method = "POST",
        path = "/api/v1/robot/move",
        payload = JSONObject().put("x", point.x).put("y", point.y),
    )

    suspend fun stop(): RobotSnapshot = request("POST", "/api/v1/robot/stop")

    private suspend fun request(
        method: String,
        path: String,
        payload: JSONObject? = null,
    ): RobotSnapshot = withContext(Dispatchers.IO) {
        val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 2_500
            connection.readTimeout = 3_500
            connection.setRequestProperty("Accept", "application/json")
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("Backend HTTP $status: $responseBody")

            val response = JSONObject(responseBody)
            val position = response.getJSONObject("position")
            val area = response.getJSONObject("area")
            RobotSnapshot(
                mode = response.getString("mode"),
                x = position.getDouble("x"),
                y = position.getDouble("y"),
                width = area.getDouble("width"),
                length = area.getDouble("length"),
                simulator = response.optString("simulator", "unknown"),
            )
        } finally {
            connection.disconnect()
        }
    }
}