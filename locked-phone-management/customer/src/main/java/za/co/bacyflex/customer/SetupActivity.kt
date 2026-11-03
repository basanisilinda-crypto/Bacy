package za.co.bacyflex.customer

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Premium, consent-based native onboarding. Never pretends an app can grant itself Device Owner. */
internal object SetupStyle {
    val ink=Color(0xFF19383B)
    val mint=Color(0xFF146D63)
    val bg=Color(0xFFF8FAF6)
    val surface=Color.White
    val soft=Color(0xFFE0F4E8)
    val peach=Color(0xFFFFE7D8)
    val muted=Color(0xFF627674)
    val line=Color(0xFFE0E9E3)
}
@Composable internal fun SetupTheme(content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=lightColorScheme(
        primary=SetupStyle.mint,background=SetupStyle.bg,
        surface=SetupStyle.surface,onSurface=SetupStyle.ink),content=content)
}
@Composable internal fun SetupHeader(step:Int,title:String,subtitle:String) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        Surface(shape=RoundedCornerShape(14.dp),color=SetupStyle.mint,
            modifier=Modifier.size(46.dp)) {
            Box(contentAlignment=Alignment.Center) {
                Text("M",fontSize=25.sp,fontWeight=FontWeight.ExtraBold,color=Color.White)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("MBMF",fontSize=18.sp,fontWeight=FontWeight.ExtraBold,
                color=SetupStyle.ink,letterSpacing=2.sp)
            Text("DEVICE FINANCE",fontSize=9.sp,letterSpacing=1.sp,color=SetupStyle.muted)
        }
        Spacer(Modifier.weight(1f))
        Surface(color=SetupStyle.soft,shape=RoundedCornerShape(24.dp)) {
            Text("0"+step+" / 04",fontSize=11.sp,color=SetupStyle.mint,
                fontWeight=FontWeight.Bold,modifier=Modifier.padding(12.dp))
        }
    }
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        (1..4).forEach { i ->
            Box(modifier=Modifier.weight(1f).height(5.dp).background(
                if(i<=step)SetupStyle.mint else SetupStyle.line,RoundedCornerShape(8.dp)))
        }
    }
    Spacer(Modifier.height(30.dp))
    Text(title,fontSize=30.sp,lineHeight=36.sp,color=SetupStyle.ink,
        fontWeight=FontWeight.SemiBold,letterSpacing=(-0.7).sp)
    Spacer(Modifier.height(10.dp))
    Text(subtitle,fontSize=14.sp,lineHeight=21.sp,color=SetupStyle.muted)
    Spacer(Modifier.height(24.dp))
}
@Composable internal fun SetupCard(content:@Composable ColumnScope.()->Unit) {
    Surface(color=SetupStyle.surface,shape=RoundedCornerShape(21.dp),
        modifier=Modifier.fillMaxWidth(),
        border=androidx.compose.foundation.BorderStroke(1.dp,SetupStyle.line)) {
        Column(modifier=Modifier.padding(21.dp),content=content)
    }
}
@Composable internal fun SetupFact(name:String,detail:String) {
    Text(name,fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=SetupStyle.ink)
    Spacer(Modifier.height(5.dp))
    Text(detail,fontSize=13.sp,lineHeight=19.sp,color=SetupStyle.muted)
    Spacer(Modifier.height(16.dp))
}
@Composable internal fun SetupBanner(value:String,alert:Boolean=false) {
    Surface(shape=RoundedCornerShape(15.dp),
        color=if(alert)SetupStyle.peach else SetupStyle.soft,
        modifier=Modifier.fillMaxWidth()) {
        Text(value,modifier=Modifier.padding(17.dp),fontSize=13.sp,
            lineHeight=20.sp,color=SetupStyle.ink)
    }
}
@Composable internal fun SetupButton(label:String,enabled:Boolean=true,secondary:Boolean=false,onClick:()->Unit) {
    if(secondary) OutlinedButton(onClick=onClick,enabled=enabled,
        modifier=Modifier.fillMaxWidth().height(54.dp),
        shape=RoundedCornerShape(15.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,SetupStyle.line)) {
        Text(label,fontWeight=FontWeight.SemiBold,fontSize=14.sp,color=SetupStyle.ink)
    } else Button(onClick=onClick,enabled=enabled,
        modifier=Modifier.fillMaxWidth().height(54.dp),
        shape=RoundedCornerShape(15.dp),
        colors=ButtonDefaults.buttonColors(containerColor=SetupStyle.mint)) {
        Text(label,fontWeight=FontWeight.SemiBold,fontSize=14.sp)
    }
}
@Composable internal fun SetupTerms(checked:Boolean,onChecked:(Boolean)->Unit) {
    SetupCard {
        SetupFact("Your payment plan","MBMF shows your real instalments, balance, due dates and manually verified EFT records.")
        SetupFact("Device management","Your finance provider may use Android management policies only as disclosed in the signed agreement.")
        SetupFact("Your privacy","MBMF does not need your messages, photos, banking passwords or private location.")
        SetupFact("Support and emergency access","Emergency calling and appropriate support access must remain available.")
        SetupFact("After settlement","Finance controls must be released through a supported, verified process.")
        Row(verticalAlignment=Alignment.Top) {
            Checkbox(checked=checked,onCheckedChange=onChecked,
                colors=CheckboxDefaults.colors(checkedColor=SetupStyle.mint))
            Text("I have read and understood the device-management information and my signed finance agreement.",
                modifier=Modifier.padding(top=10.dp),fontSize=13.sp,
                lineHeight=20.sp,color=SetupStyle.ink)
        }
    }
}
class SetupActivity:ComponentActivity() {
    private lateinit var manager:DevicePolicyManager
    private lateinit var component:ComponentName
    private var deviceOwner by mutableStateOf(false)
    private var deviceAdmin by mutableStateOf(false)
    private var hasCode by mutableStateOf(false)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        manager=getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        component=ComponentName(this,FinanceAdminReceiver::class.java)
        refresh()
        window.statusBarColor=android.graphics.Color.rgb(25,56,59)
        window.navigationBarColor=android.graphics.Color.rgb(248,250,246)
        window.decorView.systemUiVisibility=android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {SetupTheme{Wizard()}}
    }
    override fun onResume() {super.onResume();if(::manager.isInitialized)refresh()}
    private fun refresh() {
        deviceOwner=manager.isDeviceOwnerApp(packageName)
        deviceAdmin=manager.isAdminActive(component)
        hasCode=DeviceSession.read(this,"pending_enrollment")!=null
    }
    /** This requests ordinary Device Administrator; Android alone controls the approval screen. */
    private fun requestAdmin() {
        if(deviceOwner||deviceAdmin)return
        val intent=Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,component)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Review MBMF device-administrator access only if you understand and agree. " +
                "This is not Device Owner enrolment. Full management needs Android setup-time QR.")
        }
        try {startActivity(intent)} catch(_:Exception) { /* Use QR setup if unavailable. */ }
    }
    @Composable private fun Wizard() {
        var step by rememberSaveable {mutableIntStateOf(1)}
        var accepted by rememberSaveable {mutableStateOf(false)}
        Column(modifier=Modifier.fillMaxSize().background(SetupStyle.bg)
            .verticalScroll(rememberScrollState()).padding(24.dp)) {
            when(step) {
                1 -> {
                    SetupHeader(step,"A lovely start for your phone.",
                        "Your MBMF account is already arranged by the finance provider. You never need to sign up.")
                    SetupCard {
                        SetupFact("Everything in one place","Keep up with payments, balances and your finance plan.")
                        SetupFact("Made for your phone","Model: "+Build.MANUFACTURER+" "+Build.MODEL+
                            " · Android "+Build.VERSION.RELEASE)
                        SetupBanner("A clear setup, a real account and support when you need it.")
                    }
                    Spacer(Modifier.height(24.dp))
                    SetupButton("Start my setup"){step=2}
                }
                2 -> {
                    SetupHeader(step,"Your phone. Your choice.",
                        "Before we continue, please understand how financing and Android management work.")
                    SetupTerms(accepted){accepted=it}
                    Spacer(Modifier.height(22.dp))
                    SetupButton("I understand · Continue",enabled=accepted){step=3}
                    Spacer(Modifier.height(10.dp))
                    SetupButton("Back",secondary=true){step=1}
                }
                3 -> {
                    SetupHeader(step,"Let's check Android.",
                        "Permissions appear in Android's own secure system screens.")
                    SetupCard {
                        SetupFact("Device Owner",if(deviceOwner)"Confirmed by Android."
                            else "Not active. A normally installed app cannot request Device Owner.")
                        SetupFact("Device Administrator",if(deviceAdmin)"Permission enabled by Android."
                            else "Not enabled. You may review Android's optional administrator request.")
                        SetupBanner(if(deviceOwner)"Fully managed enrolment detected."
                            else "To become Device Owner, your provider must scan a one-time QR on a new or reset compatible phone during Android Welcome.",!deviceOwner)
                    }
                    Spacer(Modifier.height(20.dp))
                    if(!deviceOwner&&!deviceAdmin) {
                        SetupButton("Review administrator permission"){requestAdmin()}
                        Spacer(Modifier.height(10.dp))
                    }
                    SetupButton("Check and continue"){refresh();step=4}
                    Spacer(Modifier.height(10.dp))
                    SetupButton("Back",secondary=true){step=2}
                }
                else -> {
                    SetupHeader(step,"You're nearly set.",
                        "Your account will open when Android setup and secure contract linking are confirmed.")
                    SetupCard {
                        SetupFact("Android Device Owner",if(deviceOwner)"Verified"
                            else "Not active. Scan the official MBMF setup QR during phone setup.")
                        SetupFact("One-time finance link",if(hasCode)"Setup code received"
                            else "Waiting for your finance provider's QR")
                        SetupBanner(if(deviceOwner && hasCode)
                            "All set for secure activation. Your payments will load from the real server."
                            else "Ordinary administrator permission is not Device Owner. Ask your provider to register the contract and scan the QR during Android Welcome.",!(deviceOwner&&hasCode))
                    }
                    Spacer(Modifier.height(22.dp))
                    if(deviceOwner && hasCode) SetupButton("Open my finance account"){
                        startActivity(Intent(this@SetupActivity,MainActivity::class.java)
                            .putExtra("mbmf_continue_activation",true))
                        finish()
                    } else SetupButton("Check setup again"){refresh()}
                    Spacer(Modifier.height(10.dp))
                    SetupButton("Back",secondary=true){step=3}
                }
            }
        }
    }
}
