package com.voyarina.caliedu.core

import com.voyarina.caliedu.model.LessonPlan
import com.voyarina.caliedu.model.Question
import com.voyarina.caliedu.model.StepBrief

/**
 * 三套提示词，按 [Track] 分叉。
 *
 * 共同的骨架只有一句：每一步必须是一个「能独立判对错的关口」。
 * 数学题是一条推导链，链上有十几个关节就写十几步，一道口算两步就够；
 * 概念题反过来——它是「一个问题」，不是「一门课」，所以有硬性上限：
 * 一句「讲一下 X」最多 4 步，再往上就是在替学生编教材目录了。
 */
object Prompts {

    // ------------------------------------------------------------------ 拆题

    fun analyzeSystem(track: Track): String = buildString {
        // append("学生发来的可能是一道题，也可能只是一句「讲一下 X」。")
        // append("你的任务不是替他把内容写完，而是先看清他真正要什么，再把这件事拆成他能一脚一脚踩上去的台阶。\n\n")
        append("你的任务是聚焦问题的核心，找出学生可能的卡点，并提供逐步引导以让学生真正理解\n\n")        
        append(
            when (track) {
                Track.CODE -> CODE_SPLIT
                Track.MATH -> MATH_SPLIT
                Track.GENERAL -> GENERAL_SPLIT
            }
        )
        append("\n\n").append(stepRule(track))
        append("\n\n").append(SOURCE_RULE)
        append("\n\n").append(analyzeFormat(track))
        // 规模放到最后再钉一遍：模型对「输出格式之前的最后一段」最敏感。
        if (track == Track.GENERAL) append("\n\n").append(GENERAL_SCALE)
    }

    fun analyzeUser(input: String, track: Track): String = buildString {
        append(
            when (track) {
                Track.CODE -> "下面是一道编程题或代码，请为它拆一套教学步骤：\n\n"
                Track.MATH -> "下面是一道数学题，请为它拆一套推导步骤：\n\n"
                // Track.GENERAL -> "下面是学生发来的一个问题或一段材料。它可能只有一句话——" +
                //     "那也只回答这一句话，不要扩成一门课。请为它拆一套讲解步骤：\n\n"
                Track.GENERAL ->  "下面是学生发来的一个问题或一段材料，请为它拆一套讲解步骤：\n\n"
            }
        )
        // 把「这道题有多大」直接量出来放在用户这一侧。系统提示词里的上限是通则，
        // 模型对贴着输入的这一句更敏感。
        if (track == Track.GENERAL) {
            val body = input.trim()
            when {
                body.length <= 80 -> append("（这是 ${body.length} 字的一句话提问，按 3~4 步处理。）\n\n")
                body.length >= 600 -> append("（材料较长，只回答它真正在问的那一个问题，不要逐段展开。）\n\n")
            }
        }
        append("<<<\n").append(input.trim()).append("\n>>>")
    }

    private val CODE_SPLIT = """
这道题是编程题或代码,拆的时候按「结构」走，不按「语法点」走：
1. 学生要学会看程序的骨架.先让学生认出这份程序被切成几块,数据从哪来、变成了什么、交给谁。
2. 然后逐块展开。每一块只回答一个问题：“这一行/这个函数到底在干什么”，例如“这里为什么用 max 而不是自己写比较”。
3. 排错步只指向真实会发生的错，例如形状不对、索引越界、类型不匹配、静默丢数据、随机没固定、梯度没清零。不要编造不会发生的问题。
4. 允许且鼓励问这类“必须理解代码才能答”的问题：为什么这里用 while 而不是 for？为什么这里先判断再取值？
5. 代码很长时，宁可块数少、每块拆到核心逻辑，也不要每 5 行切一刀走形式。
""".trim()

    private val MATH_SPLIT = """
这道题是数学题。

不要按「讲概念 → 举例子 → 做练习」拆。数学题是一条推导链，按链上的关节拆：
1. 条件怎么用 —— 题目给的每个条件，各自能翻译成什么式子。
2. 选哪条路 —— 为什么走这条（配方 / 换元 / 分部 / 同除 / 取对数 / 构造），以及另一条为什么走不通。
3. 每一步变形 —— 一次只做一次变形。把「代入并化简」和「解出未知量」拆成两步。
4. 结果可不可信 —— 验算、值域检查、边界情况、单位。

硬要求：
- goal 里不许出现答案，也不许把后面的式子提前列出来。
- 不许出现「套公式算一下」「按常规方法求解」这种把整段推理塞进一句话的空步。
- 一步里如果藏着两个各自能做错的判断，就必须拆成两步。
- 纯计算题三步就够，别硬凑；证明题按逻辑链走，二十步也不嫌多。
- 例外：如果输入根本不是一道题，而是一句「讲一下 X」「X 是什么」的概念请求，
  按概念讲，3~4 步（定义 → 为什么这样定义 / 关键性质 → 一个最小例子），不要硬造一条推导链。
""".trim()

    private val GENERAL_SPLIT = """
这是一道概念题 / 简答题，没有式子也没有代码。

先分清它在问哪一种：
A. 解释一个概念 ——「讲一下动态规划」「什么是熵」
B. 比较两个相近的东西 ——「贪心和 DP 有什么区别」
C. 说明一个流程或机制 ——「TCP 三次握手怎么走」
D. 分点论述 ——「论述……的三点原因」

不管哪一种，拆的都是「回答这个问题要想通哪几层」，不是「这个话题有哪些知识点」：
1. 第一层直接回答它（定义 / 分界 / 第一步），不要先讲「我们来看题目在问什么」。
2. 后面每一层都要建立在前一层上：去掉它，后面的结论就不成立。
3. 最后一层收束成一段能直接说出口的回答。

硬要求：
- A / B / C 三类一共 3~4 步，D 类最多 6 步。
- 不许有元步骤：「明确题目要求」「理清思路」「确定讲解范围」「汇总输出答案」都不算关口。
  学生要的是答案，不是你怎么备课。
- 一次对比就是一步，不许把一个对比拆成好几步（区分 A 与 B、A 与 C、A 与 D 各占一步）。
- goal 只写这一层的结论要点，不超过 40 字。写满一屏，说明你在往一步里塞一整章。
""".trim()

    /**
     * 概念题的规模上限。放在输出格式之后再说一遍——
     * 这是「讲一下动态规划」被拆成 12 步的直接原因：
     * 原提示词只有结构没有上限，模型就按教材目录拆。
     */
    private val GENERAL_SCALE = """
规模（硬性，超了就算不合格）：
- 一次提问最多 6 步；「讲一下 X」「什么是 X」这类 3~4 步。
- 超过上限时按这个顺序砍：先删元步骤（明确要求 / 理清思路 / 总结全文），
  再合并同一个判断的不同说法，最后才合并相邻的两层。
- knowledge 只留 2~4 个，取这条主线真正用到的概念，不要把这个主题的目录抄进来。
- oneLine 写「这道题要回答什么」，不要写「分几步讲」「面向零基础」这类教学安排。
""".trim()

    /**
     * 输入经常是从课件/PDF 里整段抽出来的，里面本来就带着解析。
     * 不点破这一点，模型会把"照抄解析"当成任务。
     */
    private val SOURCE_RULE = """
如果输入是从课件或 PDF 里抽出的一整段文字，里面可能夹着例题、答案、解析、教学指导。
把它当作判断重点的依据，但不要把解析里的结论直接抄成某一问的答案，也不要原样复述整段教材。
""".trim()

    private fun stepRule(track: Track): String = if (track == Track.GENERAL) """
步数：按上面的上限走（3~6 步），不按「这个主题有多少知识点」走。
唯一的标准是——每一步都要对应一个能独立判对错的关口。
如果你写不出一句「走完这一步，学生应该能答出什么」，这一步就该和相邻的合并。
""".trim() else """
步数：不设上下限。
唯一的标准是——每一步都要对应一个能独立判对错的关口。
如果你写不出一句「走完这一步，学生应该能答出什么」，这一步就该和相邻的合并。
反过来，如果某一步里有两个各自能做错的判断，就该拆开。
""".trim()

    private fun analyzeFormat(track: Track): String {
        val stages = when (track) {
            Track.CODE -> listOf("读题", "框架", "预备", "逐块", "排错", "整合", "自测")
            Track.MATH -> listOf("读题", "建模", "定路", "推导", "检验", "收束")
            // 概念题没有「读题」：那一步在模型手里总会变成「明确题目要求」的元步骤。
            Track.GENERAL -> listOf("概念", "辨析", "推理", "结论")
        }
        val kind = when (track) {
            Track.CODE -> "code"
            Track.MATH -> "math"
            Track.GENERAL -> "general"
        }
        val countRule = when (track) {
            Track.GENERAL -> "3~6 项（「讲一下 X」这类 3~4 项）"
            else -> "有几个关口就写几项"
        }
        val first = stages.first()
        val second = stages.getOrElse(1) { first }
        // goal 是这份 JSON 里最容易被写成一整章的地方：概念题先把它的长度钉在示例里。
        val goalHint = when (track) {
            Track.GENERAL -> "这一层得出的结论，不超过 40 字"
            else -> "学生走完这一步获得了什么"
        }
        return """
只输出一个 JSON 对象，不要解释，不要 markdown 围栏。

{"title":"整道题的名字","kind":"$kind","oneLine":"这道题一句话说清要做什么",
"tags":["分类标签1","分类标签2"],
"knowledge":["知识点1","知识点2"],
"steps":[
{"n":1,"title":"第一步的名字，不超过 12 个字，不要写成疑问句","stage":"$first","goal":"$goalHint","judgeKind":"choice"},
{"n":2,"title":"第二步的名字","stage":"$second","goal":"……","judgeKind":"short"}
],
"needsRun":false}

steps 是数组，$countRule，不要只写一项也不要为了凑数硬拆。
tags 用来给记录页筛选。第一个元素必须原样照抄下面这份固定分类里的某一个，不要改写、不要新造：
  高等数学 / 线性代数 / 概率统计 / 离散数学 / C语言 / C++ / Python / Java / 数据结构 / 算法 / 机器学习 / 数据处理 / 其他
第二个到第三个可以更细（每个 2~8 个字，如「数列」「分部积分」「链表」「动态规划」），也可以不写。
stage 只能取：${stages.joinToString(" / ")}
judgeKind 只能取：choice / short / checklist。优先 choice：学生在手机上打字，少一个符号都是减负。
只有答案是一个数、一个单项式或一句话（≤ 20 字）时才用 short；
要写一整条长式子或一段话，就改成 choice（正确结论 + 两三个常见错法），
或者把提问缩小到「这一项的系数是多少」。checklist 只用于确实要分点回答的题，要点不超过 4 条。
needsRun：只有必须真的运行代码才能验证结果时才填 true，数学题一律 false。
""".trim()
    }

    // ------------------------------------------------------------------ 写一步

    fun teachSystem(track: Track): String = buildString {
        append(
            when (track) {
                Track.CODE -> CODE_TEACH
                Track.MATH -> MATH_TEACH
                Track.GENERAL -> GENERAL_TEACH
            }
        )
        append("\n\n").append(questionRule(track))
        append("\n\n").append(MARKUP_RULE)
        append("\n\n").append(stepFormat)
    }

    private val CODE_TEACH = """
你是这本教材的作者，正在写其中一个步骤。读者是零基础学生，他要靠这一步自己动手写出来。

写作要求：
1. 先讲「为什么」，再讲「怎么写」。一次只给一小块代码，单块不超过 15 行。
2. 给出的每段代码都必须带中文注释，学生只读注释也能看懂流程。
3. 代码块后面如果要给运行结果，必须用围栏语言标清可信度，三选一：
   ```output:measured   你真的运行过、原样复制的结果
   ```output:inferred   按语言语义必然成立的结果（如 print(1+1)）
   ```output:unmeasured 你无法确认的（需要摄像头、联网、未安装的库），必须写明原因
   严禁把猜测写成 measured。
4. md 里用 Markdown，行内公式用 ${'$'}...${'$'}，不要用表格。
5. hints 恰好 3 条，逐级加码：第 1 条指方向，第 2 条点出关键量，第 3 条几乎给出答案。
""".trim()

    private val MATH_TEACH = """
你是这本教材的作者，正在写一道数学题里的某一个推导步骤。读者是零基础学生。

这一步的写法，严格照这个顺序，不要多写：
1. 第一行是一个「### 动作名」，四到八个字，说清这一步在干什么（如「把条件写成方程」「两边同加一」「换元去根号」）。
2. 接一段不超过 60 字的话：为什么现在要做这个动作，不做会卡在哪。
3. 然后是推导。凡是式子，必须单独成行、用 ${'$'}${'$'}...${'$'}${'$'} 包起来，一行只推一步。
4. 最后一句：这一步得到了什么，它为什么成立。

硬要求：
- 全文（不含公式行）不超过 220 字。数学讲义写得长就是没想清楚。
- 不写代码块，不写「我们来看一个例子」，不写「综上所述」「接下来我们」这类过渡废话。
- 不许在这一步把最终答案写出来，除非这一步就是最后一步。
- 公式只用这些 LaTeX 子集：\frac{}{} \sqrt{} ^{} _{} \sum \prod \int \lim \le \ge \ne \times \cdot \pi \theta \alpha \beta \Delta \Rightarrow
  不要用 \begin{aligned}、\begin{cases}、\text{}、\left( 这类排版环境，行内 ${'$'}...${'$'} 一屏最多两处。
- blocks 只给一块 core；确实需要补充说明才加一块 deep，没有就别加。
- hints 恰好 3 条：第 1 条指该盯哪个条件，第 2 条给动作方向，第 3 条几乎把式子写出来。
""".trim()

    private val GENERAL_TEACH = """
你是这本教材的作者，正在写其中一个步骤。读者是零基础学生。

写作要求：
1. 第一句就把这一步的结论说出来，再解释它为什么成立、边界在哪。
   不要写「这一步要回答的问题是……」「我们来看一下……」——那是目录，不是讲义。
2. 只用必要的术语，第一次出现的术语要顺手解释半句。
3. 需要举例就给一个最小的例子，只给一个，不要铺三个。
4. 总长不超过 300 字。写不下就说明这一步拆大了，不要靠压缩句子硬塞。
5. md 里用 Markdown，行内公式用 ${'$'}...${'$'}。
6. hints 恰好 3 条，逐级加码：第 1 条指方向，第 2 条点出关键，第 3 条几乎给出答案。
""".trim()

    private fun questionRule(track: Track): String = when (track) {
        Track.CODE -> """
question 必须一句话问清、答案唯一。choice 给 3~4 个选项，干扰项要像学生真的会犯的错；
answer 字段必须原样复制正确选项的 value。short 题把可接受答案写进 expect，用 | 分隔。
short 只用于一个词或一句话；要写代码、或者答案超过 20 个字符的，一律改 choice（选出正确写法）。
问代码逻辑，不要问名词含义。答对后的 onCorrect 先肯定，再补一句他可能没意识到的原理。
""".trim()

        Track.MATH -> """
question 的写法：
- 优先问「下一步该做什么」或「这一步代入后得到什么」，不要问「这个概念是什么」。
- choice 的四个选项必须是四条不同的解题动作或四个不同的式子。干扰项要像学生真会走错的岔路：
  符号漏了、方向反了、忘了定义域、忘了常数项、把必要条件当充分条件。
- short 只给「一个数 / 一个单项式 / 不超过 20 个字符的短式子」。expect 里把等价写法都用 | 隔开
  （如 "-1|−1|x=-1"），纯数值题不要写单位。
- 要敲一整条展开式、一长串不等式的，一律改 choice：把正确结论和三个典型错法做成选项。
  不许让用户在手机上输入整段推导。
- 绝不出「以上都对」「以上都不对」这种选项，也不要出需要长篇论述的题。
- answer 字段必须原样复制正确选项的 value；short 题 answer 也要填，用于连错三次时公布。
""".trim()

        Track.GENERAL -> """
question 一句话问清、答案唯一。choice 给 3~4 个选项，干扰项要像学生真的会混淆的说法；
answer 字段必须原样复制正确选项的 value。
short 只用于一句话能答完的（≤ 20 字）。答案更长就改 choice：把正确说法和两三个常见误解做成选项，
别让用户在手机上打一段话。checklist 只在题目本来就要求分点作答时用，要点不超过 4 条。
问的是这一步的内容本身，不许问「这道题要讲哪几件事」这种教学安排类的元问题。
""".trim()
    }

    private val stepFormat = """
只输出一个 JSON 对象。

{"n":1,"blocks":[{"depth":"core","md":"这一步的正文"}],
"question":{"prompt":"提问","kind":"choice","choices":[{"label":"显示的文字","value":"判定用的值"}],"answer":"正确选项的 value","expect":"","rubric":[]},
"hints":["第一级","第二级","第三级"],"onCorrect":"答对后的回应","summary":["这一步要记住的一句话"]}

blocks 里 depth 用 core 表示必读，用 deep 表示想深入才看的补充。
""".trim()

    /**
     * 排版红线。截图里那种 `$f(a)=...\frac{1}{2}...\n得到...` 整段印成正文，
     * 就是因为模型开了 `$` 不关、还用字面量 `\n` 当换行。渲染器已经兜底，
     * 但源头不该产出这种东西。
     */
    private val MARKUP_RULE = """
排版（写错就会直接显示成一堆反斜杠，务必遵守）：
- 行内公式用 ${'$'}...${'$'}，开了就必须在同一行用 ${'$'} 关掉。写不下、或者超过 20 个字符，就单独成行用 ${'$'}${'$'}...${'$'}${'$'}。
- 不要在正文里写 \n 这两个字符来表示换行；要换行就直接换行。
- 公式只用常见 LaTeX 子集，不要用 \text{}、\begin{} 这类排版环境。
""".trim()

    fun teachUser(plan: LessonPlan, brief: StepBrief, notes: String): String = buildString {
        append("整道题：").append(plan.title)
        if (plan.oneLine.isNotBlank()) append("（").append(plan.oneLine).append("）")
        append("\n全部步骤：\n")
        plan.steps.forEach { append(it.n).append(". ").append(it.title).append(" — ").append(it.goal).append('\n') }
        val total = plan.steps.size
        append("\n现在只写第 ").append(brief.n).append(" 步《").append(brief.title).append("》。\n")
        append("这一步在整条链上的位置：").append(brief.n).append(" / ").append(total)
        if (brief.n == 1) append("（开头，只摆条件，不要提前解题）")
        if (brief.n == total) append("（最后一步，可以给结论）")
        append('\n')
        append("这一步的目标：").append(brief.goal).append('\n')
        append("这一步的判定方式：").append(brief.judgeKind).append('\n')
        val prev = plan.steps.getOrNull(brief.n - 2)
        if (prev != null) append("上一步已经讲过：").append(prev.title).append(" —— 不要重复它。\n")
        if (notes.isNotBlank()) {
            append("\n学生走到这里时暴露过的问题（正文要针对它，但不要把答案提前说出来）：\n")
            append(notes)
        }
        append("\n只输出第 ").append(brief.n).append(" 步的 JSON，n 字段必须是 ").append(brief.n).append("。")
    }

    /**
     * 接力模式：一次写完所有步骤。
     *
     * 逐步写意味着 6 步课要复制粘贴 7 次，手机上没人受得了；更要命的是模型看不到
     * 其它步骤写过什么，前后经常整段重复。写作规范和单步完全一样，只换输出格式，
     * 再加一条「不许重复」。
     */
    fun teachAllSystem(track: Track): String = buildString {
        append(
            when (track) {
                Track.CODE -> CODE_TEACH
                Track.MATH -> MATH_TEACH
                Track.GENERAL -> GENERAL_TEACH
            }
        )
        append("\n\n").append(questionRule(track))
        append("\n\n").append(MARKUP_RULE)
        append("\n\n").append(TEACH_ALL_RULE)
    }

    private val TEACH_ALL_RULE = """
这次不是写一步，而是把整份讲义一次写完。

只输出一个 JSON 对象：
{"steps":[
{"n":1,"blocks":[{"depth":"core","md":"第 1 步的正文"}],"question":{"prompt":"提问","kind":"choice","choices":[{"label":"显示的文字","value":"判定用的值"}],"answer":"正确选项的 value","expect":"","rubric":[]},"hints":["第一级","第二级","第三级"],"onCorrect":"答对后的回应","summary":["这一步要记住的一句话"]},
{"n":2,"blocks":[{"depth":"core","md":"第 2 步的正文"}],"question":{"prompt":"","kind":"short","choices":[],"answer":"","expect":"能接受的答案","rubric":[]},"hints":["","",""],"onCorrect":"","summary":[]}
]}

硬要求：
- 给定的每一步都要写，n 必须与给定编号一致，不许合并、跳号、只写前几步。
- 每步的 question.kind 按该步给出的「判定」来；但如果按判定会让学生打一整条长式子或一段话，
  就改成 choice（正确结论 + 两三个常见错法）。手机上少打字优先于形式一致。
- 相邻步骤不许重复：上一步给过的定义和例子，这一步引用一句就够，不要重讲。
- 每一步仍然遵守上面的长度限制：不要因为一次写多步就注水，也不要为了省字数把某一步压成一句话。
- 不要输出任何解释，不要 markdown 围栏。
""".trim()

    fun teachAllUser(plan: LessonPlan, briefs: List<StepBrief>, notes: String): String = buildString {
        val writing = briefs.map { it.n }.toSet()
        val whole = writing.size == plan.steps.size
        append("整道题：").append(plan.title)
        if (plan.oneLine.isNotBlank()) append("（").append(plan.oneLine).append("）")
        append(if (whole) "\n全部步骤，每一步都要写：\n" else "\n全部步骤，这一批只写标了「要写」的：\n")
        plan.steps.forEach {
            append(it.n).append(". ").append(it.title)
            if (it.goal.isNotBlank()) append(" — ").append(it.goal)
            append("（判定：").append(it.judgeKind).append("）")
            if (!whole && it.n in writing) append("  ← 要写")
            append('\n')
        }
        // 分批时给上一批的标题，模型才知道哪些话已经说过了。
        val prev = plan.steps.lastOrNull { it.n < briefs.first().n }
        if (!whole && prev != null) {
            append("\n前面已经写过《").append(prev.title).append("》等步骤，这一步不要重复它们给过的定义和例子。\n")
        }
        if (notes.isNotBlank()) {
            append("\n学生暴露过的问题（正文要针对它，但不要把答案提前说出来）：\n").append(notes).append('\n')
        }
        append("只输出 {\"steps\":[...]}，共 ").append(briefs.size).append(" 步。")
    }

    // ------------------------------------------------------------------ 出同类题

    /**
     * 同类题：只换情境和数，不换骨架。
     *
     * 这一段故意不许模型写解答——它写出来的解法会和后面 teach 生成的讲义打架，
     * 学生看到两套说法，反而不知道该信哪个。
     */
    val variantSystem = """
你在给一个刚做完某道题的学生出「同类型」的新题。

规则：
- 只换情境和数，不换知识点、不换解题骨架。原题每一步该做的动作，新题都要有对应的一步。
- 数字、对象、问法都要变。只把 3 改成 4 不算新题。
- 条件给全，答案唯一，不需要计算器也能算完。
- 不要写解答、提示、解析，只写题目本身。
- 原题是代码题时，新题也要是能直接写的代码题，保留输入输出格式说明。
""".trim()

    fun variantUser(plan: LessonPlan, notes: String): String = buildString {
        append("原题：").append(plan.title)
        if (plan.oneLine.isNotBlank()) append("（").append(plan.oneLine).append("）")
        append("\n")
        if (plan.knowledge.isNotEmpty()) {
            append("知识点：").append(plan.knowledge.joinToString("、")).append("\n")
        }
        append("原题的关口：\n")
        plan.steps.take(14).forEach {
            append(it.n).append(". ").append(it.title)
            if (it.goal.isNotBlank()) append(" — ").append(it.goal)
            append("\n")
        }
        if (notes.isNotBlank()) {
            append("\n学生在这道题上暴露过的问题（新题要考到同一处，但不要提示他）：\n")
            append(notes.take(600)).append("\n")
        }
        append("只输出一个 JSON 对象，不要解释，不要 markdown 围栏。\n")
        append("""{"title":"新题的名字，不超过 20 字","problem":"新题原文，条件、问法写全"}""")
    }

    // ------------------------------------------------------------------ 批改

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
4. 不要因为学生没有用「标准术语」就判错，先看意思。
5. 数学题的等价写法一律算对：x=(-1) 与 -1 与 x = −1 是同一个答案；分数、小数、根式能互化的也算对。
6. note 用一句话说清差在哪，直接对学生说，不要客套。
7. 只输出一个 JSON 对象，不要解释。

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

    // ------------------------------------------------------------------ 追问

    /**
     * 追问：学生卡在某一步时最需要的东西。
     * 关键约束是「只答这一步、别提前透后面的答案」，否则追问会变成抄答案的捷径。
     */
    fun askSystem(track: Track): String = buildString {
        append("你是这本教材的作者，学生正卡在某一步，随时会问你。\n\n要求：\n")
        append("1. 只回答他这次问的问题，不要顺手把后面的步骤也讲了，更不要提前给出后续题目的答案。\n")
        append("2. 先用一句话直接回答，再补必要的解释。\n")
        when (track) {
            Track.CODE -> append("3. 需要时给一小段代码，不超过 12 行；要说输出，就标清是实测还是推断。\n")
            Track.MATH -> append("3. 需要时写式子，用 ${'$'}${'$'}...${'$'}${'$'} 单独成行；不写代码，也不要一次推超过三行。\n")
            Track.GENERAL -> append("3. 需要时举一个最小例子，不要铺开讲。\n")
        }
        append("4. 不确定就说不确定，绝不编造结果。\n")
        append("5. 用中文，不要客套，不要「希望这对你有帮助」这类废话。\n")
        append("6. 如果他的问题本身建立在误解上，先点破那个误解。")
    }

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
            append(stepMd.take(2000))
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

    // ------------------------------------------------------------------ 补发

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

    // ------------------------------------------------------------------ 问答出题

    /**
     * 讲义里的问答已经有正确答案，模型只需要补干扰项。
     *
     * 不让它重写问题和答案：原文是作者写的，比模型改写的准；
     * 而且一次只给几道题、每道只给 300 字，省 token 也不容易跑偏。
     */
    fun quizSystem(): String = """
你在给一份讲义里的问答出干扰项。每道题都已经有正确答案。

每个错误选项要满足：
1. 8~30 字，长度和正确答案接近，不要一眼就看出来是废话。
2. 错得真实：混淆相邻概念、把必要条件说成充分条件、少一个限定词、因果倒置、
   或者只对了前半句。
3. 不许出现「以上都不对」「不确定」「视情况而定」这类废选项。
4. 不要照抄正确答案，也不要把正确答案换个说法当成干扰项。

每道题恰好 3 个错误选项。只输出 JSON，不要解释，不要 markdown 围栏：

{"items":[{"n":1,"options":["错误选项1","错误选项2","错误选项3"]}]}
""".trim()

    fun quizUser(title: String, items: List<Pair<Int, Qa>>): String = buildString {
        append("讲义：").append(title)
        append("\n下面这些题只补干扰项，n 与题号一致。\n")
        items.forEach { (n, qa) ->
            append("\n").append(n).append(". 问：").append(qa.question.trim()).append('\n')
            append("   答：").append(qa.answer.trim().take(300)).append('\n')
        }
        append("\n只输出这 ").append(items.size).append(" 道题。")
    }
}
