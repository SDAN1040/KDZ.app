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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

@Serializable
data class ScheduleEntry(
    val time: String,
    val number: String,
    val state: String
)

class MainActivity : ComponentActivity() {

    private var adultCount by mutableIntStateOf(0)
    private var childCount by mutableIntStateOf(0)
    private var allSchedules by mutableStateOf<Map<String, List<ScheduleEntry>>>(emptyMap())
    private var selectedScheduleName by mutableStateOf("Міжсезонна зміна")

    private var lastVolumeUpPressTime = 0L
    private var lastVolumeDownPressTime = 0L
    private val volumeButtonDebounceTime = 100L

    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        loadSchedules()

        setContent {
            CounterScreen(
                adultCount,
                childCount,
                allSchedules,
                selectedScheduleName,
                { selectedScheduleName = it },
                { vibrate(duration = 100, count = 2) }
            ) {
                adultCount = 0
                childCount = 0
            }
        }
    }

    private fun loadSchedules() {
        try {
            val inputStream = assets.open("schedule.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            allSchedules = Json.decodeFromString(jsonString)

            // Если расписание по умолчанию не существует, устанавливаем первое доступное
            if (!allSchedules.containsKey(selectedScheduleName) && allSchedules.isNotEmpty()) {
                selectedScheduleName = allSchedules.keys.first()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun vibrate(duration: Long = 100, count: Int = 1) {
        if (count <= 0 || vibrator == null) return

        Thread {
            repeat(count) { index ->
                if (index > 0) {
                    Thread.sleep(100)
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
    allSchedules: Map<String, List<ScheduleEntry>>,
    selectedScheduleName: String,
    onScheduleSelected: (String) -> Unit,
    onScheduleChange: () -> Unit,
    onReset: () -> Unit
) {

    var currentTime by remember { mutableStateOf("") }
    var currentScheduleEntry by remember { mutableStateOf<ScheduleEntry?>(null) }
    var nextScheduleEntry by remember { mutableStateOf<ScheduleEntry?>(null) }
    var timeUntilNext by remember { mutableStateOf("") }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var previousScheduleEntry by remember { mutableStateOf<ScheduleEntry?>(null) }
    var expandedScheduleMenu by remember { mutableStateOf(false) }

    val currentScheduleList = allSchedules[selectedScheduleName] ?: emptyList()

    LaunchedEffect(selectedScheduleName, currentScheduleList) {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

        while (true) {
            val now = Date()
            currentTime = formatter.format(now)

            val currentTimeString = timeFormatter.format(now)

            val newScheduleEntry = findCurrentEntry(currentScheduleList, currentTimeString)

            if (newScheduleEntry != previousScheduleEntry && newScheduleEntry != null) {
                onScheduleChange()
                previousScheduleEntry = newScheduleEntry
            }

            currentScheduleEntry = newScheduleEntry

            val nextEntry = findNextEntry(currentScheduleList, currentTimeString)
            nextScheduleEntry = nextEntry

            if (nextEntry != null) {
                timeUntilNext = calculateTimeDifference(currentTimeString, nextEntry.time)
            }

            delay(1000)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Київська Дитяча Залізниця\n$selectedScheduleName",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            // Выпадающее меню для выбора расписания
            Box {
                Button(
                    onClick = { expandedScheduleMenu = !expandedScheduleMenu },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("Обрати розклад руху", fontSize = 14.sp)
                }

                DropdownMenu(
                    expanded = expandedScheduleMenu,
                    onDismissRequest = { expandedScheduleMenu = false },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    allSchedules.keys.forEach { scheduleName ->
                        DropdownMenuItem(
                            text = { Text(scheduleName) },
                            onClick = {
                                onScheduleSelected(scheduleName)
                                previousScheduleEntry = null
                                expandedScheduleMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(35.dp))



            if (currentScheduleEntry != null) {
                Text(
                    text = "Рейс: ${currentScheduleEntry!!.number}",
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.height(15.dp))
            }

            Text(
                text = currentTime,
                fontSize = 28.sp
            )

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

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = "Дорослих: $adult",
                fontSize = 36.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Дитячих: $child",
                fontSize = 36.sp
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Всього: ${child+adult}",
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
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Застосунок створено в наукових цілях виключно для працівників київської дитячої залізниці.\nАТ \"УКРЗАЛІЗНИЦЯ\" не має відношення до нього.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            val annotatedString = buildAnnotatedString {
                append("Сайт: ")
                pushStringAnnotation(tag = "URL", annotation = "https://sites.google.com/view/kdz-app")
                pushStyle(SpanStyle(color = Color.Gray, textDecoration = TextDecoration.Underline))
                append("Перейти на сайт")
                pop()
                pop()
            }

            val context = LocalContext.current

            ClickableText(
                text = annotatedString,
                onClick = { offset ->
                    annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                            context.startActivity(intent)
                        }
                },
                modifier = Modifier.padding(10.dp)
            )
        }


        // Ссылка внизу по центру
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

        }
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