package com.radium.inkwell.core.webdav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 收藏、书签、摘抄的合并：收藏随书的元数据 LWW，书签与摘抄逐条 LWW + 墓碑 */
class BackupNotesMergeTest {

    private fun payload(
        books: List<BackupBook> = emptyList(),
        bookmarks: List<BackupBookmark> = emptyList(),
        excerpts: List<BackupExcerpt> = emptyList(),
    ) = BackupPayload(deviceId = "test", exportedAt = 0, books = books, bookmarks = bookmarks, excerpts = excerpts)

    private fun book(updatedAt: Long, favorite: Boolean?, group: String = "") =
        BackupBook(id = "b", type = 0, title = "书", updatedAt = updatedAt, groupName = group, favorite = favorite)

    @Test
    fun `收藏随元数据按 updatedAt 裁决`() {
        val result = BackupMerger.merge(
            payload(books = listOf(book(100, favorite = false))),
            payload(books = listOf(book(200, favorite = true))),
        )
        assertEquals(true, result.books.single().favorite)
        assertEquals(1, result.changedBooks.size)
    }

    @Test
    fun `老版本写的备份没带收藏 不会把本地的收藏清掉`() {
        // 老设备改了分组（时间戳更新、整条胜出），但它不认识收藏，写出来是 null
        val result = BackupMerger.merge(
            payload(books = listOf(book(100, favorite = true))),
            payload(books = listOf(book(200, favorite = null, group = "新分组"))),
        )
        val merged = result.books.single()
        assertEquals("新分组", merged.groupName)
        assertEquals(true, merged.favorite)
    }

    @Test
    fun `两边都没带收藏时保持 null`() {
        val result = BackupMerger.merge(
            payload(books = listOf(book(100, favorite = null))),
            payload(books = listOf(book(200, favorite = null))),
        )
        assertNull(result.books.single().favorite)
    }

    @Test
    fun `书签逐条 LWW 远端新增会写回本地`() {
        val local = BackupBookmark(id = "m1", bookId = "b", chapterIndex = 1, charOffset = 10, updatedAt = 100)
        val remoteNew = BackupBookmark(id = "m2", bookId = "b", chapterIndex = 3, charOffset = 0, updatedAt = 50)
        val result = BackupMerger.merge(
            payload(bookmarks = listOf(local)),
            payload(bookmarks = listOf(remoteNew)),
        )
        assertEquals(setOf("m1", "m2"), result.bookmarks.map { it.id }.toSet())
        assertEquals(listOf(remoteNew), result.changedBookmarks)
    }

    @Test
    fun `本地删掉的书签不会被远端的旧副本补回来`() {
        val alive = BackupBookmark(id = "m", bookId = "b", updatedAt = 100)
        val tomb = alive.copy(deleted = true, updatedAt = 200)
        val result = BackupMerger.merge(payload(bookmarks = listOf(tomb)), payload(bookmarks = listOf(alive)))
        assertTrue(result.bookmarks.single().deleted)
        assertTrue(result.changedBookmarks.isEmpty())
    }

    @Test
    fun `远端删除的摘抄同步到本地`() {
        val alive = BackupExcerpt(id = "e", bookId = "b", text = "一句话", updatedAt = 100)
        val tomb = alive.copy(deleted = true, updatedAt = 300)
        val result = BackupMerger.merge(payload(excerpts = listOf(alive)), payload(excerpts = listOf(tomb)))
        assertEquals(listOf(tomb), result.changedExcerpts)
    }

    @Test
    fun `时间戳打平时本地优先`() {
        val l = BackupExcerpt(id = "e", bookId = "b", text = "本地", updatedAt = 100)
        val r = l.copy(text = "远端")
        val result = BackupMerger.merge(payload(excerpts = listOf(l)), payload(excerpts = listOf(r)))
        assertEquals("本地", result.excerpts.single().text)
        assertTrue(result.changedExcerpts.isEmpty())
    }

    @Test
    fun `老备份没有书签和摘抄的键 读进来是空列表`() {
        val old = BackupCodec.encode(BackupPayload(deviceId = "d", exportedAt = 1))
        val decoded = BackupCodec.decode(old)
        assertTrue(decoded.bookmarks.isEmpty())
        assertTrue(decoded.excerpts.isEmpty())
        val roundTrip = BackupCodec.decode(
            BackupCodec.encode(payload(excerpts = listOf(BackupExcerpt(id = "e", bookId = "b", text = "第一行\n第二行")))),
        )
        assertEquals("第一行\n第二行", roundTrip.excerpts.single().text)
    }
}
