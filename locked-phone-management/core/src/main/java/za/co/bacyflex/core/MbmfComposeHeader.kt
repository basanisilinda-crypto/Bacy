package za.co.bacyflex.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Native Compose header shared by both APKs; no fictional business values. */
object MbmfComposeHeader {
    @JvmStatic
    fun attach(target: ComposeView, title: String, subtitle: String) {
        target.setContent { BrandHeader(title, subtitle) }
    }
}
@Composable
private fun BrandHeader(title: String, subtitle: String) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF176AD5))) {
        Surface(modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp), color = Color(0xFF10243F)) {
            Row(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Box(modifier = Modifier.size(44.dp)
                    .background(Color(0xFF2B82E5), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center) {
                    Text("M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 25.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("MBMF", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(title, color = Color(0xFFBFE0FF), fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = Color(0xFFD0D9E6), fontSize = 12.sp)
                }
            }
        }
    }
}
