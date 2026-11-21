package com.radium.inkwell.data.db.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * 书签：书里的一个位置。
 *
 * 位置存 (章, 章内字符偏移)，与阅读进度同一套坐标（见 [BookEntity.readChapterIndex]）—— 存页码会被改字号、
 * 改边距毁掉，偏移不会。[charOffset] 取加书签那一页的页首，跳回来就是同一页。
 *
 * beta.26 有过一张同名表（MIGRATION_6_7 建、MIGRATION_7_8 删），这里沿用那份设计，另加两列给 WebDAV 同步：
 * [updatedAt] 做 LWW、[deleted] 是软删除墓碑 —— 理由同 [BookEntity.deleted]，真删的话别的设备下次同步又把它补回来。
 */
@Entity(tableName = "bookmark", indices = [Index("bookId")])
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val charOffset: Int,
    /** 加书签时的章名。目录换过源也照旧显示得出是哪一章 */
    val chapterTitle: String,
    /** 那一页开头的一小段正文，列表里一眼认得出是哪儿 */
    val excerpt: String,
    val createdAt: Long,
    /** 改动时间，合并靠它裁决；删除时同时更新 */
    val updatedAt: Long,
    /** 软删除墓碑。所有面向用户的查询都要过滤掉 deleted = 1 */
    val deleted: Boolean = false,
)
