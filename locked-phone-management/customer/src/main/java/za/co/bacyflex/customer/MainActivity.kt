package za.co.bacyflex.customer

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.math.BigDecimal
import java.util.Locale
import za.co.bacyflex.core.*

private data class HistoryRow(val amount:Long,val status:String,val reference:String)
private data class FinanceAccount(
    val name:String, val model:String, val status:String,
    val balance:Long,val installment:Long,val due:String,val contractId:String,
    val history:List<HistoryRow>,val bank:JSONObject?
)

class MainActivity : ComponentActivity() {
    private var page by mutableStateOf("Home")
    private var message by mutableStateOf("")
    private var working by mutableStateOf(false)
    private var account by mutableStateOf<FinanceAccount?>(null)
    private var cached by mutableStateOf(false)
    private var owner by mutableStateOf(false)
    private var paymentAmount by mutableStateOf("")
    private var paymentReference by mutableStateOf("")
    private var confirmPayment by mutableStateOf(false)
    private var alreadyRefreshed=false

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(248,248,247)
        window.navigationBarColor=android.graphics.Color.rgb(255,255,255)
        window.decorView.systemUiVisibility=android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        checkOwner()
        DeviceSyncScheduler.start(this)
        setContent { MbAppTheme { App() } }
    }
    override fun onResume() {
        super.onResume()
        checkOwner()
        if(!alreadyRefreshed || account!=null || DeviceSession.read(this,"pending_enrollment")!=null) {
            alreadyRefreshed=true
            refresh()
        }
    }

    private fun checkOwner() {
        val dpm=getSystemService(DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        owner=dpm?.isDeviceOwnerApp(packageName)==true
    }
    private fun work(action:()->String) {
        if(working)return
        working=true
        message="Connecting securely…"
        Thread {
            try {
                val result=action()
                runOnUiThread {message=result;working=false}
            } catch(e:Exception) {
                runOnUiThread {
                    message="Unable to complete: "+(e.message?:"Please try again.")
                    working=false
                }
            }
        }.start()
    }
    private fun refresh() {
        val activeSecret=DeviceSession.read(this,"device_secret")
        val enrolment=DeviceSession.read(this,"pending_enrollment")
        if(activeSecret==null && owner && enrolment!=null) {
            work {
                val secret=DeviceFinanceApi.newSecret()
                DeviceFinanceApi.activate(enrolment,secret)
                DeviceSession.store(this,"device_secret",secret)
                DeviceSyncScheduler.start(this)
                DeviceSession.clear(this,"pending_enrollment")
                readSnapshot(secret)
            }
        } else if(activeSecret!=null) {
            work {readSnapshot(activeSecret)}
        } else {
            account=null
            cached=false
            message=if(owner) "Android management enabled. Waiting for a valid admin enrolment code."
                else "This phone is not yet enrolled. Ask your finance provider for Android setup."
        }
    }
    private fun readSnapshot(secret:String):String {
        try {
            val response=DeviceFinanceApi.snapshot(secret)
            if(response.optJSONObject("account")==null)throw IllegalStateException("No assigned finance contract.")
            DeviceSession.store(this,"latest_snapshot",response.toString())
            runOnUiThread {applySnapshot(response,false)}
            return "Account refreshed from the finance server."
        } catch(error:Exception) {
            val stored=DeviceSession.read(this,"latest_snapshot")
            if(stored!=null) {
                val response=JSONObject(stored)
                runOnUiThread {applySnapshot(response,true)}
                return "Offline: last verified information only. Payments require internet."
            }
            throw error
        }
    }
    private fun applySnapshot(snapshot:JSONObject,fromCache:Boolean) {
        val record=snapshot.optJSONObject("account")?:return
        val transactions=record.optJSONArray("claims")
        val history=if(transactions==null) emptyList()
            else (0 until transactions.length()).mapNotNull {i->
                transactions.optJSONObject(i)?.let {row->
                    HistoryRow(row.optLong("amount_cents"),
                        row.optString("status","pending"),row.optString("reference",""))
                }
            }
        val state=record.optString("status","pending")
        val id=record.optString("contract_id","")
        val instalment=record.optLong("installment_cents")
        val balance=record.optLong("balance_cents")
        val due=if(record.isNull("next_due_date"))"Paid in full"
            else record.optString("next_due_date","Not assigned")
        account=FinanceAccount(record.optString("customer_name"),
            record.optString("device_model",Build.MODEL),state,
            balance,instalment,due,id,history,snapshot.optJSONObject("bank"))
        cached=fromCache
        if(!fromCache && state=="active") {
            if(paymentAmount.isEmpty())paymentAmount=rand(minOf(instalment,balance))
            if(paymentReference.isEmpty() && id.length>=8)paymentReference="MBMF-"+id.take(8).uppercase(Locale.ROOT)
        }
    }
    private fun submitPayment() {
        val secret=DeviceSession.read(this,"device_secret")
        val info=account
        if(secret==null || info==null || info.bank==null || cached) {
            message="Connect to the internet and refresh verified bank details before submitting.";return
        }
        if(info.status!="active") {
            message="Your account is not active. No payment claim can be submitted.";return
        }
        val reference=paymentReference.trim()
        val amount=try {BigDecimal(paymentAmount.trim()).movePointRight(2).longValueExact()}
            catch(e:Exception){-1L}
        if(!Regex("[a-zA-Z0-9 _./-]{4,80}").matches(reference) || amount<=0 || amount>100000000L) {
            message="Enter a valid payment reference and amount in rand.";return
        }
        work {
            DeviceFinanceApi.claim(secret,reference,amount)
            val status=readSnapshot(secret)
            "Your EFT claim was received. It is pending bank verification. "+status
        }
    }
    private fun copyReference() {
        if(paymentReference.isBlank()) {
            message="No payment reference available.";return
        }
        val clipboard=getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("MBMF EFT reference",paymentReference))
        message="Payment reference copied."
    }
    private fun openPhoneHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }
    @Composable private fun App() {
        Scaffold(containerColor=MbPalette.Canvas,
            bottomBar={ MbBottomNav(listOf("Home","Payments","Phone","Help"),page){page=it} }
        ) {inner->
            Column(modifier=Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
                .padding(start=22.dp,end=22.dp,bottom=26.dp)) {
                MbBrand("My account")
                when(page) {
                    "Home"->Home()
                    "Payments"->Payments()
                    "Phone"->Phone()
                    "Help"->Help()
                    else->Home()
                }
                Spacer(Modifier.height(18.dp))
                MbNotice(message)
                if(working) LinearProgressIndicator(
                    modifier=Modifier.fillMaxWidth().padding(top=8.dp),
                    color=MbPalette.Accent,trackColor=MbPalette.Line)
            }
        }
        if(confirmPayment) {
            AlertDialog(onDismissRequest={confirmPayment=false},
                title={Text("Submit payment claim")},
                text={Text("I confirm that I made a bank transfer of R "+paymentAmount+
                    " with reference "+paymentReference+
                    ". This will not mark the instalment paid until the finance provider verifies receipt.")},
                confirmButton={TextButton(onClick={
                    confirmPayment=false
                    submitPayment()
                }){Text("Submit for review")}},
                dismissButton={TextButton(onClick={confirmPayment=false}){Text("Cancel")}})
        }
    }

    @Composable private fun Home() {
        MbTitle("My finance","Your phone and the latest verified finance information.")
        val info=account
        MbMoneyCard("Remaining balance",
            if(info==null)"—" else "R "+rand(info.balance),
            if(info==null)"Next due date will appear after device setup"
            else "Next instalment: "+info.due)
        Spacer(Modifier.height(17.dp))
        if(cached) MbNotice("Offline copy: amounts may have changed. Connect to refresh.")
        if(info==null) {
            MbSection("Not yet activated")
            MbCard {
                Text("Your phone is not linked to a finance contract yet.",
                    fontSize=15.sp,color=MbPalette.Ink)
                Spacer(Modifier.height(10.dp))
                Text("Your provider must register you and show the Android setup QR. No customer email or signup is needed.",
                    fontSize=13.sp,color=MbPalette.Subtle)
                Spacer(Modifier.height(13.dp))
                MbButton("Check my account",!working){refresh()}
            }
        } else {
            MbSection("My agreement")
            MbCard {
                MbLine("Customer",info.name)
                MbLine("Phone",info.model)
                MbLine("Finance status",info.status.uppercase())
                MbLine("Regular instalment","R "+rand(info.installment))
                MbLine("Next payment",info.due)
            }
            MbSection("Quick actions")
            MbCard {
                MbLink("Pay by EFT","View your verified bank details"){page="Payments"}
                MbLink("My payment history","Claims and their actual approval status"){page="Payments"}
                MbLink("Phone information","Android device-management status"){page="Phone"}
                MbLink("Support","How to get help"){page="Help"}
            }
        }
        Spacer(Modifier.height(14.dp))
        MbButton("Refresh my account",!working,secondary=true){refresh()}
    }
    @Composable private fun Payments() {
        val info=account
        MbTitle("Payments","Transfer directly to the verified beneficiary. No in-app card payments.")
        if(info==null) {
            MbNotice("An administrator must enrol your phone before EFT details are shown.")
            MbButton("Check for activation",!working,secondary=true){refresh()}
            return
        }
        MbSection("Bank transfer details")
        val bank=info.bank
        if(bank==null) {
            MbNotice("The administrator has not yet published verified EFT banking details.")
        } else {
            MbCard {
                MbLine("Bank",bank.optString("bank_name"))
                MbLine("Beneficiary",bank.optString("account_holder"))
                MbLine("Account number",bank.optString("account_number"))
                MbLine("Branch",bank.optString("branch_code",""))
                if(bank.optString("instructions").isNotBlank())
                    Text(bank.optString("instructions"),color=MbPalette.Subtle,fontSize=12.sp)
            }
        }
        MbSection("Payment reference")
        MbField(paymentReference,{paymentReference=it},"Your bank transfer reference")
        Spacer(Modifier.height(10.dp))
        MbField(paymentAmount,{paymentAmount=it},"Amount transferred (R)")
        Spacer(Modifier.height(12.dp))
        MbButton("Copy bank reference",secondary=true){copyReference()}
        Spacer(Modifier.height(8.dp))
        MbButton("Open phone to use banking app",secondary=true){openPhoneHome()}
        Spacer(Modifier.height(8.dp))
        MbButton("I paid · Submit claim",!working && !cached && bank!=null && info.status=="active") {
            confirmPayment=true
        }
        Spacer(Modifier.height(10.dp))
        MbNotice("Submitting a claim is not proof of payment. Your provider verifies the bank transaction before approving.")
        MbSection("Claim history")
        if(info.history.isEmpty()) Text("No submitted payment claims.",color=MbPalette.Subtle)
        info.history.take(30).forEach { row->
            MbCard {
                Text("R "+rand(row.amount),fontSize=21.sp,color=MbPalette.Ink,
                    fontWeight=FontWeight.SemiBold)
                MbLine("Status",row.status.uppercase())
                MbLine("Reference",row.reference)
            }
            Spacer(Modifier.height(10.dp))
        }
        MbButton("Check payment status",!working,secondary=true){refresh()}
    }
    @Composable private fun Phone() {
        MbTitle("My phone","Android setup and device-management information.")
        MbCard {
            MbLine("Manufacturer",Build.MANUFACTURER.replaceFirstChar{it.uppercase()})
            MbLine("Model",Build.MODEL)
            MbLine("Android version",Build.VERSION.RELEASE)
            MbLine("Android Device Owner",if(owner)"Enrolled" else "Not enrolled")
            MbLine("Finance restriction","Not enabled in this pilot")
            MbLine("Assigned phone",account?.model?:"Not activated")
        }
        MbSection("Phone management")
        MbNotice("MBMF is only configured through Android's informed setup process. Emergency calling must remain available. Device restrictions are not active in this development build.")
        Spacer(Modifier.height(14.dp))
        MbButton("Check phone status",secondary=true){checkOwner();refresh()}
    }
    @Composable private fun Help() {
        MbTitle("Help & support","Clear information about your agreement.")
        MbSection("How payment works")
        MbCard {
            MbLine("Step 1","Use the displayed bank details")
            MbLine("Step 2","Pay in your normal banking app")
            MbLine("Step 3","Submit the exact EFT reference")
            MbLine("Step 4","Provider verifies the money")
            MbLine("Step 5","Approved payment updates balance")
        }
        MbSection("Need assistance?")
        Text("Contact the finance provider using the telephone number or email printed in your agreement. This app does not sell loans, collect bank passwords or block emergency calls.",
            fontSize=14.sp,color=MbPalette.Subtle,lineHeight=21.sp)
        Spacer(Modifier.height(16.dp))
        MbButton("Check finance account",!working,secondary=true){refresh()}
    }
    private fun rand(amount:Long):String=BigDecimal.valueOf(amount,2).toPlainString()
}
