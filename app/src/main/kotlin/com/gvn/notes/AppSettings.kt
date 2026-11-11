package com.gvn.notes

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/**
 * Uygulama ayarları (notebooks kökünün yanındaki settings.json). Kalem ayarları ayrıca pen.txt'de tutulur.
 * Okuma hatasında varsayılanlar kullanılır; yazma atomiktir.
 */
data class AppSettings(
    /** Açıksa tek parmak da yazar; iki parmak her zaman kaydırır/yakınlaştırır. Varsayılan: yalnız kalem. */
    val fingerDraw:Boolean=false,
    /** Yeni notların ilk sayfa şablonu (PagePattern kodu). */
    val defaultPattern:Int=0,
) {
    fun save(file:File) {
        file.parentFile?.mkdirs()
        val atomic=AtomicFile(file); val out=atomic.startWrite()
        try {
            out.write(JSONObject().put("v",1).put("fingerDraw",fingerDraw).put("defaultPattern",defaultPattern).toString().toByteArray())
            atomic.finishWrite(out)
        } catch(e:Exception) { atomic.failWrite(out); throw e }
    }
    companion object {
        fun fileFor(notebooksRoot:File)=File(notebooksRoot.parentFile ?: notebooksRoot,"settings.json")
        fun load(file:File):AppSettings = runCatching {
            val o=JSONObject(String(AtomicFile(file).readFully(),Charsets.UTF_8))
            AppSettings(o.optBoolean("fingerDraw",false),o.optInt("defaultPattern",0).coerceIn(0,3))
        }.getOrDefault(AppSettings())
    }
}

/** Çökme kaydı: uygulama beklenmedik şekilde kapanırsa ayrıntı bu dosyaya eklenir (en çok 64 KB). */
object CrashLog {
    private var installed=false
    fun file(filesDir:File)=File(filesDir,"crash-log.txt")
    fun install(filesDir:File,version:String) {
        if(installed) return; installed=true
        val previous=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread,error ->
            runCatching {
                val f=file(filesDir)
                val text=buildString {
                    append("=== ").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.US).format(java.util.Date()))
                    append(" · GVNnotes ").append(version).append(" · Android ").append(android.os.Build.VERSION.RELEASE)
                    append(" · ").append(android.os.Build.MODEL).append(" · ").append(thread.name).append('\n')
                    append(android.util.Log.getStackTraceString(error)).append('\n')
                }
                val old=if(f.isFile) f.readText().takeLast(48*1024) else ""
                f.writeText(old+text)
            }
            previous?.uncaughtException(thread,error)
        }
    }
}
