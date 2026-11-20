package ar.edu.fguerrero.da1.robot_guia_universitario.data

import ar.edu.fguerrero.da1.robot_guia_universitario.domain.BackendConfig
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.BackendMode
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/** Endpoints REST (sección 7 del plan). */
interface RobotApi {
    @GET("health") suspend fun health(): HealthDto
    @GET("api/v1/robots") suspend fun robots(): List<RobotDto>
    @GET("api/v1/robots/{id}/telemetry") suspend fun telemetry(@Path("id") id: String): RobotTelemetryDto
    @POST("api/v1/missions/guide") suspend fun startGuide(@Body body: GuideMissionRequestDto): CommandResponseDto
    @POST("api/v1/missions/{id}/pause") suspend fun pause(@Path("id") id: String): CommandResponseDto
    @POST("api/v1/missions/{id}/resume") suspend fun resume(@Path("id") id: String): CommandResponseDto
    @POST("api/v1/missions/{id}/cancel") suspend fun cancel(@Path("id") id: String): CommandResponseDto
    @POST("api/v1/robots/{id}/return-safe") suspend fun returnSafe(@Path("id") id: String): CommandResponseDto
    @POST("api/v1/robots/{id}/stop") suspend fun stop(@Path("id") id: String): CommandResponseDto
    @POST("api/v1/alerts/{id}/ack") suspend fun ackAlert(@Path("id") id: String): CommandResponseDto
    @POST("api/v1/simulation/{robotId}/{scenario}") suspend fun scenario(@Path("robotId") robotId: String, @Path("scenario") scenario: String)
}

class RestRobotDataSource(private val api: RobotApi) : RobotRemoteDataSource {
    override suspend fun health() = api.health()
    override suspend fun robots() = api.robots()
    override suspend fun telemetry(robotId: String) = api.telemetry(robotId)
    override suspend fun startGuide(request: GuideMissionRequestDto) = command { api.startGuide(request) }
    override suspend fun pause(missionId: String) = command { api.pause(missionId) }
    override suspend fun resume(missionId: String) = command { api.resume(missionId) }
    override suspend fun cancel(missionId: String) = command { api.cancel(missionId) }
    override suspend fun returnSafe(robotId: String) = command { api.returnSafe(robotId) }
    override suspend fun stop(robotId: String) = command { api.stop(robotId) }
    override suspend fun ackAlert(alertId: String) = command { api.ackAlert(alertId) }
    override suspend fun scenario(robotId: String, scenario: String) = api.scenario(robotId, scenario)

    /** Un HTTP 4xx/5xx significa que el backend respondió y rechazó la orden. */
    private suspend fun command(block: suspend () -> CommandResponseDto): CommandResponseDto = try {
        block()
    } catch (e: HttpException) {
        throw RemoteRejectedException("HTTP ${e.code()}: ${e.response()?.errorBody()?.string()?.take(120) ?: e.message()}")
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        fun create(baseUrl: String, timeoutSeconds: Int): RestRobotDataSource {
            val client = OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RestRobotDataSource(retrofit.create(RobotApi::class.java))
        }
    }
}

/**
 * Elige la fuente remota según la configuración guardada en DataStore.
 * Retrofit se reconstruye sólo cuando cambia la URL o el timeout (sección 11 del plan).
 */
class RemoteDataSourceProvider(
    private val simulator: RobotRemoteDataSource,
    private val restFactory: (String, Int) -> RobotRemoteDataSource = RestRobotDataSource::create,
) {
    private var cachedKey: Pair<String, Int>? = null
    private var cachedRest: RobotRemoteDataSource? = null

    @Synchronized
    fun get(config: BackendConfig): RobotRemoteDataSource {
        if (config.mode == BackendMode.SIMULATED) return simulator
        val key = config.baseUrl to config.timeoutSeconds
        if (key != cachedKey || cachedRest == null) {
            cachedRest = restFactory(config.baseUrl, config.timeoutSeconds)
            cachedKey = key
        }
        return cachedRest!!
    }
}
