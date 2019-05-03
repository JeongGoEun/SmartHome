package io.smarthome.mesh.setup.flow.setupsteps

import androidx.annotation.WorkerThread
import io.smarthome.android.sdk.cloud.ParticleCloud
import io.smarthome.mesh.setup.flow.MeshSetupStep
import io.smarthome.mesh.setup.flow.Scopes
import io.smarthome.mesh.setup.flow.context.SetupContexts
import io.smarthome.mesh.setup.flow.modules.FlowUiDelegate
import mu.KotlinLogging


class StepCheckIfTargetDeviceShouldBeClaimed(
    private val cloud: ParticleCloud,
    private val flowUi: FlowUiDelegate
) : MeshSetupStep() {

    private val log = KotlinLogging.logger {}

    @WorkerThread
    override suspend fun doRunStep(ctxs: SetupContexts, scopes: Scopes) {
        if (ctxs.mesh.targetJoinedSuccessfully) {
            log.info { "Skipping because we've already joined the mesh successfully" }
            return
        }

        if (ctxs.ble.targetDevice.shouldBeClaimed == null) {
            checkTargetDeviceIsClaimed(ctxs, scopes)
        } else if (ctxs.ble.targetDevice.shouldBeClaimed == true && ctxs.cloud.claimCode == null) {
            fetchClaimCode(ctxs)
        }
    }

    private suspend fun checkTargetDeviceIsClaimed(ctxs: SetupContexts, scopes: Scopes) {
        log.info { "checkTargetDeviceIsClaimed()" }

        val targetDeviceId = ctxs.ble.targetDevice.deviceId!!
        val userOwnsDevice = cloud.userOwnsDevice(targetDeviceId)
        log.info { "User owns device?: $userOwnsDevice" }
        if (userOwnsDevice) {
            val device = cloud.getDevice(targetDeviceId)
            ctxs.ble.targetDevice.currentDeviceName = device.name
            ctxs.ble.targetDevice.updateIsClaimed(true)
            ctxs.ble.targetDevice.shouldBeClaimed = false
            return
        }

        ctxs.ble.targetDevice.updateIsClaimed(false)
        ctxs.ble.targetDevice.shouldBeClaimed = true
        // run through step again...
        doRunStep(ctxs, scopes)
    }

    private fun fetchClaimCode(ctxs: SetupContexts) {
        if (ctxs.cloud.claimCode == null) {
            log.info { "Fetching new claim code" }
            try {
                flowUi.showGlobalProgressSpinner(true)
                ctxs.cloud.claimCode = cloud.generateClaimCode().claimCode
            } finally {
                flowUi.showGlobalProgressSpinner(true)
            }
        }
    }

}