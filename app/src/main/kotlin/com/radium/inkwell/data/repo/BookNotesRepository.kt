package com.radium.inkwell.data.repo

import com.radium.inkwell.data.db.dao.BookmarkDao
import com.radium.inkwell.data.db.dao.ExcerptDao
import com.radium.inkwell.data.db.entity.BookmarkEntity
import com.radium.inkwell.data.db.entity.ExcerptEntity
import com.radium.inkwell.data.db.entity.ExcerptWithBook
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * 书签与摘抄：都是「书里的一个位置 + 一段字」，位置与阅读进度同一套坐标 (章, 章内字符偏移)。
 *
 * 两样都参与 WebDAV 同步（见 WebDavRepository），所以删除一律**软删除**、写入一律盖 updatedAt。
 */
class BookNotesRepository(
    private val bookmarkDao: BookmarkDao,
    private val excerptDao: ExcerptDao,
) {
    fun observeBookmarks(bookId: String): Flow<List<BookmarkEntity>> = bookmarkDao.observeByBook(bookId)

    suspend fun addBookmark(
        bookId: String,
        chapterIndex: Int,
        charOffset: Int,
        chapterTitle: String,
        excerpt: String,
    ): BookmarkEntity {
        val now = System.currentTimeMillis()
        val row = BookmarkEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            chapterIndex = chapterIndex,
            charOffset = charOffset,
            chapterTitle = chapterTitle,
            excerpt = excerpt,
            createdAt = now,
            updatedAt = now,
        )
        bookmarkDao.upsert(row)
        return row
    }

    suspend fun deleteBookmark(id: String) = bookmarkDao.softDelete(id, System.currentTimeMillis())

    fun observeExcerpts(bookId: String): Flow<List<ExcerptEntity>> = excerptDao.observeByBook(bookId)

    /** 全部摘抄（不含隐藏的书），见 [ExcerptDao.observeAllWithBook] */
    fun observeAllExcerpts(): Flow<List<ExcerptWithBook>> = excerptDao.observeAllWithBook()

    suspend fun addExcerpt(
        bookId: String,
        chapterIndex: Int,
        charOffset: Int,
        chapterTitle: String,
        text: String,
    ): ExcerptEntity {
        val now = System.currentTimeMillis()
        val row = ExcerptEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            chapterIndex = chapterIndex,
            charOffset = charOffset,
            chapterTitle = chapterTitle,
            text = text,
            createdAt = now,
            updatedAt = now,
        )
        excerptDao.upsert(row)
        return row
    }

    suspend fun deleteExcerpt(id: String) = excerptDao.softDelete(id, System.currentTimeMillis())

    // ---- WebDAV ----

    suspend fun allBookmarksForBackup(): List<BookmarkEntity> = bookmarkDao.getAll()
    suspend fun allExcerptsForBackup(): List<ExcerptEntity> = excerptDao.getAll()
    suspend fun getBookmark(id: String): BookmarkEntity? = bookmarkDao.getById(id)
    suspend fun getExcerpt(id: String): ExcerptEntity? = excerptDao.getById(id)
    suspend fun upsertBookmark(row: BookmarkEntity) = bookmarkDao.upsert(row)
    suspend fun upsertExcerpt(row: ExcerptEntity) = excerptDao.upsert(row)

    /** 两张表里最近一次改动的时间；空表为 0 */
    suspend fun lastChangeAt(): Long = maxOf(bookmarkDao.lastChangeAt(), excerptDao.lastChangeAt())
}
