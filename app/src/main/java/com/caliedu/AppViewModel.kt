package com.caliedu

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caliedu.core.Judge
import com.caliedu.core.Prompts
import com.caliedu.core.LessonDoc
import com.caliedu.core.Progress
import com.caliedu.core.Verdict
import com.caliedu.data.ChannelMode
import com.caliedu.data.LessonRecord
import com.caliedu.data.LessonStore
import com.caliedu.data.Mistake
import com.caliedu.data.LlmSettings
import com.caliedu.data.SettingsStore
import com.caliedu.data.ThemeMode
import com.caliedu.llm.ChatException
import com.caliedu.llm.LessonGenerator
import com.caliedu.llm.RelayBroker
import com.caliedu.model.LessonPlan
import com.caliedu.model.StepContent
import com.caliedu.sample.SampleLesson
import com.caliedu.ui.UiStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class Screen { HOME, RECORDS, SETTINGS, STYLE, MODEL, LESSON, PLAY }

data class Feedback(val verdict: Verdict, val text: String)

/** 追问里的一条消息。 */
data class AskMessage(val fromUser: Boolean, val text: String)

data class UiState(
    val screen: Screen = Screen.HOME,
    val input: String = "",
    val plan: LessonPlan? = null,
    val contents: Map<Int, StepContent> = emptyMap(),
    val progress: Progress = Progress(),
    val loading: String? = null,
    val seconds: Int = 0,
    val error: String? = null,
    val feedback: Feedback? = null,
    val draft: String = "",
    val notes: String = "",
    val offline: Boolean = false,
    val finished: Boolean = false,
    val settings: LlmSettings = LlmSettings(),
    val style: UiStyle = UiStyle.PRINT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val records: List<LessonRecord> = emptyList(),
    val recordId: String = "",
    val relay: RelayBroker.Pending? = null,
    val grading: Boolean = false,
    val waitingStep: Int = -1,
    val reading: Boolean = false,
    val toast: String? = null,
    val solved: Int = 0,
    val wrongs: Int = 0,
    val playCode: String = "",
    val mistakes: List<Mistake> = emptyList(),
    val askOpen: Boolean = false,
    val askMessages: List<AskMessage> = emptyList(),
    val askInput: String = "",
    val asking: Boolean = false,
    val askPartial: String = "",
    val recordsTab: Int = 0,
) {
    val stepCount: Int get() = plan?.steps?.size ?: 0
    val brief get() = plan?.steps?.getOrNull(progress.idx)
    val content get() = brief?.let { contents[it.n] }

    /** 通读模式下判断还有哪些步骤没写。 */
    val pending: List<Int> get() = plan?.steps?.map { it.n }?.filterNot { contents.containsKey(it) } ?: emptyList()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val lessons = LessonStore(app)
    private val generator = LessonGenerator()
    private val broker: RelayBroker get() = generator.relay

    private var work: Job? = null
    private var askWork: Job? = null
    private var ticker: Job? = null
    private val inflight = mutableSetOf<Int>()

    var state by mutableStateOf(UiState())
        private set

    init {
        viewModelScope.launch {
            store.flow.collect { saved -> state = state.copy(settings = saved) }
        }
        viewModelScope.launch {
            store.styleFlow.collect { saved -> state = state.copy(style = saved) }
        }
        viewModelScope.launch {
            store.themeFlow.collect { saved -> state = state.copy(themeMode = saved) }
        }
        viewModelScope.launch {
            broker.pending.collect { pending -> state = state.copy(relay = pending) }
        }
        refreshRecords()
    }

    fun setStyle(style: UiStyle) {
        state = state.copy(style = style)
        viewModelScope.launch { store.saveStyle(style) }
    }

    fun cycleTheme() {
        val next = ThemeMode.next(state.themeMode)
        state = state.copy(themeMode = next)
        viewModelScope.launch { store.saveTheme(next) }
    }

    fun onInput(text: String) {
        state = state.copy(input = text, error = null)
    }

    fun go(screen: Screen) {
        state = state.copy(screen = screen, feedback = null)
        if (screen == Screen.RECORDS) refreshRecords()
    }

    fun goRecords(tab: Int) {
        state = state.copy(screen = Screen.RECORDS, recordsTab = tab)
        refreshRecords()
    }

    fun setRecordsTab(tab: Int) {
        state = state.copy(recordsTab = tab)
    }

    fun back(): Boolean = when (state.screen) {
        Screen.PLAY -> {
            state = state.copy(screen = Screen.LESSON)
            true
        }

        Screen.LESSON -> {
            work?.cancel()
            broker.cancel()
            endLoading()
            persist()
            state = state.copy(
                screen = Screen.HOME,
                plan = null,
                contents = emptyMap(),
                finished = false,
                reading = false,
                recordId = "",
            )
            refreshRecords()
            true
        }

        Screen.STYLE, Screen.MODEL -> {
            state = state.copy(screen = Screen.SETTINGS)
            true
        }

        Screen.RECORDS, Screen.SETTINGS -> {
            state = state.copy(screen = Screen.HOME)
            true
        }

        Screen.HOME -> false
    }

    fun saveSettings(settings: LlmSettings) {
        viewModelScope.launch { store.save(settings) }
        state = state.copy(settings = settings, screen = Screen.SETTINGS)
    }

    // ---------------------------------------------------------------- 一课的生命周期

    fun startSample() {
        work?.cancel()
        endLoading()
        // 已经有一份没走完的示例就接着走，别每次点「示例」都堆一条新记录。
        val existing = state.records.firstOrNull { it.offline && !it.finished && it.plan.steps.isNotEmpty() }
        if (existing != null) {
            openRecord(existing)
            return
        }
        val now = System.currentTimeMillis()
        val record = LessonRecord(
            id = UUID.randomUUID().toString(),
            title = SampleLesson.plan.title,
            oneLine = SampleLesson.plan.oneLine,
            source = "内置示例",
            offline = true,
            createdAt = now,
            updatedAt = now,
            plan = SampleLesson.plan,
        )
        state = state.copy(
            plan = SampleLesson.plan,
            contents = SampleLesson.contents,
            screen = Screen.LESSON,
            offline = true,
            progress = Progress(),
            feedback = null,
            draft = "",
            notes = "",
            finished = false,
            reading = false,
            recordId = record.id,
            solved = 0,
            wrongs = 0,
            mistakes = emptyList(),
        )
        saveRecord(record)
    }

    fun start() {
        val text = state.input.trim()
        if (text.isBlank()) {
            state = state.copy(error = "先粘一道题，或者一段代码。")
            return
        }
        work?.cancel()
        work = viewModelScope.launch {
            val settings = store.current()
            if (!settings.canTalk) {
                state = state.copy(error = "还没配模型。去设置里填 Key，或者切到「手动接力」。下面先用示例演示。")
                startSample()
                return@launch
            }
            beginLoading("正在读题")
            try {
                val plan = generator.analyze(settings, text)
                val now = System.currentTimeMillis()
                val record = LessonRecord(
                    id = UUID.randomUUID().toString(),
                    title = plan.title,
                    oneLine = plan.oneLine,
                    source = text.take(4000),
                    offline = false,
                    createdAt = now,
                    updatedAt = now,
                    plan = plan,
                )
                state = state.copy(
                    plan = plan,
                    screen = Screen.LESSON,
                    offline = false,
                    progress = Progress(),
                    feedback = null,
                    draft = "",
                    notes = "",
                    finished = false,
                    reading = false,
                    recordId = record.id,
                    solved = 0,
                    wrongs = 0,
                    mistakes = emptyList(),
                )
                saveRecord(record)
                endLoading()
                loadStep(0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun loadStep(index: Int) {
        val plan = state.plan ?: return
        if (index !in plan.steps.indices) return
        state = state.copy(
            progress = Progress(idx = index),
            feedback = null,
            draft = "",
            finished = false,
        )
        persist()
        val brief = plan.steps[index]
        if (state.contents.containsKey(brief.n)) {
            prefetch(plan, index + 1)
            return
        }
        if (state.offline) return

        work?.cancel()
        work = viewModelScope.launch {
            beginLoading("正在写第 ${brief.n} 步")
            try {
                val settings = store.current()
                val content = generator.teach(settings, plan, brief, state.notes)
                state = state.copy(contents = state.contents + (brief.n to content))
                persist()
                endLoading()
                prefetch(plan, index + 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** 通读模式下补写某一步，不动游标。 */
    fun ensureStep(index: Int) {
        val plan = state.plan ?: return
        val brief = plan.steps.getOrNull(index) ?: return
        if (state.contents.containsKey(brief.n) || state.offline) return
        if (state.waitingStep == brief.n) return
        viewModelScope.launch {
            state = state.copy(waitingStep = brief.n, error = null)
            try {
                val settings = store.current()
                val content = generator.teach(settings, plan, brief, state.notes)
                state = state.copy(contents = state.contents + (brief.n to content), waitingStep = -1)
                persist()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(waitingStep = -1)
                fail(e)
            }
        }
    }

    private fun prefetch(plan: LessonPlan, index: Int) {
        if (state.offline) return
        val brief = plan.steps.getOrNull(index) ?: return
        if (state.contents.containsKey(brief.n) || !inflight.add(brief.n)) return
        viewModelScope.launch {
            try {
                val settings = store.current()
                val content = generator.teach(settings, plan, brief, state.notes)
                state = state.copy(contents = state.contents + (brief.n to content))
                persist()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                inflight.remove(brief.n)
            }
        }
    }

    // ---------------------------------------------------------------- 判定

    fun submit(value: String) {
        val content = state.content ?: return
        val brief = state.brief ?: return
        if (state.progress.canAdvance) return
        val local = Judge.judge(content.question, value)

        if (local == Verdict.CORRECT) {
            accept(value, content.onCorrect)
            return
        }
        val openEnded = Judge.isOpenEnded(content.question)
        if (!openEnded || !state.settings.canGrade) {
            reject(value, content, local, null)
            return
        }
        // 本地判不准，交给模型复核一次。批改失败就当没发生，不能把学生卡住。
        viewModelScope.launch {
            state = state.copy(grading = true)
            val grade = runCatching { generator.grade(state.settings, brief, content.question, value) }.getOrNull()
            state = state.copy(grading = false)
            if (grade?.verdict == Verdict.CORRECT) {
                accept(value, grade.note.ifBlank { content.onCorrect })
            } else {
                reject(value, content, grade?.verdict ?: local, grade?.note)
            }
        }
    }

    private fun accept(value: String, reply: String) {
        val already = state.progress.canAdvance
        state = state.copy(
            progress = state.progress.correct(),
            feedback = Feedback(Verdict.CORRECT, reply.ifBlank { "对。" }),
            draft = value,
            solved = if (already) state.solved else state.solved + 1,
        )
        persist()
    }

    private fun reject(value: String, content: StepContent, verdict: Verdict, note: String?) {
        val progressed = state.progress.wrong()
        val hint = content.hints.getOrNull(progressed.hintsShown - 1)
        val text = when {
            progressed.revealed ->
                "连错三次，这一步的答案先给你：${content.question.answer.ifBlank { content.question.expect }}"

            !note.isNullOrBlank() -> note
            verdict == Verdict.PARTIAL -> "差一点。$hint"
            hint != null -> hint
            else -> "再想想。"
        }
        val brief = state.brief
        val last = state.mistakes.lastOrNull()
        val duplicate = last != null && last.step == state.progress.idx && last.input == value
        val mistake = Mistake(
            step = state.progress.idx,
            title = brief?.title.orEmpty(),
            prompt = content.question.prompt,
            input = value,
            answer = content.question.answer.ifBlank { content.question.expect },
            at = System.currentTimeMillis(),
        )
        state = state.copy(
            progress = progressed,
            feedback = Feedback(if (progressed.revealed) Verdict.CORRECT else verdict, text),
            draft = value,
            notes = state.notes + "第 ${state.progress.idx + 1} 步答错：$value\n",
            wrongs = state.wrongs + 1,
            mistakes = if (duplicate) state.mistakes else (state.mistakes + mistake).takeLast(80),
        )
        persist()
    }

    fun askHint() {
        val content = state.content ?: return
        val progressed = state.progress.hint()
        val hint = content.hints.getOrNull(progressed.hintsShown - 1)
        state = state.copy(progress = progressed, feedback = hint?.let { Feedback(Verdict.PARTIAL, it) })
        persist()
    }

    fun onDraft(text: String) {
        state = state.copy(draft = text)
    }

    fun next() {
        val plan = state.plan ?: return
        val nextIndex = state.progress.idx + 1
        if (nextIndex >= plan.steps.size) {
            state = state.copy(finished = true)
            persist(finished = true)
            refreshRecords()
            return
        }
        loadStep(nextIndex)
    }

    // ---------------------------------------------------------------- 追问

    fun openAsk() {
        if (!state.settings.canTalk || state.settings.mode != ChannelMode.DIRECT) {
            state = state.copy(error = "追问需要直连模型。手动接力模式下每问一句都要人工转一趟，不划算。")
            return
        }
        state = state.copy(askOpen = true, askPartial = "")
    }

    fun closeAsk() {
        askWork?.cancel()
        askWork = null
        state = state.copy(askOpen = false, asking = false, askPartial = "")
    }

    fun onAskInput(text: String) {
        state = state.copy(askInput = text)
    }

    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty() || state.asking) return
        val plan = state.plan ?: return
        val brief = state.brief ?: return
        val content = state.content
        val history = state.askMessages.map { (if (it.fromUser) "user" else "assistant") to it.text }
        val stepMd = content?.blocks?.joinToString("\n\n") { it.md }.orEmpty()

        state = state.copy(
            askMessages = state.askMessages + AskMessage(true, text),
            askInput = "",
            asking = true,
            askPartial = "",
        )
        askWork?.cancel()
        askWork = viewModelScope.launch {
            try {
                val settings = store.current()
                val acc = StringBuilder()
                var lastPush = 0L
                generator.ask(
                    settings = settings,
                    system = Prompts.askSystem,
                    user = Prompts.askUser(plan, brief, stepMd, history, text),
                ) { delta ->
                    acc.append(delta)
                    val now = System.currentTimeMillis()
                    // 每个 token 都刷一次 UI 会卡；攒到 70ms 再推一次。
                    if (now - lastPush > 70) {
                        lastPush = now
                        state = state.copy(askPartial = acc.toString())
                    }
                }
                state = state.copy(
                    askMessages = state.askMessages + AskMessage(false, acc.toString().trim()),
                    asking = false,
                    askPartial = "",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    asking = false,
                    askPartial = "",
                    askMessages = state.askMessages + AskMessage(false, "没答上来：${e.message ?: e::class.simpleName}"),
                )
            }
        }
    }

    fun toggleReading() {
        state = state.copy(reading = !state.reading)
    }

    fun openPlay() {
        val md = state.content?.blocks?.joinToString("\n\n") { it.md }.orEmpty()
        state = state.copy(screen = Screen.PLAY, playCode = LessonDoc.firstCode(md))
    }

    // ---------------------------------------------------------------- 记录

    fun refreshRecords() {
        viewModelScope.launch {
            val list = runCatching { lessons.all() }.getOrDefault(emptyList())
            state = state.copy(records = list)
        }
    }

    fun openRecord(record: LessonRecord) {
        work?.cancel()
        endLoading()
        state = state.copy(
            plan = record.plan,
            contents = record.contents,
            screen = Screen.LESSON,
            offline = record.offline,
            progress = Progress(idx = record.cursor.coerceIn(0, maxOf(0, record.plan.steps.lastIndex))),
            feedback = null,
            draft = "",
            notes = "",
            finished = record.finished,
            reading = false,
            recordId = record.id,
            solved = record.solved,
            wrongs = record.wrongs,
            mistakes = record.mistakes,
        )
    }

    /** 从错题本跳到某一课的具体一步。 */
    fun openAt(record: LessonRecord, step: Int) {
        openRecord(record)
        loadStep(step.coerceIn(0, maxOf(0, record.plan.steps.lastIndex)))
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch {
            lessons.delete(id)
            refreshRecords()
        }
    }

    fun clearRecords() {
        viewModelScope.launch {
            lessons.clear()
            refreshRecords()
        }
    }

    private fun saveRecord(record: LessonRecord) {
        viewModelScope.launch {
            lessons.save(record)
            state = state.copy(
                records = (state.records.filterNot { it.id == record.id } + record).sortedByDescending { it.updatedAt }
            )
        }
    }

    /** 把当前状态写回记录。没有记录 id 时什么也不做（比如示例课刚进来的瞬间）。 */
    private fun persist(finished: Boolean = state.finished) {
        val plan = state.plan ?: return
        val id = state.recordId
        if (id.isBlank()) return
        val existing = state.records.firstOrNull { it.id == id }
        val record = (existing ?: LessonRecord(id = id, createdAt = System.currentTimeMillis())).copy(
            title = plan.title,
            oneLine = plan.oneLine,
            offline = state.offline,
            updatedAt = System.currentTimeMillis(),
            plan = plan,
            contents = state.contents,
            cursor = state.progress.idx,
            solved = state.solved,
            wrongs = state.wrongs,
            finished = finished,
            mistakes = state.mistakes,
        )
        viewModelScope.launch {
            lessons.save(record)
            state = state.copy(
                records = (state.records.filterNot { it.id == record.id } + record).sortedByDescending { it.updatedAt }
            )
        }
    }

    // ---------------------------------------------------------------- 接力

    fun relaySubmit(text: String) {
        if (text.isBlank()) {
            state = state.copy(error = "粘回来的内容是空的。")
            return
        }
        state = state.copy(error = null)
        if (!broker.submit(text)) {
            state = state.copy(error = "这一包已经收下了，等下一步再说。")
        }
    }

    fun relayCancel() {
        broker.cancel()
        state = state.copy(error = "这一步取消了。可以重新点开始。")
    }

    // ---------------------------------------------------------------- 导出

    fun exportMarkdown(): File? {
        val plan = state.plan ?: return null
        val text = LessonDoc.markdown(plan, state.contents)
        return writeFile(safeName(plan.title) + ".md", text)
    }

    fun exportHtml(): File? {
        val plan = state.plan ?: return null
        // A4 版式：手机版（100×178mm）交给打印机会被缩到左上角，一页挤不了几行。
        val text = LessonDoc.html(plan, state.contents, mobile = false)
        return writeFile(safeName(plan.title) + ".html", text)
    }

    private fun writeFile(name: String, text: String): File? = runCatching {
        val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
        File(dir, name).apply { writeText(text, Charsets.UTF_8) }
    }.getOrNull()

    private fun safeName(raw: String): String {
        val cleaned = raw.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")
        return cleaned.take(40).ifBlank { "lesson" }
    }

    // ---------------------------------------------------------------- 杂项

    fun dismissError() {
        state = state.copy(error = null)
    }

    fun toast(message: String) {
        state = state.copy(toast = message)
    }

    fun clearToast() {
        state = state.copy(toast = null)
    }

    fun cancel() {
        work?.cancel()
        broker.cancel()
        endLoading()
    }

    private fun beginLoading(label: String) {
        state = state.copy(loading = label, seconds = 0, error = null)
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                state = state.copy(seconds = state.seconds + 1)
            }
        }
    }

    private fun endLoading() {
        ticker?.cancel()
        ticker = null
        state = state.copy(loading = null)
    }

    private fun fail(e: Exception) {
        endLoading()
        val message = when (e) {
            is ChatException -> e.message ?: "请求失败"
            else -> e.message ?: e::class.simpleName ?: "未知错误"
        }
        state = state.copy(error = message)
    }
}
