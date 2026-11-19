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
