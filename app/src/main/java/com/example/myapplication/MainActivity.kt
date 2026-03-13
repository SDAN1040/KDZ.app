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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class MainActivity : ComponentActivity() {

    private var adultCount by mutableStateOf(0)
    private var childCount by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            CounterScreen(adultCount, childCount) {
                adultCount = 0
                childCount = 0
            }
        }

    }



    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            adultCount++
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            childCount++
            return true
        }

        return super.onKeyDown(keyCode, event)
    }



}


@Composable
fun CounterScreen(adult: Int, child: Int, onReset: () -> Unit) {

    var currentTime by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {

        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        while (true) {
            currentTime = formatter.format(Date())
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
            text = currentTime,
            fontSize = 28.sp
        )

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "Взрослые: $adult",
            fontSize = 36.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Детские: $child",
            fontSize = 36.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Button(onClick = onReset) {
            Text("Сброс", fontSize = 24.sp)
        }


    }
}