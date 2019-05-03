package io.smarthome.mesh.setup.flow

import android.app.Application
import androidx.annotation.StringRes
import com.squareup.okhttp.OkHttpClient
import io.smarthome.android.sdk.cloud.ParticleCloud
import io.smarthome.mesh.bluetooth.connecting.BluetoothConnectionManager
import io.smarthome.mesh.common.QATool
import io.smarthome.mesh.common.android.livedata.awaitUpdate
import io.smarthome.mesh.common.android.livedata.nonNull
import io.smarthome.mesh.common.android.livedata.runBlockOnUiThreadAndAwaitUpdate
import io.smarthome.mesh.ota.FirmwareUpdateManager
import io.smarthome.mesh.setup.connection.ProtocolTransceiverFactory
import io.smarthome.mesh.setup.connection.security.SecurityManager
import io.smarthome.mesh.setup.flow.ExceptionType.ERROR_FATAL
import io.smarthome.mesh.setup.flow.ExceptionType.EXPECTED_FLOW
import io.smarthome.mesh.setup.flow.FlowType.CELLULAR_FLOW
import io.smarthome.mesh.setup.flow.FlowType.ETHERNET_FLOW
import io.smarthome.mesh.setup.flow.FlowType.INTERNET_CONNECTED_PREFLOW
import io.smarthome.mesh.setup.flow.FlowType.JOINER_FLOW
import io.smarthome.mesh.setup.flow.FlowType.NETWORK_CREATOR_POSTFLOW
import io.smarthome.mesh.setup.flow.FlowType.PREFLOW
import io.smarthome.mesh.setup.flow.FlowType.SINGLE_TASK_POSTFLOW
import io.smarthome.mesh.setup.flow.FlowType.STANDALONE_POSTFLOW
import io.smarthome.mesh.setup.flow.FlowType.WIFI_FLOW
import io.smarthome.mesh.setup.flow.context.SetupContexts
import io.smarthome.mesh.setup.flow.modules.FlowRunnerUiResponseReceiver
import io.smarthome.mesh.setup.flow.modules.FlowUiDelegate
import io.smarthome.mesh.setup.flow.setupsteps.*
import io.smarthome.mesh.setup.ui.BarcodeData.CompleteBarcodeData
import io.smarthome.mesh.setup.ui.DialogSpec.StringDialogSpec
import io.smarthome.mesh.setup.ui.MeshFlowTerminator
import kotlinx.coroutines.delay
import mu.KotlinLogging


private const val FLOW_RETRIES = 10


fun buildFlowManager(
    app: Application,
    cloud: ParticleCloud,
    dialogTool: DialogTool,
    flowUi: FlowUiDelegate,
    okHttpClient: OkHttpClient = OkHttpClient(),
    securityManager: SecurityManager = SecurityManager()
): MeshSetupFlowRunner {
    val btConMan = BluetoothConnectionManager(app)
    val transceiverFactory = ProtocolTransceiverFactory(securityManager)
    val deviceConnector = DeviceConnector(cloud, btConMan, transceiverFactory)

    val fwUpdateManager = FirmwareUpdateManager(cloud, okHttpClient)

    val deps = StepDeps(
        cloud,
        deviceConnector,
        fwUpdateManager,
        dialogTool,
        flowUi
    )

    return MeshSetupFlowRunner(deps, app)
}



class StepDeps(
    val cloud: ParticleCloud,
    val deviceConnector: DeviceConnector,
    val firmwareUpdateManager: FirmwareUpdateManager,
    val dialogTool: DialogTool,
    val flowUi: FlowUiDelegate
)



enum class FlowIntent {
    FIRST_TIME_SETUP,
    SINGLE_TASK_FLOW
}


enum class FlowType {
    PREFLOW,
    JOINER_FLOW,
    INTERNET_CONNECTED_PREFLOW,
    ETHERNET_FLOW,
    WIFI_FLOW,
    CELLULAR_FLOW,
    NETWORK_CREATOR_POSTFLOW,
    STANDALONE_POSTFLOW,
    SINGLE_TASK_POSTFLOW
}


class MeshSetupFlowRunner(
    private val deps: StepDeps,
    private val everythingNeedsAContext: Application
) {

    private val log = KotlinLogging.logger {}

    var responseReceiver: FlowRunnerUiResponseReceiver? = null
    var terminator: MeshFlowTerminator? = null

    private var contexts: SetupContexts? = null

    fun startControlPanelWifiConfigFlow(deviceId: String, barcode: CompleteBarcodeData) {
        val ctxs = initNewFlow(FlowIntent.SINGLE_TASK_FLOW)

        ctxs.updateGetReadyNextButtonClicked(true)

        ctxs.ble.targetDevice.deviceId = deviceId
        ctxs.updatePricingImpactConfirmed(true)
        // FIXME: should use device name here
        ctxs.ble.targetDevice.currentDeviceName
        ctxs.singleStepCongratsMessage = "Wi-Fi credentials were successfully added to your device"

        ctxs.scopes.onWorker {
            ctxs.ble.targetDevice.updateBarcode(barcode, deps.cloud)
            ctxs.ble.targetDevice.barcode
                .nonNull(ctxs.scopes)
                .runBlockOnUiThreadAndAwaitUpdate(ctxs.scopes) {
                    // nothing to do but wait for the update
                }

            ctxs.currentFlow = listOf(
                FlowType.PREFLOW,
                FlowType.WIFI_FLOW,
                FlowType.SINGLE_TASK_POSTFLOW
            )
            runCurrentFlow()
        }
    }

    fun endSetup() {
        log.info { "endSetup()" }
        terminator?.terminateSetup()
    }

    fun getString(@StringRes stringRes: Int): String {
        return everythingNeedsAContext.getString(stringRes)
    }

    fun getString(@StringRes stringRes: Int, vararg formatArgs: String): String {
        return everythingNeedsAContext.getString(stringRes, formatArgs)
    }

    private fun initNewFlow(intent: FlowIntent): SetupContexts {
        val ctxs = SetupContexts()
        contexts = ctxs
        responseReceiver = FlowRunnerUiResponseReceiver(ctxs, deps.cloud)
        ctxs.flowIntent = intent
        return ctxs
    }

    private fun runCurrentFlow() {
        log.info { "runCurrentFlow()" }

        fun assembleSteps(ctxs: SetupContexts): List<MeshSetupStep> {
            val flow = mutableListOf(FlowType.PREFLOW) + ctxs.currentFlow
            log.info { "assembleSteps(), steps=$flow" }
            val steps = mutableListOf<MeshSetupStep>()
            for (type in ctxs.currentFlow) {
                val newSteps = getFlowSteps(type)
                steps.addAll(newSteps)
            }
            return steps
        }

        suspend fun doRunFlow(flowSteps: List<MeshSetupStep>) {
            for (step in flowSteps) {
                contexts?.let {
                    step.runStep(it, it.scopes)
                }
            }
        }

        val ctxs = contexts
        ctxs?.scopes?.onWorker {
            var error: Exception? = null

            var i = 0
            while (i < FLOW_RETRIES) {
                try {
                    val steps = assembleSteps(ctxs)
                    doRunFlow(steps)
                    log.info { "FLOW COMPLETED SUCCESSFULLY!" }
                    return@onWorker

                } catch (ex: Exception) {
                    deps.flowUi.showGlobalProgressSpinner(false)

                    if (ex is MeshSetupFlowException && ex.severity == ERROR_FATAL) {
                        log.info(ex) { "Hit fatal error, exiting setup: " }
                        QATool.log(ex.message ?: "(no message)")
                        endSetup()
                        return@onWorker
                    }

                    delay(1000)
                    QATool.report(ex)
                    error = ex

                    if (ex is MeshSetupFlowException && ex.severity == EXPECTED_FLOW) {
                        continue  // avoid incrementing the counter, since this was expected flow
                    }

                    i++
                }
            }

            // we got through all the retries and we finally failed on a specific error.
            // Quit and notify the user of the error we died on
            quitSetupfromError(ctxs.scopes, error)
        }
    }

    private suspend fun quitSetupfromError(scopes: Scopes, ex: Exception?) {
        scopes.withMain {
            deps.dialogTool.newDialogRequest(
                StringDialogSpec(
                    "Setup has encountered an error and cannot " +
                            "continue. Please exit setup and try again."
                )
            )
            deps.dialogTool.clearDialogResult()
            deps.dialogTool.dialogResultLD.nonNull().awaitUpdate(scopes)
            endSetup()
        }
    }


    private fun getFlowSteps(flowType: FlowType): List<MeshSetupStep> {

        return when(flowType) {

            PREFLOW -> listOf(
                StepGetTargetDeviceInfo(deps.flowUi),
                StepShowGetReadyForSetup(deps.flowUi),
                StepConnectToTargetDevice(deps.flowUi, deps.deviceConnector),
                StepEnsureCorrectEthernetFeatureStatus(),
                StepEnsureLatestFirmware(deps.flowUi, deps.firmwareUpdateManager),
                StepFetchDeviceId(),
                StepGetAPINetworks(deps.cloud),
                StepCheckIfTargetDeviceShouldBeClaimed(deps.cloud, deps.flowUi),
                StepEnsureTargetDeviceIsNotOnMeshNetwork(deps.cloud, deps.dialogTool),
                StepSetClaimCode(),
                StepShowTargetPairingSuccessful(deps.flowUi),
                StepDetermineFlowAfterPreflow()
            )



            JOINER_FLOW -> listOf(
                StepCollectMeshNetworkToJoinSelection(deps.flowUi),
                StepCollectCommissionerDeviceInfo(deps.flowUi),
                StepEnsureCommissionerConnected(deps.flowUi, deps.deviceConnector),
                StepEnsureCommissionerNetworkMatches(deps.flowUi, deps.cloud),
                StepCollectMeshNetworkToJoinPassword(deps.flowUi),
                StepShowJoiningMeshNetworkUi(deps.flowUi),
                StepJoinSelectedNetwork(deps.cloud),
                StepSetSetupDone(),
                StepEnsureListeningStoppedForBothDevices(),
                StepEnsureConnectionToCloud(),
                StepCheckDeviceGotClaimed(deps.cloud),
                StepSetNewDeviceName(deps.flowUi, deps.cloud),
                StepPublishDeviceSetupDoneEvent(deps.cloud),
                StepShowJoinerSetupFinishedUi(deps.flowUi)
            )


            INTERNET_CONNECTED_PREFLOW -> listOf(
                StepAwaitSetupStandAloneOrWithNetwork(deps.cloud, deps.flowUi)
            )


            ETHERNET_FLOW -> listOf(
                StepShowPricingImpact(deps.flowUi, deps.cloud),
                StepShowConnectingToDeviceCloudUi(deps.flowUi),
                StepSetSetupDone(),
                StepEnsureListeningStoppedForBothDevices(),
                StepEnsureEthernetHasIpAddress(deps.flowUi),
                StepEnsureConnectionToCloud(),
                StepCheckDeviceGotClaimed(deps.cloud),
                StepPublishDeviceSetupDoneEvent(deps.cloud)
            )


            WIFI_FLOW -> listOf(
                StepShowPricingImpact(deps.flowUi, deps.cloud),
                StepShowShouldConnectToDeviceCloudConfirmation(deps.flowUi),
                StepCollectUserWifiNetworkSelection(deps.flowUi),
                StepCollectSelectedWifiNetworkPassword(deps.flowUi),
                StepEnsureSelectedWifiNetworkJoined(deps.flowUi),
                // FIXME: this last sequence is virtually the same across setup flows -- unify them.
                StepSetSetupDone(),
                StepEnsureListeningStoppedForBothDevices(),
                StepShowWifiConnectingToDeviceCloudUi(deps.flowUi),
                StepEnsureConnectionToCloud(),
                StepCheckDeviceGotClaimed(deps.cloud),
                StepShowConnectedToCloudSuccessUi(deps.flowUi),
                StepPublishDeviceSetupDoneEvent(deps.cloud)
            )


            CELLULAR_FLOW -> listOf(
                StepEnsureCardOnFile(deps.flowUi, deps.cloud),
                StepFetchIccid(),
                StepEnsureSimActivationStatusUpdated(deps.cloud),
                StepShowPricingImpact(deps.flowUi, deps.cloud),
                StepShowShouldConnectToDeviceCloudConfirmation(deps.flowUi),
                StepShowCellularConnectingToDeviceCloudUi(deps.flowUi),
                StepEnsureSimActivated(deps.cloud),
                StepSetSetupDone(),
                StepEnsureListeningStoppedForBothDevices(),
                StepEnsureConnectionToCloud(),
                StepCheckDeviceGotClaimed(deps.cloud),
                StepShowConnectedToCloudSuccessUi(deps.flowUi),
                StepPublishDeviceSetupDoneEvent(deps.cloud)
            )


            NETWORK_CREATOR_POSTFLOW -> listOf(
                StepSetNewDeviceName(deps.flowUi, deps.cloud),
                StepGetNewMeshNetworkName(deps.flowUi),
                StepGetNewMeshNetworkPassword(deps.flowUi),
                StepShowCreateNewMeshNetworkUi(deps.flowUi),
                StepCreateNewMeshNetworkOnCloud(deps.cloud),
                StepCreateNewMeshNetworkOnLocalDevice(),
                StepEnsureConnectionToCloud(),
                StepShowCreateNetworkFinished(deps.flowUi)
            )


            STANDALONE_POSTFLOW -> listOf(
                StepSetNewDeviceName(deps.flowUi, deps.cloud)
                // StepOfferToAddOneMoreDevice()  // FIXME: add support for this
            )


            SINGLE_TASK_POSTFLOW -> listOf(
                StepShowSingleTaskCongratsScreen(deps.flowUi)
            )
        }
    }

}