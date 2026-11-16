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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/** Tabletteki önerilen kaynak klasör: dahili depolamanın kökündeki GVNnotes (kullanıcı kararı, 16 Kasım 2026; önce Documents/GVNnotes idi). */
private val SuggestedFolder=DocumentsContract.buildDocumentUri("com.android.externalstorage.documents","primary:GVNnotes")
/** Bağlı klasörün okunur adı: "Dahili depolama/GVNnotes". Eski yere bağlıysa gerçek yeri gösterir. */
internal fun sourceLabel(source:String?):String {
    val id=runCatching { android.net.Uri.parse(source).takeIf { it.scheme=="content" }?.let { DocumentsContract.getTreeDocumentId(it) } }.getOrNull()
    val path=id?.substringAfter(':',"")?.ifEmpty { null } ?: "GVNnotes"
    return "Dahili depolama/$path"
}

internal fun parentOf(path:String)=path.substringBeforeLast('/',"")
/** Bir üst görünüm: alt klasörden üst klasöre, en üst klasörden ve "Klasörler"den "Tüm notlar"a. */
private fun upOf(path:String):String?=if(path.contains('/')) parentOf(path) else null
internal fun nameOf(path:String)=path.substringAfterLast('/')

/** Kaynak klasördeki ve notlardaki tüm klasör yolları (üst klasörler dahil), sıralı. */
internal fun allFoldersOf(state:LibraryState)=(state.folders+state.notes.map { it.folderPath }.filter { it.isNotEmpty() })
    .flatMap { p -> generateSequence(p) { parentOf(it).ifEmpty { null } }.toList() }.toSortedSet().toList()

/**
 * Kütüphane ("Petrol Ada"): solda kalıcı, yüzen klasör ağacı adası; sağda not kartları. [folder] null = Tüm notlar,
 * "" = Klasörler kökü, "A/B" = alt klasör. Klasörler kaynak klasörden (dahili depolama/GVNnotes) gelir.
 * Dar ekranda klasör ağacı çekmece olarak açılır.
 */
@Composable fun LibraryScreen(state:LibraryState) {
    var showNew by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var pattern by rememberSaveable { mutableStateOf(0) }
    var newIn by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var showTrash by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var renamingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var movingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var newFolder by rememberSaveable { mutableStateOf(false) }
    var deletingFolder by rememberSaveable { mutableStateOf<String?>(null) }
    // Çoklu seçim (uzun bas): toplu taşıma / çöpe atma. Tek notun ⋯ menüsü ayrıca sürer.
    var selectedKeys by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var movingKeys by rememberSaveable { mutableStateOf<List<String>?>(null) }
    var trashingKeys by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val selecting=selectedKeys.isNotEmpty()
    // Seçim çubuğu solarak kapanırken "0 not seçili" görünmesin: son sayı tutulur.
    var shownCount by remember { mutableStateOf(1) }
    if(selecting) shownCount=selectedKeys.size
    fun toggle(key:String) { selectedKeys=if(key in selectedKeys) selectedKeys-key else selectedKeys+key }
    var sortByName by rememberSaveable { mutableStateOf(false) }
    val collapsed=remember { mutableStateListOf<String>() }
    val context=LocalContext.current
    val displayPrefs=remember { context.getSharedPreferences("library-display",0) }
    var compact by rememberSaveable { mutableStateOf(displayPrefs.getBoolean("compact",true)) }
    val drawer=rememberDrawerState(DrawerValue.Closed)
    val scope=rememberCoroutineScope()
    fun openNew(location:String) { title=""; pattern=state.settings.defaultPattern; newIn=location; state.clearCreationError(); showNew=true }
    // Düzenleyicideki Klasör adasından gelen istekler: klasöre git / o klasörde yeni not.
    LaunchedEffect(state.requestedFolder) {
        state.requestedFolder?.let { folder=it; showTrash=false; showSettings=false; searching=false; query=""; state.requestedFolder=null }
    }
    LaunchedEffect(state.requestedNewIn) {
        state.requestedNewIn?.let { folder=it; showTrash=false; showSettings=false; openNew(it); state.requestedNewIn=null }
    }
    // Uygulama öne geldiğinde kaynak klasör yeniden taranır (anlık arka plan algılaması yoktur).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.sync() }
    // Gezinme (klasör, arama, çöp, ayarlar) seçimi bırakır; eşitlemenin kaldırdığı notlar seçimden düşer.
    LaunchedEffect(folder,showTrash,showSettings,searching) { selectedKeys=emptyList() }
    LaunchedEffect(state.notes) { if(selectedKeys.isNotEmpty()) selectedKeys=selectedKeys.filter { k -> state.notes.any { it.key==k } } }
    BackHandler(enabled=selecting || folder!=null || drawer.isOpen || showTrash || showSettings) {
        if(selecting) selectedKeys=emptyList() else if(drawer.isOpen) scope.launch { drawer.close() } else if(showSettings) showSettings=false else if(showTrash) showTrash=false else folder=folder?.let { upOf(it) }
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
            // Okuma + yazma: taşıma, klasör oluşturma ve çöp kaynak klasöre yansısın diye.
            runCatching { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                .onFailure { runCatching { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            state.link(uri.toString())
        }
    }
    // Birden çok resim seçilebilir; seçim sırasıyla tek notun sayfaları olur.
    val pickImages=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if(uris.isNotEmpty()) {
            val name=runCatching {
                context.contentResolver.query(uris.first(),arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()?.let { titleOf(it) } ?: "Resim notu"
            state.importImages(name,folder ?: "",uris.map { u -> { checkNotNull(context.contentResolver.openInputStream(u)) { "Resim okunamadı." } } })
        }
    }
    val allFolders=remember(state.folders,state.notes) { allFoldersOf(state) }
    fun countUnder(path:String)=state.notes.count { it.folderPath==path || it.folderPath.startsWith("$path/") }
    val current=folder
    val childFolders=allFolders.filter { parentOf(it)==(current ?: "") }
    val inFolder=if(current==null) state.notes else state.notes.filter { it.folderPath==current }
    val found=if(query.isBlank()) inFolder else state.notes.filter { it.title.contains(query.trim(),ignoreCase=true) || it.folderPath.contains(query.trim(),ignoreCase=true) }
    val visible=if(sortByName) found.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }) else found

    BoxWithConstraints(Modifier.fillMaxSize().background(Ada.Desk).testTag("library").safeDrawingPadding()) {
        val wide=maxWidth>=840.dp
        val tree:@Composable ()->Unit={
            FolderTree(state,allFolders,collapsed,current,showTrash,showSettings,::countUnder,
                onSelect={ p -> folder=p; showTrash=false; showSettings=false; searching=false; query=""; scope.launch { drawer.close() } },
                onTrash={ showTrash=true; showSettings=false; scope.launch { drawer.close() } },
                onSettings={ showSettings=true; showTrash=false; scope.launch { drawer.close() } },
                onNewFolder={ newFolder=true },
                onLink={ pickFolder.launch(SuggestedFolder) })
        }
        ModalNavigationDrawer(drawerState=drawer,gesturesEnabled=!wide && drawer.isOpen,drawerContent={
            if(!wide) ModalDrawerSheet(Modifier.width(320.dp),drawerContainerColor=Ada.Surface) { tree() }
        }) {
        Row(Modifier.fillMaxSize()) {
            if(wide) Island(Modifier.padding(start=12.dp,top=12.dp,bottom=12.dp).width(248.dp).fillMaxHeight().testTag("folder-tree")) { tree() }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                // Liste / çöp / ayarlar arasında yumuşak geçiş.
                AnimatedContent(targetState=if(showSettings) 2 else if(showTrash) 1 else 0,
                    transitionSpec={ fadeIn(tween(SOFT_IN)) togetherWith fadeOut(tween(SOFT_OUT)) },label="library-page") { page ->
                if(page==2) SettingsView(state,onBack={showSettings=false},onPickFolder={pickFolder.launch(SuggestedFolder)})
                else if(page==1) TrashView(state,onBack={showTrash=false},onDelete={deletingKey=it}) else Column(Modifier.fillMaxSize()) {
                    AnimatedContent(targetState=selecting,transitionSpec={ fadeIn(tween(SOFT_IN)) togetherWith fadeOut(tween(SOFT_OUT)) },label="library-header") { selectMode ->
                    Row(Modifier.fillMaxWidth().height(84.dp).padding(start=12.dp,end=20.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(selectMode) {
                            // Seçim çubuğu mevcut başlık satırında; ayrı alt çubuk yok.
                            IconButton(onClick={selectedKeys=emptyList()},modifier=Modifier.testTag("selection-close")) { Icon(Icons.Outlined.Close,"Seçimi kapat",tint=Ada.Ink) }
                            Text("$shownCount not seçili",Modifier.weight(1f).padding(start=8.dp).testTag("selection-count"),
                                fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=Ada.Ink,maxLines=1)
                            val all=visible.map { it.key }
                            TextButton(onClick={ selectedKeys=if(selectedKeys.containsAll(all)) emptyList() else all },modifier=Modifier.testTag("selection-all")) {
                                Text(if(selectedKeys.containsAll(all)) "Seçimi kaldır" else "Tümünü seç") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick={movingKeys=selectedKeys},modifier=Modifier.height(48.dp).testTag("selection-move")) {
                                Icon(Icons.AutoMirrored.Outlined.DriveFileMove,null); Spacer(Modifier.width(8.dp)); Text("Taşı") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick={trashingKeys=selectedKeys},modifier=Modifier.height(48.dp).testTag("selection-trash"),
                                colors=ButtonDefaults.buttonColors(containerColor=Ada.Danger)) {
                                Icon(Icons.Outlined.Delete,null); Spacer(Modifier.width(8.dp)); Text("Çöpe at") }
                        } else if(searching) {
                            IconButton(onClick={searching=false; query=""}) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Aramayı kapat",tint=Ada.Ink) }
                            OutlinedTextField(value=query,onValueChange={query=it},singleLine=true,placeholder={Text("Notlarda ara")},
                                modifier=Modifier.weight(1f).testTag("search-field"),shape=RoundedCornerShape(24.dp),
                                colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Ada.Surface,focusedContainerColor=Ada.Surface))
                        } else {
                            if(current!=null) IconButton(onClick={folder=upOf(current)}) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Üst klasör",tint=Ada.Ink) }
                            else if(!wide) IconButton(onClick={scope.launch { drawer.open() }},modifier=Modifier.testTag("drawer")) { Icon(Icons.Outlined.Menu,"Klasör ağacı",tint=Ada.Ink) }
                            Column(Modifier.weight(1f).padding(start=if(current==null && wide) 12.dp else 8.dp)) {
                                Text(when(current) { null->"Tüm notlar"; ""->"Klasörler"; else->nameOf(current) },
                                    fontSize=28.sp,fontWeight=FontWeight.SemiBold,color=Ada.Ink,maxLines=1,overflow=TextOverflow.Ellipsis,
                                    modifier=Modifier.testTag("library-title"))
                                Text(listOfNotNull(if(current!=null && current.contains('/')) parentOf(current).replace("/"," › ") else null,
                                        "${inFolder.size} not",state.syncMessage).joinToString(" · "),
                                    style=MaterialTheme.typography.bodyMedium,color=Ada.Muted,maxLines=1,overflow=TextOverflow.Ellipsis,
                                    modifier=Modifier.testTag("sync-status"))
                            }
                            IconButton(onClick={searching=true}) { Icon(Icons.Outlined.Search,"Ara",tint=Ada.Ink) }
                            IconButton(onClick={compact=!compact; displayPrefs.edit().putBoolean("compact",compact).apply()},
                                modifier=Modifier.testTag("library-view-toggle")) {
                                Icon(if(compact) Icons.Outlined.GridView else Icons.Outlined.ViewList,
                                    if(compact) "Kart görünümüne geç" else "Sık liste görünümüne geç",tint=Ada.Ink)
                            }
                            Box {
                                IconButton(onClick={menu=true}) { Icon(Icons.Outlined.MoreHoriz,"Diğer seçenekler",tint=Ada.Ink) }
                                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                                    DropdownMenuItem(text={Text("Yeni klasör")},onClick={menu=false; newFolder=true},
                                        leadingIcon={Icon(Icons.Outlined.CreateNewFolder,null)},modifier=Modifier.testTag("menu-new-folder"))
                                    if(!current.isNullOrEmpty()) DropdownMenuItem(text={Text("Bu klasörü sil",color=Ada.Danger)},onClick={menu=false; deletingFolder=current},
                                        leadingIcon={Icon(Icons.Outlined.FolderDelete,null,tint=Ada.Danger)},modifier=Modifier.testTag("menu-delete-folder"))
                                    DropdownMenuItem(text={Text(if(sortByName) "Tarihe göre sırala" else "Ada göre sırala")},onClick={menu=false; sortByName=!sortByName},
                                        leadingIcon={Icon(Icons.AutoMirrored.Outlined.Sort,null)})
                                    DropdownMenuItem(text={Text("Ayarlar")},onClick={menu=false; showSettings=true},
                                        leadingIcon={Icon(Icons.Outlined.Settings,null)},modifier=Modifier.testTag("menu-settings"))
                                    DropdownMenuItem(text={Text("Notları yenile")},onClick={menu=false; state.refresh(); state.sync()},
                                        leadingIcon={Icon(Icons.Outlined.Refresh,null)})
                                    DropdownMenuItem(text={Text(if(state.source==null) "Klasörü bağla" else "Kaynak klasörü değiştir")},
                                        onClick={menu=false; pickFolder.launch(SuggestedFolder)},leadingIcon={Icon(Icons.Outlined.FolderSpecial,null)})
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick={ openNew(current ?: "") },modifier=Modifier.height(48.dp).testTag("new-note"),
                                contentPadding=PaddingValues(start=16.dp,end=22.dp)) {
                                Icon(Icons.Outlined.Add,null); Spacer(Modifier.width(8.dp)); Text("Yeni not",fontWeight=FontWeight.SemiBold)
                            }
                        }
                    }
                    }
                    if(state.syncing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal=24.dp).height(2.dp),color=Ada.Petrol,trackColor=Color.Transparent)
                    when {
                        state.loading && state.notes.isEmpty() -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                CircularProgressIndicator(color=Ada.Accent); Text("Notlar açılıyor…",color=Ada.Muted)
                            }
                        }
                        state.error!=null -> Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                Text(state.error!!); Button(onClick={state.refresh()}){Text("Yeniden dene")}
                            }
                        }
                        else -> LazyVerticalGrid(if(compact) GridCells.Fixed(1) else GridCells.Adaptive(212.dp),Modifier.fillMaxSize(),
                            contentPadding=PaddingValues(start=24.dp,end=24.dp,top=4.dp,bottom=48.dp),
                            horizontalArrangement=Arrangement.spacedBy(22.dp),verticalArrangement=Arrangement.spacedBy(if(compact) 4.dp else 22.dp)) {
                            val full:(androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope)->GridItemSpan={ GridItemSpan(it.maxLineSpan) }
                            if(query.isBlank()) item(key="actions",span=full) {
                                Row(Modifier.animateItem(SOFT_FADE_IN,SOFT_MOVE,SOFT_FADE_OUT),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    ActionPill(Icons.Outlined.PictureAsPdf,"PDF içe aktar",!state.importing,Modifier.testTag("import-pdf")) {
                                        state.clearImportError(); pickPdf.launch(arrayOf("application/pdf")) }
                                    ActionPill(Icons.Outlined.Image,"Resimden not",!state.importing) { pickImages.launch(arrayOf("image/jpeg","image/png","image/webp")) }
                                }
                            }
                            if(state.source!=null && !state.writable && current==null && query.isBlank()) item(key="relink",span=full) {
                                LinkCard(title="Kaynak klasöre yazma izni yok",
                                    body="Taşıma, yeni klasör ve çöp işlemlerinin GVNnotes klasörüne de yansıması için klasörü bir kez yeniden bağla.",
                                    action="Yeniden bağla") { pickFolder.launch(SuggestedFolder) }
                            }
                            if(state.source==null && current==null && query.isBlank()) item(key="link",span=full) {
                                LinkCard { pickFolder.launch(SuggestedFolder) }
                            }
                            // Geniş ekranda ağaç zaten görünür; aynı klasörleri ikinci kez büyük kartlarla tekrarlama.
                            if(query.isBlank() && childFolders.isNotEmpty() && (!wide || current=="")) items(childFolders.size,key={ "folder:"+childFolders[it] },span={full(this)}) { index ->
                                val p=childFolders[index]
                                FolderTile(nameOf(p),countUnder(p),allFolders.count { parentOf(it)==p },Modifier.animateItem(SOFT_FADE_IN,SOFT_MOVE,SOFT_FADE_OUT),onDelete={deletingFolder=p}) { folder=p }
                            }
                            if(visible.isEmpty() && (childFolders.isEmpty() || query.isNotBlank() || current==null)) item(key="empty",span=full) {
                                Box(Modifier.fillMaxWidth().padding(top=64.dp),contentAlignment=Alignment.Center) {
                                    Text(when {
                                        query.isNotBlank() -> "Eşleşen not yok"
                                        state.notes.isEmpty() -> "İlk notunu oluştur ya da bir PDF içe aktar."
                                        else -> "Bu klasörde not yok"
                                    },color=Ada.Muted)
                                }
                            }
                            items(visible.size,key={ visible[it].key }) { i ->
                                val note=visible[i]
                                val picked=note.key in selectedKeys
                                val tap={ if(selecting) toggle(note.key) else state.open(note.key) }
                                val hold={ toggle(note.key) }
                                val motion=Modifier.animateItem(SOFT_FADE_IN,SOFT_MOVE,SOFT_FADE_OUT)
                                if(compact) CompactNoteRow(note,state.sourceStates[note.key],showFolder=current==null || query.isNotBlank(),
                                    selecting,picked,motion,hold,onRename={renamingKey=note.key},onMove={movingKey=note.key},onTrash={state.moveToTrash(note.key)},onClick=tap)
                                else NoteTile(note,state.sourceStates[note.key],showFolder=current==null || query.isNotBlank(),
                                    selecting,picked,motion,hold,onRename={renamingKey=note.key},onMove={movingKey=note.key},onTrash={state.moveToTrash(note.key)},onClick=tap)
                            }
                        }
                    }
                }
                }
                androidx.compose.animation.AnimatedVisibility(state.importing,Modifier.align(Alignment.BottomCenter),enter=fadeIn(tween(SOFT_IN))+slideInVertically(tween(SOFT_IN)) { it/3 },
                    exit=fadeOut(tween(SOFT_OUT))) { Surface(Modifier.padding(bottom=32.dp),shape=RoundedCornerShape(24.dp),shadowElevation=4.dp,color=Ada.Surface) {
                    Row(Modifier.padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=Ada.Accent); Text("İçe aktarılıyor…",color=Ada.Ink)
                    }
                } }
                // Bildirim yumuşak girer/çıkar; çıkış sırasında son metin görünür kalır.
                var lastMessage by remember { mutableStateOf("") }
                state.actionMessage?.let { lastMessage=it }
                androidx.compose.animation.AnimatedVisibility(state.actionMessage!=null,Modifier.align(Alignment.BottomCenter),
                    enter=fadeIn(tween(SOFT_IN))+slideInVertically(tween(SOFT_IN)) { it/3 },exit=fadeOut(tween(SOFT_OUT))) {
                    Surface(Modifier.padding(bottom=36.dp),shape=RoundedCornerShape(24.dp),shadowElevation=4.dp,color=Ada.Ink) {
                        Text(lastMessage,color=Color.White,modifier=Modifier.padding(horizontal=22.dp,vertical=14.dp).testTag("action-message"))
                    }
                }
            }
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
    (movingKeys ?: movingKey?.let { listOf(it) })?.let { keys ->
        val notes=state.notes.filter { it.key in keys }
        val close={ movingKey=null; movingKeys=null }
        if(notes.isEmpty()) close() else AlertDialog(onDismissRequest=close,
            title={Text(if(notes.size==1) "\"${notes[0].title}\" nereye taşınsın?" else "${notes.size} not nereye taşınsın?")},
            text={
                Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
                    (listOf("") + allFolders).forEach { p ->
                        val here=notes.all { it.folderPath==p }
                        Row(Modifier.fillMaxWidth().clip(Ada.RowShape).clickable(enabled=!here) {
                                // Tek not eski yoldan (aynı bildirim); çoklu seçim tek toplu işlemle.
                                if(movingKeys==null) state.moveNote(notes[0].key,p) else { state.moveNotes(notes.map { it.key },p); selectedKeys=emptyList() }
                                close() }
                            .padding(start=(8+(if(p.isEmpty()) 0 else p.count { it=='/' }*20)).dp,top=12.dp,bottom=12.dp,end=8.dp).testTag("move-to-${if(p.isEmpty()) "root" else p}"),
                            verticalAlignment=Alignment.CenterVertically) {
                            Icon(if(p.isEmpty()) Icons.Outlined.Home else Icons.Outlined.Folder,null,tint=if(here) Ada.Muted else Ada.Petrol)
                            Text(if(p.isEmpty()) "Klasör dışı (kök)" else nameOf(p),Modifier.padding(start=12.dp).weight(1f),color=if(here) Ada.Muted else Ada.Ink)
                            if(here) Text("şu an burada",style=MaterialTheme.typography.bodySmall,color=Ada.Muted)
                        }
                    }
                }
            },
            confirmButton={},dismissButton={TextButton(onClick=close){Text("Vazgeç")}})
    }
    trashingKeys?.let { keys ->
        AlertDialog(onDismissRequest={trashingKeys=null},icon={Icon(Icons.Outlined.Delete,null,tint=Ada.Danger)},
            title={Text("${keys.size} not çöp kutusuna taşınsın mı?")},
            text={Text("Notlar $TRASH_DAYS gün çöp kutusunda kalır, o süre içinde geri yükleyebilirsin."+
                if(state.source!=null) " GVNnotes klasöründeki dosyaları .GVNnotes-çöp klasörüne taşınır; hiçbir dosya silinmez." else "")},
            confirmButton={TextButton(onClick={ state.trashNotes(keys); selectedKeys=emptyList(); trashingKeys=null },
                modifier=Modifier.testTag("confirm-selection-trash")){Text("Çöpe at",color=Ada.Danger)}},
            dismissButton={TextButton(onClick={trashingKeys=null}){Text("Vazgeç")}})
    }
    if(newFolder) {
        var name by remember { mutableStateOf("") }
        val parent=folder ?: ""
        AlertDialog(onDismissRequest={newFolder=false},title={Text("Yeni klasör")},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(parent.isEmpty()) "Konum: en üst düzey" else "Konum: ${parent.replace("/"," › ")}",style=MaterialTheme.typography.bodySmall,color=Ada.Muted)
                OutlinedTextField(value=name,onValueChange={ if(it.length<=80) name=it },singleLine=true,label={Text("Klasör adı")},
                    modifier=Modifier.fillMaxWidth().testTag("folder-name"))
            }},
            confirmButton={TextButton(onClick={state.createFolder(parent,name); newFolder=false},
                enabled=name.isNotBlank() && !name.trim().startsWith(".") && name.none { it in "/\\:*?\"<>|" },modifier=Modifier.testTag("folder-create")){Text("Oluştur")}},
            dismissButton={TextButton(onClick={newFolder=false}){Text("Vazgeç")}})
    }
    deletingFolder?.let { p ->
        val notes=countUnder(p); val subs=allFolders.count { it.startsWith("$p/") }
        AlertDialog(onDismissRequest={deletingFolder=null},icon={Icon(Icons.Outlined.FolderDelete,null,tint=Ada.Danger)},
            title={Text("\"${nameOf(p)}\" silinsin mi?")},
            text={Text(buildString {
                append("Klasör")
                if(subs>0) append(" ve $subs alt klasörü")
                append(" silinir. ")
                append(if(notes>0) "İçindeki $notes not çöp kutusuna taşınır; $TRASH_DAYS gün sonra kalıcı silinir, o zamana kadar geri yükleyebilirsin." else "İçinde not yok.")
                if(state.source!=null) append(" GVNnotes klasöründeki dosyalar .GVNnotes-çöp klasörüne taşınır; hiçbir dosya silinmez.")
            })},
            confirmButton={TextButton(onClick={
                state.deleteFolder(p)
                if(folder!=null && (folder==p || folder!!.startsWith("$p/"))) folder=upOf(p)
                deletingFolder=null
            },modifier=Modifier.testTag("confirm-delete-folder")){Text("Klasörü sil",color=Ada.Danger)}},
            dismissButton={TextButton(onClick={deletingFolder=null}){Text("Vazgeç")}})
    }
    deletingKey?.let { key ->
        val note=state.trash.firstOrNull { it.key==key }
        AlertDialog(onDismissRequest={deletingKey=null},title={Text("Kalıcı olarak silinsin mi?")},
            text={Text("\"${note?.title ?: "Not"}\" ve üzerindeki tüm yazılar kalıcı olarak silinir. Bu işlem geri alınamaz.")},
            confirmButton={TextButton(onClick={state.deleteForever(key); deletingKey=null},modifier=Modifier.testTag("confirm-delete-forever")){Text("Kalıcı sil",color=Ada.Danger)}},
            dismissButton={TextButton(onClick={deletingKey=null}){Text("Vazgeç")}})
    }
    state.importError?.let { message ->
        AlertDialog(onDismissRequest={state.clearImportError()},title={Text("İçe aktarılamadı")},text={Text(message)},
            confirmButton={TextButton(onClick={state.clearImportError()}){Text("Tamam")}})
    }
    if(showNew) NewNoteDialog(state,allFolders,title,{title=it},pattern,{pattern=it},newIn,{newIn=it},onDismiss={ if(!state.creating) showNew=false }) {
        state.create(title,pattern,newIn) { showNew=false }
    }
}

/** Klasör ağacı adası: Tüm notlar, iç içe klasörler (daraltılabilir), Çöp kutusu, eşitleme kartı ve Ayarlar. */
@Composable private fun FolderTree(state:LibraryState,allFolders:List<String>,collapsed:MutableList<String>,current:String?,
    showTrash:Boolean,showSettings:Boolean,countUnder:(String)->Int,onSelect:(String?)->Unit,onTrash:()->Unit,onSettings:()->Unit,
    onNewFolder:()->Unit,onLink:()->Unit) {
    val home=!showTrash && !showSettings
    Column(Modifier.fillMaxSize().padding(vertical=12.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start=22.dp,end=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("GVNnotes",fontSize=22.sp,fontWeight=FontWeight.Bold,color=Ada.Ink,modifier=Modifier.weight(1f))
            IconButton(onClick=onNewFolder,modifier=Modifier.size(44.dp).background(Ada.Soft,CircleShape).testTag("tree-new-folder")) {
                Icon(Icons.Outlined.CreateNewFolder,"Yeni klasör",tint=Ada.Accent)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top=8.dp)) {
            TreeRow(Icons.Outlined.GridView,"Tüm notlar",state.notes.size,home && current==null,0,Ada.Accent) { onSelect(null) }
            TreeRow(Icons.Outlined.Folder,"Klasörler",state.notes.count { it.folderPath.isEmpty() },home && current=="",0) { onSelect("") }
            allFolders.filter { p -> generateSequence(parentOf(p)) { parentOf(it) }.takeWhile { it.isNotEmpty() }.none { it in collapsed } }.forEach { p ->
                val hasChildren=allFolders.any { parentOf(it)==p }
                TreeRow(if(current==p && home) Icons.Outlined.FolderOpen else Icons.Outlined.Folder,nameOf(p),countUnder(p),home && current==p,p.count { it=='/' }+1,
                    tag="tree-folder-${nameOf(p)}",expanded=if(hasChildren) p !in collapsed else null,onToggle={ if(p in collapsed) collapsed.remove(p) else collapsed.add(p) }) { onSelect(p) }
            }
            HorizontalDivider(Modifier.padding(horizontal=24.dp,vertical=8.dp),color=Ada.Line)
            TreeRow(Icons.Outlined.Delete,"Çöp kutusu",state.trash.size,showTrash,0,Ada.Muted,onClick=onTrash)
        }
        // Eşitleme kartı: bağlı değilse bağlar, bağlıysa şimdi eşitler.
        Row(Modifier.padding(horizontal=14.dp,vertical=8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ada.Desk)
            .clickable { if(state.source==null) onLink() else state.sync() }.padding(horizontal=14.dp,vertical=12.dp).testTag("sync-card"),
            verticalAlignment=Alignment.CenterVertically) {
            if(state.syncing) CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp,color=Ada.Petrol)
            else Icon(if(state.source==null) Icons.Outlined.FolderSpecial else Icons.Outlined.Sync,null,tint=Ada.Petrol)
            Column(Modifier.padding(start=12.dp)) {
                Text(if(state.source==null) "Kaynak klasör bağlı değil" else sourceLabel(state.source),fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=Ada.Ink)
                Text(state.syncMessage ?: if(state.source==null) "Bağlamak için dokun" else "Eşitlemek için dokun",
                    fontSize=12.sp,color=Ada.Muted,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
        TreeRow(Icons.Outlined.Settings,"Ayarlar",null,showSettings,0,Ada.Muted,tag="tree-settings",onClick=onSettings)
    }
}

@Composable private fun TreeRow(icon:ImageVector,label:String,count:Int?,selected:Boolean,depth:Int,tint:Color=Ada.Petrol,
    expanded:Boolean?=null,onToggle:()->Unit={},tag:String?=null,onClick:()->Unit) {
    val back by androidx.compose.animation.animateColorAsState(if(selected) Ada.Soft else Ada.Soft.copy(alpha=0f),tween(SOFT_IN),label="tree-row")
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp).heightIn(min=48.dp).clip(Ada.RowShape)
        .background(back).clickable(onClick=onClick)
        .semantics { contentDescription=if(count==null) label else "$label, $count not"; this.selected=selected }
        .then(if(tag!=null) Modifier.testTag(tag) else Modifier)
        .padding(start=(4+depth*12).dp,end=14.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).then(if(expanded!=null) Modifier.clip(CircleShape).clickable(onClick=onToggle) else Modifier),contentAlignment=Alignment.Center) {
            if(expanded!=null) Icon(if(expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                if(expanded) "$label daralt" else "$label genişlet",tint=Ada.Muted,modifier=Modifier.size(20.dp))
        }
        Icon(icon,null,tint=if(selected) Ada.Accent else tint,modifier=Modifier.size(22.dp))
        Text(label,Modifier.weight(1f).padding(start=12.dp),fontSize=15.sp,color=Ada.Ink,maxLines=1,overflow=TextOverflow.Ellipsis,
            fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal)
        if(count!=null) Text("$count",color=Ada.Muted,fontSize=12.sp)
    }
}

@Composable private fun ActionPill(icon:ImageVector,label:String,enabled:Boolean,modifier:Modifier=Modifier,onClick:()->Unit) {
    Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(24.dp),color=Ada.Surface,shadowElevation=2.dp,modifier=modifier.height(48.dp)) {
        Row(Modifier.padding(start=16.dp,end=20.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,tint=if(enabled) Ada.Ink else Ada.Disabled,modifier=Modifier.size(22.dp))
            Text(label,Modifier.padding(start=10.dp),color=if(enabled) Ada.Ink else Ada.Disabled,fontWeight=FontWeight.Medium,fontSize=15.sp)
        }
    }
}

@Composable private fun FolderTile(name:String,count:Int,subfolders:Int,modifier:Modifier=Modifier,onDelete:()->Unit,onClick:()->Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(12.dp),color=Ada.Surface,
        modifier=modifier.fillMaxWidth().semantics { contentDescription="Klasör, $name, $count not" }.testTag("folder-$name")) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(Ada.Desk,RoundedCornerShape(8.dp)),contentAlignment=Alignment.Center) {
                Icon(Icons.Outlined.Folder,null,tint=Ada.Petrol)
            }
            Column(Modifier.padding(start=12.dp).weight(1f)) {
                Text(name,color=Ada.Ink,fontWeight=FontWeight.SemiBold,fontSize=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(listOfNotNull("$count not",if(subfolders>0) "$subfolders klasör" else null).joinToString(" · "),color=Ada.Muted,fontSize=12.sp)
            }
            Box {
                var open by remember { mutableStateOf(false) }
                IconButton(onClick={open=true},modifier=Modifier.testTag("folder-menu-$name")) { Icon(Icons.Outlined.MoreHoriz,"$name seçenekleri",tint=Ada.Muted) }
                DropdownMenu(expanded=open,onDismissRequest={open=false}) {
                    DropdownMenuItem(text={Text("Klasörü sil",color=Ada.Danger)},onClick={open=false; onDelete()},
                        leadingIcon={Icon(Icons.Outlined.FolderDelete,null,tint=Ada.Danger)},modifier=Modifier.testTag("menu-delete-folder-$name"))
                }
            }
        }
    }
}

@Composable private fun LinkCard(title:String="GVNnotes klasörünü bağla",
    body:String="Bu klasöre koyduğun PDF ve resimler alt klasörleriyle birlikte otomatik not olur.",action:String="Bağla",onLink:()->Unit) {
    Island(Modifier.fillMaxWidth(),elevation=2.dp) {
        Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(Ada.Desk,RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center) {
                Icon(Icons.Outlined.FolderSpecial,null,tint=Ada.Petrol,modifier=Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal=16.dp)) {
                Text(title,fontWeight=FontWeight.SemiBold,color=Ada.Ink)
                Text(body,color=Ada.Muted,style=MaterialTheme.typography.bodyMedium)
            }
            Button(onClick=onLink,modifier=Modifier.testTag("link-folder")) { Text(action) }
        }
    }
}

/** Kütüphane geçişleri: kısa, yaylanmasız, tekrarlamayan (kullanıcı isteği "her şey çok sert", 14 Kasım 2026). */
private const val SOFT_IN=200
private const val SOFT_OUT=150
private val SOFT_FADE_IN=tween<Float>(SOFT_IN)
private val SOFT_FADE_OUT=tween<Float>(SOFT_OUT)
private val SOFT_MOVE=tween<androidx.compose.ui.unit.IntOffset>(220)

private val dayFormat=SimpleDateFormat("d MMM",Locale("tr"))
private val timeFormat=SimpleDateFormat("HH:mm",Locale("tr"))
internal fun shortDate(time:Long)=if(DateUtils.isToday(time)) timeFormat.format(time) else dayFormat.format(time)
internal fun metaOf(note:NoteCard)=if(note.unreadable) "Kayıt açılamadı · açıp yeniden deneyin"
    else listOfNotNull(shortDate(note.modified),"${note.pageCount} sayfa",if(note.isPdf) "PDF" else null).joinToString(" · ")

/** Sık görünüm: tek satırda başlık ve bilgi; önizleme için bütün satırı büyütmez. */
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun CompactNoteRow(note:NoteCard,source:SourceState?,showFolder:Boolean,
    selecting:Boolean,picked:Boolean,modifier:Modifier,onLongClick:()->Unit,
    onRename:()->Unit,onMove:()->Unit,onTrash:()->Unit,onClick:()->Unit) {
    val back by androidx.compose.animation.animateColorAsState(if(picked) Ada.Soft else Ada.Surface,tween(SOFT_IN),label="note-row")
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(back)
        .combinedClickable(onClick=onClick,onLongClick=onLongClick,onLongClickLabel="Seç")
        .semantics { selected=picked }.testTag("note-${note.key}").padding(start=14.dp,end=4.dp,top=6.dp,bottom=6.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Crossfade(selecting,animationSpec=tween(SOFT_IN),label="note-lead") { mode ->
            if(mode) Icon(if(picked) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,null,
                tint=if(picked) Ada.Accent else Ada.Muted,modifier=Modifier.size(28.dp))
            else Icon(if(note.isPdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.Description,null,
                tint=Ada.Petrol,modifier=Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(note.title,color=Ada.Ink,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(listOfNotNull(metaOf(note),note.folderPath.takeIf { showFolder && it.isNotEmpty() }?.replace("/"," › ")).joinToString(" · "),
                style=MaterialTheme.typography.bodySmall,color=Ada.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
            SourceWarning(source)
        }
        // Seçim sırasında tek not menüsü gizlenir; yeri korunur ki satır zıplamasın.
        Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(!selecting,enter=fadeIn(tween(SOFT_IN)),exit=fadeOut(tween(SOFT_OUT))) { NoteMenu(note,onRename,onMove,onTrash) }
        }
    }
}

private data class PreviewState(val bitmap:Bitmap?,val loaded:Boolean)
@Composable private fun rememberPreview(note:NoteCard):PreviewState {
    val preview by produceState(PreviewState(null,false),note.key,note.revision,note.preview?.path) {
        value=PreviewState(null,false)
        value=PreviewState(withContext(Dispatchers.IO) { note.preview?.let { BitmapFactory.decodeFile(it.path) } },true)
    }
    return preview
}

@Composable private fun SourceWarning(source:SourceState?) {
    when(source) {
        SourceState.MISSING -> Text("Kaynak PDF klasörde bulunamadı · yazılar korunuyor",style=MaterialTheme.typography.bodySmall,color=Ada.Danger)
        SourceState.REPLACED -> Text("Kaynak PDF değişti · yeni sürüm ayrı not",style=MaterialTheme.typography.bodySmall,color=Color(0xff8a5a00))
        else -> {}
    }
}

@Composable private fun NoteMenu(note:NoteCard,onRename:(()->Unit)?,onMove:(()->Unit)?,onTrash:(()->Unit)?) {
    Box {
        var open by remember { mutableStateOf(false) }
        IconButton(onClick={open=true},modifier=Modifier.testTag("note-menu-${note.title}")) { Icon(Icons.Outlined.MoreHoriz,"${note.title} seçenekleri",tint=Ada.Muted) }
        DropdownMenu(expanded=open,onDismissRequest={open=false}) {
            onRename?.let { DropdownMenuItem(text={Text("Yeniden adlandır")},onClick={open=false; it()},leadingIcon={Icon(Icons.Outlined.Edit,null)}) }
            onMove?.let { DropdownMenuItem(text={Text("Klasöre taşı")},onClick={open=false; it()},leadingIcon={Icon(Icons.Outlined.DriveFileMove,null)},
                modifier=Modifier.testTag("menu-move")) }
            onTrash?.let { DropdownMenuItem(text={Text("Çöp kutusuna taşı")},onClick={open=false; it()},leadingIcon={Icon(Icons.Outlined.Delete,null)},
                modifier=Modifier.testTag("menu-trash")) }
        }
    }
}

/** Not kartı: yüzen önizleme, başlık, tarih/sayfa ve (Tüm notlar'da) klasör etiketi. */
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun NoteTile(note:NoteCard,source:SourceState?,showFolder:Boolean,selecting:Boolean,picked:Boolean,modifier:Modifier,onLongClick:()->Unit,
    onRename:()->Unit,onMove:()->Unit,onTrash:()->Unit,onClick:()->Unit) {
    val preview=rememberPreview(note)
    val ring by androidx.compose.animation.animateColorAsState(if(picked) Ada.Accent else Ada.Accent.copy(alpha=0f),tween(SOFT_IN),label="tile-ring")
    Column(modifier.clip(RoundedCornerShape(20.dp)).combinedClickable(onClick=onClick,onLongClick=onLongClick,onLongClickLabel="Seç")
        .semantics { selected=picked }.testTag("note-${note.key}")) {
        Island(Modifier.fillMaxWidth().aspectRatio(1.3f).border(3.dp,ring,RoundedCornerShape(22.dp)),shape=RoundedCornerShape(22.dp),elevation=3.dp) {
            Box(Modifier.padding(10.dp),contentAlignment=Alignment.Center) {
                preview.bitmap?.let { Image(it.asImageBitmap(),contentDescription=null,modifier=Modifier.fillMaxSize().testTag("preview-${note.key}"),contentScale=ContentScale.Fit) }
                    ?: Icon(Icons.Outlined.Description,null,tint=Ada.Line,modifier=Modifier.size(40.dp))
                androidx.compose.animation.AnimatedVisibility(selecting,Modifier.align(Alignment.TopEnd),enter=fadeIn(tween(SOFT_IN)),exit=fadeOut(tween(SOFT_OUT))) {
                    Icon(if(picked) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,null,
                        tint=if(picked) Ada.Accent else Ada.Muted,modifier=Modifier.size(28.dp))
                }
            }
        }
        Row(Modifier.padding(start=4.dp,top=8.dp),verticalAlignment=Alignment.Top) {
            Column(Modifier.weight(1f).padding(top=4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(note.title,fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=Ada.Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(metaOf(note),fontSize=13.sp,color=Ada.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(showFolder && note.folderPath.isNotEmpty()) Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Folder,null,tint=Ada.Petrol,modifier=Modifier.size(14.dp))
                    Text(note.folderPath.replace("/"," › "),Modifier.padding(start=5.dp),fontSize=12.sp,color=Ada.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                SourceWarning(source)
            }
            Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(!selecting,enter=fadeIn(tween(SOFT_IN)),exit=fadeOut(tween(SOFT_OUT))) { NoteMenu(note,onRename,onMove,onTrash) }
            }
        }
    }
}

/** Liste satırı (çöp kutusu): küçük önizleme, başlık, bilgi ve sağda eylemler. */
@Composable private fun NoteRow(note:NoteCard,extra:String?=null,trailing:@Composable ()->Unit) {
    val preview=rememberPreview(note)
    Island(Modifier.fillMaxWidth().testTag("note-${note.key}"),shape=RoundedCornerShape(20.dp),elevation=2.dp) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).border(1.dp,Ada.Line,RoundedCornerShape(14.dp)).background(Color.White,RoundedCornerShape(14.dp)).padding(5.dp),
                contentAlignment=Alignment.Center) {
                preview.bitmap?.let { Image(it.asImageBitmap(),contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit) }
                    ?: Icon(Icons.Outlined.Description,null,tint=Ada.Line)
            }
            Column(Modifier.padding(start=16.dp).weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(note.title,fontSize=17.sp,fontWeight=FontWeight.Medium,color=Ada.Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(listOfNotNull(metaOf(note),note.folderPath.ifEmpty { null }?.replace("/"," › ")).joinToString(" · "),
                    style=MaterialTheme.typography.bodyMedium,color=Ada.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(extra!=null) Text(extra,fontSize=12.sp,color=Ada.Danger)
            }
            trailing()
        }
    }
}

@Composable private fun ScreenHeader(title:String,subtitle:String?,onBack:()->Unit) {
    Row(Modifier.fillMaxWidth().height(84.dp).padding(start=12.dp,end=20.dp),verticalAlignment=Alignment.CenterVertically) {
        IconButton(onClick=onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Notlara dön",tint=Ada.Ink) }
        Column(Modifier.weight(1f).padding(start=8.dp)) {
            Text(title,fontSize=28.sp,fontWeight=FontWeight.SemiBold,color=Ada.Ink)
            if(subtitle!=null) Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=Ada.Muted)
        }
    }
}

/** Çöp kutusu: notlar burada silinene kadar saklanır; geri yükleme notu eski klasörüne döndürür. */
@Composable private fun TrashView(state:LibraryState,onBack:()->Unit,onDelete:(String)->Unit) {
    Column(Modifier.fillMaxSize().testTag("trash")) {
        ScreenHeader("Çöp kutusu","${state.trash.size} not · $TRASH_DAYS gün sonra kalıcı silinir",onBack)
        if(state.trash.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text("Çöp kutusu boş",color=Ada.Muted) }
        else LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=24.dp,end=24.dp,top=4.dp,bottom=48.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(state.trash,key={it.key}) { note ->
                val left=(TRASH_DAYS-((System.currentTimeMillis()-note.modified)/(24L*60*60*1000)).toInt()).coerceAtLeast(0)
                NoteRow(note,if(left<=1) "Yarın kalıcı silinecek" else "$left gün sonra kalıcı silinecek") {
                    TextButton(onClick={state.restore(note.key)},modifier=Modifier.testTag("restore-${note.title}")) { Text("Geri yükle") }
                    TextButton(onClick={onDelete(note.key)},modifier=Modifier.testTag("delete-${note.title}")) { Text("Kalıcı sil",color=Ada.Danger) }
                }
            }
        }
    }
}

private enum class SettingsPage(val label:String,val icon:ImageVector) {
    WRITING("Yazma",Icons.Outlined.Gesture), PEN("Kalem (GVNpen)",Icons.Outlined.Draw), NEW_NOTE("Yeni not",Icons.AutoMirrored.Outlined.NoteAdd),
    SOURCE("Kaynak klasör",Icons.Outlined.FolderSpecial), ABOUT("Hakkında",Icons.Outlined.Info)
}

/** Ayarlar: solda seçili bölümün kartları, sağda yüzen kategori adası. */
@Composable private fun SettingsView(state:LibraryState,onBack:()->Unit,onPickFolder:()->Unit) {
    val context=LocalContext.current
    var page by rememberSaveable { mutableStateOf(SettingsPage.WRITING) }
    var confirmUnlink by remember { mutableStateOf(false) }
    val version=remember { runCatching { context.packageManager.getPackageInfo(context.packageName,0).versionName }.getOrNull() ?: "?" }
    val crash=remember { CrashLog.file(context.filesDir) }
    var crashExists by remember { mutableStateOf(crash.isFile && crash.length()>0) }
    Column(Modifier.fillMaxSize().testTag("settings")) {
        ScreenHeader("Ayarlar","Çalışma biçimini kendine göre düzenle",onBack)
        Row(Modifier.fillMaxSize().padding(start=24.dp,end=16.dp,bottom=16.dp),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(page.label,fontSize=30.sp,fontWeight=FontWeight.SemiBold,color=Ada.Ink)
                when(page) {
                    SettingsPage.WRITING -> {
                        Text("S Pen'in ve parmağın sayfada ne yapacağını seç.",color=Ada.Muted)
                        // GVNpen "Parmakla çizimi engelle" ayarının tersi; tek kaynak GVNpen ayarlarıdır.
                        var fingerDraw by remember { mutableStateOf(!com.gvn.notes.pen.Prefs.load(context).onlyPen) }
                        fun setFinger(on:Boolean) { fingerDraw=on; com.gvn.notes.pen.Prefs.load(context).also { it.onlyPen=!on; it.save(context) } }
                        SettingCard("Parmakla çizim","Açıkken tek parmak da yazar; iki parmak her zaman kaydırır ve yakınlaştırır. Kapalıyken yalnız S Pen yazar (önerilen).",
                            Modifier.clickable { setFinger(!fingerDraw) }.testTag("setting-finger")) { Switch(checked=fingerDraw,onCheckedChange={ setFinger(it) }) }
                        Text("Kalem, renk, kalınlık, silgi, akıllı şekil ve S Pen düğmesi ayarları \"Kalem (GVNpen)\" bölümünde.",
                            style=MaterialTheme.typography.bodySmall,color=Ada.Muted)
                    }
                    SettingsPage.PEN -> {
                        Text("Yazma tarafı GVNpen ile aynıdır; ayarları da GVNpen'in kendi ekranındadır.",color=Ada.Muted)
                        SettingCard("GVNpen ayarları","Çizim, Silgi, Görünüm, Kalem düğmesi ve Dosyalar. Notun içindeki araç şeridinin dişli düğmesi de aynı ekranı açar.") {
                            Button(onClick={ context.startActivity(Intent(context,com.gvn.notes.pen.PenSettingsActivity::class.java)) }) { Text("Aç") }
                        }
                    }
                    SettingsPage.NEW_NOTE -> {
                        Text("Yeni notların ilk sayfası.",color=Ada.Muted)
                        Island(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),elevation=1.dp) {
                            Row(Modifier.padding(20.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                                PagePattern.entries.forEach { p ->
                                    PatternCard(p,state.settings.defaultPattern==p.code,Modifier.weight(1f)) {
                                        state.updateSettings(state.settings.copy(defaultPattern=p.code)) }
                                }
                            }
                        }
                    }
                    SettingsPage.SOURCE -> {
                        Text("Bu klasöre koyduğun PDF ve resimler, alt klasörleriyle birlikte otomatik not olur.",color=Ada.Muted)
                        SettingCard(if(state.source==null) "Bağlı değil" else sourceLabel(state.source),when {
                            state.source==null -> "Klasörü bağlarsan oradaki PDF ve resimler otomatik not olur."
                            state.writable -> "Okuma ve yazma izni var. Taşıma, yeni klasör ve çöp klasöre de yansır."
                            else -> "Yalnız okuma izni var. Klasöre yansıyan işlemler için yeniden bağla."
                        }+(state.syncMessage?.let { "\nSon eşitleme: $it" } ?: "")) {}
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button(onClick=onPickFolder) { Text(if(state.source==null) "Klasörü bağla" else if(state.writable) "Klasörü değiştir" else "Yeniden bağla") }
                            if(state.source!=null) {
                                OutlinedButton(onClick={state.sync()},enabled=!state.syncing) { Text("Şimdi eşitle") }
                                TextButton(onClick={confirmUnlink=true}) { Text("Bağlantıyı kaldır",color=Ada.Danger) }
                            }
                        }
                    }
                    SettingsPage.ABOUT -> {
                        SettingCard("GVNnotes $version","Notlar yalnız bu tablette, uygulamanın kendi alanında saklanır; internet kullanılmaz. Uygulamayı kaldırmak notları siler: önemli notları PDF olarak dışa aktar.") {}
                        SettingCard("Hata kaydı",if(crashExists) "Kaydedilmiş çökme var. Paylaşıp bana gönderebilirsin." else "Kaydedilmiş çökme yok.") {
                            Row {
                                TextButton(onClick={
                                    val text=runCatching { crash.readText() }.getOrDefault("").ifEmpty { "Hata kaydı boş." }
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
                                        .setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,"GVNnotes $version hata kaydı")
                                        .putExtra(Intent.EXTRA_TEXT,text),"Hata kaydını paylaş"))
                                },enabled=crashExists) { Text("Paylaş") }
                                TextButton(onClick={ crash.delete(); crashExists=false },enabled=crashExists) { Text("Temizle") }
                            }
                        }
                    }
                }
            }
            Island(Modifier.width(272.dp).fillMaxHeight()) {
                Column(Modifier.padding(vertical=14.dp)) {
                    SettingsPage.entries.forEach { p ->
                        TreeRow(p.icon,p.label,null,page==p,0,Ada.Muted,tag="settings-${p.name.lowercase()}") { page=p }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("GVNnotes $version\nAyarlar otomatik kaydedilir.",fontSize=12.sp,color=Ada.Muted,modifier=Modifier.padding(horizontal=24.dp,vertical=8.dp))
                }
            }
        }
    }
    if(confirmUnlink) AlertDialog(onDismissRequest={confirmUnlink=false},title={Text("Bağlantı kaldırılsın mı?")},
        text={Text("Notlar ve yazılar kalır; klasördeki dosyalara dokunulmaz. Klasör yeniden bağlanana kadar eşitleme yapılmaz.")},
        confirmButton={TextButton(onClick={state.unlink(); confirmUnlink=false}){Text("Kaldır")}},
        dismissButton={TextButton(onClick={confirmUnlink=false}){Text("Vazgeç")}})
}

@Composable private fun SettingCard(title:String,body:String,modifier:Modifier=Modifier,trailing:@Composable ()->Unit) {
    Island(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),elevation=1.dp) {
        Row(modifier.padding(horizontal=24.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end=16.dp)) {
                Text(title,fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=Ada.Ink)
                Text(body,fontSize=13.sp,color=Ada.Muted)
            }
            trailing()
        }
    }
}

@Composable private fun PatternCard(p:PagePattern,selected:Boolean,modifier:Modifier=Modifier,onClick:()->Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick=onClick).semantics { this.selected=selected }.testTag("pattern-${p.code}")) {
        Box(Modifier.fillMaxWidth().aspectRatio(.72f).border(if(selected) 3.dp else 1.dp,if(selected) Ada.Accent else Ada.Line,RoundedCornerShape(18.dp))
            .background(Ada.Surface,RoundedCornerShape(18.dp)).padding(10.dp)) {
            PatternPreview(p.code,Modifier.fillMaxSize())
        }
        Row(Modifier.fillMaxWidth().padding(top=8.dp,start=2.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(if(p==PagePattern.NONE) "Boş" else p.label,Modifier.weight(1f),fontWeight=FontWeight.SemiBold,color=Ada.Ink)
            if(selected) Icon(Icons.Outlined.Check,null,tint=Ada.Accent,modifier=Modifier.size(20.dp))
        }
    }
}

/** Yeni not: ad, klasör ağacından konum ve dört kâğıt şablonu. */
@Composable private fun NewNoteDialog(state:LibraryState,allFolders:List<String>,title:String,onTitle:(String)->Unit,pattern:Int,onPattern:(Int)->Unit,
    location:String,onLocation:(String)->Unit,onDismiss:()->Unit,onCreate:()->Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Island(Modifier.fillMaxWidth(.9f).widthIn(max=980.dp),elevation=12.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(32.dp)) {
                Text("Bir sayfayla başla.",fontSize=32.sp,fontWeight=FontWeight.SemiBold,color=Ada.Ink)
                Text("Adını yaz, yerini ve kâğıdını seç.",color=Ada.Muted,fontSize=16.sp)
                Row(Modifier.padding(top=24.dp),horizontalArrangement=Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.width(320.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Not adı",fontWeight=FontWeight.SemiBold,color=Ada.Ink,fontSize=14.sp)
                        OutlinedTextField(value=title,onValueChange={ if(it.length<=100) onTitle(it) },singleLine=true,enabled=!state.creating,
                            shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().testTag("note-title"))
                        Text("Konum",fontWeight=FontWeight.SemiBold,color=Ada.Ink,fontSize=14.sp,modifier=Modifier.padding(top=12.dp))
                        Column(Modifier.fillMaxWidth().heightIn(max=280.dp).clip(RoundedCornerShape(20.dp)).background(Ada.Desk)
                            .verticalScroll(rememberScrollState()).padding(vertical=6.dp)) {
                            (listOf("")+allFolders).forEach { p ->
                                val selected=p==location
                                Row(Modifier.fillMaxWidth().padding(horizontal=6.dp).heightIn(min=48.dp).clip(Ada.RowShape)
                                    .background(if(selected) Ada.Soft else Color.Transparent).clickable { onLocation(p) }
                                    .semantics { this.selected=selected }.testTag("location-${p.ifEmpty { "root" }}")
                                    .padding(start=(14+(if(p.isEmpty()) 0 else (p.count { it=='/' }+1)*18)).dp,end=12.dp),
                                    verticalAlignment=Alignment.CenterVertically) {
                                    Icon(if(p.isEmpty()) Icons.Outlined.Home else Icons.Outlined.Folder,null,tint=if(selected) Ada.Accent else Ada.Petrol,modifier=Modifier.size(22.dp))
                                    Text(if(p.isEmpty()) "Klasör dışı" else nameOf(p),Modifier.weight(1f).padding(start=12.dp),color=Ada.Ink,
                                        fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    if(selected) Icon(Icons.Outlined.Check,null,tint=Ada.Accent,modifier=Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Sayfa şablonu",fontWeight=FontWeight.SemiBold,color=Ada.Ink,fontSize=18.sp,modifier=Modifier.padding(bottom=12.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            PagePattern.entries.forEach { p -> PatternCard(p,pattern==p.code,Modifier.weight(1f)) { onPattern(p.code) } }
                        }
                        Text("Yeni notlar otomatik kaydedilir. Sayfa şablonunu sonradan sayfa sıralayıcıdan değiştirebilirsin.",
                            fontSize=13.sp,color=Ada.Muted,modifier=Modifier.padding(top=16.dp))
                    }
                }
                state.creationError?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=12.dp)) }
                Row(Modifier.fillMaxWidth().padding(top=24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.End)) {
                    OutlinedButton(onClick=onDismiss,enabled=!state.creating,modifier=Modifier.height(48.dp)) { Text("Vazgeç") }
                    Button(onClick=onCreate,enabled=title.isNotBlank() && !state.creating,modifier=Modifier.height(48.dp).testTag("create-note")) {
                        Icon(Icons.Outlined.Add,null); Spacer(Modifier.width(8.dp)); Text(if(state.creating) "Oluşturuluyor…" else "Notu oluştur",fontWeight=FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
