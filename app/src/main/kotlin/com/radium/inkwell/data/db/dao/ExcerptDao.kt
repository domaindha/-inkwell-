package com.radium.inkwell.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.radium.inkwell.data.db.entity.ExcerptEntity
import com.radium.inkwell.data.db.entity.ExcerptWithBook
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcerptDao {
    /** 一本书的摘抄，按在书里的先后排 */
    @Query("SELECT * FROM excerpt WHERE bookId = :bookId AND deleted = 0 ORDER BY chapterIndex, charOffset, createdAt")
    fun observeByBook(bookId: String): Flow<List<ExcerptEntity>>

    /**
     * 全部摘抄（设置 →「我的摘抄」），新摘的在前。
     *
     * **不含隐藏的书**：那一页一个字都不提隐藏区（同设置页概览卡，见 BookDao.observeLatestRead），
     * 藏起来的书的摘抄列在那儿，等于把书名说出来。删掉的书照列（书行还在库里，是墓碑）。
     */
    @Query(
        "SELECT excerpt.*, book.title AS bookTitle, book.author AS bookAuthor, book.deleted AS bookDeleted " +
            "FROM excerpt INNER JOIN book ON book.id = excerpt.bookId " +
            "WHERE excerpt.deleted = 0 AND book.hidden = 0 " +
            "ORDER BY excerpt.createdAt DESC",
    )
    fun observeAllWithBook(): Flow<List<ExcerptWithBook>>

    @Query("SELECT * FROM excerpt WHERE id = :id")
    suspend fun getById(id: String): ExcerptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ExcerptEntity)

    /** 软删除：同时更新 updatedAt，理由同 BookDao.softDelete */
    @Query("UPDATE excerpt SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    /** 含墓碑，**只给 WebDAV 备份用** */
    @Query("SELECT * FROM excerpt")
    suspend fun getAll(): List<ExcerptEntity>

    /** 本地最近一次改动的时间，并进同步的「本地有没有动过」判断 */
    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM excerpt")
    suspend fun lastChangeAt(): Long
}
