package com.gvn.notes

/**
 * Testler GVNpen ayarlarını yazma yüzeyinin bellekteki [com.gvn.notes.pen.Prefs] nesnesinde ayarlar; kaydetmez,
 * böylece kullanıcının gerçek kalem ayarları değişmez. Akıllı şekil varsayılan kapalıdır (düz test çizgileri bekletilince
 * şekle dönüşmesin). [eraser] GVNpen silgi boyutudur (yarıçap = boyut / 2).
 */
fun InkPageView.usePen(tool:String="pen",width:Float=8f,color:Int=0xff18222e.toInt(),whole:Boolean=true,
    eraser:Float=48f,smart:Boolean=false,onlyPen:Boolean=true) {
    drawing=true
    prefs.tool=tool; prefs.pen=width; prefs.marker=width*3; prefs.color=color; prefs.opacity=1f
    prefs.whole=whole; prefs.eraser=eraser; prefs.smart=smart; prefs.onlyPen=onlyPen; prefs.predict=false
}
