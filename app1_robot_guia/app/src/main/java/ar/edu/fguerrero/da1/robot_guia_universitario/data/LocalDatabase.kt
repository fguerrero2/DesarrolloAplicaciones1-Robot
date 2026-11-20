package ar.edu.fguerrero.da1.robot_guia_universitario.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * Fuente de datos local (Room) = fuente de verdad de la app (offline-first).
 * Tablas: robots, telemetry (último estado conocido), alerts, missions, mission_events, commands.
 * Los enums se guardan como String (name) para no necesitar TypeConverters.
 */

@Entity(tableName = "robots")
data class RobotEntity(@PrimaryKey val id: String, val name: String, val location: String)

@Entity(tableName = "telemetry")
data class TelemetryEntity(
    @PrimaryKey val robotId: String,
    val connection: String,
    val battery: Int,
    val operationalState: String,
    val missionId: String?,
    val missionProgress: Int,
    val x: Double, val y: Double, val yaw: Double,
    val vx: Double, val vy: Double, val omega: Double,
    val destinationId: String?,
    val destinationName: String?,
    val remainingDistance: Double,
    val companions: Int,
    val updatedAt: Long,
)

@Entity(tableName = "alerts", indices = [Index("robotId"), Index("missionId")])
data class AlertEntity(
    @PrimaryKey val id: String,
    val robotId: String,
    val missionId: String?,
    val type: String,
    val severity: String,
    val message: String,
    val timestamp: Long,
    val acknowledged: Boolean,
)

@Entity(tableName = "missions", indices = [Index("robotId")])
data class MissionEntity(
    @PrimaryKey val id: String,
    val robotId: String,
    val destinationId: String,
    val destinationName: String,
    val companions: Int,
    val startedAt: Long,
    val endedAt: Long?,
    val finalState: String,
)

@Entity(tableName = "mission_events", indices = [Index("missionId")])
data class MissionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val missionId: String,
    val timestamp: Long,
    val message: String,
)

@Entity(tableName = "commands", indices = [Index("robotId")])
data class CommandEntity(
    @PrimaryKey val id: String,
    val robotId: String,
    val missionId: String?,
    val type: String,
    val status: String,
    val message: String,
    val sentAt: Long,
    val respondedAt: Long?,
)

data class MissionWithAlertCount(@Embedded val mission: MissionEntity, val alertCount: Int)

@Dao
interface RobotDao {
    // Robots
    @Query("SELECT * FROM robots ORDER BY id") fun observeRobots(): Flow<List<RobotEntity>>
    @Upsert suspend fun upsertRobots(robots: List<RobotEntity>)

    // Telemetría
    @Query("SELECT * FROM telemetry WHERE robotId = :robotId") fun observeTelemetry(robotId: String): Flow<TelemetryEntity?>
    @Query("SELECT * FROM telemetry WHERE robotId = :robotId") suspend fun getTelemetry(robotId: String): TelemetryEntity?
    @Upsert suspend fun upsertTelemetry(telemetry: TelemetryEntity)
    @Query("UPDATE telemetry SET connection = :connection WHERE robotId = :robotId")
    suspend fun updateConnection(robotId: String, connection: String)

    // Alertas
    @Query("SELECT * FROM alerts WHERE robotId = :robotId ORDER BY acknowledged ASC, timestamp DESC")
    fun observeAlerts(robotId: String): Flow<List<AlertEntity>>
    @Query("SELECT * FROM alerts WHERE id = :id") suspend fun getAlert(id: String): AlertEntity?
    @Upsert suspend fun upsertAlert(alert: AlertEntity)
    @Query("UPDATE alerts SET acknowledged = 1 WHERE id = :id") suspend fun acknowledgeAlert(id: String)

    // Misiones
    @Query("SELECT * FROM missions WHERE robotId = :robotId AND finalState = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveMission(robotId: String): Flow<MissionEntity?>
    @Query("SELECT * FROM missions WHERE robotId = :robotId AND finalState = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveMission(robotId: String): MissionEntity?
    @Query("SELECT m.*, (SELECT COUNT(*) FROM alerts a WHERE a.missionId = m.id) AS alertCount FROM missions m ORDER BY m.startedAt DESC")
    fun observeHistory(): Flow<List<MissionWithAlertCount>>
    @Insert suspend fun insertMission(mission: MissionEntity)
    @Query("UPDATE missions SET finalState = :finalState, endedAt = :endedAt WHERE id = :id")
    suspend fun finishMission(id: String, finalState: String, endedAt: Long)

    // Eventos de misión
    @Insert suspend fun insertEvent(event: MissionEventEntity)
    @Query("SELECT * FROM mission_events WHERE missionId = :missionId ORDER BY timestamp ASC, id ASC")
    fun observeEvents(missionId: String): Flow<List<MissionEventEntity>>

    // Comandos
    @Upsert suspend fun upsertCommand(command: CommandEntity)
    @Query("UPDATE commands SET status = :status, message = :message, respondedAt = :respondedAt, missionId = :missionId WHERE id = :id")
    suspend fun updateCommand(id: String, status: String, message: String, respondedAt: Long, missionId: String?)
    @Query("SELECT * FROM commands WHERE robotId = :robotId ORDER BY sentAt DESC LIMIT 50")
    fun observeCommands(robotId: String): Flow<List<CommandEntity>>
}

@Database(
    entities = [RobotEntity::class, TelemetryEntity::class, AlertEntity::class, MissionEntity::class, MissionEventEntity::class, CommandEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RobotDatabase : RoomDatabase() {
    abstract fun dao(): RobotDao

    companion object {
        fun build(context: Context): RobotDatabase =
            Room.databaseBuilder(context, RobotDatabase::class.java, "robot_guia.db").build()

        fun inMemory(context: Context): RobotDatabase =
            Room.inMemoryDatabaseBuilder(context, RobotDatabase::class.java).allowMainThreadQueries().build()
    }
}
