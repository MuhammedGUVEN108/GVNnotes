package com.gvn.notes

import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.util.Vector
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportTransformTest {
    @Test fun mapsVisibleCornersIntoOriginalCropForEveryRotation() {
        // Kırpılmış kaynak kutusu (20,30)-(320,430). Beklenenler PDF'nin fiziksel köşeleridir.
        val corners=mapOf(
            0 to listOf(20f to 30f,320f to 30f,20f to 430f),
            90 to listOf(320f to 30f,320f to 430f,20f to 30f),
            180 to listOf(320f to 430f,20f to 430f,320f to 30f),
            270 to listOf(20f to 430f,20f to 30f,320f to 430f))
        corners.forEach { (rotation,expected) ->
            val page=PDPage(PDRectangle(400f,500f)).apply {
                cropBox=PDRectangle(20f,30f,300f,400f); this.rotation=rotation
            }
            val matrix=overlayMatrix(page,600f,800f)
            listOf(0f to 0f,600f to 0f,0f to 800f).forEachIndexed { i,(x,y) ->
                val p=matrix.transform(Vector(x,y))
                assertEquals("$rotation köşe $i x",expected[i].first,p.x,.001f)
                assertEquals("$rotation köşe $i y",expected[i].second,p.y,.001f)
            }
        }
    }
}
