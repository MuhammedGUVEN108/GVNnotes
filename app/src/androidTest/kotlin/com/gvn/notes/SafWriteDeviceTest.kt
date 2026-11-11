package com.gvn.notes

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Gerçek SAF sağlayıcısında yazma işlemleri. Yalnız elle bağlanmış bir test klasörü varsa çalışır
 * (test-library/sync-source.txt içinde content:// ağaç URI'si; klasörde Ders/x.pdf bulunmalı).
 */
@RunWith(AndroidJUnit4::class)
class SafWriteDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun safWriteOperations() {
        val uri=runCatching { File(context.filesDir,"test-library/sync-source.txt").readText().trim() }.getOrNull()
        assumeTrue("SAF test klasörü bağlı değil",uri!=null && uri.startsWith("content://") && uri.contains("GVNnotes-test"))
        val tree=SafSourceTree(context,Uri.parse(uri))
        assertTrue("Yazma izni alınmamış",tree.writable)
        fun file(path:String)=tree.scan().items.firstOrNull { !it.isDirectory && (if(it.path.isEmpty()) it.name else "${it.path}/${it.name}")==path }
        val x=checkNotNull(file("Ders/x.pdf")) { "Ders/x.pdf yok" }
        tree.createFolder("","Hedef")
        assertThrows(NameTakenException::class.java) { tree.createFolder("","Hedef") }
        assertTrue(tree.scan().items.any { it.isDirectory && it.name=="Hedef" })
        val moved=tree.move(x.documentId,"Ders","Hedef","x.pdf")
        assertNotNull(file("Hedef/x.pdf")); assertNull(file("Ders/x.pdf"))
        val trashed=tree.moveToTrash(moved,"Hedef","x.pdf")
        assertNull(file("Hedef/x.pdf")) // Gizli çöp klasörü taranmaz.
        val back=tree.restoreFromTrash(trashed,"Ders","x.pdf")
        assertNotNull(file("Ders/x.pdf"))
        assertTrue(tree.open(back).use { it.read() }>=0)
    }
}
