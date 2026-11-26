package com.example.smileaid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ───────────── Entity ─────────────
@Entity(tableName = "custom_cards")
data class CustomCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val emoji: String,
    val label: String
)

@Entity(tableName = "task_logs")
data class TaskLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskName: String,
    val time: Long
)

@Entity(tableName = "settings")
data class Setting(@PrimaryKey val key: String, val value: String)

// ───────────── DAO ─────────────
@Dao
interface SmileDao {
    @Query("SELECT * FROM custom_cards ORDER BY id") fun cards(): Flow<List<CustomCard>>
    @Insert suspend fun insertCard(card: CustomCard)
    @Delete suspend fun deleteCard(card: CustomCard)

    @Query("SELECT * FROM task_logs ORDER BY time DESC") fun logs(): Flow<List<TaskLog>>
    @Insert suspend fun insertLog(log: TaskLog)

    @Query("SELECT value FROM settings WHERE `key` = :key") suspend fun getSetting(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setSetting(s: Setting)
}

// ───────────── Database ─────────────
@Database(entities = [CustomCard::class, TaskLog::class, Setting::class], version = 1, exportSchema = false)
abstract class SmileDb : RoomDatabase() {
    abstract fun dao(): SmileDao

    companion object {
        @Volatile private var inst: SmileDb? = null
        fun get(ctx: Context): SmileDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, SmileDb::class.java, "smileaid.db")
                .build().also { inst = it }
        }
    }
}

// ───────────── UI 狀態（資料皆來自 Room，離線可用）─────────────
class AppState(private val dao: SmileDao, private val scope: CoroutineScope) {
    var emergencyPhone by mutableStateOf("")
        private set
    val customCards = mutableStateListOf<CustomCard>()
    val log = mutableStateListOf<String>()
    val badges: Int get() = log.size

    private val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN)

    init {
        scope.launch { emergencyPhone = dao.getSetting("phone") ?: "" }
        scope.launch { dao.cards().collect { customCards.clear(); customCards.addAll(it) } }
        scope.launch {
            dao.logs().collect { list ->
                log.clear(); log.addAll(list.map { "${fmt.format(Date(it.time))}　完成「${it.taskName}」" })
            }
        }
    }

    fun complete(task: String) { scope.launch { dao.insertLog(TaskLog(taskName = task, time = System.currentTimeMillis())) } }
    fun addCard(category: String, emoji: String, label: String) { scope.launch { dao.insertCard(CustomCard(category = category, emoji = emoji, label = label)) } }
    fun deleteCard(card: CustomCard) { scope.launch { dao.deleteCard(card) } }
    fun savePhone(p: String) { emergencyPhone = p; scope.launch { dao.setSetting(Setting("phone", p)) } }
}