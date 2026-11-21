package com.radium.inkwell.ui.excerpt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radium.inkwell.data.db.entity.ExcerptWithBook
import com.radium.inkwell.data.repo.BookNotesRepository
import com.radium.inkwell.data.repo.BookRepository
import com.radium.inkwell.ui.components.MessageBus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 一本书和它的摘抄（全部摘抄页按书分组，书按最近一次摘抄排） */
data class ExcerptBookGroup(
    val bookId: String,
    val title: String,
    val author: String,
    val deleted: Boolean,
    val items: List<ExcerptWithBook>,
)

/**
 * 全部摘抄（设置 →「我的摘抄」）。
 *
 * 书读得散，摘下来的句子散落在十几本书里：这一页把它们收在一起，按书分组，点一条跳回那本书的原文。
 */
class ExcerptsViewModel(
    private val notes: BookNotesRepository,
    private val bookRepo: BookRepository,
) : ViewModel() {

    /** null = 还在查（转圈，别先闪空态） */
    val groups: StateFlow<List<ExcerptBookGroup>?> = notes.observeAllExcerpts()
        .map<List<ExcerptWithBook>, List<ExcerptBookGroup>?> { group(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages = MessageBus()

    /** 进度写完了，页面该进阅读器了（带书的 id） */
    private val _openReader = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openReader: SharedFlow<String> = _openReader

    /**
     * 跳回原文：先把阅读进度写成这条摘抄的位置，再进阅读器（同详情页点目录，见 BookDetailViewModel.openChapter）。
     * 写进度不能被页面离开掐断，否则进去看到的还是旧位置。
     */
    fun open(item: ExcerptWithBook) {
        if (item.bookDeleted) {
            messages.emit("这本书已经删除了，摘抄还留着，只是跳不回原文")
            return
        }
        val e = item.excerpt
        viewModelScope.launch {
            withContext(NonCancellable) { bookRepo.saveProgress(e.bookId, e.chapterIndex, e.charOffset) }
            _openReader.emit(e.bookId)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            notes.deleteExcerpt(id)
            messages.emit("已删除摘抄")
        }
    }

    fun copied() = messages.emit("已复制")

    companion object {
        /** 查询已按摘抄时间倒序：按书分组时保留这个顺序，最近摘过的那本书排最前，组内也是新的在前 */
        fun group(rows: List<ExcerptWithBook>): List<ExcerptBookGroup> =
            rows.groupBy { it.excerpt.bookId }.map { (bookId, items) ->
                val first = items.first()
                ExcerptBookGroup(bookId, first.bookTitle, first.bookAuthor, first.bookDeleted, items)
            }
    }
}
