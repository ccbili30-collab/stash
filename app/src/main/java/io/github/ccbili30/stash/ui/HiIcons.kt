package io.github.ccbili30.stash.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ccbili30.stash.R

/**
 * 图标族：stroke 细线圆头风格（Lucide 实现，命名沿用 HugeIcons 体系）。
 * 单色 tint 渲染；顶栏动作 24dp，行内 20dp，按钮内 18dp。
 */
object HiIcons {
    val Search01 = R.drawable.ic_hi_search01
    val Settings01 = R.drawable.ic_hi_settings01
    val Tag01 = R.drawable.ic_hi_tag01
    val Link01 = R.drawable.ic_hi_link01
    val Image01 = R.drawable.ic_hi_image01
    val Camera01 = R.drawable.ic_hi_camera01
    val Scan01 = R.drawable.ic_hi_scan01
    val Delete01 = R.drawable.ic_hi_delete01
    val Edit01 = R.drawable.ic_hi_edit01
    val Share01 = R.drawable.ic_hi_share01
    val ArrowLeft01 = R.drawable.ic_hi_arrowleft01
    val Cancel01 = R.drawable.ic_hi_cancel01
    val Download01 = R.drawable.ic_hi_download01
    val Refresh01 = R.drawable.ic_hi_refresh01
    val ArrowUpRight01 = R.drawable.ic_hi_arrowupright01
    val Add01 = R.drawable.ic_hi_add01
    val Tick01 = R.drawable.ic_hi_tick01
    val Alert01 = R.drawable.ic_hi_alert01
    val Archive01 = R.drawable.ic_hi_archive01
    val Globe01 = R.drawable.ic_hi_globe01
    val Accessibility01 = R.drawable.ic_hi_accessibility01
    val Information01 = R.drawable.ic_hi_information01
    val Notification01 = R.drawable.ic_hi_notification01
    val ArrowDown01 = R.drawable.ic_hi_arrowdown01
    val ArrowUp01 = R.drawable.ic_hi_arrowup01
}

@Composable
fun HiIcon(
    @DrawableRes icon: Int,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    Box(modifier = modifier.size(size), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}
