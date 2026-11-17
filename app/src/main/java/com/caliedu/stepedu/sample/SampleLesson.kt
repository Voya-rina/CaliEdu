package com.caliedu.stepedu.sample

import com.caliedu.stepedu.model.Block
import com.caliedu.stepedu.model.Choice
import com.caliedu.stepedu.model.LessonPlan
import com.caliedu.stepedu.model.Question
import com.caliedu.stepedu.model.StepBrief
import com.caliedu.stepedu.model.StepContent

/**
 * 没有配置 Key 时用这套离线内容，保证第一次打开就有东西可走。
 * 内容取自闯关 10-11《鲍鱼年龄预测》，数据细节对照过真实文件。
 */
object SampleLesson {

    val plan = LessonPlan(
        title = "闯关 10-11 鲍鱼年龄预测",
        kind = "code",
        oneLine = "把一份 4177 行的文本读成矩阵，训练一个能预测鲍鱼年龄的神经网络",
        knowledge = listOf("文件读写", "numpy 数组", "最小-最大归一化", "dtype"),
        needsRun = true,
        steps = listOf(
            StepBrief(1, "先认清数据", "读题", "知道 9 列各是什么，以及哪 8 列才是特征", "choice"),
            StepBrief(2, "字母列变数字", "逐块", "明白 sex 为什么映射成 1 / -1 / 0", "short"),
            StepBrief(3, "归一化", "逐块", "能手算 min-max，并说清该用谁的极值", "choice"),
            StepBrief(4, "字符串数组做减法", "排错", "认出 dtype 不对时会发生什么", "choice"),
        ),
    )

    val contents: Map<Int, StepContent> = listOf(
        StepContent(
            n = 1,
            blocks = listOf(
                Block(
                    "core",
                    """
题目给了两样东西：一份 `10abalone.data`，一份要跑起来的程序。先把数据认清楚，别急着看代码。

打开文件，第一行长这样：

```text
M,0.455,0.365,0.095,0.514,0.2245,0.101,0.15,15
```

九个字段，逗号分隔，**没有表头**。也就是说第一行就是第一条样本，读取时不需要跳过任何一行。

九列的分工是这样的：

- 第 0 列 `sex`：`M` 雄、`F` 雌、`I` 幼体（infant）
- 第 1~7 列：长度、直径、高度、总重、去壳重、内脏重、去血重
- 第 8 列 `rings`：环数，也就是题目要预测的"年龄"

最后这一列是**标签**，前面八列才是**特征**。喂进网络的是前八列，要求网络吐出来的是第九列。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "这 4177 行里，哪些列会被当作特征喂给网络？",
                kind = "choice",
                choices = listOf(
                    Choice("第 1~7 列，共 7 列", "第 1~7 列，共 7 列"),
                    Choice("第 0~7 列，共 8 列", "第 0~7 列，共 8 列"),
                    Choice("第 0~8 列，共 9 列", "第 0~8 列，共 9 列"),
                    Choice("只有第 8 列的 rings", "只有第 8 列的 rings"),
                ),
                answer = "第 0~7 列，共 8 列",
            ),
            hints = listOf(
                "特征是用来预测别人的那些列，标签是被预测的那一列。",
                "rings 在第 8 列（索引从 0 数起）。它既然要被预测，就不能同时当输入。",
                "`sex` 虽然写成字母，但它同样是可用信息，所以特征是从第 0 列到第 7 列，共 8 列。",
            ),
            onCorrect = "对。记住这个分工：0~7 列进 `X`，第 8 列进 `y`。`load_data` 里的 `data[:, :-1]` 和 `data[:, -1]` 说的正是这件事 —— `:-1` 是「从头取到倒数第二个」，`-1` 是「最后一个」。",
            summary = listOf("9 列 = 8 个特征 + 1 个标签", "第一行就是数据，没有表头"),
        ),
        StepContent(
            n = 2,
            blocks = listOf(
                Block(
                    "core",
                    """
`sex` 这一列只有三个字母，可神经网络只认数字，所以必须先把它们编码。

```python
# M / F / I 三种性别编码成数字
# 幼体（infant）解剖前分不出性别，单独作为一类
# 用 1 / -1 / 0 而不是 1 / 2 / 3，是为了让三类彼此等距
if sex == 'M':
    sex = 1.0
elif sex == 'F':
    sex = -1.0
else:
    sex = 0.0      # 'I'，幼体
```

为什么不用 1 / 2 / 3？因为数字进到模型里会被当成**有大小、有距离**的量。写成 1/2/3，网络就会以为"F 正好站在 M 和 I 中间"，而这三类本来没有顺序。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "第一列里的 `I` 代表什么？",
                kind = "short",
                expect = "幼体|infant|幼龄|婴儿|未成熟",
            ),
            hints = listOf(
                "`M` 是 male，`F` 是 female，剩下那个照字母猜。",
                "它的英文是 infant。",
                "幼体 —— 还没发育出性别的小鲍鱼。",
            ),
            onCorrect = "对，infant。所以数据里一共三类：M 雄、F 雌、I 幼体。",
            summary = listOf("字母列必须编码成数字", "编码要让三类等距，别凭空造顺序"),
        ),
        StepContent(
            n = 3,
            blocks = listOf(
                Block(
                    "core",
                    """
看看归一化到底在做什么。假设某一列只有两个值：`0.3` 和 `0.6`。

最小-最大归一化只有一条式子：

${'$'}x' = \frac{x - \min}{\max - \min}$

代进去：`min = 0.3`，`max = 0.6`，分母 `0.6 - 0.3 = 0.3`。

- `0.3` → `(0.3 - 0.3) / 0.3 = 0`
- `0.6` → `(0.6 - 0.3) / 0.3 = 1`

整列被压进 `[0, 1]`。要注意的一点是：`min` 和 `max` 应该取自**训练集**；测试集直接用训练集这两个数去缩放，不能自己再算一遍。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "如果测试集用自己的极值归一化，会发生什么？",
                kind = "choice",
                choices = listOf(
                    Choice("测试集的信息会泄漏进训练过程，评估结果偏乐观", "测试集的信息会泄漏进训练过程，评估结果偏乐观"),
                    Choice("测试集会被压得更小，训练更快", "测试集会被压得更小，训练更快"),
                    Choice("没什么区别，反正都压到 0~1", "没什么区别，反正都压到 0~1"),
                    Choice("numpy 会报形状不匹配", "numpy 会报形状不匹配"),
                ),
                answer = "测试集的信息会泄漏进训练过程，评估结果偏乐观",
            ),
            hints = listOf(
                "想一想：模型训练时见到的数值范围，和它预测时见到的，一样吗？",
                "两套极值意味着同一个 0.5，在训练集和测试集里代表不同的实际长度。",
                "等于让模型提前知道了测试集的最大最小值 —— 这就是数据泄漏。",
            ),
            onCorrect = "对。所以 `normalize_data(train, test)` 只用训练集算极值，再把同一套极值套到测试集上。评估时偷偷看过测试集，报出来的准确率就是假的。",
            summary = listOf("x' = (x - min) / (max - min)", "极值只从训练集取"),
        ),
        StepContent(
            n = 4,
            blocks = listOf(
                Block(
                    "core",
                    """
新手最常撞的墙：读进来忘了转 `float`。

```python
# 第一列是 'M' 这样的字母，numpy 只能把整张表当成字符串数组
data = np.loadtxt('10abalone.data', delimiter=',')
print(data.dtype)
```

```output:inferred
<U5
```

`U` 是 unicode，后面的数字是最长字段的长度。字符串数组能切片、能打印，看起来一切正常 —— 直到你拿它做算术。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "对这样的字符串数组做 `(x - min) / range`，会怎样？",
                kind = "choice",
                choices = listOf(
                    Choice("报错：这个 ufunc 不支持字符串相减", "报错：这个 ufunc 不支持字符串相减"),
                    Choice("自动转成数字，结果正确", "自动转成数字，结果正确"),
                    Choice("字符串被拼接起来", "字符串被拼接起来"),
                    Choice("返回全 0", "返回全 0"),
                ),
                answer = "报错：这个 ufunc 不支持字符串相减",
            ),
            hints = listOf(
                "字符串能减字符串吗？",
                "报错里会出现 `ufunc 'subtract' not supported` 这类字样。",
                "直接抛 TypeError：该 ufunc 不支持字符串类型。",
            ),
            onCorrect = "对。所以 sex 映射完必须立刻 `astype(float)`，把整张表变成数字，后面才谈得上归一化。",
            summary = listOf("loadtxt 遇到字母列，整表变字符串", "映射完立刻 astype(float)"),
        ),
    ).associateBy { it.n }
}
