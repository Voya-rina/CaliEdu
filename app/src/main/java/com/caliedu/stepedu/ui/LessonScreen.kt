package com.caliedu.stepedu.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caliedu.stepedu.UiState
import com.caliedu.stepedu.core.Verdict
import com.caliedu.stepedu.model.Choice
import com.caliedu.stepedu.model.StepContent

@Composable
fun LessonScreen(
    state: UiState,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    onDraft: (String) -> Unit,
    onHint: () -> Unit,
    onNext: () -> Unit,
) {
    val plan = state.plan ?: return
    val brief = state.brief
    val content = state.content
    val answered = state.progress.canAdvance
    val isLast = state.progress.idx == plan.steps.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Bg)
            .imePadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = Ink.Text)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    plan.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (state.offline) "示例 · 第 ${state.progress.idx + 1} 步" else "第 ${state.progress.idx + 1} 步 · ${brief?.stage.orEmpty()}",
                    fontSize = 12.sp,
                    color = Ink.Sub,
                )
            }
            Text("${state.progress.idx + 1} / ${plan.steps.size}", fontSize = 13.sp, color = Ink.Sub)
        }

        Segments(total = plan.steps.size, current = state.progress.idx)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(22.dp))
            if (state.finished) {
                FinishedView(state, onBack)
            } else if (brief != null) {
                Text(brief.title, fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium, color = Ink.Text)
                Spacer(Modifier.height(16.dp))
                if (content != null) {
                    Markdown(content.blocks.joinToString("\n\n") { it.md })
                    Spacer(Modifier.height(22.dp))
                    QuestionCard(
                        content = content,
                        answered = answered,
                        chosen = state.draft,
                        draft = state.draft,
                        onPick = onSubmit,
                        onDraft = onDraft,
                        onSubmit = { onSubmit(state.draft) },
                    )
                    state.feedback?.let { feedback ->
                        Spacer(Modifier.height(14.dp))
                        val correct = feedback.verdict == Verdict.CORRECT
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(if (correct) Ink.AccentSoft else Ink.WrongSoft, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                feedback.text,
                                fontSize = 14.sp,
                                lineHeight = 23.sp,
                                color = if (correct) Ink.Accent else Ink.Wrong,
                            )
                        }
                    }
                    if (answered && content.summary.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            content.summary.forEach { line ->
                                Row {
                                    Text("·", fontSize = 14.sp, color = Ink.Sub, modifier = Modifier.width(14.dp))
                                    Text(line, fontSize = 14.sp, lineHeight = 22.sp, color = Ink.Sub)
                                }
                            }
                        }
                    }
                } else {
                    Text(brief.goal, fontSize = 15.sp, lineHeight = 25.sp, color = Ink.Sub)
                    Spacer(Modifier.height(12.dp))
                    Text("正在写这一步的讲义…", fontSize = 14.sp, color = Ink.Sub)
                }
            }
            Spacer(Modifier.height(40.dp))
        }

        if (!state.finished) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!answered) {
                    TextButton(onClick = onHint, enabled = state.progress.hintsShown < 3) {
                        Text("提示", fontSize = 14.sp, color = if (state.progress.hintsShown < 3) Ink.Sub else Ink.Line)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onNext,
                    enabled = answered,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Ink.Accent,
                        contentColor = Ink.Card,
                        disabledContainerColor = Ink.Line,
                        disabledContentColor = Ink.Sub,
                    ),
                    modifier = Modifier.height(46.dp),
                ) {
                    Text(if (isLast) "完成" else "下一步", fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun Segments(total: Int, current: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(if (i <= current) Ink.Accent else Ink.Line)
            )
        }
    }
}

@Composable
private fun QuestionCard(
    content: StepContent,
    answered: Boolean,
    chosen: String?,
    draft: String,
    onPick: (String) -> Unit,
    onDraft: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val question = content.question
    Column(
        Modifier
            .fillMaxWidth()
            .background(Ink.Card, RoundedCornerShape(16.dp))
            .border(1.dp, Ink.Line, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text(inlineMarkdown(question.prompt), fontSize = 15.sp, lineHeight = 25.sp, color = Ink.Text)
        Spacer(Modifier.height(16.dp))
        if (question.kind == "short") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Ink.Code, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (draft.isEmpty()) Text("一句话回答", fontSize = 14.sp, color = Ink.Sub)
                BasicTextField(
                    value = draft,
                    onValueChange = onDraft,
                    enabled = !answered,
                    textStyle = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = Ink.Text),
                    cursorBrush = SolidColor(Ink.Accent),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSubmit,
                enabled = !answered && draft.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink.Text,
                    contentColor = Ink.Card,
                    disabledContainerColor = Ink.Line,
                    disabledContentColor = Ink.Sub,
                ),
                modifier = Modifier.height(42.dp),
            ) {
                Text("提交", fontSize = 14.sp)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.choices.forEach { choice ->
                    ChoiceRow(
                        choice = choice,
                        answered = answered,
                        isAnswer = answered && choice.value == question.answer,
                        isChosen = chosen == choice.value,
                        onClick = { if (!answered) onPick(choice.value) },
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
) {
    val background = when {
        isAnswer -> Ink.AccentSoft
        isChosen && answered -> Ink.WrongSoft
        else -> Color.Transparent
    }
    val border = when {
        isAnswer -> Ink.Accent
        isChosen && answered -> Ink.Wrong
        else -> Ink.Line
    }
    val textColor = when {
        isAnswer -> Ink.Accent
        isChosen && answered -> Ink.Wrong
        else -> Ink.Text
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(11.dp))
            .border(1.dp, border, RoundedCornerShape(11.dp))
            .clickable(enabled = !answered, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Text(choice.label, fontSize = 14.5.sp, lineHeight = 22.sp, color = textColor)
    }
}

@Composable
private fun FinishedView(state: UiState, onBack: () -> Unit) {
    val plan = state.plan
    Column {
        Text("走完了。", fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, color = Ink.Text)
        Spacer(Modifier.height(12.dp))
        Text(
            "这道题被拆成 ${plan?.steps?.size ?: 0} 步，每一步都是你自己答上来的。" +
                if (state.offline) " 这是离线示例，配好模型后换成你自己的题。" else "",
            fontSize = 15.sp,
            lineHeight = 25.sp,
            color = Ink.Sub,
        )
        Spacer(Modifier.height(24.dp))
        if (!plan?.knowledge.isNullOrEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plan?.knowledge?.forEach { k ->
                    Row {
                        Text("·", fontSize = 14.sp, color = Ink.Sub, modifier = Modifier.width(14.dp))
                        Text(k, fontSize = 14.sp, color = Ink.Sub)
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onBack,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink.Accent, contentColor = Ink.Card),
            modifier = Modifier.height(46.dp),
        ) {
            Text("换一道题", fontSize = 15.sp)
        }
    }
}
