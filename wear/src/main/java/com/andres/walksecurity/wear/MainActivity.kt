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
import com.andres.walksecurity.wear.ui.WatchActions
import com.andres.walksecurity.wear.ui.WatchApp

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

        val actions = WatchActions(
            onSos = viewModel::onSosPressed,
            onSendNow = viewModel::sendNow,
            onCancel = viewModel::cancelSos,
            onDismissResult = viewModel::dismissResult,
            onImOk = viewModel::acknowledgeAlert,
            onEmergency = viewModel::confirmEmergency,
        )
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            WatchApp(state, actions)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPhone()
    }
}
