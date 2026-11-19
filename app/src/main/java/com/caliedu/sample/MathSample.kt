package com.caliedu.sample

import com.caliedu.model.Block
import com.caliedu.model.Choice
import com.caliedu.model.LessonPlan
import com.caliedu.model.Question
import com.caliedu.model.StepBrief
import com.caliedu.model.StepContent

/**
 * 离线数学示例：一阶线性递推求通项。
 *
 * 这份示例专门用来对照「数学不该写成大篇幅讲义」——每步只有一个动作、一两行式子、
 * 一段不超过六十字的说明。没有代码块，没有过渡句，没有「综上所述」。
 */
object MathSample : Sample {

    override val plan = LessonPlan(
        title = "求数列通项 a(n+1)=2a(n)+1",
        kind = "math",
        oneLine = "首项 1，后一项是前一项的两倍加一，求通项公式",
        tags = listOf("高等数学", "数列", "代数变形"),
        knowledge = listOf("递推数列", "等比数列", "换元", "待定常数法"),
        needsRun = false,
        steps = listOf(
            StepBrief(1, "条件只有两条", "读题", "分清递推式与通项是两种不同的表达", "choice"),
            StepBrief(2, "和等比差在哪", "建模", "看出 +1 会随项数被放大，所以不是等比", "choice"),
            StepBrief(3, "两边同加一个数", "定路", "用解方程的办法找出该加几", "short"),
            StepBrief(4, "换个字母就是等比", "推导", "把 a(n)+1 当成整体，认出公比", "choice"),
            StepBrief(5, "首项要跟着换", "推导", "算出 b1 而不是沿用 a1", "short"),
            StepBrief(6, "换回原来的字母", "推导", "由 b(n) 反解出 a(n)", "short"),
            StepBrief(7, "代前几项验算", "检验", "用两条路对同一批项，确认没算错", "choice"),
            StepBrief(8, "这条路能搬走", "收束", "把方法抽象成 p、q 的通用形状", "choice"),
        ),
    )

    override val contents: Map<Int, StepContent> = listOf(
        StepContent(
            n = 1,
            blocks = listOf(
                Block(
                    "core",
                    """
### 先看清给的是什么

题目给了两样东西：一个首项，一个递推式。递推式只说「后一项怎么由前一项算出来」，它算不出第 10 项，只能一项一项往前爬。

$$ a_1 = 1 $$

$$ a_{n+1} = 2a_n + 1 \quad (n \ge 1) $$

所以这题不是代入求值，而是把递推改写成能直接算第 n 项的样子。改写只有一个方向：往认识的数列上靠。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "只有这两条，能一次算出 a(100) 吗？",
                kind = "choice",
                choices = listOf(
                    Choice("不能，得从 a1 一项一项推到 a100", "不能，得从 a1 推到 a100"),
                    Choice("能，把 n = 100 代进递推式", "能，直接代 n=100"),
                    Choice("能，但要把 100 拆成 2 的幂", "能，拆成 2 的幂"),
                ),
                answer = "不能，得从 a1 推到 a100",
            ),
            hints = listOf(
                "看递推式里有没有出现 n 本身。",
                "式子里只有 a(n+1) 和 a(n)，没有 n，代 100 代到哪里去？",
                "把 n = 100 代进去，得到的是 a101 和 a100 的关系，还是解不出 a100。",
            ),
            onCorrect = "对。递推式给出的是相邻两项的关系，不是第 n 项本身——这正是要改写它的原因。",
            summary = listOf("递推式只连相邻两项，通项才直接给出第 n 项"),
        ),
        StepContent(
            n = 2,
            blocks = listOf(
                Block(
                    "core",
                    """
### 和等比数列比一比

先看简化版：如果式子是 a(n+1) = 2a(n)，每一项都是前一项的两倍，那是公比为 2 的等比数列，通项立刻能写。

$$ a_{n+1} = 2a_n \Rightarrow a_n = 2^{n-1} $$

现在多了一个 +1。这个常数每往后推一项，就会被前面的 2 放大一次：+1、+2、+4、+8……

所以它不是等比，但只差一点。差的这一点能不能消掉，就是这道题的全部。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "式子里多出来的 +1，每往后推一项会怎样？",
                kind = "choice",
                choices = listOf(
                    Choice("被前面的 2 放大，变成 +2、+4、+8", "被 2 放大"),
                    Choice("一直是 +1，不增不减", "一直是 +1"),
                    Choice("被吃掉，不影响后面的项", "被吃掉"),
                ),
                answer = "被 2 放大",
            ),
            hints = listOf(
                "动手写前三项看看，别在心里推。",
                "a2 = 2a1 + 1，a3 = 2a2 + 1。把 a2 展开再代进 a3。",
                "a3 = 2(2a1 + 1) + 1 = 4a1 + 3，那个 1 变成了 3。",
            ),
            onCorrect = "对。正因为这一项会累积，才不能直接套等比公式——但它累积的方式很规整，可以被整体消掉。",
            summary = listOf("常数项会被公比反复放大，所以不能直接用等比通项"),
        ),
        StepContent(
            n = 3,
            blocks = listOf(
                Block(
                    "core",
                    """
### 把常数项吸收进整体

要让 +1 消失，办法是两边同时加上同一个数，凑成完全的比例关系。设这个数是 x：

$$ a_{n+1} + x = 2a_n + 1 + x $$

右边要能提成 2(a(n) + x)，就得 1 + x = 2x，解出 x = 1。于是

$$ a_{n+1} + 1 = 2(a_n + 1) $$

这一步不是灵感，是解方程解出来的。以后遇到 a(n+1) = p·a(n) + q，同加的数就是 q/(p−1)。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "a(n+1) = 3a(n) + 2，两边该同加几？",
                kind = "short",
                answer = "1",
                expect = "1|x=1",
            ),
            hints = listOf(
                "照搬上一段的做法：设加的数是 x，让常数项能提出公因式。",
                "要求 2 + x = 3x。",
                "2 + x = 3x 解得 x = 1。",
            ),
            onCorrect = "对。同加的数满足 q + x = p·x，也就是 x = q/(p−1)。这是待定常数法，不是凑出来的。",
            summary = listOf("同加 x = q/(p−1)，把递推式凑成等比"),
        ),
        StepContent(
            n = 4,
            blocks = listOf(
                Block(
                    "core",
                    """
### 换个字母，它就是等比

令 b(n) = a(n) + 1。上面那条式子就变成

$$ b_{n+1} = 2b_n $$

按定义，b(n) 是公比 q = 2 的等比数列。这里的换元没有技巧，只是把 a(n) + 1 取个短名字，省得每次写一长串。

要注意：换元换了整个数列，不是只换某一项。所以首项也得跟着换。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "b(n) 的公比是多少？",
                kind = "choice",
                choices = listOf(
                    Choice("2", "2"),
                    Choice("1", "1"),
                    Choice("3", "3"),
                    Choice("n", "n"),
                ),
                answer = "2",
            ),
            hints = listOf(
                "公比就是 b(n+1) 与 b(n) 的比值。",
                "从 b(n+1) = 2b(n) 里直接读系数。",
                "系数是 2，公比就是 2。",
            ),
            onCorrect = "对。换元之后式子退化成最干净的等比形式，剩下的都是套公式。",
            summary = listOf("换元要把整个数列一起换，包括首项"),
        ),
        StepContent(
            n = 5,
            blocks = listOf(
                Block(
                    "core",
                    """
### 首项要跟着一起换

等比数列的通项是 b(n) = b(1)·q^(n−1)。这里的 b(1) 不是 a(1) = 1，而是

$$ b_1 = a_1 + 1 = 2 $$

代进去：

$$ b_n = 2 \cdot 2^{n-1} = 2^n $$
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "b(n) 等于什么？",
                kind = "short",
                answer = "2^n",
                expect = "2^n|2n|2**n",
            ),
            hints = listOf(
                "先算 b(1)，再套 b(n) = b(1)·q^(n−1)。",
                "b(1) = 2，q = 2，两个 2 相乘可以合并。",
                "2·2^(n−1) = 2^n。",
            ),
            onCorrect = "对。最容易错的地方就是拿 a(1) 当 b(1)——换元之后首项必须重算。",
            summary = listOf("b(n) = 2^n"),
        ),
        StepContent(
            n = 6,
            blocks = listOf(
                Block(
                    "core",
                    """
### 换回原来的字母

b(n) 是自己起的名字，题目要的是 a(n)。从 b(n) = a(n) + 1 反解：

$$ a_n = b_n - 1 = 2^n - 1 $$

通项到这里就出来了。剩下的工作是确认它没算错，而不是继续变形。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "a(n) 等于什么？",
                kind = "short",
                answer = "2^n-1",
                expect = "2^n-1|2n-1|2^n − 1",
            ),
            hints = listOf(
                "把上一步的 b(n) 代回 b(n) = a(n) + 1。",
                "a(n) = b(n) − 1。",
                "a(n) = 2^n − 1。",
            ),
            onCorrect = "对。注意减 1 是在指数算完之后减，不是 2^(n−1)。",
            summary = listOf("a(n) = 2^n − 1"),
        ),
        StepContent(
            n = 7,
            blocks = listOf(
                Block(
                    "core",
                    """
### 两边都对一遍

验算要往两个方向走：通项算出来的项，能不能满足递推式；它跟手推的前几项对不对得上。

$$ a_1 = 2^1 - 1 = 1 $$

$$ a_2 = 3, \quad a_3 = 7, \quad a_4 = 15 $$

手推：1 → 2·1+1 = 3 → 2·3+1 = 7 → 2·7+1 = 15。两边一致。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "按递推式手推，a(3) 等于多少？",
                kind = "choice",
                choices = listOf(
                    Choice("7", "7"),
                    Choice("5", "5"),
                    Choice("9", "9"),
                    Choice("15", "15"),
                ),
                answer = "7",
            ),
            hints = listOf(
                "从 a(1) = 1 出发，一步一步推。",
                "a(2) = 2×1 + 1 = 3。",
                "a(3) = 2×3 + 1 = 7。",
            ),
            onCorrect = "对。手推前三项是成本最低的验算，错了立刻能发现。",
            summary = listOf("用前几项双向校验，比反复检查推导过程有效"),
        ),
        StepContent(
            n = 8,
            blocks = listOf(
                Block(
                    "core",
                    """
### 记住式子的形状，不是这道题

形如 a(n+1) = p·a(n) + q（p ≠ 1）的递推，都能同加 x = q/(p−1) 化成等比。p = 1 时它本身就是等差，另算。

$$ a_{n+1} + \frac{q}{p-1} = p\left(a_n + \frac{q}{p-1}\right) $$

所以真正要带走的是「把常数项吸收进整体」这个动作，不是 2 和 1 这两个数。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "a(n+1) = 2a(n) + 6，两边该同加几？",
                kind = "choice",
                choices = listOf(
                    Choice("6", "6"),
                    Choice("3", "3"),
                    Choice("2", "2"),
                    Choice("12", "12"),
                ),
                answer = "6",
            ),
            hints = listOf(
                "套 x = q/(p−1)，先认出 p 和 q。",
                "p = 2，q = 6。",
                "x = 6/(2−1) = 6。",
            ),
            onCorrect = "对。p = 2 时 p−1 = 1，同加的数正好等于常数项——这是最容易记混的一档。",
            summary = listOf("a(n+1) = p·a(n) + q 同加 q/(p−1)，p = 1 时是等差"),
        ),
    ).associateBy { it.n }
}
