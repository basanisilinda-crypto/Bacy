package za.co.bacyflex.core

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** MBMF Ledger: quiet, editorial finance UI. No simulated data or visual effects. */
object MbPalette {
    val Canvas = Color(0xFFF8F7F2)
    val White = Color(0xFFFFFEFC)
    val Ink = Color(0xFF202823)
    val Green = Color(0xFF355E4C)
    val Subtle = Color(0xFF65736A)
    val Line = Color(0xFFE4E6DF)
    val Warm = Color(0xFFBE744F)
    val Light = Color(0xFFEEF2EB)
}

@Composable
fun MbAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary=MbPalette.Green, onPrimary=Color.White,
            background=MbPalette.Canvas, onBackground=MbPalette.Ink,
            surface=MbPalette.White, onSurface=MbPalette.Ink,
            outline=MbPalette.Line, secondary=MbPalette.Warm
        ),
        content=content
    )
}

@Composable
fun MbBrand(area: String) {
    Column(modifier=Modifier.fillMaxWidth().padding(top=12.dp,bottom=18.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(modifier=Modifier.width(4.dp).height(26.dp).background(MbPalette.Green))
            Spacer(Modifier.width(12.dp))
            Text("MBMF",fontSize=20.sp,fontWeight=FontWeight.ExtraBold,
                color=MbPalette.Ink,letterSpacing=1.8.sp)
            Spacer(Modifier.weight(1f))
            Text(area.uppercase(),fontSize=10.sp,color=MbPalette.Subtle,letterSpacing=1.0.sp)
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color=MbPalette.Line)
    }
}

@Composable
fun MbTitle(title:String, description:String) {
    Column(modifier=Modifier.fillMaxWidth().padding(bottom=20.dp)) {
        Text(title,fontSize=29.sp,fontWeight=FontWeight.SemiBold,color=MbPalette.Ink,
            letterSpacing=(-0.7).sp)
        Spacer(Modifier.height(5.dp))
        Text(description,fontSize=13.sp,color=MbPalette.Subtle,lineHeight=19.sp)
    }
}

@Composable
fun MbCard(modifier:Modifier=Modifier, content:@Composable ColumnScope.() -> Unit) {
    Surface(modifier=modifier.fillMaxWidth(),color=MbPalette.White,
        border=BorderStroke(1.dp,MbPalette.Line),
        shape=RoundedCornerShape(14.dp)) {
        Column(modifier=Modifier.padding(18.dp),content=content)
    }
}

@Composable
fun MbMoneyCard(label:String, amount:String, bottom:String) {
    Surface(modifier=Modifier.fillMaxWidth(),color=MbPalette.Ink,
        shape=RoundedCornerShape(16.dp)) {
        Column(modifier=Modifier.padding(horizontal=22.dp,vertical=23.dp)) {
            Text(label.uppercase(),fontSize=10.sp,letterSpacing=1.4.sp,
                color=Color(0xFFD3DCD3))
            Spacer(Modifier.height(12.dp))
            Text(amount,color=MbPalette.White,fontSize=37.sp,fontWeight=FontWeight.Medium)
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color=Color(0xFF4A534B))
            Spacer(Modifier.height(12.dp))
            Text(bottom,color=Color(0xFFE1E9E2),fontSize=12.sp)
        }
    }
}

@Composable
fun MbMetric(label:String,value:String, modifier:Modifier=Modifier) {
    Column(modifier=modifier.padding(vertical=6.dp)) {
        Text(label.uppercase(),fontSize=9.sp,color=MbPalette.Subtle,letterSpacing=0.75.sp)
        Spacer(Modifier.height(7.dp))
        Text(value,fontSize=27.sp,fontWeight=FontWeight.SemiBold,color=MbPalette.Ink)
    }
}

@Composable
fun MbSection(label:String) {
    Text(label.uppercase(),fontSize=11.sp,color=MbPalette.Subtle,
        letterSpacing=1.2.sp,fontWeight=FontWeight.Bold,
        modifier=Modifier.padding(top=20.dp,bottom=10.dp))
}

@Composable
fun MbLine(label:String, value:String) {
    Row(modifier=Modifier.fillMaxWidth().padding(vertical=10.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Text(label,color=MbPalette.Subtle,fontSize=13.sp,modifier=Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(value,color=MbPalette.Ink,fontWeight=FontWeight.Medium,fontSize=13.sp,
            maxLines=2,overflow=TextOverflow.Ellipsis)
    }
    HorizontalDivider(color=MbPalette.Line)
}

@Composable
fun MbButton(title:String, enabled:Boolean=true, secondary:Boolean=false, onClick:()->Unit) {
    if (secondary) OutlinedButton(onClick=onClick,enabled=enabled,
        shape=RoundedCornerShape(8.dp),modifier=Modifier.fillMaxWidth().height(49.dp),
        colors=ButtonDefaults.outlinedButtonColors(contentColor=MbPalette.Ink),
        border=BorderStroke(1.dp,MbPalette.Line)) {
        Text(title,fontWeight=FontWeight.SemiBold,fontSize=14.sp)
    }
    else Button(onClick=onClick,enabled=enabled,
        shape=RoundedCornerShape(8.dp),modifier=Modifier.fillMaxWidth().height(49.dp),
        colors=ButtonDefaults.buttonColors(containerColor=MbPalette.Green)) {
        Text(title,fontWeight=FontWeight.SemiBold,fontSize=14.sp)
    }
}

@Composable
fun MbField(value:String, onChange:(String)->Unit, label:String, password:Boolean=false) {
    OutlinedTextField(value=value,onValueChange=onChange, label={Text(label)},
        modifier=Modifier.fillMaxWidth(), singleLine=true,
        shape=RoundedCornerShape(8.dp),
        visualTransformation=if(password) PasswordVisualTransformation() else VisualTransformation.None,
        colors=OutlinedTextFieldDefaults.colors(
            focusedBorderColor=MbPalette.Green,unfocusedBorderColor=MbPalette.Line,
            focusedContainerColor=MbPalette.White,unfocusedContainerColor=MbPalette.White
        ))
}

@Composable
fun MbNotice(text:String) {
    if(text.isNotEmpty()) {
        Surface(color=MbPalette.Light,shape=RoundedCornerShape(9.dp),
            modifier=Modifier.fillMaxWidth()) {
            Text(text,color=MbPalette.Green,fontSize=12.sp,lineHeight=17.sp,
                modifier=Modifier.padding(12.dp))
        }
    }
}

@Composable
fun MbLink(title:String, detail:String="", onClick:()->Unit) {
    Column(modifier=Modifier.fillMaxWidth().clickable(onClick=onClick)
        .padding(vertical=15.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(modifier=Modifier.weight(1f)) {
                Text(title,color=MbPalette.Ink,fontSize=15.sp,fontWeight=FontWeight.Medium)
                if(detail.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(detail,color=MbPalette.Subtle,fontSize=12.sp)
                }
            }
            Text("›",color=MbPalette.Green,fontSize=22.sp)
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color=MbPalette.Line)
    }
}

@Composable
fun MbBottomNav(options:List<String>, selected:String, choose:(String)->Unit) {
    Surface(color=MbPalette.White,shadowElevation=3.dp) {
        Row(modifier=Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(horizontal=10.dp,vertical=8.dp)) {
            options.forEach { label ->
                val active=selected==label
                Column(modifier=Modifier.weight(1f).clickable{choose(label)}
                    .padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.width(20.dp).height(3.dp)
                        .background(if(active) MbPalette.Green else Color.Transparent))
                    Spacer(Modifier.height(7.dp))
                    Text(label,fontSize=11.sp, fontWeight=if(active)FontWeight.Bold else FontWeight.Normal,
                        color=if(active)MbPalette.Ink else MbPalette.Subtle)
                }
            }
        }
    }
}
