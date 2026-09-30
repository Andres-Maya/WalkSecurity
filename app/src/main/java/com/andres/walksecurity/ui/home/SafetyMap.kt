package com.andres.walksecurity.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    // Sin API key de Google Maps se usa OpenStreetMap: el mapa funciona igual
    if (!BuildConfig.HAS_MAPS_KEY) {
        OsmMap(location, modifier)
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
