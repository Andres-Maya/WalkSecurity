package com.andres.walksecurity.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.andres.walksecurity.BuildConfig
import com.andres.walksecurity.core.model.GeoPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import org.osmdroid.util.GeoPoint as OsmPoint

/**
 * Mapa de OpenStreetMap (osmdroid): funciona sin API key. Se usa mientras no haya
 * MAPS_API_KEY de Google Maps en local.properties.
 */
@Composable
fun OsmMap(location: GeoPoint?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember { createMapView(context) }
    val marker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Estás aquí"
        }
    }
    var centeredOnUser by rememberSaveable { mutableStateOf(false) }

    // osmdroid necesita enterarse del ciclo de vida para pausar la descarga de mosaicos
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier) { view ->
        if (location == null) return@AndroidView
        val point = OsmPoint(location.latitude, location.longitude)
        marker.position = point
        if (marker !in view.overlays) view.overlays.add(marker)
        if (!centeredOnUser) {
            centeredOnUser = true
            view.controller.setZoom(16.5)
            view.controller.animateTo(point)
        }
        view.invalidate()
    }
}

@SuppressLint("ClickableViewAccessibility")
private fun createMapView(context: Context): MapView {
    Configuration.getInstance().apply {
        // La política de uso de OpenStreetMap exige identificar la app
        userAgentValue = BuildConfig.APPLICATION_ID
        // Caché de mosaicos en el almacenamiento privado de la app (sin permisos de almacenamiento)
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(osmdroidBasePath, "tiles")
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        controller.setZoom(12.0)
        controller.setCenter(OsmPoint(4.7110, -74.0721)) // Bogotá hasta que llegue el GPS
        overlays.add(CopyrightOverlay(context)) // atribución obligatoria de OpenStreetMap
        // La pantalla principal se desplaza: al tocar el mapa, el gesto es para el mapa
        setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) view.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
    }
}
