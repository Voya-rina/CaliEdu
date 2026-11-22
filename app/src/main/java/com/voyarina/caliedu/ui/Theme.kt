package com.voyarina.caliedu.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class UiStyle(val key: String, val label: String, val note: String) {
    PRINT("print", "md2pdf 版式", "白纸、蓝标、印刷行距"),
    APPLE("apple", "Apple", "分组底、留白、系统蓝"),
    MATERIAL("material", "Material You", "大圆角、色调层"),
    ;

    companion object {
        fun of(key: String?): UiStyle = entries.firstOrNull { it.key == key } ?: PRINT
    }
}

/**
 * 一套界面语言。正文字号、行距、代码配色取自 md2pdf 的 CSS，
 * 三种风格只在容器形状与色层上分叉。
 *
 * [scale] 是用户拖出来的全局字号倍率。所有写死的字号都必须走 [fs]，
 * 否则调大字之后按钮上那几行小字会原地不动。
 */
@Immutable
data class Skin(
    val style: UiStyle,
    val bg: Color,
    val surface: Color,
    val text: Color,
    val sub: Color,
    val line: Color,
    val accent: Color,
    val accentDeep: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val wrong: Color,
    val wrongSoft: Color,
    val amber: Color,
    val amberSoft: Color,
    val okSoft: Color,
    val codeBg: Color,
    val codeBorder: Color,
    val codePlain: Color,
    val codeKw: Color,
    val codeStr: Color,
    val codeNum: Color,
    val codeCmt: Color,
    val codeFn: Color,
    val radiusCard: Dp,
    val radiusBlock: Dp,
    val radiusChoice: Dp,
    val borderWidth: Dp,
    val bodySize: TextUnit,
    val bodyLine: TextUnit,
    val smallSize: TextUnit,
    val stepTitleSize: TextUnit,
    val heroSize: TextUnit,
    val stepTitleWeight: FontWeight,
    val justify: Boolean,
    val sectionBar: Boolean,
    val pillChoices: Boolean,
    val filledCorrect: Boolean,
    val chromeUppercase: Boolean,
    val mono: FontFamily,
    val scale: Float = 1f,
) {
    /** 写死的字号统一过这里：`fontSize = skin.fs(11f)`。 */
    fun fs(base: Float): TextUnit = (base * scale).sp

    /**
     * 控件尺寸（圆按钮、图标、胶囊高度）。只跟七成：
     * 全跟会把布局撑破，不跟又显得字大了壳没大。
     */
    fun ui(base: Float): Dp = (base * (1f + (scale - 1f) * 0.7f)).dp
}

private val PrintSkin = Skin(
    style = UiStyle.PRINT,
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF1F2937),
    sub = Color(0xFF6B7280),
    line = Color(0xFFE5E7EB),
    accent = Color(0xFF2563EB),
    accentDeep = Color(0xFF1E40AF),
    accentSoft = Color(0xFFEFF6FF),
    onAccent = Color(0xFFFFFFFF),
    wrong = Color(0xFFB91C1C),
    wrongSoft = Color(0xFFFEF2F2),
    amber = Color(0xFFB45309),
    amberSoft = Color(0xFFFFFBEB),
    okSoft = Color(0xFFECFDF5),
    codeBg = Color(0xFFF6F8FB),
    codeBorder = Color(0xFFDBE3EE),
    codePlain = Color(0xFF1F2937),
    codeKw = Color(0xFF7C3AED),
    codeStr = Color(0xFF047857),
    codeNum = Color(0xFFB45309),
    codeCmt = Color(0xFF64748B),
    codeFn = Color(0xFF0369A1),
    radiusCard = 8.dp,
    radiusBlock = 8.dp,
    radiusChoice = 4.dp,
    borderWidth = 1.dp,
    bodySize = 14.5.sp,
    bodyLine = 25.sp,
    smallSize = 13.sp,
    stepTitleSize = 18.sp,
    heroSize = 27.sp,
    stepTitleWeight = FontWeight.Bold,
    justify = true,
    sectionBar = true,
    pillChoices = false,
    filledCorrect = false,
    chromeUppercase = false,
    mono = FontFamily.Monospace,
)

private val AppleSkin = Skin(
    style = UiStyle.APPLE,
    bg = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF000000),
    sub = Color(0xFF8E8E93),
    line = Color(0xFFD9D9DE),
    accent = Color(0xFF007AFF),
    accentDeep = Color(0xFF0055CC),
    accentSoft = Color(0xFFE5F1FF),
    onAccent = Color(0xFFFFFFFF),
    wrong = Color(0xFFD70015),
    wrongSoft = Color(0xFFFFEBEA),
    amber = Color(0xFF9A6B12),
    amberSoft = Color(0xFFFFF6E0),
    okSoft = Color(0xFFE8F9EE),
    codeBg = Color(0xFFF7F7FA),
    codeBorder = Color(0x00000000),
    codePlain = Color(0xFF1C1C1E),
    codeKw = Color(0xFFAD3DA4),
    codeStr = Color(0xFFC41A16),
    codeNum = Color(0xFF1C00CF),
    codeCmt = Color(0xFF8E8E93),
    codeFn = Color(0xFF326D74),
    radiusCard = 12.dp,
    radiusBlock = 12.dp,
    radiusChoice = 12.dp,
    borderWidth = 0.dp,
    bodySize = 15.5.sp,
    bodyLine = 24.sp,
    smallSize = 13.sp,
    stepTitleSize = 22.sp,
    heroSize = 30.sp,
    stepTitleWeight = FontWeight.Bold,
    justify = false,
    sectionBar = false,
    pillChoices = false,
    filledCorrect = false,
    chromeUppercase = true,
    mono = FontFamily.Monospace,
)

private val MaterialSkin = Skin(
    style = UiStyle.MATERIAL,
    // 底色用 surfaceContainerLow、卡片用 surface：两者原来几乎同色，
    // 卡片只剩一圈描边，看起来像「颜色没对齐」。Material 3 本来就是这么分层的。
    bg = Color(0xFFF7F2FA),
    surface = Color(0xFFFEF7FF),
    text = Color(0xFF1D1B20),
    sub = Color(0xFF49454F),
    line = Color(0xFFE6E0E9),
    accent = Color(0xFF6750A4),
    accentDeep = Color(0xFF4F378B),
    accentSoft = Color(0xFFE8DEF8),
    onAccent = Color(0xFFFFFFFF),
    wrong = Color(0xFFB3261E),
    wrongSoft = Color(0xFFFCEEEE),
    amber = Color(0xFF7D5260),
    amberSoft = Color(0xFFF7ECEF),
    okSoft = Color(0xFFDCEFE4),
    codeBg = Color(0xFFF3EDF7),
    codeBorder = Color(0x00000000),
    codePlain = Color(0xFF1D1B20),
    codeKw = Color(0xFF7C4DFF),
    codeStr = Color(0xFF0A7A5A),
    codeNum = Color(0xFFB45309),
    codeCmt = Color(0xFF79747E),
    codeFn = Color(0xFF1D5FA8),
    // Material 3 的卡片是 12~16dp，不是 24。24 的弧线会压到 12~15dp 内边距里的第一行字上，
    // 选项更不该做成整圆胶囊——两行文字的左端会顶到弧线上。
    radiusCard = 16.dp,
    radiusBlock = 12.dp,
    radiusChoice = 12.dp,
    borderWidth = 1.dp,
    bodySize = 15.sp,
    bodyLine = 23.sp,
    smallSize = 13.sp,
    stepTitleSize = 22.sp,
    heroSize = 29.sp,
    stepTitleWeight = FontWeight.Medium,
    justify = false,
    sectionBar = false,
    pillChoices = true,
    filledCorrect = true,
    chromeUppercase = false,
    mono = FontFamily.Monospace,
)

/**
 * 暗色不是把亮色反相：纸感的那套夜里要变成"深色纸"，代码高亮得整体降饱和再提亮，
 * 否则紫色关键字在深底上会糊成一片。
 */
private val PrintDark = PrintSkin.copy(
    bg = Color(0xFF14151A),
    surface = Color(0xFF1B1D23),
    text = Color(0xFFE5E7EB),
    sub = Color(0xFF9CA3AF),
    line = Color(0xFF2A2D36),
    accent = Color(0xFF60A5FA),
    accentDeep = Color(0xFF93C5FD),
    accentSoft = Color(0xFF1B2738),
    onAccent = Color(0xFF0B1220),
    wrong = Color(0xFFF87171),
    wrongSoft = Color(0xFF37191B),
    amber = Color(0xFFFBBF24),
    amberSoft = Color(0xFF33280F),
    okSoft = Color(0xFF13301F),
    codeBg = Color(0xFF1B1E25),
    codeBorder = Color(0xFF2F3542),
    codePlain = Color(0xFFE5E7EB),
    codeKw = Color(0xFFC4B5FD),
    codeStr = Color(0xFF6EE7B7),
    codeNum = Color(0xFFFCD34D),
    codeCmt = Color(0xFF94A3B8),
    codeFn = Color(0xFF7DD3FC),
)

private val AppleDark = AppleSkin.copy(
    bg = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    text = Color(0xFFFFFFFF),
    sub = Color(0xFF8E8E93),
    line = Color(0xFF38383A),
    accent = Color(0xFF0A84FF),
    accentDeep = Color(0xFF64B5FF),
    accentSoft = Color(0xFF0B2540),
    onAccent = Color(0xFFFFFFFF),
    wrong = Color(0xFFFF453A),
    wrongSoft = Color(0xFF3A1512),
    amber = Color(0xFFFFD60A),
    amberSoft = Color(0xFF332B0A),
    okSoft = Color(0xFF10261A),
    codeBg = Color(0xFF1C1C1E),
    codeBorder = Color(0xFF2C2C2E),
    codePlain = Color(0xFFF2F2F7),
    codeKw = Color(0xFFFC5FA3),
    codeStr = Color(0xFFFC6A5D),
    codeNum = Color(0xFFD0BF69),
    codeCmt = Color(0xFF7F8C98),
    codeFn = Color(0xFF67B7A4),
)

private val MaterialDark = MaterialSkin.copy(
    bg = Color(0xFF141218),
    surface = Color(0xFF211F26),
    text = Color(0xFFE6E0E9),
    sub = Color(0xFFCAC4D0),
    line = Color(0xFF3B3742),
    accent = Color(0xFFD0BCFF),
    accentDeep = Color(0xFFEADDFF),
    accentSoft = Color(0xFF3A2E5C),
    onAccent = Color(0xFF381E72),
    wrong = Color(0xFFF2B8B5),
    wrongSoft = Color(0xFF3F1D1B),
    amber = Color(0xFFE8C36A),
    amberSoft = Color(0xFF382C12),
    okSoft = Color(0xFF1B3A2A),
    codeBg = Color(0xFF2B2930),
    codeBorder = Color(0xFF49454F),
    codePlain = Color(0xFFE6E0E9),
    codeKw = Color(0xFFD0BCFF),
    codeStr = Color(0xFF7FD1B9),
    codeNum = Color(0xFFF2C77B),
    codeCmt = Color(0xFF938F99),
    codeFn = Color(0xFFA8C7FA),
)

fun skinOf(style: UiStyle, dark: Boolean = false): Skin = when (style) {
    UiStyle.PRINT -> if (dark) PrintDark else PrintSkin
    UiStyle.APPLE -> if (dark) AppleDark else AppleSkin
    UiStyle.MATERIAL -> if (dark) MaterialDark else MaterialSkin
}

val LocalSkin = staticCompositionLocalOf { PrintSkin }

/**
 * 只放大"读的字"，不动容器半径和留白——否则整页会像被放大镜压过。
 * [scale] 取 0.9~1.4，1.0 是设计稿原尺寸。
 *
 * 除了几个具名 token，Skin 里还记下 scale 本身，供 [Skin.fs] / [Skin.ui]
 * 给写死的字号和控件尺寸兜底——否则调大字号后只有正文变、按钮上的小字不变。
 */
fun Skin.scaled(scale: Float): Skin {
    if (scale == 1f) return copy(scale = 1f)
    // 所有字号统一乘同一个倍率：小的那几档再单独压一压，会出现「正文变大了、辅助字没变」的错位。
    return copy(
        bodySize = (bodySize.value * scale).sp,
        bodyLine = (bodyLine.value * scale).sp,
        smallSize = (smallSize.value * scale).sp,
        stepTitleSize = (stepTitleSize.value * scale).sp,
        heroSize = (heroSize.value * scale).sp,
        scale = scale,
    )
}

@Composable
fun CaliTheme(
    style: UiStyle,
    dark: Boolean = false,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val skin = remember(style, dark, textScale) { skinOf(style, dark).scaled(textScale) }
    val scheme = remember(skin, dark) {
        if (dark) darkColorScheme(
            primary = skin.accent,
            onPrimary = skin.onAccent,
            background = skin.bg,
            onBackground = skin.text,
            surface = skin.surface,
            onSurface = skin.text,
            surfaceVariant = skin.codeBg,
            onSurfaceVariant = skin.sub,
            outline = skin.line,
        ) else lightColorScheme(
            primary = skin.accent,
            onPrimary = skin.onAccent,
            background = skin.bg,
            onBackground = skin.text,
            surface = skin.surface,
            onSurface = skin.text,
            surfaceVariant = skin.codeBg,
            onSurfaceVariant = skin.sub,
            outline = skin.line,
        )
    }
    CompositionLocalProvider(LocalSkin provides skin) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
