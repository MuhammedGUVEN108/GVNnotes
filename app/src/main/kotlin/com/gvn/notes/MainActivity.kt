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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.File
import com.gvn.notes.core.Tool

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
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
        setContent { MaterialTheme(colorScheme=lightColorScheme(
            primary=Color(0xff186c84),secondary=Color(0xff4fc4dd),surface=Color(0xfffafafa),
            surfaceContainerLow=Color(0xfff1f3f4)
        )) {
            if(testDocument) EditorScreen(editor("test"))
            else {
                val selected=libraryState.selectedKey
                if(selected==null) LibraryScreen(libraryState)
                else key(selected) { EditorDestination(editor(selected)) { libraryState.close(); stores.clear(selected) } }
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

@Composable private fun EditorDestination(editor:EditorState,onClose:()->Unit) {
    var closing by rememberSaveable { mutableStateOf(false) }
    BackHandler { closing=true }
    LaunchedEffect(closing) { editor.navigationPending=closing }
    DisposableEffect(editor) { onDispose { editor.navigationPending=false } }
    LaunchedEffect(closing,editor.busy,editor.ready,editor.savedVersion,editor.editVersion,editor.problem) {
        if(closing && !editor.busy &&
            ((editor.ready && editor.savedVersion==editor.editVersion) || (!editor.ready && editor.problem!=null))) onClose()
    }
    EditorScreen(editor,onBack={closing=true},closing=closing)
    if(closing && editor.ready && editor.problem!=null) {
        AlertDialog(onDismissRequest={closing=false},title={Text("Not henüz kaydedilemedi")},
            text={Text("Değişiklikleri korumak için kaydı yeniden deneyin veya notta kalın.")},
            confirmButton={TextButton(onClick={editor.retry()}){Text("Tekrar dene")}},
            dismissButton={TextButton(onClick={closing=false}){Text("Notta kal")}})
    }
}


/** Samsung Notes düzenini referans alan, GVNnotes'a ait renk ve simgelerle editör. */
private val Ink=Color(0xff1f2328)
private val Muted=Color(0xff5f6368)
private val Accent=Color(0xff186c84)
private val AccentSoft=Color(0xffdcecf1)
private val Divider=Color(0xffe3e5e8)
private val Palette=listOf(0xff18222e to "Siyah",0xff1f6fd1 to "Mavi",0xffd33f49 to "Kırmızı",0xff2e9e5b to "Yeşil",0xff8a4fd8 to "Mor")
private val Thickness=listOf(1.5f to "İnce",3f to "Orta",6f to "Kalın")

@Composable private fun EditorScreen(editor:EditorState,onBack:(()->Unit)?=null,closing:Boolean=false) {
    val revision=editor.revision
    val canEdit=editor.ready && !editor.busy && !closing
    var pageView by remember { mutableStateOf<InkPageView?>(null) }
    var sorter by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val view=LocalView.current
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
    Column(Modifier.fillMaxSize().testTag("editor").background(Color(0xfffafafa))
        .then(if(fullscreen) Modifier else Modifier.safeDrawingPadding())) {
        if(!fullscreen) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                if(onBack!=null) IconButton(onClick=onBack,enabled=!closing,modifier=Modifier.testTag("back-to-notes")) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Notlara dön",tint=Ink)
                }
                Text(if(revision>=0) editor.note.title else "",style=MaterialTheme.typography.titleLarge,color=Ink,maxLines=1,
                    overflow=TextOverflow.Ellipsis,
                    modifier=Modifier.weight(1f).padding(start=8.dp).clickable(enabled=canEdit,onClickLabel="Başlığı değiştir") { renaming=true })
                TopAction(Icons.Outlined.ViewSidebar,"Sayfa sıralayıcı",sorter,editor.ready) { sorter=!sorter }
                TopAction(Icons.Outlined.Add,"Bu sayfanın arkasına boş sayfa ekle",false,canEdit) {
                    val added=editor.insertBlankPageAfter(editor.currentPage)
                    if(added>=0) pageView?.let { v -> v.post { v.refresh(); v.scrollToPage(added) } }
                }
                TopAction(Icons.Outlined.Fullscreen,"Tam ekran",false,editor.ready) { fullscreen=true }
                Box {
                    TopAction(Icons.Outlined.MoreVert,"Diğer seçenekler",false,true) { menu=true }
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
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
            ToolStrip(editor,canEdit)
            if(sorter && !fullscreen) PageSorter(editor,revision,canEdit) { i -> pageView?.scrollToPage(i) }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AndroidView(factory={ InkPageView(it,editor).also { v -> pageView=v } },modifier=Modifier.fillMaxSize(),
                    update={ if(revision>=0) it.refresh() })
                // Tuval üstü göstergeler: sağ üstte yakınlaştırma, sağ altta sayfa sayacı.
                Row(Modifier.align(Alignment.TopEnd).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(fullscreen) Pill {
                        IconButton(onClick={fullscreen=false},modifier=Modifier.size(40.dp)) { Icon(Icons.Outlined.FullscreenExit,"Tam ekrandan çık",tint=Ink) }
                    }
                    Pill {
                        Text("${(editor.viewport.scale*100).toInt()}%",color=Ink,style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(start=14.dp))
                        IconButton(onClick={ pageView?.fit() },enabled=canEdit,modifier=Modifier.size(40.dp)) { Icon(Icons.Outlined.FitScreen,"Ekrana sığdır",tint=Ink) }
                    }
                }
                Text("${editor.currentPage+1}/${editor.pages.size}",color=Muted,style=MaterialTheme.typography.labelLarge,
                    modifier=Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("page-indicator")
                        .semantics { contentDescription="Sayfa ${editor.currentPage+1} / ${editor.pages.size}" })
                val status=if(closing) "Notlara dönülüyor…" else if(editor.busy) "Çiziliyor…" else editor.saveStatus
                Text(status,color=Muted,style=MaterialTheme.typography.bodySmall,modifier=Modifier.align(Alignment.BottomStart).padding(16.dp))
                // Kement seçimi araç çubuğu.
                if(editor.selection.isNotEmpty()) Surface(Modifier.align(Alignment.TopCenter).padding(top=12.dp).testTag("selection-bar"),
                    shape=RoundedCornerShape(24.dp),color=Color.White,shadowElevation=3.dp) {
                    Row(Modifier.padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("${editor.selection.size} çizgi",color=Ink,style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(horizontal=10.dp))
                        TextButton(onClick={editor.deleteSelection()},enabled=canEdit,modifier=Modifier.testTag("selection-delete")) { Icon(Icons.Outlined.Delete,null); Spacer(Modifier.width(6.dp)); Text("Sil") }
                        TextButton(onClick={editor.duplicateSelection()},enabled=canEdit) { Icon(Icons.Outlined.ContentCopy,null); Spacer(Modifier.width(6.dp)); Text("Çoğalt") }
                        TextButton(onClick={editor.recolorSelection(editor.color)},enabled=canEdit) {
                            Box(Modifier.size(18.dp).background(Color(editor.color),CircleShape)); Spacer(Modifier.width(6.dp)); Text("Rengi uygula") }
                        IconButton(onClick={editor.clearSelection(); pageView?.refresh()}) { Icon(Icons.Outlined.Close,"Seçimi kaldır",tint=Ink) }
                    }
                }
                (editor.exporting ?: editor.exportResult)?.let { message ->
                    Surface(Modifier.align(Alignment.BottomCenter).padding(bottom=16.dp),shape=RoundedCornerShape(24.dp),
                        color=Color.White,shadowElevation=3.dp) {
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
        }
    }
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

@Composable private fun Pill(content:@Composable RowScope.()->Unit) {
    Surface(shape=RoundedCornerShape(24.dp),color=Color.White.copy(alpha=.94f),shadowElevation=2.dp) {
        Row(verticalAlignment=Alignment.CenterVertically,content=content)
    }
}

/** GVNpen paletindeki renkler + ek tonlar (renk seçici). */
private val PickerColors=listOf(
    0xff00d9f5,0xff3b82f6,0xffa78bfa,0xffef4444,0xfff59e0b,0xfffacc15,0xff22c55e,0xffff00ff,0xffffffff,0xff000000,
    0xff0f766e,0xff1e3a8a,0xff6d28d9,0xff9f1239,0xffc2410c,0xff854d0e,0xff166534,0xff475569,0xff94a3b8,0xffe11d48,
    0xff0284c7,0xff7c3aed,0xffea580c,0xff65a30d).map { it.toInt() }

/** Dar, açık renkli dikey araç şeridi: araçlar, renkler, kalınlık ön ayarları, geri/ileri al. Seçili araca tekrar dokunmak ayarlarını açar. */
@Composable private fun ToolStrip(editor:EditorState,canEdit:Boolean) {
    var settings by remember { mutableStateOf<Tool?>(null) }
    var picking by remember { mutableStateOf(false) }
    var sizing by remember { mutableStateOf(false) }
    fun choose(tool:Tool) {
        if(editor.tool==tool) { if(tool!=Tool.LASSO) settings=tool }
        else { editor.tool=tool; editor.clearSelection(); editor.savePen() }
    }
    Column(Modifier.width(68.dp).fillMaxHeight().background(Color.White)
        .verticalScroll(rememberScrollState()).padding(vertical=12.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        ToolButton(Icons.Outlined.Edit,"Kalem",editor.tool==Tool.PEN,canEdit) { choose(Tool.PEN) }
        ToolButton(Icons.Outlined.Highlight,"Fosforlu kalem",editor.tool==Tool.HIGHLIGHTER,canEdit) { choose(Tool.HIGHLIGHTER) }
        ToolButton(EraserIcon,if(editor.eraserWhole) "Silgi (tüm çizgi)" else "Silgi (alan)",editor.tool==Tool.ERASER,canEdit) { choose(Tool.ERASER) }
        ToolButton(Icons.Outlined.Gesture,"Kement seçimi",editor.tool==Tool.LASSO,canEdit) { choose(Tool.LASSO) }
        HorizontalDivider(Modifier.width(36.dp).padding(vertical=6.dp),color=Divider)
        val colors=Palette.map { it.first.toInt() to it.second }+editor.customColors.map { it to "Özel renk" }
        colors.forEach { (value,name) ->
            val selected=editor.color==value
            Box(Modifier.size(44.dp).clickable(enabled=canEdit) { editor.useColor(value) }
                .semantics { contentDescription=name; this.selected=selected },contentAlignment=Alignment.Center) {
                Box(Modifier.size(30.dp)
                    .border(2.dp,if(selected) Color(value).copy(alpha=.4f) else Color.Transparent,CircleShape)
                    .padding(4.dp).background(Color(value),CircleShape).border(if(value==0xffffffff.toInt()) 1.dp else 0.dp,Divider,CircleShape))
            }
        }
        ToolButton(Icons.Outlined.Palette,"Renk seç",false,canEdit) { picking=true }
        HorizontalDivider(Modifier.width(36.dp).padding(vertical=6.dp),color=Divider)
        Thickness.forEach { (size,name) ->
            val selected=editor.size==size
            Box(Modifier.size(44.dp).background(if(selected) AccentSoft else Color.Transparent,CircleShape)
                .clickable(enabled=canEdit) { if(selected) sizing=true else { editor.size=size; editor.savePen() } }
                .semantics { contentDescription="$name kalınlık"; this.selected=selected },contentAlignment=Alignment.Center) {
                Box(Modifier.width(24.dp).height((size*1.3f).dp.coerceAtLeast(2.dp)).background(if(selected) Accent else Ink,RoundedCornerShape(4.dp)))
            }
        }
        if(Thickness.none { it.first==editor.size }) Box(Modifier.size(44.dp).background(AccentSoft,CircleShape).clickable(enabled=canEdit) { sizing=true }
            .semantics { contentDescription="Özel kalınlık ${"%.1f".format(editor.size)}"; selected=true },contentAlignment=Alignment.Center) {
            Text("%.1f".format(editor.size),style=MaterialTheme.typography.labelSmall,color=Accent)
        }
        HorizontalDivider(Modifier.width(36.dp).padding(vertical=6.dp),color=Divider)
        ToolButton(Icons.AutoMirrored.Outlined.Undo,"Geri al",false,canEdit&&editor.history.canUndo) { editor.undo() }
        ToolButton(Icons.AutoMirrored.Outlined.Redo,"İleri al",false,canEdit&&editor.history.canRedo) { editor.redo() }
    }
    Box(Modifier.width(1.dp).fillMaxHeight().background(Divider))

    if(picking) AlertDialog(onDismissRequest={picking=false},title={Text("Renk seç")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                PickerColors.chunked(6).forEach { row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        row.forEach { value ->
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(value)).border(if(editor.color==value) 3.dp else 1.dp,
                                if(editor.color==value) Accent else Divider,CircleShape).clickable { editor.useColor(value); picking=false }
                                .semantics { contentDescription="Renk #%06X".format(value and 0xffffff) })
                        }
                    }
                }
            }
        },confirmButton={TextButton(onClick={picking=false}){Text("Kapat")}})

    if(sizing) {
        var value by remember { mutableFloatStateOf(editor.size) }
        AlertDialog(onDismissRequest={sizing=false},title={Text("Kalem kalınlığı · ${"%.1f".format(value)}")},
            text={
                Column {
                    Slider(value=value,onValueChange={ value=(it*2).roundToInt()/2f },valueRange=.5f..16f)
                    Box(Modifier.fillMaxWidth().height(48.dp),contentAlignment=Alignment.Center) {
                        Box(Modifier.width(180.dp).height((value*1.3f).dp.coerceAtLeast(1.dp)).background(Color(editor.color),RoundedCornerShape(8.dp)))
                    }
                }
            },
            confirmButton={TextButton(onClick={ editor.size=value; editor.savePen(); sizing=false }){Text("Uygula")}},
            dismissButton={TextButton(onClick={sizing=false}){Text("Vazgeç")}})
    }

    settings?.let { tool ->
        AlertDialog(onDismissRequest={settings=null},
            title={Text(when(tool) { Tool.ERASER->"Silgi"; Tool.HIGHLIGHTER->"Fosforlu kalem"; else->"Kalem" })},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(tool==Tool.ERASER) {
                        SettingSwitch("Dokunulan çizgiyi tamamen sil","Kapalıysa yalnız silginin değdiği alan silinir. Yanlışlıkla silersen Geri al.",editor.eraserWhole) { editor.eraserWhole=it }
                        Text("Silgi boyutu · ${editor.eraserSize.roundToInt()}",style=MaterialTheme.typography.labelLarge)
                        Slider(value=editor.eraserSize,onValueChange={ editor.eraserSize=it.roundToInt().toFloat() },valueRange=2f..60f)
                    } else {
                        SettingSwitch("Akıllı şekil","Çizip kalemi ucunda kısa süre beklet: çizgi, üçgen, dikdörtgen veya daire düzgünleşir.",editor.smartShape) { editor.smartShape=it }
                        Text("S Pen yan düğmesi basılıyken kalem geçici olarak silgi olur.",style=MaterialTheme.typography.bodySmall,color=Muted)
                    }
                }
            },
            confirmButton={TextButton(onClick={ editor.savePen(); settings=null }){Text("Tamam")}})
    }
}

@Composable private fun SettingSwitch(title:String,body:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) },verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=12.dp)) {
            Text(title,style=MaterialTheme.typography.titleSmall,color=Ink)
            Text(body,style=MaterialTheme.typography.bodySmall,color=Muted)
        }
        Switch(checked=checked,onCheckedChange=onChange)
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
@Composable private fun PageSorter(editor:EditorState,revision:Int,canEdit:Boolean,onOpen:(Int)->Unit) {
    val strokes=editor.history.items
    var deleting by remember { mutableStateOf<Int?>(null) }
    deleting?.let { i ->
        AlertDialog(onDismissRequest={deleting=null},title={Text("Sayfa ${i+1} silinsin mi?")},
            text={Text("Sayfa ve üzerindeki yazılar silinir. Bu işlem geri alınamaz.")},
            confirmButton={TextButton(onClick={editor.deletePage(i); deleting=null},modifier=Modifier.testTag("confirm-delete-page")){Text("Sil")}},
            dismissButton={TextButton(onClick={deleting=null}){Text("Vazgeç")}})
    }
    Column(Modifier.width(220.dp).fillMaxHeight().background(Color(0xfff4f5f6)).testTag("page-sorter")) {
        Text("${editor.currentPage+1}/${editor.pages.size}",style=MaterialTheme.typography.titleMedium,color=Ink,
            modifier=Modifier.padding(start=20.dp,top=16.dp,bottom=8.dp))
        LazyColumn(contentPadding=PaddingValues(start=24.dp,end=24.dp,bottom=24.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            items(editor.pages.size,key={ editor.pages[it].id }) { i ->
                val page=editor.pages[i]
                val own=strokes.filter { it.data.pageId==page.id }
                val signature="${page.id}:${own.size}:${own.lastOrNull()?.data?.id}:$revision"
                val thumb by produceState<Bitmap?>(null,signature) {
                    value=withContext(Dispatchers.IO) { runCatching { renderPage(editor.directory,page,own,240) }.getOrNull() }
                }
                val current=i==editor.currentPage
                Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().aspectRatio(page.width/page.height)
                        .border(if(current) 2.dp else 1.dp,if(current) Accent else Divider,RoundedCornerShape(4.dp))
                        .background(Color.White).clickable { onOpen(i) }
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
                Box(Modifier.fillMaxWidth().aspectRatio(1f/1.414f).background(Color.White,RoundedCornerShape(4.dp))
                    .border(1.dp,Divider,RoundedCornerShape(4.dp)).clickable(enabled=canEdit) {
                        editor.addBlankPage(); onOpen(editor.pages.lastIndex)
                    },contentAlignment=Alignment.Center) {
                    Icon(Icons.Outlined.Add,"Sona boş sayfa ekle",tint=Muted,modifier=Modifier.size(32.dp))
                }
            }
        }
    }
    Box(Modifier.width(1.dp).fillMaxHeight().background(Divider))
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
