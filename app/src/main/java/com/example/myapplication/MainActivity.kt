package com.example.myapplication

import android.R
import android.os.Bundle
import android.view.KeyEvent
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

@Serializable
data class ScheduleEntry(
    val time: String,
    val number: String,
    val state: String
)

class MainActivity : ComponentActivity() {

    private var adultCount by mutableStateOf(0)
    private var childCount by mutableStateOf(0)
    private var scheduleList by mutableStateOf<List<ScheduleEntry>>(emptyList())

    private var lastVolumeUpPressTime = 0L
    private var lastVolumeDownPressTime = 0L
    private val VOLUME_BUTTON_DEBOUNCE_TIME = 200L // когда 500 то если быстро нажимать и отпускать нажатия не проходят, поэтому 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Загружаем расписание из JSON
        loadSchedule()

        setContent {
            CounterScreen(adultCount, childCount, scheduleList) {
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val currentTime = System.currentTimeMillis()

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (currentTime - lastVolumeUpPressTime >= VOLUME_BUTTON_DEBOUNCE_TIME) {
                adultCount++
                lastVolumeUpPressTime = currentTime
            }
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (currentTime - lastVolumeDownPressTime >= VOLUME_BUTTON_DEBOUNCE_TIME) {
                childCount++
                lastVolumeDownPressTime = currentTime
            }
            return true
        }

        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun CounterScreen(adult: Int, child: Int, scheduleList: List<ScheduleEntry>, onReset: () -> Unit) {

    var currentTime by remember { mutableStateOf("") }
    var currentScheduleEntry by remember { mutableStateOf<ScheduleEntry?>(null) }
    var nextScheduleEntry by remember { mutableStateOf<ScheduleEntry?>(null) }
    var timeUntilNext by remember { mutableStateOf("") }
    var showResetConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(scheduleList) {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

        while (true) {
            val now = Date()
            currentTime = formatter.format(now)

            val currentTimeString = timeFormatter.format(now)

            // Находим текущую запись в расписании
            currentScheduleEntry = findCurrentEntry(scheduleList, currentTimeString)

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
                    text = "Наст.: ${nextScheduleEntry!!.state}, Через: $timeUntilNext хв (  )",
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

// Находит текущую запись в расписании по времени
fun findCurrentEntry(schedule: List<ScheduleEntry>, currentTime: String): ScheduleEntry? {
    return schedule.lastOrNull { it.time <= currentTime }
}

// Находит следующую запись в расписании
fun findNextEntry(schedule: List<ScheduleEntry>, currentTime: String): ScheduleEntry? {
    return schedule.firstOrNull { it.time > currentTime }
}

// Вычисляет время между двумя временными точками
fun calculateTimeDifference(from: String, to: String): String {
    return try {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val fromDate = formatter.parse(from) ?: return "00:00:00"
        val toDate = formatter.parse(to) ?: return "00:00:00"

        var diffMillis = toDate.time - fromDate.time

        // Если результат отрицательный, значит время перейдёт на следующий день
        if (diffMillis < 0) {
            diffMillis += 24 * 60 * 60 * 1000
        }

        val seconds = (diffMillis / 1000) % 60
        val minutes = (diffMillis / (1000 * 60)) % 60
        val hours = diffMillis / (1000 * 60 * 60)

        //String.format("%02d:%02d:%02d", hours, minutes, seconds)
        String.format("%02d:%02d", hours, minutes) // тут лучше без секунд наверное все же
    } catch (e: Exception) {
        "00:00:00"
    }
}