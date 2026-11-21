package com.radium.inkwell.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.radium.inkwell.data.db.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    /** 一本书的书签，按在书里的先后排（不是按添加时间：书签列表是「书里的几个位置」） */
    @Query("SELECT * FROM bookmark WHERE bookId = :bookId AND deleted = 0 ORDER BY chapterIndex, charOffset, createdAt")
    fun observeByBook(bookId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmark WHERE id = :id")
    suspend fun getById(id: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: BookmarkEntity)

    /** 软删除：同时更新 updatedAt，理由同 BookDao.softDelete */
    @Query("UPDATE bookmark SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    /** 删书时连带：书签只是书里的位置，书没了它也就没有意义。打墓碑以便 WebDAV 把删除同步出去 */
    @Query("UPDATE bookmark SET deleted = 1, updatedAt = :now WHERE bookId = :bookId AND deleted = 0")
    suspend fun softDeleteByBook(bookId: String, now: Long)

    /** 含墓碑，**只给 WebDAV 备份用** */
    @Query("SELECT * FROM bookmark")
    suspend fun getAll(): List<BookmarkEntity>

    /** 本地最近一次改动的时间，并进同步的「本地有没有动过」判断 */
    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM bookmark")
    suspend fun lastChangeAt(): Long
}
