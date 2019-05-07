package io.smarthome.mesh.setup.flow.setupsteps

import io.smarthome.android.sdk.cloud.ParticleCloud
import io.smarthome.mesh.R
import io.smarthome.mesh.common.android.livedata.nonNull
import io.smarthome.mesh.common.android.livedata.runBlockOnUiThreadAndAwaitUpdate
import io.smarthome.mesh.setup.flow.*
import io.smarthome.mesh.setup.flow.context.SetupContexts
import io.smarthome.mesh.setup.flow.modules.FlowUiDelegate
import io.smarthome.mesh.setup.flow.modules.meshsetup.MeshNetworkToJoin.SelectedNetwork
import io.smarthome.mesh.setup.ui.DialogSpec.ResDialogSpec
import kotlinx.coroutines.delay
import mu.KotlinLogging


class StepEnsureCommissionerNetworkMatches(
    private val flowUi: FlowUiDelegate,
    private val cloud: ParticleCloud
) : MeshSetupStep() {

    private val log = KotlinLogging.logger {}

    override suspend fun doRunStep(ctxs: SetupContexts, scopes: Scopes) {
        val commissioner = ctxs.requireCommissionerXceiver()
        val reply = commissioner.sendGetNetworkInfo().throwOnErrorOrAbsent()

        val commissionerNetwork = reply.network
        val toJoinLD = ctxs.mesh.targetDeviceMeshNetworkToJoinLD
        val toJoin = (ctxs.mesh.targetDeviceMeshNetworkToJoinLD.value!! as SelectedNetwork)

        if (commissionerNetwork?.extPanId == toJoin.networkToJoin.extPanId) {
            toJoinLD.nonNull(scopes).runBlockOnUiThreadAndAwaitUpdate(scopes) {
                // update the network to one which has the network ID
                ctxs.mesh.updateSelectedMeshNetworkToJoin(commissionerNetwork)
            }
            // update the network to be joined
            return  // it's a match; we're done.
        }

        commissioner.disconnect()
        ctxs.ble.commissioner.updateDeviceTransceiver(null)
        ctxs.ble.commissioner.updateBarcode(null, cloud)

        val result = flowUi.dialogTool.dialogResultLD
            .nonNull(scopes)
            .runBlockOnUiThreadAndAwaitUpdate(scopes) {
                flowUi.dialogTool.newDialogRequest(
                    ResDialogSpec(
                        R.string.p_manualcommissioning_commissioner_candidate_not_on_target_network,
                        android.R.string.ok
                    )
                )
            }

        log.info { "result from awaiting on 'commissioner not on network to be joined' dialog: $result" }
        flowUi.dialogTool.clearDialogResult()
        delay(500)

        throw CommissionerNetworkDoesNotMatchException()
    }

}
