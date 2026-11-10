package com.gvn.notes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LibraryScreen(state:LibraryState) {
    var showNew by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
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
    Scaffold(modifier=Modifier.fillMaxSize().testTag("library"),containerColor=MaterialTheme.colorScheme.surface,
        topBar={TopAppBar(title={Column {
            Text("Notlar",style=MaterialTheme.typography.headlineMedium)
            Text("GVNnotes",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }},actions={IconButton(onClick={state.refresh()},enabled=!state.loading) {Icon(Icons.Outlined.Refresh,"Notları yenile")}})},
        floatingActionButton={Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            ExtendedFloatingActionButton(
                onClick={ if(!state.importing) { state.clearImportError(); pickPdf.launch(arrayOf("application/pdf")) } },
                icon={Icon(Icons.Outlined.PictureAsPdf,null)},text={Text(if(state.importing)"PDF açılıyor…" else "PDF'den not")},
                modifier=Modifier.testTag("import-pdf"),
                containerColor=MaterialTheme.colorScheme.surfaceContainerLow,contentColor=MaterialTheme.colorScheme.primary)
            ExtendedFloatingActionButton(
                onClick={title=""; state.clearCreationError(); showNew=true},
                icon={Icon(Icons.Outlined.Add,null)},text={Text("Yeni not")},modifier=Modifier.testTag("new-note"),
                containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary)
        }}
    ) { insets ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(insets),contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(); Text("Notlar açılıyor…")
                }
            }
            state.error!=null -> Box(Modifier.fillMaxSize().padding(insets).padding(24.dp),contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text(state.error!!); Button(onClick={state.refresh()}){Text("Yeniden dene")}
                }
            }
            state.notes.isEmpty() -> Box(Modifier.fillMaxSize().padding(insets).padding(32.dp),contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Description,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
                    Text("İlk notunu oluştur",style=MaterialTheme.typography.headlineSmall)
                    Text("Yeni not açıp S Pen ile yazmaya başla.",style=MaterialTheme.typography.bodyLarge)
                }
            }
            else -> LazyVerticalGrid(columns=GridCells.Adaptive(220.dp),modifier=Modifier.fillMaxSize().padding(insets),
                contentPadding=PaddingValues(start=24.dp,end=24.dp,top=24.dp,bottom=112.dp),
                verticalArrangement=Arrangement.spacedBy(20.dp),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                items(state.notes,key={it.key}) { note -> NoteCardView(note,onClick={state.open(note.key)}) }
            }
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

private data class PreviewState(val bitmap:Bitmap?,val loaded:Boolean)
@Composable private fun NoteCardView(note:NoteCard,onClick:()->Unit) {
    val preview by produceState(PreviewState(null,false),note.key,note.revision,note.preview?.path) {
        value=PreviewState(null,false)
        value=PreviewState(withContext(Dispatchers.IO) { note.preview?.let { BitmapFactory.decodeFile(it.path) } },true)
    }
    Card(onClick=onClick,modifier=Modifier.fillMaxWidth().testTag("note-${note.key}"),shape=RoundedCornerShape(12.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow),
        elevation=CardDefaults.cardElevation(defaultElevation=0.dp)) {
        Box(Modifier.fillMaxWidth().height(232.dp).background(Color.White).padding(12.dp),contentAlignment=Alignment.Center) {
            preview.bitmap?.let { Image(it.asImageBitmap(),contentDescription=null,modifier=Modifier.fillMaxSize().testTag("preview-${note.key}"),contentScale=ContentScale.Fit) }
                ?: Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Description,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if(note.unreadable)"Kayıt açılamadı" else if(!preview.loaded)"Önizleme hazırlanıyor…" else if(note.strokeCount==0)"Boş sayfa" else "Önizleme yok",
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
        }
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(note.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(if(note.unreadable)"Açıp yeniden deneyin" else "${note.pageCount} sayfa${if(note.isPdf)" · PDF" else ""} · ${DateUtils.getRelativeTimeSpanString(note.modified,System.currentTimeMillis(),DateUtils.MINUTE_IN_MILLIS)}",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
