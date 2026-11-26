package com.example.smileaid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// ───────────── 資料 ─────────────
data class AacCard(val emoji: String, val label: String, val speech: String, val color: Color)
data class Task(val title: String, val steps: List<Pair<String, String>>) // emoji + 步驟文字

val Blue = Color(0xFFD6E8FA); val Yellow = Color(0xFFFFE9B0); val Green = Color(0xFFCDEBCB)
val Red = Color(0xFFE53935); val DoneGreen = Color(0xFF22A04B)

val categories: MutableMap<String, SnapshotStateList<AacCard>> = mutableMapOf(
    "打招呼" to mutableStateListOf(
        AacCard("👋", "你好", "你好，很高興認識你！", Color(0xFFFFE0DC)),
        AacCard("🙏", "謝謝", "謝謝你", Color(0xFFFFF1C9)),
        AacCard("😊", "再見", "再見，明天見！", Color(0xFFDDF0FF)),
        AacCard("🤝", "請多指教", "請多指教", Color(0xFFE6F7E4))),
    "吃飯" to mutableStateListOf(
        AacCard("🍚", "我肚子餓了", "我肚子餓了，想吃飯", Color(0xFFFFF1C9)),
        AacCard("🥤", "我想喝水", "我想喝水", Color(0xFFDDF0FF)),
        AacCard("🍎", "我想吃點心", "我想吃點心", Color(0xFFFFE0DC)),
        AacCard("✅", "我吃飽了", "我吃飽了", Color(0xFFE6F7E4))),
    "心情" to mutableStateListOf(
        AacCard("😄", "我很開心", "我很開心", Color(0xFFFFF1C9)),
        AacCard("😢", "我很難過", "我很難過", Color(0xFFDDF0FF)),
        AacCard("😠", "我生氣了", "我生氣了", Color(0xFFFFE0DC)),
        AacCard("🤗", "我想抱抱", "我想要抱抱", Color(0xFFF8DDF3))),
    "身體" to mutableStateListOf(
        AacCard("🤒", "我不舒服", "我不舒服，想看醫生", Color(0xFFF8DDF3)),
        AacCard("🚻", "我想上廁所", "我想上廁所", Color(0xFFDDF0FF)),
        AacCard("🤕", "我頭痛", "我頭痛", Color(0xFFFFE0DC)),
        AacCard("😴", "我想睡覺", "我想睡覺", Color(0xFFE6F7E4)))
)

val tasks = listOf(
    Task("洗手練習", listOf(
        "🚰" to "先把雙手沖濕", "🧼" to "擠一點肥皂搓一搓",
        "🙌" to "搓洗手心、手背、指縫", "💦" to "沖乾淨並擦乾")),
    Task("餐前準備", listOf(
        "🧽" to "擦乾淨桌子", "🍽️" to "擺好餐具", "🪑" to "坐好等吃飯")),
    Task("禮貌打招呼", listOf(
        "👀" to "看著對方", "👋" to "揮揮手", "😊" to "說：你好！")),
    Task("物品歸位", listOf(
        "🎒" to "拿起物品", "📦" to "放回原來位置"))
)

val smileFaces = listOf("😀", "😆", "🤪", "😎", "🥳", "😜", "🤩")
val cheers = listOf("你好棒！", "太厲害了！", "加油加油！", "笑一個！", "做得好！")

enum class Screen { Home, Voice, Tasks, TaskRun, Smile, Caregiver }

// ───────────── Activity ─────────────
class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var pending: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent { MaterialTheme { SmileAidApp { speakText(it) } } }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Toast.makeText(this, "語音引擎初始化失敗，請確認手機已安裝文字轉語音引擎", Toast.LENGTH_LONG).show()
            return
        }
        var r = tts.setLanguage(Locale.TAIWAN)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            r = tts.setLanguage(Locale.CHINESE)
        }
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(this, "找不到中文語音，請安裝中文語音資料", Toast.LENGTH_LONG).show()
            try { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) } catch (_: Exception) {}
            return
        }
        tts.setSpeechRate(0.9f)
        ttsReady = true
        pending?.let { speakText(it) }; pending = null
    }

    private fun speakText(text: String) {
        if (!ttsReady) { pending = text; return }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "smile")
    }

    override fun onDestroy() { tts.stop(); tts.shutdown(); super.onDestroy() }
}

// ───────────── 導覽 ─────────────
@Composable
fun SmileAidApp(speak: (String) -> Unit) {
    val ctx = LocalContext.current
    val appScope = rememberCoroutineScope()
    val state = remember { AppState(SmileDb.get(ctx).dao(), appScope) }
    var screen by remember { mutableStateOf(Screen.Home) }
    var currentTask by remember { mutableStateOf(tasks[0]) }
    val back = { screen = Screen.Home }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF1F6FC)) {
        when (screen) {
            Screen.Home -> HomeScreen(speak, { screen = it })
            Screen.Voice -> VoiceScreen(speak, state, back)
            Screen.Tasks -> TaskListScreen(back) { currentTask = it; screen = Screen.TaskRun }
            Screen.TaskRun -> TaskRunScreen(currentTask, speak, state, { screen = Screen.Tasks })
            Screen.Smile -> SmileScreen(speak, state, back)
            Screen.Caregiver -> CaregiverScreen(state, back)
        }
    }
}

// ───────────── 首頁（3 大按鈕 + 求助 + 照護者長按 3 秒）─────────────
@Composable
fun HomeScreen(speak: (String) -> Unit, go: (Screen) -> Unit) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("微笑小幫手", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            // 長按 3 秒進入照護者模式
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFDDE3EA)).padding(10.dp, 6.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            val job = scope.launch { delay(3000); go(Screen.Caregiver) }
                            tryAwaitRelease(); job.cancel()
                        })
                    }
            ) { Text("照護者模式 (長按3秒)", fontSize = 12.sp, color = Color.DarkGray) }
        }
        Spacer(Modifier.height(16.dp))
        BigButton("💬  一鍵說心聲\n(AAC 視覺溝通)", Blue, Modifier.weight(1f)) { go(Screen.Voice) }
        Spacer(Modifier.height(12.dp))
        BigButton("📋  步驟小任務\n(生活自理引導)", Yellow, Modifier.weight(1f)) { go(Screen.Tasks) }
        Spacer(Modifier.height(12.dp))
        BigButton("😊  哈哈微笑鏡\n(情緒與回饋)", Green, Modifier.weight(1f)) { go(Screen.Smile) }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(Red)
                .clickable { speak("我需要幫忙，請過來幫我") },
            contentAlignment = Alignment.Center
        ) { Text("需要幫忙", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

@Composable
fun BigButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(color)
            .clickable(onClick = onClick).padding(20.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", fontSize = 40.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 16.dp, start = 4.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

// ───────────── 一鍵說心聲 ─────────────
@Composable
fun VoiceScreen(speak: (String) -> Unit, state: AppState, back: () -> Unit) {
    var cat by remember { mutableStateOf("打招呼") }
    var flash by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(flash) { if (flash != null) { delay(700); flash = null } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TopBar("一鍵說心聲", back)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories.keys.toList()) { c ->
                FilterChip(selected = c == cat, onClick = { cat = c },
                    label = { Text(c, fontSize = 20.sp, modifier = Modifier.padding(6.dp)) })
            }
        }
        Spacer(Modifier.height(12.dp))
        // 2×2 超大圖卡
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(categories[cat]!! + state.customCards.filter { it.category == cat }
                .map { AacCard(it.emoji, it.label, it.label, Color(0xFFEDE7F6)) }) { card ->
                val border by animateColorAsState(if (flash == card.label) Color(0xFFFFB300) else Color.Transparent, label = "b")
                Column(
                    Modifier.aspectRatio(0.9f).clip(RoundedCornerShape(24.dp)).background(card.color)
                        .then(Modifier.padding(0.dp))
                        .clickable { flash = card.label; speak(card.speech) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Surface(shape = RoundedCornerShape(20.dp), color = card.color, border = BorderStroke(6.dp, border)) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(card.emoji, fontSize = 64.sp)
                            Text(card.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

// ───────────── 步驟小任務 ─────────────
@Composable
fun TaskListScreen(back: () -> Unit, pick: (Task) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TopBar("步驟小任務", back)
        tasks.forEach { t -> BigButton("${t.steps.first().first}  ${t.title}", Yellow, Modifier) { pick(t) } }
    }
}

@Composable
fun TaskRunScreen(task: Task, speak: (String) -> Unit, state: AppState, back: () -> Unit) {
    var idx by remember(task) { mutableStateOf(0) }
    var finished by remember(task) { mutableStateOf(false) }
    val step = task.steps[idx]
    LaunchedEffect(idx, finished) { if (!finished) speak(step.second) }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar("步驟小任務 (${task.title})", back)
        if (finished) {
            Spacer(Modifier.weight(1f))
            Text("🏅", fontSize = 120.sp)
            Text("太棒了！獲得微笑勳章！", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            GreenButton("回到列表", back)
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("任務：${task.title}", fontSize = 16.sp); Text("步驟 ${idx + 1}/${task.steps.size}", fontSize = 16.sp)
        }
        LinearProgressIndicator(progress = { (idx + 1f) / task.steps.size }, Modifier.fillMaxWidth().height(10.dp).padding(vertical = 2.dp))
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White)
                .clickable { speak(step.second) }, // 點擊圖片可重播語音
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text(step.first, fontSize = 120.sp)
            Text(step.second, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        GreenButton("我完成了！") {
            if (idx + 1 < task.steps.size) idx++ else { state.complete(task.title); speak("太棒了，你完成了！"); finished = true }
        }
    }
}

@Composable
fun GreenButton(text: String, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().height(72.dp), shape = RoundedCornerShape(36.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DoneGreen)) { Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
}

// ───────────── 哈哈微笑鏡 ─────────────
@Composable
fun SmileScreen(speak: (String) -> Unit, state: AppState, back: () -> Unit) {
    var i by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar("哈哈微笑鏡", back)
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Green)
                .pointerInput(Unit) {
                    detectTapGestures { i = (i + 1) % smileFaces.size; speak(cheers.random()) }
                },
            contentAlignment = Alignment.Center
        ) { Text(smileFaces[i], fontSize = 200.sp) }
        Spacer(Modifier.height(12.dp))
        Text("點一下螢幕，換個搞笑表情！", fontSize = 18.sp)
        Text("本週微笑貼紙：${"⭐".repeat(state.badges.coerceAtMost(10))} (${state.badges})", fontSize = 20.sp)
    }
}

// ───────────── 照護者安心模式 ─────────────
@Composable
fun CaregiverScreen(state: AppState, back: () -> Unit) {
    val ctx = LocalContext.current
    var emoji by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("吃飯") }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TopBar("照護者安心模式", back)

        Text("自訂圖卡", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.keys.forEach { c -> FilterChip(cat == c, { cat = c }, { Text(c) }) }
        }
        OutlinedTextField(emoji, { emoji = it }, label = { Text("圖示 (emoji)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(label, { label = it }, label = { Text("要說的話") }, modifier = Modifier.fillMaxWidth())
        Button({
            if (label.isNotBlank()) {
                state.addCard(cat, emoji.ifBlank { "⭐" }, label)
                emoji = ""; label = ""
            }
        }) { Text("新增圖卡") }
        state.customCards.forEach { c ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${c.emoji} ${c.label}（${c.category}）")
                TextButton({ state.deleteCard(c) }) { Text("刪除") }
            }
        }

        HorizontalDivider()
        Text("緊急聯絡人", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        OutlinedTextField(state.emergencyPhone, { state.savePhone(it) }, label = { Text("電話號碼") }, modifier = Modifier.fillMaxWidth())
        Button({
            if (state.emergencyPhone.isNotBlank())
                ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${state.emergencyPhone}")))
        }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("📞 一鍵撥號") }

        HorizontalDivider()
        Text("任務完成紀錄（共 ${state.log.size} 次）", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        if (state.log.isEmpty()) Text("尚無紀錄", color = Color.Gray)
        state.log.forEach { Text("• $it") }
    }
}