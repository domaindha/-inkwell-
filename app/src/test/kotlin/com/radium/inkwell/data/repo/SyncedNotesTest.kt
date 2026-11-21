package com.radium.inkwell.data.repo

import com.radium.inkwell.core.webdav.BackupBook
import com.radium.inkwell.core.webdav.BackupBookmark
import com.radium.inkwell.core.webdav.BackupExcerpt
import com.radium.inkwell.data.db.entity.BookEntity
import com.radium.inkwell.data.db.entity.BookType
import com.radium.inkwell.data.db.entity.BookmarkEntity
import com.radium.inkwell.data.db.entity.ExcerptEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 收藏、书签、摘抄同步落到本机最新行上的裁决（WebDavRepository.applyToLocal 的纯逻辑部分） */
class SyncedNotesTest {
    private val book = BookEntity(
        id = "b", type = BookType.LOCAL_TXT, title = "书", addedAt = 1, updatedAt = 100, favorite = true,
    )
    private val remoteBook = BackupBook(id = "b", type = BookType.LOCAL_TXT, title = "书", updatedAt = 200)

    @Test fun `远端更新的收藏状态会落到本机`() {
        assertFalse(applySyncedBook(book, remoteBook.copy(favorite = false)).entity.favorite)
        assertTrue(applySyncedBook(book.copy(favorite = false), remoteBook.copy(favorite = true)).entity.favorite)
    }

    @Test fun `老版本备份没带收藏时保留本机的`() {
        assertTrue(applySyncedBook(book, remoteBook.copy(favorite = null, groupName = "新分组")).entity.favorite)
    }

    @Test fun `本机更新的收藏不被旧的合并结果盖掉`() {
        val newer = book.copy(updatedAt = 300)
        assertTrue(applySyncedBook(newer, remoteBook.copy(favorite = false)).entity.favorite)
    }

    private val bookmark = BookmarkEntity(
        id = "m", bookId = "b", chapterIndex = 2, charOffset = 30, chapterTitle = "第三章",
        excerpt = "开头", createdAt = 100, updatedAt = 100,
    )

    @Test fun `书签按事务里的最新行裁决`() {
        val remote = BackupBookmark(id = "m", bookId = "b", chapterIndex = 2, charOffset = 30, updatedAt = 200, deleted = true)
        assertTrue(assertNotNull(applySyncedBookmark(bookmark, remote)).deleted)
        // 本机在拍快照之后刚删过（时间戳更新）：旧合并结果里的活书签不能把它复活
        assertNull(applySyncedBookmark(bookmark.copy(deleted = true, updatedAt = 300), remote.copy(deleted = false)))
        // 本机没有：远端新增，原样落地
        val added = assertNotNull(applySyncedBookmark(null, remote.copy(deleted = false)))
        assertEquals(30, added.charOffset)
    }

    @Test fun `摘抄的字与位置原样落地`() {
        val remote = BackupExcerpt(
            id = "e", bookId = "b", chapterIndex = 4, charOffset = 12, chapterTitle = "第五章",
            text = "第一行\n第二行", createdAt = 50, updatedAt = 60,
        )
        val row = assertNotNull(applySyncedExcerpt(null, remote))
        assertEquals("第一行\n第二行", row.text)
        assertEquals(4 to 12, row.chapterIndex to row.charOffset)
        val existing = ExcerptEntity(
            id = "e", bookId = "b", chapterIndex = 4, charOffset = 12, chapterTitle = "第五章",
            text = "第一行\n第二行", createdAt = 50, updatedAt = 60,
        )
        assertNull(applySyncedExcerpt(existing, remote))
    }
}
