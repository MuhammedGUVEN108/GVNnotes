package com.gvn.notes

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private val LibInk=Color(0xff1f2328)
private val LibMuted=Color(0xff6b7075)
private val LibAccent=Color(0xff186c84)
private val LibBorder=Color(0xffe3e5e8)
private val LibSoft=Color(0xffdcecf1)

/** Tabletteki önerilen kaynak klasör: Documents/GVNnotes (kullanıcı kararı, 11 Kasım 2026). */
private val SuggestedFolder=DocumentsContract.buildDocumentUri("com.android.externalstorage.documents","primary:Documents/GVNnotes")

private fun parentOf(path:String)=path.substringBeforeLast('/',"")
/** Bir üst görünüm: alt klasörden üst klasöre, en üst klasörden ve "Klasörler"den "Tüm notlar"a. */
private fun upOf(path:String):String?=if(path.contains('/')) parentOf(path) else null
private fun nameOf(path:String)=path.substringAfterLast('/')

/**
 * Not listesi: Samsung Notes'taki düzen referans alınır. [folder] null = Tüm notlar,
 * "" = Klasörler kökü, "A/B" = alt klasör. Klasörler kaynak klasörden (Documents/GVNnotes) gelir.
 */
@Composable fun LibraryScreen(state:LibraryState) {
    var showNew by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var showTrash by rememberSaveable { mutableStateOf(false) }
    var renamingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val context=LocalContext.current
    val drawer=rememberDrawerState(DrawerValue.Closed)
    val scope=rememberCoroutineScope()
    // Uygulama öne geldiğinde kaynak klasör yeniden taranır (anlık arka plan algılaması yoktur).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.sync() }
    BackHandler(enabled=folder!=null || drawer.isOpen || showTrash) {
        if(drawer.isOpen) scope.launch { drawer.close() } else if(showTrash) showTrash=false else folder=folder?.let { upOf(it) }
    }
    // Kısa işlem bildirimi birkaç saniye sonra kaybolur.
    LaunchedEffect(state.actionMessage) { if(state.actionMessage!=null) { kotlinx.coroutines.delay(3500); state.actionMessage=null } }
    // Sistem dosya seçicisi; uygulama yalnız kullanıcının seçtiği PDF'yi okur ve kendi alanına kopyalar.
    val pickPdf=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) {
            val name=runCatching {
                context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c ->
                    if(c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()?.removeSuffix(".pdf")?.removeSuffix(".PDF") ?: "PDF notu"
            state.importPdf(name) { checkNotNull(context.contentResolver.openInputStream(uri)) { "PDF okunamadı." } }
        }
    }
    // Kaynak klasör seçici: izin kalıcı alınır, sonraki açılışlarda yeniden sorulmaz.
    val pickFolder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if(uri!=null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            state.link(uri.toString())
        }
    }
    val allFolders=remember(state.folders,state.notes) {
        (state.folders+state.notes.map { it.folderPath }.filter { it.isNotEmpty() })
            .flatMap { p -> generateSequence(p) { parentOf(it).ifEmpty { null } }.toList() }.toSortedSet().toList()
    }
    fun countUnder(path:String)=state.notes.count { it.folderPath==path || it.folderPath.startsWith("$path/") }
    val current=folder
    val childFolders=allFolders.filter { parentOf(it)==(current ?: "") }
    val inFolder=if(current==null) state.notes else state.notes.filter { it.folderPath==current }
    val visible=if(query.isBlank()) inFolder else state.notes.filter { it.title.contains(query.trim(),ignoreCase=true) }

    ModalNavigationDrawer(drawerState=drawer,drawerContent={
        ModalDrawerSheet(Modifier.width(340.dp),drawerContainerColor=Color(0xfffafafa)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical=16.dp)) {
                DrawerRow(Icons.Outlined.Description,"Tüm notlar",state.notes.size,current==null && !showTrash,0) { folder=null; showTrash=false; scope.launch { drawer.close() } }
                DrawerRow(Icons.Outlined.Delete,"Çöp kutusu",state.trash.size,showTrash,0) { showTrash=true; scope.launch { drawer.close() } }
                HorizontalDivider(Modifier.padding(horizontal=20.dp,vertical=8.dp),color=LibBorder)
                DrawerRow(Icons.Outlined.Folder,"Klasörler",state.notes.count { it.folderPath.isEmpty() },current=="" && !showTrash,0) { folder=""; showTrash=false; scope.launch { drawer.close() } }
                allFolders.forEach { p ->
                    DrawerRow(Icons.Outlined.FolderOpen,nameOf(p),countUnder(p),current==p && !showTrash,p.count { it=='/' }+1) { folder=p; showTrash=false; scope.launch { drawer.close() } }
                }
                HorizontalDivider(Modifier.padding(horizontal=20.dp,vertical=8.dp),color=LibBorder)
                Text(if(state.source==null) "Kaynak klasör bağlı değil" else "Kaynak klasör: Documents/GVNnotes",
                    style=MaterialTheme.typography.bodySmall,color=LibMuted,modifier=Modifier.padding(horizontal=24.dp,vertical=4.dp))
                Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(state.source!=null) TextButton(onClick={state.sync()},enabled=!state.syncing) { Text("Şimdi eşitle") }
                    TextButton(onClick={pickFolder.launch(SuggestedFolder)}) { Text(if(state.source==null) "Klasörü bağla" else "Klasörü değiştir") }
                }
            }
        }
    }) {
    Box(Modifier.fillMaxSize().background(Color(0xfffafafa)).testTag("library").safeDrawingPadding()) {
        if(showTrash) TrashView(state,onBack={showTrash=false},onDelete={deletingKey=it}) else Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(72.dp).padding(start=8.dp,end=12.dp),verticalAlignment=Alignment.CenterVertically) {
                if(searching) {
                    IconButton(onClick={searching=false; query=""}) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Aramayı kapat",tint=LibInk) }
                    OutlinedTextField(value=query,onValueChange={query=it},singleLine=true,placeholder={Text("Notlarda ara")},
                        modifier=Modifier.weight(1f).testTag("search-field"),shape=RoundedCornerShape(24.dp))
                } else {
                    if(current==null) IconButton(onClick={scope.launch { drawer.open() }},modifier=Modifier.testTag("drawer")) { Icon(Icons.Outlined.Menu,"Gezinme menüsü",tint=LibInk) }
                    else IconButton(onClick={folder=upOf(current)}) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Üst klasör",tint=LibInk) }
                    Column(Modifier.weight(1f).padding(start=8.dp)) {
                        Text(when(current) { null->"Tüm notlar"; ""->"Klasörler"; else->nameOf(current) },
                            fontSize=26.sp,fontWeight=FontWeight.SemiBold,color=LibInk,maxLines=1,overflow=TextOverflow.Ellipsis,
                            modifier=Modifier.testTag("library-title"))
                        Text(listOfNotNull("${inFolder.size} not",state.syncMessage).joinToString(" · "),
                            style=MaterialTheme.typography.bodyMedium,color=LibMuted,maxLines=1,overflow=TextOverflow.Ellipsis,
                            modifier=Modifier.testTag("sync-status"))
                    }
                    IconButton(onClick={ if(!state.importing) { state.clearImportError(); pickPdf.launch(arrayOf("application/pdf")) } },
                        enabled=!state.importing,modifier=Modifier.testTag("import-pdf")) {
                        Icon(Icons.Outlined.PictureAsPdf,"PDF'yi içe aktar",tint=LibInk)
                    }
                    IconButton(onClick={searching=true}) { Icon(Icons.Outlined.Search,"Ara",tint=LibInk) }
                    Box {
                        IconButton(onClick={menu=true}) { Icon(Icons.Outlined.MoreVert,"Diğer seçenekler",tint=LibInk) }
                        DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                            DropdownMenuItem(text={Text("Notları yenile")},onClick={menu=false; state.refresh(); state.sync()},
                                leadingIcon={Icon(Icons.Outlined.Refresh,null)})
                            DropdownMenuItem(text={Text(if(state.source==null) "Klasörü bağla" else "Kaynak klasörü değiştir")},
                                onClick={menu=false; pickFolder.launch(SuggestedFolder)},leadingIcon={Icon(Icons.Outlined.CreateNewFolder,null)})
                        }
                    }
                }
            }
            if(state.syncing) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp),color=LibAccent,trackColor=Color.Transparent)
            when {
                state.loading && state.notes.isEmpty() -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(color=LibAccent); Text("Notlar açılıyor…",color=LibMuted)
                    }
                }
                state.error!=null -> Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text(state.error!!); Button(onClick={state.refresh()}){Text("Yeniden dene")}
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=24.dp,end=24.dp,top=8.dp,bottom=120.dp)) {
                    if(state.source==null && current==null && query.isBlank()) item {
                        LinkCard { pickFolder.launch(SuggestedFolder) }
                    }
                    if(query.isBlank() && childFolders.isNotEmpty()) item {
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(vertical=12.dp)) {
                            items(childFolders,key={it}) { p -> FolderTile(nameOf(p),countUnder(p)) { folder=p } }
                        }
                    }
                    if(visible.isEmpty()) item {
                        Box(Modifier.fillMaxWidth().padding(top=64.dp),contentAlignment=Alignment.Center) {
                            Text(when {
                                query.isNotBlank() -> "Eşleşen not yok"
                                state.notes.isEmpty() -> "İlk notunu oluştur ya da bir PDF içe aktar."
                                else -> "Bu klasörde not yok"
                            },color=LibMuted)
                        }
                    }
                    items(visible,key={it.key}) { note ->
                        NoteRow(note,state.sourceStates[note.key],showFolder=current==null,
                            onRename={renamingKey=note.key},onTrash={state.moveToTrash(note.key)}) { state.open(note.key) }
                    }
                }
            }
        }
        if(state.importing) Surface(Modifier.align(Alignment.BottomCenter).padding(bottom=32.dp),shape=RoundedCornerShape(24.dp),shadowElevation=3.dp,color=Color.White) {
            Row(Modifier.padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=LibAccent); Text("PDF açılıyor…")
            }
        }
        state.actionMessage?.let { message ->
            Surface(Modifier.align(Alignment.BottomCenter).padding(bottom=36.dp),shape=RoundedCornerShape(24.dp),shadowElevation=3.dp,color=LibInk) {
                Text(message,color=Color.White,modifier=Modifier.padding(horizontal=22.dp,vertical=14.dp).testTag("action-message"))
            }
        }
        // Sağ altta yuvarlak yeni not düğmesi.
        if(!showTrash) Surface(onClick={title=""; state.clearCreationError(); showNew=true},shape=CircleShape,color=Color.White,shadowElevation=4.dp,
            modifier=Modifier.align(Alignment.BottomEnd).padding(28.dp).size(72.dp).testTag("new-note")
                .semantics { contentDescription="Yeni not" }) {
            Box(contentAlignment=Alignment.Center) { Icon(Icons.Outlined.EditNote,null,tint=LibAccent,modifier=Modifier.size(34.dp)) }
        }
    }
    }
    renamingKey?.let { key ->
        val note=state.notes.firstOrNull { it.key==key }
        if(note==null) renamingKey=null else {
            var name by remember(key) { mutableStateOf(note.title) }
            AlertDialog(onDismissRequest={renamingKey=null},title={Text("Yeniden adlandır")},
                text={OutlinedTextField(value=name,onValueChange={ if(it.length<=100) name=it },singleLine=true,modifier=Modifier.fillMaxWidth().testTag("rename-field"))},
                confirmButton={TextButton(onClick={state.rename(key,name); renamingKey=null},enabled=name.isNotBlank(),modifier=Modifier.testTag("rename-confirm")){Text("Kaydet")}},
                dismissButton={TextButton(onClick={renamingKey=null}){Text("Vazgeç")}})
        }
    }
    deletingKey?.let { key ->
        val note=state.trash.firstOrNull { it.key==key }
        AlertDialog(onDismissRequest={deletingKey=null},title={Text("Kalıcı olarak silinsin mi?")},
            text={Text("\"${note?.title ?: "Not"}\" ve üzerindeki tüm yazılar kalıcı olarak silinir. Bu işlem geri alınamaz.")},
            confirmButton={TextButton(onClick={state.deleteForever(key); deletingKey=null},modifier=Modifier.testTag("confirm-delete-forever")){Text("Kalıcı sil",color=Color(0xffb3261e))}},
            dismissButton={TextButton(onClick={deletingKey=null}){Text("Vazgeç")}})
    }
    state.importError?.let { message ->
        AlertDialog(onDismissRequest={state.clearImportError()},title={Text("PDF içe aktarılamadı")},text={Text(message)},
            confirmButton={TextButton(onClick={state.clearImportError()}){Text("Tamam")}})
    }
    if(showNew) AlertDialog(onDismissRequest={if(!state.creating)showNew=false},title={Text("Yeni not")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value=title,onValueChange={if(it.length<=100)title=it},singleLine=true,
                label={Text("Not adı")},enabled=!state.creating,modifier=Modifier.fillMaxWidth().testTag("note-title"))
            state.creationError?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
        }},confirmButton={TextButton(onClick={state.create(title){showNew=false}},enabled=title.isNotBlank()&&!state.creating,
            modifier=Modifier.testTag("create-note")) {Text(if(state.creating)"Oluşturuluyor…" else "Oluştur")}},
        dismissButton={TextButton(onClick={showNew=false},enabled=!state.creating){Text("Vazgeç")}})
}

@Composable private fun DrawerRow(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,count:Int,selected:Boolean,depth:Int,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp).clip(RoundedCornerShape(14.dp))
        .background(if(selected) LibSoft else Color.Transparent).clickable(onClick=onClick)
        .padding(start=(12+depth*20).dp,end=16.dp,top=14.dp,bottom=14.dp).semantics { contentDescription="$label, $count not" },
        verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,null,tint=if(selected) LibAccent else LibInk)
        Text(label,Modifier.weight(1f).padding(start=16.dp),fontSize=18.sp,color=LibInk,maxLines=1,overflow=TextOverflow.Ellipsis,
            fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal)
        Text("$count",color=LibMuted,style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun FolderTile(name:String,count:Int,onClick:()->Unit) {
    Column(Modifier.width(132.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xfff1f3f4)).clickable(onClick=onClick)
        .padding(14.dp).semantics { contentDescription="Klasör, $name, $count not" }.testTag("folder-$name"),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.Folder,null,tint=LibAccent)
            Spacer(Modifier.weight(1f)); Text("$count",color=LibMuted,style=MaterialTheme.typography.labelMedium)
        }
        Text(name,color=LibInk,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}

@Composable private fun LinkCard(onLink:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp).clip(RoundedCornerShape(16.dp)).background(LibSoft).padding(20.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Icon(Icons.Outlined.FolderSpecial,null,tint=LibAccent,modifier=Modifier.size(32.dp))
        Column(Modifier.weight(1f).padding(horizontal=16.dp)) {
            Text("Documents/GVNnotes klasörünü bağla",fontWeight=FontWeight.SemiBold,color=LibInk)
            Text("Bu klasöre koyduğun PDF'ler alt klasörleriyle birlikte otomatik not olur.",color=LibMuted,style=MaterialTheme.typography.bodyMedium)
        }
        Button(onClick=onLink,colors=ButtonDefaults.buttonColors(containerColor=LibAccent),modifier=Modifier.testTag("link-folder")) { Text("Bağla") }
    }
}

private val dayFormat=SimpleDateFormat("d MMM",Locale("tr"))
private val timeFormat=SimpleDateFormat("HH:mm",Locale("tr"))
private fun shortDate(time:Long)=if(DateUtils.isToday(time)) timeFormat.format(time) else dayFormat.format(time)

private data class PreviewState(val bitmap:Bitmap?,val loaded:Boolean)
@Composable private fun NoteRow(note:NoteCard,source:SourceState?,showFolder:Boolean,onRename:(()->Unit)?=null,onTrash:(()->Unit)?=null,
    trailing:(@Composable ()->Unit)?=null,onClick:()->Unit) {
    val preview by produceState(PreviewState(null,false),note.key,note.revision,note.preview?.path) {
        value=PreviewState(null,false)
        value=PreviewState(withContext(Dispatchers.IO) { note.preview?.let { BitmapFactory.decodeFile(it.path) } },true)
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick=onClick).padding(vertical=10.dp).testTag("note-${note.key}"),
        verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(96.dp).border(1.dp,LibBorder,RoundedCornerShape(14.dp)).background(Color.White,RoundedCornerShape(14.dp)).padding(6.dp),
            contentAlignment=Alignment.Center) {
            preview.bitmap?.let { Image(it.asImageBitmap(),contentDescription=null,modifier=Modifier.fillMaxSize().testTag("preview-${note.key}"),contentScale=ContentScale.Fit) }
                ?: Icon(Icons.Outlined.Description,null,tint=LibBorder)
        }
        Column(Modifier.padding(start=20.dp).weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(note.title,fontSize=20.sp,color=LibInk,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(if(note.unreadable) "Kayıt açılamadı · açıp yeniden deneyin"
                else listOfNotNull(shortDate(note.modified),"${note.pageCount} sayfa",if(note.isPdf) "PDF" else null,
                    if(showFolder && note.folderPath.isNotEmpty()) note.folderPath else null).joinToString(" · "),
                style=MaterialTheme.typography.bodyMedium,color=LibMuted,maxLines=1,overflow=TextOverflow.Ellipsis)
            when(source) {
                SourceState.MISSING -> Text("Kaynak PDF klasörde bulunamadı · yazılar korunuyor",style=MaterialTheme.typography.bodySmall,color=Color(0xffb3261e))
                SourceState.REPLACED -> Text("Kaynak PDF değişti · yeni sürüm ayrı not",style=MaterialTheme.typography.bodySmall,color=Color(0xff8a5a00))
                else -> {}
            }
        }
        trailing?.invoke()
        if(onRename!=null || onTrash!=null) Box {
            var open by remember { mutableStateOf(false) }
            IconButton(onClick={open=true},modifier=Modifier.testTag("note-menu-${note.title}")) { Icon(Icons.Outlined.MoreVert,"${note.title} seçenekleri",tint=LibMuted) }
            DropdownMenu(expanded=open,onDismissRequest={open=false}) {
                onRename?.let { DropdownMenuItem(text={Text("Yeniden adlandır")},onClick={open=false; it()},leadingIcon={Icon(Icons.Outlined.Edit,null)}) }
                onTrash?.let { DropdownMenuItem(text={Text("Çöp kutusuna taşı")},onClick={open=false; it()},leadingIcon={Icon(Icons.Outlined.Delete,null)},
                    modifier=Modifier.testTag("menu-trash")) }
            }
        }
    }
}

/** Çöp kutusu: notlar burada silinene kadar saklanır; geri yükleme notu eski klasörüne döndürür. */
@Composable private fun TrashView(state:LibraryState,onBack:()->Unit,onDelete:(String)->Unit) {
    Column(Modifier.fillMaxSize().testTag("trash")) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(start=8.dp,end=12.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Notlara dön",tint=LibInk) }
            Column(Modifier.weight(1f).padding(start=8.dp)) {
                Text("Çöp kutusu",fontSize=26.sp,fontWeight=FontWeight.SemiBold,color=LibInk)
                Text("${state.trash.size} not · silinene kadar saklanır",style=MaterialTheme.typography.bodyMedium,color=LibMuted)
            }
        }
        if(state.trash.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text("Çöp kutusu boş",color=LibMuted) }
        else LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=24.dp,end=24.dp,top=8.dp,bottom=48.dp)) {
            items(state.trash,key={it.key}) { note ->
                NoteRow(note,null,showFolder=true,trailing={
                    TextButton(onClick={state.restore(note.key)},modifier=Modifier.testTag("restore-${note.title}")) { Text("Geri yükle") }
                    TextButton(onClick={onDelete(note.key)},modifier=Modifier.testTag("delete-${note.title}")) { Text("Kalıcı sil",color=Color(0xffb3261e)) }
                }) {}
            }
        }
    }
}
