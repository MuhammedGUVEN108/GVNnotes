package com.gvn.notes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private val LibInk=Color(0xff1f2328)
private val LibMuted=Color(0xff6b7075)
private val LibAccent=Color(0xff186c84)
private val LibBorder=Color(0xffe3e5e8)

/** Not listesi: Samsung Notes'taki liste düzeni referans alınır (küçük resim + başlık + tarih). */
@Composable fun LibraryScreen(state:LibraryState) {
    var showNew by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val context=LocalContext.current
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
    val visible=if(query.isBlank()) state.notes else state.notes.filter { it.title.contains(query.trim(),ignoreCase=true) }
    Box(Modifier.fillMaxSize().background(Color(0xfffafafa)).testTag("library").safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(72.dp).padding(start=24.dp,end=12.dp),verticalAlignment=Alignment.CenterVertically) {
                if(searching) {
                    IconButton(onClick={searching=false; query=""}) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Aramayı kapat",tint=LibInk) }
                    OutlinedTextField(value=query,onValueChange={query=it},singleLine=true,placeholder={Text("Notlarda ara")},
                        modifier=Modifier.weight(1f).testTag("search-field"),shape=RoundedCornerShape(24.dp))
                } else {
                    Column(Modifier.weight(1f)) {
                        Text("Tüm notlar",fontSize=26.sp,fontWeight=FontWeight.SemiBold,color=LibInk)
                        Text("${state.notes.size} not",style=MaterialTheme.typography.bodyMedium,color=LibMuted)
                    }
                    IconButton(onClick={ if(!state.importing) { state.clearImportError(); pickPdf.launch(arrayOf("application/pdf")) } },
                        enabled=!state.importing,modifier=Modifier.testTag("import-pdf")) {
                        Icon(Icons.Outlined.PictureAsPdf,"PDF'yi içe aktar",tint=LibInk)
                    }
                    IconButton(onClick={searching=true}) { Icon(Icons.Outlined.Search,"Ara",tint=LibInk) }
                    Box {
                        IconButton(onClick={menu=true}) { Icon(Icons.Outlined.MoreVert,"Diğer seçenekler",tint=LibInk) }
                        DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                            DropdownMenuItem(text={Text("Notları yenile")},onClick={menu=false; state.refresh()},
                                leadingIcon={Icon(Icons.Outlined.Refresh,null)})
                        }
                    }
                }
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(color=LibAccent); Text("Notlar açılıyor…",color=LibMuted)
                    }
                }
                state.error!=null -> Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text(state.error!!); Button(onClick={state.refresh()}){Text("Yeniden dene")}
                    }
                }
                state.notes.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp),contentAlignment=Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Description,null,Modifier.size(48.dp),tint=LibAccent)
                        Text("İlk notunu oluştur",style=MaterialTheme.typography.headlineSmall)
                        Text("Yeni not aç veya bir PDF içe aktar.",style=MaterialTheme.typography.bodyLarge,color=LibMuted)
                    }
                }
                visible.isEmpty() -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text("Eşleşen not yok",color=LibMuted) }
                else -> LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=24.dp,end=24.dp,top=8.dp,bottom=120.dp)) {
                    items(visible,key={it.key}) { note -> NoteRow(note) { state.open(note.key) } }
                }
            }
        }
        if(state.importing) Surface(Modifier.align(Alignment.BottomCenter).padding(bottom=32.dp),shape=RoundedCornerShape(24.dp),shadowElevation=3.dp,color=Color.White) {
            Row(Modifier.padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=LibAccent); Text("PDF açılıyor…")
            }
        }
        // Sağ altta yuvarlak yeni not düğmesi.
        Surface(onClick={title=""; state.clearCreationError(); showNew=true},shape=CircleShape,color=Color.White,shadowElevation=4.dp,
            modifier=Modifier.align(Alignment.BottomEnd).padding(28.dp).size(72.dp).testTag("new-note")
                .semantics { contentDescription="Yeni not" }) {
            Box(contentAlignment=Alignment.Center) { Icon(Icons.Outlined.EditNote,null,tint=LibAccent,modifier=Modifier.size(34.dp)) }
        }
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

private val dayFormat=SimpleDateFormat("d MMM",Locale("tr"))
private val timeFormat=SimpleDateFormat("HH:mm",Locale("tr"))
private fun shortDate(time:Long)=if(DateUtils.isToday(time)) timeFormat.format(time) else dayFormat.format(time)

private data class PreviewState(val bitmap:Bitmap?,val loaded:Boolean)
@Composable private fun NoteRow(note:NoteCard,onClick:()->Unit) {
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
                else "${shortDate(note.modified)} · ${note.pageCount} sayfa${if(note.isPdf) " · PDF" else ""}",
                style=MaterialTheme.typography.bodyMedium,color=LibMuted)
        }
    }
}
