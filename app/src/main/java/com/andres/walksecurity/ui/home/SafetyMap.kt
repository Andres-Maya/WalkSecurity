package com.andres.walksecurity.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andres.walksecurity.BuildConfig
import com.andres.walksecurity.core.model.GeoPoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState

// Bogotá como posición inicial mientras llega el primer fix de GPS
private val DEFAULT_POSITION = LatLng(4.7110, -74.0721)

@Composable
fun SafetyMap(
    location: GeoPoint?,
    myLocationEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!BuildConfig.HAS_MAPS_KEY) {
        MapPlaceholder(modifier)
        return
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_POSITION, 11f)
    }
    var centeredOnUser by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(location) {
        if (location != null && !centeredOnUser) {
            centeredOnUser = true
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 16f),
                durationMs = 800,
            )
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        // Solo se habilita con permiso concedido; de lo contrario Maps lanza SecurityException
        properties = MapProperties(isMyLocationEnabled = myLocationEnabled),
        uiSettings = MapUiSettings(
            myLocationButtonEnabled = myLocationEnabled,
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
        ),
    )
}

@Composable
private fun MapPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Mapa no configurado.\nAgrega MAPS_API_KEY en local.properties y recompila.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}
