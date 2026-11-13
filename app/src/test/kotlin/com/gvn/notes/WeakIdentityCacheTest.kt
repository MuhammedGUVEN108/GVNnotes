package com.gvn.notes

import org.junit.Assert.*
import org.junit.Test

class WeakIdentityCacheTest {
    private class Key {
        override fun hashCode():Int=error("Noktaların hashCode'u çağrılmamalı")
        override fun equals(other:Any?)=other is Key
    }
    @Test fun usesIdentityWithoutDeepHashOrValueEquality() {
        val cache=WeakIdentityCache<Key,String>(); val a=Key(); val b=Key()
        cache[a]="a"; cache[b]="b"
        assertEquals("a",cache[a]); assertEquals("b",cache[b]); assertNull(cache[Key()])
    }
    @Test fun evictsLeastRecentlyUsedAtBudget() {
        val cache=WeakIdentityCache<Key,String>(4) { it.length }; val a=Key(); val b=Key(); val c=Key()
        cache[a]="aa"; cache[b]="bb"; assertEquals("aa",cache[a]); cache[c]="cc"
        assertNull(cache[b]); assertEquals("aa",cache[a]); assertEquals("cc",cache[c])
    }
}
