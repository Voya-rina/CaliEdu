package com.voyarina.caliedu

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
import androidx.compose.material.icons.rounded.FolderOpen
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voyarina.caliedu.core.LessonDoc
import com.voyarina.caliedu.ui.CaliTheme
import com.voyarina.caliedu.ui.ExportKind
import com.voyarina.caliedu.ui.Exporter
import com.voyarina.caliedu.ui.HomeScreen
import com.voyarina.caliedu.ui.LessonScreen
import com.voyarina.caliedu.ui.LibraryScreen
import com.voyarina.caliedu.ui.LocalSkin
import com.voyarina.caliedu.ui.ModelScreen
import com.voyarina.caliedu.ui.PlayScreen
import com.voyarina.caliedu.ui.RecordsScreen
import com.voyarina.caliedu.ui.RelayScreen
import com.voyarina.caliedu.ui.ReviewScreen
import com.voyarina.caliedu.ui.SettingsHome
import com.voyarina.caliedu.ui.StyleScreen
import com.voyarina.caliedu.ui.UiStyle

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
        com.voyarina.caliedu.data.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        com.voyarina.caliedu.data.ThemeMode.LIGHT -> false
        com.voyarina.caliedu.data.ThemeMode.DARK -> true
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
        val docked = state.screen == Screen.HOME || state.screen == Screen.RECORDS ||
            state.screen == Screen.LIBRARY || state.screen == Screen.SETTINGS
        val relay = state.relay

        // 加载浮层盖着的时候，返回键先当作「取消」——否则会直接把 App 退掉，
        // 下次进来还留着上一次的「正在读题」。
        BackHandler(
            enabled = state.screen != Screen.HOME || relay != null || state.loading != null
        ) {
            when {
                relay != null -> vm.relayCancel()
                // 只有停在首页时才把返回当取消；课里按返回就是退出这节课。
                state.loading != null && state.screen == Screen.HOME -> vm.cancel()
                else -> vm.back()
            }
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
                            onReview = vm::startReview,
                            onFile = vm::importFile,
                            onResume = vm::openRecord,
                            onMistakes = { vm.goRecords(1) },
                        )

                        Screen.RECORDS -> RecordsScreen(
                            // 讲义归资料页：记录是做题的地方，混着几十节只读内容会让统计和筛选失真。
                            records = state.records.filterNot { it.isDoc },
                            tab = state.recordsTab,
                            filter = state.recordsFilter,
                            onTab = vm::setRecordsTab,
                            onFilter = vm::setRecordsFilter,
                            onOpen = vm::openRecord,
                            onOpenMistake = vm::openAt,
                            onDelete = vm::deleteRecord,
                            onDeleteMistake = vm::deleteMistake,
                            onClearMistakes = vm::clearMistakes,
                            onRecategorize = vm::setCategory,
                            onReview = vm::startReview,
                            onSample = vm::startSample,
                            onDocSample = vm::startDocSample,
                            onStart = { vm.go(Screen.HOME) },
                            onClear = vm::clearRecords,
                        )

                        Screen.SETTINGS -> SettingsHome(
                            state = state,
                            onStyle = { vm.go(Screen.STYLE) },
                            onModel = { vm.go(Screen.MODEL) },
                            onScale = vm::setTextScale,
                            onScaleCommit = vm::commitTextScale,
                            onReadingBar = vm::setReadingBar,
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
                            docs = state.records.filter { it.isDoc }.sortedByDescending { it.updatedAt },
                            busy = state.libraryBusy,
                            onImport = vm::importFolder,
                            onAddFiles = vm::addFiles,
                            onRescan = vm::rescanFolder,
                            onUse = { item, start, pages -> vm.useFile(item, start, pages) },
                            onDoc = vm::openAsDoc,
                            onQuiz = vm::quizFromDoc,
                            onOpenDoc = vm::openRecord,
                            onDeleteDoc = { vm.deleteRecord(it.id) },
                            onRemove = vm::removeFile,
                            onToast = vm::toast,
                        )

                        Screen.LESSON -> LessonScreen(
                            state = state,
                            onBack = { vm.back() },
                            onSubmit = vm::submit,
                            onDraft = vm::onDraft,
                            onHint = vm::askHint,
                            onNext = vm::next,
                            onPrev = vm::prev,
                            onSimilar = vm::practiseSimilar,
                            onReview = vm::reviewSteps,
                            onEndReview = vm::endReview,
                            onToggleReading = vm::toggleReading,
                            onRetry = { vm.loadStep(state.progress.idx) },
                            onJump = vm::loadStep,
                            onAskOpen = vm::openAsk,
                            onAskClose = vm::closeAsk,
                            onAskInput = vm::onAskInput,
                            onAskSend = { vm.ask(state.askInput) },
                            onScale = vm::setTextScale,
                            onScaleCommit = vm::commitTextScale,
                            onReadAt = vm::noteReadingAt,
                            onRecategorize = { major -> vm.setCategory(state.recordId, major) },
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

                        Screen.REVIEW -> ReviewScreen(
                            state = state,
                            onReveal = vm::revealReview,
                            onGrade = vm::gradeReview,
                            onBack = { vm.back() },
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
    Screen.HOME, Screen.RECORDS, Screen.LIBRARY, Screen.SETTINGS -> 0
    Screen.STYLE, Screen.MODEL, Screen.REVIEW -> 1
    Screen.LESSON, Screen.PLAY -> 2
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("CaliEdu", text))
}

@Composable
private fun Dock(current: Screen, onSelect: (Screen) -> Unit) {
    val skin = LocalSkin.current
    // 只留图标：标题栏已经写着你在哪一页，这里再来四个字是重复。
    // label 只给读屏用，不画出来。
    val items: List<Triple<Screen, ImageVector, String>> = listOf(
        Triple(Screen.HOME, Icons.Rounded.Home, "首页"),
        Triple(Screen.RECORDS, Icons.Rounded.AutoStories, "记录"),
        Triple(Screen.LIBRARY, Icons.Rounded.FolderOpen, "资料"),
        Triple(Screen.SETTINGS, Icons.Rounded.Settings, "设置"),
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
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { (screen, icon, label) ->
                val selected = screen == current
                val lift by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(180),
                    label = "dock",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable { onSelect(screen) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (skin.style == UiStyle.MATERIAL) {
                        Box(
                            Modifier
                                .background(
                                    if (selected) skin.accentSoft else Color.Transparent,
                                    CircleShape,
                                )
                                .padding(horizontal = 18.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (selected) skin.accentDeep else skin.sub,
                                modifier = Modifier.size(skin.ui(21f)),
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (selected) skin.accent else skin.sub,
                                modifier = Modifier.size(skin.ui(21f + lift * 2f)),
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier
                                    .size(4.dp)
                                    .background(if (selected) skin.accent else Color.Transparent, CircleShape)
                            )
                        }
                    }
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
            // clickable(enabled = false) 不会消费指针事件，浮层下面的按钮照样点得到。
            // 这里把所有事件吞掉，浮层才真的是一层遮罩。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
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
            Text(message, fontSize = skin.smallSize, lineHeight = skin.fs(21f), color = skin.wrong)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("知道了", fontSize = skin.fs(12.5f), color = skin.wrong)
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
            fontSize = skin.fs(12.5f),
            // 底色用正文色、字色用页面底色：气泡永远和页面反着来。
            // 原来固定用 onAccent（白），深色主题下就成了白字白底。
            color = skin.bg,
            modifier = Modifier
                .background(skin.text, RoundedCornerShape(100.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}
