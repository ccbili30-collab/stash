package io.github.ccbili30.stash.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.ccbili30.stash.service.StashAccessibilityService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun A11yGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(StashAccessibilityService.isEnabled(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = StashAccessibilityService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) { HiIcon(HiIcons.ArrowLeft01, contentDescription = "返回") }
                },
                title = { Text("开启临时截图", style = MaterialTheme.typography.titleLarge) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            CardGroup {
                CardGroupRow(
                    position = 0,
                    total = 4,
                    leading = { HiIcon(HiIcons.Accessibility01, size = 24.dp) },
                    headline = { Text("第 1 步") },
                    supporting = { Text("点下方按钮打开系统「无障碍」设置") },
                )
                CardGroupRow(
                    position = 1,
                    total = 4,
                    leading = { HiIcon(HiIcons.Search01, size = 24.dp) },
                    headline = { Text("第 2 步") },
                    supporting = { Text("在列表里找到「已下载的应用」，进入「Stash 临时截图服务」") },
                )
                CardGroupRow(
                    position = 2,
                    total = 4,
                    leading = { HiIcon(HiIcons.Scan01, size = 24.dp) },
                    headline = { Text("第 3 步") },
                    supporting = { Text("打开开关并允许。Stash 声明了不读取屏幕内容，只截屏") },
                )
                CardGroupRow(
                    position = 3,
                    total = 4,
                    leading = {
                        HiIcon(
                            if (enabled) HiIcons.Tick01 else HiIcons.Alert01,
                            size = 24.dp,
                            tint = if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    headline = { Text(if (enabled) "已开启 ✓" else "完成上面的步骤后回到这里") },
                    supporting = {
                        Text(
                            if (enabled) "下拉通知栏，把「Stash 临时截图」磁贴拖到常用位置，一点即截"
                            else "开启后回到此页会自动显示对勾",
                        )
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (enabled) "再看看无障碍设置" else "打开无障碍设置")
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HiIcon(HiIcons.Information01, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    "临时截图只进 Stash，相册完全无感知",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
