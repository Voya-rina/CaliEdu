package com.caliedu.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caliedu.UiState
import com.caliedu.core.Category
import com.caliedu.core.Track
import com.caliedu.core.Verdict
import com.caliedu.data.ChannelMode
import com.caliedu.model.Choice
import com.caliedu.model.StepContent
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class ExportKind { MARKDOWN, PDF, COPY, PLAY }

@Composable
fun LessonScreen(
    state: UiState,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    onDraft: (String) -> Unit,
    onHint: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSimilar: () -> Unit,
    onToggleReading: () -> Unit,
    onReview: () -> Unit,
    onEndReview: () -> Unit,
    onGenerate: (Int) -> Unit,
    onFocusStep: (Int) -> Unit,
    onExport: (ExportKind) -> Unit,
    onRetry: () -> Unit,
    onJump: (Int) -> Unit,
    onAskOpen: () -> Unit,
    onAskClose: () -> Unit,
    onAskInput: (String) -> Unit,
    onAskSend: () -> Unit,
    onScale: (Float) -> Unit,
    onScaleCommit: () -> Unit,
) {
    val skin = LocalSkin.current
    val plan = state.plan ?: return
    val brief = state.brief
    val track = Track.of(plan.kind)
    var dirOpen by remember { mutableStateOf(false) }
    var fontOpen by remember { mutableStateOf(false) }

    // 答题框的选区要和底栏的符号条共用：符号要插在光标处，String 拿不到光标。
    val question = state.content?.question
    val typing = question?.kind == "short" || question?.kind == "checklist"
    var field by remember(state.recordId, state.progress.idx) { mutableStateOf(TextFieldValue(state.draft)) }
    var focused by remember(state.recordId, state.progress.idx) { mutableStateOf(false) }
    LaunchedEffect(state.draft) {
        if (field.text != state.draft) field = TextFieldValue(state.draft, TextRange(state.draft.length))
    }

    // 翻页：整块正文跟着手指横移，滑出去之后才换内容，再从另一侧滑回来。
    // 不用 AnimatedContent 是因为它会把「退场的那一步」按新 state 重组一遍，
    // 结果滑走的其实是下一步的内容。这里先离场、再换、再入场，顺序不会错。
    val dx = remember { Animatable(0f) }
    var turning by remember { mutableStateOf(false) }
    var bodyWidth by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun slideTo(target: Float, spec: AnimationSpec<Float>) {
        // 拖动中途要能打断上一段动画，animateTo 天然会取消前一段。
        scope.launch { runCatching { dx.animateTo(target, spec) } }
    }

    fun turnTo(dir: Int, allowed: Boolean, width: Float, action: () -> Unit) {
        if (turning) return
        val w = if (width > 0f) width else 1080f
        if (!allowed) {
            // 到头了：顶一下再弹回来。什么都不发生的话，用户会以为手势没生效。
            scope.launch {
                runCatching {
                    dx.animateTo(dir * 26f, tween(90))
                    dx.animateTo(0f, tween(150))
                }
            }
            return
        }
        turning = true
        scope.launch {
            runCatching {
                dx.animateTo(-dir * w, tween(170, easing = FastOutLinearInEasing))
                action()
                // 走出去之后从另一侧回来。这里必须 snap：动画此刻在 -w，
                // 换成 +0.44w 会让同一个值同时被两段动画追，看起来是从半路弹回来的。
                dx.snapTo(dir * w * 0.44f)
                dx.animateTo(0f, tween(210, easing = LinearOutSlowInEasing))
            }
            turning = false
        }
    }

    val canPrev = state.progress.idx > 0
    val canNext = state.canAdvance

    // 切去通读时把翻页的位移收掉，免得回来时正文停在屏幕外。
    LaunchedEffect(state.reading) {
        if (state.reading) {
            turning = false
            runCatching { dx.snapTo(0f) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(skin.bg)
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 6.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = skin.accent)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    plan.title,
                    fontSize = skin.smallSize,
                    fontWeight = FontWeight.Medium,
                    color = skin.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val tag = Category.majorOf(plan)
                val unit = if (state.isDoc) "节" else "步"
                val meta = when {
                    state.reading -> "通读 · 共 ${plan.steps.size} $unit"
                    state.isDoc -> "第 ${state.progress.idx + 1} / ${plan.steps.size} $unit · $tag"
                    state.offline -> "示例 · 第 ${state.progress.idx + 1} 步"
                    else -> buildString {
                        append("第 ${state.progress.idx + 1} 步 · ${brief?.stage.orEmpty()}")
                        if (tag.isNotBlank()) append(" · ").append(tag)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        trackIcon(track),
                        contentDescription = track.label,
                        tint = skin.sub,
                        modifier = Modifier.size(skin.ui(11f)),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (skin.chromeUppercase) meta.uppercase() else meta,
                        fontSize = skin.fs(11.5f),
                        letterSpacing = if (skin.chromeUppercase) 0.6.sp else 0.sp,
                        color = skin.sub,
                    )
                }
            }
            IconButton(onClick = { fontOpen = true }) {
                Icon(
                    Icons.Rounded.FormatSize,
                    contentDescription = "字号",
                    tint = skin.sub,
                )
            }
            IconButton(onClick = onToggleReading) {
                Icon(
                    if (state.reading) Icons.Rounded.ViewAgenda else Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = if (state.reading) "回到单步" else "通读",
                    tint = skin.sub,
                )
            }
            ExportMenu(allowPlay = state.settings.runCode && track == Track.CODE, onExport = onExport)
        }

        if (state.reviewing) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(skin.accentSoft)
                    .padding(start = 20.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("回看中 · 题目不再要求作答", fontSize = skin.fs(11.5f), color = skin.accentDeep)
                Spacer(Modifier.weight(1f))
                Text(
                    "结束回看",
                    fontSize = skin.fs(11.5f),
                    color = skin.accentDeep,
                    modifier = Modifier
                        .clickable(onClick = onEndReview)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        if (state.reading) {
            Box(Modifier.weight(1f)) {
                ReadingView(state = state, onGenerate = onGenerate, onFocusStep = onFocusStep)
            }
            ReadToolbar(state, onGenerate)
        } else {
            Segments(
                total = plan.steps.size,
                current = state.progress.idx,
                skin = skin,
                onClick = { dirOpen = true },
            )
            Box(
                Modifier
                    .weight(1f)
                    .clipToBounds()
                    .onSizeChanged { bodyWidth = it.width }
                    .pointerInput(state.progress.idx, state.finished, state.reading, canNext) {
                        var total = 0f
                        // 走完那一屏没有「下一步」，横滑只会平白抖一下。
                        if (state.finished) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onHorizontalDrag = { change, amount ->
                                // 跟手，但留一点阻尼：手指走 100，页面走 100 会很滑。
                                change.consume()
                                total += amount
                                val limit = size.width.toFloat() * 0.94f
                                val next = (dx.value + amount * 0.92f).coerceIn(-limit, limit)
                                scope.launch { runCatching { dx.snapTo(next) } }
                            },
                            onDragEnd = {
                                val w = size.width.toFloat()
                                when {
                                    total < -w * 0.2f -> turnTo(1, canNext, w, onNext)
                                    total > w * 0.2f -> turnTo(-1, canPrev, w, onPrev)
                                    else -> slideTo(0f, tween(180))
                                }
                            },
                            onDragCancel = { slideTo(0f, tween(180)) },
                        )
                    }
            ) {
                StepBody(
                    state = state,
                    track = track,
                    field = field,
                    onField = { value ->
                        field = value
                        onDraft(value.text)
                    },
                    onFocus = { focused = it },
                    onSubmit = onSubmit,
                    onFinish = onBack,
                    onSimilar = onSimilar,
                    onReview = onReview,
                    onRetry = onRetry,
                    dx = dx.value,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!state.finished) {
                StepBottomBar(
                    state = state,
                    showSymbols = track == Track.MATH && typing && !state.canAdvance && !state.grading,
                    onInsertSymbol = { item ->
                        val next = field.insertSymbol(item, focused)
                        field = next
                        onDraft(next.text)
                    },
                    onHint = onHint,
                    // 点按钮和横滑走同一条动画：否则同一个动作两种观感。
                    onNext = { turnTo(1, canNext, bodyWidth.toFloat(), onNext) },
                    onPrev = { turnTo(-1, canPrev, bodyWidth.toFloat(), onPrev) },
                    onAsk = onAskOpen,
                )
            }
        }
    }

    if (state.askOpen) {
        AskSheet(
            stepTitle = brief?.title.orEmpty(),
            messages = state.askMessages,
            partial = state.askPartial,
            input = state.askInput,
            asking = state.asking,
            onInput = onAskInput,
            onSend = onAskSend,
            onDismiss = onAskClose,
            // 数学课里问「这一步为什么能约掉」时，符号同样是打不出来的。
            showSymbols = track == Track.MATH,
        )
    }

    if (fontOpen) {
        FontDialog(
            scale = state.textScale,
            onChange = onScale,
            onCommit = onScaleCommit,
            onDismiss = { fontOpen = false },
        )
    }

    if (dirOpen) {
        StepDirectorySheet(
            state = state,
            onPick = { index ->
                dirOpen = false
                onJump(index)
            },
            onDismiss = { dirOpen = false },
        )
    }
}

@Composable
private fun ExportMenu(allowPlay: Boolean, onExport: (ExportKind) -> Unit) {
    val skin = LocalSkin.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.IosShare, contentDescription = "导出", tint = skin.sub, modifier = Modifier.size(skin.ui(20f)))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("导出 Markdown", fontSize = skin.smallSize) },
                onClick = { open = false; onExport(ExportKind.MARKDOWN) },
            )
            DropdownMenuItem(
                text = { Text("打印成 PDF", fontSize = skin.smallSize) },
                onClick = { open = false; onExport(ExportKind.PDF) },
            )
            DropdownMenuItem(
                text = { Text("复制全文", fontSize = skin.smallSize) },
                onClick = { open = false; onExport(ExportKind.COPY) },
            )
            if (allowPlay) {
                DropdownMenuItem(
                    text = { Text("试跑这段代码", fontSize = skin.smallSize) },
                    onClick = { open = false; onExport(ExportKind.PLAY) },
                )
            }
        }
    }
}

@Composable
private fun ReadToolbar(state: UiState, onGenerate: (Int) -> Unit) {
    val skin = LocalSkin.current
    val nextMissing = state.plan?.steps?.indexOfFirst { !state.contents.containsKey(it.n) } ?: -1
    Column(Modifier.background(skin.bg)) {
        if (skin.style != UiStyle.MATERIAL) {
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
                .padding(horizontal = 20.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("已写 ${state.contents.size} / ${state.stepCount} 步", fontSize = skin.fs(12f), color = skin.sub)
            Spacer(Modifier.weight(1f))
            if (nextMissing >= 0 && !state.offline) {
                val busy = state.waitingStep >= 0
                Row(
                    Modifier
                        .clickable(enabled = !busy) { onGenerate(nextMissing) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(skin.ui(12f)),
                            strokeWidth = 1.5.dp,
                            color = skin.accent,
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        if (busy) "正在写…" else "补写第 ${nextMissing + 1} 步",
                        fontSize = skin.fs(12.5f),
                        color = if (busy) skin.sub else skin.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepBody(
    state: UiState,
    track: Track,
    field: TextFieldValue,
    onField: (TextFieldValue) -> Unit,
    onFocus: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    onFinish: () -> Unit,
    onSimilar: () -> Unit,
    onReview: () -> Unit,
    onRetry: () -> Unit,
    dx: Float,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val brief = state.brief
    val content = state.content
    // 用 canAdvance 而不是 progress.canAdvance：回看时（以及从记录里翻回已过的步）
    // 这道题的 progress 是新的，但这一课里它早就过了，不该再让人答一遍。
    val answered = state.canAdvance

    val scroll = rememberScrollState()
    // 换一步就回到顶部。否则读到长讲义底部再点「下一步」，新一步一进来就停在底部。
    LaunchedEffect(state.progress.idx, state.finished) { scroll.scrollTo(0) }

    Box(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(dx.roundToInt(), 0) }
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
        ) {
        Spacer(Modifier.height(20.dp))
        if (state.finished) {
            FinishedView(state, skin, onBack = onFinish, onSimilar = onSimilar, onReview = onReview)
            Spacer(Modifier.height(40.dp))
            return@Column
        }
        if (brief != null) {
            Text(
                text = brief.title,
                fontSize = skin.stepTitleSize,
                lineHeight = skin.stepTitleSize * 1.4f,
                fontWeight = skin.stepTitleWeight,
                color = if (skin.style == UiStyle.PRINT) skin.accentDeep else skin.text,
            )
            Spacer(Modifier.height(14.dp))

            if (content != null) {
                Markdown(dropDuplicateTitle(content.blocks.joinToString("\n\n") { it.md }, brief.title))
                if (content.question.prompt.isBlank()) {
                    Spacer(Modifier.height(40.dp))
                    return@Column
                }
                Spacer(Modifier.height(22.dp))
                QuestionCard(
                    content = content,
                    track = track,
                    answered = answered,
                    field = field,
                    grading = state.grading,
                    onPick = onSubmit,
                    onField = onField,
                    onFocus = onFocus,
                    onSubmit = { onSubmit(state.draft) },
                    skin = skin,
                )
                state.feedback?.let { feedback ->
                    Spacer(Modifier.height(12.dp))
                    FeedbackBlock(feedback.verdict, feedback.text, skin)
                }
                if (answered && content.summary.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        content.summary.forEach { line ->
                            Row {
                                Text("·", fontSize = skin.bodySize * 0.95f, color = skin.accent, modifier = Modifier.width(14.dp))
                                Text(line, fontSize = skin.bodySize * 0.95f, lineHeight = skin.bodyLine * 0.95f, color = skin.sub)
                            }
                        }
                    }
                }
            } else {
                Text(brief.goal, fontSize = skin.bodySize, lineHeight = skin.bodyLine, color = skin.sub)
                Spacer(Modifier.height(12.dp))
                if (state.error != null) {
                    Row(
                        Modifier
                            .background(skin.codeBg, RoundedCornerShape(100.dp))
                            .clickable(onClick = onRetry)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = skin.accent,
                            modifier = Modifier.size(skin.ui(14f)),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("这一步没写出来，重试", fontSize = skin.fs(12.5f), color = skin.accent)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(skin.ui(14f)),
                            strokeWidth = 1.6.dp,
                            color = skin.accent,
                        )
                        Spacer(Modifier.width(9.dp))
                        Text("正在写这一步的讲义…", fontSize = skin.smallSize, color = skin.sub)
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun StepBottomBar(
    state: UiState,
    showSymbols: Boolean,
    onInsertSymbol: (SymItem) -> Unit,
    onHint: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onAsk: () -> Unit,
) {
    val skin = LocalSkin.current
    // 这一步刚答完（或讲义型没有提问）：提示按钮该收起来
    val solvedNow = state.progress.canAdvance ||
        (state.content?.question?.prompt?.isBlank() == true)
    // 能不能往下走：本次答完，或者这一步之前就过过
    val canNext = state.canAdvance
    val canPrev = state.progress.idx > 0
    val canAsk = state.content != null && state.settings.mode == ChannelMode.DIRECT && state.settings.configured
    val isLast = state.progress.idx == (state.plan?.steps?.lastIndex ?: 0)
    // 底栏所有按键同一尺寸、同一形状。之前上一步 42dp 圆、下一步 50dp 方，
    // 两个键看着像来自两套设计。
    val keyShape = if (skin.pillChoices) CircleShape else RoundedCornerShape(skin.radiusCard)

    Column(Modifier.background(skin.bg)) {
        if (skin.style != UiStyle.MATERIAL) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(skin.line)
            )
        }
        // 符号条贴着底栏：输入框就在上面，手不用在屏幕上下两头来回跑。
        if (showSymbols) {
            SymbolStrip(
                onInsert = onInsertSymbol,
                enabled = !state.grading,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 往回翻：长课里想重看上一节，不该只能开目录。
            BarKey(
                icon = Icons.Rounded.ChevronLeft,
                label = "上一步",
                enabled = canPrev,
                primary = false,
                shape = keyShape,
                onClick = onPrev,
            )
            if (!solvedNow) {
                Spacer(Modifier.width(8.dp))
                BarKey(
                    icon = Icons.Rounded.Lightbulb,
                    label = "提示",
                    enabled = state.progress.hintsShown < 3,
                    primary = false,
                    shape = keyShape,
                    onClick = onHint,
                )
            }
            if (canAsk) {
                Spacer(Modifier.width(8.dp))
                BarKey(
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    label = "追问",
                    enabled = true,
                    primary = false,
                    shape = keyShape,
                    onClick = onAsk,
                )
            }
            Spacer(Modifier.weight(1f))
            BarKey(
                icon = if (isLast) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                label = if (isLast) "完成" else "下一步",
                enabled = canNext,
                primary = true,
                shape = keyShape,
                onClick = onNext,
            )
        }
    }
}

/** 底栏按键。[primary] 只改填充色，几何尺寸和形状一律跟随底栏，两个方向键才像一对。 */
@Composable
private fun BarKey(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    primary: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    val background = if (primary && enabled) skin.accent else skin.codeBg
    val tint = when {
        primary && enabled -> skin.onAccent
        !enabled -> skin.line
        primary -> skin.accent
        else -> skin.sub
    }
    Box(
        Modifier
            .size(skin.ui(46f))
            .background(background, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(skin.ui(20f)))
    }
}

@Composable
private fun Segments(total: Int, current: Int, skin: Skin, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (total <= 14) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(if (skin.style == UiStyle.PRINT) 4.dp else 5.dp),
            ) {
                repeat(total) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(if (i <= current) skin.accent else skin.line, RoundedCornerShape(2.dp))
                    )
                }
            }
        } else {
            // 二十几段细线并排就看不出进度了，退成一条连续进度 + 计数。
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(skin.line, RoundedCornerShape(2.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((current + 1f) / total.coerceAtLeast(1))
                        .height(3.dp)
                        .background(skin.accent, RoundedCornerShape(2.dp))
                )
            }
            Spacer(Modifier.width(10.dp))
            Text("${current + 1}/$total", fontSize = skin.fs(10.5f), color = skin.sub)
        }
    }
}

private fun trackIcon(track: Track) = when (track) {
    Track.CODE -> Icons.Rounded.Code
    Track.MATH -> Icons.Rounded.Functions
    Track.GENERAL -> Icons.AutoMirrored.Rounded.Notes
}

@Composable
private fun QuestionCard(
    content: StepContent,
    track: Track,
    answered: Boolean,
    field: TextFieldValue,
    grading: Boolean,
    onPick: (String) -> Unit,
    onField: (TextFieldValue) -> Unit,
    onFocus: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    skin: Skin,
) {
    val question = content.question
    val chosen = field.text
    val cardShape = RoundedCornerShape(skin.radiusCard)
    Column(
        Modifier
            .fillMaxWidth()
            .background(skin.surface, cardShape)
            .let { base ->
                if (skin.borderWidth > 0.dp) base.border(skin.borderWidth, skin.line, cardShape) else base
            }
            .padding(16.dp)
    ) {
        if (skin.style == UiStyle.PRINT) {
            Row {
                Box(
                    Modifier
                        .width(5.dp)
                        .height((skin.bodySize.value * 2.6f).dp)
                        .background(skin.accent)
                )
                Spacer(Modifier.width(11.dp))
                Text(
                    text = inlineMarkdown(question.prompt, skin),
                    fontSize = skin.bodySize,
                    lineHeight = skin.bodyLine,
                    color = skin.accentDeep,
                )
            }
        } else {
            Text(
                text = inlineMarkdown(question.prompt, skin),
                fontSize = skin.bodySize * 1.03f,
                lineHeight = skin.bodyLine,
                fontWeight = FontWeight.Medium,
                color = skin.text,
            )
        }
        Spacer(Modifier.height(15.dp))

        if (question.kind == "short" || question.kind == "checklist") {
            val multi = question.kind == "checklist"

            Box(
                Modifier
                    .fillMaxWidth()
                    .background(skin.codeBg, RoundedCornerShape(skin.radiusBlock))
                    .padding(horizontal = 13.dp, vertical = 12.dp)
            ) {
                if (chosen.isEmpty()) {
                    Text(
                        text = when {
                            multi -> "用自己的话写两句，按要点说"
                            track == Track.MATH -> "把式子或数值写在这"
                            track == Track.CODE -> "一句话回答，说清为什么"
                            else -> "一句话回答"
                        },
                        fontSize = skin.smallSize,
                        color = skin.sub,
                    )
                }
                BasicTextField(
                    value = field,
                    onValueChange = onField,
                    enabled = !answered && !grading,
                    textStyle = TextStyle(fontSize = skin.bodySize, lineHeight = skin.bodyLine, color = skin.text),
                    cursorBrush = SolidColor(skin.accent),
                    maxLines = if (multi) 6 else 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { onFocus(it.isFocused) },
                )
            }

            // 符号条在底栏：挨着输入框会被键盘顶来顶去，也会把「提交」挤到看不见。
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(
                            if (!answered && !grading && chosen.isNotBlank()) skin.text else skin.line,
                            RoundedCornerShape(if (skin.pillChoices) 100.dp else skin.radiusChoice),
                        )
                        .clickable(enabled = !answered && !grading && chosen.isNotBlank(), onClick = onSubmit)
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        "提交",
                        fontSize = skin.smallSize,
                        color = if (!answered && !grading && chosen.isNotBlank()) skin.bg else skin.sub,
                    )
                }
                if (grading) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(skin.ui(13f)),
                        strokeWidth = 1.5.dp,
                        color = skin.accent,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("看一下", fontSize = skin.fs(11.5f), color = skin.sub)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(if (skin.style == UiStyle.APPLE) 0.dp else 8.dp)) {
                question.choices.forEach { choice ->
                    ChoiceRow(
                        choice = choice,
                        answered = answered,
                        isAnswer = answered && choice.value == question.answer,
                        isChosen = chosen == choice.value,
                        onClick = { if (!answered) onPick(choice.value) },
                        skin = skin,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    choice: Choice,
    answered: Boolean,
    isAnswer: Boolean,
    isChosen: Boolean,
    onClick: () -> Unit,
    skin: Skin,
) {
    val shape = RoundedCornerShape(skin.radiusChoice)
    val background = when {
        isAnswer && skin.filledCorrect -> skin.accent
        isAnswer -> skin.accentSoft
        isChosen && answered -> skin.wrongSoft
        else -> if (skin.style == UiStyle.APPLE) skin.surface else skin.bg
    }
    val border = when {
        isAnswer && skin.filledCorrect -> skin.accent
        isAnswer -> skin.accent
        isChosen && answered -> skin.wrong
        else -> skin.line
    }
    val textColor = when {
        isAnswer && skin.filledCorrect -> skin.onAccent
        isAnswer -> skin.accentDeep
        isChosen && answered -> skin.wrong
        else -> skin.text
    }
    val showBorder = skin.borderWidth > 0.dp || skin.style == UiStyle.APPLE

    Box(
        Modifier
            .fillMaxWidth()
            .background(background, shape)
            .let { base -> if (showBorder) base.border(if (skin.borderWidth > 0.dp) skin.borderWidth else 0.5.dp, border, shape) else base }
            .clickable(enabled = !answered, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (skin.style == UiStyle.MATERIAL) 13.dp else 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = inlineMarkdown(choice.label, skin),
                fontSize = skin.bodySize,
                lineHeight = skin.bodyLine,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            if (isAnswer) {
                Text("✓", fontSize = skin.bodySize, color = if (skin.filledCorrect) skin.onAccent else skin.accent)
            }
        }
    }
}

@Composable
private fun FeedbackBlock(verdict: Verdict, text: String, skin: Skin) {
    // 三档而不是两档：连错三次公布答案既不是「答对」也不该按「答错」的红字骂人。
    val tone = when (verdict) {
        Verdict.CORRECT -> if (skin.style == UiStyle.PRINT) skin.amber else skin.accent
        Verdict.PARTIAL -> skin.amber
        Verdict.WRONG -> skin.wrong
    }
    val soft = when (verdict) {
        Verdict.CORRECT -> if (skin.style == UiStyle.PRINT) skin.amberSoft else skin.okSoft
        Verdict.PARTIAL -> skin.amberSoft
        Verdict.WRONG -> skin.wrongSoft
    }
    val ink = when (verdict) {
        Verdict.CORRECT -> if (skin.style == UiStyle.PRINT) skin.text else skin.accentDeep
        Verdict.PARTIAL -> skin.text
        Verdict.WRONG -> skin.wrong
    }
    if (skin.style == UiStyle.PRINT) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(soft)
        ) {
            Box(
                Modifier
                    .width(5.dp)
                    .height((skin.bodySize.value * 4f).dp)
                    .background(tone)
            )
            Text(
                text = text,
                fontSize = skin.bodySize * 0.97f,
                lineHeight = skin.bodyLine,
                color = ink,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    } else {
        Text(
            text = text,
            fontSize = skin.bodySize * 0.97f,
            lineHeight = skin.bodyLine,
            color = ink,
            modifier = Modifier
                .fillMaxWidth()
                .background(soft, RoundedCornerShape(skin.radiusChoice))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun FinishedView(
    state: UiState,
    skin: Skin,
    onBack: () -> Unit,
    onSimilar: () -> Unit,
    onReview: () -> Unit,
) {
    val plan = state.plan
    Column {
        Text(
            "走完了。",
            fontSize = skin.heroSize,
            lineHeight = skin.heroSize * 1.35f,
            fontWeight = FontWeight.Bold,
            color = skin.text,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (state.isDoc) {
                "读完了 ${plan?.steps?.size ?: 0} 节。"
            } else {
                "这道题被拆成 ${plan?.steps?.size ?: 0} 步，答对 ${state.solved} 次、答错 ${state.wrongs} 次。"
            },
            fontSize = skin.bodySize,
            lineHeight = skin.bodyLine,
            color = skin.sub,
        )
        if (plan != null) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Category.tagsOf(plan).forEach { tag ->
                    Text(
                        tag,
                        fontSize = skin.fs(10.5f),
                        color = skin.accentDeep,
                        modifier = Modifier
                            .background(skin.accentSoft, RoundedCornerShape(100.dp))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        if (!plan?.knowledge.isNullOrEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                plan?.knowledge?.forEach { k ->
                    Row {
                        Text("·", fontSize = skin.bodySize * 0.95f, color = skin.accent, modifier = Modifier.width(14.dp))
                        Text(k, fontSize = skin.bodySize * 0.95f, color = skin.sub)
                    }
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        // 走完了不等于看不了了。讲义都在记录里，给一个回看的入口——
        // 之前这一屏只有「再来一道」和「换一道」，想翻回去看看第 3 步怎么推的，没有路。
        Box(
            Modifier
                .fillMaxWidth()
                .background(skin.codeBg, RoundedCornerShape(if (skin.pillChoices) 100.dp else skin.radiusCard))
                .clickable(onClick = onReview)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "回看这 ${plan?.steps?.size ?: 0} ${if (state.isDoc) "节" else "步"}",
                fontSize = skin.bodySize * 0.95f,
                color = skin.text,
            )
        }
        Spacer(Modifier.height(10.dp))
        // 走完不是终点：要么换一道新的，要么照着这道再来一道同类型的。
        val canSimilar = !state.isDoc &&
            state.settings.mode == ChannelMode.DIRECT &&
            state.settings.configured
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (canSimilar) {
                Box(
                    Modifier
                        .weight(1f)
                        .background(skin.accentSoft, RoundedCornerShape(if (skin.pillChoices) 100.dp else skin.radiusCard))
                        .clickable(onClick = onSimilar)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "练一道同类的",
                        fontSize = skin.bodySize * 0.95f,
                        color = skin.accentDeep,
                    )
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .background(skin.accent, RoundedCornerShape(if (skin.pillChoices) 100.dp else skin.radiusCard))
                    .clickable(onClick = onBack)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (state.isDoc) "回首页" else "换一道题",
                    fontSize = skin.bodySize * 0.95f,
                    color = skin.onAccent,
                )
            }
        }
    }
}

/** 正在读的时候想调字号，不该被赶回设置页。 */
@Composable
private fun FontDialog(
    scale: Float,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val skin = LocalSkin.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = skin.surface,
        shape = RoundedCornerShape(skin.radiusCard),
        title = {
            Text("字号", fontSize = skin.fs(15.5f), fontWeight = FontWeight.Medium, color = skin.text)
        },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A", fontSize = skin.fs(12f), color = skin.sub)
                Slider(
                    value = scale,
                    onValueChange = onChange,
                    onValueChangeFinished = onCommit,
                    valueRange = 0.9f..1.4f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                )
                Text("A", fontSize = skin.fs(22f), color = skin.sub)
            }
        },
        confirmButton = {
            Text(
                "好",
                fontSize = skin.fs(12.5f),
                color = skin.accent,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        },
    )
}
