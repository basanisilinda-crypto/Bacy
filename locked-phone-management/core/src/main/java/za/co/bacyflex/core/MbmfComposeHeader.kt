package za.co.bacyflex.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow

/**
 * MBMF Ledger masthead. Original editorial financial identity rather than an
 * illustration, phone orbit, luminous dashboard or AI-inspired effect.
 */
object MbmfComposeHeader {
    @JvmStatic fun attach(target: ComposeView, title: String, subtitle: String) {
        target.setContent { LedgerMasthead(title, subtitle) }
    }
}

@Composable
private fun LedgerMasthead(title: String, subtitle: String) {
    val ink = Color(0xFF1D2521)
    val green = Color(0xFF244E3C)
    val muted = Color(0xFF666D66)
    MaterialTheme(colorScheme = lightColorScheme(primary = green)) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(modifier = Modifier.size(width = 5.dp, height = 29.dp).background(green))
                Column(modifier = Modifier.weight(1f)) {
                    Text("MBMF", color = ink, fontSize = 17.sp,
                        letterSpacing = 2.4.sp, fontWeight = FontWeight.Black)
                    Text("MOBILE BUSINESS  /  FINANCE", color = muted, fontSize = 8.sp,
                        letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium)
                }
                Text("SA  ·  ZAR", color = green, fontSize = 10.sp,
                    letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = ink, fontSize = 29.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp)
                Text(subtitle, color = muted, fontSize = 13.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(modifier = Modifier.fillMaxWidth().size(height = 1.dp, width = 1.dp)
                .background(Color(0xFFDCD8CF)))
        }
    }
}