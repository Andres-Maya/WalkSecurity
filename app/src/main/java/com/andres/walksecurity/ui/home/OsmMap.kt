package com.andres.walksecurity.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.andres.walksecurity.BuildConfig
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.andres.walksecurity.core.model.GeoPoint
import com.andres.walksecurity.core.risk.RiskZone
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File
import org.osmdroid.util.GeoPoint as OsmPoint

/**
 * Mapa de OpenStreetMap (osmdroid): funciona sin API key. Se usa mientras no haya
 * MAPS_API_KEY de Google Maps en local.properties.
 */
@Composable
fun OsmMap(location: GeoPoint?, zones: List<RiskZone>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember { createMapView(context).also { drawZones(it, zones) } }
    val marker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Estás aquí"
        }
    }
    // remember (no rememberSaveable): si el mapa se vuelve a crear, hay que centrarlo otra vez
    var centeredOnUser by remember { mutableStateOf(false) }

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

    // clipToBounds: osmdroid dibuja mosaicos fuera de su área y taparía las tarjetas de abajo
    AndroidView(factory = { mapView }, modifier = modifier.clipToBounds()) { view ->
        if (location == null) return@AndroidView
        val point = OsmPoint(location.latitude, location.longitude)
        marker.position = point
        if (marker !in view.overlays) view.overlays.add(marker)
        if (!centeredOnUser) {
            centeredOnUser = true
            // post: antes de que el mapa tenga tamaño, osmdroid centra mal (el punto queda en una esquina).
            // Zoom 15: se ve la persona y las zonas de riesgo de alrededor (~1 km)
            view.post {
                view.controller.setZoom(15.0)
                view.controller.setCenter(point)
            }
        }
        view.invalidate()
    }
}

/** Círculos translúcidos de las zonas con riesgo estimado (ámbar = precaución, rojo = alerta). */
private fun drawZones(map: MapView, zones: List<RiskZone>) {
    // Primero las de menor riesgo, para que las de alerta queden encima
    zones.sortedBy { it.riskScore }.forEach { zone ->
        val color = zone.level.mapColor().toArgb()
        map.overlays.add(
            Polygon(map).apply {
                points = Polygon.pointsAsCircle(OsmPoint(zone.latitude, zone.longitude), zone.radiusMeters.toDouble())
                fillPaint.color = ColorUtils.setAlphaComponent(color, 60)
                outlinePaint.color = color
                outlinePaint.strokeWidth = 3f
                title = zone.name
            }
        )
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
