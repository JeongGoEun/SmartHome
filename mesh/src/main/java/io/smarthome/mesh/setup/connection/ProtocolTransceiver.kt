package io.smarthome.mesh.setup.connection

import android.util.SparseArray
import com.google.protobuf.AbstractMessage
import com.google.protobuf.ByteString
import com.google.protobuf.GeneratedMessageV3
import io.smarthome.firmwareprotos.ctrl.Config.DeviceMode
import io.smarthome.firmwareprotos.ctrl.Config.Feature
import io.smarthome.firmwareprotos.ctrl.Config.GetDeviceIdReply
import io.smarthome.firmwareprotos.ctrl.Config.GetDeviceIdRequest
import io.smarthome.firmwareprotos.ctrl.Config.GetNcpFirmwareVersionReply
import io.smarthome.firmwareprotos.ctrl.Config.GetNcpFirmwareVersionRequest
import io.smarthome.firmwareprotos.ctrl.Config.GetSerialNumberReply
import io.smarthome.firmwareprotos.ctrl.Config.GetSerialNumberRequest
import io.smarthome.firmwareprotos.ctrl.Config.GetSystemVersionReply
import io.smarthome.firmwareprotos.ctrl.Config.GetSystemVersionRequest
import io.smarthome.firmwareprotos.ctrl.Config.SetClaimCodeReply
import io.smarthome.firmwareprotos.ctrl.Config.SetClaimCodeRequest
import io.smarthome.firmwareprotos.ctrl.Config.SetDeviceSetupDoneReply
import io.smarthome.firmwareprotos.ctrl.Config.SetDeviceSetupDoneRequest
import io.smarthome.firmwareprotos.ctrl.Config.SetFeatureReply
import io.smarthome.firmwareprotos.ctrl.Config.SetFeatureRequest
import io.smarthome.firmwareprotos.ctrl.Config.SetStartupModeReply
import io.smarthome.firmwareprotos.ctrl.Config.SetStartupModeRequest
import io.smarthome.firmwareprotos.ctrl.Config.StartListeningModeReply
import io.smarthome.firmwareprotos.ctrl.Config.StartListeningModeRequest
import io.smarthome.firmwareprotos.ctrl.Config.StopListeningModeReply
import io.smarthome.firmwareprotos.ctrl.Config.StopListeningModeRequest
import io.smarthome.firmwareprotos.ctrl.Config.SystemResetReply
import io.smarthome.firmwareprotos.ctrl.Config.SystemResetRequest
import io.smarthome.firmwareprotos.ctrl.Extensions
import io.smarthome.firmwareprotos.ctrl.Network.GetInterfaceListReply
import io.smarthome.firmwareprotos.ctrl.Network.GetInterfaceListRequest
import io.smarthome.firmwareprotos.ctrl.Network.GetInterfaceReply
import io.smarthome.firmwareprotos.ctrl.Network.GetInterfaceRequest
import io.smarthome.firmwareprotos.ctrl.Network.NetworkGetConfigurationReply
import io.smarthome.firmwareprotos.ctrl.Network.NetworkGetConfigurationRequest
import io.smarthome.firmwareprotos.ctrl.Network.NetworkGetStatusReply
import io.smarthome.firmwareprotos.ctrl.Network.NetworkGetStatusRequest
import io.smarthome.firmwareprotos.ctrl.Storage.FinishFirmwareUpdateReply
import io.smarthome.firmwareprotos.ctrl.Storage.FinishFirmwareUpdateRequest
import io.smarthome.firmwareprotos.ctrl.Storage.FirmwareUpdateDataReply
import io.smarthome.firmwareprotos.ctrl.Storage.FirmwareUpdateDataRequest
import io.smarthome.firmwareprotos.ctrl.Storage.StartFirmwareUpdateReply
import io.smarthome.firmwareprotos.ctrl.Storage.StartFirmwareUpdateRequest
import io.smarthome.firmwareprotos.ctrl.cellular.Cellular.GetIccidReply
import io.smarthome.firmwareprotos.ctrl.cellular.Cellular.GetIccidRequest
import io.smarthome.firmwareprotos.ctrl.cloud.Cloud.GetConnectionStatusReply
import io.smarthome.firmwareprotos.ctrl.cloud.Cloud.GetConnectionStatusRequest
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.AddJoinerReply
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.AddJoinerRequest
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.AuthReply
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.AuthRequest
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.CreateNetworkReply
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.CreateNetworkRequest
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.GetNetworkInfoReply
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.GetNetworkInfoRequest
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.JoinNetworkReply
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.JoinNetworkRequest
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.LeaveNetworkReply
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.LeaveNetworkRequest
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.NetworkInfo
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.PrepareJoinerReply
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.PrepareJoinerRequest
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.ScanNetworksReply
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.ScanNetworksRequest
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.StartCommissionerReply
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.StartCommissionerRequest
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.StopCommissionerReply
import io.smarthome.firmwareprotos.ctrl.mesh.Mesh.StopCommissionerRequest
import io.smarthome.firmwareprotos.ctrl.wifi.WifiNew
import io.smarthome.firmwareprotos.ctrl.wifi.WifiNew.Credentials
import io.smarthome.firmwareprotos.ctrl.wifi.WifiNew.CredentialsType
import io.smarthome.firmwareprotos.ctrl.wifi.WifiNew.JoinNewNetworkReply
import io.smarthome.firmwareprotos.ctrl.wifi.WifiNew.JoinNewNetworkRequest
import io.smarthome.firmwareprotos.ctrl.wifi.WifiNew.Security
import io.smarthome.mesh.bluetooth.connecting.BluetoothConnection
import io.smarthome.mesh.bluetooth.connecting.ConnectionPriority
import io.smarthome.mesh.common.QATool
import io.smarthome.mesh.common.Result
import io.smarthome.android.sdk.utils.UnknownEnumIntValueException
import io.smarthome.android.sdk.utils.buildIntValueMap
import io.smarthome.firmwareprotos.ctrl.Config.GetFeatureReply
import io.smarthome.firmwareprotos.ctrl.Config.GetFeatureRequest
import io.smarthome.mesh.setup.connection.ResultCode.Companion.toResultCode
import io.smarthome.mesh.setup.flow.Scopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


private const val DEFAULT_NETWORK_CHANNEL = 11


class ProtocolTransceiver internal constructor(
    private val requestWriter: RequestWriter,
    private val connection: BluetoothConnection,
    private val scopes: Scopes,
    private val connectionName: String
) {

    private val log = KotlinLogging.logger {}
    private val requestCallbacks = SparseArray<(DeviceResponse?) -> Unit>()
    private var didDisconnect = false

    val isConnected: Boolean
        get() = if (didDisconnect) false else connection.isConnected

    val bleBroadcastName: String
        get() = connection.deviceName

    fun disconnect() {
        didDisconnect = true
        GlobalScope.launch(Dispatchers.Default) {
            launch(Dispatchers.Main) { connection.disconnect() }
        }
    }

    fun setConnectionPriority(priority: ConnectionPriority) {
        connection.setConnectionPriority(priority)
    }

    suspend fun sendGetIccId(): Result<GetIccidReply, ResultCode> {
        val response = sendRequest(GetIccidRequest.newBuilder().build())
        return buildResult(response) { r -> GetIccidReply.parseFrom(r.payloadData) }
    }

    suspend fun sendStartupMode(mode: DeviceMode): Result<SetStartupModeReply, ResultCode> {
        val response = sendRequest(
            SetStartupModeRequest.newBuilder()
                .setMode(mode)
                .build()
        )
        return buildResult(response) { r -> SetStartupModeReply.parseFrom(r.payloadData) }
    }

    suspend fun sendReset(): Result<SystemResetReply, ResultCode> {
        val response = sendRequest(SystemResetRequest.newBuilder().build())
        return buildResult(response) { r -> SystemResetReply.parseFrom(r.payloadData) }
    }

    suspend fun sendGetFeature(feature: Feature): Result<GetFeatureReply, ResultCode> {
        val response = sendRequest(
            GetFeatureRequest.newBuilder()
                .setFeature(feature)
                .build()
        )
        return buildResult(response) { r -> GetFeatureReply.parseFrom(r.payloadData) }
    }

    suspend fun sendSetFeature(
        feature: Feature,
        enabled: Boolean
    ): Result<SetFeatureReply, ResultCode> {
        val response = sendRequest(
            SetFeatureRequest.newBuilder()
                .setFeature(feature)
                .setEnabled(enabled)
                .build()
        )
        return buildResult(response) { r -> SetFeatureReply.parseFrom(r.payloadData) }
    }

    suspend fun sendJoinNewNetwork(
        network: WifiNew.ScanNetworksReply.Network,
        password: String? = null
    ): Result<JoinNewNetworkReply, ResultCode> {

        val credentials = if (network.security == Security.NO_SECURITY) {
            Credentials.newBuilder()
                .setType(CredentialsType.NO_CREDENTIALS)
                .build()
        } else {
            Credentials.newBuilder()
                .setType(CredentialsType.PASSWORD)
                .setPassword(password)
                .build()
        }

        val response = sendRequest(
            JoinNewNetworkRequest.newBuilder()
                .setSsid(network.ssid)
                .setSecurity(network.security)
                .setCredentials(credentials)
                .build()
        )

        return buildResult(response) { r -> JoinNewNetworkReply.parseFrom(r.payloadData) }
    }

    suspend fun sendScanWifiNetworks(): Result<WifiNew.ScanNetworksReply, ResultCode> {
        val response = sendRequest(WifiNew.ScanNetworksRequest.newBuilder().build())
        return buildResult(response) { r -> WifiNew.ScanNetworksReply.parseFrom(r.payloadData) }
    }

    suspend fun sendGetNcpFirmwareVersion(): Result<GetNcpFirmwareVersionReply, ResultCode> {
        val response = sendRequest(GetNcpFirmwareVersionRequest.newBuilder().build())
        return buildResult(response) { r -> GetNcpFirmwareVersionReply.parseFrom(r.payloadData) }
    }

    suspend fun sendGetSystemFirmwareVersion(): Result<GetSystemVersionReply, ResultCode> {
        val response = sendRequest(GetSystemVersionRequest.newBuilder().build())
        return buildResult(response) { r -> GetSystemVersionReply.parseFrom(r.payloadData) }
    }

    suspend fun sendGetConnectionStatus(): Result<GetConnectionStatusReply, ResultCode> {
        val response = sendRequest(GetConnectionStatusRequest.newBuilder().build())
        return buildResult(response) { r -> GetConnectionStatusReply.parseFrom(r.payloadData) }
    }

    suspend fun sendNetworkGetStatus(interfaceIndex: Int): Result<NetworkGetStatusReply, ResultCode> {
        val response = sendRequest(
            NetworkGetStatusRequest.newBuilder()
                .setInterface(interfaceIndex)
                .build()
        )
        return buildResult(response) { r -> NetworkGetStatusReply.parseFrom(r.payloadData) }
    }

    suspend fun sendNetworkGetConfig(interfaceIndex: Int): Result<NetworkGetConfigurationReply, ResultCode> {
        val response = sendRequest(
            NetworkGetConfigurationRequest.newBuilder()
                .setInterface(interfaceIndex)
                .build()
        )
        return buildResult(response) { r -> NetworkGetConfigurationReply.parseFrom(r.payloadData) }
    }

    suspend fun sendGetInterface(interfaceIndex: Int): Result<GetInterfaceReply, ResultCode> {
        val response = sendRequest(
            GetInterfaceRequest.newBuilder()
                .setIndex(interfaceIndex)
                .build()
        )
        return buildResult(response) { r -> GetInterfaceReply.parseFrom(r.payloadData) }
    }

    suspend fun sendGetInterfaceList(): Result<GetInterfaceListReply, ResultCode> {
        val response = sendRequest(GetInterfaceListRequest.newBuilder().build())
        return buildResult(response) { r -> GetInterfaceListReply.parseFrom(r.payloadData) }
    }

    suspend fun sendStartListeningMode(): Result<StartListeningModeReply, ResultCode> {
        val response = sendRequest(StartListeningModeRequest.newBuilder().build())
        return buildResult(response) { r -> StartListeningModeReply.parseFrom(r.payloadData) }
    }

    suspend fun sendStopListeningMode(): Result<StopListeningModeReply, ResultCode> {
        val response = sendRequest(StopListeningModeRequest.newBuilder().build())
        return buildResult(response) { r -> StopListeningModeReply.parseFrom(r.payloadData) }
    }

    suspend fun sendSetDeviceSetupDone(done: Boolean): Result<SetDeviceSetupDoneReply, ResultCode> {
        val response = sendRequest(
            SetDeviceSetupDoneRequest.newBuilder()
                .setDone(done)
                .build()
        )
        return buildResult(response) { r -> SetDeviceSetupDoneReply.parseFrom(r.payloadData) }
    }

    suspend fun sendStartFirmwareUpdate(
        firmwareSizeBytes: Int
    ): Result<StartFirmwareUpdateReply, ResultCode> {
        val response = sendRequest(
            StartFirmwareUpdateRequest.newBuilder()
                .setSize(firmwareSizeBytes)
                .build()
        )
        return buildResult(response) { r -> StartFirmwareUpdateReply.parseFrom(r.payloadData) }
    }

    suspend fun sendFirmwareUpdateData(chunk: ByteArray): Result<FirmwareUpdateDataReply, ResultCode> {
        val response = sendRequest(
            FirmwareUpdateDataRequest.newBuilder()
                .setData(ByteString.copyFrom(chunk))
                .build()
        )
        return buildResult(response) { r -> FirmwareUpdateDataReply.parseFrom(r.payloadData) }
    }

    suspend fun sendFinishFirmwareUpdate(
        validateOnly: Boolean
    ): Result<FinishFirmwareUpdateReply, ResultCode> {
        val response = sendRequest(
            FinishFirmwareUpdateRequest.newBuilder()
                .setValidateOnly(validateOnly)
                .build()
        )
        return buildResult(response) { r -> FinishFirmwareUpdateReply.parseFrom(r.payloadData) }
    }

    suspend fun sendGetNetworkInfo(): Result<GetNetworkInfoReply, ResultCode> {
        val response = sendRequest(
            GetNetworkInfoRequest.newBuilder()
                .build()
        )
        return buildResult(response) { r -> GetNetworkInfoReply.parseFrom(r.payloadData) }
    }

    suspend fun sendCreateNetwork(
        name: String,
        password: String,
        networkId: String,
        channel: Int = DEFAULT_NETWORK_CHANNEL
    ): Result<CreateNetworkReply, ResultCode> {
        val response = sendRequest(
            CreateNetworkRequest.newBuilder()
                .setName(name)
                .setPassword(password)
                .setChannel(channel)
                .setNetworkId(networkId)
                .build()
        )
        return buildResult(response) { r -> CreateNetworkReply.parseFrom(r.payloadData) }
    }

    suspend fun sendAuth(commissionerCredential: String): Result<AuthReply, ResultCode> {
        val response = sendRequest(
            AuthRequest.newBuilder()
                .setPassword(commissionerCredential)
                .build()
        )
        return buildResult(response) { r -> AuthReply.parseFrom(r.payloadData) }
    }

    suspend fun sendStartCommissioner(): Result<StartCommissionerReply, ResultCode> {
        val response = sendRequest(StartCommissionerRequest.newBuilder().build())
        return buildResult(response) { r -> StartCommissionerReply.parseFrom(r.payloadData) }
    }

    suspend fun sendPrepareJoiner(network: NetworkInfo): Result<PrepareJoinerReply, ResultCode> {
        val response = sendRequest(
            PrepareJoinerRequest.newBuilder()
                .setNetwork(network)
                .build()
        )
        return buildResult(response) { r -> PrepareJoinerReply.parseFrom(r.payloadData) }
    }

    suspend fun sendAddJoiner(
        eui64: String,
        joiningCredential: String
    ): Result<AddJoinerReply, ResultCode> {
        val response = sendRequest(
            AddJoinerRequest.newBuilder()
                .setEui64(eui64)
                .setPassword(joiningCredential)
                .build()
        )
        return buildResult(response) { r -> AddJoinerReply.parseFrom(r.payloadData) }
    }

    // NOTE: yes, 60 seconds is a CRAZY timeout, but... this is how long it takes to receive
    // a response sometimes.
    suspend fun sendJoinNetwork(): Result<JoinNetworkReply, ResultCode> {
        val response = sendRequest(
            JoinNetworkRequest.newBuilder().build()
        )
        return buildResult(response) { r -> JoinNetworkReply.parseFrom(r.payloadData) }
    }

    suspend fun sendStopCommissioner(): Result<StopCommissionerReply, ResultCode> {
        val response = sendRequest(
            StopCommissionerRequest.newBuilder().build()
        )
        return buildResult(response) { r -> StopCommissionerReply.parseFrom(r.payloadData) }
    }

    suspend fun sendLeaveNetwork(): Result<LeaveNetworkReply, ResultCode> {
        val response = sendRequest(LeaveNetworkRequest.newBuilder().build())
        return buildResult(response) { r -> LeaveNetworkReply.parseFrom(r.payloadData) }
    }

    suspend fun sendGetDeviceId(): Result<GetDeviceIdReply, ResultCode> {
        val response = sendRequest(GetDeviceIdRequest.newBuilder().build())
        return buildResult(response) { r -> GetDeviceIdReply.parseFrom(r.payloadData) }
    }

    suspend fun sendSetClaimCode(claimCode: String): Result<SetClaimCodeReply, ResultCode> {
        val response = sendRequest(
            SetClaimCodeRequest.newBuilder()
                .setCode(claimCode)
                .build()
        )
        return buildResult(response) { r -> SetClaimCodeReply.parseFrom(r.payloadData) }
    }

    suspend fun sendScanNetworks(): Result<ScanNetworksReply, ResultCode> {
        val response = sendRequest(ScanNetworksRequest.newBuilder().build())
        return buildResult(response) { r -> ScanNetworksReply.parseFrom(r.payloadData) }
    }

    suspend fun sendGetSerialNumber(): Result<GetSerialNumberReply, ResultCode> {
        val response = sendRequest(GetSerialNumberRequest.newBuilder().build())
        return buildResult(response) { r -> GetSerialNumberReply.parseFrom(r.payloadData) }
    }

    fun receiveResponse(responseFrame: DeviceResponse) {
        val callback = requestCallbacks[responseFrame.requestId.toInt()]
        if (callback != null) {
            callback(responseFrame)
        } else {
            // FIXME: handle the timeout case here better
            QATool.report(IllegalStateException("No callbacks found for request! ID: ${responseFrame.requestId}"))
            log.error { "No callbacks found for request! ID: ${responseFrame.requestId}" }
        }
    }

    // FIXME: timeouts don't appear to be working as intended

    //region PRIVATE
    private suspend fun sendRequest(
        message: GeneratedMessageV3,
        timeout: Long = BLE_PROTO_REQUEST_TIMEOUT_MILLIS
    ): DeviceResponse? {
        val requestFrame = message.asRequest()
        log.info {
            "Sending message ${message.javaClass} to '$connectionName': '$message' " +
                    "with ID: ${requestFrame.requestId}"
        }

        if (!isConnected) {
            return null
        }

        val response = withTimeoutOrNull(timeout) {
            suspendCoroutine { continuation: Continuation<DeviceResponse?> ->
                doSendRequest(requestFrame) { continuation.resume(it) }
            }
        }


        val id = requestFrame.requestId.toInt()
        if (response == null) {
            log.warn { "Timeout reached for request $id" }
        }
        // By the time we get to here, our callback has been used up,
        // so we can remove it from the map.
        synchronized(requestCallbacks) {
            requestCallbacks.remove(requestFrame.requestId.toInt())
        }
        return response
    }

    private fun doSendRequest(
        request: DeviceRequest,
        continuationCallback: (DeviceResponse?) -> Unit
    ) {
        val requestCallback = { frame: DeviceResponse? -> continuationCallback(frame) }
        synchronized(requestCallbacks) {
            requestCallbacks.put(request.requestId.toInt(), requestCallback)
        }
        requestWriter.writeRequest(request)
    }

    private inline fun <reified V : GeneratedMessageV3> buildResult(
        response: DeviceResponse?,
        successTransformer: (DeviceResponse) -> V
    ): Result<V, ResultCode> {
        if (response == null) {
            return Result.Absent()
        }

        return if (response.resultCode == 0) {
            val transformed = successTransformer(response)
            log.info { "Successful response ${transformed::class.java}: '$transformed'" }
            Result.Present(transformed)
        } else {
            val code = response.resultCode.toResultCode()
            if (code == ResultCode.UNKNOWN) {
                QATool.report(UnknownErrorCodeException(response.resultCode, V::class.java))
            }
            log.error { "Error with request/response: error code $code" }
            Result.Error(code)
        }
    }
    //endregion
}


class UnknownErrorCodeException(intValue: Int, requestClazz: Class<*>) : Exception(
    "Unknown enum value for $intValue from request: ${requestClazz.simpleName}"
)


private var requestIdGenerator = AtomicInteger()

internal fun AbstractMessage.asRequest(): DeviceRequest {
    val requestId = requestIdGenerator.incrementAndGet().toShort()
    return DeviceRequest(
        requestId,
        // get type ID from the proto message descriptor
        this.descriptorForType.options.getExtension(Extensions.typeId).toShort(),
        this.toByteArray()
    )
}


enum class ResultCode(val intValue: Int) {

    OK(0),
    UNKNOWN(-100),
    BUSY(-110),
    NOT_SUPPORTED(-120),
    NOT_ALLOWED(-130),
    CANCELLED(-140),
    ABORTED(-150),
    TIMEOUT(-160),
    NOT_FOUND(-170),
    ALREADY_EXISTS(-180),
    TOO_LARGE(-190),
    NOT_ENOUGH_DATA(-191),
    LIMIT_EXCEEDED(-200),
    END_OF_STREAM(-201),
    INVALID_STATE(-210),
    IO(-220),
    WOULD_BLOCK(-221),
    FILE(-225),
    NETWORK(-230),
    PROTOCOL(-240),
    INTERNAL(-250),
    NO_MEMORY(-260),
    INVALID_ARGUMENT(-270),
    BAD_DATA(-280),
    OUT_OF_RANGE(-290),
    // the error code for when we are sent an invalid/unknown error code
    INVALID_ERROR_CODE(-101010);

    companion object {

        private val intValueMap = buildIntValueMap(values()) { state -> state.intValue }

        fun Int.toResultCode(): ResultCode {
            val enumValue = intValueMap.get(this, ResultCode.UNKNOWN)
            if (enumValue == ResultCode.UNKNOWN) {
                QATool.report(UnknownEnumIntValueException(this))
            }
            return enumValue
        }

    }

}
