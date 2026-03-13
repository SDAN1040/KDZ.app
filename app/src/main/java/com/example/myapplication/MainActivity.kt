package com.example.myapplication

import android.os.Bundle
import android.view.KeyEvent
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Build

@Serializable
data class ScheduleEntry(
    val time: String,
    val number: String,
    val state: String
)

class MainActivity : ComponentActivity() {

    private var adultCount by mutableIntStateOf(0)
    private var childCount by mutableIntStateOf(0)
    private var scheduleList by mutableStateOf<List<ScheduleEntry>>(emptyList())

    private var lastVolumeUpPressTime = 0L
    private var lastVolumeDownPressTime = 0L
    private val volumeButtonDebounceTime = 200L

    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Инициализируем вибратор
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Загружаем расписание из JSON
        loadSchedule()

        setContent {
            CounterScreen(adultCount, childCount, scheduleList, { vibrate(duration = 100, count = 2) }) {
                adultCount = 0
                childCount = 0
            }
        }
    }

    private fun loadSchedule() {
        try {
            val inputStream = assets.open("schedule.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            scheduleList = Json.decodeFromString(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun vibrate(duration: Long = 100, count: Int = 1) {
        if (count <= 0 || vibrator == null) return

        Thread {
            repeat(count) { index ->
                if (index > 0) {
                    Thread.sleep(100) // пауза между вибрациями
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(duration)
                }
            }
        }.start()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val currentTime = System.currentTimeMillis()

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (currentTime - lastVolumeUpPressTime >= volumeButtonDebounceTime) {
                adultCount++
                lastVolumeUpPressTime = currentTime
                vibrate(duration = 500, count = 1)
            }
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (currentTime - lastVolumeDownPressTime >= volumeButtonDebounceTime) {
                childCount++
                lastVolumeDownPressTime = currentTime
                vibrate(duration = 500, count = 1)
            }
            return true
        }

        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun CounterScreen(
    adult: Int,
    child: Int,
    scheduleList: List<ScheduleEntry>,
    onScheduleChange: () -> Unit,
    onReset: () -> Unit
) {

    var currentTime by remember { mutableStateOf("") }
    var currentScheduleEntry by remember { mutableStateOf<ScheduleEntry?>(null) }
    var nextScheduleEntry by remember { mutableStateOf<ScheduleEntry?>(null) }
    var timeUntilNext by remember { mutableStateOf("") }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var previousScheduleEntry by remember { mutableStateOf<ScheduleEntry?>(null) }

    LaunchedEffect(scheduleList) {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

        while (true) {
            val now = Date()
            currentTime = formatter.format(now)

            val currentTimeString = timeFormatter.format(now)

            // Находим текущую запись в расписании
            val newScheduleEntry = findCurrentEntry(scheduleList, currentTimeString)

            // Проверяем, изменилась ли текущая запись
            if (newScheduleEntry != previousScheduleEntry && newScheduleEntry != null) {
                onScheduleChange()
                previousScheduleEntry = newScheduleEntry
            }

            currentScheduleEntry = newScheduleEntry

            // Находим следующую запись
            val nextEntry = findNextEntry(scheduleList, currentTimeString)
            nextScheduleEntry = nextEntry

            // Вычисляем время до следующей записи
            if (nextEntry != null) {
                timeUntilNext = calculateTimeDifference(currentTimeString, nextEntry.time)
            }

            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Київська Дитяча Залізниця\nМіжсезонна зміна",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(40.dp))
        // Номер рейса
        if (currentScheduleEntry != null) {
            Text(
                text = "Рейс: ${currentScheduleEntry!!.number}",
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.height(15.dp))
        }

        // Время
        Text(
            text = currentTime,
            fontSize = 28.sp
        )

        // Текущее состояние и следующее
        if (currentScheduleEntry != null) {
            Spacer(modifier = Modifier.height(15.dp))
            Text(
                text = "Зараз: ${currentScheduleEntry!!.state}",
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (nextScheduleEntry != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "\nНаст.: ${nextScheduleEntry!!.state}\nЧерез: $timeUntilNext хв (${nextScheduleEntry!!.time})",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(50.dp))

        Text(
            text = "Дорослих: $adult",
            fontSize = 36.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Дитячих: $child",
            fontSize = 36.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = {
                showResetConfirmation = true
            },
            modifier = Modifier.size(width = 150.dp, height = 60.dp)
        ) {
            Text("Скинути", fontSize = 24.sp)
        }

        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = "Додавання пасажирів кнопками регулювання гучності,\n+ дорослий, - дитячий",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = "Застосунок створено в наукових цілях.\nАТ \"УКРЗАЛІЗНИЦЯ\" не має відношення до нього.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Підтвердження") },
            text = { Text("Ви дійсно хочете скинути лічильники?") },
            confirmButton = {
                Button(
                    onClick = {
                        onReset()
                        showResetConfirmation = false
                    }
                ) {
                    Text("Так")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showResetConfirmation = false }
                ) {
                    Text("Скасувати")
                }
            }
        )
    }
}

fun findCurrentEntry(schedule: List<ScheduleEntry>, currentTime: String): ScheduleEntry? {
    return schedule.lastOrNull { it.time <= currentTime }
}

fun findNextEntry(schedule: List<ScheduleEntry>, currentTime: String): ScheduleEntry? {
    return schedule.firstOrNull { it.time > currentTime }
}

fun calculateTimeDifference(from: String, to: String): String {
    return try {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val fromDate = formatter.parse(from) ?: return "00:00:00"
        val toDate = formatter.parse(to) ?: return "00:00:00"

        var diffMillis = toDate.time - fromDate.time

        if (diffMillis < 0) {
            diffMillis += 24 * 60 * 60 * 1000
        }

        val minutes = (diffMillis / (1000 * 60)) % 60
        val hours = diffMillis / (1000 * 60 * 60)

        val result = String.format(Locale.getDefault(), "%02d:%02d", hours, minutes)

        if (hours == 0L) {
            if (minutes < 10) {
                String.format(Locale.getDefault(), "%01d", minutes)
            } else {
                String.format(Locale.getDefault(), "%02d", minutes)
            }
        } else {
            result
        }

    } catch (e: Exception) {
        "00:00:00"
    }
}