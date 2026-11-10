package com.gvn.notes

import android.os.Bundle
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
        val testDocument=(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0 &&
            intent.getBooleanExtra("gvnnotes.testDocument",false)
        val testLibrary=(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0 &&
            intent.getBooleanExtra("gvnnotes.testLibrary",false)
        val notebooks=File(filesDir,if(testLibrary || testDocument) "test-library/notebooks" else "notebooks")
        val library=NoteLibrary(notebooks)
        val libraryState=ViewModelProvider(this,object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T:ViewModel> create(modelClass:Class<T>):T = LibraryState(library) as T
        })[LibraryState::class.java]
        fun editor(key:String):EditorState = ViewModelProvider(this,object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T:ViewModel> create(modelClass:Class<T>):T =
                EditorState(if(testDocument)File(filesDir,"test-document") else library.directory(key)) as T
        }).get("editor-$key",EditorState::class.java)
        setContent { MaterialTheme(colorScheme=lightColorScheme(
            primary=Color(0xff186c84),secondary=Color(0xff4fc4dd),surface=Color(0xfffafafa),
            surfaceContainerLow=Color(0xfff1f3f4)
        )) {
            if(testDocument) EditorScreen(editor("test"))
            else {
                val selected=libraryState.selectedKey
                if(selected==null) LibraryScreen(libraryState)
                else key(selected) { EditorDestination(editor(selected),libraryState::close) }
            }
        } }
    }
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
                TopAction(Icons.Outlined.Add,"Sona boş sayfa ekle",false,canEdit) {
                    editor.addBlankPage(); pageView?.let { v -> v.post { v.refresh(); v.scrollToPage(editor.pages.lastIndex) } }
                }
                TopAction(Icons.Outlined.Fullscreen,"Tam ekran",false,editor.ready) { fullscreen=true }
                Box {
                    TopAction(Icons.Outlined.MoreVert,"Diğer seçenekler",false,true) { menu=true }
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                        DropdownMenuItem(text={Text("Başlığı değiştir")},enabled=canEdit,onClick={menu=false; renaming=true})
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

/** Dar, açık renkli dikey araç şeridi: araçlar, renkler, kalınlık ön ayarları, geri/ileri al. */
@Composable private fun ToolStrip(editor:EditorState,canEdit:Boolean) {
    Column(Modifier.width(68.dp).fillMaxHeight().background(Color.White)
        .verticalScroll(rememberScrollState()).padding(vertical=12.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        ToolButton(Icons.Outlined.Edit,"Kalem",editor.tool==Tool.PEN,canEdit) { editor.tool=Tool.PEN }
        ToolButton(Icons.Outlined.Highlight,"Fosforlu kalem",editor.tool==Tool.HIGHLIGHTER,canEdit) { editor.tool=Tool.HIGHLIGHTER }
        ToolButton(EraserIcon,"Tüm çizgi silgisi",editor.tool==Tool.ERASER,canEdit) { editor.tool=Tool.ERASER }
        HorizontalDivider(Modifier.width(36.dp).padding(vertical=6.dp),color=Divider)
        Palette.forEach { (c,name) ->
            val value=c.toInt(); val selected=editor.color==value
            Box(Modifier.size(44.dp).clickable(enabled=canEdit) { editor.color=value }
                .semantics { contentDescription=name; this.selected=selected },contentAlignment=Alignment.Center) {
                Box(Modifier.size(30.dp)
                    .border(2.dp,if(selected) Color(value).copy(alpha=.4f) else Color.Transparent,CircleShape)
                    .padding(4.dp).background(Color(value),CircleShape))
            }
        }
        HorizontalDivider(Modifier.width(36.dp).padding(vertical=6.dp),color=Divider)
        Thickness.forEach { (size,name) ->
            val selected=editor.size==size
            Box(Modifier.size(44.dp).background(if(selected) AccentSoft else Color.Transparent,CircleShape)
                .clickable(enabled=canEdit) { editor.size=size }
                .semantics { contentDescription="$name kalınlık"; this.selected=selected },contentAlignment=Alignment.Center) {
                Box(Modifier.width(24.dp).height((size*1.3f).dp.coerceAtLeast(2.dp)).background(if(selected) Accent else Ink,RoundedCornerShape(4.dp)))
            }
        }
        HorizontalDivider(Modifier.width(36.dp).padding(vertical=6.dp),color=Divider)
        ToolButton(Icons.AutoMirrored.Outlined.Undo,"Geri al",false,canEdit&&editor.history.canUndo) { editor.undo() }
        ToolButton(Icons.AutoMirrored.Outlined.Redo,"İleri al",false,canEdit&&editor.history.canRedo) { editor.redo() }
    }
    Box(Modifier.width(1.dp).fillMaxHeight().background(Divider))
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
