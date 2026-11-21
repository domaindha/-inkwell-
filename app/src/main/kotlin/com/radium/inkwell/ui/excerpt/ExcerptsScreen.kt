package com.radium.inkwell.ui.excerpt

import android.content.ClipData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radium.inkwell.ui.components.AppDropdownMenu
import com.radium.inkwell.ui.components.AppMenuItem
import com.radium.inkwell.ui.components.AppScaffold
import com.radium.inkwell.ui.components.AppSnackbarHost
import com.radium.inkwell.ui.components.AppTopBar
import com.radium.inkwell.ui.components.CollectMessages
import com.radium.inkwell.ui.components.Dimens
import com.radium.inkwell.ui.components.EmptyState
import com.radium.inkwell.ui.components.LoadingState
import com.radium.inkwell.ui.components.SectionHeader
import com.radium.inkwell.ui.components.groupedCardItem
import com.radium.inkwell.ui.components.horizontalOnly
import com.radium.inkwell.ui.components.pageScroll
import com.radium.inkwell.ui.components.rememberAppTopBarScroll
import com.radium.inkwell.ui.components.settingsPageColor
import com.radium.inkwell.ui.components.topBarScroll
import com.radium.inkwell.ui.reader.ExcerptRow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Create

/**
 * 我的摘抄：所有书里摘下来的句子，按书分组（最近摘过的那本在最前，组内新的在前）。
 *
 * 点一条跳回原文（进阅读器，停在那句话所在的那一页）；长按复制或删除。书删了摘抄还在，只是跳不回去。
 * 隐藏的书不列（见 ExcerptDao.observeAllWithBook）：设置这一支一个字都不提隐藏区。
 */
@Composable
fun ExcerptsScreen(
    onBack: () -> Unit,
    onOpenBook: (bookId: String) -> Unit,
    viewModel: ExcerptsViewModel,
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel.messages, snackbar)
    LaunchedEffect(Unit) {
        viewModel.openReader.collect { onOpenBook(it) }
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val pageColor = settingsPageColor()
    val topBarScroll = rememberAppTopBarScroll()
    AppScaffold(
        modifier = Modifier.topBarScroll(topBarScroll),
        containerColor = pageColor,
        topBar = { AppTopBar("我的摘抄", topBarScroll, onBack = onBack, containerColor = pageColor) },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        val list = groups
        when {
            list == null -> LoadingState(Modifier.padding(padding))
            list.isEmpty() -> EmptyState(
                icon = MiuixIcons.Create,
                title = "还没有摘抄",
                hint = "读书时长按正文选中文字，点「摘抄」，句子就会收在这里；配了 WebDAV 的话各台设备的摘抄会合在一起",
                modifier = Modifier.padding(padding),
            )
            // 只让开两侧：顶栏高度垫在列表开头、导航栏垫在末尾，列表滚到玻璃顶栏与手势条下面（见 AppScaffold）
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(padding.horizontalOnly()).pageScroll(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + Dimens.gapXL,
                ),
                overscrollEffect = null,
            ) {
                list.forEach { group ->
                    item(key = "header:${group.bookId}") {
                        SectionHeader(
                            buildString {
                                append(group.title)
                                if (group.author.isNotBlank()) append(" · ").append(group.author)
                                if (group.deleted) append("（已删除）")
                            },
                        )
                    }
                    itemsIndexed(group.items, key = { _, it -> it.excerpt.id }) { i, item ->
                        var menu by remember { mutableStateOf(false) }
                        Box(Modifier.groupedCardItem(i, group.items.size)) {
                            ExcerptRow(
                                excerpt = item.excerpt,
                                source = item.excerpt.chapterTitle.ifBlank { "第 ${item.excerpt.chapterIndex + 1} 章" },
                                onClick = { viewModel.open(item) },
                                onLongClick = { menu = true },
                            )
                            AppDropdownMenu(
                                expanded = menu,
                                onDismissRequest = { menu = false },
                                items = buildList {
                                    if (!item.bookDeleted) add(AppMenuItem("跳到原文", onClick = { viewModel.open(item) }))
                                    add(
                                        AppMenuItem(
                                            "复制",
                                            onClick = {
                                                scope.launch {
                                                    clipboard.setClipEntry(
                                                        ClipEntry(ClipData.newPlainText("excerpt", item.excerpt.text)),
                                                    )
                                                    viewModel.copied()
                                                }
                                            },
                                        ),
                                    )
                                    add(AppMenuItem("删除摘抄", onClick = { viewModel.delete(item.excerpt.id) }))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
