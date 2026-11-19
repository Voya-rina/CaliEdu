package com.caliedu

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caliedu.core.Judge
import com.caliedu.core.Prompts
import com.caliedu.core.LessonDoc
import com.caliedu.core.MdLesson
import com.caliedu.core.Category
import com.caliedu.core.Progress
import com.caliedu.core.Subject
import com.caliedu.core.Track
import com.caliedu.core.Verdict
import com.caliedu.data.ChannelMode
import com.caliedu.data.LessonRecord
import com.caliedu.data.LessonStore
import com.caliedu.data.Mistake
import com.caliedu.data.PdfItem
import com.caliedu.data.PdfLibrary
import com.caliedu.data.PdfText
import com.caliedu.data.Review
import com.caliedu.data.PdfScanner
import com.caliedu.data.LlmSettings
import com.caliedu.data.SettingsStore
import com.caliedu.data.ThemeMode
import com.caliedu.llm.ChatException
import com.caliedu.llm.LessonGenerator
import com.caliedu.llm.RelayBroker
import com.caliedu.model.LessonPlan
import com.caliedu.model.StepContent
import com.caliedu.sample.MathSample
import com.caliedu.sample.Sample
import com.caliedu.sample.SampleLesson
import com.caliedu.ui.UiStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class Screen { HOME, RECORDS, SETTINGS, STYLE, MODEL, LIBRARY, LESSON, PLAY, REVIEW }

/** 从资料夹导入时最多填这么多字，再多上下文撑不住，钱也撑不住。 */
private const val MAX_IMPORT_CHARS = 12_000

data class Feedback(val verdict: Verdict, val text: String)

/** 追问里的一条消息。 */
data class AskMessage(val fromUser: Boolean, val text: String)

/**
 * 复习队列的一条。渲染要用的字段全部拍平——
 * 复习过程中不应该再去翻记录，也不该因为记录被删就崩掉。
 * [at] 是这条错题的时间戳，配合 [step] 用来定位它。
 */
data class ReviewItem(
    val recordId: String,
    val course: String,
    val step: Int,
    val title: String,
    val prompt: String,
    val answer: String,
    val input: String,
    val major: String,
    val at: Long,
)

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
    /** 记录页选中的一级分类。放在这里，切到别的页面再回来不会被重置。 */
    val recordsFilter: String = "",
    val textScale: Float = 1f,
    val library: PdfLibrary = PdfLibrary(),
    val libraryBusy: Boolean = false,
    val reviewQueue: List<ReviewItem> = emptyList(),
    val reviewIdx: Int = 0,
    val reviewRevealed: Boolean = false,
    val reviewKnown: Int = 0,
    val reviewLost: Int = 0,
    val reviewDone: Boolean = false,
    /** 这一课里已经过关的步（从 0 起）。翻回去看一眼，不用把中间几步重答一遍。 */
    val passed: Set<Int> = emptySet(),
    /** 复习是从首页还是记录页进来的，退出去要回到原处。 */
    val reviewReturn: Screen = Screen.RECORDS,
) {
    val stepCount: Int get() = plan?.steps?.size ?: 0
    val brief get() = plan?.steps?.getOrNull(progress.idx)
    val content get() = brief?.let { contents[it.n] }

    /** 讲义型课程：没有提问，只有正文。靠 judgeKind 认，重启后也认得出。 */
    val isDoc: Boolean get() = plan?.steps?.firstOrNull()?.judgeKind == "none"

    /** 这一步能不能往下走：本步刚答对（或看过答案），或者这一课里它早就过了。 */
    val canAdvance: Boolean get() = isDoc || progress.canAdvance || progress.idx in passed

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
            store.scaleFlow.collect { saved -> state = state.copy(textScale = saved) }
        }
        viewModelScope.launch {
            store.libraryFlow.collect { saved -> state = state.copy(library = saved) }
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

    fun setTheme(mode: ThemeMode) {
        state = state.copy(themeMode = mode)
        viewModelScope.launch { store.saveTheme(mode) }
    }

    fun cycleTheme() {
        setTheme(ThemeMode.next(state.themeMode))
    }

    /** 拖动时只改内存，松手才落盘——否则每一帧都在写 DataStore。 */
    fun setTextScale(scale: Float) {
        state = state.copy(textScale = scale.coerceIn(0.9f, 1.4f))
    }

    fun commitTextScale() {
        viewModelScope.launch { store.saveScale(state.textScale) }
    }

    /** 设置页改成边改边存，所以这里不跳页面。 */
    fun updateSettings(settings: LlmSettings) {
        state = state.copy(settings = settings)
        viewModelScope.launch { store.save(settings) }
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

    fun setRecordsFilter(tag: String) {
        state = state.copy(recordsFilter = tag)
    }

    /**
     * 改一条记录的一级分类。
     *
     * 只动 tags，不动 updatedAt——整理一下分类不该把这条记录顶到「最近学习」的最前面。
     * 细分标签保留：用户改的是大类，没道理顺手把「数列」抹掉。
     */
    fun setCategory(id: String, major: String) {
        val record = state.records.firstOrNull { it.id == id } ?: return
        val tags = listOf(major) + record.plan.tags
            .drop(1)
            .filter { it != major && Subject.match(it) == null && it.length in 2..8 }
            .take(2)
        val updated = record.copy(plan = record.plan.copy(tags = tags))
        state = state.copy(
            records = state.records.map { if (it.id == id) updated else it },
            plan = if (state.recordId == id) updated.plan else state.plan,
        )
        viewModelScope.launch { lessons.save(updated) }
        toast("已归到「$major」")
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

        Screen.STYLE, Screen.MODEL, Screen.LIBRARY -> {
            state = state.copy(screen = Screen.SETTINGS)
            true
        }

        Screen.REVIEW -> {
            // 这里不 refreshRecords：内存里的记录已经跟着每次自评改过了，
            // 立刻回读磁盘反而可能抢在 save 之前，把最后一次结果读没了。
            state = state.copy(
                screen = state.reviewReturn,
                reviewQueue = emptyList(),
                reviewDone = false,
            )
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

    // ---------------------------------------------------------------- PDF 资料夹

    fun importFolder(uri: Uri) {
        val ctx = getApplication<Application>()
        state = state.copy(libraryBusy = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // 先拿到持久授权，否则重启后就读不到了
                    ctx.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                    PdfScanner.scan(ctx, uri)
                }
            }
            result.onSuccess { library ->
                store.saveLibrary(library)
                state = state.copy(library = library, libraryBusy = false)
            }.onFailure { e ->
                state = state.copy(libraryBusy = false, error = "读不了这个文件夹：${e.message ?: e::class.simpleName}")
            }
        }
    }

    fun rescanFolder() {
        val library = state.library
        if (!library.picked) return
        importFolder(Uri.parse(library.treeUri))
    }

    fun forgetFolder() {
        state = state.copy(library = PdfLibrary())
        viewModelScope.launch { store.saveLibrary(PdfLibrary()) }
    }

    /**
     * 资料夹里的文件拿来做题。
     * 纯文本直接读；PDF 交给本机 Python 抽文字层——扫描件抽不出就如实说。
     */
    fun useFile(item: PdfItem, startPage: Int = 1, pages: Int = 10) {
        if (!item.isText) {
            importPdf(item, startPage, pages)
            return
        }
        val ctx = getApplication<Application>()
        state = state.copy(libraryBusy = true, error = null)
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(Uri.parse(item.uri))?.use { stream ->
                        stream.bufferedReader().use { reader ->
                            val buffer = CharArray(MAX_IMPORT_CHARS * 2)
                            val read = reader.read(buffer)
                            if (read > 0) String(buffer, 0, read) else ""
                        }
                    }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                state = state.copy(libraryBusy = false, error = "这个文件读不出内容。")
                return@launch
            }
            fillInput(item, text)
        }
    }

    private fun importPdf(item: PdfItem, startPage: Int, pages: Int) {
        val ctx = getApplication<Application>()
        state = state.copy(libraryBusy = true, error = null)
        viewModelScope.launch {
            val result = PdfText.extract(ctx, Uri.parse(item.uri), startPage, pages)
            if (!result.ok) {
                state = state.copy(libraryBusy = false, error = result.text)
                return@launch
            }
            fillInput(item, result.text)
        }
    }

    private fun fillInput(item: PdfItem, raw: String) {
        val text = raw.trim()
        val kept = text.take(MAX_IMPORT_CHARS)
        state = state.copy(
            libraryBusy = false,
            screen = Screen.HOME,
            input = kept,
            error = null,
        )
        val extra = if (text.length > kept.length) "，只填了前 $MAX_IMPORT_CHARS 字" else ""
        toast("从「${item.name}」取了 ${text.length} 字$extra")
    }

    // ---------------------------------------------------------------- 一课的生命周期

    fun startSample(track: Track = Track.CODE) {
        work?.cancel()
        endLoading()
        // 已经有一份没走完的同类示例就接着走，别每次点「示例」都堆一条新记录。
        val existing = state.records.firstOrNull {
            it.offline && !it.finished && it.plan.steps.isNotEmpty() && Track.of(it.plan.kind) == track
        }
        if (existing != null) {
            openRecord(existing)
            return
        }
        val sample: Sample = if (track == Track.MATH) MathSample else SampleLesson
        val now = System.currentTimeMillis()
        val record = LessonRecord(
            id = UUID.randomUUID().toString(),
            title = sample.plan.title,
            oneLine = sample.plan.oneLine,
            source = "内置示例",
            offline = true,
            createdAt = now,
            updatedAt = now,
            plan = sample.plan,
        )
        state = state.copy(
            plan = sample.plan,
            contents = sample.contents,
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
            passed = emptySet(),
        )
        saveRecord(record)
    }

    /** 内置讲义示例：直接读 assets 里的 md，不走模型。 */
    fun startDocSample() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.assets.open("samples/abalone.md").bufferedReader().use { it.readText() }
                }.getOrNull()
            }
            if (raw.isNullOrBlank()) {
                state = state.copy(error = "内置讲义读不出来。")
                return@launch
            }
            openDoc(raw, "鲍鱼年龄预测 · 讲义")
        }
    }

    /** 从文件管理器挑到一份 .md，按讲义打开，不花 token。 */
    fun openMarkdown(raw: String, name: String) {
        if (raw.isBlank()) {
            state = state.copy(error = "这个 Markdown 是空的。")
            return
        }
        openDoc(raw, name)
    }

    private fun openDoc(raw: String, name: String) {
        work?.cancel()
        endLoading()
        val doc = MdLesson.parse(raw, name)
        if (doc.plan.steps.isEmpty()) {
            state = state.copy(error = "这份 Markdown 里没找到可分节的小标题（## ）。")
            return
        }
        val now = System.currentTimeMillis()
        val record = LessonRecord(
            id = UUID.randomUUID().toString(),
            title = doc.plan.title,
            oneLine = doc.plan.oneLine,
            // 讲义把全文存在 source 里：正文是解析出来的，没必要再存一份
            source = raw,
            offline = true,
            createdAt = now,
            updatedAt = now,
            plan = doc.plan,
        )
        state = state.copy(
            plan = doc.plan,
            contents = doc.contents,
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
            passed = emptySet(),
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
                openNew(generator.analyze(settings, text), text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** 拆题成功之后：建记录、进学习页、写第一步。手输的题和同类题共用这一段。 */
    private fun openNew(plan: LessonPlan, source: String) {
        val now = System.currentTimeMillis()
        val record = LessonRecord(
            id = UUID.randomUUID().toString(),
            title = plan.title,
            oneLine = plan.oneLine,
            source = source.take(4000),
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
            passed = emptySet(),
        )
        saveRecord(record)
        endLoading()
        // loadStep 开头会 work?.cancel()；此刻 work 就是正在跑的这一条，先摘掉它。
        work = null
        loadStep(0)
    }

    /**
     * 同类型再来一道。
     *
     * 只换情境和数，不换知识点与解题骨架，所以先让模型照原题改写出一道新题，
     * 再走同一条拆题流水线——不另起一套 prompt，也不降低后续讲义的稳定性。
     * 手动接力模式下要人肉转两趟，不划算，直接挡掉。
     */
    fun practiseSimilar() {
        val plan = state.plan ?: return
        // 接力模式要人肉转两趟（出题一趟、拆题一趟），不如让用户自己换一道。
        if (state.settings.mode != ChannelMode.DIRECT || !state.settings.configured) {
            state = state.copy(error = "出同类题需要直连模型。手动接力模式下不划算。")
            return
        }
        work?.cancel()
        work = viewModelScope.launch {
            beginLoading("正在出一道同类题")
            try {
                val settings = store.current()
                val text = generator.variant(settings, plan, state.notes)
                openNew(generator.analyze(settings, text), text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** 往回翻一步。已经过掉的步骤不会因为翻回来就要求重答。 */
    fun prev() {
        val idx = state.progress.idx - 1
        if (idx < 0) return
        loadStep(idx)
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
        val idx = state.progress.idx
        // 同一个关口只记一次：翻回来重答不会把「答对」累加。
        val already = state.progress.canAdvance || idx in state.passed
        state = state.copy(
            progress = state.progress.correct(),
            passed = state.passed + idx,
            feedback = Feedback(Verdict.CORRECT, reply.ifBlank { "对。" }),
            draft = value,
            solved = if (already) state.solved else state.solved + 1,
            // 这一步之前错过，现在答对了：订正，并把下次复习往后推一档。
            mistakes = state.mistakes.map {
                if (it.step == idx && !it.fixed) Review.afterCorrect(it) else it
            },
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
            // 连错三次把答案给出来，这一步就算过去了；否则离开再回来还得再错三次。
            passed = if (progressed.revealed) state.passed + state.progress.idx else state.passed,
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
        // 讲义型的「读过了」记在当前节上：目录里能看出读到哪，不必再问一遍。
        if (state.isDoc && state.progress.idx !in state.passed) {
            state = state.copy(passed = state.passed + state.progress.idx)
        }
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
                    system = Prompts.askSystem(Track.of(plan.kind)),
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

    // ---------------------------------------------------------------- 复习

    /**
     * 把到期的错题串成一条队列。
     *
     * 之前「该复习 N 条」只是跳到最到期的那一步，看完还得自己退回记录页找下一条；
     * 真正要的是一条能一路走完的队。
     */
    fun startReview() {
        val now = System.currentTimeMillis()
        val due = state.records
            .flatMap { record -> record.mistakes.filter { Review.isDue(it, now) }.map { record to it } }
            .sortedBy { Review.dueAt(it.second) }
        // 还没到期的日子也得能进来：先过一遍没订正的。
        // 否则装上新版当天根本看不到这个页面，只能等明天。
        val pairs = due.ifEmpty {
            state.records
                .flatMap { record -> record.mistakes.filter { !it.fixed }.map { record to it } }
                .sortedByDescending { it.second.at }
        }
        if (pairs.isEmpty()) {
            toast("没有要复习的错题。")
            return
        }
        state = state.copy(
            screen = Screen.REVIEW,
            reviewReturn = state.screen,
            reviewQueue = pairs.map { (record, mistake) ->
                ReviewItem(
                    recordId = record.id,
                    course = record.headline,
                    step = mistake.step,
                    title = mistake.title,
                    prompt = mistake.prompt,
                    answer = mistake.answer,
                    input = mistake.input,
                    major = Category.majorOf(record.plan),
                    at = mistake.at,
                )
            },
            reviewIdx = 0,
            reviewRevealed = false,
            reviewKnown = 0,
            reviewLost = 0,
            reviewDone = false,
        )
    }

    fun revealReview() {
        if (state.reviewIdx >= 0 && !state.reviewDone) state = state.copy(reviewRevealed = true)
    }

    /** 记住了往后推一档，没记住打回一天后。两个结果都要写回记录。 */
    fun gradeReview(known: Boolean) {
        val item = state.reviewQueue.getOrNull(state.reviewIdx) ?: return
        state.records.firstOrNull { it.id == item.recordId }?.let { record ->
            val mistakes = record.mistakes.map { mistake ->
                if (mistake.step == item.step && mistake.at == item.at) {
                    if (known) Review.afterCorrect(mistake) else Review.afterWrong(mistake)
                } else {
                    mistake
                }
            }
            val updated = record.copy(mistakes = mistakes)
            state = state.copy(
                records = state.records.map { if (it.id == record.id) updated else it },
                mistakes = if (state.recordId == record.id) mistakes else state.mistakes,
            )
            viewModelScope.launch { lessons.save(updated) }
        }

        val knownCount = state.reviewKnown + if (known) 1 else 0
        val lostCount = state.reviewLost + if (known) 0 else 1
        val next = state.reviewIdx + 1
        state = if (next >= state.reviewQueue.size) {
            state.copy(reviewKnown = knownCount, reviewLost = lostCount, reviewDone = true, reviewRevealed = false)
        } else {
            state.copy(reviewKnown = knownCount, reviewLost = lostCount, reviewIdx = next, reviewRevealed = false)
        }
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
        // 讲义型记录只存了原文，正文在打开时重新切一遍
        val restored = if (record.contents.isEmpty() && isDocPlan(record)) {
            runCatching { MdLesson.parse(record.source, record.headline).contents }.getOrDefault(emptyMap())
        } else {
            record.contents
        }
        state = state.copy(
            plan = record.plan,
            contents = restored,
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
            passed = when {
                record.passed.isNotEmpty() -> record.passed.toSet()
                // 旧记录只有 cursor：走到第 N 步，就当前面都过了，不然一打开全是没答的。
                record.cursor > 0 ->
                    (0 until record.cursor.coerceAtMost(record.plan.steps.size)).toSet()
                else -> emptySet()
            },
        )
    }

    private fun isDocPlan(record: LessonRecord): Boolean =
        record.plan.steps.firstOrNull()?.judgeKind == "none" && record.source.isNotBlank()

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
            state = state.copy(recordsFilter = "")
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
            contents = if (state.isDoc) emptyMap() else state.contents,
            cursor = state.progress.idx,
            solved = state.solved,
            wrongs = state.wrongs,
            finished = finished,
            mistakes = state.mistakes,
            passed = state.passed.sorted(),
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
