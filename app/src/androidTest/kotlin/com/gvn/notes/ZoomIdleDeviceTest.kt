package com.gvn.notes

import android.content.Intent
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gvn.notes.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 14 Kasım 2026 kullanıcı hatası: yatay tablette %150 üstü bir ölçekte mürekkep sürekli yanıp söndü.
 * Gerçek InkPageView'de (yatay, tam ekran, yoğun yatay PDF sayfaları) her ölçekte görünüm durulduktan sonra
 * kendiliğinden yeniden çizim olmamalı. Karo önbelleği, PDF arka planı ve sayfa hazırlama döngülerinin hepsini kapsar.
 */
@RunWith(AndroidJUnit4::class)
class ZoomIdleDeviceTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private fun ui(block:()->Unit)=inst.runOnMainSync(block)

    @Test fun everyZoomLevelBecomesIdle()=sweep(portrait=false)
    /** Dik A4: 4096 px'lik sayfa görüntüsü ~95 MB; iki sayfa 160 MB PDF önbelleğini aşar. Sayfa üretimi de durmalı. */
    @Test fun everyZoomLevelBecomesIdlePortrait()=sweep(portrait=true)

    private fun sweep(portrait:Boolean) {
        // Etkinlik açılmadan önce yataya sabitle (açıldıktan sonra döndürmek etkinliği yeniden oluşturur).
        inst.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90); SystemClock.sleep(1500)
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
            .putExtra("gvnnotes.testDocument",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SystemClock.sleep(800)
        val root=File(inst.targetContext.filesDir,"test-library/zoom-idle-"+(if(portrait) "dik" else "yatay")); root.deleteRecursively()
        val library=NoteLibrary(root)
        val pdf=File(inst.targetContext.cacheDir,"zoom-idle.pdf")
        PdfDocument().apply {
            val pw=if(portrait) 595 else 842; val ph=if(portrait) 842 else 595
            repeat(3) { val p=startPage(PdfDocument.PageInfo.Builder(pw,ph,it+1).create())
                p.canvas.drawRect(40f,40f,pw-40f,ph-40f,android.graphics.Paint().apply { color=Color.rgb(240,236,220) }); finishPage(p) }
            pdf.outputStream().use { writeTo(it) }; close()
        }
        val key=library.importPdf("Ölçek boşta") { pdf.inputStream() }
        lateinit var editor:EditorState; lateinit var view:InkPageView
        ui { editor=EditorState(library.directory(key)); view=InkPageView(activity,editor); activity.setContentView(view); view.usePen(onlyPen=true) }
        val end=SystemClock.uptimeMillis()+15000
        while(SystemClock.uptimeMillis()<end) { var ok=false; ui { ok=editor.ready && view.width>0 && editor.fitted }; if(ok) break; SystemClock.sleep(50) }
        // Her sayfayı baştan sona kaplayan yoğun mürekkep (sayfa başına 8400 nokta, arka plan karo yolu).
        ui {
            for(page in editor.pages) editor.replacePage(page.id,List(70) { n -> RenderedStroke(PageStroke(pageId=page.id,tool=Tool.PEN,
                color=Color.BLACK,width=3f,samples=List(120) { k -> Sample(10f+k*8.3f,8f+n*(page.height-16f)/70f,.8f,0f,0f,k.toLong()) })) })
        }
        fun shell(cmd:String)=inst.uiAutomation.executeShellCommand(cmd).let { fd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes().toString(Charsets.UTF_8) } }
        shell("dumpsys gfxinfo ${inst.targetContext.packageName} reset")
        var draws=0
        val listener=ViewTreeObserver.OnDrawListener { draws++ }
        ui { view.viewTreeObserver.addOnDrawListener(listener) }
        val loops=ArrayList<String>(); val report=StringBuilder()
        try {
            var w=0; var h=0; var pageH=0f
            ui { w=view.width; h=view.height; pageH=editor.pages[0].height }
            assertTrue("Test yatay ekranda çalışmalı: ${w}x$h",w>h)
            var s=1.5f
            while(s<=5.0001f) {
                val scale=s
                // İlk iki sayfanın sınırı ekranın ortasında, yatayda ortalı.
                ui { editor.viewport=Viewport(scale,(w-1000f*scale)/2,h/2f-(pageH+InkPageView.GAP/2)*scale); view.refresh() }
                // Durulma: 500 ms boyunca hiç çizim yok. En çok 6 s beklenir.
                val deadline=SystemClock.uptimeMillis()+6000
                var idle=false; var lastCount=-1; var quietSince=SystemClock.uptimeMillis()
                while(SystemClock.uptimeMillis()<deadline) {
                    SystemClock.sleep(50)
                    var count=0; ui { count=draws }
                    if(count!=lastCount) { lastCount=count; quietSince=SystemClock.uptimeMillis() }
                    else if(SystemClock.uptimeMillis()-quietSince>=500) { idle=true; break }
                }
                var before=0; var rendersBefore=0; ui { before=draws; rendersBefore=editor.pdf?.renders ?: 0 }
                SystemClock.sleep(2000)
                var after=0; var rendersAfter=0; ui { after=draws; rendersAfter=editor.pdf?.renders ?: 0 }
                val label="%${Math.round(scale*100)}"
                // Geç biten tek seferlik iş (yoğun sayfada >500 ms süren karo/sayfa hazırlığı) döngü değildir:
                // hareket görülürse 2 s daha izlenir; döngü ancak ikinci pencerede de sürerse sayılır.
                var late=""
                var looping=!idle || after-before>2 || rendersAfter>rendersBefore
                if(looping) {
                    var d2=0; var r2=0; ui { d2=draws; r2=editor.pdf?.renders ?: 0 }
                    SystemClock.sleep(2000)
                    var d3=0; var r3=0; ui { d3=draws; r3=editor.pdf?.renders ?: 0 }
                    late=" sonra(${d3-d2}/pdf ${r3-r2})"
                    looping=d3-d2>2 || r3>r2
                }
                report.append("$label:${if(idle) "durgun" else "DONGU"}(${after-before}/pdf ${rendersAfter-rendersBefore})$late ")
                if(looping) loops+=label
                s+=.1f
            }
        } finally {
            ui { view.viewTreeObserver.removeOnDrawListener(listener) }; inst.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
            android.util.Log.i("GVNnotesTest","zoom-idle ${if(portrait) "dik" else "yatay"} $report")
            val stats=File(inst.targetContext.getExternalFilesDir(null),"zoom-idle-${if(portrait) "dik" else "yatay"}-gfxinfo.txt")
            runCatching { stats.writeText(shell("dumpsys gfxinfo ${inst.targetContext.packageName}")) }
            val mi=android.os.Debug.MemoryInfo(); android.os.Debug.getMemoryInfo(mi)
            android.util.Log.i("GVNnotesTest","zoom-idle-bellek ${if(portrait) "dik" else "yatay"} pss=${mi.totalPss/1024}MB graphics=${(mi.getMemoryStat("summary.graphics") ?: "0").toInt()/1024}MB native=${(mi.getMemoryStat("summary.native-heap") ?: "0").toInt()/1024}MB")
            root.deleteRecursively()
        }
        assertTrue("Bu ölçeklerde görünüm durulmadı (sürekli yeniden çizim): $loops",loops.isEmpty())
    }
}
