package com.voyarina.caliedu

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voyarina.caliedu.core.Judge
import com.voyarina.caliedu.core.Prompts
import com.voyarina.caliedu.core.LessonDoc
import com.voyarina.caliedu.core.MdLesson
import com.voyarina.caliedu.core.Category
import com.voyarina.caliedu.core.Progress
import com.voyarina.caliedu.core.Qa
import com.voyarina.caliedu.core.QaParser
import com.voyarina.caliedu.core.Subject
import com.voyarina.caliedu.core.Track
import com.voyarina.caliedu.core.TrackDetector
import com.voyarina.caliedu.core.Verdict
import com.voyarina.caliedu.data.ChannelMode
import com.voyarina.caliedu.data.LessonRecord
import com.voyarina.caliedu.data.LessonStore
import com.voyarina.caliedu.data.Mistake
import com.voyarina.caliedu.data.PdfItem
import com.voyarina.caliedu.data.PdfLibrary
import com.voyarina.caliedu.data.PdfText
import com.voyarina.caliedu.data.Review
import com.voyarina.caliedu.data.PdfScanner
import com.voyarina.caliedu.data.SUPPORTED_HINT
import com.voyarina.caliedu.data.LlmSettings
import com.voyarina.caliedu.data.SettingsStore
import com.voyarina.caliedu.data.TextScale
import com.voyarina.caliedu.data.ThemeMode
import com.voyarina.caliedu.data.withoutMistake
import com.voyarina.caliedu.data.withoutMistakes
import com.voyarina.caliedu.llm.ChatException
import com.voyarina.caliedu.llm.LessonGenerator
import com.voyarina.caliedu.llm.RelayBroker
import com.voyarina.caliedu.model.Block
import com.voyarina.caliedu.model.Choice
import com.voyarina.caliedu.model.LessonPlan
import com.voyarina.caliedu.model.Question
import com.voyarina.caliedu.model.StepBrief
import com.voyarina.caliedu.model.StepContent
import com.voyarina.caliedu.sample.MathSample
import com.voyarina.caliedu.sample.Sample
import com.voyarina.caliedu.sample.SampleLesson
import com.voyarina.caliedu.ui.UiStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import java.io.File
import java.util.UUID

enum class Screen { HOME, RECORDS, SETTINGS, STYLE, MODEL, LIBRARY, LESSON, PLAY, REVIEW }

/** 从资料夹导入时最多填这么多字，再多上下文撑不住，钱也撑不住。 */
private const val MAX_IMPORT_CHARS = 12_000

/**
 * 直接粘进来的题面最多这么长。
 *
 * 首页的文件选择器一次能读 60 万字符，`start()` 原来对长度不设防——
 * 粘进一份 300KB 的 txt 就会发出去几十万 token，服务端回 400，
 * 用户看到的是一段 HTTP 报文。这里截断并明确告知，比原样发出去好。
 */
private const val MAX_INPUT_CHARS = 20_000

/** 开放题批改最多等这么久，超时就按本地判定走。 */
private const val GRADE_TIMEOUT_MS = 45_000L

/** 答案超过这个长度就不做成选择题了：选项装不下，判也判不准。 */
private const val CHOICE_ANSWER_MAX = 320

/** 一次最多拆几道长答案。拆得越多请求越多，长答案通常也就那几道。 */
private const val MAX_SPLIT_ANSWERS = 6

/** 自测题里的一道：问答 + 该做成选择题还是留成阅读节。 */
private data class QuizItem(val qa: Qa, val choice: Boolean)

/** 接力模式一批写几步。再多，单次输出就容易在 4k token 处被截断。 */
private const val RELAY_BATCH_STEPS = 6

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
    /**
     * 走完之后又翻回去看。
     *
     * 和 [finished] 分开：finished 是「这课算完了，记录上要留着」，
     * reviewing 是「完事了但人正在里面翻页」。回看时不能重新作答（passed 全给上），
     * 也不能因为翻到某一步就把记录改回未完成。
     */
    val reviewing: Boolean = false,
    /** 进回看之前停在哪一步、过了哪几步：回看翻页不该把这些推走，退出时按这个还原。 */
    val reviewResume: Int = 0,
    val reviewPassed: Set<Int> = emptySet(),
    val settings: LlmSettings = LlmSettings(),
    val style: UiStyle = UiStyle.PRINT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val records: List<LessonRecord> = emptyList(),
    val recordId: String = "",
    val relay: RelayBroker.Pending? = null,
    val grading: Boolean = false,
    val waitingStep: Int = -1,
    val reading: Boolean = false,
    /** 通读读到第几节（从 0 起）；-1 表示这节课还没通读过。 */
    val readingAt: Int = -1,
    /** 顶栏要不要直接放一个通读/单步切换：默认收在齿轮里。 */
    val readingBar: Boolean = false,
    /** 记录页顶部那张汇总卡（几门课、答对几题）要不要显示。 */
    val statsStrip: Boolean = true,
    /** 这一份讲义按什么标记分节；做题课用不上，恒为 auto。 */
    val mark: String = MdLesson.MARK_AUTO,
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
    /** 这一课是从哪进来的：退出要回原处，从记录进来的不该被送回首页。 */
    val lessonReturn: Screen = Screen.HOME,
    /** 复习是从首页还是记录页进来的，退出去要回到原处。 */
    val reviewReturn: Screen = Screen.RECORDS,
) {
    val stepCount: Int get() = plan?.steps?.size ?: 0
    val brief get() = plan?.steps?.getOrNull(progress.idx)
    val content get() = brief?.let { contents[it.n] }

    /**
     * 讲义型课程：整门课都没有提问，只有正文。
     *
     * 必须同时满足「离线」——讲义的正文存在 source 里，contents 是不落盘的；
     * 从问答生成的自测课也可能整门都是长答案节，但它的正文在 contents 里，
     * 当成讲义就会在保存时被清空。
     */
    val isDoc: Boolean
        get() = offline && plan?.steps?.let { steps -> steps.isNotEmpty() && steps.all { it.judgeKind == "none" } } ?: false

    /** 这一步能不能往下走：本步刚答对（或看过答案），或者这一课里它早就过了。 */
    val canAdvance: Boolean
        get() = isDoc || progress.canAdvance || progress.idx in passed ||
            // 模型偶尔漏写提问，这一步就没有可答的东西；不能因此把人钉在这一页。
            content?.question?.prompt?.isBlank() == true

    /** 通读模式下判断还有哪些步骤没写。 */
    val pending: List<Int> get() = plan?.steps?.map { it.n }?.filterNot { contents.containsKey(it) } ?: emptyList()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val lessons = LessonStore(app)
    private val generator = LessonGenerator()
    private val broker: RelayBroker get() = generator.relay

    private var work: Job? = null
    /**
     * 换课或退出时 +1。后台补写（预取、通读补写）拿着一份旧编号回来时，
     * 就知道自己已经过期了——否则旧课的正文会写进新课的 contents 里。
     */
    private var lessonSeq = 0
    private var askWork: Job? = null
    /** 开放题批改单独一个句柄：它没有正文可取消，但必须能被打断、也必须能超时。 */
    private var gradeWork: Job? = null
    private var ticker: Job? = null
    private val inflight = mutableSetOf<Int>()
    /** 正在补写的步号。单个 Int 挡不住「先点第 5 步再点第 8 步」。 */
    private val filling = mutableSetOf<Int>()
    /** 记录写满被淘汰时只提醒一次，否则每答一题弹一次。 */
    private var evictWarned = false

    var state by mutableStateOf(UiState())
        private set

    private fun switchLesson() {
        lessonSeq++
        inflight.clear()
        filling.clear()
        gradeWork?.cancel()
        gradeWork = null
    }

    /** 从记录页点进来的课，退出就该回记录页；讲义回资料页；其余都回首页。 */
    private fun lessonReturnOf(): Screen = when (state.screen) {
        Screen.RECORDS -> Screen.RECORDS
        Screen.LIBRARY -> Screen.LIBRARY
        else -> Screen.HOME
    }

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
            store.readingBarFlow.collect { saved -> state = state.copy(readingBar = saved) }
        }
        viewModelScope.launch {
            store.statsFlow.collect { saved -> state = state.copy(statsStrip = saved) }
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
        state = state.copy(textScale = TextScale.snap(scale))
    }

    fun commitTextScale() {
        viewModelScope.launch { store.saveScale(state.textScale) }
    }

    fun setReadingBar(on: Boolean) {
        state = state.copy(readingBar = on)
        viewModelScope.launch { store.saveReadingBar(on) }
    }

    fun setStatsStrip(on: Boolean) {
        state = state.copy(statsStrip = on)
        viewModelScope.launch { store.saveStats(on) }
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
            askWork?.cancel()
            askWork = null
            broker.cancel()
            endLoading()
            // 回看中直接退出：把游标和已过步数还原成进回看之前，别把「看了几页」当成绩写进记录。
            if (state.reviewing) {
                state = state.copy(
                    progress = Progress(idx = state.reviewResume),
                    passed = state.reviewPassed,
                )
            }
            persist()
            switchLesson()
            val backTo = state.lessonReturn
            state = state.copy(
                screen = backTo,
                // 出栈动画还有 280ms。这时候 lesson 的那几个可见字段一个都不能动：
                // 清 finished 会让「走完了」那一屏变回正文，清 reading 会从通读弹回单步，
                // 清 draft / passed / feedback 会让用户刚写完的答案和判定当场消失——
                // 于是滑出去的根本不是他刚才看的那个界面。
                // 下一课进来时 startSample / openNew / openRecord 都会整份重置。
                recordId = "",
                lessonReturn = Screen.HOME,
                // 退出时把加载态一并按死：不让「正在读题」跟着回到首页。
                loading = null,
                seconds = 0,
                // 追问是盖在上面的弹层，退出时必须收起；其余一概留给动画。
                askOpen = false,
                asking = false,
                askMessages = emptyList(),
                askInput = "",
                askPartial = "",
            )
            refreshRecords()
            true
        }

        Screen.STYLE, Screen.MODEL -> {
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

        Screen.RECORDS, Screen.SETTINGS, Screen.LIBRARY -> {
            state = state.copy(screen = Screen.HOME)
            true
        }

        Screen.HOME -> false
    }

    fun saveSettings(settings: LlmSettings) {
        viewModelScope.launch { store.save(settings) }
        state = state.copy(settings = settings, screen = Screen.SETTINGS)
    }

    // ---------------------------------------------------------------- 资料夹

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
            result.onSuccess { scanned ->
                // 单独挑进来的那几个跟文件夹无关，重新扫描不能把它们冲掉；
                // 手动移出列表的名单也一样——换了文件夹才作废。
                val sameTree = scanned.treeUri == state.library.treeUri
                val library = scanned.copy(
                    extraItems = state.library.extraItems,
                    hidden = if (sameTree) state.library.hidden else emptyList(),
                )
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
     * 单独挑几个文件收进资料夹，和文件夹扫出来的并列。
     * 只认 md / txt / tex（PDF 暂时关掉了）：别的收进来也只是占一行点不动。
     */
    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val ctx = getApplication<Application>()
        state = state.copy(libraryBusy = true, error = null)
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { describe(ctx, it) }
            }
            var library = state.library
            added.forEach { library = library.plus(it) }
            if (added.isNotEmpty()) store.saveLibrary(library)
            state = state.copy(library = library, libraryBusy = false)
            val skipped = uris.size - added.size
            when {
                added.isEmpty() ->
                    state = state.copy(error = "这几个文件收不进来：$SUPPORTED_HINT。")

                skipped > 0 -> toast("收了 ${added.size} 个，跳过 $skipped 个不支持的")
                else -> toast("收了 ${added.size} 个文件")
            }
        }
    }

    fun removeFile(item: PdfItem) {
        val library = state.library.minus(item)
        state = state.copy(library = library)
        viewModelScope.launch { store.saveLibrary(library) }
        toast("不再列在资料夹里，磁盘上的文件没动")
    }

    /**
     * 把一个 uri 读成资料夹条目；查不到名字或类型不支持就返回 null。
     * [keep] 为真时申请持久授权，重启后还能读——一次性导入不需要占着这个授权。
     */
    private fun describe(ctx: Context, uri: Uri, keep: Boolean = true): PdfItem? {
        if (keep) {
            // 有的提供方不支持持久授权，失败也要能继续用——只是重启后会失效。
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        var name = ""
        var size = 0L
        var modified = 0L
        runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fun column(key: String): String? =
                        cursor.getColumnIndex(key).takeIf { it >= 0 }?.let {
                            if (cursor.isNull(it)) null else cursor.getString(it)
                        }
                    name = column(OpenableColumns.DISPLAY_NAME).orEmpty()
                    size = column(OpenableColumns.SIZE)?.toLongOrNull() ?: 0L
                    // OpenableColumns 没有 LAST_MODIFIED，用 DocumentsContract 那一个的列名。
                    modified = column("last_modified")?.toLongOrNull() ?: 0L
                }
            }
        }
        if (name.isBlank()) name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        val item = PdfItem(name = name, uri = uri.toString(), size = size, modified = modified)
        return item.takeIf { it.supported && it.name.isNotBlank() }
    }

    private fun readText(ctx: Context, uri: Uri, limit: Int = Int.MAX_VALUE): String? = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().use { reader ->
                if (limit >= Int.MAX_VALUE) reader.readText()
                else {
                    val buffer = CharArray(limit)
                    var filled = 0
                    while (filled < limit) {
                        val read = reader.read(buffer, filled, limit - filled)
                        if (read <= 0) break
                        filled += read
                    }
                    String(buffer, 0, filled)
                }
            }
        }
    }.getOrNull()

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
            val text = withContext(Dispatchers.IO) { readText(ctx, Uri.parse(item.uri), MAX_IMPORT_CHARS * 2) }
            if (text.isNullOrBlank()) {
                state = state.copy(libraryBusy = false, error = "这个文件读不出内容。")
                return@launch
            }
            fillInput(item, text)
        }
    }

    /** 把 md 当讲义读：按小标题切节，整份留在记录里，不是塞进输入框。 */
    fun openAsDoc(item: PdfItem) {
        val ctx = getApplication<Application>()
        state = state.copy(libraryBusy = true, error = null)
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) { readText(ctx, Uri.parse(item.uri)) }
            state = state.copy(libraryBusy = false)
            if (raw.isNullOrBlank()) {
                state = state.copy(error = "这个文件读不出内容。")
                return@launch
            }
            openDoc(raw, item.name.substringBeforeLast('.'), item.uri)
        }
    }

    /**
     * 从系统文件选择器直接导入一个文件，按类型走各自的入口。
     * PDF 一律先抽文字层——以前是当纯文本读的，读出来全是乱码。
     */
    fun importFile(uri: Uri) {
        val ctx = getApplication<Application>()
        state = state.copy(libraryBusy = true, error = null)
        viewModelScope.launch {
            val item = withContext(Dispatchers.IO) { describe(ctx, uri, keep = false) }
            if (item == null) {
                state = state.copy(libraryBusy = false)
                toast(SUPPORTED_HINT)
                return@launch
            }
            when {
                item.isPdf -> {
                    val result = PdfText.extract(ctx, uri, 1, 10)
                    if (!result.ok) {
                        state = state.copy(libraryBusy = false, error = result.text)
                        return@launch
                    }
                    fillInput(item, result.text)
                }

                item.isMarkdown -> {
                    val raw = withContext(Dispatchers.IO) { readText(ctx, uri) }
                    state = state.copy(libraryBusy = false)
                    if (raw.isNullOrBlank()) {
                        state = state.copy(error = "这个文件读不出内容。")
                        return@launch
                    }
                    openDoc(raw, item.name.substringBeforeLast('.'))
                }

                else -> {
                    val raw = withContext(Dispatchers.IO) { readText(ctx, uri, MAX_IMPORT_CHARS * 2) }
                    if (raw.isNullOrBlank()) {
                        state = state.copy(libraryBusy = false, error = "这个文件读不出内容。")
                        return@launch
                    }
                    fillInput(item, raw)
                }
            }
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
        val sample: Sample = if (track == Track.MATH) MathSample else SampleLesson
        // 同一份示例只留一条记录：反复点示例不该堆出一串一模一样的课。
        val existing = state.records.firstOrNull {
            it.offline && it.plan.title == sample.plan.title && Track.of(it.plan.kind) == track
        }
        // 没走完的接着走；已经走完的重跑，但复用同一条记录。
        if (existing != null && !existing.finished) {
            openRecord(existing)
            return
        }
        switchLesson()
        val now = System.currentTimeMillis()
        val record = LessonRecord(
            id = existing?.id ?: UUID.randomUUID().toString(),
            title = sample.plan.title,
            oneLine = sample.plan.oneLine,
            source = "内置示例",
            offline = true,
            createdAt = existing?.createdAt ?: now,
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
            reviewing = false,
            reading = false,
            readingAt = -1,
            recordId = record.id,
            lessonReturn = lessonReturnOf(),
            solved = 0,
            wrongs = 0,
            mistakes = emptyList(),
            passed = emptySet(),
            waitingStep = -1,
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
            // 讲义现在住在资料页：从记录页点「示例」打开，退出也该落到它该在的地方。
            state = state.copy(lessonReturn = Screen.LIBRARY)
        }
    }

    private fun openDoc(raw: String, name: String, uri: String = "") {
        work?.cancel()
        endLoading()
        // 同一份原文再打开一次，接着上次读到的地方，而不是复制一份 176KB 的原文进记录。
        // 比字符串本身而不是长度：两篇长度相同的不同讲义会被误判成同一份。
        val same = state.records.firstOrNull { it.offline && it.source == raw }
        val mark = same?.mark ?: MdLesson.MARK_AUTO
        val doc = MdLesson.parse(raw, name, mark)
        if (same != null) {
            var target = refreshDoc(same, doc, raw)
            // 老记录没存 uri，资料页就只能按文件名猜。这次既然知道是哪个文件读进来的，补上。
            if (uri.isNotBlank() && target.uri != uri) {
                target = target.copy(uri = uri)
                saveRecord(target)
            }
            openRecord(target)
            return
        }
        switchLesson()
        if (doc.plan.steps.isEmpty()) {
            state = state.copy(error = "这份 Markdown 里没找到可分节的标记（# / ## / > ）。")
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
            mark = mark,
            uri = uri,
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
            reviewing = false,
            reading = false,
            readingAt = -1,
            recordId = record.id,
            lessonReturn = lessonReturnOf(),
            mark = mark,
            solved = 0,
            wrongs = 0,
            mistakes = emptyList(),
            passed = emptySet(),
        )
        saveRecord(record)
    }

    /**
     * 换一份讲义的分节标记。
     *
     * 自动判断认错的时候（整篇只有一节，或者被 `###` 切得太碎），在这里改一次，
     * 之后每次打开都按这个标记切。位置按「读到第几成」换算，不会一下跳回开头。
     */
    fun setSectionMark(mark: String) {
        val record = state.records.firstOrNull { it.id == state.recordId }
        if (record == null || record.source.isBlank()) {
            toast("这一课不是从讲义来的，没有分节标记可换。")
            return
        }
        val doc = MdLesson.parse(record.source, record.headline, mark)
        if (doc.plan.steps.isEmpty()) {
            toast("按「${MdLesson.labelOf(mark)}」切不出任何一节。")
            return
        }
        val oldTotal = state.plan?.steps?.size ?: 0
        val ratio = if (oldTotal <= 1) 0f else state.progress.idx.toFloat() / (oldTotal - 1)
        val newTotal = doc.plan.steps.size
        val cursor = (ratio * (newTotal - 1)).roundToInt().coerceIn(0, newTotal - 1)
        val at = if (state.readingAt >= 0) cursor else state.readingAt

        val updated = record.copy(
            title = doc.plan.title,
            oneLine = doc.plan.oneLine,
            plan = doc.plan,
            contents = doc.contents,
            mark = doc.mark,
            cursor = cursor,
            readingAt = at,
            finished = false,
            updatedAt = System.currentTimeMillis(),
        )
        state = state.copy(
            plan = doc.plan,
            contents = doc.contents,
            progress = Progress(idx = cursor),
            readingAt = at,
            mark = doc.mark,
            finished = false,
            passed = emptySet(),
        )
        saveRecord(updated)
        toast("分节标记：${MdLesson.labelOf(doc.mark)} · 共 $newTotal 节")
    }

    /**
     * 从讲义里的 Q/A 直接出一套自测题。
     *
     * 问题和答案都是原文，模型只补干扰项——比让它重写一遍准，也省得多。
     * 答案太长的问答不动它，原样留成阅读节；宁可少一道题，也不出一道判不准的题。
     */
    fun quizFromDoc(item: PdfItem) {
        if (!state.settings.canTalk) {
            toast("出题需要模型。先点「讲义」，问答会以遮罩形式让你自测。")
            return
        }
        val ctx = getApplication<Application>()
        state = state.copy(libraryBusy = true, error = null)
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) { readText(ctx, Uri.parse(item.uri)) }
            if (raw.isNullOrBlank()) {
                state = state.copy(libraryBusy = false, error = "这个文件读不出内容。")
                return@launch
            }
            val qas = withContext(Dispatchers.Default) { QaParser.parse(raw) }
            if (qas.isEmpty()) {
                state = state.copy(libraryBusy = false)
                toast("没识别到成对的 Q/A（形如 Q: … A: …，写成引用块也认）")
                return@launch
            }
            state = state.copy(libraryBusy = false)
            val settings = store.current()
            val name = item.name.substringBeforeLast('.').ifBlank { "问答自测" }

            // 一段话的答案没法直接做选择题：三个干扰项怎么造都判不准。
            // 先让模型只在原文里「取」，把它拆成两三道答案很短的小题；
            // 拆不动的（或者没配模型）留成阅读节——宁可少一道题，也不出一道判不准的题。
            val longPairs = qas.mapIndexedNotNull { index, qa ->
                if (qa.answer.length > CHOICE_ANSWER_MAX) (index + 1) to qa else null
            }
            beginLoading(
                if (longPairs.isEmpty()) "正在给 ${qas.size} 道问答出选项"
                else "正在把长答案拆成小题"
            )
            try {
                val splits = if (longPairs.isEmpty()) {
                    emptyMap()
                } else {
                    generator.splitLongAnswers(settings, name, longPairs.take(MAX_SPLIT_ANSWERS))
                }
                val items = buildList {
                    qas.forEachIndexed { index, qa ->
                        val sub = splits[index + 1]
                        when {
                            qa.answer.length <= CHOICE_ANSWER_MAX -> add(QuizItem(qa, true))
                            !sub.isNullOrEmpty() -> sub.forEach { add(QuizItem(it, true)) }
                            else -> add(QuizItem(qa, false))
                        }
                    }
                }
                val asking = items.mapIndexedNotNull { index, item ->
                    if (item.choice) (index + 1) to item.qa else null
                }
                state = state.copy(loading = "正在给 ${asking.size} 道问答出选项")
                val plan = quizPlan(item, raw, items, name)
                val options = generator.quizOptions(settings, plan.title, asking)
                openQuiz(plan, items, options)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private fun quizPlan(item: PdfItem, raw: String, items: List<QuizItem>, name: String): LessonPlan {
        val track = TrackDetector.detect(raw)
        return LessonPlan(
            title = name.take(40),
            kind = track.key,
            oneLine = "${items.count { it.choice }} 问",
            tags = Subject.guess(raw, name)?.let { listOf(it) }
                ?: Subject.normalize(emptyList(), track, name),
            steps = items.mapIndexed { index, item ->
                StepBrief(
                    n = index + 1,
                    title = item.qa.question.take(24),
                    stage = "自测",
                    goal = "",
                    judgeKind = if (item.choice) "choice" else "none",
                )
            },
            needsRun = false,
        )
    }

    private fun openQuiz(
        plan: LessonPlan,
        items: List<QuizItem>,
        options: Map<Int, List<String>>,
    ) {
        val contents = items.mapIndexed { index, item ->
            val n = index + 1
            val qa = item.qa
            val distractors = options[n].orEmpty()
                .filter { Judge.normalize(it) != Judge.normalize(qa.answer) }
                .distinctBy { Judge.normalize(it) }
                .take(3)
            if (distractors.isEmpty()) {
                // 没拿到选项（长答案，或模型没吐回来）：当阅读节，不硬出题。
                StepContent(
                    n = n,
                    blocks = listOf(Block("core", qa.answer)),
                    question = Question(),
                    hints = emptyList(),
                    onCorrect = "",
                    summary = emptyList(),
                )
            } else {
                StepContent(
                    n = n,
                    blocks = emptyList(),
                    question = Question(
                        prompt = qa.question,
                        kind = "choice",
                        choices = buildChoices(qa.answer, distractors, n),
                        answer = qa.answer,
                    ),
                    hints = emptyList(),
                    // 答对之后才给完整答案，答题前它只藏在选项里。
                    onCorrect = qa.answer,
                    summary = emptyList(),
                )
            }
        }.associateBy { it.n }

        val now = System.currentTimeMillis()
        val record = LessonRecord(
            id = UUID.randomUUID().toString(),
            title = plan.title,
            oneLine = plan.oneLine,
            offline = false,
            createdAt = now,
            updatedAt = now,
            plan = plan,
            contents = contents,
        )
        endLoading()
        state = state.copy(
            plan = plan,
            contents = contents,
            screen = Screen.LESSON,
            offline = false,
            progress = Progress(),
            feedback = null,
            draft = "",
            notes = "",
            finished = false,
            reviewing = false,
            reading = false,
            readingAt = -1,
            recordId = record.id,
            lessonReturn = lessonReturnOf(),
            solved = 0,
            wrongs = 0,
            mistakes = emptyList(),
            passed = emptySet(),
        )
        saveRecord(record)
        val reading = plan.steps.count { it.judgeKind == "none" }
        if (reading > 0) toast("$reading 条答案太长，留成了阅读节")
    }

    /** 正确项轮换位置：全放第一个，用户闭眼都能答对。 */
    private fun buildChoices(answer: String, distractors: List<String>, n: Int): List<Choice> {
        val items = distractors.toMutableList()
        items.add((n - 1) % (items.size + 1), answer)
        return items.map { Choice(label = it, value = it) }
    }

    fun start(track: Track? = null) {
        val raw = state.input.trim()
        if (raw.isBlank()) {
            state = state.copy(error = "先粘一道题，或者一段代码。")
            return
        }
        val text = if (raw.length > MAX_INPUT_CHARS) {
            val cut = raw.take(MAX_INPUT_CHARS)
            state = state.copy(input = cut)
            toast("题面 ${raw.length} 字，只取了前 $MAX_INPUT_CHARS 字")
            cut
        } else {
            raw
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
                openNew(generator.analyze(settings, text, track), text, settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** 拆题成功之后：建记录、进学习页、写讲义。手输的题和同类题共用这一段。 */
    private fun openNew(plan: LessonPlan, source: String, settings: LlmSettings) {
        switchLesson()
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
            // 必须清空：从「练一道同类的」进来时，上一课的 contents 还在，
            // 新第 1 步的编号同样是 1，会直接把旧课的正文顶上来。
            contents = emptyMap(),
            screen = Screen.LESSON,
            offline = false,
            progress = Progress(),
            feedback = null,
            draft = "",
            notes = "",
            finished = false,
            readingAt = -1,
            reviewing = false,
            reading = false,
            recordId = record.id,
            lessonReturn = lessonReturnOf(),
            solved = 0,
            wrongs = 0,
            mistakes = emptyList(),
            passed = emptySet(),
            waitingStep = -1,
            askOpen = false,
            asking = false,
            askMessages = emptyList(),
            askInput = "",
            askPartial = "",
        )
        saveRecord(record)
        endLoading()
        // loadStep 开头会 work?.cancel()；此刻 work 就是正在跑的这一条，先摘掉它。
        work = null
        // 接力模式下逐步写就是逐步复制粘贴：6 步课要人工往返 7 次。
        // 这里一次把整份讲义要回来，整节课只剩两次：拆题、写讲义。
        if (settings.mode == ChannelMode.RELAY) teachAllSteps(plan) else loadStep(0)
    }

    /**
     * 接力模式：一次写完所有步骤。
     *
     * 漏写的步骤不在这里自动补——补一次就是又一次复制粘贴，
     * 交给用户在学习页按需「补写」，一次失败不至于把人拖进第三轮。
     */
    private fun teachAllSteps(plan: LessonPlan) {
        val seq = lessonSeq
        work = viewModelScope.launch {
            beginLoading("正在写整份讲义 · 共 ${plan.steps.size} 步")
            try {
                val settings = store.current()
                // 一批最多 6 步：多数模型单次输出在 4k token 封顶，6 步正文差不多 3k。
                val batches = plan.steps.chunked(RELAY_BATCH_STEPS)
                val all = mutableMapOf<Int, StepContent>()
                batches.forEach { batch ->
                    if (seq != lessonSeq) return@launch
                    val part = generator.teachAll(settings, plan, batch, state.notes)
                    if (seq != lessonSeq) return@launch
                    if (part.isEmpty()) {
                        throw ChatException("第 ${batch.first().n}–${batch.last().n} 步没解析出来，可以在通读里点「补写」。")
                    }
                    all += part
                    state = state.copy(contents = all)
                    persist()
                }
                endLoading()
                loadStep(0)
                val missing = plan.steps.size - all.size
                if (missing > 0) {
                    state = state.copy(error = "有 $missing 步没写出来，可以在通读里点「补写」。")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
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
                // 同类题沿用原题的题型，不重新猜：模型改写时可能把代码题写成应用题。
                openNew(generator.analyze(settings, text, Track.of(plan.kind)), text, settings)
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
        // 换了步，上一段的追问就不再是「关于这一步」的了，留着会答串题。
        askWork?.cancel()
        askWork = null
        // 走完的课点目录跳进来，是回看，不是把这一课重新打开：
        // 已过的步全给上，题目不再要求作答，记录也保持「已完成」。
        val reviewing = state.reviewing || state.finished
        state = state.copy(
            progress = Progress(idx = index),
            // 讲义只有一条进度：逐步翻到第几节，通读也停在那一节。
            readingAt = if (state.isDoc) index else state.readingAt,
            feedback = null,
            draft = "",
            finished = false,
            reviewing = reviewing,
            passed = if (reviewing && state.passed.size < plan.steps.size) {
                plan.steps.indices.toSet()
            } else {
                state.passed
            },
            askOpen = false,
            asking = false,
            askMessages = emptyList(),
            askInput = "",
            askPartial = "",
        )
        // 回看只是翻着看，游标不能跟着走。
        if (!state.reviewing) persist()
        val brief = plan.steps[index]
        if (state.contents.containsKey(brief.n)) {
            prefetch(plan, index + 1)
            return
        }
        if (state.offline) return

        work?.cancel()
        val seq = lessonSeq
        work = viewModelScope.launch {
            beginLoading("正在写第 ${brief.n} 步")
            try {
                val settings = store.current()
                val content = generator.teach(settings, plan, brief, state.notes)
                if (seq != lessonSeq) return@launch
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
        // 用集合挡并发：只用一个 waitingStep 的话，先点第 5 步再点第 8 步，
        // 第 5 步写完会把 waitingStep 清成 -1，同一步就能被再点一次，白花一份 token。
        if (!filling.add(brief.n)) return
        val seq = lessonSeq
        viewModelScope.launch {
            state = state.copy(waitingStep = brief.n, error = null)
            try {
                val settings = store.current()
                val content = generator.teach(settings, plan, brief, state.notes)
                if (seq != lessonSeq) return@launch
                state = state.copy(contents = state.contents + (brief.n to content), waitingStep = -1)
                persist()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (seq != lessonSeq) return@launch
                state = state.copy(waitingStep = -1)
                fail(e)
            } finally {
                filling.remove(brief.n)
            }
        }
    }

    private fun prefetch(plan: LessonPlan, index: Int) {
        if (state.offline) return
        // 接力模式下每写一步都要人肉转一趟。提前把下一步的提示词弹出来，
        // 用户还没读完当前这一步，屏幕就被接力页盖住了。
        if (state.settings.mode != ChannelMode.DIRECT) return
        val brief = plan.steps.getOrNull(index) ?: return
        if (state.contents.containsKey(brief.n) || !inflight.add(brief.n)) return
        val seq = lessonSeq
        viewModelScope.launch {
            try {
                val settings = store.current()
                val content = generator.teach(settings, plan, brief, state.notes)
                if (seq != lessonSeq) return@launch
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
        // 批改还没回来就再点一次，会叠出第二个请求。
        if (state.grading) return
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
        val seq = lessonSeq
        val stepAtSubmit = state.progress.idx
        gradeWork?.cancel()
        gradeWork = viewModelScope.launch {
            state = state.copy(grading = true)
            // 读超时是 10 分钟。不设上限的话，一次挂死的批改会让「提交」一直灰着。
            val grade = withTimeoutOrNull(GRADE_TIMEOUT_MS) {
                runCatching { generator.grade(state.settings, brief, content.question, value) }.getOrNull()
            }
            // 这期间用户可能换了课、也可能翻到了别的步，结果不能再写回去。
            if (seq != lessonSeq || state.progress.idx != stepAtSubmit) {
                if (seq == lessonSeq) state = state.copy(grading = false)
                return@launch
            }
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
            // 公布答案不等于答对。用中性色，别让学生以为自己做对了。
            feedback = Feedback(if (progressed.revealed) Verdict.PARTIAL else verdict, text),
            draft = value,
            notes = state.notes + "第 ${state.progress.idx + 1} 步答错：$value\n",
            wrongs = state.wrongs + 1,
            mistakes = if (duplicate) state.mistakes else (state.mistakes + mistake).takeLast(80),
        )
        persist()
    }

    /** 回看中途想退出：回到「走完了」那一屏，记录仍然是已完成。 */
    fun endReview() {
        if (!state.reviewing) return
        state = state.copy(
            reviewing = false,
            finished = true,
            progress = Progress(idx = state.reviewResume),
            passed = state.reviewPassed,
        )
        persist(finished = true)
        refreshRecords()
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
            // 回看走到最后一节，回的是「走完了」那一屏，不是把这一课再结一次。
            if (state.reviewing) {
                state = state.copy(finished = true, reviewing = false)
                persist(finished = true)
                return
            }
            state = state.copy(finished = true)
            persist(finished = true)
            refreshRecords()
            return
        }
        loadStep(nextIndex)
    }

    /**
     * 走完之后回看。
     *
     * 记录里存着整份讲义，翻一遍不该再花钱重新生成。
     * 这里只是把游标挪回第一步，并把这课标成「已完成、正在翻」。
     */
    fun reviewSteps() {
        val plan = state.plan ?: return
        if (plan.steps.isEmpty()) return
        state = state.copy(
            finished = false,
            reviewing = true,
            // 回看是逐步翻题，不是接着读；上次停在通读也得回到单步。
            reading = false,
            reviewResume = state.progress.idx,
            reviewPassed = state.passed,
            progress = Progress(idx = 0),
            passed = plan.steps.indices.toSet(),
            draft = "",
            feedback = null,
        )
        // 这里不落盘：游标刚被归零，写下去记录页的进度条就当没学过。
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
        val next = !state.reading
        // 第一次进通读就停在正在做的那一步；之后沿用上次读到的地方。
        val at = if (next && state.readingAt < 0) state.progress.idx else state.readingAt
        state = state.copy(reading = next, readingAt = at)
        persist()
    }

    /** 通读滚到哪一节就记到哪一节。 */
    fun noteReadingAt(index: Int) {
        if (!state.reading) return
        val total = state.plan?.steps?.size ?: return
        if (total <= 0) return
        val at = index.coerceIn(0, total - 1)
        // 讲义没有作答，读到哪就是进度，三处（记录页 / 逐步 / 阅读器）共用一个数；
        // 做题课的通读位置单独记，不能因为翻了两页就把「做到第几步」推走。
        val moveCursor = state.isDoc && at != state.progress.idx
        if (at == state.readingAt && !moveCursor) return
        state = state.copy(
            readingAt = at,
            progress = if (state.isDoc) Progress(idx = at) else state.progress,
        )
        persist()
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
            reportStoreIssues()
        }
    }

    fun openRecord(record: LessonRecord) {
        if (record.plan.steps.isEmpty()) {
            state = state.copy(error = "这条记录里没有步骤，可能已经损坏。")
            return
        }
        work?.cancel()
        switchLesson()
        endLoading()
        // 讲义型记录只存了原文，正文在打开时重新切一遍
        val restored = if (record.contents.isEmpty() && isDocPlan(record)) {
            runCatching { MdLesson.parse(record.source, record.headline, record.mark).contents }
                .getOrDefault(emptyMap())
        } else {
            record.contents
        }
        state = state.copy(
            plan = record.plan,
            contents = restored,
            screen = Screen.LESSON,
            offline = record.offline,
            // 讲义只有「读到哪」这一个进度：旧记录里可能只存了 readingAt，
            // 取两者较大的，别让刚读的十几节退回去。
            progress = Progress(
                idx = (if (record.isDoc) maxOf(record.cursor, record.readingAt) else record.cursor)
                    .coerceIn(0, maxOf(0, record.plan.steps.lastIndex))
            ),
            feedback = null,
            draft = "",
            notes = "",
            finished = record.finished,
            reviewing = false,
            // 上次退出时停在通读，这次进来就还在通读，而且翻回读到的那一节。
            reading = record.reading,
            readingAt = record.readingAt,
            mark = record.mark,
            recordId = record.id,
            lessonReturn = lessonReturnOf(),
            solved = record.solved,
            wrongs = record.wrongs,
            mistakes = record.mistakes,
            waitingStep = -1,
            askOpen = false,
            asking = false,
            askMessages = emptyList(),
            askInput = "",
            askPartial = "",
            passed = when {
                record.passed.isNotEmpty() -> record.passed.toSet()
                // 旧记录只有 cursor：走到第 N 步，就当前面都过了，不然一打开全是没答的。
                record.cursor > 0 ->
                    (0 until record.cursor.coerceAtMost(record.plan.steps.size)).toSet()
                else -> emptySet()
            },
        )
    }

    /**
     * 讲义记录的 plan / contents 都是从 source 解析出来的派生数据。
     *
     * 解析规则改过之后，旧记录还停在旧的分节上——用户打开看到的仍是那堆
     * 被代码注释切碎的碎节。所以结构变了就按新解析重建一次，进度和错题留着。
     * 结构没变则一个字都不动：用户手动改过的分类不能被覆盖掉。
     */
    private fun refreshDoc(record: LessonRecord, doc: MdLesson.Doc, raw: String): LessonRecord {
        if (doc.plan.steps.isEmpty()) return record
        val sameShape = record.plan.steps.size == doc.plan.steps.size &&
            record.plan.steps.map { it.title } == doc.plan.steps.map { it.title }
        if (sameShape) return record

        val updated = record.copy(
            title = doc.plan.title,
            oneLine = doc.plan.oneLine,
            source = raw,
            plan = doc.plan,
            contents = doc.contents,
            mark = doc.mark,
            cursor = record.cursor.coerceIn(0, doc.plan.steps.lastIndex),
            passed = record.passed.filter { it < doc.plan.steps.size },
            updatedAt = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            lessons.save(updated)
            refreshRecords()
        }
        return updated
    }

    private fun isDocPlan(record: LessonRecord): Boolean = record.isDoc

    /** 从错题本跳到某一课的具体一步。 */
    fun openAt(record: LessonRecord, step: Int) {
        openRecord(record)
        // 从错题点进来是要看那道题，不是接着读——记录里的通读位置留着，但这一趟先回单步。
        state = state.copy(reading = false)
        loadStep(step.coerceIn(0, maxOf(0, record.plan.steps.lastIndex)))
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch {
            lessons.delete(id)
            refreshRecords()
            toast("已删除")
        }
    }

    fun clearRecords() {
        viewModelScope.launch {
            lessons.clear()
            state = state.copy(recordsFilter = "")
            refreshRecords()
        }
    }

    /** 删一条错题。只动错题本，不动这一课的进度和答题数。 */
    fun deleteMistake(recordId: String, mistake: Mistake) {
        val record = state.records.firstOrNull { it.id == recordId } ?: return
        val next = record.withoutMistake(mistake)
        if (next === record) return
        viewModelScope.launch {
            val evicted = lessons.save(next)
            reportStoreIssues(evicted)
            state = state.copy(records = state.records.map { if (it.id == next.id) next else it })
            toast("这条错题删了")
        }
    }

    fun clearMistakes() {
        val dirty = state.records.filter { it.mistakes.isNotEmpty() }
        if (dirty.isEmpty()) return
        viewModelScope.launch {
            var updated = state.records
            var evicted = 0
            dirty.forEach { record ->
                val next = record.withoutMistakes()
                evicted = maxOf(evicted, lessons.save(next))
                updated = updated.map { if (it.id == next.id) next else it }
            }
            reportStoreIssues(evicted)
            state = state.copy(records = updated)
            toast("错题本清空了")
        }
    }

    private fun saveRecord(record: LessonRecord) {
        viewModelScope.launch {
            val evicted = lessons.save(record)
            reportStoreIssues(evicted)
            state = state.copy(
                records = (state.records.filterNot { it.id == record.id } + record).sortedByDescending { it.updatedAt }
            )
        }
    }

    /**
     * 存储层出问题时说一次。
     *
     * 这三件事原来都是静默的：写盘失败、记录文件损坏、超出上限被淘汰。
     * 用户看到的现象分别是「白答了」「历史全没了」「最旧的一课不见了」，
     * 如果不说明，只会以为 App 有 bug。
     */
    private fun reportStoreIssues(evicted: Int = 0) {
        lessons.lastWriteError?.let { reason ->
            toast("记录没能写进磁盘：$reason")
            return
        }
        lessons.consumeQuarantine()?.let { name ->
            toast("上一次的记录文件读不出来，已备份为 $name")
            return
        }
        if (evicted > 0 && !evictWarned) {
            evictWarned = true
            toast("记录最多 ${LessonStore.MAX} 条，最旧的 $evicted 条已移出")
        }
    }

    /** 把当前状态写回记录。没有记录 id 时什么也不做（比如示例课刚进来的瞬间）。 */
    private fun persist(finished: Boolean = state.finished || state.reviewing) {
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
            reading = state.reading,
            readingAt = state.readingAt,
        )
        viewModelScope.launch {
            val evicted = lessons.save(record)
            reportStoreIssues(evicted)
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
        // 只取消 gate 不会走到 fail()：CancellationException 是被重抛的，
        // endLoading() 于是永远不执行，全屏浮层就停在「正在读题」上一直数秒。
        work?.cancel()
        work = null
        endLoading()
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

    private fun writeFile(name: String, text: String): File? {
        val dir = File(getApplication<Application>().cacheDir, "exports")
        return runCatching {
            dir.mkdirs()
            File(dir, name).apply { writeText(text, Charsets.UTF_8) }
        }.getOrElse { e ->
            // 原来返回 null 时调用方什么都不做，用户点了「导出」毫无反应。
            toast("导出失败：${e.message ?: "存储空间不足"}")
            null
        }
    }

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
        gradeWork?.cancel()
        broker.cancel()
        endLoading()
        // 在课里取消（写某一步时），要留一个「重试」出口；
        // 否则正文区会一直转圈，因为没有任何协程还在补它。
        if (state.screen == Screen.LESSON && state.content == null) {
            state = state.copy(error = "这一步停下了。可以重试。")
        }
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
