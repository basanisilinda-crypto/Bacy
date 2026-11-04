package za.co.bacyflex.admin

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import za.co.bacyflex.core.*

private data class ContractRow(
    val id:String,val name:String,val phone:String,val model:String,
    val balance:Long,val due:String,val status:String,val enrolled:Boolean
)
private data class EftRow(val id:String,val reference:String,val amount:Long,val submitted:String)

class MainActivity : ComponentActivity() {
    private var accessToken:String?=null
    private var authenticated by mutableStateOf(false)
    private var page by mutableStateOf("Overview")
    private var message by mutableStateOf("")
    private var working by mutableStateOf(false)
    private var contracts by mutableStateOf<List<ContractRow>>(emptyList())
    private var claims by mutableStateOf<List<EftRow>>(emptyList())
    private var dashboard by mutableStateOf(JSONObject())
    private var enrollmentCode:String?=null
    private var qrBitmap by mutableStateOf<Bitmap?>(null)
    private var decision by mutableStateOf<Pair<EftRow,Boolean>?>(null)

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(32,40,35)
        window.navigationBarColor=android.graphics.Color.rgb(255,254,252)
        window.decorView.systemUiVisibility=android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { MbAppTheme { App() } }
    }

    private fun work(action:()->String) {
        if(working)return
        working=true
        message="Connecting securely…"
        Thread {
            try {
                val result=action()
                runOnUiThread { message=result; working=false }
            } catch(e:Exception) {
                runOnUiThread {
                    message="Unable to complete: "+(e.message?:"Please try again.")
                    working=false
                }
            }
        }.start()
    }

    private fun signIn(email:String,password:String) {
        if(email.isBlank()||password.isBlank()) {message="Enter your email and password.";return}
        work {
            val auth=Cloud.signIn(email,password)
            val jwt=auth.getString("access_token")
            val uid=auth.getJSONObject("user").getString("id")
            val found=Cloud.rows("finance_admins?select=user_id&user_id=eq."+uid,jwt)
            if(found.length()==0)throw SecurityException("This account is not authorised as an MBMF administrator.")
            val result=readDashboard(jwt)
            runOnUiThread {
                accessToken=jwt
                authenticated=true
                page="Overview"
                applyDashboard(result)
            }
            "Connected to your live finance records."
        }
    }

    private fun createLogin(email:String,password:String) {
        if(!email.contains("@")||password.length<10) {
            message="Enter a valid email and a password with at least ten characters.";return
        }
        work {
            Cloud.signUp(email,password)
            "Check your email. A trusted operator must separately grant administrator permission."
        }
    }

    private data class Snapshot(
        val contracts:List<ContractRow>,val claims:List<EftRow>,val metrics:JSONObject
    )
    private fun readDashboard(jwt:String):Snapshot {
        val finance=Cloud.rows(
            "finance_contracts?select=id,customer_name,customer_phone,device_model,balance_cents,next_due_date,status,enrolled_at&order=created_at.desc&limit=250",jwt)
        val payments=Cloud.rows(
            "finance_contract_claims?select=id,eft_reference,amount_cents,submitted_at&status=eq.pending&order=submitted_at.asc&limit=150",jwt)
        val counts=Cloud.rpc("finance_admin_dashboard",JSONObject(),jwt)
        val rows=(0 until finance.length()).mapNotNull { i ->
            finance.optJSONObject(i)?.let { c ->
                ContractRow(c.optString("id"),c.optString("customer_name"),
                    c.optString("customer_phone"),c.optString("device_model"),
                    c.optLong("balance_cents"),c.optString("next_due_date",""),
                    c.optString("status"),!c.isNull("enrolled_at"))
            }
        }
        val pending=(0 until payments.length()).mapNotNull { i ->
            payments.optJSONObject(i)?.let { p ->
                EftRow(p.optString("id"),p.optString("eft_reference"),
                    p.optLong("amount_cents"),p.optString("submitted_at"))
            }
        }
        return Snapshot(rows,pending,counts)
    }
    private fun applyDashboard(result:Snapshot) {
        contracts=result.contracts
        claims=result.claims
        dashboard=result.metrics
    }
    private fun refresh() {
        val jwt=accessToken?:run {message="Administrator sign-in required.";return}
        work {
            val result=readDashboard(jwt)
            runOnUiThread { applyDashboard(result) }
            "Business information refreshed from Supabase."
        }
    }

    private fun register(
        customerName:String,customerPhone:String,deviceModel:String,
        price:String,deposit:String,installment:String,firstDue:String
    ) {
        val jwt=accessToken?:return
        work {
            val priceCents=asCents(price)
            val depositCents=asCents(deposit)
            val instalmentCents=asCents(installment)
            require(priceCents>0 && depositCents>=0 && depositCents<priceCents && instalmentCents>0) {
                "Enter a valid price, deposit and instalment"
            }
            LocalDate.parse(firstDue)
            require(customerName.trim().length>=2 && customerPhone.trim().length>=7 && deviceModel.trim().length>=2)
            val args=JSONObject()
                .put("p_customer_name",customerName.trim()).put("p_customer_phone",customerPhone.trim())
                .put("p_device_model",deviceModel.trim()).put("p_price_cents",priceCents)
                .put("p_deposit_cents",depositCents).put("p_installment_cents",instalmentCents)
                .put("p_first_due_date",firstDue)
            val created=Cloud.rpc("finance_register_contract",args,jwt)
            val code=created.getString("enrollment_code")
            val data=readDashboard(jwt)
            runOnUiThread {
                enrollmentCode=code
                applyDashboard(data)
                page="Enrolment"
            }
            "Contract created. The one-use setup code is ready."
        }
    }
    private fun showQr() {
        val code=enrollmentCode?:run {message="Register a finance contract first.";return}
        work {
            val image=AdminEnrollmentQr.create(code)
            runOnUiThread {qrBitmap=image}
            "Android setup QR prepared. Use only on a new or reset supported phone."
        }
    }
    private fun decidePayment(claim:EftRow,approved:Boolean) {
        val jwt=accessToken?:return
        work {
            Cloud.rpc("finance_decide_contract_claim",
                JSONObject().put("p_claim_id",claim.id).put("p_approve",approved),jwt)
            val result=readDashboard(jwt)
            runOnUiThread { applyDashboard(result) }
            if(approved) "Verified EFT approved in the live ledger."
            else "EFT claim rejected."
        }
    }
    private fun saveBank(bank:String,holder:String,number:String,branch:String,note:String) {
        val jwt=accessToken?:return
        if(bank.isBlank()||holder.isBlank()||number.isBlank()) {
            message="Complete the verified bank account details.";return
        }
        work {
            Cloud.rpc("finance_set_payment_details",
                JSONObject().put("p_bank",bank.trim()).put("p_holder",holder.trim())
                    .put("p_account",number.trim()).put("p_branch",branch.trim())
                    .put("p_instructions",note.trim()).put("p_active",true),jwt)
            "Verified EFT beneficiary saved."
        }
    }
    @Composable private fun App() {
        Scaffold(containerColor=MbPalette.Canvas,
            bottomBar={
                if(authenticated) MbBottomNav(
                    listOf("Overview","Clients","Payments","More"),
                    if(page in listOf("Overview","Clients","Payments"))page else "More"
                ) {page=it}
            }) { scaffoldPadding ->
            Column(modifier=Modifier.fillMaxSize().padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .padding(start=22.dp,end=22.dp,bottom=24.dp)) {
                MbBrand(if(authenticated)"Finance office" else "Secure access")
                if(!authenticated) SignIn() else {
                    when(page) {
                        "Overview" -> Overview()
                        "Clients" -> Clients()
                        "Payments" -> Payments()
                        "More" -> More()
                        "New contract" -> NewContract()
                        "Enrolment" -> Enrolment()
                        "Devices" -> Devices()
                        "Bank settings" -> BankSettings()
                        "Reports" -> Reports()
                        else -> Overview()
                    }
                }
                Spacer(Modifier.height(16.dp))
                MbNotice(message)
                if(working) LinearProgressIndicator(
                    modifier=Modifier.fillMaxWidth().padding(top=8.dp),
                    color=MbPalette.Green,trackColor=MbPalette.Line)
            }
        }
        decision?.let { item ->
            val claim=item.first
            val approve=item.second
            AlertDialog(onDismissRequest={decision=null},
                title={Text(if(approve)"Verify bank receipt" else "Reject claim")},
                text={Text(if(approve)
                    "Approve R "+rand(claim.amount)+" with reference "+claim.reference+
                    " only after confirming the funds arrived in your own bank account."
                    else "Reject the claim for R "+rand(claim.amount)+"?")},
                confirmButton={TextButton(onClick={
                    decision=null
                    decidePayment(claim,approve)
                }) {Text(if(approve)"I verified the money" else "Reject claim")}},
                dismissButton={TextButton(onClick={decision=null}) {Text("Cancel")}}
            )
        }
        qrBitmap?.let { image ->
            AlertDialog(onDismissRequest={qrBitmap=null},
                title={Text("Device setup QR")},
                text={Column {
                    Image(bitmap=image.asImageBitmap(),contentDescription="Unique Android provisioning QR",
                        modifier=Modifier.fillMaxWidth().aspectRatio(1f))
                    Text("Use a new or reset compatible Android phone. Read the management consent before enrolling.",
                        fontSize=12.sp,color=MbPalette.Subtle)
                }},
                confirmButton={TextButton(onClick={qrBitmap=null}) {Text("Close")}})
        }
    }
    @Composable private fun SignIn() {
        var email by rememberSaveable {mutableStateOf("")}
        var password by rememberSaveable {mutableStateOf("")}
        MbTitle("Business access","Only authorised MBMF administrators can access finance records.")
        Spacer(Modifier.height(14.dp))
        MbCard {
            MbField(email,{email=it},"Admin email")
            Spacer(Modifier.height(12.dp))
            MbField(password,{password=it},"Password",password=true)
            Spacer(Modifier.height(18.dp))
            MbButton("Sign in to MBMF",!working){signIn(email,password)}
        }
        Spacer(Modifier.height(12.dp))
        MbButton("Create administrator login",!working,secondary=true){createLogin(email,password)}
        MbSection("Account security")
        Text("Creating a login does not grant access. Administrator rights are assigned separately.",
            fontSize=13.sp,color=MbPalette.Subtle)
    }
    @Composable private fun Overview() {
        MbTitle("Business overview","Financed phones, customers and payment verification.")
        MbMoneyCard("Waiting for EFT confirmation",
            dashboard.optLong("pending_eft").toString()+" claims",
            "Every transfer must be verified in your bank before approval.")
        Spacer(Modifier.height(15.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            MbMetric("Customers",dashboard.optLong("customers").toString(),Modifier.weight(1f))
            MbMetric("Active finance",dashboard.optLong("active").toString(),Modifier.weight(1f))
        }
        Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            MbMetric("Paid in full",dashboard.optLong("settled").toString(),Modifier.weight(1f))
            MbMetric("Awaiting setup",dashboard.optLong("not_enrolled").toString(),Modifier.weight(1f))
        }
        MbSection("Work today")
        MbCard {
            MbLink("Register a customer","Create a genuine finance agreement"){page="New contract"}
            MbLink("Review EFT claims",claims.size.toString()+" pending claims"){page="Payments"}
            MbLink("Manage financed devices","Check actual enrolment state"){page="Devices"}
        }
        MbSection("Recent customers")
        if(contracts.isEmpty()) Text("No contracts registered yet.",color=MbPalette.Subtle)
        contracts.take(4).forEach { c ->
            MbLink(c.name,c.model+" · R "+rand(c.balance)+" outstanding"){page="Clients"}
        }
        Spacer(Modifier.height(12.dp))
        MbButton("Refresh live information",!working,secondary=true){refresh()}
    }
    @Composable private fun Clients() {
        MbTitle("Customers","Real records from the secure finance database.")
        MbButton("Add a customer and financed phone",!working){page="New contract"}
        MbSection("Registered contracts")
        if(contracts.isEmpty()) MbNotice("No customers have been registered.")
        contracts.forEach { c ->
            MbCard {
                Text(c.name,fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=MbPalette.Ink)
                MbLine("Phone",c.model)
                MbLine("Mobile",c.phone)
                MbLine("Outstanding","R "+rand(c.balance))
                MbLine("Next payment",if(c.status=="settled")"Settled" else c.due)
                MbLine("Status",c.status.uppercase())
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    @Composable private fun Payments() {
        MbTitle("Payment review","Every EFT claim requires independent bank verification.")
        if(claims.isEmpty()) MbNotice("No pending EFT claims.")
        claims.forEach { c ->
            MbCard {
                Text("R "+rand(c.amount),fontSize=26.sp,fontWeight=FontWeight.SemiBold,
                    color=MbPalette.Ink)
                MbLine("Reference",c.reference)
                MbLine("Submitted",c.submitted.take(16).replace('T',' '))
                Spacer(Modifier.height(12.dp))
                MbButton("Approve verified receipt",!working){decision=c to true}
                Spacer(Modifier.height(8.dp))
                MbButton("Reject claim",!working,secondary=true){decision=c to false}
            }
            Spacer(Modifier.height(12.dp))
        }
        MbButton("Refresh pending payments",!working,secondary=true){refresh()}
    }
    @Composable private fun More() {
        MbTitle("Business tools","Set up contracts, view devices and manage verified account details.")
        MbCard {
            MbLink("New finance contract","Admin-only customer registration"){page="New contract"}
            MbLink("Device enrolment","One-use Android initial-setup QR"){page="Enrolment"}
            MbLink("Financed devices","Enrolment states from actual records"){page="Devices"}
            MbLink("Bank payment details","Verified EFT beneficiary information"){page="Bank settings"}
            MbLink("Reports","Live business totals"){page="Reports"}
            MbLink("Refresh all","Synchronise from Supabase"){refresh()}
            MbLink("Sign out","Close your administrator session"){
                accessToken=null;authenticated=false;page="Overview";dashboard=JSONObject()
                contracts=emptyList();claims=emptyList();enrollmentCode=null;message="Signed out."
            }
        }
    }
    @Composable private fun NewContract() {
        var name by rememberSaveable {mutableStateOf("")}
        var phone by rememberSaveable {mutableStateOf("")}
        var model by rememberSaveable {mutableStateOf("")}
        var price by rememberSaveable {mutableStateOf("")}
        var deposit by rememberSaveable {mutableStateOf("0.00")}
        var instalment by rememberSaveable {mutableStateOf("")}
        var due by rememberSaveable {mutableStateOf("")}
        MbTitle("New finance contract","Register a customer's phone. Customers do not sign up.")
        MbField(name,{name=it},"Customer full name")
        Spacer(Modifier.height(8.dp))
        MbField(phone,{phone=it},"Cellphone number")
        Spacer(Modifier.height(8.dp))
        MbField(model,{model=it},"Phone manufacturer and model")
        MbSection("Agreed payment terms")
        MbField(price,{price=it},"Selling price in rand")
        Spacer(Modifier.height(8.dp))
        MbField(deposit,{deposit=it},"Deposit in rand")
        Spacer(Modifier.height(8.dp))
        MbField(instalment,{instalment=it},"Instalment in rand")
        Spacer(Modifier.height(8.dp))
        MbField(due,{due=it},"First due date (YYYY-MM-DD)")
        Spacer(Modifier.height(16.dp))
        MbButton("Save contract and create QR",!working){
            register(name,phone,model,price,deposit,instalment,due)
        }
        Spacer(Modifier.height(8.dp))
        MbButton("Back to business tools",secondary=true){page="More"}
    }
    @Composable private fun Enrolment() {
        MbTitle("Android enrolment","Setup-time QR for the last customer registered in this session.")
        MbCard {
            MbLine("One-time code",if(enrollmentCode==null)"No contract selected" else "Ready")
            Text("On a compatible new or reset phone, start QR provisioning, connect to Wi-Fi, scan and read Android's management disclosure.",
                fontSize=13.sp,color=MbPalette.Subtle)
            Spacer(Modifier.height(18.dp))
            MbButton("Show this customer's QR",!working && enrollmentCode!=null){showQr()}
        }
        MbSection("Pilot limitations")
        MbNotice("The published setup APK is debug-signed. Do not enrol paying customers until actual Device Owner setup and release are hardware-tested.")
    }
    @Composable private fun Devices() {
        MbTitle("Financed phones","Live enrolment records. No simulated remote locking.")
        if(contracts.isEmpty()) MbNotice("No registered phones.")
        contracts.forEach { c ->
            MbCard {
                Text(c.model,fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=MbPalette.Ink)
                MbLine("Customer",c.name)
                MbLine("Enrolment",if(c.enrolled)"Activated" else "Awaiting setup")
                MbLine("Finance",c.status.uppercase())
                MbLine("Balance","R "+rand(c.balance))
            }
            Spacer(Modifier.height(10.dp))
        }
        MbNotice("Remote enforcement and permanent unmanagement require supported hardware and testing.")
        Spacer(Modifier.height(10.dp))
        MbButton("Refresh devices",!working,secondary=true){refresh()}
    }
    @Composable private fun Reports() {
        MbTitle("Portfolio figures","Database totals, not generated sample customers.")
        MbCard {
            MbLine("Customers",dashboard.optLong("customers").toString())
            MbLine("Active financed phones",dashboard.optLong("active").toString())
            MbLine("Pending EFT verification",dashboard.optLong("pending_eft").toString())
            MbLine("Settled contracts",dashboard.optLong("settled").toString())
            MbLine("Awaiting enrolment",dashboard.optLong("not_enrolled").toString())
        }
        Spacer(Modifier.height(16.dp))
        MbButton("Refresh reports",!working,secondary=true){refresh()}
    }
    @Composable private fun BankSettings() {
        var bank by rememberSaveable {mutableStateOf("")}
        var holder by rememberSaveable {mutableStateOf("")}
        var number by rememberSaveable {mutableStateOf("")}
        var branch by rememberSaveable {mutableStateOf("")}
        var instructions by rememberSaveable {mutableStateOf("")}
        var confirmed by remember {mutableStateOf(false)}
        MbTitle("EFT beneficiary","Only verified account details should reach financed customers.")
        MbField(bank,{bank=it},"Bank name")
        Spacer(Modifier.height(8.dp))
        MbField(holder,{holder=it},"Account holder")
        Spacer(Modifier.height(8.dp))
        MbField(number,{number=it},"Account number")
        Spacer(Modifier.height(8.dp))
        MbField(branch,{branch=it},"Branch code (if required)")
        Spacer(Modifier.height(8.dp))
        MbField(instructions,{instructions=it},"Payment instructions")
        Spacer(Modifier.height(16.dp))
        MbButton("Review bank details",!working){confirmed=true}
        if(confirmed) {
            AlertDialog(onDismissRequest={confirmed=false},
                title={Text("Verify the banking beneficiary")},
                text={Text("Confirm these details in your own banking app. Never guess the account number. Publish to enrolled devices?")},
                confirmButton={TextButton(onClick={
                    confirmed=false;saveBank(bank,holder,number,branch,instructions)
                }) {Text("Publish verified details")}},
                dismissButton={TextButton(onClick={confirmed=false}) {Text("Cancel")}})
        }
    }
    private fun asCents(raw:String):Long = BigDecimal(raw.trim()).movePointRight(2).longValueExact()
    private fun rand(value:Long):String = BigDecimal.valueOf(value,2).toPlainString()
}
