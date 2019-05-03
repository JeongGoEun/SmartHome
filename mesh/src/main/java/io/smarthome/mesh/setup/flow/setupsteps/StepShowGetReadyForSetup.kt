package io.smarthome.mesh.setup.flow.setupsteps

import io.smarthome.mesh.common.android.livedata.nonNull
import io.smarthome.mesh.common.android.livedata.runBlockOnUiThreadAndAwaitUpdate
import io.smarthome.mesh.setup.flow.MeshSetupStep
import io.smarthome.mesh.setup.flow.Scopes
import io.smarthome.mesh.setup.flow.context.SetupContexts
import io.smarthome.mesh.setup.flow.modules.FlowUiDelegate


// FIXME: this whole class shouldn't even exist.
// FlowType should start by providing the FlowManager a barcode and
// the intended flow type (i.e.: setup, or one of the control panel actions)
class StepShowGetReadyForSetup(private val flowUi: FlowUiDelegate) : MeshSetupStep() {

    override suspend fun doRunStep(ctxs: SetupContexts, scopes: Scopes) {
        // FIXME: insert something here about whether we're in a control panel flow or not
        if (ctxs.getReadyNextButtonClickedLD.value == true) {
            return
        }

        ctxs.getReadyNextButtonClickedLD.nonNull(scopes).runBlockOnUiThreadAndAwaitUpdate(scopes) {
            flowUi.showGetReadyForSetupScreen()
        }
    }
}