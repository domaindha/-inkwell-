package com.radium.inkwell.data.db.entity

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * 摘抄：选中正文后点「摘抄」存下来的一段字。
 *
 * 字本身原样存一份（[text]），不靠位置回头去取：网络书换个源、净化规则一改，同一个偏移上的字就变了，
 * 摘下来的那句话不该跟着变。位置 (章, 章内字符偏移) 与阅读进度同一套坐标，只用来「跳回原文」。
 *
 * 同步语义同 [BookmarkEntity]：[updatedAt] 做 LWW，[deleted] 是软删除墓碑。
 * 删书**不**连带删摘抄 —— 摘下来的话是用户自己的东西，书没了它还在（全部摘抄页照常列出，只是跳不回去）。
 */
@Entity(tableName = "excerpt", indices = [Index("bookId")])
data class ExcerptEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    /** 选区起点在章内的字符偏移 */
    val charOffset: Int,
    val chapterTitle: String,
    /** 选中的字；跨段选的各段之间是换行 */
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** 全部摘抄页的一行：摘抄 + 它所在那本书的书名（书删了也还在库里，墓碑行带着书名） */
data class ExcerptWithBook(
    @Embedded val excerpt: ExcerptEntity,
    val bookTitle: String,
    val bookAuthor: String,
    /** 书已删除：摘抄照列，但跳不回原文 */
    val bookDeleted: Boolean,
)
