package com.radium.inkwell.ui.reader

import com.radium.inkwell.data.db.entity.BookmarkEntity
import com.radium.inkwell.data.db.entity.ExcerptEntity
import com.radium.inkwell.data.db.entity.ExcerptWithBook
import com.radium.inkwell.reader.paginate.PageSpec
import com.radium.inkwell.ui.excerpt.ExcerptsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderNotesTest {
    private fun mark(chapter: Int, offset: Int) = BookmarkEntity(
        id = "$chapter:$offset", bookId = "b", chapterIndex = chapter, charOffset = offset,
        chapterTitle = "", excerpt = "", createdAt = 0, updatedAt = 0,
    )

    private fun page(chapter: Int, start: Int, end: Int) = PageSpec(chapter, 0, emptyList(), start, end)

    @Test fun `书签按偏移落在这一页的范围里认 改字号重排后也认得出`() {
        val marks = listOf(mark(2, 100), mark(3, 0))
        // 加书签时页首是 100；字号调大后这一页从 80 排到 160，书签仍在这一页上
        assertEquals("2:100", marks.onPage(2, page(2, 80, 160))?.id)
        assertNull(marks.onPage(2, page(2, 160, 240)))
        // 章号不同不算
        assertNull(marks.onPage(1, page(1, 80, 160)))
    }

    @Test fun `整页插画首尾偏移相同 也认得出页首的书签`() {
        assertEquals("3:0", listOf(mark(3, 0)).onPage(3, page(3, 0, 0))?.id)
        assertNull(listOf(mark(3, 0)).onPage(3, null))
    }

    @Test fun `全部摘抄按书分组 保留最近摘过的书在前`() {
        fun row(id: String, book: String, at: Long) = ExcerptWithBook(
            ExcerptEntity(
                id = id, bookId = book, chapterIndex = 0, charOffset = 0, chapterTitle = "",
                text = id, createdAt = at, updatedAt = at,
            ),
            bookTitle = "书$book", bookAuthor = "", bookDeleted = false,
        )
        // 查询已按时间倒序
        val groups = ExcerptsViewModel.group(listOf(row("e3", "b", 300), row("e2", "a", 200), row("e1", "b", 100)))
        assertEquals(listOf("b", "a"), groups.map { it.bookId })
        assertEquals(listOf("e3", "e1"), groups.first().items.map { it.excerpt.id })
        assertEquals("书b", groups.first().title)
    }
}
