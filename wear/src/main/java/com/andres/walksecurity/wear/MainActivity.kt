package com.andres.walksecurity.wear

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.TimeText
import com.andres.walksecurity.watch.ui.WatchActions
import com.andres.walksecurity.watch.ui.WatchApp

class MainActivity : ComponentActivity() {

    private val viewModel: WatchViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            permissionLauncher.launch(
                buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }.toTypedArray()
            )
        }

        val controller = viewModel.controller
        val actions = WatchActions(
            onSos = controller::onSosPressed,
            onSendNow = controller::sendNow,
            onCancel = controller::cancelSos,
            onDismissResult = controller::dismissResult,
            onImOk = controller::acknowledgeAlert,
            onEmergency = controller::confirmEmergency,
        )
        setContent {
            val state by controller.state.collectAsStateWithLifecycle()
            MaterialTheme {
                AppScaffold(timeText = { TimeText() }) {
                    WatchApp(state, actions)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.controller.refreshPhone()
    }
}
