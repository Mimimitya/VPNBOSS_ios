package space.vpnboss

import android.net.VpnService
import android.os.ParcelFileDescriptor

class VpnBossVpnService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null

    fun startPreparedTunnel() {
        tunnel?.close()
        tunnel = Builder()
            .setSession("VPNBOSS")
            .addAddress("10.10.10.2", 32)
            .addDnsServer("1.1.1.1")
            .addRoute("0.0.0.0", 0)
            .establish()
    }

    override fun onDestroy() {
        tunnel?.close()
        tunnel = null
        super.onDestroy()
    }
}
