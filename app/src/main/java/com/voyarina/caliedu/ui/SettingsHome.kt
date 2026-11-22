package com.voyarina.caliedu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voyarina.caliedu.UiState
import com.voyarina.caliedu.data.ChannelMode
import com.voyarina.caliedu.data.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsHome(
    state: UiState,
    onStyle: () -> Unit,
    onModel: () -> Unit,
    onLibrary: () -> Unit,
    onScale: (Float) -> Unit,
    onScaleCommit: () -> Unit,
) {
    val skin = LocalSkin.current
    // 版本号从安装包读，不认 BuildConfig：包名改过一次，编译期常量会跟着变。
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(skin.bg)
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("设置", fontSize = (skin.heroSize.value * 0.72f).sp, fontWeight = FontWeight.Bold, color = skin.text)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(22.dp))
            Group("外观")
            SettingRow(
                icon = Icons.Rounded.Palette,
                title = "风格",
                trailing = state.style.label,
                onClick = onStyle,
            )
            Spacer(Modifier.height(8.dp))
            ScaleCard(
                scale = state.textScale,
                onChange = onScale,
                onCommit = onScaleCommit,
            )

            Spacer(Modifier.height(20.dp))
            Group("模型")
            SettingRow(
                icon = Icons.Rounded.Key,
                title = "模型与批改",
                trailing = buildString {
                    append(state.settings.mode.label)
                    if (state.settings.mode == ChannelMode.DIRECT) {
                        append(if (state.settings.configured) " · 已配置" else " · 未配置")
                    }
                },
                accent = state.settings.canTalk,
                onClick = onModel,
            )

            Spacer(Modifier.height(20.dp))
            Group("资料")
            SettingRow(
                icon = Icons.Rounded.FolderOpen,
                title = "资料",
                trailing = when {
                    state.libraryBusy -> "读取中…"
                    state.library.hasAny -> "${state.library.visibleItems.size} 个"
                    else -> "未选择"
                },
                accent = state.library.hasAny,
                onClick = onLibrary,
            )

            Spacer(Modifier.height(20.dp))
            Group("关于")
            SettingRow(
                icon = Icons.Rounded.Info,
                title = "版本",
                trailing = version.ifBlank { "—" },
                onClick = null,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Group(label: String) {
    val skin = LocalSkin.current
    Text(
        label,
        fontSize = skin.fs(11.5f),
        letterSpacing = 0.6.sp,
        color = skin.sub,
        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    trailing: String,
    accent: Boolean = false,
    onClick: (() -> Unit)?,
) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusCard)
    Row(
        Modifier
            .fillMaxWidth()
            .background(skin.surface, shape)
            .border(if (skin.borderWidth > 0.dp) skin.borderWidth else 0.5.dp, skin.line, shape)
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
            .padding(horizontal = 15.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = skin.accent, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(13.dp))
        Text(title, fontSize = skin.bodySize, color = skin.text, modifier = Modifier.weight(1f))
        if (accent) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(skin.accent, RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(trailing, fontSize = skin.fs(12.5f), color = skin.sub)
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = skin.sub,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 字号放在设置首页直接拖，拖完松手才写盘。 */
@Composable
private fun ScaleCard(scale: Float, onChange: (Float) -> Unit, onCommit: () -> Unit) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(skin.radiusCard)
    Column(
        Modifier
            .fillMaxWidth()
            .background(skin.surface, shape)
            .border(if (skin.borderWidth > 0.dp) skin.borderWidth else 0.5.dp, skin.line, shape)
            .padding(horizontal = 15.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.FormatSize,
                contentDescription = null,
                tint = skin.accent,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(13.dp))
            Text("字号", fontSize = skin.bodySize, color = skin.text, modifier = Modifier.weight(1f))
            Text(
                "${(scale * 100).roundToInt()}%",
                fontSize = skin.fs(12.5f),
                color = if (scale > 1.001f) skin.accentDeep else skin.sub,
            )
        }
        Slider(
            value = scale,
            onValueChange = onChange,
            valueRange = 0.9f..1.4f,
            steps = 9,
            onValueChangeFinished = onCommit,
            colors = SliderDefaults.colors(
                thumbColor = skin.accent,
                activeTrackColor = skin.accent,
                inactiveTrackColor = skin.line,
            ),
        )
        Row {
            Text("小", fontSize = skin.fs(10.5f), color = skin.sub)
            Spacer(Modifier.weight(1f))
            Text("大", fontSize = skin.fs(10.5f), color = skin.sub)
        }
    }
}

// ---------------------------------------------------------------- 风格页

@Composable
fun StyleScreen(
    style: UiStyle,
    dark: Boolean,
    themeMode: ThemeMode,
    onPick: (UiStyle) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    val skin = LocalSkin.current
    Column(
        Modifier
            .fillMaxSize()
            .background(skin.bg)
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        PageHeader("风格", onBack)
        Text(
            "选了立刻生效，自动保存",
            fontSize = skin.fs(11.5f),
            color = skin.sub,
            modifier = Modifier.padding(start = 2.dp, top = 2.dp),
        )

        Spacer(Modifier.height(16.dp))
        Text("深浅色", fontSize = skin.fs(11.5f), letterSpacing = 0.6.sp, color = skin.sub, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
        SegmentedRow(
            options = listOf("跟随系统", "浅色", "深色"),
            selected = themeMode.ordinal,
            onSelect = { index -> onTheme(ThemeMode.entries[index]) },
        )

        Spacer(Modifier.height(18.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UiStyle.entries.forEach { option ->
                StyleCard(
                    option = option,
                    selected = option == style,
                    onClick = { onPick(option) },
                    skin = skin,
                    dark = dark,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StyleCard(option: UiStyle, selected: Boolean, onClick: () -> Unit, skin: Skin, dark: Boolean) {
    // 卡片底色/文字色都取「被预览的那套皮肤」，不是当前皮肤。
    // 否则会出现外壳是 Material 的近白紫、里面的纸是纯白，两圈颜色对不上。
    val o = skinOf(option, dark)
    val shape = RoundedCornerShape(o.radiusCard)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(o.bg, shape)
            // 选中环也用这套皮肤自己的主色：否则会是「蓝环套紫卡」，两种配色打架。
            .border(if (selected) 2.dp else 1.dp, if (selected) o.accent else o.line, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        StylePreviewPage(option, dark)
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    option.label,
                    fontSize = skin.bodySize,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = o.text,
                )
                Text(option.note, fontSize = skin.fs(11.5f), color = o.sub)
            }
            if (selected) {
                Box(
                    Modifier
                        .size(skin.ui(18f))
                        .background(o.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", fontSize = skin.fs(11f), color = o.onAccent)
                }
            }
        }
    }
}

/**
 * 缩略页：不是画几条灰线，而是用那套皮肤真渲染一小段讲义
 * （标题 + 正文 + 语法高亮代码 + 选项），跟实际学习页同源。
 */
@Composable
private fun StylePreviewPage(option: UiStyle, dark: Boolean) {
    val s = skinOf(option, dark)
    val shape = RoundedCornerShape(if (s.pillChoices) 16.dp else 8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .height(154.dp)
            .clip(shape)
            .background(s.bg, shape)
            .border(0.5.dp, s.line, shape)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (s.sectionBar) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(12.dp)
                        .background(s.accent, RoundedCornerShape(1.dp))
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                "第 3 步 · else 后面为什么是 break",
                fontSize = s.fs(7.5f),
                fontWeight = FontWeight.Bold,
                color = if (s.style == UiStyle.PRINT) s.accentDeep else s.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "缩进层级决定这行 break 挂在哪层循环上，写错就只跳出 if。",
            fontSize = s.fs(6.5f),
            lineHeight = s.fs(9.5f),
            color = s.text.copy(alpha = 0.85f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(s.codeBg, RoundedCornerShape(4.dp))
                .border(if (s.codeBorder.alpha > 0f) 0.5.dp else 0.dp, s.codeBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            Text(
                text = CodeHi.highlight(
                    "for row in rows:\n    if row[0] == 0:\n        break  # 只跳 if",
                    "python",
                    s,
                ),
                fontFamily = s.mono,
                fontSize = s.fs(5.8f),
                lineHeight = s.fs(8f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OptionPill(s, "只跳出 if", true)
            OptionPill(s, "跳出整个 for", false)
        }
    }
}

@Composable
private fun OptionPill(s: Skin, label: String, correct: Boolean) {
    val shape = RoundedCornerShape(s.radiusChoice)
    val bg = when {
        correct && s.filledCorrect -> s.accent
        correct -> s.accentSoft
        else -> s.surface
    }
    val fg = when {
        correct && s.filledCorrect -> s.onAccent
        correct -> s.accentDeep
        else -> s.sub
    }
    Text(
        label,
        fontSize = s.fs(6f),
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .background(bg, shape)
            .border(0.5.dp, if (correct) s.accent else s.line, shape)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}
