package com.gvn.notes

import android.graphics.RectF
import com.gvn.notes.core.Sample
import java.io.File
import java.lang.ref.WeakReference

internal data class SampleRef(val file:String,val token:String)

/** Noktalar diskten gerektiğinde gelir; kaydedilmiş yeni çizgilerin RAM kopyası da bırakılabilir. */
class SampleHandle private constructor(private val count:Int,val bounds:RectF,
    @Volatile private var resident:List<Sample>?,@Volatile internal var reference:SampleRef?):AbstractList<Sample>() {
    override val size get()=count
    private fun values():List<Sample> = resident ?: SampleMemory.get(checkNotNull(reference))
    override fun get(index:Int)=values()[index]
    override fun iterator():Iterator<Sample> = values().iterator()
    internal fun ready()=resident!=null || reference?.let { SampleMemory.contains(it) }==true
    @Synchronized internal fun bind(ref:SampleRef) {
        if(reference!=null) return
        resident?.let { SampleMemory.put(ref,it) }; reference=ref; resident=null
    }
    companion object {
        private val handles=ArrayList<WeakReference<SampleHandle>>()
        @Synchronized private fun track(handle:SampleHandle):SampleHandle {
            if(handles.size%1024==0) handles.removeAll { it.get()==null }
            handles.add(WeakReference(handle)); return handle
        }
        @Synchronized internal fun liveTokens(file:String):Set<String> {
            handles.removeAll { it.get()==null }
            return handles.mapNotNull { it.get()?.reference?.takeIf { r -> r.file==file }?.token }.toSet()
        }
        fun wrap(samples:List<Sample>):SampleHandle {
            if(samples is SampleHandle) return samples
            val b=RectF(Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE)
            samples.forEach { b.left=minOf(b.left,it.x); b.top=minOf(b.top,it.y); b.right=maxOf(b.right,it.x); b.bottom=maxOf(b.bottom,it.y) }
            return track(SampleHandle(samples.size,b,samples,null))
        }
        internal fun disk(file:File,token:String,count:Int,bounds:RectF)=track(SampleHandle(count,bounds,null,SampleRef(file.absolutePath,token)))
    }
}

/** Nokta sayısı ağırlıklı LRU. Açık sayfanın gerekli noktaları yükleme sırasında tahliye edilmez. */
internal object SampleMemory {
    private const val BUDGET=750_000
    private val cache=LinkedHashMap<SampleRef,List<Sample>>(16,.75f,true)
    private val pins=HashMap<Any,Set<SampleRef>>()
    private var points=0
    @Synchronized fun contains(ref:SampleRef)=cache.containsKey(ref)
    @Synchronized fun peek(ref:SampleRef):List<Sample>?=cache[ref]
    fun get(ref:SampleRef):List<Sample> = peek(ref) ?: PageInkDatabase.loadSamples(listOf(ref)).getValue(ref).also { put(ref,it) }
    @Synchronized fun put(ref:SampleRef,value:List<Sample>) {
        points-=cache.remove(ref)?.size ?: 0; cache[ref]=value; points+=value.size; trim()
    }
    @Synchronized fun pin(owner:Any,refs:Set<SampleRef>) { pins[owner]=refs; trim() }
    @Synchronized fun release(owner:Any) { pins.remove(owner); trim() }
    private fun trim() {
        if(points<=BUDGET) return
        val protected=pins.values.flatMap { it }.toHashSet()
        val iterator=cache.entries.iterator()
        while(points>BUDGET && iterator.hasNext()) {
            val e=iterator.next(); if(e.key !in protected) { points-=e.value.size; iterator.remove() }
        }
    }
    fun prefetch(handles:List<SampleHandle>) {
        val refs=handles.mapNotNull { it.reference }.distinct().filterNot { contains(it) }
        if(refs.isEmpty()) return
        PageInkDatabase.loadSamples(refs).forEach { (ref,data) -> put(ref,data) }
    }
    @Synchronized fun clearUnpinned() { val protected=pins.values.flatMap { it }.toHashSet(); val iterator=cache.entries.iterator()
        while(iterator.hasNext()) { val e=iterator.next(); if(e.key !in protected) { points-=e.value.size; iterator.remove() } } }
    @Synchronized fun statistics()=points to cache.size
}
