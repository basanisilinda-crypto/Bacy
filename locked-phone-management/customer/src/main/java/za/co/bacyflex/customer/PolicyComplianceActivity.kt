package za.co.bacyflex.customer

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Consent gate invoked by Android QR provisioning; it cannot grant Device Owner itself. */
class PolicyComplianceActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(25,56,59)
        setContent {SetupTheme{
            var accepted by remember {mutableStateOf(false)}
            Column(Modifier.fillMaxSize().background(SetupStyle.bg)
                .verticalScroll(rememberScrollState()).padding(24.dp)) {
                SetupHeader(3,"One last thing: your consent.",
                    "Android is setting up this financed phone for management. Please review what that means.")
                SetupTerms(accepted){accepted=it}
                Spacer(Modifier.height(22.dp))
                SetupButton("Agree and continue",enabled=accepted){
                    val codeValid=EnrollmentExtras.capture(this@PolicyComplianceActivity,intent)
                    setResult(if(codeValid)Activity.RESULT_OK else Activity.RESULT_CANCELED)
                    finish()
                }
                Spacer(Modifier.height(10.dp))
                SetupButton("Decline enrolment",secondary=true){
                    DeviceSession.clear(this@PolicyComplianceActivity,"pending_enrollment")
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
                Spacer(Modifier.height(18.dp))
                SetupBanner("Only Android can complete Device Owner provisioning. MBMF will verify that state before activating any finance contract.")
            }
        }}
    }
    @Deprecated("Deprecated in Android framework")
    override fun onBackPressed() {
        DeviceSession.clear(this,"pending_enrollment")
        setResult(Activity.RESULT_CANCELED)
        super.onBackPressed()
    }
}
