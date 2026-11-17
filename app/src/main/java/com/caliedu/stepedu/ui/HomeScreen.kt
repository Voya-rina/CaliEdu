package com.caliedu.stepedu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caliedu.stepedu.UiState

@Composable
fun HomeScreen(
    state: UiState,
    onInput: (String) -> Unit,
    onStart: () -> Unit,
    onSample: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Bg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("逐步", fontSize = 15.sp, color = Ink.Sub, fontWeight = FontWeight.Medium)
            TextButton(onClick = onSettings) {
                Text("模型", fontSize = 14.sp, color = Ink.Sub)
            }
        }

        Spacer(Modifier.height(34.dp))
        Text(
            "把一道题\n拆成几步。",
            fontSize = 30.sp,
            lineHeight = 43.sp,
            color = Ink.Text,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "粘题目，或者整份 .py。\n它会先出骨架，再一步步写到你能动手。",
            fontSize = 15.sp,
            lineHeight = 24.sp,
            color = Ink.Sub,
        )

        Spacer(Modifier.height(30.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(Ink.Card, RoundedCornerShape(16.dp))
                .border(1.dp, Ink.Line, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            if (state.input.isEmpty()) {
                Text(
                    "闯关 8 红色警戒：用 OpenCV 圈出画面里的红色物体……",
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    color = Ink.Sub,
                )
            }
            BasicTextField(
                value = state.input,
                onValueChange = onInput,
                textStyle = TextStyle(fontSize = 15.sp, lineHeight = 24.sp, color = Ink.Text),
                cursorBrush = SolidColor(Ink.Accent),
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink.Accent, contentColor = Ink.Card),
                modifier = Modifier.height(48.dp),
            ) {
                Text("开始拆解", fontSize = 15.sp)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSample) {
                Text("看示例", fontSize = 15.sp, color = Ink.Sub)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
