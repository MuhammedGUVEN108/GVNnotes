package com.gvn.notes

/** Geri alma tüm dosyanın listelerini kopyalamaz; yalnız değişen sayfaların önceki listelerini tutar. */
class PageHistory(private val limit:Int=80) {
    private data class Edit(val before:Map<String,List<RenderedStroke>>,val after:Map<String,List<RenderedStroke>>)
    var grouped:Map<String,List<RenderedStroke>> = emptyMap(); private set
    var items:List<RenderedStroke> = emptyList(); private set
    private val index=HashMap<String,RenderedStroke>()
    private val past=ArrayDeque<Edit>(); private val future=ArrayDeque<Edit>()
    val canUndo get()=past.isNotEmpty()
    val canRedo get()=future.isNotEmpty()
    fun forPage(id:String)=grouped[id].orEmpty()
    fun selected(ids:Set<String>)=ids.mapNotNull { index[it] }
    fun restore(strokes:List<RenderedStroke>)=restorePages(strokes.groupBy { it.data.pageId })
    fun restorePages(groups:Map<String,List<RenderedStroke>>) {
        grouped=groups.toMap(); index.clear(); groups.values.forEach { list -> list.forEach { index[it.data.id]=it } }
        items=FlattenedInk(grouped.values.toList(),grouped); past.clear(); future.clear()
    }
    private fun apply(changes:Map<String,List<RenderedStroke>>) {
        val next=grouped.toMutableMap()
        changes.forEach { (page,list) ->
            next[page]?.forEach { index.remove(it.data.id) }
            if(list.isEmpty()) next.remove(page) else next[page]=list
            list.forEach { index[it.data.id]=it }
        }
        grouped=next; items=FlattenedInk(grouped.values.toList(),grouped)
    }
    fun replacePage(page:String,list:List<RenderedStroke>) {
        val before=forPage(page); if(before===list || before==list) return
        val edit=Edit(mapOf(page to before),mapOf(page to list.toList()))
        past.addLast(edit); if(past.size>limit) past.removeFirst(); future.clear(); apply(edit.after)
    }
    fun transformPages(pages:Set<String>, transform:(List<RenderedStroke>)->List<RenderedStroke>) {
        val before=pages.associateWith { forPage(it) }
        val after=before.mapValues { transform(it.value) }
        if(before==after) return
        val edit=Edit(before,after); past.addLast(edit); if(past.size>limit) past.removeFirst(); future.clear(); apply(after)
    }
    fun replace(next:List<RenderedStroke>) {
        val groups=next.groupBy { it.data.pageId }
        val keys=(grouped.keys+groups.keys).filter { forPage(it)!=groups[it].orEmpty() }
        if(keys.isEmpty()) return
        val edit=Edit(keys.associateWith { forPage(it) },keys.associateWith { groups[it].orEmpty() })
        past.addLast(edit); if(past.size>limit) past.removeFirst(); future.clear(); apply(edit.after)
    }
    fun undo() { if(canUndo) { val e=past.removeLast(); apply(e.before); future.addLast(e) } }
    fun redo() { if(canRedo) { val e=future.removeLast(); apply(e.after); past.addLast(e) } }
}

/** Sayfa listelerinin ucuz ve değişmez birleşimi; nokta veya belge genelinde çizgi kopyası oluşturmaz. */
internal class FlattenedInk(private val lists:List<List<RenderedStroke>>,internal val groupToken:Any?=null):AbstractList<RenderedStroke>() {
    private val ends=IntArray(lists.size)
    init { var n=0; lists.forEachIndexed { i,list -> n+=list.size; ends[i]=n } }
    override val size get()=ends.lastOrNull() ?: 0
    override fun get(index:Int):RenderedStroke {
        require(index in 0 until size)
        var lo=0; var hi=ends.lastIndex
        while(lo<hi) { val mid=(lo+hi)/2; if(index<ends[mid]) hi=mid else lo=mid+1 }
        return lists[lo][index-(if(lo==0) 0 else ends[lo-1])]
    }
    override fun iterator()=sequence { lists.forEach { yieldAll(it) } }.iterator()
}
