package com.caliedu.stepedu

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caliedu.stepedu.core.Judge
import com.caliedu.stepedu.core.Progress
import com.caliedu.stepedu.core.Verdict
import com.caliedu.stepedu.data.LlmSettings
import com.caliedu.stepedu.data.SettingsStore
import com.caliedu.stepedu.llm.ChatException
import com.caliedu.stepedu.llm.LessonGenerator
import com.caliedu.stepedu.model.LessonPlan
import com.caliedu.stepedu.model.StepContent
import com.caliedu.stepedu.sample.SampleLesson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Screen { HOME, LESSON }

data class Feedback(val verdict: Verdict, val text: String)

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
    val settingsOpen: Boolean = false,
) {
    val stepCount: Int get() = plan?.steps?.size ?: 0
    val brief get() = plan?.steps?.getOrNull(progress.idx)
    val content get() = brief?.let { contents[it.n] }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val generator = LessonGenerator()
    private var work: Job? = null
    private var ticker: Job? = null
    private val inflight = mutableSetOf<Int>()

    var state by mutableStateOf(UiState())
        private set

    init {
        viewModelScope.launch {
            store.flow.collect { saved -> state = state.copy(settings = saved) }
        }
    }

    fun onInput(text: String) {
        state = state.copy(input = text, error = null)
    }

    fun openSettings(open: Boolean) {
        state = state.copy(settingsOpen = open)
    }

    fun saveSettings(settings: LlmSettings) {
        viewModelScope.launch {
            store.save(settings)
            state = state.copy(settingsOpen = false)
        }
    }

    fun startSample() {
        work?.cancel()
        endLoading()
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
        )
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
            if (!settings.configured) {
                startSample()
                return@launch
            }
            beginLoading("正在读题")
            try {
                val plan = generator.analyze(settings, text)
                state = state.copy(
                    plan = plan,
                    screen = Screen.LESSON,
                    offline = false,
                    progress = Progress(),
                    feedback = null,
                    draft = "",
                    notes = "",
                    finished = false,
                )
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
                endLoading()
                prefetch(plan, index + 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                inflight.remove(brief.n)
            }
        }
    }

    fun submit(value: String) {
        val content = state.content ?: return
        val verdict = Judge.judge(content.question, value)
        if (verdict == Verdict.CORRECT) {
            state = state.copy(
                progress = state.progress.correct(),
                feedback = Feedback(Verdict.CORRECT, content.onCorrect),
                draft = value,
            )
            return
        }
        val progressed = state.progress.wrong()
        val hint = content.hints.getOrNull(progressed.hintsShown - 1)
        val text = when {
            progressed.revealed -> "连错三次，这一步的答案先给你：${content.question.answer.ifBlank { content.question.expect }}"
            hint != null -> hint
            else -> "再想想。"
        }
        state = state.copy(
            progress = progressed,
            feedback = Feedback(if (progressed.revealed) Verdict.CORRECT else Verdict.WRONG, text),
            draft = value,
            notes = state.notes + "第 ${state.progress.idx + 1} 步答错：$value\n",
        )
    }

    fun askHint() {
        val content = state.content ?: return
        val progressed = state.progress.hint()
        val hint = content.hints.getOrNull(progressed.hintsShown - 1)
        state = state.copy(
            progress = progressed,
            feedback = hint?.let { Feedback(Verdict.PARTIAL, it) },
        )
    }

    fun onDraft(text: String) {
        state = state.copy(draft = text)
    }

    fun next() {
        val plan = state.plan ?: return
        val nextIndex = state.progress.idx + 1
        if (nextIndex >= plan.steps.size) {
            state = state.copy(finished = true)
            return
        }
        loadStep(nextIndex)
    }

    fun back() {
        work?.cancel()
        endLoading()
        state = state.copy(screen = Screen.HOME, plan = null, contents = emptyMap())
    }

    fun dismissError() {
        state = state.copy(error = null)
    }

    fun cancel() {
        work?.cancel()
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
