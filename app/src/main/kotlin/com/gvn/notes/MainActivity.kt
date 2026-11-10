package com.gvn.notes

import android.os.Bundle
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
            primary=Color(0xff186c84),secondary=Color(0xff4fc4dd),surface=Color(0xffeff5f9),
            surfaceContainerLow=Color(0xffe3edf1)
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

@Composable private fun EditorScreen(editor:EditorState,onBack:(()->Unit)?=null,closing:Boolean=false) {
    val revision=editor.revision
    val canEdit=editor.ready && !editor.busy && !closing
    Column(Modifier.fillMaxSize().testTag("editor").background(MaterialTheme.colorScheme.surface).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            if(onBack!=null) IconButton(onClick=onBack,enabled=!closing,modifier=Modifier.testTag("back-to-notes")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Notlara dön")
            }
            Column(Modifier.weight(1f)) {
                Text("GVNnotes",style=MaterialTheme.typography.titleLarge)
                Text(editor.note.title,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            Text("${(editor.viewport.scale*100).toInt()}%",style=MaterialTheme.typography.labelLarge)
        }
        HorizontalDivider()
        Row(Modifier.weight(1f)) {
            Column(Modifier.width(72.dp).fillMaxHeight().background(Color(0xff18222e))
                .verticalScroll(rememberScrollState()).padding(vertical=12.dp),
                horizontalAlignment=Alignment.CenterHorizontally) {
                Text("GVNpen",color=Color.White,style=MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(12.dp))
                RailButton(Icons.Outlined.Edit,"Kalem",editor.tool==Tool.PEN,canEdit) { editor.tool=Tool.PEN }
                RailButton(Icons.Outlined.Highlight,"Fosforlu kalem",editor.tool==Tool.HIGHLIGHTER,canEdit) { editor.tool=Tool.HIGHLIGHTER }
                RailButton(EraserIcon,"Tüm çizgi silgisi",editor.tool==Tool.ERASER,canEdit) { editor.tool=Tool.ERASER }
                HorizontalDivider(Modifier.padding(12.dp),color=Color(0xff394b5c))
                RailButton(Icons.AutoMirrored.Outlined.Undo,"Geri al",false,canEdit&&editor.history.canUndo) { editor.undo() }
                RailButton(Icons.AutoMirrored.Outlined.Redo,"İleri al",false,canEdit&&editor.history.canRedo) { editor.redo() }
                HorizontalDivider(Modifier.padding(12.dp),color=Color(0xff394b5c))
                listOf(0xff18222e,0xff186c84,0xffd33f49,0xffe5a900).forEach { c ->
                    val value=c.toInt()
                    Box(Modifier.size(48.dp)
                        .clickable(enabled=canEdit) { editor.color=value }
                        .semantics { contentDescription=when(value) {
                            0xff18222e.toInt()->"Koyu mürekkep";0xff186c84.toInt()->"Petrol mavisi";
                            0xffd33f49.toInt()->"Kırmızı";else->"Sarı" }; selected=editor.color==value },
                        contentAlignment=Alignment.Center) {
                        Box(Modifier.size(30.dp).background(Color(value),CircleShape)
                            .border(if(editor.color==value)3.dp else 1.dp,Color.White,CircleShape))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            AndroidView(factory={ InkPageView(it,editor) },modifier=Modifier.weight(1f).fillMaxHeight(),
                update={ if(revision>=0) it.refresh() })
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Kalınlık",style=MaterialTheme.typography.labelLarge)
            Slider(value=editor.size,onValueChange={ editor.size=it },valueRange=1f..12f,
                enabled=canEdit,modifier=Modifier.weight(1f).padding(horizontal=12.dp))
            Text("${editor.size.toInt()}",style=MaterialTheme.typography.labelLarge)
            IconToggleButton(checked=editor.mouseInput,onCheckedChange={editor.mouseInput=it},enabled=canEdit) {
                Icon(Icons.Outlined.Mouse,contentDescription="Fare ile çizim denemesi")
            }
        }
        Text("İki parmakla kaydır / yakınlaştır · ${if(closing) "Notlara dönülüyor…" else if(editor.busy) "Çiziliyor…" else editor.saveStatus}",
            modifier=Modifier.padding(start=16.dp,end=16.dp,bottom=8.dp),style=MaterialTheme.typography.bodySmall)
        editor.problem?.let { message ->
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(message,Modifier.weight(1f),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={editor.retry()}) { Text("Tekrar dene") }
            }
        }
    }
}

@Composable private fun RailButton(icon:ImageVector,label:String,selected:Boolean,enabled:Boolean,onClick:()->Unit) {
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(48.dp)
        .semantics { this.selected=selected }
        .background(if(selected)Color(0xff186c84) else Color.Transparent,CircleShape)) {
        Icon(icon,label,tint=if(enabled)Color.White else Color(0xff738393))
    }
    Spacer(Modifier.height(8.dp))
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
