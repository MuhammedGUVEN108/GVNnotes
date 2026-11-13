package com.gvn.notes

import android.os.Bundle
import kotlin.math.roundToInt
import androidx.compose.ui.draw.clip
import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.File
import com.gvn.notes.core.Tool

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
        CrashLog.install(filesDir,runCatching { packageManager.getPackageInfo(packageName,0).versionName }.getOrNull() ?: "?")
        // Cihaz testleri kullanıcı notunu değiştirmeyen ayrı, yalnız debug'da seçilebilen klasör kullanır.
        val debuggable=(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0
        val testDocument=(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0 &&
            intent.getBooleanExtra("gvnnotes.testDocument",false)
        val testLibrary=(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0 &&
            intent.getBooleanExtra("gvnnotes.testLibrary",false)
        val notebooks=File(filesDir,if(testLibrary || testDocument) "test-library/notebooks" else "notebooks")
        val library=NoteLibrary(notebooks)
        val libraryState=ViewModelProvider(this,object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T:ViewModel> create(modelClass:Class<T>):T = LibraryState(library) { uri ->
                // Cihaz testleri SAF izni olmadan düz klasörle eşitleme dener; yalnız debug derlemede.
                if(debuggable && uri.startsWith("file://")) FileSourceTree(File(android.net.Uri.parse(uri).path!!))
                else SafSourceTree(applicationContext,android.net.Uri.parse(uri))
            } as T
        })[LibraryState::class.java]
        // Her açık notun editörü kendi ViewModelStore'unda yaşar; not kapanınca temizlenir.
        // Böylece kapalı notun eski bellek durumu (ör. eşitlemenin taşıdığı notta) diske geri yazılamaz.
        val stores=ViewModelProvider(this)[EditorStores::class.java]
        fun editor(key:String):EditorState = ViewModelProvider(stores.store(key),object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T:ViewModel> create(modelClass:Class<T>):T =
                EditorState(if(testDocument)File(filesDir,"test-document") else library.directory(key)) as T
        })[EditorState::class.java]
        setContent { MaterialTheme(colorScheme=AdaColors) {
            if(testDocument) EditorScreen(editor("test"))
            else {
                val selected=libraryState.selectedKey
                if(selected==null) LibraryScreen(libraryState)
                else key(selected) {
                    // Klasör adası: notun klasörü ve aynı klasördeki diğer notlar.
                    val path=libraryState.notes.firstOrNull { it.key==selected }?.folderPath
                    val siblings=libraryState.notes.filter { it.folderPath==(path ?: "") }
                    EditorDestination(editor(selected),FolderContext(selected,path,siblings)) { after ->
                        libraryState.close(); stores.clear(selected)
                        when(after) {
                            is AfterClose.Open -> libraryState.open(after.key)
                            is AfterClose.Folder -> libraryState.requestedFolder=after.path
                            is AfterClose.NewIn -> libraryState.requestedNewIn=after.path
                            null -> {}
                        }
                    }
                }
            }
        } }
    }
}

/** Açık notların editör durumlarını taşır; yapılandırma değişiminde (ekran dönmesi) korunur. */
class EditorStores:ViewModel() {
    private val stores=HashMap<String,androidx.lifecycle.ViewModelStore>()
    fun store(key:String):androidx.lifecycle.ViewModelStoreOwner {
        val store=stores.getOrPut(key) { androidx.lifecycle.ViewModelStore() }
        return object:androidx.lifecycle.ViewModelStoreOwner { override val viewModelStore=store }
    }
    fun clear(key:String) { stores.remove(key)?.clear() }
    override fun onCleared() { stores.values.forEach { it.clear() }; stores.clear() }
}

/** Düzenleyicinin bulunduğu klasör: başlıktaki konum çipi ve Klasör adası için. [path] null = bilinmiyor. */
class FolderContext(val key:String,val path:String?,val notes:List<NoteCard>)
/** Not kapandıktan (kayıt bittikten) sonra yapılacak gezinme. */
sealed interface AfterClose {
    data class Open(val key:String):AfterClose
    data class Folder(val path:String):AfterClose
    data class NewIn(val path:String):AfterClose
}

@Composable private fun EditorDestination(editor:EditorState,folder:FolderContext,onClose:(AfterClose?)->Unit) {
    var closing by rememberSaveable { mutableStateOf(false) }
    var after by remember { mutableStateOf<AfterClose?>(null) }
    BackHandler { closing=true }
    LaunchedEffect(closing) { editor.navigationPending=closing }
    DisposableEffect(editor) { onDispose { editor.navigationPending=false } }
    LaunchedEffect(closing,editor.busy,editor.ready,editor.savedVersion,editor.editVersion,editor.problem) {
        if(closing && !editor.busy &&
            ((editor.ready && editor.savedVersion==editor.editVersion) || (!editor.ready && editor.problem!=null))) onClose(after)
    }
    EditorScreen(editor,onBack={after=null; closing=true},closing=closing,folder=folder,onNavigate={ after=it; closing=true })
    if(closing && editor.ready && editor.problem!=null) {
        AlertDialog(onDismissRequest={closing=false},title={Text("Not henüz kaydedilemedi")},
            text={Text("Değişiklikleri korumak için kaydı yeniden deneyin veya notta kalın.")},
            confirmButton={TextButton(onClick={editor.retry()}){Text("Tekrar dene")}},
            dismissButton={TextButton(onClick={closing=false; after=null}){Text("Notta kal")}})
    }
}


/** "Petrol Ada" düzenleyici çerçevesi; yazma yüzeyi ve araç şeridi GVNpen ile aynıdır. */
private val Ink=Ada.Ink
private val Muted=Ada.Muted
private val Accent=Ada.Accent
private val AccentSoft=Ada.Soft
private val Divider=Ada.Line

@Composable private fun EditorScreen(editor:EditorState,onBack:(()->Unit)?=null,closing:Boolean=false,
    folder:FolderContext?=null,onNavigate:(AfterClose)->Unit={}) {
    val revision=editor.revision
    val canEdit=editor.ready && !editor.busy && !closing
    var pageView by remember { mutableStateOf<InkPageView?>(null) }
    var toolbar by remember { mutableStateOf<com.gvn.notes.pen.PenToolbar?>(null) }
    // GVNpen ayarları ekranından dönünce yeni ayarlar şeride ve yazma yüzeyine uygulanır.
    val penSettings=androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        pageView?.let { v -> v.reloadPrefs(); toolbar?.setPrefs(v.prefs) }
    }
    var sorter by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var folderIsland by remember { mutableStateOf(false) }
    val view=LocalView.current
    fun openPenSettings() { penSettings.launch(android.content.Intent(view.context,com.gvn.notes.pen.PenSettingsActivity::class.java)) }
    val context=androidx.compose.ui.platform.LocalContext.current
    // Dışa aktarma hedefi kullanıcının seçtiği yerdir (sistem "Kaydet" penceresi).
    val exportTo=androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if(uri!=null) editor.export { checkNotNull(context.contentResolver.openOutputStream(uri,"wt")) { "Hedef açılamadı." } }
    }
    LaunchedEffect(fullscreen) {
        val window=(view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller=WindowCompat.getInsetsController(window,view)
        if(fullscreen) {
            controller.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else controller.show(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(Unit) { onDispose {
        (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it,view).show(WindowInsetsCompat.Type.systemBars()) }
    } }
    Column(Modifier.fillMaxSize().testTag("editor").background(Ada.Surface)
        .then(if(fullscreen) Modifier else Modifier.safeDrawingPadding())) {
        run {
            Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                if(onBack!=null) IconButton(onClick=onBack,enabled=!closing,modifier=Modifier.testTag("back-to-notes")) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Notlara dön",tint=Ink)
                }
                Column(Modifier.weight(1f).padding(start=8.dp)) {
                    Text(if(revision>=0) editor.note.title else "",style=MaterialTheme.typography.titleMedium,color=Ink,maxLines=1,
                        overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold,
                        modifier=Modifier.clickable(enabled=canEdit,onClickLabel="Başlığı değiştir") { renaming=true })
                    Text("Sayfa ${editor.currentPage+1} / ${editor.pages.size} · ${(editor.viewport.scale*100).toInt()}%",
                        color=Muted,style=MaterialTheme.typography.bodySmall,maxLines=1,
                        modifier=Modifier.testTag("page-indicator")
                            .semantics { contentDescription="Sayfa ${editor.currentPage+1} / ${editor.pages.size}" })
                }
                if(folder?.path!=null) FolderChip(folder.path,folderIsland,enabled=!closing) { folderIsland=!folderIsland }
                TopAction(Icons.Outlined.FitScreen,"Ekrana sığdır",false,canEdit) { pageView?.fit() }
                TopAction(if(fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                    if(fullscreen) "Tam ekrandan çık" else "Tam ekran",fullscreen,editor.ready) { fullscreen=!fullscreen }
                TopAction(Icons.Outlined.Gesture,"Kement seçimi",editor.lassoMode,editor.ready) {
                    editor.lassoMode=!editor.lassoMode; editor.clearSelection(); pageView?.refresh() }
                TopAction(Icons.Outlined.ViewSidebar,"Sayfa sıralayıcı",sorter,editor.ready) { sorter=!sorter }
                TopAction(Icons.Outlined.Add,"Bu sayfanın arkasına boş sayfa ekle",false,canEdit) {
                    val added=editor.insertBlankPageAfter(editor.currentPage)
                    if(added>=0) pageView?.let { v -> v.post { v.refresh(); v.scrollToPage(added) } }
                }
                Box {
                    TopAction(Icons.Outlined.MoreHoriz,"Diğer seçenekler",false,true) { menu=true }
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                        DropdownMenuItem(text={Text(if(fullscreen) "Tam ekrandan çık" else "Tam ekran")},enabled=editor.ready,onClick={menu=false; fullscreen=!fullscreen},leadingIcon={Icon(Icons.Outlined.Fullscreen,null)})
                        DropdownMenuItem(text={Text("Başlığı değiştir")},enabled=canEdit,onClick={menu=false; renaming=true})
                        DropdownMenuItem(text={Text("PDF olarak dışa aktar")},enabled=editor.ready&&editor.exporting==null,
                            onClick={menu=false; exportTo.launch("${editor.note.title}.pdf")},leadingIcon={Icon(Icons.Outlined.IosShare,null)})
                        DropdownMenuItem(text={Text("Fare ile çizim (deneme)")},onClick={editor.mouseInput=!editor.mouseInput; menu=false},
                            trailingIcon={Checkbox(checked=editor.mouseInput,onCheckedChange=null)})
                    }
                }
            }
            HorizontalDivider(color=Divider)
        }
        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                // Yazma yüzeyi + üstünde kayan GVNpen araç şeridi.
                AndroidView(factory={ ctx ->
                    val ink=InkPageView(ctx,editor).also { v -> pageView=v }
                    val bar=com.gvn.notes.pen.PenToolbar(ctx,object:com.gvn.notes.pen.PenToolbar.Host {
                        override fun selectTool(tool:String) { editor.lassoMode=false; editor.clearSelection(); ink.drawing=true; ink.savePrefs() }
                        override fun setDrawing(drawing:Boolean) { ink.drawing=drawing }
                        override fun drawing()=ink.drawing
                        override fun temporary()=false
                        override fun undo() { ink.cancel(); editor.undo(); ink.refresh() }
                        override fun redo() { ink.cancel(); editor.redo(); ink.refresh() }
                        override fun canUndo()=editor.history.canUndo
                        override fun canRedo()=editor.history.canRedo
                        override fun clear() { ink.clearCurrentPage() }
                        override fun openSettings() { openPenSettings() }
                        override fun close() { onBack?.invoke() }
                        override fun prefsChanged() { ink.savePrefs(); ink.refresh() }
                    },ink.prefs)
                    toolbar=bar
                    ink.onStateChanged={ bar.refreshSelection(); bar.refreshHistory() }
                    android.widget.FrameLayout(ctx).apply { addView(ink,-1,-1); addView(bar,-1,-1) }
                },modifier=Modifier.fillMaxSize().testTag("writing-area"),
                    update={ if(revision>=0) { pageView?.refresh(); toolbar?.refreshHistory() } })
                // Kement seçimi araç çubuğu.
                if(editor.selection.isNotEmpty()) Surface(Modifier.align(Alignment.TopCenter).padding(top=14.dp).testTag("selection-bar"),
                    shape=RoundedCornerShape(24.dp),color=Ada.Surface,shadowElevation=6.dp) {
                    Row(Modifier.padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("${editor.selection.size} çizgi",color=Ink,style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(horizontal=10.dp))
                        TextButton(onClick={editor.deleteSelection()},enabled=canEdit,modifier=Modifier.testTag("selection-delete")) { Icon(Icons.Outlined.Delete,null); Spacer(Modifier.width(6.dp)); Text("Sil") }
                        TextButton(onClick={editor.duplicateSelection()},enabled=canEdit) { Icon(Icons.Outlined.ContentCopy,null); Spacer(Modifier.width(6.dp)); Text("Çoğalt") }
                        TextButton(onClick={ pageView?.let { editor.recolorSelection(it.prefs.color) } },enabled=canEdit) {
                            Box(Modifier.size(18.dp).background(Color(pageView?.prefs?.color ?: 0xff000000.toInt()),CircleShape)); Spacer(Modifier.width(6.dp)); Text("Rengi uygula") }
                        IconButton(onClick={editor.clearSelection(); pageView?.refresh()}) { Icon(Icons.Outlined.Close,"Seçimi kaldır",tint=Ink) }
                    }
                }
                (editor.exporting ?: editor.exportResult)?.let { message ->
                    Surface(Modifier.align(Alignment.BottomCenter).padding(bottom=16.dp),shape=RoundedCornerShape(24.dp),
                        color=Ada.Surface,shadowElevation=6.dp) {
                        Row(Modifier.padding(start=18.dp,end=6.dp),verticalAlignment=Alignment.CenterVertically) {
                            if(editor.exporting!=null) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=Accent)
                            Text(message,color=Ink,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(horizontal=10.dp,vertical=12.dp).testTag("export-status"))
                            if(editor.exporting==null) TextButton(onClick={editor.exportResult=null}) { Text("Tamam") }
                        }
                    }
                }
                editor.problem?.let { message ->
                    Surface(Modifier.align(Alignment.TopCenter).padding(top=12.dp),shape=RoundedCornerShape(24.dp),
                        color=MaterialTheme.colorScheme.errorContainer,shadowElevation=2.dp) {
                        Row(Modifier.padding(start=16.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(message,color=MaterialTheme.colorScheme.onErrorContainer,style=MaterialTheme.typography.bodyMedium)
                            TextButton(onClick={editor.retry()}) { Text("Tekrar dene") }
                        }
                    }
                }
            }
            // Yan panel kendi yerini ayırır; PDF ve araç şeridinin üzerine binmez.
            if(sorter && !fullscreen) Island(Modifier.padding(top=12.dp,end=12.dp,bottom=12.dp).width(184.dp).fillMaxHeight()) {
                PageSorter(editor,revision,canEdit,onClose={sorter=false}) { i -> pageView?.scrollToPage(i) }
            }
        }
    }
    if(folderIsland && folder?.path!=null) FolderIsland(folder,onDismiss={folderIsland=false}) { folderIsland=false; onNavigate(it) }
    if(renaming) {
        var title by remember { mutableStateOf(editor.note.title) }
        AlertDialog(onDismissRequest={renaming=false},title={Text("Başlık")},
            text={OutlinedTextField(value=title,onValueChange={ if(it.length<=100) title=it },singleLine=true,modifier=Modifier.fillMaxWidth())},
            confirmButton={TextButton(onClick={editor.rename(title); renaming=false},enabled=title.isNotBlank()){Text("Kaydet")}},
            dismissButton={TextButton(onClick={renaming=false}){Text("Vazgeç")}})
    }
}

@Composable private fun TopAction(icon:ImageVector,label:String,selected:Boolean,enabled:Boolean,onClick:()->Unit) {
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(48.dp).semantics { this.selected=selected }
        .background(if(selected) AccentSoft else Color.Transparent,CircleShape)) {
        Icon(icon,label,tint=if(!enabled) Color(0xffb0b4b9) else if(selected) Accent else Ink)
    }
}



/** Başlıktaki konum çipi: "Tıp Fakültesi › Fizyoloji". Dokununca Klasör adası açılır. */
@Composable private fun FolderChip(path:String,open:Boolean,enabled:Boolean,onClick:()->Unit) {
    Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(24.dp),color=Ada.Soft,
        border=if(open) BorderStroke(2.dp,Accent) else null,
        modifier=Modifier.padding(start=16.dp).height(44.dp).widthIn(max=200.dp).testTag("folder-chip")
            .semantics { contentDescription="Konum: ${if(path.isEmpty()) "Klasör dışı" else path.replace("/"," › ")}. Klasör adasını aç" }) {
        Row(Modifier.padding(start=14.dp,end=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.Folder,null,tint=Ada.Petrol,modifier=Modifier.size(20.dp))
            Text(if(path.isEmpty()) "Klasör dışı" else path.replace("/","  ›  "),color=Accent,style=MaterialTheme.typography.labelLarge,
                maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(start=10.dp,end=6.dp).weight(1f,fill=false))
            Icon(if(open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null,tint=Accent,modifier=Modifier.size(20.dp))
        }
    }
}

/** Klasör adası: aynı klasördeki notlar arasında geçiş, klasöre dönüş ve o klasörde yeni not. */
@Composable private fun FolderIsland(folder:FolderContext,onDismiss:()->Unit,onNavigate:(AfterClose)->Unit) {
    val path=folder.path ?: return
    Box(Modifier.fillMaxSize().background(Ada.Ink.copy(alpha=.10f)).clickable(interactionSource=null,indication=null,onClick=onDismiss)) {
        Island(Modifier.safeDrawingPadding().padding(start=200.dp,top=72.dp,bottom=24.dp).width(420.dp).heightIn(max=680.dp)
            .clickable(interactionSource=null,indication=null) {}.testTag("folder-island"),elevation=12.dp) {
            Column(Modifier.padding(vertical=16.dp)) {
                if(path.contains('/')) Row(Modifier.padding(start=20.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.SubdirectoryArrowRight,null,tint=Muted,modifier=Modifier.size(16.dp))
                    Text(path.substringBeforeLast('/').replace("/"," › "),color=Muted,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(start=6.dp))
                }
                Row(Modifier.padding(start=20.dp,end=20.dp,top=6.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Folder,null,tint=Ada.Petrol,modifier=Modifier.size(28.dp))
                    Text(if(path.isEmpty()) "Klasör dışı" else path.substringAfterLast('/'),fontSize=24.sp,
                        fontWeight=FontWeight.SemiBold,color=Ink,maxLines=1,overflow=TextOverflow.Ellipsis,
                        modifier=Modifier.weight(1f).padding(start=10.dp))
                    Text("${folder.notes.size} not",color=Muted,style=MaterialTheme.typography.bodySmall)
                }
                LazyColumn(Modifier.weight(1f,fill=false),contentPadding=PaddingValues(horizontal=12.dp)) {
                    items(folder.notes.size,key={ folder.notes[it].key }) { i ->
                        val note=folder.notes[i]; val here=note.key==folder.key
                        val thumb by produceState<Bitmap?>(null,note.key,note.revision) {
                            value=withContext(Dispatchers.IO) { note.preview?.let { android.graphics.BitmapFactory.decodeFile(it.path) } }
                        }
                        Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clip(Ada.RowShape).background(if(here) AccentSoft else Color.Transparent)
                            .clickable(enabled=!here) { onNavigate(AfterClose.Open(note.key)) }.padding(horizontal=12.dp,vertical=8.dp)
                            .semantics { contentDescription=note.title+if(here) ", açık" else "" },verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp,52.dp).border(1.dp,Divider,RoundedCornerShape(6.dp)).background(Color.White,RoundedCornerShape(6.dp)).padding(2.dp)) {
                                thumb?.let { Image(it.asImageBitmap(),null,Modifier.fillMaxSize()) }
                            }
                            Column(Modifier.weight(1f).padding(start=14.dp)) {
                                Text(note.title,color=Ink,maxLines=1,overflow=TextOverflow.Ellipsis,
                                    fontWeight=if(here) FontWeight.SemiBold else FontWeight.Normal)
                                Text(metaOf(note),color=Muted,style=MaterialTheme.typography.bodySmall,maxLines=1)
                            }
                            if(here) Text("Açık",color=Accent,style=MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick={ onNavigate(AfterClose.Folder(path)) },modifier=Modifier.height(48.dp).testTag("go-to-folder")) {
                        Icon(Icons.Outlined.FolderOpen,null,modifier=Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Klasöre git") }
                    Button(onClick={ onNavigate(AfterClose.NewIn(path)) },modifier=Modifier.height(48.dp).testTag("new-in-folder")) {
                        Icon(Icons.Outlined.Add,null,modifier=Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Bu klasörde yeni") }
                }
            }
        }
    }
}


@Composable private fun ToolButton(icon:ImageVector,label:String,selected:Boolean,enabled:Boolean,onClick:()->Unit) {
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(48.dp)
        .semantics { this.selected=selected }
        .background(if(selected) AccentSoft else Color.Transparent,CircleShape)) {
        Icon(icon,label,tint=if(!enabled) Color(0xffb0b4b9) else if(selected) Accent else Ink)
    }
}

/** Sol panel: numaralı sayfa küçük resimleri; dokununca o sayfaya gider, + kutucuğu boş sayfa ekler. */
@Composable private fun PageSorter(editor:EditorState,revision:Int,canEdit:Boolean,onClose:()->Unit,onOpen:(Int)->Unit) {
    val strokes=editor.history.items
    var deleting by remember { mutableStateOf<Int?>(null) }
    deleting?.let { i ->
        AlertDialog(onDismissRequest={deleting=null},title={Text("Sayfa ${i+1} silinsin mi?")},
            text={Text("Sayfa ve üzerindeki yazılar silinir. Bu işlem geri alınamaz.")},
            confirmButton={TextButton(onClick={editor.deletePage(i); deleting=null},modifier=Modifier.testTag("confirm-delete-page")){Text("Sil")}},
            dismissButton={TextButton(onClick={deleting=null}){Text("Vazgeç")}})
    }
    Column(Modifier.fillMaxSize().testTag("page-sorter")) {
        Row(Modifier.fillMaxWidth().padding(start=20.dp,end=6.dp,top=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Sayfalar",style=MaterialTheme.typography.titleSmall,color=Ink,modifier=Modifier.weight(1f))
            Text("${editor.pages.size}",style=MaterialTheme.typography.bodySmall,color=Muted)
            IconButton(onClick=onClose,modifier=Modifier.size(48.dp)) { Icon(Icons.Outlined.Close,"Sayfa sıralayıcıyı kapat",tint=Muted) }
        }
        LazyColumn(contentPadding=PaddingValues(start=28.dp,end=28.dp,bottom=20.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            items(editor.pages.size,key={ editor.pages[it].id }) { i ->
                val page=editor.pages[i]
                val own=remember(strokes,page.id) { strokes.filter { it.data.pageId==page.id } }
                // Başka sayfadaki yeni mürekkep bu küçük resmi yeniden üretmez.
                val thumb by produceState<Bitmap?>(null,page,own) {
                    value=withContext(Dispatchers.IO) { runCatching { renderPage(editor.directory,page,own,240) }.getOrNull() }
                }
                val current=i==editor.currentPage
                Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().aspectRatio(page.width/page.height)
                        .border(if(current) 2.dp else 1.dp,if(current) Accent else Divider,RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp)).background(Color.White).clickable { onOpen(i) }
                        .semantics { contentDescription="Sayfa ${i+1}"; selected=current }) {
                        thumb?.let { Image(it.asImageBitmap(),null,Modifier.fillMaxSize().padding(2.dp)) }
                        // Sayfa işlemleri: taşı / sil.
                        Box(Modifier.align(Alignment.TopEnd)) {
                            var open by remember { mutableStateOf(false) }
                            IconButton(onClick={open=true},enabled=canEdit,modifier=Modifier.size(36.dp).testTag("page-menu-$i")) {
                                Icon(Icons.Outlined.MoreVert,"Sayfa ${i+1} seçenekleri",tint=Muted)
                            }
                            DropdownMenu(expanded=open,onDismissRequest={open=false}) {
                                DropdownMenuItem(text={Text("Yukarı taşı")},enabled=i>0,onClick={open=false; editor.movePage(i,i-1)},
                                    leadingIcon={Icon(Icons.Outlined.ArrowUpward,null)})
                                DropdownMenuItem(text={Text("Aşağı taşı")},enabled=i<editor.pages.lastIndex,onClick={open=false; editor.movePage(i,i+1)},
                                    leadingIcon={Icon(Icons.Outlined.ArrowDownward,null)})
                                DropdownMenuItem(text={Text("Arkasına boş sayfa ekle")},onClick={open=false; val n=editor.insertBlankPageAfter(i); if(n>=0) onOpen(n)},
                                    leadingIcon={Icon(Icons.Outlined.Add,null)})
                                if(page.background==com.gvn.notes.core.BackgroundType.BLANK) PagePattern.entries.forEach { p ->
                                    DropdownMenuItem(text={Text("Şablon: ${p.label}")},enabled=page.pattern!=p.code,
                                        onClick={open=false; editor.setPattern(i,p.code)},leadingIcon={Icon(Icons.Outlined.GridOn,null)})
                                }
                                DropdownMenuItem(text={Text("Sayfayı sil")},enabled=editor.pages.size>1,onClick={open=false; deleting=i},
                                    leadingIcon={Icon(Icons.Outlined.Delete,null)})
                            }
                        }
                    }
                    Text("${i+1}",style=MaterialTheme.typography.labelMedium,color=if(current) Accent else Muted,modifier=Modifier.padding(top=6.dp))
                }
            }
            item {
                Box(Modifier.fillMaxWidth().aspectRatio(1f/1.414f).background(Ada.Desk,RoundedCornerShape(8.dp))
                    .border(1.dp,Divider,RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).clickable(enabled=canEdit) {
                        editor.addBlankPage(); onOpen(editor.pages.lastIndex)
                    },contentAlignment=Alignment.Center) {
                    Icon(Icons.Outlined.Add,"Sona boş sayfa ekle",tint=Muted,modifier=Modifier.size(32.dp))
                }
            }
        }
    }
}

// Kendi basit silgi geometrimiz; Samsung/GVNpen görsel kaynağı içermez.
private val EraserIcon=ImageVector.Builder("Silgi",24.dp,24.dp,24f,24f).apply {
    path(fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.8f,
        strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round) {
        moveTo(3f,14f); lineTo(13f,4f); lineTo(21f,12f)
        lineTo(13f,20f); lineTo(9f,20f); close()
        moveTo(8f,9f); lineTo(16f,17f)
        moveTo(13f,20f); lineTo(21f,20f)
    }
}.build()
