package za.co.bacyflex.customer

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Normal first-launch education after installation.
 *
 * Device Owner can ONLY be granted by supported Android initial-setup provisioning.
 * ACTION_ADD_DEVICE_ADMIN is an explicit, optional and lower-privilege OS request;
 * the UI never claims that it is equivalent to a device owner enrolment.
 */
class SetupWizardActivity : ComponentActivity() {
    private var statusRevision by mutableIntStateOf(0)
    private val dpm get() = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ClientSetupUi.showWizard(
            activity = this,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            deviceStatus = { currentStatus() },
            refreshVersion = { statusRevision },
            onRequestAdmin = { requestDeviceAdmin() },
            onFinish = {
                getSharedPreferences("mbmf_setup",MODE_PRIVATE)
                    .edit().putBoolean("finished",true).apply()
                finish()
            }
        )
    }

    override fun onResume() {
        super.onResume()
        statusRevision += 1
    }

    private fun currentStatus(): SetupAccess {
        val receiver=ComponentName(this,FinanceAdminReceiver::class.java)
        return when {
            dpm.isDeviceOwnerApp(packageName) -> SetupAccess.DEVICE_OWNER
            dpm.isAdminActive(receiver) -> SetupAccess.DEVICE_ADMIN
            else -> SetupAccess.NONE
        }
    }

    private fun requestDeviceAdmin() {
        if (currentStatus()==SetupAccess.DEVICE_OWNER) return
        val receiver=ComponentName(this,FinanceAdminReceiver::class.java)
        val intent=Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, receiver)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "MBMF requests limited Android device administration for a disclosed phone-finance pilot. " +
                "It may allow the supported screen-lock policy shown by Android. " +
                "You can decline. This does NOT grant Device Owner access or activate a loan contract."
            )
        }
        try {
            startActivity(intent)
        } catch (ex: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(
                this, "This Android device does not support the administrator request.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}

enum class SetupAccess { NONE, DEVICE_ADMIN, DEVICE_OWNER }
