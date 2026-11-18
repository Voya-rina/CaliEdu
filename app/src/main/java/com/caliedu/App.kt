package com.caliedu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caliedu.core.LessonDoc
import com.caliedu.ui.CaliTheme
import com.caliedu.ui.ExportKind
import com.caliedu.ui.Exporter
import com.caliedu.ui.HomeScreen
import com.caliedu.ui.LessonScreen
import com.caliedu.ui.LibraryScreen
import com.caliedu.ui.LocalSkin
import com.caliedu.ui.ModelScreen
import com.caliedu.ui.PlayScreen
import com.caliedu.ui.RecordsScreen
import com.caliedu.ui.RelayScreen
import com.caliedu.ui.SettingsHome
import com.caliedu.ui.StyleScreen
import com.caliedu.ui.UiStyle

@Composable
fun CaliEduApp(
    vm: AppViewModel = viewModel(),
    sharedText: String? = null,
    onSharedConsumed: () -> Unit = {},
) {
    val state = vm.state
    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            vm.onInput(sharedText)
            onSharedConsumed()
        }
    }
    val context = LocalContext.current
    val dark = when (state.themeMode) {
        com.caliedu.data.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        com.caliedu.data.ThemeMode.LIGHT -> false
        com.caliedu.data.ThemeMode.DARK -> true
    }
    val view = LocalView.current
    LaunchedEffect(dark) {
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
    CaliTheme(state.style, dark, state.textScale) {
        val skin = LocalSkin.current
        val docked = state.screen == Screen.HOME || state.screen == Screen.RECORDS || state.screen == Screen.SETTINGS
        val relay = state.relay

        BackHandler(enabled = state.screen != Screen.HOME || relay != null) {
            if (relay != null) vm.relayCancel() else vm.back()
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(skin.bg)
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            Box(Modifier.weight(1f)) {
                // 进二级页从右侧推进来，回退还出去；同级之间只淡入淡出。
                AnimatedContent(
                    targetState = state.screen,
                    transitionSpec = {
                        val back = depthOf(initialState) > depthOf(targetState)
                        val forward = depthOf(targetState) > depthOf(initialState)
                        when {
                            forward -> (slideInHorizontally(tween(280)) { it / 5 } + fadeIn(tween(200)))
                                .togetherWith(slideOutHorizontally(tween(280)) { -it / 10 } + fadeOut(tween(140)))

                            back -> (slideInHorizontally(tween(280)) { -it / 10 } + fadeIn(tween(200)))
                                .togetherWith(slideOutHorizontally(tween(280)) { it / 5 } + fadeOut(tween(140)))

                            else -> fadeIn(tween(180)).togetherWith(fadeOut(tween(120)))
                        }
                    },
                    label = "screen",
                ) { screen ->
                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            state = state,
                            onInput = vm::onInput,
                            onStart = vm::start,
                            onSample = vm::startSample,
                            onResume = vm::openRecord,
                            onMistakes = { vm.goRecords(1) },
                        )

                        Screen.RECORDS -> RecordsScreen(
                            records = state.records,
                            tab = state.recordsTab,
                            onTab = vm::setRecordsTab,
                            onOpen = vm::openRecord,
                            onOpenMistake = vm::openAt,
                            onDelete = vm::deleteRecord,
                            onStart = { vm.go(Screen.HOME) },
                            onClear = vm::clearRecords,
                        )

                        Screen.SETTINGS -> SettingsHome(
                            state = state,
                            onStyle = { vm.go(Screen.STYLE) },
                            onModel = { vm.go(Screen.MODEL) },
                            onLibrary = { vm.go(Screen.LIBRARY) },
                            onClear = vm::clearRecords,
                            onScale = vm::setTextScale,
                            onScaleCommit = vm::commitTextScale,
                        )

                        Screen.STYLE -> StyleScreen(
                            style = state.style,
                            dark = dark,
                            themeMode = state.themeMode,
                            onPick = vm::setStyle,
                            onTheme = vm::setTheme,
                            onBack = { vm.back() },
                        )

                        Screen.MODEL -> ModelScreen(
                            settings = state.settings,
                            onSave = vm::updateSettings,
                            onBack = { vm.back() },
                        )

                        Screen.LIBRARY -> LibraryScreen(
                            library = state.library,
                            busy = state.libraryBusy,
                            onImport = vm::importFolder,
                            onRescan = vm::rescanFolder,
                            onToast = vm::toast,
                            onBack = { vm.back() },
                        )

                        Screen.LESSON -> LessonScreen(
                            state = state,
                            onBack = { vm.back() },
                            onSubmit = vm::submit,
                            onDraft = vm::onDraft,
                            onHint = vm::askHint,
                            onNext = vm::next,
                            onToggleReading = vm::toggleReading,
                            onRetry = { vm.loadStep(state.progress.idx) },
                            onJump = vm::loadStep,
                            onAskOpen = vm::openAsk,
                            onAskClose = vm::closeAsk,
                            onAskInput = vm::onAskInput,
                            onAskSend = { vm.ask(state.askInput) },
                            onGenerate = vm::ensureStep,
                            onFocusStep = { index ->
                                vm.toggleReading()
                                vm.loadStep(index)
                            },
                            onExport = { kind ->
                                when (kind) {
                                    ExportKind.PLAY -> vm.openPlay()
                                    ExportKind.COPY -> {
                                        val plan = state.plan
                                        if (plan != null) {
                                            copyToClipboard(context, LessonDoc.markdown(plan, state.contents))
                                            vm.toast("全文已复制")
                                        }
                                    }

                                    ExportKind.MARKDOWN -> vm.exportMarkdown()?.let {
                                        runCatching { Exporter.share(context, it, "text/markdown") }
                                            .onFailure { e -> vm.toast("导出失败：${e.message}") }
                                    }

                                    ExportKind.PDF -> vm.exportHtml()?.let {
                                        runCatching { Exporter.print(context, it) }
                                            .onFailure { e -> vm.toast("打印失败：${e.message}") }
                                    }
                                }
                            },
                        )

                        Screen.PLAY -> PlayScreen(
                            initial = state.playCode,
                            title = state.plan?.steps?.getOrNull(state.progress.idx)?.title.orEmpty(),
                            onBack = { vm.back() },
                        )
                    }
                }

                if (relay != null) {
                    RelayScreen(
                        pending = relay,
                        onSubmit = vm::relaySubmit,
                        onCancel = vm::relayCancel,
                    )
                }
            }

            if (docked) {
                Dock(current = state.screen, onSelect = vm::go)
            }
        }

        if (state.loading != null && relay == null) {
            LoadingOverlay(state.loading, state.seconds, vm::cancel)
        }
        state.error?.let { message -> ErrorBanner(message, vm::dismissError) }
        state.toast?.let { message -> ToastBanner(message, vm::clearToast) }
    }
}

/** 页面层级：同级淡入淡出，跨级左右滑。 */
private fun depthOf(screen: Screen): Int = when (screen) {
    Screen.HOME, Screen.RECORDS, Screen.SETTINGS -> 0
    Screen.STYLE, Screen.MODEL, Screen.LIBRARY -> 1
    Screen.LESSON, Screen.PLAY -> 2
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("CaliEdu", text))
}

@Composable
private fun Dock(current: Screen, onSelect: (Screen) -> Unit) {
    val skin = LocalSkin.current
    val items: List<Pair<Screen, Pair<ImageVector, String>>> = listOf(
        Screen.HOME to (Icons.Rounded.Home to "首页"),
        Screen.RECORDS to (Icons.Rounded.AutoStories to "记录"),
        Screen.SETTINGS to (Icons.Rounded.Settings to "设置"),
    )
    Column {
        if (skin.style == UiStyle.PRINT) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(skin.line)
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { entry ->
                val screen = entry.first
                val icon = entry.second.first
                val label = entry.second.second
                val selected = screen == current
                val lift by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(180),
                    label = "dock",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(screen) }
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when {
                        skin.style == UiStyle.MATERIAL && selected -> Box(
                            Modifier
                                .background(skin.accentSoft, CircleShape)
                                .padding(horizontal = 17.dp, vertical = 3.dp)
                        ) {
                            Icon(icon, contentDescription = null, tint = skin.accentDeep, modifier = Modifier.size(20.dp))
                        }

                        skin.style == UiStyle.MATERIAL -> Box(Modifier.padding(vertical = 3.dp)) {
                            Icon(icon, contentDescription = null, tint = skin.sub, modifier = Modifier.size(20.dp))
                        }

                        else -> Icon(
                            icon,
                            contentDescription = null,
                            tint = if (selected) skin.accent else skin.sub,
                            modifier = Modifier.size((20 + lift * 2).dp),
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(label, fontSize = 10.5.sp, color = if (selected) skin.accent else skin.sub)
                }
            }
        }
    }
}

@Composable
private fun LoadingOverlay(label: String, seconds: Int, onCancel: () -> Unit) {
    val skin = LocalSkin.current
    Box(
        Modifier
            .fillMaxSize()
            .background(skin.bg)
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = (skin.bodySize.value * 1.05f).sp, fontWeight = FontWeight.Medium, color = skin.text)
            Text(
                text = if (seconds < 12) "第 $seconds 秒" else "第 $seconds 秒 · 本地模型慢是常态",
                fontSize = skin.smallSize,
                color = skin.sub,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 6.dp)) {
                Text("取消", fontSize = skin.smallSize, color = skin.accent)
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    val skin = LocalSkin.current
    Box(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(skin.wrongSoft, RoundedCornerShape(skin.radiusCard))
                .border(1.dp, skin.wrong.copy(alpha = 0.25f), RoundedCornerShape(skin.radiusCard))
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(message, fontSize = skin.smallSize, lineHeight = 21.sp, color = skin.wrong)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("知道了", fontSize = 12.5.sp, color = skin.wrong)
            }
        }
    }
}

@Composable
private fun ToastBanner(message: String, onDone: () -> Unit) {
    val skin = LocalSkin.current
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(1500)
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            message,
            fontSize = 12.5.sp,
            color = skin.onAccent,
            modifier = Modifier
                .background(skin.text, RoundedCornerShape(100.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}
