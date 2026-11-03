package za.co.bacyflex.customer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A friendly but serious native Android setup experience.
 * No customer signup, invented finance data, coercive permission screens or fake status.
 */
object ClientSetupUi {
    private val paper=Color(0xFFFCFAF5)
    private val ink=Color(0xFF25352E)
    private val muted=Color(0xFF69716B)
    private val forest=Color(0xFF315944)
    private val mint=Color(0xFFDEEEE4)
    private val peach=Color(0xFFF7E6D9)
    private val line=Color(0xFFE7E6DD)

    @JvmStatic
    fun showWizard(
        activity: ComponentActivity,
        deviceName: String,
        deviceStatus: () -> SetupAccess,
        refreshVersion: () -> Int,
        onRequestAdmin: () -> Unit,
        onFinish: () -> Unit
    ) {
        activity.window.statusBarColor=android.graphics.Color.rgb(37,53,46)
        activity.window.navigationBarColor=android.graphics.Color.rgb(37,53,46)
        val view=ComposeView(activity)
        view.setContent {
            var page by rememberSaveable { mutableIntStateOf(0) }
            val refresh=refreshVersion()
            val status=remember(refresh) { deviceStatus() }
            Theme {
                SetupFrame(
                    step=page+1,
                    heading=listOf(
                        "Hello, lovely phone.",
                        "Your plan, made simple.",
                        "A phone you understand.",
                        "You're all set to explore."
                    )[page],
                    detail=listOf(
                        "Let's get to know MBMF and set up your finance information.",
                        "Everything you need for your instalments stays in one place.",
                        "You choose what you agree to. Android controls the permissions.",
                        "Your account is ready to display once the secure finance setup is complete."
                    )[page],
                    primary=if(page==3) "Open my phone plan" else "Continue",
                    onPrimary={
                        if(page==3) onFinish() else page += 1
                    },
                    onBack=if(page>0) ({page-=1}) else null,
                    footer="MBMF Client  ·  South Africa  ·  No password needed"
                ) {
                    when(page) {
                        0 -> {
                            CutePhoneGraphic()
                            Spacer(Modifier.height(20.dp))
                            FactCard("Welcome to MBMF", "A friendly place for your payment dates, balance and phone information.")
                            Spacer(Modifier.height(11.dp))
                            FactCard("Your device", deviceName.ifBlank { "Android phone" })
                        }
                        1 -> {
                            SectionIllustration("R", "Rands & reminders", peach)
                            Spacer(Modifier.height(16.dp))
                            FactCard("No customer account to create", "Your finance provider registers your phone and payment plan.")
                            Spacer(Modifier.height(11.dp))
                            FactCard("Only verified payments count", "A bank transfer claim stays pending until the provider checks the money received.")
                            Spacer(Modifier.height(11.dp))
                            FactCard("Works offline too", "You can view the last verified plan while disconnected.")
                        }
                        2 -> {
                            SectionIllustration("✓", "Clear, informed management", mint)
                            Spacer(Modifier.height(15.dp))
                            FactCard("What management may do", "With an agreed contract and compatible phone, the provider may apply disclosed device policies. Emergency and support access must remain available.")
                            Spacer(Modifier.height(11.dp))
                            FactCard("What we do not do", "This app does not secretly read your messages, photos or location. Device Owner access is granted through Android setup—not by tapping a normal app button.")
                            Spacer(Modifier.height(15.dp))
                            AccessStatus(status)
                            if(status==SetupAccess.NONE) {
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(onClick=onRequestAdmin,
                                    shape=RoundedCornerShape(15.dp),
                                    modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)) {
                                    Text("Review optional device admin access",color=forest,
                                        fontSize=13.sp, textAlign=TextAlign.Center)
                                }
                                Text("Android will show its own permission screen. You may decline; this is not Device Owner enrolment.",
                                    color=muted,fontSize=12.sp,
                                    modifier=Modifier.padding(top=7.dp))
                            }
                        }
                        else -> {
                            CutePhoneGraphic()
                            Spacer(Modifier.height(16.dp))
                            AccessStatus(status)
                            Spacer(Modifier.height(12.dp))
                            FactCard("Important", when(status) {
                                SetupAccess.DEVICE_OWNER -> "Device Owner is confirmed by Android. The app will link the finance agreement using its assigned one-time enrolment credential when available."
                                SetupAccess.DEVICE_ADMIN -> "Limited Android device administrator is active. Full Device Owner enrolment still requires a compatible phone and an admin-generated QR during Android initial setup."
                                SetupAccess.NONE -> "This phone has not been enrolled as Device Owner. Your finance provider must use a compatible phone's Android setup QR to complete a fully managed finance installation."
                            })
                        }
                    }
                }
            }
        }
        activity.setContentView(view)
    }

    @JvmStatic
    fun showConsent(activity: ComponentActivity,onAgree: Runnable,onDecline: Runnable) {
        activity.window.statusBarColor=android.graphics.Color.rgb(37,53,46)
        activity.window.navigationBarColor=android.graphics.Color.rgb(37,53,46)
        val view=ComposeView(activity)
        view.setContent {
            var agreed by rememberSaveable { mutableStateOf(false) }
            Theme {
                Column(Modifier.fillMaxSize().background(paper)
                    .statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
                    BrandRow()
                    Spacer(Modifier.height(22.dp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text("Before we finish",fontSize=13.sp,
                            fontWeight=FontWeight.SemiBold,color=forest)
                        Spacer(Modifier.height(12.dp))
                        Text("Your phone.\nYour choice.",fontSize=34.sp,
                            fontWeight=FontWeight.Bold,color=ink,lineHeight=40.sp)
                        Spacer(Modifier.height(20.dp))
                        SectionIllustration("✓","Device finance management",mint)
                        Spacer(Modifier.height(16.dp))
                        FactCard("About this agreement", "This phone is being enrolled as a fully managed financed device. Android may give the finance provider broad device-management authority.")
                        Spacer(Modifier.height(10.dp))
                        FactCard("What may change", "Agreed management policies may affect normal phone access when permitted by law and supported by Android. Your signed agreement must explain any restrictions.")
                        Spacer(Modifier.height(10.dp))
                        FactCard("Your safety and privacy", "Emergency calling and support access must remain available. MBMF does not request access to personal messages, photos or location.")
                        Spacer(Modifier.height(15.dp))
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(checked=agreed,onCheckedChange={agreed=it},
                                colors=CheckboxDefaults.colors(checkedColor=forest))
                            Text("I have read this explanation and agree to the disclosed device management.",
                                color=ink,fontSize=13.sp,lineHeight=19.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick={onAgree.run()},enabled=agreed,
                        shape=RoundedCornerShape(15.dp),modifier=Modifier.fillMaxWidth().height(54.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=forest)) {
                        Text("Agree and continue",fontWeight=FontWeight.SemiBold)
                    }
                    TextButton(onClick={onDecline.run()},
                        modifier=Modifier.fillMaxWidth()) {
                        Text("Decline managed setup",color=ink)
                    }
                }
            }
        }
        activity.setContentView(view)
    }

    @Composable
    private fun Theme(content:@Composable ()->Unit) {
        MaterialTheme(colorScheme=lightColorScheme(primary=forest,background=paper,surface=paper),
            content=content)
    }

    @Composable
    private fun SetupFrame(
        step:Int,heading:String,detail:String,primary:String,
        onPrimary:()->Unit,onBack:(()->Unit)?,footer:String,
        children:@Composable ColumnScope.()->Unit
    ) {
        Column(Modifier.fillMaxSize().background(paper)
            .statusBarsPadding().navigationBarsPadding().padding(horizontal=24.dp,vertical=16.dp)) {
            BrandRow()
            Spacer(Modifier.height(25.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                for(i in 1..4) {
                    Box(Modifier.weight(1f).height(5.dp)
                        .background(if(i<=step) forest else line, RoundedCornerShape(5.dp)))
                }
            }
            Text("STEP $step OF 4",color=forest,fontSize=11.sp,
                fontWeight=FontWeight.Bold,letterSpacing=1.6.sp,
                modifier=Modifier.padding(top=18.dp))
            Spacer(Modifier.height(10.dp))
            Text(heading,fontSize=30.sp,fontWeight=FontWeight.Bold,
                color=ink,lineHeight=37.sp)
            Spacer(Modifier.height(8.dp))
            Text(detail,color=muted,fontSize=14.sp,lineHeight=21.sp)
            Spacer(Modifier.height(21.dp))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                content=children)
            Spacer(Modifier.height(10.dp))
            Button(onClick=onPrimary,shape=RoundedCornerShape(15.dp),
                colors=ButtonDefaults.buttonColors(containerColor=forest),
                modifier=Modifier.fillMaxWidth().height(55.dp)) {
                Text(primary,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            }
            if(onBack!=null) {
                TextButton(onClick=onBack,modifier=Modifier.fillMaxWidth()) {
                    Text("Back",color=ink)
                }
            }
            Text(footer,fontSize=10.sp,color=muted,textAlign=TextAlign.Center,
                modifier=Modifier.fillMaxWidth().padding(top=6.dp))
        }
    }

    @Composable
    private fun BrandRow() {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(forest,RoundedCornerShape(11.dp)),
                contentAlignment=Alignment.Center) {
                Text("m.",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold)
            }
            Spacer(Modifier.width(9.dp))
            Text("MBMF",color=ink,fontSize=17.sp,fontWeight=FontWeight.Black,
                letterSpacing=1.8.sp)
            Spacer(Modifier.weight(1f))
            Text("PHONE FINANCE",color=muted,fontSize=10.sp,letterSpacing=1.sp)
        }
    }

    @Composable
    private fun CutePhoneGraphic() {
        Box(Modifier.fillMaxWidth().height(196.dp)
            .background(peach,RoundedCornerShape(28.dp)),
            contentAlignment=Alignment.Center) {
            Box(Modifier.width(105.dp).height(161.dp).border(3.dp,ink,RoundedCornerShape(21.dp))
                .background(paper,RoundedCornerShape(21.dp)),contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.size(51.dp).background(mint,CircleShape),
                        contentAlignment=Alignment.Center) {
                        Text("✓",color=forest,fontSize=29.sp,fontWeight=FontWeight.Bold)
                    }
                    Spacer(Modifier.height(11.dp))
                    Box(Modifier.width(53.dp).height(5.dp).background(line,RoundedCornerShape(5.dp)))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(35.dp).height(5.dp).background(line,RoundedCornerShape(5.dp)))
                }
            }
        }
    }

    @Composable
    private fun SectionIllustration(symbol:String,title:String,color:Color) {
        Row(modifier=Modifier.fillMaxWidth().background(color,RoundedCornerShape(21.dp))
            .padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(55.dp).background(paper,CircleShape),
                contentAlignment=Alignment.Center) {
                Text(symbol,color=forest,fontSize=27.sp,fontWeight=FontWeight.Bold)
            }
            Spacer(Modifier.width(15.dp))
            Text(title,color=ink,fontSize=17.sp,fontWeight=FontWeight.SemiBold,
                lineHeight=22.sp)
        }
    }

    @Composable
    private fun FactCard(title:String,description:String) {
        Column(Modifier.fillMaxWidth().border(1.dp,line,RoundedCornerShape(16.dp))
            .padding(15.dp)) {
            Text(title,color=ink,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(description,color=muted,fontSize=12.sp,lineHeight=19.sp)
        }
    }

    @Composable
    private fun AccessStatus(status:SetupAccess) {
        val caption=when(status) {
            SetupAccess.DEVICE_OWNER -> "Android confirms: Device Owner"
            SetupAccess.DEVICE_ADMIN -> "Android confirms: Device Administrator only"
            SetupAccess.NONE -> "Android has not granted device management"
        }
        Row(modifier=Modifier.fillMaxWidth().background(mint,RoundedCornerShape(13.dp))
            .padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(if(status==SetupAccess.NONE) "○" else "✓",color=forest,fontSize=23.sp)
            Spacer(Modifier.width(10.dp))
            Text(caption,color=ink,fontWeight=FontWeight.SemiBold,fontSize=13.sp,
                lineHeight=19.sp)
        }
    }
}
