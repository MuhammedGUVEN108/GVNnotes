package com.gvn.notes

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.graphics.RectF
import com.gvn.notes.core.*
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.util.UUID

/** Sayfa/çizgi bazlı işlemler; değişmeyen sayfaların noktaları kayıt sırasında okunmaz. */
internal class PageInkDatabase(private val file:File) {
    private var savedGroups:Map<String,List<RenderedStroke>> = emptyMap()
    private var savedPages:List<Page> = emptyList()
    var lastWrittenPoints=0L; private set
    var lastWrittenPages=0; private set

    fun open():PageSnapshot? = connection(file,false).use { db ->
        if(db.version>PageStore.SCHEMA) throw NewerSaveFormatException()
        val headerText=db.rawQuery("SELECT value FROM meta WHERE key='header'",null).use { if(it.moveToFirst()) it.getString(0) else null } ?: return@use null
        val header=JSONObject(headerText)
        val folder=Folder(header.getString("folderId"),null,header.getString("folderName"),header.getString("folderPath"))
        val note=Note(header.getString("noteId"),folder.id,header.getString("title"))
        val source=header.optJSONObject("source")?.let { NoteSource(it.getString("sha"),it.getString("name"),it.getInt("pages")) }
        val pages=ArrayList<Page>()
        db.rawQuery("SELECT id,ordinal,width,height,background,sourcePage,pattern FROM pages ORDER BY ordinal",null).use { c ->
            while(c.moveToNext()) pages+=Page(c.getString(0),note.id,c.getInt(1),c.getFloat(2),c.getFloat(3),
                BackgroundType.valueOf(c.getString(4)),sourceBlobHash=source?.sha256,sourcePageIndex=c.getInt(5).takeIf { it>=0 },pattern=c.getInt(6))
        }
        require(pages.isNotEmpty() && pages.size<=PageStore.MAX_PAGES)
        val groups=LinkedHashMap<String,MutableList<RenderedStroke>>()
        db.rawQuery("SELECT id,page,fmt,tool,color,width,token,count,l,t,r,b FROM strokes ORDER BY page,ordinal",null).use { c ->
            while(c.moveToNext()) {
                val page=c.getString(1); val count=c.getInt(7); require(count>0)
                val samples=SampleHandle.disk(file,c.getString(6),count,RectF(c.getFloat(8),c.getFloat(9),c.getFloat(10),c.getFloat(11)))
                groups.getOrPut(page) { ArrayList() }+=RenderedStroke(PageStroke(c.getString(0),page,c.getInt(2),Tool.valueOf(c.getString(3)),c.getInt(4),c.getFloat(5),samples))
            }
        }
        val ordered=pages.associate { it.id to groups[it.id].orEmpty() }
        savedGroups=ordered; savedPages=pages
        PageSnapshot(header.getLong("revision"),folder,note,pages,FlattenedInk(ordered.values.toList(),ordered),source,ordered)
    }

    fun save(snapshot:PageSnapshot) {
        val groups=snapshot.groupedInk()
        require(snapshot.pages.size<=PageStore.MAX_PAGES && snapshot.pages.map { it.id }.toSet().size==snapshot.pages.size)
        val ids=snapshot.pages.map { it.id }.toSet(); require(groups.keys.all { it in ids })
        val bind=ArrayList<Pair<SampleHandle,SampleRef>>()
        lastWrittenPoints=0; lastWrittenPages=0
        connection(file,true).use { db ->
            db.beginTransactionNonExclusive()
            try {
                val oldPages=HashSet<String>()
                db.rawQuery("SELECT id FROM pages",null).use { c -> while(c.moveToNext()) oldPages+=c.getString(0) }
                for(p in snapshot.pages) {
                    val values=ContentValues().apply {
                        put("id",p.id); put("ordinal",p.order); put("width",p.width); put("height",p.height)
                        put("background",p.background.name); put("sourcePage",p.sourcePageIndex ?: -1); put("pattern",p.pattern)
                    }
                    if(p.id !in oldPages) db.insertOrThrow("pages",null,values)
                    else if(savedPages.firstOrNull { it.id==p.id }!=p) db.update("pages",values,"id=?",arrayOf(p.id))
                    val own=groups[p.id].orEmpty(); val before=savedGroups[p.id]
                    if(before===own || before==own) continue
                    lastWrittenPages++
                    val existing=HashSet<String>()
                    db.rawQuery("SELECT id FROM strokes WHERE page=?",arrayOf(p.id)).use { c -> while(c.moveToNext()) existing+=c.getString(0) }
                    val prior=before.orEmpty().withIndex().associate { it.value.data.id to it }
                    for((ordinal,stroke) in own.withIndex()) {
                        val s=stroke.data; require(s.pageId==p.id && s.width.isFinite() && s.width>0 && s.samples.isNotEmpty())
                        existing.remove(s.id)
                        val old=prior[s.id]
                        if(old?.value===stroke && old.index==ordinal) continue
                        val handle=s.samples as SampleHandle
                        val ref=handle.reference?.takeIf { it.file==file.absolutePath }
                            ?: writeSamples(db,handle).also { bind+=handle to it }
                        val b=handle.bounds
                        db.insertWithOnConflict("strokes",null,ContentValues().apply {
                            put("id",s.id); put("page",p.id); put("ordinal",ordinal); put("fmt",s.formatVersion)
                            put("tool",s.tool.name); put("color",s.color); put("width",s.width); put("token",ref.token); put("count",handle.size)
                            put("l",b.left); put("t",b.top); put("r",b.right); put("b",b.bottom)
                        },SQLiteDatabase.CONFLICT_REPLACE).also { check(it!=-1L) { "Çizgi kaydedilemedi." } }
                    }
                    existing.forEach { db.delete("strokes","id=?",arrayOf(it)) }
                }
                (oldPages-ids).forEach { db.delete("pages","id=?",arrayOf(it)) }
                val h=JSONObject().put("revision",snapshot.revision).put("folderId",snapshot.folder.id)
                    .put("folderName",snapshot.folder.name).put("folderPath",snapshot.folder.relativePath)
                    .put("noteId",snapshot.note.id).put("title",snapshot.note.title)
                snapshot.source?.let { h.put("source",JSONObject().put("sha",it.sha256).put("name",it.displayName).put("pages",it.pageCount)) }
                db.insertWithOnConflict("meta",null,ContentValues().apply { put("key","header"); put("value",h.toString()) },SQLiteDatabase.CONFLICT_REPLACE)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            // Noktalar ancak transaction başarıyla tamamlandıktan sonra RAM'den bırakılabilir.
            bind.forEach { (handle,ref) -> handle.bind(ref) }
            if(snapshot.revision%128L==0L) collectUnused(db)
        }
        savedGroups=groups.toMap(); savedPages=snapshot.pages
    }
    private fun writeSamples(db:SQLiteDatabase,handle:SampleHandle):SampleRef {
        val token=UUID.randomUUID().toString()
        db.insertOrThrow("sample_sets",null,ContentValues().apply { put("token",token); put("count",handle.size) })
        val iterator=handle.iterator(); var seq=0; var previous=Long.MIN_VALUE
        while(iterator.hasNext()) {
            val bytes=ByteArrayOutputStream(CHUNK*28)
            DataOutputStream(bytes).use { out ->
                var count=0
                while(iterator.hasNext() && count++<CHUNK) {
                    val p=iterator.next(); require(p.x.isFinite() && p.y.isFinite() && p.pressure.isFinite() && p.tiltRadians.isFinite() && p.orientationRadians.isFinite() && p.elapsedMillis>=previous); previous=p.elapsedMillis
                    out.writeFloat(p.x); out.writeFloat(p.y); out.writeFloat(p.pressure); out.writeFloat(p.tiltRadians); out.writeFloat(p.orientationRadians); out.writeLong(p.elapsedMillis)
                    lastWrittenPoints++
                }
            }
            val data=bytes.toByteArray()
            db.insertOrThrow("chunks",null,ContentValues().apply { put("token",token); put("seq",seq++); put("data",data); put("sha",digest(data)) })
        }
        return SampleRef(file.absolutePath,token)
    }
    private fun collectUnused(db:SQLiteDatabase) {
        val live=SampleHandle.liveTokens(file.absolutePath); val unused=ArrayList<String>()
        db.rawQuery("SELECT token FROM sample_sets s WHERE NOT EXISTS(SELECT 1 FROM strokes t WHERE t.token=s.token) LIMIT 4096",null).use { c ->
            while(c.moveToNext()) c.getString(0).takeIf { it !in live }?.let { unused+=it }
        }
        db.beginTransactionNonExclusive()
        try { unused.forEach { db.delete("sample_sets","token=?",arrayOf(it)) }; db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
    companion object {
        private const val CHUNK=256
        private fun digest(data:ByteArray)=MessageDigest.getInstance("SHA-256").digest(data)
        internal fun connection(file:File,write:Boolean):SQLiteDatabase {
            if(write) check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
            val flags=SQLiteDatabase.NO_LOCALIZED_COLLATORS or if(write) SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING else SQLiteDatabase.OPEN_READONLY
            // Varsayılan corruption handler'ın test/kullanıcı dosyasını otomatik silmesine izin verme.
            val db=SQLiteDatabase.openDatabase(file.path,null,flags,android.database.DatabaseErrorHandler { })
            if(write) {
                db.setForeignKeyConstraintsEnabled(true); db.execSQL("PRAGMA synchronous=FULL")
                if(db.version==0) {
                    db.beginTransactionNonExclusive()
                    try {
                        db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE pages(id TEXT PRIMARY KEY,ordinal INTEGER NOT NULL,width REAL,height REAL,background TEXT,sourcePage INTEGER,pattern INTEGER)")
                        db.execSQL("CREATE TABLE sample_sets(token TEXT PRIMARY KEY,count INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE strokes(id TEXT PRIMARY KEY,page TEXT NOT NULL REFERENCES pages(id) ON DELETE CASCADE,ordinal INTEGER,fmt INTEGER,tool TEXT,color INTEGER,width REAL,token TEXT NOT NULL REFERENCES sample_sets(token),count INTEGER,l REAL,t REAL,r REAL,b REAL)")
                        db.execSQL("CREATE INDEX strokes_page ON strokes(page,ordinal)")
                        db.execSQL("CREATE INDEX strokes_token ON strokes(token)")
                        db.execSQL("CREATE TABLE chunks(token TEXT NOT NULL REFERENCES sample_sets(token) ON DELETE CASCADE,seq INTEGER NOT NULL,data BLOB NOT NULL,sha BLOB NOT NULL,PRIMARY KEY(token,seq))")
                        db.version=PageStore.SCHEMA; db.setTransactionSuccessful()
                    } finally { db.endTransaction() }
                }
            }
            if(db.version>PageStore.SCHEMA) { db.close(); throw NewerSaveFormatException() }
            if(db.version!=PageStore.SCHEMA) { db.close(); error("Kayıt sürümü desteklenmiyor.") }
            return db
        }
        fun loadSamples(refs:List<SampleRef>):Map<SampleRef,List<Sample>> {
            val result=HashMap<SampleRef,List<Sample>>()
            refs.groupBy { it.file }.forEach { (path,group) ->
                connection(File(path),false).use { db ->
                    group.chunked(400).forEach { batch ->
                        val lists=HashMap<String,ArrayList<Sample>>(); val expected=HashMap<String,Int>()
                        val marks=batch.joinToString(",") { "?" }
                        db.rawQuery("SELECT c.token,c.seq,c.data,c.sha,s.count FROM chunks c JOIN sample_sets s ON s.token=c.token WHERE c.token IN ($marks) ORDER BY c.token,c.seq",batch.map { it.token }.toTypedArray()).use { c ->
                            val sequence=HashMap<String,Int>()
                            while(c.moveToNext()) {
                                val token=c.getString(0); expected[token]=c.getInt(4); check(c.getInt(1)==(sequence[token] ?: 0)); sequence[token]=c.getInt(1)+1
                                val data=c.getBlob(2); check(digest(data).contentEquals(c.getBlob(3))) { "Mürekkep parçasının bütünlüğü doğrulanamadı." }
                                val list=lists.getOrPut(token) { ArrayList(c.getInt(4).coerceAtMost(100_000)) }
                                DataInputStream(ByteArrayInputStream(data)).use { input ->
                                    require(data.size%28==0)
                                    repeat(data.size/28) { list+=Sample(input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat(),input.readFloat(),input.readLong()) }
                                }
                                if(c.isLast || list.size==c.getInt(4)) { check(list.size<=c.getInt(4)) }
                            }
                        }
                        batch.forEach { ref ->
                            val list=checkNotNull(lists[ref.token]) { "Mürekkep parçası bulunamadı." }
                            check(list.size==expected[ref.token]) { "Mürekkep noktaları eksik." }; result[ref]=list
                        }
                    }
                }
            }
            return result
        }
    }
}
