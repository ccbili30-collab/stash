package io.github.ccbili30.stash.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.ccbili30.stash.data.Entry
import io.github.ccbili30.stash.data.EntryType
import io.github.ccbili30.stash.data.StashRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    entryId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { StashRepository.get(context) }
    val item by repo.observeEntry(entryId).collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    var noteEditing by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }
    var tagDraft by remember { mutableStateOf("") }
    var tagDraftAsk by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val entry = item?.entry

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        HiIcon(HiIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
                title = {},
                actions = {
                    if (entry?.type == EntryType.IMAGE) {
                        IconButton(onClick = {
                            entry?.let { img ->
                                scope.launch {
                                    val ok = repo.saveToGallery(img)
                                    android.widget.Toast.makeText(
                                        context,
                                        if (ok) "已存回相册（Pictures/Stash）" else "保存失败",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }) {
                            HiIcon(HiIcons.Download01, contentDescription = "存回相册")
                        }
                    }
                    IconButton(onClick = { entry?.let { shareOut(context, repo, it) } }) {
                        HiIcon(HiIcons.Share01, contentDescription = "分享出去")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        HiIcon(
                            HiIcons.Delete01, contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (entry == null) {
            // 条目已删除等场景
            LaunchedEffect(entryId) { }
            androidx.compose.foundation.layout.Box(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            when (entry.type) {
                EntryType.IMAGE -> ImageHero(entry, repo)
                EntryType.LINK -> LinkHero(entry)
            }

            Spacer(Modifier.height(16.dp))

            CardGroup {
                // 备注
                CardGroupRow(
                    position = 0,
                    total = 3,
                    headline = { Text("备注", style = MaterialTheme.typography.titleSmall) },
                    supporting = if (noteEditing) {
                        {
                            Column(Modifier.animateContentSize()) {
                                OutlinedTextField(
                                    value = noteDraft,
                                    onValueChange = { noteDraft = it },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    placeholder = { Text("写点什么…") },
                                    minLines = 2,
                                )
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { noteEditing = false }) { Text("取消") }
                                    Button(onClick = {
                                        scope.launch {
                                            repo.updateNote(entry.id, noteDraft)
                                            noteEditing = false
                                        }
                                    }) { Text("保存") }
                                }
                            }
                        }
                    } else {
                        {
                            Text(
                                entry.note?.ifBlank { null } ?: "点右侧添加",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (entry.note.isNullOrBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    },
                    trailing = {
                        IconButton(onClick = {
                            noteDraft = entry.note.orEmpty()
                            noteEditing = !noteEditing
                        }) {
                            HiIcon(HiIcons.Edit01, contentDescription = "编辑备注")
                        }
                    },
                    onClick = if (!noteEditing) {
                        { noteDraft = entry.note.orEmpty(); noteEditing = true }
                    } else null,
                )

                // 标签
                CardGroupRow(
                    position = 1,
                    total = 3,
                    headline = { Text("标签", style = MaterialTheme.typography.titleSmall) },
                    supporting = {
                        Column(Modifier.animateContentSize()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                item?.tagNames.orEmpty().forEach { tag ->
                                    InputChip(
                                        selected = true,
                                        onClick = {
                                            scope.launch {
                                                repo.setTags(entry.id, item?.tagNames.orEmpty() - tag)
                                            }
                                        },
                                        label = { Text(tag) },
                                    )
                                }
                                InputChip(
                                    selected = false,
                                    onClick = { tagDraftAsk = true },
                                    label = { Text("加标签") },
                                    leadingIcon = { HiIcon(HiIcons.Add01, size = 16.dp) },
                                )
                            }
                            if (item?.tagNames.isNullOrEmpty() && tagDraft.isBlank()) {
                                Text(
                                    "点「加标签」输入，逗号或空格分隔",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )

                // 信息
                CardGroupRow(
                    position = 2,
                    total = 3,
                    headline = { Text("信息", style = MaterialTheme.typography.titleSmall) },
                    supporting = {
                        Column {
                            Text(
                                "收集于 ${formatTime(entry.createdAt)}" +
                                    (entry.sourceApp?.let { " · 来自 $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                when (entry.origin) {
                                    io.github.ccbili30.stash.data.EntryOrigin.SHARE -> "来源：分享"
                                    io.github.ccbili30.stash.data.EntryOrigin.TILE -> "来源：临时截图"
                                    io.github.ccbili30.stash.data.EntryOrigin.AUTO -> "来源：自动收截屏"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // 标签草稿：加标签走对话框，多个标签一次录入
    if (tagDraftAsk) {
        AlertDialog(
            onDismissRequest = { tagDraftAsk = false },
            title = { Text("添加标签") },
            text = {
                OutlinedTextField(
                    value = tagDraft,
                    onValueChange = { tagDraft = it },
                    placeholder = { Text("多个标签用逗号或空格分隔") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val names = tagDraft.split(',', '，', ' ', '\t').filter { it.isNotBlank() }
                    scope.launch {
                        repo.setTags(entryId, (item?.tagNames.orEmpty() + names).distinct())
                        tagDraft = ""
                        tagDraftAsk = false
                    }
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { tagDraftAsk = false }) { Text("取消") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条？") },
            text = { Text("图片文件会一并删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        entry?.let { repo.delete(it) }
                        confirmDelete = false
                        onDeleted()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ImageHero(entry: Entry, repo: StashRepository) {
    val file = entry.fileName?.let { repo.imageFile(it) }
    if (file?.exists() == true) {
        AsyncImage(
            model = file,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.FillWidth,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkHero(entry: Entry) {
    val context = LocalContext.current
    Surface(
        onClick = {
            entry.url?.let { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HiIcon(
                    HiIcons.Link01,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentDescription = null,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    entry.title?.ifBlank { null } ?: domainOf(entry.url).orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                HiIcon(
                    HiIcons.ArrowUpRight01,
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentDescription = "打开链接",
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                entry.url.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun shareOut(context: android.content.Context, repo: StashRepository, entry: Entry) {
    runCatching {
        val intent = when (entry.type) {
            EntryType.IMAGE -> {
                val file = entry.fileName?.let { repo.imageFile(it) } ?: return
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file,
                )
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            EntryType.LINK -> {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, entry.url.orEmpty())
                }
            }
        }
        context.startActivity(Intent.createChooser(intent, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
