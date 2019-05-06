package io.smarthome.mesh.setup.ui


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import io.smarthome.mesh.R
import kotlinx.android.synthetic.main.fragment_new_mesh_network_finished.view.*


class NewMeshNetworkFinishedFragment : BaseMeshSetupFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_new_mesh_network_finished, container, false)

        root.action_add_next_mesh_device.setOnClickListener {
            flowManagerVM.flowManager?.startNewFlowWithCommissioner()
        }
        root.action_start_building.setOnClickListener { endSetup() }

        return root
    }

    private fun endSetup() {
        findNavController().navigate(R.id.action_global_letsGetBuildingFragment)
    }

}
