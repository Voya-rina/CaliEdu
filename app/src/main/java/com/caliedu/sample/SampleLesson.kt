package com.caliedu.sample

import com.caliedu.model.Block
import com.caliedu.model.Choice
import com.caliedu.model.LessonPlan
import com.caliedu.model.Question
import com.caliedu.model.StepBrief
import com.caliedu.model.StepContent

/**
 * 离线示例：闯关 10-11《鲍鱼年龄预测》，逐块对着真实源码提问。
 * 数据事实（4177 行、无表头、9 列）与源码行为按 0学校/AI大数据实验室 下的原文件核对。
 */
object SampleLesson {

    val plan = LessonPlan(
        title = "闯关 10-11 鲍鱼年龄预测",
        kind = "code",
        oneLine = "4177 行文本 → 归一化矩阵 → 分批喂进四层全连接网络 → 预测环数",
        knowledge = listOf(
            "csv 逐行读取", "numpy 切片与形状", "最小-最大归一化",
            "生成器与分批", "全连接回归", "R² 评估",
        ),
        needsRun = true,
        steps = listOf(
            StepBrief(1, "程序被切成几块", "框架", "认出数据准备三件事与网络两件事的边界", "choice"),
            StepBrief(2, "一行文本变九个数字", "逐块", "看懂 load_data 里列表拼接的作用", "choice"),
            StepBrief(3, "else 后面为什么是 break", "排错", "发现一处会静默丢数据的写法", "choice"),
            StepBrief(4, "归一化只动前八列", "逐块", "算清 feature_num 与标签的处理", "short"),
            StepBrief(5, "sex 归一化后成了什么", "逐块", "推演三类编码经过 min-max 的结果", "choice"),
            StepBrief(6, "range 为 0 的那行防御", "逐块", "说清除零发生在什么情况下", "choice"),
            StepBrief(7, "冒号差在哪", "逐块", "分清 (n,) 与 (n,1) 对 loss 的影响", "choice"),
            StepBrief(8, "洗牌写在哪一层", "逐块", "理解 __iter__ 每次调用都重洗", "choice"),
            StepBrief(9, "最后一批有几条", "逐块", "手算 batch 切分的收尾", "short"),
            StepBrief(10, "输出层为什么光秃秃", "框架", "说清回归层不加激活的理由", "choice"),
            StepBrief(11, "为什么是 MSE", "框架", "区分回归损失与分类损失", "choice"),
            StepBrief(12, "这个 accuracy 其实是 R²", "逐块", "读懂评估函数的分子分母", "choice"),
            StepBrief(13, "train 与 eval 的分工", "排错", "知道漏掉 no_grad 的代价", "choice"),
            StepBrief(14, "推理前的 reshape", "整合", "把一条样本摆成模型要的形状", "choice"),
            StepBrief(15, "那两行 copy 不能省", "排错", "知道原地赋值会改掉调用方的数组", "choice"),
            StepBrief(16, "空行与少一列", "排错", "分辨两种坏行各自的下场", "choice"),
            StepBrief(17, "顺序切分是运气好", "排错", "看懂 0.8 硬切的风险与实测分布", "choice"),
            StepBrief(18, "设备在哪一行定", "整合", "知道 set_device 的生效时机", "choice"),
            StepBrief(19, "参数怎么存怎么取", "整合", "分清 state_dict 与整个模型", "choice"),
            StepBrief(20, "这个训练 Acc 是平均值", "整合", "发现按批平均与整集计算的差别", "choice"),
            StepBrief(21, "item() 在同步什么", "整合", "说清它为什么拖慢训练", "choice"),
            StepBrief(22, "如果只改一个地方", "自测", "在过拟合时选出正确的第一手", "choice"),
        ),
    )

    val contents: Map<Int, StepContent> = listOf(
        StepContent(
            n = 1,
            blocks = listOf(
                Block(
                    "core",
                    """
先别看细节，把骨架立起来。整份文件只做五件事，前三件属于数据准备，后两件属于用网：

```python
# ① 读文件 → (4177, 9) 的矩阵
def load_data(file_path=data_path):

# ② 把数值压到 [0,1]
def normalize_data(train_data, test_data):

# ③ 把矩阵切成一批一批
class DatasetProvider:

# ④ 四层全连接
class MLP(nn.Layer):

# ⑤ 训练、评估、保存、推理
def main():
```

闯关 10 只要前三块 —— 题目专门写了「使用 numpy 实现，不要用 paddle」。闯关 11 才把后两块接上。

这个划分不是装饰。①②③ 全部只依赖 numpy，可以单独跑、单独打印形状；④⑤ 依赖 paddle 和显卡。出问题时先看形状，再看网络 —— 顺序反了会白调一晚上。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "下面哪一个不属于「数据准备」那三块？",
                kind = "choice",
                choices = listOf(
                    Choice("`MLP`", "`MLP`"),
                    Choice("`load_data`", "`load_data`"),
                    Choice("`normalize_data`", "`normalize_data`"),
                    Choice("`DatasetProvider`", "`DatasetProvider`"),
                ),
                answer = "`MLP`",
            ),
            hints = listOf(
                "判断标准很简单：这一块需不需要 paddle。",
                "`load_data` 只用 csv 和 numpy，`normalize_data` 只用 numpy，`DatasetProvider` 只用 numpy 和切片。",
                "`MLP` 继承 `nn.Layer`，它是网络本身，属于用网那一半。",
            ),
            onCorrect = "对。这条界线决定了调试顺序：先让前三块各自打印出正确的形状，再让网络跑起来。形状错了，网络再对也没用。",
            summary = listOf("数据准备三块只依赖 numpy，可以离线单测", "网络与训练依赖 paddle，排错优先级在形状之后"),
        ),
        StepContent(
            n = 2,
            blocks = listOf(
                Block(
                    "core",
                    """
`load_data` 里最关键的是这一行：

```python
data.append([sex_val] + num_vals)
```

`sex_val` 是一个数，`num_vals` 是后面 8 个浮点数组成的列表。一个数加一个列表，Python 会先把数包进列表再拼接，得到长度 9 的列表：

```python
[1.0] + [0.455, 0.365, 0.095, 0.514, 0.2245, 0.101, 0.15, 15.0]
```

```output:inferred
[1.0, 0.455, 0.365, 0.095, 0.514, 0.2245, 0.101, 0.15, 15.0]
```

每行都拼成等长列表，`np.array(data)` 才能把它摞成一个 `(4177, 9)` 的规整矩阵。少拼一段、或某行少一列，矩阵就散了。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "如果把 `[sex_val] + num_vals` 改成 `num_vals.append(sex_val)`，会出什么问题？",
                kind = "choice",
                choices = listOf(
                    Choice("性别跑到最后一列，和 rings 的位置撞了", "性别跑到最后一列，和 rings 的位置撞了"),
                    Choice("没有区别，append 和 + 等价", "没有区别，append 和 + 等价"),
                    Choice("会报 TypeError", "会报 TypeError"),
                    Choice("矩阵会变成 8 列", "矩阵会变成 8 列"),
                ),
                answer = "性别跑到最后一列，和 rings 的位置撞了",
            ),
            hints = listOf(
                "列的顺序是谁决定的？",
                "`append` 是往尾部加，于是 sex 排到 rings 后面变成第 10 个位置；而后面切分是按「前 8 列特征、最后一列标签」来的。",
                "结果标签被当成特征、性别被当成标签 —— 形状还是 (4177, 9)，程序照跑，结果全错。",
            ),
            onCorrect = "对，这才是最阴的一类 bug：形状对、不报错、结果全错。列序是这份程序的隐含契约，改拼接方式就会悄悄破坏它。",
            summary = listOf("列表拼接决定列序，列序是后续所有切片的契约", "形状正确不等于语义正确"),
        ),
        StepContent(
            n = 3,
            blocks = listOf(
                Block(
                    "core",
                    """
看这段分支的收尾：

```python
sex = row[0]
if sex == 'M':
    sex_val = 1.
elif sex == 'F':
    sex_val = -1.
elif sex == 'I':
    sex_val = 0.
else:
    break        # ← 注意这里
```

`break` 跳出的不是 `if`，是整个 `for` 循环。也就是说：只要撞上一行 sex 不是这三个字母，读取就到此为止，后面的行全部作废，而且没有报错。

如果本意是跳过这一行继续读，该写 `continue`；如果是数据脏了就立刻停，该写 `raise ValueError(...)`。`break` 卡在两者之间，是最糟的选择：它既不告诉你出了问题，也不肯把数据读完。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "文件第 2000 行的 sex 写成了小写 `m`，程序跑完会读到多少行？",
                kind = "choice",
                choices = listOf(
                    Choice("1999 行，而且不会有任何提示", "1999 行，而且不会有任何提示"),
                    Choice("4177 行，`m` 会被当成 `M`", "4177 行，`m` 会被当成 `M`"),
                    Choice("直接抛异常，程序停下", "直接抛异常，程序停下"),
                    Choice("2000 行，坏的那行被跳过", "2000 行，坏的那行被跳过"),
                ),
                answer = "1999 行，而且不会有任何提示",
            ),
            hints = listOf(
                "小写 `m` 会走到哪个分支？",
                "三个 `elif` 都不成立，落到 `else`，执行 `break`；前 1999 行已经 append 进 `data` 了。",
                "于是读到 1999 行就安静结束，后面 2178 行全丢，`print(data.shape)` 才会看到 `(1999, 9)`。",
            ),
            onCorrect = "对。这类 bug 危险在于：训练照样跑完、loss 照样下降，只有准确率莫名其妙地低。读完数据永远打印一次行数，4177 是你的基准线。",
            summary = listOf("`break` 终止整个读文件循环", "读完立刻核对行数，比事后调模型便宜"),
        ),
        StepContent(
            n = 4,
            blocks = listOf(
                Block(
                    "core",
                    """
归一化函数一进来先算一件事：

```python
feature_num = train_data.shape[1] - 1
max_vals = train_data[:, :feature_num].max(axis=0)
min_vals = train_data[:, :feature_num].min(axis=0)
```

`shape[1]` 是列数 9，减 1 得 8，所以 `:feature_num` 取的是前 8 列，最后一列 rings 被排除在外。

排除不是随手为之。归一化会改变数值尺度，如果连标签也压到 `[0,1]`，模型预测出来的是「归一化后的年龄」，你还得再乘回去才知道几岁。而 R² 对尺度不敏感，标签保持原始值反而更好读。

`axis=0` 也别忽略：它表示沿行方向压，也就是每一列各自求最大值，得到一个长度 8 的数组，而不是一个全局最大值。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "本题里 `feature_num` 等于几？",
                kind = "short",
                expect = "8|八",
            ),
            hints = listOf(
                "先问 `shape[1]` 是几。",
                "矩阵是 `(4177, 9)`，`shape[1]` 是列数 9。",
                "9 - 1 = 8，正好对应前 8 个特征。",
            ),
            onCorrect = "对，8。所以 `max_vals` 和 `min_vals` 都是长度 8 的数组；标签那一列从头到尾没被碰过。",
            summary = listOf("`shape[1] - 1` 就是特征数 8", "`axis=0` 是按列求极值"),
        ),
        StepContent(
            n = 5,
            blocks = listOf(
                Block(
                    "core",
                    """
归一化只做一件事，但对 sex 这列有个不显眼的后果。

编码时三类是 `1 / 0 / -1`，这列的 `max = 1`、`min = -1`，区间长度 2。代进式子 `(x - min) / (max - min)`：

```
M:  ( 1 - (-1)) / 2 = 1.0
I:  ( 0 - (-1)) / 2 = 0.5
F:  (-1 - (-1)) / 2 = 0.0
```

三类从 `1 / 0 / -1` 变成 `1 / 0.5 / 0`。

好消息是等距性没被破坏：两两间隔仍是 0.5，模型不会因此以为某一类更大。这也是当初选 1 / -1 / 0 而不是 1 / 2 / 3 的回报 —— 对称编码经过 min-max 之后依然对称。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "如果一开始用的编码是 `1 / 2 / 3`，归一化后三类会变成什么？",
                kind = "choice",
                choices = listOf(
                    Choice("0 / 0.5 / 1，仍然等距", "0 / 0.5 / 1，仍然等距"),
                    Choice("0 / 0.5 / 1，但顺序被颠倒", "0 / 0.5 / 1，但顺序被颠倒"),
                    Choice("三类会被压成同一个值", "三类会被压成同一个值"),
                    Choice("超出 [0,1] 区间", "超出 [0,1] 区间"),
                ),
                answer = "0 / 0.5 / 1，仍然等距",
            ),
            hints = listOf(
                "min-max 是线性变换，等距的东西变换完还等距。",
                "1 / 2 / 3 的最大值是 3、最小值是 1，区间长度 2。",
                "于是 (1-1)/2=0、(2-1)/2=0.5、(3-1)/2=1 —— 确实还是等距的。",
            ),
            onCorrect = "对。这里要破一个常见误解：归一化会把任何线性编码的等距性保留下来，它救不了 1 / 2 / 3 的毛病。那个毛病在归一化之前就存在 —— 它让 F 看起来站在 M 和 I 中间，这个语义错误归一化消不掉。",
            summary = listOf("对称编码归一化后仍是 0 / 0.5 / 1", "归一化只改尺度，不改语义"),
        ),
        StepContent(
            n = 6,
            blocks = listOf(
                Block(
                    "core",
                    """
`normalize_data` 里有三行看起来多余的代码：

```python
range_vals = max_vals - min_vals

for i in range(len(range_vals)):
    if range_vals[i] == 0:
        range_vals[i] = 1
```

`range_vals` 是分母。如果某列的 `max` 恰好等于 `min` —— 也就是这一列所有行都是同一个数 —— 分母就是 0，接下来得到 `nan`。

`nan` 的麻烦在于它不当场报错。它会顺着矩阵流进网络，loss 变成 `nan`，训练十几轮之后你才发现模型什么都没学到，而报错只有一句 loss is nan，指不回原始数据。

把 0 换成 1，效果是这一列变成 `(x - min) / 1 = 0`，整列归零。对常量列来说这恰好正确：它不携带任何区分信息，压成 0 最合适。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "某一列全部是 `0.5`，走完这段代码后变成什么？",
                kind = "choice",
                choices = listOf(
                    Choice("全 0", "全 0"),
                    Choice("全 0.5", "全 0.5"),
                    Choice("全 1", "全 1"),
                    Choice("全 nan", "全 nan"),
                ),
                answer = "全 0",
            ),
            hints = listOf(
                "这一列 max 和 min 都是 0.5，`range` 是 0，被改成了 1。",
                "代入 `(0.5 - 0.5) / 1`。",
                "结果是 0，整列归零。",
            ),
            onCorrect = "对。常量列归一化后必然全 0 —— 它提供不了信息，模型只能靠别的列。这行防御同时挡住了除零和 nan 扩散。",
            summary = listOf("分母为 0 会产出 nan，且不在原地报错", "常量列压成 0 是合理结果"),
        ),
        StepContent(
            n = 7,
            blocks = listOf(
                Block(
                    "core",
                    """
`DatasetProvider` 里这两行只差一个冒号，形状完全不同：

```python
self.feature = data[:, :-1]   # 前 8 列 → (n, 8)
self.label   = data[:, -1:]   # 最后 1 列 → (n, 1)
```

`data[:, -1]` 得到 `(n,)`，一维；`data[:, -1:]` 得到 `(n, 1)`，二维。

为什么非要二维？因为 loss 要求预测值和真值逐元素相减。模型输出是 `(bs, 1)`，如果标签是 `(bs,)`，广播会把它拉成 `(bs, bs)` —— 一个 64×64 的矩阵，然后照常算出 loss，不报错，梯度全乱。

`y` 后面那个冒号，就为这一件事。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "设 `bs = 64`，`pred` 是 `(64,1)`、`label` 是 `(64,)`，相减得到什么？",
                kind = "choice",
                choices = listOf(
                    Choice("(64, 64)，广播把两者摊开了", "(64, 64)，广播把两者摊开了"),
                    Choice("(64, 1)，自动对齐", "(64, 1)，自动对齐"),
                    Choice("报错，形状不匹配", "报错，形状不匹配"),
                    Choice("(64,)，降到一维", "(64,)，降到一维"),
                ),
                answer = "(64, 64)，广播把两者摊开了",
            ),
            hints = listOf(
                "广播从最后一维开始比较，缺的那一维会被补成 1。",
                "`(64,1)` 与 `(64,)` → 后者补成 `(1,64)`，再和 `(64,1)` 广播。",
                "结果是 `(64,64)`，而且不报错。",
            ),
            onCorrect = "对，而且它不报错 —— 这是形状 bug 里最贵的一种。写 label 时习惯性带上冒号，让形状自己说明它是二维的。",
            summary = listOf("`-1:` 保二维，`-1` 降一维", "广播能让错误形状静默通过"),
        ),
        StepContent(
            n = 8,
            blocks = listOf(
                Block(
                    "core",
                    """
洗牌写在 `__iter__` 里，不在 `__init__` 里：

```python
def __init__(self, data, batch_size=batch_size, shuffle=True):
    self.feature = data[:, :-1]
    self.shuffle = shuffle

def __iter__(self):
    indices = np.arange(self.num_samples)
    if self.shuffle:
        np.random.shuffle(indices)      # ← 每次 for 都重洗一遍
    for start_idx in range(0, self.num_samples, self.batch_size):
        ...
        yield self.feature[batch_indices], self.label[batch_indices]
```

`__iter__` 每被调用一次，洗牌就发生一次；而 `for ... in train_provider:` 每进入一次，都会调用一次 `__iter__`。

于是每个 epoch 的批次顺序都不一样。如果洗牌写在 `__init__` 里，整个训练过程中顺序就固定了，模型每轮看到的都是同一种「先看哪些样本、再看哪些样本」。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "把 `np.random.shuffle(indices)` 挪到 `__init__` 里，会怎样？",
                kind = "choice",
                choices = listOf(
                    Choice("100 个 epoch 的批次顺序完全一样", "100 个 epoch 的批次顺序完全一样"),
                    Choice("之后不再洗牌，样本会丢失", "之后不再洗牌，样本会丢失"),
                    Choice("没有影响，反正都是随机的", "没有影响，反正都是随机的"),
                    Choice("会报错，`__init__` 里拿不到 `num_samples`", "会报错，`__init__` 里拿不到 `num_samples`"),
                ),
                answer = "100 个 epoch 的批次顺序完全一样",
            ),
            hints = listOf(
                "`__init__` 只在 `DatasetProvider(...)` 这一句执行一次。",
                "`__iter__` 是每次 `for ... in provider` 执行一次。",
                "挪进 `__init__` 只会洗一次牌，之后 100 轮都用同一副排列。",
            ),
            onCorrect = "对。每次洗牌等于重新抽一次对数据的看法，这是 mini-batch 训练随机性的一半来源，另一半是初始化。",
            summary = listOf("`__iter__` 每进一次 for 就执行一次", "洗牌放 `__iter__` 等于每轮重新排列"),
        ),
        StepContent(
            n = 9,
            blocks = listOf(
                Block(
                    "core",
                    """
切批的循环长这样：

```python
for start_idx in range(0, self.num_samples, self.batch_size):
    end_idx = min(start_idx + self.batch_size, self.num_samples)
    batch_indices = indices[start_idx:end_idx]
    yield self.feature[batch_indices], self.label[batch_indices]
```

数据量是这么来的：4177 行，`ratio = 0.8`，`offset = int(4177 * 0.8) = 3341`，所以训练集 3341 行、测试集 836 行。

`range(0, 3341, 64)` 依次给出 0、64、128……；`min(...)` 那行负责兜住最后一批，因为 3341 不是 64 的整数倍。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "训练集 3341 条、`batch_size = 64`，最后一批有几条？",
                kind = "short",
                expect = "13|十三",
            ),
            hints = listOf(
                "先算 3341 里能装下几个完整的 64。",
                "64 × 52 = 3328。",
                "3341 - 3328 = 13，所以最后一批 13 条，共 53 批。",
            ),
            onCorrect = "对，13 条，一共 53 批。没有 `min` 的话，最后一批会切到 3392 越界；numpy 会安静地只返回剩下的部分，看起来也对，但换成别的框架就可能报错。`min` 把这件写明确了。",
            summary = listOf("3341 = 52 × 64 + 13", "`min` 兜住不整除的尾巴"),
        ),
        StepContent(
            n = 10,
            blocks = listOf(
                Block(
                    "core",
                    """
网络是四层 Linear 串起来，层间夹 ReLU，最后一层直接输出：

```python
self.net = nn.Sequential(
    nn.Linear(input_dim, 128), nn.ReLU(),
    nn.Linear(128, 64),       nn.ReLU(),
    nn.Linear(64, 32),        nn.ReLU(),
    nn.Linear(32, 1),         # ← 后面没有 ReLU
)
```

最后一层输出维度是 1，对应一个年龄数值，后面不加激活。

这和分类网络相反：分类要在最后接 softmax 把 logits 变成概率，回归要的就是那个数本身。如果这里加了 ReLU，输出会被截断在 0 以上 —— 恰好本题标签都是正数，看起来也能跑，但模型再也不能输出任何负值，0 以下的梯度恒为 0，等于焊死了一半输出空间。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "如果最后一层后面误加了 `ReLU`，最直接的后果是什么？",
                kind = "choice",
                choices = listOf(
                    Choice("负的输出被一律压成 0，那一侧梯度也断了", "负的输出被一律压成 0，那一侧梯度也断了"),
                    Choice("输出变成概率，需要再乘 100", "输出变成概率，需要再乘 100"),
                    Choice("形状从 (bs,1) 变成 (bs,)", "形状从 (bs,1) 变成 (bs,)"),
                    Choice("会立刻报错", "会立刻报错"),
                ),
                answer = "负的输出被一律压成 0，那一侧梯度也断了",
            ),
            hints = listOf(
                "ReLU 的定义是 `max(0, x)`。",
                "输出被夹在 0 以上之后，本该输出负值的地方全被压成 0，这些位置的梯度恒为 0。",
                "模型失去一半表达能力，而且不报错。",
            ),
            onCorrect = "对。判断标准就一句：输出要什么就留什么。回归留线性，二分类留一个 logit 配 sigmoid，多分类留 logits 配 softmax。",
            summary = listOf("回归输出层不加激活", "误加 ReLU 会截断负值并杀死梯度"),
        ),
        StepContent(
            n = 11,
            blocks = listOf(
                Block(
                    "core",
                    """
```python
loss_fn = nn.MSELoss()
```

预测年龄是回归问题：答案是连续的数（1 到 29 环），不是类别。MSE 直接惩罚预测值与真实值差多少：

${'$'}MSE = \frac{1}{n}\sum (\hat{y} - y)^2${'$'}

交叉熵是给分类用的，衡量两个概率分布的差异。把 rings 当成 29 个类别去分类当然也能做，但那等于丢掉「7 环和 8 环很接近」这个信息 —— 分错相邻的环和被判成完全无关的类别，在交叉熵眼里惩罚一样重。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "真实 8 环，预测 9 环和预测 20 环，MSE 分别惩罚多少？",
                kind = "choice",
                choices = listOf(
                    Choice("1 和 144，差得越远罚得越重", "1 和 144，差得越远罚得越重"),
                    Choice("都是 1，只要猜错就一样", "都是 1，只要猜错就一样"),
                    Choice("1 和 12，惩罚与差距成正比", "1 和 12，惩罚与差距成正比"),
                    Choice("无法比较，MSE 不接受单个样本", "无法比较，MSE 不接受单个样本"),
                ),
                answer = "1 和 144，差得越远罚得越重",
            ),
            hints = listOf(
                "MSE 里有个平方。",
                "`(9-8)² = 1`，`(20-8)² = 144`。",
                "差 1 环罚 1，差 12 环罚 144 —— 平方把大错放大了。",
            ),
            onCorrect = "对。平方让 MSE 对离群点特别敏感，这就是它的性格：宁可稳一点，也不接受偶尔错得离谱。数据里有 29 环的极端样本，它会拽着模型往那边偏。",
            summary = listOf("回归用 MSE，分类用交叉熵", "平方把大误差放大"),
        ),
        StepContent(
            n = 12,
            blocks = listOf(
                Block(
                    "core",
                    """
训练日志里那个 `Acc` 不是准确率，是这个：

```python
def accuracy(pred_val, true_val):
    ss_res = paddle.sum(paddle.square(pred_val - true_val))
    ss_tot = paddle.sum(paddle.square(true_val - paddle.mean(true_val)))
    if ss_tot.item() == 0:
        return 1.0 if ss_res.item() == 0 else 0.0
    r2 = 1.0 - ss_res / ss_tot
    return r2.item()
```

`ss_res` 是模型没解释掉的残差平方和，`ss_tot` 是「如果一律猜平均值会产生的误差平方和」。两者一比：

${'$'}R^2 = 1 - \frac{ss_{res}}{ss_{tot}}${'$'}

- `R² = 1`：完美拟合，残差为 0
- `R² = 0`：和直接猜平均值一样烂
- `R² < 0`：比猜平均值还差

`ss_tot == 0` 那个分支防的是「真实值全一样」的退化情况 —— 此时分母为 0，公式失效。教科书常省略它，真实数据上必须写。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "某个 epoch 打印出 `Acc = -0.12`，说明什么？",
                kind = "choice",
                choices = listOf(
                    Choice("模型还不如一律猜平均值", "模型还不如一律猜平均值"),
                    Choice("准确率是负数，说明代码有 bug", "准确率是负数，说明代码有 bug"),
                    Choice("还差 12% 达到完美", "还差 12% 达到完美"),
                    Choice("标签被归一化坏了", "标签被归一化坏了"),
                ),
                answer = "模型还不如一律猜平均值",
            ),
            hints = listOf(
                "R² 的基准线是 0，不是 1。",
                "`1 - ss_res/ss_tot` 里，若残差比总方差还大，比值大于 1，结果就是负数。",
                "所以负值意味着：这个模型的预测连「全部猜平均值」这个最笨的基线都不如。",
            ),
            onCorrect = "对。R² 的 0 点对应猜平均值，这条基线比随机猜高得多，所以负的 R² 是很严重的信号 —— 通常意味着学习率太大、数据没归一化，或者标签列切错了。",
            summary = listOf("这里打印的 Acc 其实是 R²", "R²=0 等于猜平均值，负值比基线还差"),
        ),
        StepContent(
            n = 13,
            blocks = listOf(
                Block(
                    "core",
                    """
训练循环里有两组容易被抄漏的开关：

```python
for epoch in range(num_epochs):
    model.train()                    # 训练态
    for batch_x, batch_y in train_provider:
        pred = model(batch_x)
        loss = loss_fn(pred, batch_y)
        loss.backward()
        optimizer.step()
        optimizer.clear_grad()       # 不清零就累加

    model.eval()                     # 评估态
    with paddle.no_grad():           # 不建计算图
        test_pred = model(X_test)
        test_loss = loss_fn(test_pred, Y_test).item()
```

`train()` 和 `eval()` 切换的是层的行为：Dropout 只在训练时随机关掉神经元，BatchNorm 只在训练时更新统计量。本题没有这两层，所以切换看不出差别 —— 但一旦网络里加了 Dropout，漏掉 `eval()` 评估结果就会每次都不一样。

`no_grad()` 省的是显存和计算：评估不需要反向传播，也就不需要保留中间结果。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "评估测试集时漏写了 `with paddle.no_grad():`，会怎样？",
                kind = "choice",
                choices = listOf(
                    Choice("数值一样，但白占显存、白算一遍图", "数值一样，但白占显存、白算一遍图"),
                    Choice("测试集的参数会被更新", "测试集的参数会被更新"),
                    Choice("R² 会算错", "R² 会算错"),
                    Choice("会报错，eval 模式必须配 no_grad", "会报错，eval 模式必须配 no_grad"),
                ),
                answer = "数值一样，但白占显存、白算一遍图",
            ),
            hints = listOf(
                "`no_grad` 管的是要不要记录梯度，不管参数要不要改。",
                "改参数只发生在 `optimizer.step()`，而评估那段没有调用它。",
                "所以数值不受影响，代价是显存和速度。",
            ),
            onCorrect = "对，这是性能问题不是正确性问题 —— 但显存不够时它会以 OOM 的形式变成正确性问题。顺带记住：漏掉 `clear_grad()` 的后果才是真的算错，梯度会在批次之间累加。",
            summary = listOf("`train()/eval()` 切层行为，`no_grad()` 省显存", "漏掉 `clear_grad()` 才会算错"),
        ),
        StepContent(
            n = 14,
            blocks = listOf(
                Block(
                    "core",
                    """
最后一步推理，取一条测试样本喂进模型：

```python
idx = np.random.randint(0, len(test_data))
single_x = paddle.to_tensor(test_data[idx, :-1].reshape(1, -1).astype(np.float32))
true_age = test_data[idx, -1]

with paddle.no_grad():
    pred_age = infer_model(single_x).item()
```

`test_data[idx, :-1]` 是形状 `(8,)` 的一条样本，但模型要的是一批样本，也就是二维。

`reshape(1, -1)` 把那 8 个数摆成 `(1, 8)`：第一维 1 表示这批只有一条，`-1` 让 numpy 自己算剩下的维度。换成长度不同的输入时，`-1` 不用改。

`astype(np.float32)` 也不是可选项：paddle 默认权重是 float32，喂 float64 进去会报类型不匹配。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "`reshape(1, -1)` 里那个 `-1` 被算成了几？",
                kind = "choice",
                choices = listOf(
                    Choice("8", "8"),
                    Choice("1", "1"),
                    Choice("4177", "4177"),
                    Choice("-1 是字面值，不参与计算", "-1 是字面值，不参与计算"),
                ),
                answer = "8",
            ),
            hints = listOf(
                "一条样本里有几个特征？",
                "`test_data[idx, :-1]` 取的是前 8 列。",
                "这一维有 8 个元素，总数 8 ÷ 1 = 8，所以 `-1` 就是 8。",
            ),
            onCorrect = "对。`reshape` 的铁律：元素总数必须守恒。8 个元素摆成 `(1,8)` 行，摆成 `(2,4)` 也行，摆成 `(1,9)` 会直接报错。",
            summary = listOf("单条样本也要摆成 (1, 特征数)", "`-1` 由 numpy 按元素总数反推", "paddle 要 float32"),
        ),
        StepContent(
            n = 15,
            blocks = listOf(
                Block(
                    "core",
                    """
`normalize_data` 一进来先干两件看起来很浪费的事：

```python
def normalize_data(train_data, test_data):
    train_data = train_data.copy()
    test_data = test_data.copy()
    ...
    for i in range(feature_num):
        train_data[:, i] = (train_data[:, i] - min_vals[i]) / range_vals[i]
        test_data[:, i] = (test_data[:, i] - min_vals[i]) / range_vals[i]
```

注意下面那个赋值 `train_data[:, i] = ...`：它写进的是**原数组**，不是新建一个。切片赋值在 numpy 里是原地操作。

所以如果没有 `copy()`，`normalize_data(train_raw, test_raw)` 调用完，`preprocessing` 里的 `train_raw` 也被改成了归一化之后的矩阵。后面任何想用原始数值算极值、或者想打印一条原始样本的代码，拿到的都是被压扁的数。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "去掉那两行 `copy()`，`preprocessing` 里的 `train_raw` 会变成什么？",
                kind = "choice",
                choices = listOf(
                    Choice("也被归一化改写了，和返回的数组内容相同", "也被归一化改写了，和返回的数组内容相同"),
                    Choice("保持不变，numpy 参数默认传副本", "保持不变，numpy 参数默认传副本"),
                    Choice("会变成 NaN", "会变成 NaN"),
                    Choice("会报错，函数内不能改参数", "会报错，函数内不能改参数"),
                ),
                answer = "也被归一化改写了，和返回的数组内容相同",
            ),
            hints = listOf(
                "Python 传的是引用，numpy 数组不是不可变对象。",
                "`train_data[:, i] = ...` 是对数组元素直接赋值。",
                "所以调用方手里的 `train_raw` 和函数里的 `train_data` 指向同一块内存，改一个就是改两个。",
            ),
            onCorrect = "对。这类副作用最难查：函数声明上写着「传入两个数组」，行为上却把两个数组都改了。凡是函数里出现 `arr[...] = ...`，先问一句要不要 copy。",
            summary = listOf("切片赋值是原地操作", "函数里改参数前，先 copy"),
        ),
        StepContent(
            n = 16,
            blocks = listOf(
                Block(
                    "core",
                    """
读取循环里有两处对「坏行」的处理，态度完全不同：

```python
for row in reader:
    if not row:          # 空行：csv 返回 []，直接跳过
        continue

    sex = row[0]
    ...
    else:
        break            # 未知性别：整份文件不再往下读
```

空行被安静跳过，这是合理的 —— 文件末尾多一个换行不该毁掉整个数据集。

但如果某行**列数不够**，比如只有 8 个字段，`row[1:]` 就只有 7 个元素，拼出来的列表长度是 8，而别的行是 9。这时候 `np.array(data)` 拿到的是一堆长度不齐的列表。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "有一行少了一列，`np.array(data)` 会怎样？",
                kind = "choice",
                choices = listOf(
                    Choice("新版 numpy 直接抛形状不规则的错，旧版退化成 object 数组", "新版 numpy 直接抛形状不规则的错，旧版退化成 object 数组"),
                    Choice("自动用 0 补齐成 9 列", "自动用 0 补齐成 9 列"),
                    Choice("抛 IndexError，指向读文件那一行", "抛 IndexError，指向读文件那一行"),
                    Choice("正常得到 (4177, 8) 的矩阵", "正常得到 (4177, 8) 的矩阵"),
                ),
                answer = "新版 numpy 直接抛形状不规则的错，旧版退化成 object 数组",
            ),
            hints = listOf(
                "numpy 想要一个规则的多维数组。",
                "长度不齐时，新版本会报 inhomogeneous shape；老版本会造一个 dtype=object 的数组。",
                "object 数组更阴：能创建成功，但里面的元素是 Python 列表，向量化运算全失效。",
            ),
            onCorrect = "对。所以读完数据要先断言 `data.shape == (4177, 9)`。这句断言一旦写上，第 3 步那个 break 造成的丢行也会被一起拦住。",
            summary = listOf("空行跳过、坏行 break，是两种态度", "读完立刻断言形状"),
        ),
        StepContent(
            n = 17,
            blocks = listOf(
                Block(
                    "core",
                    """
`preprocessing` 里的切分方式是硬切：

```python
offset = int(raw_data.shape[0] * ratio)   # 0.8 × 4177 = 3341
train_raw = raw_data[:offset]
test_raw = raw_data[offset:]
```

前十行、后十行原样保留，没有打乱。这在教学代码里很常见，但要清楚它什么时候会出事：**如果文件是按类别排序的，测试集可能只剩某一类**。

这份文件恰好没出事。实测两边的性别占比：训练集 M 36.9% / F 32.0% / I 31.1%，测试集 F 28.5% / M 35.3% / I 36.2%。分布接近，所以模型不会因为「没见过的类别」而崩。

但这是运气，不是保证。数据来源换一份、排序方式变一下，同一个 `0.8` 就可能把某一类全划进测试集。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "想让这件事不靠运气，最小改动是哪个？",
                kind = "choice",
                choices = listOf(
                    Choice("切分前先用 np.random.shuffle 打乱整份数据", "切分前先用 np.random.shuffle 打乱整份数据"),
                    Choice("把 ratio 从 0.8 改成 0.9", "把 ratio 从 0.8 改成 0.9"),
                    Choice("把 batch_size 调大", "把 batch_size 调大"),
                    Choice("在归一化里加一点随机噪声", "在归一化里加一点随机噪声"),
                ),
                answer = "切分前先用 np.random.shuffle 打乱整份数据",
            ),
            hints = listOf(
                "问题出在「哪些行进测试集」，不在比例大小。",
                "改成 0.9 只会改变切点，不改变「连续取一段」这件事。",
                "先 shuffle 再切，才能让测试集变成总体的随机抽样。",
            ),
            onCorrect = "对。而且要注意顺序：**先打乱、再切分、最后归一化**。如果先归一化再打乱，极值仍然是从训练集算的，没问题；但先切分再打乱就会把训练数据混进测试集。这三步的顺序不能颠倒。",
            summary = listOf("硬切的前提是文件本身无序", "先 shuffle → 再切 → 再归一化"),
        ),
        StepContent(
            n = 18,
            blocks = listOf(
                Block(
                    "core",
                    """
`main()` 的第一行就是设备选择：

```python
def main():
    paddle.set_device("gpu:0")
    print("训练设备:", paddle.get_device())
```

写 `gpu:0` 而不是 `gpu`，是因为一台机器可能有多张卡，`:0` 指定第一张。

这两行的位置也很关键：`set_device` 只影响**它之后**创建的 tensor 和层。如果你把它挪到 `preprocessing()` 后面，或者放进训练循环里，前面已经建在 CPU 上的 tensor 不会自动搬过去，混合设备的运算会报错。

题目里那句「请确保训练过程是载入 GPU 进行的训练」，验的就是 `paddle.get_device()` 打印出来是不是 `gpu:0`。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "`paddle.set_device(\"gpu:0\")` 最晚必须写在什么之前？",
                kind = "choice",
                choices = listOf(
                    Choice("第一次 `paddle.to_tensor` 或建层之前", "第一次 `paddle.to_tensor` 或建层之前"),
                    Choice("训练循环开始之前就行", "训练循环开始之前就行"),
                    Choice("保存模型之前", "保存模型之前"),
                    Choice("写在哪儿都一样，paddle 会自己找卡", "写在哪儿都一样，paddle 会自己找卡"),
                ),
                answer = "第一次 `paddle.to_tensor` 或建层之前",
            ),
            hints = listOf(
                "它设的是「默认设备」这个全局状态。",
                "状态只对之后创建的东西生效，已经建好的 tensor 不会搬家。",
                "所以最晚要在第一次 `to_tensor` 或 `nn.Linear` 之前。",
            ),
            onCorrect = "对。这也是为什么本机没装 CUDA 时会直接报错停在第一行 —— 反而比跑到一半才炸要好。想临时跑 CPU，把这个字符串改成 `cpu` 就行。",
            summary = listOf("`set_device` 只影响之后创建的 tensor", "验证方式是打印 `get_device()`"),
        ),
        StepContent(
            n = 19,
            blocks = listOf(
                Block(
                    "core",
                    """
训练结束，参数是这么存的：

```python
paddle.save(model.state_dict(), model_path)      # 存的是参数字典，不是模型对象
```

推理时先重建结构，再把参数灌进去：

```python
infer_model = MLP(input_dim=train_data[:, :-1].shape[1])
infer_model.set_dict(paddle.load(model_path))
infer_model.eval()
```

为什么不整个模型存下来、直接 load 就能用？因为 `state_dict` 只是一堆张量（每层的权重和偏置），而网络结构写在 `MLP` 这个类里。换机器、换 paddle 版本、甚至只是改了类名，结构都还在你的代码里，参数字典照样能对上。

这也带来一个纪律：`input_dim` 必须和训练时一致，否则 `set_dict` 会因为形状不匹配而失败。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "推理时为什么必须先 `MLP(input_dim=...)` 再 `set_dict`？",
                kind = "choice",
                choices = listOf(
                    Choice("state_dict 里只有参数，网络结构得自己重建", "state_dict 里只有参数，网络结构得自己重建"),
                    Choice("因为 GPU 显存不够，得分批加载", "因为 GPU 显存不够，得分批加载"),
                    Choice("paddle 不支持直接加载整个模型", "paddle 不支持直接加载整个模型"),
                    Choice("为了跳过 `__init__` 里的初始化", "为了跳过 `__init__` 里的初始化"),
                ),
                answer = "state_dict 里只有参数，网络结构得自己重建",
            ),
            hints = listOf(
                "打开保存的文件，里面是什么？",
                "是 `{'net.0.weight': ..., 'net.0.bias': ...}` 这样的键值对。",
                "键名对应层的路径，所以得有一个结构相同、键名一致的对象来接。",
            ),
            onCorrect = "对。`set_dict` 之后还有一句 `eval()` —— 少了它，如果网络里有 Dropout，推理结果每次都不一样。",
            summary = listOf("存参数不存结构", "重建结构时 `input_dim` 必须一致", "加载后记得 `eval()`"),
        ),
        StepContent(
            n = 20,
            blocks = listOf(
                Block(
                    "core",
                    """
训练日志里那个 `Train Acc` 是这么算出来的：

```python
for batch_x, batch_y in train_provider:
    ...
    acc = accuracy(pred, batch_y)     # 这一批的 R²
    epoch_loss += loss.item()
    epoch_acc += acc
    n_batches += 1

# 打印时
epoch_acc / n_batches
```

它是**各批次 R² 的算术平均**，不是把整个训练集拼起来算一次。

两件事会让它和「整集 R²」有出入：一是 R² 对分子分母都做了平方和，本身不是线性可加的，平均下来不等于总和；二是最后一批只有 13 条，和前面 63 条的批次权重一样。

差别通常不大（都是 0.0x 级别），但你知道它是平均值，就不会在写论文时把它当成严格指标。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "这个 Train Acc 与「整集算一次 R²」相比，通常是？",
                kind = "choice",
                choices = listOf(
                    Choice("略有偏差，因为它是按批平均且小批次权重相同", "略有偏差，因为它是按批平均且小批次权重相同"),
                    Choice("完全相等，R² 可以按批线性相加", "完全相等，R² 可以按批线性相加"),
                    Choice("一定偏大", "一定偏大"),
                    Choice("一定偏小", "一定偏小"),
                ),
                answer = "略有偏差，因为它是按批平均且小批次权重相同",
            ),
            hints = listOf(
                "R² 里有除法，还有平方和，不是线性的。",
                "53 批里有一批只有 13 条，但它和 63 条的批次各占 1/53。",
                "所以这个数字是「看趋势用的」，不是最终报告的指标。",
            ),
            onCorrect = "对。想要严格数字，训练完再把 `X_train` 整份过一遍算 R²。这也解释了为什么测试集那边可以直接算 —— 它是一次性全量前向。",
            summary = listOf("按批平均不等于整集计算", "报告用的指标要重算一遍"),
        ),
        StepContent(
            n = 21,
            blocks = listOf(
                Block(
                    "core",
                    """
到处出现的 `.item()`：

```python
loss.item()
test_loss = loss_fn(test_pred, Y_test).item()
pred_age = infer_model(single_x).item()
```

`loss` 是一个 0 维 tensor，可能还躺在 GPU 上。`.item()` 把它取成一个 Python 浮点数 —— 这个过程必须把数据从显存搬回内存，**并且要等 GPU 把那一步算完**。

paddle 默认是异步执行的：你提交 `loss.backward()`，GPU 排进队列就返回了，CPU 继续往下跑。一旦你调 `.item()`，CPU 就不得不停下来等队列里的活干完。

所以每个 batch 都 `loss.item()` 一次，等于在训练里插了 53 × 100 次同步点。为了打日志值得，但别在循环里为了「看看数」而到处 `.item()`。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "每个 batch 都调 `loss.item()`，代价是什么？",
                kind = "choice",
                choices = listOf(
                    Choice("触发一次设备同步，CPU 要等 GPU 算完", "触发一次设备同步，CPU 要等 GPU 算完"),
                    Choice("显存会一直涨，最后 OOM", "显存会一直涨，最后 OOM"),
                    Choice("会把梯度清零", "会把梯度清零"),
                    Choice("精度会从 float32 掉到 float16", "精度会从 float32 掉到 float16"),
                ),
                answer = "触发一次设备同步，CPU 要等 GPU 算完",
            ),
            hints = listOf(
                "GPU 计算是排队异步执行的。",
                "`.item()` 需要那个具体的数值，就必须等它算出来。",
                "所以它是一道同步屏障，本身不改变任何数值。",
            ),
            onCorrect = "对。想减少同步，可以攒几个 batch 的 loss 再一起取，或者只在每 N 个 batch 打印一次 —— 你现在这份代码是每批都取，属于「宁可慢也要看清楚」。",
            summary = listOf("`.item()` 是同步点，不是转换成本为零", "日志频率是可以调的旋钮"),
        ),
        StepContent(
            n = 22,
            blocks = listOf(
                Block(
                    "core",
                    """
把这一路的判断收成一个场景：训练 100 轮之后，`Train Acc` 到了 0.85，`Test Acc` 只有 0.52。

这种「训练集明显好于测试集」的差距叫过拟合。要压住它，方向是**降低模型对训练数据细节的记忆**，而不是让它算得更久。

练习：从下面四个动作里挑出最该先做的那一个。
                    """.trimIndent(),
                ),
            ),
            question = Question(
                prompt = "Train R² 0.85、Test R² 0.52，最该先做哪个？",
                kind = "choice",
                choices = listOf(
                    Choice("加 Dropout 层，并确认评估时调了 `model.eval()`", "加 Dropout 层，并确认评估时调了 `model.eval()`"),
                    Choice("把 epochs 从 100 调到 1000", "把 epochs 从 100 调到 1000"),
                    Choice("把 batch_size 改成 1", "把 batch_size 改成 1"),
                    Choice("去掉归一化", "去掉归一化"),
                ),
                answer = "加 Dropout 层，并确认评估时调了 `model.eval()`",
            ),
            hints = listOf(
                "训练集好、测试集差，说明模型记住了训练样本的细节。",
                "多半是参数太多或训练太久。加深、加久都只会更糟。",
                "Dropout 在训练时随机关掉一部分神经元，逼网络不依赖某几个特定连接；评估时必须 `eval()` 关掉它。",
            ),
            onCorrect = "对。另外三个都是反向操作：训练更久更过拟合，batch=1 梯度噪声更大，去掉归一化会让不同尺度的特征互相干扰。真要看效果，Dropout 之外还可以减小隐藏层宽度、加早停。",
            summary = listOf("训练好于测试即为过拟合", "先加正则，别加训练量"),
        ),
    ).associateBy { it.n }
}
