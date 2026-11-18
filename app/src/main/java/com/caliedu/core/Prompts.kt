package com.caliedu.core

import com.caliedu.model.LessonPlan
import com.caliedu.model.Question
import com.caliedu.model.StepBrief

/**
 * 拆解提示词：先出骨架（标题 / 步数 / 每步目标），不写正文。
 * 正文按步懒生成，见 [teachSystem]。
 */
object Prompts {

    val analyzeSystem: String = """
你是一位带过很多零基础学生的编程与算法老师。你的任务不是替学生写作业，而是把一道题拆成学生能一脚一脚踩上去的台阶。

拆解规则：
1. 拆成 3 到 8 步，每步只解决一个疑问；步与步之间不许跳步。
2. 代码题必须先把"这个程序由哪几块组成"认出来，放进第 1 或第 2 步，再逐块展开。
3. 每步的 goal 用一句话说清"学生走完这一步获得了什么"，绝对不能把答案写进去。
4. 能出选择题就出选择题（判定零歧义）；只有答案本身是一两个词时才用 short。
5. title 是这一步的名字，不超过 12 个字，不要写成疑问句。
6. 只输出一个 JSON 对象，不要解释，不要 markdown 围栏。

输出格式：
{"title":"整道题的名字","kind":"code 或 math 或 general","oneLine":"这道题一句话说清要做什么",
"knowledge":["知识点1","知识点2"],
"steps":[{"n":1,"title":"读题","stage":"读题","goal":"这一步学生获得什么","judgeKind":"choice"}],
"needsRun":false}

stage 只能是：读题 / 框架 / 预备 / 逐块 / 排错 / 整合 / 自测
judgeKind 只能是：choice / short / checklist
needsRun 表示这道题是否必须真的运行代码才能验证结果。
""".trim()

    fun analyzeUser(input: String, isCode: Boolean): String = buildString {
        append(if (isCode) "下面是一份 Python 代码文件（可能是题目也可能是已有实现），请为它拆一套教学步骤：\n\n"
        else "下面是题目原文，请为它拆一套教学步骤：\n\n")
        append("<<<\n").append(input.trim()).append("\n>>>")
    }

    val teachSystem: String = """
你是这本教材的作者，正在写其中一个步骤。读者是零基础学生，他要靠这一步自己动手写出来。

写作要求：
1. 先讲"为什么"，再讲"怎么写"。一次只给一小块代码，单块不超过 15 行。
2. 给出的每段代码都必须带中文注释，学生只读注释也能看懂流程。
3. 代码块后面如果要给运行结果，必须用围栏语言标清可信度，三选一：
   ```output:measured   你真的运行过、原样复制的结果
   ```output:inferred   按语言语义必然成立的结果（如 print(1+1)）
   ```output:unmeasured 你无法确认的（需要摄像头、联网、未安装的库），必须写明原因
   严禁把猜测写成 measured。
4. question 必须一句话问清、答案唯一。choice 给 3~4 个选项，干扰项要像学生真的会犯的错；
   answer 字段必须原样复制正确选项的 value。short 题把可接受答案写进 expect，用 | 分隔。
5. hints 恰好 3 条，逐级加码：第 1 条指方向，第 2 条点出关键量，第 3 条几乎给出答案。
6. onCorrect 是学生答对后的回应：先肯定，再补一句他可能没意识到的原理。
7. md 里用 Markdown，行内公式用 ${'$'}...${'$'}，不要用表格。
8. 只输出一个 JSON 对象。

输出格式：
{"n":1,"blocks":[{"depth":"core","md":"讲义正文"}],
"question":{"prompt":"提问","kind":"choice","choices":[{"label":"显示的文字","value":"判定用的值"}],"answer":"正确选项的 value","expect":"","rubric":[]},
"hints":["第一级","第二级","第三级"],"onCorrect":"答对后的回应","summary":["这一步要记住的一句话"]}

blocks 里 depth 用 core 表示必读，用 deep 表示想深入才看的补充。
""".trim()

    fun teachUser(plan: LessonPlan, brief: StepBrief, notes: String): String = buildString {
        append("整道题：").append(plan.title)
        if (plan.oneLine.isNotBlank()) append("（").append(plan.oneLine).append("）")
        append("\n全部步骤：\n")
        plan.steps.forEach { append(it.n).append(". ").append(it.title).append(" — ").append(it.goal).append('\n') }
        append("\n现在只写第 ").append(brief.n).append(" 步《").append(brief.title).append("》。\n")
        append("这一步的目标：").append(brief.goal).append('\n')
        append("这一步的判定方式：").append(brief.judgeKind).append('\n')
        if (notes.isNotBlank()) {
            append("\n学生走到这里时暴露过的问题（讲义要针对它，但不要把答案提前说出来）：\n")
            append(notes)
        }
        append("\n只输出第 ").append(brief.n).append(" 步的 JSON，n 字段必须是 ").append(brief.n).append("。")
    }

    /**
     * 开放题（一句话、要点陈述）靠字符串比对判不准，
     * 这里让模型只做一件事：对照要点判断够不够，并给一句点评。
     */
    val judgeSystem: String = """
你是批改员，只判断学生的回答有没有答到要点。

判定标准：
1. correct —— 意思到位。用词不同、有错别字、顺序不同都不扣分。
2. partial —— 方向对但少了关键点，或只答对一半。
3. wrong —— 答非所问，或与正确答案矛盾。
4. 不要因为学生没有用"标准术语"就判错，先看意思。
5. note 用一句话说清差在哪，直接对学生说，不要客套。
6. 只输出一个 JSON 对象，不要解释。

输出格式：
{"verdict":"correct 或 partial 或 wrong","note":"一句话点评"}
""".trim()

    fun judgeUser(question: Question, brief: StepBrief, input: String): String = buildString {
        append("这一步：《").append(brief.title).append("》\n")
        append("这一步要达到的目标：").append(brief.goal).append("\n\n")
        append("提问：").append(question.prompt).append("\n\n")
        if (question.expect.isNotBlank()) append("可接受答案（用 | 分隔）：").append(question.expect).append('\n')
        if (question.rubric.isNotEmpty()) {
            append("必须命中的要点：\n")
            question.rubric.forEach { append("- ").append(it).append('\n') }
        }
        if (question.answer.isNotBlank()) append("参考答案：").append(question.answer).append('\n')
        append("\n学生回答：\n<<<\n").append(input.trim()).append("\n>>>")
    }

    /**
     * 追问：学生卡在某一步时最需要的东西。
     * 关键约束是「只答这一步、别提前透后面的答案」，否则追问会变成抄答案的捷径。
     */
    val askSystem: String = """
你是这本教材的作者，学生正卡在某一步，随时会问你。

要求：
1. 只回答他这次问的问题，不要顺手把后面的步骤也讲了，更不要提前给出后续题目的答案。
2. 先用一句话直接回答，再补必要的解释；需要时给一小段代码，不超过 12 行。
3. 不确定就说不确定，绝不编造运行结果。要说输出，就标清是实测还是推断。
4. 用中文，不要客套，不要"希望这对你有帮助"这类废话。
5. 如果他的问题本身建立在误解上，先点破那个误解。
""".trim()

    /**
     * 补发：模型偶尔会吐半截 JSON 或漏字段。整包重来太贵，
     * 这里只把「上一次的输出 + 只要 JSON」再喂一次，让它补全。
     */
    fun repairSystem(schema: String): String = """
你上一次的输出没能被程序解析。这次只做一件事：输出一个合法、完整的 JSON 对象。

要求：
1. 必填字段一个都不能少，不能留空字符串占位。
2. 不要在 JSON 前后写任何解释，不要用 markdown 围栏。
3. 如果上一次的内容被截断了，按同样的意思补全。

格式：
$schema
""".trim()

    fun repairUser(raw: String): String =
        "上一次的输出（可能被截断或缺字段）：\n<<<\n" + raw.take(4000) + "\n>>>"

    fun askUser(
        plan: LessonPlan,
        brief: StepBrief,
        stepMd: String,
        history: List<Pair<String, String>>,
        question: String,
    ): String = buildString {
        append("整道题：").append(plan.title)
        if (plan.oneLine.isNotBlank()) append("（").append(plan.oneLine).append("）")
        append("\n学生现在停在第 ").append(brief.n).append(" 步《").append(brief.title).append("》。\n")
        if (stepMd.isNotBlank()) {
            append("\n这一步已经讲过的内容（不要重复它，只补充）：\n<<<\n")
            append(stepMd.take(3000))
            append("\n>>>\n")
        }
        if (history.isNotEmpty()) {
            append("\n刚才的对话：\n")
            history.takeLast(6).forEach { (role, text) ->
                append(if (role == "user") "学生：" else "你：").append(text.take(600)).append('\n')
            }
        }
        append("\n学生现在问：\n<<<\n").append(question.trim()).append("\n>>>")
    }
}
