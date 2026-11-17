package com.caliedu.stepedu.core

/**
 * 代码文件可能几千行，整段塞进上下文既贵又容易让模型走神。
 * 这里只从本地提取结构（不算语义），把"骨架"交给模型而不是全文。
 */
object CodeDigest {

    private val importRe = Regex("""^\s*(?:from\s+([\w.]+)\s+import|import\s+([\w.,\s]+))""")
    private val defRe = Regex("""^(\s*)def\s+(\w+)\s*\((.*?)\)""")
    private val classRe = Regex("""^(\s*)class\s+(\w+)""")

    fun looksLikeCode(text: String): Boolean {
        val lines = text.lines()
        if (lines.size < 3) return false
        val hits = lines.count { line ->
            val t = line.trimStart()
            t.startsWith("import ") || t.startsWith("from ") || t.startsWith("def ") ||
                t.startsWith("class ") || t.startsWith("#!") || t.startsWith("if __name__")
        }
        return hits >= 2
    }

    fun shouldDigest(text: String): Boolean = text.lines().size > 150

    fun digest(text: String): String {
        val lines = text.lines()
        val imports = mutableListOf<String>()
        val defs = mutableListOf<String>()
        val classes = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            importRe.find(line)?.let { m ->
                val name = m.groupValues[1].ifBlank { m.groupValues[2] }.trim()
                if (name.isNotEmpty()) imports += name
                return@forEachIndexed
            }
            defRe.find(line)?.let { m ->
                val indent = m.groupValues[1].length
                defs += "${if (indent > 0) "  方法" else "函数"} ${m.groupValues[2]}(${m.groupValues[3]}) 第 ${index + 1} 行"
                return@forEachIndexed
            }
            classRe.find(line)?.let { m ->
                classes += "类 ${m.groupValues[2]} 第 ${index + 1} 行"
            }
        }
        return buildString {
            append("【结构摘要】共 ").append(lines.size).append(" 行\n")
            if (imports.isNotEmpty()) append("导入：").append(imports.distinct().joinToString(", ")).append('\n')
            if (classes.isNotEmpty()) append("类：\n  ").append(classes.take(20).joinToString("\n  ")).append('\n')
            if (defs.isNotEmpty()) append("函数：\n  ").append(defs.take(40).joinToString("\n  ")).append('\n')
            append("\n【完整代码】\n")
            append(lines.take(200).joinToString("\n"))
            if (lines.size > 200) append("\n…（后面 ${lines.size - 200} 行省略，需要时再问）")
        }
    }
}
