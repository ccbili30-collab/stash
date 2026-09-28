package io.github.ccbili30.stash.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 视觉规范 v2 招牌组件：CardGroup 分组列表。
 * 一组行包在一张 tonal 卡里（外角 20dp，组内相邻行角 4dp，shadow 0）；
 * 行被按下时外角动画收拢到 4dp，松开弹回 —— 按压试圆角是整套 UI 的辨识点。
 * 行为对齐 M3 ListItem 布局（leading 24dp / 最小高 56dp），因按压圆角需要
 * 自持 interactionSource，行本体为手写等价布局。
 */
@Composable
fun CardGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 0.dp,
    ) {
        Column(content = content)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CardGroupRow(
    position: Int,
    total: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    headline: @Composable () -> Unit,
    supporting: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // 平时：首行上角 20，末行下角 20，中间相邻边 4；按下时整体收拢到 4
    val idleTop = if (position == 0) 20.dp else 4.dp
    val idleBottom = if (position == total - 1) 20.dp else 4.dp
    val top by animateDpAsState(
        targetValue = if (pressed) 4.dp else idleTop,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>(),
        label = "rowTopCorner",
    )
    val bottom by animateDpAsState(
        targetValue = if (pressed) 4.dp else idleBottom,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>(),
        label = "rowBottomCorner",
    )

    val clickable = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(
                RoundedCornerShape(
                    topStart = top,
                    topEnd = top,
                    bottomStart = bottom,
                    bottomEnd = bottom,
                ),
            )
            .then(clickable)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            headline()
            if (supporting != null) {
                Spacer(Modifier.size(2.dp))
                supporting()
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}
