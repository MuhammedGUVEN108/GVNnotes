package com.gvn.notes

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

/** Nokta listesinin hashCode'unu hesaplamadan, nesne kimliğiyle ve sınırlı bellekle önbellekler. */
internal class WeakIdentityCache<K:Any,V:Any>(private val limit:Long=Long.MAX_VALUE,private val weight:(V)->Int={1}) {
    private class Key<T:Any>(value:T,queue:ReferenceQueue<T>?=null):WeakReference<T>(value,queue) {
        private val identity=System.identityHashCode(value)
        override fun hashCode()=identity
        override fun equals(other:Any?)=this===other || (other is Key<*> && get()!=null && get()===other.get())
    }
    private val queue=ReferenceQueue<K>()
    private val entries=LinkedHashMap<Key<K>,V>(16,.75f,true)
    private var used=0L
    private fun clean() {
        while(true) { val key=queue.poll() ?: break; entries.remove(key)?.let { used-=weight(it) } }
    }
    @Synchronized operator fun get(key:K):V? { clean(); return entries[Key(key)] }
    @Synchronized operator fun set(key:K,value:V) {
        clean(); entries.remove(Key(key))?.let { used-=weight(it) }
        val size=weight(value); if(size>limit) return
        entries[Key(key,queue)]=value; used+=size
        val iterator=entries.entries.iterator()
        while(used>limit && iterator.hasNext()) { used-=weight(iterator.next().value); iterator.remove() }
    }
}
