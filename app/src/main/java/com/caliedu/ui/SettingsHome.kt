package com.caliedu.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caliedu.UiState
import com.caliedu.data.ChannelMode

@Composable
fun SettingsHome(
    state: UiState,
    onStyle: () -> Unit,
    onModel: () -> Unit,
    onClear: () -> Unit,
    onTheme: () -> Unit,
) {
    val skin = LocalSkin.current

    Column(
        Modifier
            .fillMaxSize()
            .background(skin.bg)
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("设置", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = skin.text)

        Spacer(Modifier.height(22.dp))
        Group("外观")
        SettingRow(
            icon = Icons.Rounded.Palette,
            title = "风格",
            trailing = state.style.label,
            onClick = onStyle,
        )
        Spacer(Modifier.height(8.dp))
        SettingRow(
            icon = Icons.Rounded.DarkMode,
            title = "深浅色",
            trailing = state.themeMode.label,
            onClick = onTheme,
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
        Group("数据")
        SettingRow(
            icon = Icons.Rounded.DeleteSweep,
            title = "清空记录",
            trailing = "${state.records.size} 条",
            onClick = if (state.records.isEmpty()) null else onClear,
        )

        Spacer(Modifier.height(20.dp))
        Group("关于")
        SettingRow(
            icon = Icons.Rounded.Info,
            title = "版本",
            trailing = com.caliedu.BuildConfig.VERSION_NAME,
            onClick = null,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Group(label: String) {
    val skin = LocalSkin.current
    Text(
        label,
        fontSize = 11.5.sp,
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
        Text(trailing, fontSize = 12.5.sp, color = skin.sub)
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

@Composable
fun StyleScreen(
    style: UiStyle,
    dark: Boolean,
    themeMode: com.caliedu.data.ThemeMode,
    onPick: (UiStyle) -> Unit,
    onCycleTheme: () -> Unit,
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
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(skin.codeBg, RoundedCornerShape(100.dp))
                .clickable(onClick = onCycleTheme)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.DarkMode,
                contentDescription = null,
                tint = skin.accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text("深浅色", fontSize = skin.smallSize, color = skin.text)
            Spacer(Modifier.weight(1f))
            Text(themeMode.label, fontSize = 12.sp, color = skin.sub)
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            UiStyle.entries.forEach { option ->
                StyleCard(
                    option = option,
                    selected = option == style,
                    onClick = { onPick(option) },
                    skin = skin,
                    dark = dark,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StyleCard(option: UiStyle, selected: Boolean, onClick: () -> Unit, skin: Skin, dark: Boolean) {
    val shape = RoundedCornerShape(skin.radiusCard)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) skin.accentSoft else skin.surface, shape)
            .border(if (selected) 1.5.dp else 0.5.dp, if (selected) skin.accent else skin.line, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StylePreview(option, dark)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                option.label,
                fontSize = skin.bodySize,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) skin.accentDeep else skin.text,
            )
            Text(option.note, fontSize = 11.5.sp, color = skin.sub)
        }
        if (selected) {
            Box(
                Modifier
                    .size(18.dp)
                    .background(skin.accent, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", fontSize = 11.sp, color = skin.onAccent)
            }
        }
    }
}

@Composable
private fun StylePreview(option: UiStyle, dark: Boolean) {
    val s = skinOf(option, dark)
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .width(60.dp)
            .height(44.dp)
            .background(s.bg, shape)
            .border(0.5.dp, s.line, shape)
            .padding(6.dp)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(32.dp)
                .background(s.accent, RoundedCornerShape(1.dp))
        )
        Spacer(Modifier.width(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(0.85f)
                    .height(3.dp)
                    .background(s.text.copy(alpha = 0.8f), RoundedCornerShape(1.dp))
            )
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(3.dp)
                    .background(s.sub.copy(alpha = 0.55f), RoundedCornerShape(1.dp))
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(s.codeBg, RoundedCornerShape(2.dp))
            )
        }
    }
}
