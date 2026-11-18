package com.caliedu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caliedu.UiState
import com.caliedu.core.Verdict
import com.caliedu.data.ChannelMode
import com.caliedu.model.Choice
import com.caliedu.model.StepContent

enum class ExportKind { MARKDOWN, PDF, COPY, PLAY }

@Composable
fun LessonScreen(
    state: UiState,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    onDraft: (String) -> Unit,
    onHint: () -> Unit,
    onNext: () -> Unit,
    onToggleReading: () -> Unit,
    onGenerate: (Int) -> Unit,
    onFocusStep: (Int) -> Unit,
    onExport: (ExportKind) -> Unit,
    onRetry: () -> Unit,
    onJump: (Int) -> Unit,
    onAskOpen: () -> Unit,
    onAskClose: () -> Unit,
    onAskInput: (String) -> Unit,
    onAskSend: () -> Unit,
) {
    val skin = LocalSkin.current
    val plan = state.plan ?: return
    val brief = state.brief
    var dirOpen by remember { mutableStateOf(false) }

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
                val meta = when {
                    state.reading -> "通读 · 共 ${plan.steps.size} 步"
                    state.offline -> "示例 · 第 ${state.progress.idx + 1} 步"
                    else -> "第 ${state.progress.idx + 1} 步 · ${brief?.stage.orEmpty()}"
                }
                Text(
                    text = if (skin.chromeUppercase) meta.uppercase() else meta,
                    fontSize = 11.5.sp,
                    letterSpacing = if (skin.chromeUppercase) 0.6.sp else 0.sp,
                    color = skin.sub,
                )
            }
            IconButton(onClick = onToggleReading) {
                Icon(
                    if (state.reading) Icons.Rounded.ViewAgenda else Icons.Rounded.MenuBook,
                    contentDescription = if (state.reading) "回到单步" else "通读",
                    tint = skin.sub,
                )
            }
            ExportMenu(allowPlay = state.settings.runCode, onExport = onExport)
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
            StepBody(
                state = state,
                onSubmit = onSubmit,
                onDraft = onDraft,
                onFinish = onBack,
                onRetry = onRetry,
                modifier = Modifier.weight(1f),
            )
            if (!state.finished) {
                StepBottomBar(
                    state = state,
                    onHint = onHint,
                    onNext = onNext,
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
            Icon(Icons.Rounded.IosShare, contentDescription = "导出", tint = skin.sub, modifier = Modifier.size(20.dp))
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
            Text("已写 ${state.contents.size} / ${state.stepCount} 步", fontSize = 12.sp, color = skin.sub)
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
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = skin.accent,
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        if (busy) "正在写…" else "补写第 ${nextMissing + 1} 步",
                        fontSize = 12.5.sp,
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
    onSubmit: (String) -> Unit,
    onDraft: (String) -> Unit,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val brief = state.brief
    val content = state.content
    val answered = state.progress.canAdvance

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        if (state.finished) {
            FinishedView(state, skin, onBack = onFinish)
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
                Markdown(content.blocks.joinToString("\n\n") { it.md })
                Spacer(Modifier.height(22.dp))
                QuestionCard(
                    content = content,
                    answered = answered,
                    chosen = state.draft,
                    grading = state.grading,
                    onPick = onSubmit,
                    onDraft = onDraft,
                    onSubmit = { onSubmit(state.draft) },
                    skin = skin,
                )
                state.feedback?.let { feedback ->
                    Spacer(Modifier.height(12.dp))
                    FeedbackBlock(feedback.verdict == Verdict.CORRECT, feedback.text, skin)
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
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("这一步没写出来，重试", fontSize = 12.5.sp, color = skin.accent)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
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

@Composable
private fun StepBottomBar(state: UiState, onHint: () -> Unit, onNext: () -> Unit, onAsk: () -> Unit) {
    val skin = LocalSkin.current
    val answered = state.progress.canAdvance
    val canAsk = state.content != null && state.settings.mode == ChannelMode.DIRECT && state.settings.configured
    val isLast = state.progress.idx == (state.plan?.steps?.lastIndex ?: 0)
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!answered) {
                val left = state.progress.hintsShown < 3
                Box(
                    Modifier
                        .size(42.dp)
                        .background(if (left) skin.codeBg else skin.bg, CircleShape)
                        .clickable(enabled = left, onClick = onHint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Lightbulb,
                        contentDescription = "提示",
                        tint = if (left) skin.accent else skin.line,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (canAsk) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(42.dp)
                        .background(skin.codeBg, CircleShape)
                        .clickable(onClick = onAsk),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = "追问",
                        tint = skin.accent,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(50.dp)
                    .background(
                        if (answered) skin.accent else skin.codeBg,
                        if (skin.pillChoices) CircleShape else RoundedCornerShape(skin.radiusCard),
                    )
                    .clickable(enabled = answered, onClick = onNext),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isLast) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = if (isLast) "完成" else "下一步",
                    tint = if (answered) skin.onAccent else skin.sub,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun Segments(total: Int, current: Int, skin: Skin, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
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
}

@Composable
private fun QuestionCard(
    content: StepContent,
    answered: Boolean,
    chosen: String,
    grading: Boolean,
    onPick: (String) -> Unit,
    onDraft: (String) -> Unit,
    onSubmit: () -> Unit,
    skin: Skin,
) {
    val question = content.question
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
                        if (multi) "用自己的话写两句，按要点说" else "一句话回答",
                        fontSize = skin.smallSize,
                        color = skin.sub,
                    )
                }
                BasicTextField(
                    value = chosen,
                    onValueChange = onDraft,
                    enabled = !answered && !grading,
                    textStyle = TextStyle(fontSize = skin.bodySize, lineHeight = skin.bodyLine, color = skin.text),
                    cursorBrush = SolidColor(skin.accent),
                    maxLines = if (multi) 6 else 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 1.5.dp,
                        color = skin.accent,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("看一下", fontSize = 11.5.sp, color = skin.sub)
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
private fun FeedbackBlock(correct: Boolean, text: String, skin: Skin) {
    if (skin.style == UiStyle.PRINT) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (correct) skin.amberSoft else skin.wrongSoft)
        ) {
            Box(
                Modifier
                    .width(5.dp)
                    .height((skin.bodySize.value * 4f).dp)
                    .background(if (correct) skin.amber else skin.wrong)
            )
            Text(
                text = text,
                fontSize = skin.bodySize * 0.97f,
                lineHeight = skin.bodyLine,
                color = if (correct) skin.text else skin.wrong,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    } else {
        Text(
            text = text,
            fontSize = skin.bodySize * 0.97f,
            lineHeight = skin.bodyLine,
            color = if (correct) skin.accentDeep else skin.wrong,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (correct) skin.okSoft else skin.wrongSoft, RoundedCornerShape(skin.radiusChoice))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun FinishedView(state: UiState, skin: Skin, onBack: () -> Unit) {
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
            "这道题被拆成 ${plan?.steps?.size ?: 0} 步，答对 ${state.solved} 次、答错 ${state.wrongs} 次。",
            fontSize = skin.bodySize,
            lineHeight = skin.bodyLine,
            color = skin.sub,
        )
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
        Box(
            Modifier
                .background(skin.accent, RoundedCornerShape(if (skin.pillChoices) 100.dp else skin.radiusCard))
                .clickable(onClick = onBack)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("换一道题", fontSize = skin.bodySize, color = skin.onAccent)
        }
    }
}
