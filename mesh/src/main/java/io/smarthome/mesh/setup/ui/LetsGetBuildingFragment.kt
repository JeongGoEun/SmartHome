package io.smarthome.mesh.setup.ui


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.smarthome.android.sdk.cloud.ParticleCloudSDK
import io.smarthome.android.sdk.cloud.ParticleEventVisibility
import io.smarthome.mesh.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit


class LetsGetBuildingFragment : BaseMeshSetupFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        GlobalScope.launch(Dispatchers.Default) {
            ParticleCloudSDK.getCloud().publishEvent(
                "mesh-setup-session-complete",
                null,
                ParticleEventVisibility.PRIVATE,
                TimeUnit.HOURS.toSeconds(1).toInt()
            )
        }

        return inflater.inflate(R.layout.fragment_lets_get_building, container, false)
    }


}
