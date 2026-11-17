package com.caliedu.stepedu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caliedu.stepedu.ui.HomeScreen
import com.caliedu.stepedu.ui.Ink
import com.caliedu.stepedu.ui.LessonScreen
import com.caliedu.stepedu.ui.SettingsSheet

@Composable
fun StepEduApp(vm: AppViewModel = viewModel()) {
    val state = vm.state

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Bg)
    ) {
        when (state.screen) {
            Screen.HOME -> HomeScreen(
                state = state,
                onInput = vm::onInput,
                onStart = vm::start,
                onSample = vm::startSample,
                onSettings = { vm.openSettings(true) },
            )

            Screen.LESSON -> LessonScreen(
                state = state,
                onBack = vm::back,
                onSubmit = vm::submit,
                onDraft = vm::onDraft,
                onHint = vm::askHint,
                onNext = vm::next,
            )
        }

        state.loading?.let { label ->
            LoadingOverlay(label, state.seconds, vm::cancel)
        }

        state.error?.let { message ->
            ErrorBanner(message, vm::dismissError)
        }

        if (state.settingsOpen) {
            SettingsSheet(
                settings = state.settings,
                onSave = vm::saveSettings,
                onDismiss = { vm.openSettings(false) },
            )
        }
    }
}

@Composable
private fun LoadingOverlay(label: String, seconds: Int, onCancel: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Bg)
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink.Text)
            Box(Modifier.padding(top = 10.dp)) {
                Text(
                    if (seconds < 12) "第 $seconds 秒" else "第 $seconds 秒 · 本地模型慢是常态",
                    fontSize = 13.sp,
                    color = Ink.Sub,
                )
            }
            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
                Text("取消", fontSize = 14.sp, color = Ink.Sub)
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Ink.WrongSoft, RoundedCornerShape(14.dp))
                .border(1.dp, Ink.Wrong.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(message, fontSize = 14.sp, lineHeight = 22.sp, color = Ink.Wrong)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("知道了", fontSize = 13.sp, color = Ink.Wrong)
            }
        }
    }
}
