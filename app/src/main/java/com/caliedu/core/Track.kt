package com.caliedu.core

/**
 * 一道题走哪条流水线。数学题是一条推导链，编程题是一份结构图，
 * 两者的拆法、讲义形态、题目形态都不一样，不能共用一套 prompt。
 */
enum class Track(val key: String, val label: String) {
    CODE("code", "编程"),
    MATH("math", "数学"),
    GENERAL("general", "概念"),
    ;

    companion object {
        fun of(key: String?): Track {
            val k = key?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.key == k } ?: GENERAL
        }
    }
}

/**
 * 本地先猜一次题型，决定第一次请求带哪套 prompt。
 * 模型在返回里给的 kind 有最终否决权，所以这里只要求「别把数学题认成编程题」。
 */
object TrackDetector {

    private val codeWords = listOf(
        "输入", "输出", "程序", "代码", "编写", "写一个", "实现一个", "算法", "时间复杂度", "空间复杂度",
        "数组", "字符串", "列表", "字典", "循环", "遍历", "递归", "函数定义", "报错", "编译",
        "排序", "冒泡", "二分", "链表", "二叉树", "哈希", "栈", "队列",
        "python", "numpy", "pandas", "print", "return", "def ", "class ",
    )

    private val mathWords = listOf(
        "证明", "求证", "求解", "化简", "计算", "已知", "求", "试证", "最大值", "最小值",
        "极值", "导数", "微分", "积分", "数列", "通项", "极限", "收敛", "发散",
        "概率", "期望", "方差", "分布", "矩阵", "行列式", "向量", "函数",
        "方程", "不等式", "区间", "定义域", "值域", "单调", "递增", "递减",
        "配方", "换元", "三角形", "椭圆", "双曲线", "抛物线", "复数",
        "排列", "组合", "整除", "余数", "面积", "体积", "周长", "面积",
    )

    private const val MATH_SYMBOLS = "=+×÷≤≥≠≈∞∑∏∫√∂πθαβγλμσφωΔ±∈⊂∪∩∠"

    fun detect(text: String): Track {
        if (CodeDigest.looksLikeCode(text)) return Track.CODE
        // 只看开头一段：题型特征集中在题面，整份两千行的讲义扫完也不会有更多信息。
        val body = text.trim().take(6000)
        if (body.isEmpty()) return Track.GENERAL

        val code = codeWords.count { body.contains(it, ignoreCase = true) }
        if (code >= 2) return Track.CODE

        val words = mathWords.count { body.contains(it) }
        val symbols = body.count { it in MATH_SYMBOLS || it == '-' }
        val digits = body.count { it.isDigit() }
        val per100 = 100f / body.length

        // 关键词权重最高：中文数学题的式子往往很少，"已知 / 求 / 证明"才是硬信号。
        val score = words * 2.4f + symbols * per100 * 6f + digits * per100 * 3f
        return if (words >= 2 || score >= 9f) Track.MATH else Track.GENERAL
    }

    /** 界面上给用户看的一句话，说明为什么走了这条线。 */
    fun why(track: Track): String = when (track) {
        Track.CODE -> "按结构块拆"
        Track.MATH -> "按推导关节拆"
        Track.GENERAL -> "按概念接点拆"
    }
}
