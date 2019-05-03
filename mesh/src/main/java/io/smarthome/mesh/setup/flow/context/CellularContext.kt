package io.smarthome.mesh.setup.flow.context

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.smarthome.mesh.common.android.livedata.castAndPost
import io.smarthome.mesh.common.android.livedata.castAndSetOnMainThread
import io.smarthome.mesh.common.logged
import io.smarthome.mesh.setup.flow.Clearable
import mu.KotlinLogging


class CellularContext : Clearable {

    private val log = KotlinLogging.logger {}

    val targetDeviceIccid: LiveData<String?> = MutableLiveData()
    val isSimActivatedLD: LiveData<Boolean?> = MutableLiveData()

    var connectingToCloudUiShown by log.logged(false)

    override fun clearState() {
        log.info { "clearState()" }
        targetDeviceIccid.castAndSetOnMainThread(null)
        isSimActivatedLD.castAndSetOnMainThread(null)
        connectingToCloudUiShown = false
    }

    fun updateTargetDeviceIccid(iccid: String) {
        log.info { "updateTargetDeviceIccid(): $iccid" }
        targetDeviceIccid.castAndPost(iccid)
    }

    fun updateIsSimActivated(isActivated: Boolean) {
        log.info { "updateIsSimActivated(): $isActivated" }
        isSimActivatedLD.castAndPost(isActivated)
    }

}