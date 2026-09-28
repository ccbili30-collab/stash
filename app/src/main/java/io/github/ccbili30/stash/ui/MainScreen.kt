package io.github.ccbili30.stash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.ccbili30.stash.data.EntryType
import io.github.ccbili30.stash.data.EntryWithTags
import io.github.ccbili30.stash.data.StashRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenDetail: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { StashRepository.get(context) }
    val entries by repo.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val tags by repo.tags.collectAsStateWithLifecycle(initialValue = emptyList())

    var filter by rememberSaveable { mutableStateOf("ALL") }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val shown = remember(entries, filter, selectedTag, query) {
        entries.filter { item ->
            val typeOk = filter == "ALL" || item.entry.type.name == filter
            val tagOk = selectedTag == null || selectedTag in item.tagNames
            val queryOk = query.isBlank() || run {
                val q = query.trim()
                listOfNotNull(item.entry.title, item.entry.url, item.entry.note)
                    .any { it.contains(q, ignoreCase = true) } ||
                    item.tagNames.any { it.contains(q, ignoreCase = true) }
            }
            typeOk && tagOk && queryOk
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            placeholder = { Text("搜索标题 / 网址 / 备注 / 标签") },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            trailingIcon = {
                                IconButton(onClick = { searching = false; query = "" }) {
                                    HiIcon(HiIcons.Cancel01, contentDescription = "关闭搜索")
                                }
                            },
                        )
                    } else {
                        Text("Stash", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (!searching) {
                        IconButton(onClick = { searching = true }) {
                            HiIcon(HiIcons.Search01, contentDescription = "搜索")
                        }
                        IconButton(onClick = onOpenSettings) {
                            HiIcon(HiIcons.Settings01, contentDescription = "设置")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FilterRow(
                filter = filter,
                onFilter = { filter = it },
                tags = tags,
                selectedTag = selectedTag,
                onTag = { selectedTag = if (selectedTag == it) null else it },
            )

            if (shown.isEmpty()) {
                EmptyState(hasAnyEntry = entries.isNotEmpty())
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalItemSpacing = 10.dp,
                ) {
                    items(shown, key = { it.entry.id }) { item ->
                        when (item.entry.type) {
                            EntryType.IMAGE -> ImageCard(item, repo, onClick = { onOpenDetail(item.entry.id) })
                            EntryType.LINK -> LinkCard(item, onClick = { onOpenDetail(item.entry.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    filter: String,
    onFilter: (String) -> Unit,
    tags: List<String>,
    selectedTag: String?,
    onTag: (String) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf("ALL" to "全部", "IMAGE" to "图片", "LINK" to "链接")
            options.forEachIndexed { index, (key, label) ->
                SegmentedButton(
                    selected = filter == key,
                    onClick = { onFilter(key) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) { Text(label) }
            }
        }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tags.size) { i ->
                    val tag = tags[i]
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = { onTag(tag) },
                        label = { Text(tag) },
                        leadingIcon = if (selectedTag == tag) {
                            { HiIcon(HiIcons.Tag01, size = 16.dp) }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageCard(item: EntryWithTags, repo: StashRepository, onClick: () -> Unit) {
    val entry = item.entry
    val file: File? = entry.fileName?.let { runCatching { repo.imageFile(it) }.getOrNull() }
    val ratio = if (entry.height > 0) entry.width.toFloat() / entry.height else 1f

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 0.dp,
    ) {
        Column {
            if (file?.exists() == true) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(file).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (ratio > 0f) ratio else 1f)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = 0.dp, bottomEnd = 0.dp,
                            ),
                        ),
                    contentScale = ContentScale.Crop,
                )
            }
            val meta = entry.note?.ifBlank { null } ?: item.tagNames.take(2).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun LinkCard(item: EntryWithTags, onClick: () -> Unit) {
    val entry = item.entry
    val host = remember(entry.url) { domainOf(entry.url) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HiIcon(
                    HiIcons.Link01,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = null,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        entry.title?.ifBlank { null } ?: host ?: entry.url.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    host?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            entry.note?.ifBlank { null }?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.tagNames.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    item.tagNames.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(hasAnyEntry: Boolean) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HiIcon(
                HiIcons.Archive01,
                size = 56.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (hasAnyEntry) "这里没有匹配的条目" else "还是空的",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (hasAnyEntry) "换个筛选或搜索词试试"
                else "从任意 app 分享图片或链接过来，\n或下拉通知栏点「Stash 临时截图」磁贴",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

internal fun domainOf(url: String?): String? = runCatching {
    android.net.Uri.parse(url).host
}.getOrNull()

internal fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
