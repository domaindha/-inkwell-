package com.radium.inkwell.ui.reader

import android.content.ClipData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import com.radium.inkwell.data.db.entity.BookmarkEntity
import com.radium.inkwell.data.db.entity.ExcerptEntity
import com.radium.inkwell.reader.paginate.PageItem
import com.radium.inkwell.reader.paginate.PageSpec
import com.radium.inkwell.reader.render.RenderablePage
import com.radium.inkwell.ui.components.AppDropdownMenu
import com.radium.inkwell.ui.components.AppMenuItem
import com.radium.inkwell.ui.components.CardViewport
import com.radium.inkwell.ui.components.ContentListDefaults
import com.radium.inkwell.ui.components.ContentListItem
import com.radium.inkwell.ui.components.Dimens
import com.radium.inkwell.ui.components.cardMargin
import com.radium.inkwell.ui.components.formatRelativeTime
import com.radium.inkwell.ui.components.groupedCardItem
import com.radium.inkwell.ui.components.pageScroll
import com.radium.inkwell.ui.components.sheetBottomInset
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 当前这一页上的书签（阅读菜单「⋮」里「添加书签 / 删除本页书签」据此二选一）。
 *
 * 书签记的是加它那一页的页首偏移，改了字号页就重排了 —— 所以按「偏移落在这一页的范围里」认，而不是比相等。
 * 整页插画那种页首尾偏移相同（图不占偏移，见 Paginator），范围按至少一个字算，否则那一页永远认不出书签。
 */
internal fun List<BookmarkEntity>.onPage(chapterIndex: Int, spec: PageSpec?): BookmarkEntity? {
    spec ?: return null
    val end = maxOf(spec.endCharOffset, spec.startCharOffset + 1)
    return firstOrNull { it.chapterIndex == chapterIndex && it.charOffset >= spec.startCharOffset && it.charOffset < end }
}

/** 书签上记的那一小段正文：这一页第一段正文（不算章标题）从页首起的前 [BOOKMARK_SNIPPET_CHARS] 个字 */
internal fun RenderablePage.bookmarkSnippet(): String {
    val slices = spec.items.filterIsInstance<PageItem.TextSlice>()
    val slice = slices.firstOrNull { !it.isTitle } ?: slices.firstOrNull() ?: return ""
    val para = measured[slice.elementIndex] ?: return ""
    val from = para.lineStartOffset(slice.startLine).coerceIn(0, para.text.length)
    return para.text.substring(from, minOf(para.text.length, from + BOOKMARK_SNIPPET_CHARS)).trim()
}

private const val BOOKMARK_SNIPPET_CHARS = 60

/**
 * 目录面板的「书签」页：一本书的书签按在书里的先后排，点一行跳过去，长按删除。
 *
 * 版式照目录（[CardViewport] 裁圆上角、行拼成一张卡、列表铺到手势条下面），面板切页签时高度上限与目录一致。
 */
@Composable
internal fun BookmarkList(
    bookmarks: List<BookmarkEntity>,
    onJump: (BookmarkEntity) -> Unit,
    onDelete: (BookmarkEntity) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        NotesEmpty("还没有书签。在阅读菜单右上角「⋮」里添加，之后从这里一点就回到那一页")
        return
    }
    NotesSummary("共 ${bookmarks.size} 个书签 · 长按删除")
    NotesViewport {
        itemsIndexed(bookmarks, key = { _, it -> it.id }) { i, mark ->
            var menu by remember { mutableStateOf(false) }
            Box(Modifier.groupedCardItem(i, bookmarks.size)) {
                ContentListItem(
                    onClick = { onJump(mark) },
                    onLongClick = { menu = true },
                    supportingContent = {
                        Column {
                            if (mark.excerpt.isNotBlank()) {
                                Text(
                                    mark.excerpt,
                                    style = ContentListDefaults.supportingStyle,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(formatRelativeTime(mark.createdAt), style = MiuixTheme.textStyles.footnote1)
                        }
                    },
                ) {
                    Text(
                        mark.chapterTitle.ifBlank { "第 ${mark.chapterIndex + 1} 章" },
                        style = ContentListDefaults.titleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AppDropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    items = listOf(
                        AppMenuItem("跳到这里", onClick = { onJump(mark) }),
                        AppMenuItem("删除书签", onClick = { onDelete(mark) }),
                    ),
                )
            }
        }
    }
}

/**
 * 目录面板的「摘抄」页：这本书里摘下来的句子，按在书里的先后排。点一行跳回原文，长按复制或删除。
 * 摘下来的字原样显示（[ExcerptEntity.text]），不随换源、净化变化。
 */
@Composable
internal fun ExcerptList(
    excerpts: List<ExcerptEntity>,
    onJump: (ExcerptEntity) -> Unit,
    onDelete: (ExcerptEntity) -> Unit,
    onCopied: () -> Unit,
) {
    if (excerpts.isEmpty()) {
        NotesEmpty("还没有摘抄。长按正文选中文字，点「摘抄」就会存到这里")
        return
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    NotesSummary("共 ${excerpts.size} 条摘抄 · 长按复制或删除")
    NotesViewport {
        itemsIndexed(excerpts, key = { _, it -> it.id }) { i, excerpt ->
            var menu by remember { mutableStateOf(false) }
            Box(Modifier.groupedCardItem(i, excerpts.size)) {
                ExcerptRow(
                    excerpt = excerpt,
                    source = excerpt.chapterTitle.ifBlank { "第 ${excerpt.chapterIndex + 1} 章" },
                    onClick = { onJump(excerpt) },
                    onLongClick = { menu = true },
                )
                AppDropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    items = listOf(
                        AppMenuItem("跳到原文", onClick = { onJump(excerpt) }),
                        AppMenuItem(
                            "复制",
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("excerpt", excerpt.text)))
                                    onCopied()
                                }
                            },
                        ),
                        AppMenuItem("删除摘抄", onClick = { onDelete(excerpt) }),
                    ),
                )
            }
        }
    }
}

/**
 * 一条摘抄：摘下来的字做主行（最多四行），下面一行小字写出处与时间。
 * 阅读页面板与「我的摘抄」页共用 —— 两处只有出处那行写法不同（面板里写章名，全部摘抄里写书名 · 章名）。
 */
@Composable
internal fun ExcerptRow(
    excerpt: ExcerptEntity,
    source: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ContentListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        enabled = enabled,
        supportingContent = {
            Text(
                "$source · ${formatRelativeTime(excerpt.createdAt)}",
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        Text(
            excerpt.text,
            style = MiuixTheme.textStyles.body1,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 列表上方的一行概况，左边与卡里的字对齐（同目录的 TocSummary） */
@Composable
private fun NotesSummary(text: String) {
    Text(
        text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = cardMargin() + Dimens.listHorizontal)
            .padding(top = Dimens.gapS),
    )
}

/** 空的时候说清楚东西从哪儿来：书签在「⋮」里加，摘抄在选字后加 */
@Composable
private fun NotesEmpty(hint: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.gapXL)
            .padding(top = Dimens.gapXXL, bottom = Dimens.gapXXL + sheetBottomInset()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            hint,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 定高上限的卡片视口，同目录：导航栏高度垫在列表末尾、也加进上限，列表一直铺到手势条下面 */
@Composable
private fun NotesViewport(content: LazyListScope.() -> Unit) {
    val bottomInset = sheetBottomInset()
    Box(
        Modifier
            .heightIn(max = Dimens.sheetListMaxHeight + bottomInset)
            // 和概况行之间的缝垫在视口外面，理由同目录（见 TocList）
            .padding(top = Dimens.gapS),
    ) {
        CardViewport {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.pageScroll(),
                contentPadding = PaddingValues(bottom = bottomInset),
                overscrollEffect = null,
                content = content,
            )
        }
    }
}
