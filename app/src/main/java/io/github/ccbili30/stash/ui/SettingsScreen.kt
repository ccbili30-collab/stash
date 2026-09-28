package io.github.ccbili30.stash.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ccbili30.stash.BuildConfig
import io.github.ccbili30.stash.data.SettingsStore
import io.github.ccbili30.stash.data.StashRepository
import io.github.ccbili30.stash.service.MediaScreenshotWatcher
import io.github.ccbili30.stash.service.StashAccessibilityService
import io.github.ccbili30.stash.update.UpdateChecker
import io.github.ccbili30.stash.update.UpdateState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenA11yGuide: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    val autoScreenshot by settings.autoScreenshotFlow.collectAsStateWithLifecycle(initialValue = false)
    val dynamicColor by settings.dynamicColorFlow.collectAsStateWithLifecycle(initialValue = true)
    val a11yEnabled = remember { mutableStateOf(StashAccessibilityService.isEnabled(context)) }

    // 从无障碍设置页返回时刷新状态
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                a11yEnabled.value = StashAccessibilityService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        scope.launch {
            settings.setAutoScreenshot(granted)
            if (granted) MediaScreenshotWatcher.startIfPermitted(context)
        }
    }

    // ---- 更新流程状态 ----
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var release by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    var downloadedApk by remember { mutableStateOf<java.io.File?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) { HiIcon(HiIcons.ArrowLeft01, contentDescription = "返回") }
                },
                title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
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
            SectionTitle("收集")
            CardGroup {
                CardGroupRow(
                    position = 0,
                    total = 2,
                    headline = { Text("自动收截屏") },
                    supporting = { Text("系统相册出现新截屏时自动复制进 Stash（需要相册权限，只读截屏目录）") },
                    trailing = {
                        Switch(
                            checked = autoScreenshot,
                            onCheckedChange = { want ->
                                scope.launch {
                                    if (want) {
                                        val perm = if (Build.VERSION.SDK_INT >= 33) {
                                            Manifest.permission.READ_MEDIA_IMAGES
                                        } else {
                                            @Suppress("DEPRECATION")
                                            Manifest.permission.READ_EXTERNAL_STORAGE
                                        }
                                        if (MediaScreenshotWatcher.hasPermission(context)) {
                                            settings.setAutoScreenshot(true)
                                            MediaScreenshotWatcher.startIfPermitted(context)
                                        } else {
                                            permissionLauncher.launch(perm)
                                        }
                                    } else {
                                        settings.setAutoScreenshot(false)
                                        MediaScreenshotWatcher.stop()
                                    }
                                }
                            },
                        )
                    },
                )
                CardGroupRow(
                    position = 1,
                    total = 2,
                    headline = { Text("临时截图") },
                    supporting = {
                        Text(
                            when {
                                a11yEnabled.value && StashAccessibilityService.isRunning ->
                                    "已开启：下拉通知栏点「Stash 临时截图」磁贴唤起悬浮球"
                                a11yEnabled.value ->
                                    "服务被系统停住了：到无障碍设置里把它关掉再重新打开"
                                else ->
                                    "未开启：点这里按引导开启一次，之后磁贴一键截屏"
                            },
                        )
                    },
                    leading = {
                        val ok = a11yEnabled.value && StashAccessibilityService.isRunning
                        HiIcon(
                            when {
                                ok -> HiIcons.Scan01
                                a11yEnabled.value -> HiIcons.Alert01
                                else -> HiIcons.Accessibility01
                            },
                            size = 24.dp,
                            tint = when {
                                ok -> MaterialTheme.colorScheme.primary
                                a11yEnabled.value -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    onClick = { if (!a11yEnabled.value) onOpenA11yGuide() },
                )
            }

            SectionTitle("外观")
            CardGroup {
                CardGroupRow(
                    position = 0,
                    total = 1,
                    headline = { Text("动态取色") },
                    supporting = { Text("跟随壁纸取色（Material You）；关闭后使用 Ocean 蓝预设") },
                    trailing = {
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = { scope.launch { settings.setDynamicColor(it) } },
                        )
                    },
                )
            }

            SectionTitle("关于")
            CardGroup {
                CardGroupRow(
                    position = 0,
                    total = 2,
                    headline = { Text("版本") },
                    supporting = { Text("${BuildConfig.VERSION_NAME} · 数据仅存本机") },
                )
                CardGroupRow(
                    position = 1,
                    total = 2,
                    headline = { Text("检查更新") },
                    supporting = {
                        Text(
                            when (val s = updateState) {
                                is UpdateState.Idle -> "更新来自 GitHub Releases（不稳时可用浏览器下载）"
                                is UpdateState.Checking -> "正在检查…"
                                is UpdateState.Latest -> "已是最新版"
                                is UpdateState.Error -> s.message
                                else -> "有新版本"
                            },
                        )
                    },
                    trailing = {
                        HiIcon(HiIcons.Download01, size = 20.dp)
                    },
                    onClick = {
                        updateState = UpdateState.Checking
                        scope.launch {
                            try {
                                val rel = UpdateChecker.check(BuildConfig.VERSION_NAME)
                                release = rel
                                updateState = if (rel == null) {
                                    UpdateState.Latest(BuildConfig.VERSION_NAME)
                                } else {
                                    UpdateState.Available(rel)
                                }
                            } catch (e: Exception) {
                                // 网络断 ≠ 没新版，明确报错而不是谎报"已是最新"
                                updateState = UpdateState.Error("检查失败：${e.message ?: "网络问题"}")
                                release = null
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // 更新对话框
    val rel = release
    if (rel != null && updateState !is UpdateState.Checking &&
        updateState !is UpdateState.Latest
    ) {
        AlertDialog(
            onDismissRequest = {
                if (updateState !is UpdateState.Downloading) updateState = UpdateState.Idle
            },
            title = { Text("新版本 ${rel.tag}") },
            text = {
                Column {
                    if (rel.notes.isNotBlank()) {
                        Text(rel.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                    val s = updateState
                    if (s is UpdateState.Downloading) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "下载中 ${(s.progress * 100).toInt()}%（断流会自动重试）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (s is UpdateState.Error) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                when (val s = updateState) {
                    is UpdateState.Available -> Button(onClick = {
                        updateState = UpdateState.Downloading(0f)
                        scope.launch {
                            runCatching {
                                UpdateChecker.download(context, rel) { p ->
                                    updateState = UpdateState.Downloading(p)
                                }
                            }.onSuccess { apk ->
                                downloadedApk = apk
                                updateState = UpdateState.ReadyToInstall(apk)
                            }.onFailure { e ->
                                updateState = UpdateState.Error("下载失败：${e.message}。网络太差就点「浏览器下载」。")
                            }
                        }
                    }) { Text("下载更新") }

                    is UpdateState.Downloading -> TextButton(onClick = {}) {
                        Text("${(s.progress * 100).toInt()}%")
                    }

                    is UpdateState.ReadyToInstall -> Button(onClick = {
                        val started = UpdateChecker.install(context, s.apk)
                        if (!started) {
                            android.widget.Toast.makeText(
                                context,
                                "请允许「来自此来源安装」后，回来再点安装",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    }) { Text("安装") }

                    is UpdateState.Error -> Button(onClick = {
                        updateState = UpdateState.Downloading(0f)
                        scope.launch {
                            runCatching {
                                UpdateChecker.download(context, rel) { p ->
                                    updateState = UpdateState.Downloading(p)
                                }
                            }.onSuccess { apk ->
                                updateState = UpdateState.ReadyToInstall(apk)
                            }.onFailure { e ->
                                updateState = UpdateState.Error("下载失败：${e.message}")
                            }
                        }
                    }) { Text("重试") }

                    else -> {}
                }
            },
            dismissButton = {
                if (updateState !is UpdateState.Downloading) {
                    // 浏览器兜底：系统浏览器自带断点续传，对付烂网络最可靠
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://github.com/ccbili30-collab/stash/releases/latest"),
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        updateState = UpdateState.Idle
                    }) { Text("浏览器下载") }
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
    )
}
