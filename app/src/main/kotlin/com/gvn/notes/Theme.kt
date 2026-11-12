package com.gvn.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "Petrol Ada" tasarım dili (kullanıcı onayı, 12 Kasım 2026): açık mavi-gri masa, yüzen yumuşak gölgeli adalar,
 * indigo seçim/birincil eylem, petrol mavisi klasör ve eşitleme kimliği. Yazma tarafı (GVNpen şeridi) değişmez.
 */
object Ada {
    val Desk=Color(0xffE3E9F1)
    val Surface=Color(0xffF9FBFF)
    val Ink=Color(0xff1F2D42)
    val Muted=Color(0xff58687F)
    val Accent=Color(0xff4F57B0)
    val Soft=Color(0xffE6E8FA)
    val Line=Color(0xffCAD3E3)
    val Petrol=Color(0xff186C84)
    val Danger=Color(0xffB3261E)
    val Disabled=Color(0xffA9B3C2)
    val IslandShape=RoundedCornerShape(24.dp)
    val RowShape=RoundedCornerShape(16.dp)
}

val AdaColors=lightColorScheme(
    primary=Ada.Accent, onPrimary=Color.White, primaryContainer=Ada.Soft, onPrimaryContainer=Ada.Accent,
    secondary=Ada.Petrol, onSecondary=Color.White, secondaryContainer=Ada.Soft, onSecondaryContainer=Ada.Ink,
    background=Ada.Desk, onBackground=Ada.Ink, surface=Ada.Surface, onSurface=Ada.Ink, onSurfaceVariant=Ada.Muted,
    surfaceVariant=Ada.Soft, surfaceContainerLowest=Color.White, surfaceContainerLow=Ada.Surface, surfaceContainer=Ada.Surface,
    surfaceContainerHigh=Ada.Surface, surfaceContainerHighest=Ada.Soft, outline=Ada.Line, outlineVariant=Ada.Line,
)

/** Yüzen ada: yuvarlak köşeli, yumuşak gölgeli yüzey. */
@Composable fun Island(modifier:Modifier=Modifier,shape:Shape=Ada.IslandShape,elevation:Dp=6.dp,content:@Composable ()->Unit) {
    Surface(modifier,shape=shape,color=Ada.Surface,contentColor=Ada.Ink,shadowElevation=elevation,content=content)
}

/** Sayfa şablonu önizlemesi (Düz, Çizgili, Kareli, Noktalı); yeni not ve ayarlarda kullanılır. */
@Composable fun PatternPreview(code:Int,modifier:Modifier=Modifier) {
    Canvas(modifier) {
        drawRect(Color.White)
        val step=size.width/9f; val line=Color(0xffCFD8E3)
        when(code) {
            1 -> { var y=step*1.4f; while(y<size.height-step*.5f) { drawLine(line,Offset(step*.6f,y),Offset(size.width-step*.6f,y),1.2f); y+=step } }
            2 -> {
                var y=step*.6f; while(y<size.height) { drawLine(line,Offset(0f,y),Offset(size.width,y),1.2f); y+=step }
                var x=step*.6f; while(x<size.width) { drawLine(line,Offset(x,0f),Offset(x,size.height),1.2f); x+=step }
            }
            3 -> {
                var y=step*.8f; while(y<size.height) { var x=step*.8f; while(x<size.width) { drawCircle(Color(0xffAEBCCB),1.8f,Offset(x,y)); x+=step }; y+=step }
            }
        }
    }
}
