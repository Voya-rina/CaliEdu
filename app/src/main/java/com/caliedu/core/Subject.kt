package com.caliedu.core

/**
 * 一级分类。题目千奇百怪，但归到十几个大类里就够了——
 * 分类太细等于没有分类，筛选的时候没人会去点「待定常数法」。
 */
object Subject {

    val ALL = listOf(
        "高等数学", "线性代数", "概率统计", "离散数学",
        "C语言", "C++", "Python", "Java",
        "数据结构", "算法", "机器学习", "数据处理",
        "其他",
    )

    /** 模型爱写的各种变体，统一折到大类上。 */
    private val aliases = mapOf(
        "高数" to "高等数学", "微积分" to "高等数学", "数学分析" to "高等数学",
        "数学" to "高等数学", "代数" to "线性代数", "线代" to "线性代数",
        "矩阵" to "线性代数", "概率" to "概率统计", "概率论" to "概率统计",
        "数理统计" to "概率统计", "统计" to "概率统计",
        "c" to "C语言", "c语言" to "C语言", "cpp" to "C++", "c++" to "C++",
        "c＋＋" to "C++", "python" to "Python", "java" to "Java",
        "数据结构与算法" to "数据结构", "算法与数据结构" to "算法",
        "深度学习" to "机器学习", "神经网络" to "机器学习", "ai" to "机器学习",
        "数据分析" to "数据处理", "pandas" to "数据处理", "numpy" to "数据处理",
        "编程" to "Python",
    )

    /** 把任意写法折成大类；折不动就返回 null，由调用方兜底。 */
    fun match(raw: String): String? {
        val key = raw.trim().lowercase().replace(" ", "")
        if (key.isEmpty()) return null
        ALL.firstOrNull { it.lowercase() == key }?.let { return it }
        aliases[key]?.let { return it }
        return ALL.firstOrNull { key.contains(it.lowercase()) || it.lowercase().contains(key) }
    }

    /** 输入里没有任何线索时，至少按题型给一个。 */
    fun fallback(track: Track, text: String): String = when (track) {
        Track.MATH -> "高等数学"
        Track.GENERAL -> "其他"
        Track.CODE -> when {
            text.contains("def ") || text.contains("import ") || text.contains("numpy") -> "Python"
            text.contains("printf") || text.contains("#include") -> "C语言"
            text.contains("public static void main") -> "Java"
            else -> "Python"
        }
    }

    /**
     * 每个大类「只有在这个学科里才会这么说」的词。
     *
     * 分两套：语言类的词是「讲这门语言本身」才用的（列表推导、装饰器），
     * 而不是「用这门语言写东西」时满地都是的 import / def。
     * 不然一份用 numpy 讲回归的讲义会被判成 Python，而它其实是机器学习。
     */
    private val lexicon: Map<String, List<String>> = mapOf(
        "机器学习" to listOf(
            "神经网络", "训练集", "测试集", "验证集", "归一化", "标准化", "梯度", "损失函数",
            "反向传播", "过拟合", "欠拟合", "交叉验证", "回归", "分类器", "数据集", "偏置",
            "激活函数", "学习率", "隐藏层", "均方误差", "混淆矩阵", "特征工程", "sklearn",
        ),
        "概率统计" to listOf(
            "概率", "期望", "方差", "正态分布", "泊松", "置信区间", "假设检验", "贝叶斯",
            "随机变量", "独立同分布", "抽样", "大数定律", "中心极限",
        ),
        "线性代数" to listOf(
            "矩阵", "行列式", "特征值", "特征向量", "线性相关", "正交", "转置", "逆矩阵",
            "初等变换", "向量空间", "相似对角化", "秩",
        ),
        "高等数学" to listOf(
            "导数", "微分", "积分", "极限", "级数", "泰勒", "洛必达", "偏导数", "二重积分",
            "收敛", "单调", "极值", "渐近线", "不定积分",
        ),
        "离散数学" to listOf(
            "图论", "命题", "合取", "析取", "布尔", "归纳法", "集合论", "二元关系", "同构",
        ),
        "数据结构" to listOf(
            "链表", "二叉树", "哈希表", "平衡树", "邻接表", "图的遍历", "红黑树", "栈", "队列",
        ),
        "算法" to listOf(
            "动态规划", "贪心", "二分查找", "回溯", "分治", "时间复杂度", "空间复杂度", "排序算法",
        ),
        "数据处理" to listOf(
            "pandas", "dataframe", "缺失值", "数据清洗", "透视表", "分组聚合", "索引对齐", "数据框",
        ),
        "Python" to listOf(
            "列表推导", "装饰器", "生成器", "虚拟环境", "pip install", "解释器", "缩进",
            "面向对象", "异常处理", "鸭子类型", "python",
        ),
        "C语言" to listOf(
            "指针", "结构体", "头文件", "malloc", "printf", "数组越界", "内存泄漏", "编译链接", "c语言",
        ),
        "C++" to listOf(
            "模板", "析构", "虚函数", "命名空间", "智能指针", "引用传参", "stl", "c++",
        ),
        "Java" to listOf(
            "jvm", "泛型", "字节码", "接口实现", "spring", "maven", "java",
        ),
    )

    private const val MIN_HITS = 2
    private const val SAMPLE = 40_000

    /**
     * 按内容猜一级分类，猜不出来返回 null。
     *
     * 只看开头一段：讲义的主题词在导言和前两章就全出现了，
     * 扫完 17 万字换不来更多信息，还会拖慢打开记录的速度。
     */
    fun guess(raw: String, title: String = ""): String? {
        val body = (title + "\n" + raw.take(SAMPLE)).lowercase()
        if (body.isBlank()) return null
        var best: String? = null
        var bestScore = 0
        lexicon.forEach { (major, words) ->
            val score = words.count { body.contains(it) }
            if (score > bestScore) {
                bestScore = score
                best = major
            }
        }
        return if (bestScore >= MIN_HITS) best else null
    }

    /**
     * 落库前把 tags 洗一遍：第一个必须是固定大类，后面保留模型的细分说法（最多两个）。
     */
    fun normalize(raw: List<String>, track: Track, source: String): List<String> {
        val cleaned = raw.map { it.trim() }.filter { it.isNotEmpty() }
        val major = cleaned.firstNotNullOfOrNull { match(it) } ?: fallback(track, source)
        val minor = cleaned
            .mapNotNull { match(it)?.let { _ -> null } ?: it.takeIf { t -> t.length in 2..8 } }
            .filterNot { it == major }
            .distinct()
            .take(2)
        return listOf(major) + minor
    }
}
