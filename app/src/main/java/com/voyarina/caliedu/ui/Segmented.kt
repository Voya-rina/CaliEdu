package com.voyarina.caliedu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 一行三段的选择器，对齐 Material3 的 SegmentedButton：
 * 外框一颗胶囊，选中的那段是实色底、其余透明，中间不画分隔线。
 */
@Composable
fun SegmentedRow(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val skin = LocalSkin.current
    val outer = RoundedCornerShape(if (skin.pillChoices) 100.dp else 10.dp)
    val inner = RoundedCornerShape(if (skin.pillChoices) 100.dp else 8.dp)

    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(skin.codeBg, outer)
            .border(0.5.dp, skin.line, outer)
            .padding(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            val bg by animateColorAsState(
                targetValue = if (on) skin.surface else Color.Transparent,
                animationSpec = tween(180),
                label = "segBg",
            )
            val fg by animateColorAsState(
                targetValue = if (on) skin.accentDeep else skin.sub,
                animationSpec = tween(180),
                label = "segFg",
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(bg, inner)
                    .then(
                        if (on && skin.style != UiStyle.PRINT) {
                            Modifier.border(0.5.dp, skin.line, inner)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = (skin.smallSize.value * 0.95f).sp,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    color = fg,
                    maxLines = 1,
                )
            }
        }
    }
}
