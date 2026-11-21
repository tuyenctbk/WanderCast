package com.example.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.EpisodeEntity
import com.example.data.RouteWaypoint
import org.json.JSONArray
import org.json.JSONObject

private class StreetMapJsBridge(
    private val onPointClicked: (Long) -> Unit,
    private val onPointNarrateRequested: (Long) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onHistoricalMarkerClicked(episodeId: Long) {
        mainHandler.post {
            onPointClicked(episodeId)
        }
    }

    @JavascriptInterface
    fun onNarrateHistoricalPoint(episodeId: Long) {
        mainHandler.post {
            onPointNarrateRequested(episodeId)
        }
    }
}

/**
 * A 100% Zero-API-Key interactive street & satellite map powered by Leaflet + OpenStreetMap /
 * CartoDB / Esri tiles. Displays the user's walked GPS path polyline, 42m geofence trigger zones,
 * live GPS walker position beacon, start/end pins, and clickable numbered historical markers
 * color-coded by geofence visit status.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KeylessInteractiveStreetMapView(
    waypoints: List<RouteWaypoint>,
    triggeredPoints: List<EpisodeEntity>,
    selectedPointId: Long?,
    tileStyleIndex: Int, // 0 = Dark Broadcast, 1 = Daylight Street, 2 = OSM Standard, 3 = Satellite
    fitBoundsTrigger: Int,
    onSelectPoint: (EpisodeEntity) -> Unit,
    onNarratePoint: (EpisodeEntity) -> Unit = onSelectPoint,
    visitedPointIds: Set<Long> = emptySet(),
    currentGpsLat: Double? = null,
    currentGpsLng: Double? = null,
    geofenceRadiusMeters: Int = 85,
    isBatterySaverActive: Boolean = false,
    mapTileRefreshThrottleMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    val currentTriggeredPoints by rememberUpdatedState(triggeredPoints)
    val currentOnSelectPoint by rememberUpdatedState(onSelectPoint)
    val currentOnNarratePoint by rememberUpdatedState(onNarratePoint)
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }
    var lastMapRenderTimestampMs by remember { mutableStateOf(0L) }

    val routePayloadJson = remember(
        waypoints,
        triggeredPoints,
        visitedPointIds,
        currentGpsLat,
        currentGpsLng,
        geofenceRadiusMeters
    ) {
        buildMapPayloadJson(
            waypoints = waypoints,
            triggeredPoints = triggeredPoints,
            visitedPointIds = visitedPointIds,
            currentGpsLat = currentGpsLat,
            currentGpsLng = currentGpsLng,
            geofenceRadiusMeters = geofenceRadiusMeters
        )
    }

    // Update map route, GPS beacon & historical markers whenever tour data or GPS changes,
    // automatically throttling map tile & DOM refresh rate when Battery-Saving Mode (< 20%) is active.
    LaunchedEffect(routePayloadJson, pageLoaded, isBatterySaverActive, mapTileRefreshThrottleMs) {
        if (pageLoaded) {
            val now = System.currentTimeMillis()
            val elapsedSinceLastRender = now - lastMapRenderTimestampMs
            if (isBatterySaverActive &&
                mapTileRefreshThrottleMs > 0L &&
                lastMapRenderTimestampMs > 0L &&
                elapsedSinceLastRender < mapTileRefreshThrottleMs
            ) {
                kotlinx.coroutines.delay(mapTileRefreshThrottleMs - elapsedSinceLastRender)
            }
            val escaped = JSONObject.quote(routePayloadJson)
            webViewRef?.evaluateJavascript("window.renderTourData($escaped);", null)
            lastMapRenderTimestampMs = System.currentTimeMillis()
        }
    }

    // Apply low-battery map tile refresh & buffer throttling inside Leaflet JS when battery saver toggles
    LaunchedEffect(isBatterySaverActive, pageLoaded) {
        if (pageLoaded) {
            webViewRef?.evaluateJavascript(
                "window.setBatterySaverTileProfile($isBatterySaverActive);",
                null
            )
        }
    }

    // Pan/fly to selected historical point when user taps "Focus Map" on a Room card
    LaunchedEffect(selectedPointId, pageLoaded) {
        if (pageLoaded && selectedPointId != null) {
            val point = triggeredPoints.firstOrNull { it.id == selectedPointId }
            if (point != null) {
                webViewRef?.evaluateJavascript(
                    "window.focusPointById(${point.id}, ${point.latitude}, ${point.longitude});",
                    null
                )
            }
        }
    }

    // Switch street/satellite tile layer when user cycles map style
    LaunchedEffect(tileStyleIndex, pageLoaded) {
        if (pageLoaded) {
            webViewRef?.evaluateJavascript("window.setTileStyle($tileStyleIndex);", null)
        }
    }

    // Fit full route bounds when user taps "Fit Route"
    LaunchedEffect(fitBoundsTrigger, pageLoaded) {
        if (pageLoaded && fitBoundsTrigger > 0) {
            webViewRef?.evaluateJavascript("window.fitTourBounds();", null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef = null
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .testTag("keyless_street_map_webview"),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setBackgroundColor(0xFF0D1117.toInt())

                addJavascriptInterface(
                    StreetMapJsBridge(
                        onPointClicked = { clickedId ->
                            currentTriggeredPoints.firstOrNull { it.id == clickedId }?.let { ep ->
                                currentOnSelectPoint(ep)
                            }
                        },
                        onPointNarrateRequested = { narrateId ->
                            currentTriggeredPoints.firstOrNull { it.id == narrateId }?.let { ep ->
                                currentOnSelectPoint(ep)
                                currentOnNarratePoint(ep)
                            }
                        }
                    ),
                    "WanderCastMapBridge"
                )

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        pageLoaded = true
                    }
                }

                loadDataWithBaseURL(
                    "https://wandercast.local/",
                    LEAFLET_MAP_HTML,
                    "text/html",
                    "UTF-8",
                    null
                )
                webViewRef = this
            }
        }
    )
}

private fun buildMapPayloadJson(
    waypoints: List<RouteWaypoint>,
    triggeredPoints: List<EpisodeEntity>,
    visitedPointIds: Set<Long>,
    currentGpsLat: Double?,
    currentGpsLng: Double?,
    geofenceRadiusMeters: Int
): String {
    val root = JSONObject()
    val waypointsArray = JSONArray()
    waypoints.forEach { wp ->
        val obj = JSONObject()
        obj.put("lat", wp.latitude)
        obj.put("lng", wp.longitude)
        obj.put("label", wp.label)
        waypointsArray.put(obj)
    }
    root.put("waypoints", waypointsArray)

    if (currentGpsLat != null && currentGpsLng != null) {
        val gpsObj = JSONObject()
        gpsObj.put("lat", currentGpsLat)
        gpsObj.put("lng", currentGpsLng)
        gpsObj.put("radius", geofenceRadiusMeters)
        root.put("currentGps", gpsObj)
    }

    val pointsArray = JSONArray()
    triggeredPoints.forEachIndexed { idx, ep ->
        val obj = JSONObject()
        obj.put("id", ep.id)
        obj.put("stopNum", idx + 1)
        obj.put("lat", ep.latitude)
        obj.put("lng", ep.longitude)
        obj.put("title", ep.title)
        obj.put("category", ep.category)
        obj.put("locationLabel", ep.locationLabel)
        obj.put("hookLine", ep.hookLine)
        obj.put("voicePersona", ep.voicePersona)
        obj.put("durationSeconds", ep.durationSeconds)
        obj.put("visited", visitedPointIds.contains(ep.id))
        pointsArray.put(obj)
    }
    root.put("points", pointsArray)
    return root.toString()
}

private const val LEAFLET_MAP_HTML = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  html, body, #map {
    margin: 0;
    padding: 0;
    width: 100%;
    height: 100%;
    background: #0D1117;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  }
  .stop-badge {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: #F5A623;
    color: #0D1117;
    font-weight: 800;
    font-size: 12px;
    border: 2px solid #FFFFFF;
    box-shadow: 0 2px 10px rgba(245, 166, 35, 0.55);
  }
  .stop-badge-visited {
    background: #38D399;
    box-shadow: 0 2px 12px rgba(56, 211, 153, 0.65);
  }
  .gps-walker-beacon {
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: #38BDF8;
    border: 3px solid #FFFFFF;
    box-shadow: 0 0 14px rgba(56, 189, 248, 0.9);
  }
  .endpoint-badge {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    color: #0D1117;
    font-weight: 800;
    font-size: 10px;
    border: 2px solid #FFFFFF;
    box-shadow: 0 2px 6px rgba(0,0,0,0.45);
  }
  .leaflet-popup-content-wrapper {
    background: #161F2C;
    color: #F3F6FA;
    border: 1px solid #F5A623;
    border-radius: 14px;
  }
  .leaflet-popup-tip {
    background: #161F2C;
  }
  .popup-cat {
    color: #F5A623;
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.6px;
    text-transform: uppercase;
  }
  .popup-visited-pill {
    display: inline-block;
    margin-top: 3px;
    padding: 2px 6px;
    border-radius: 6px;
    font-size: 9px;
    font-weight: 700;
  }
  .popup-title {
    font-size: 13px;
    font-weight: 700;
    margin: 4px 0;
    color: #FFFFFF;
  }
  .popup-hook {
    font-size: 11px;
    color: #B4C0CE;
    font-style: italic;
    margin-bottom: 8px;
  }
  .popup-narrate-btn {
    display: inline-block;
    background: #F5A623;
    color: #0D1117;
    font-weight: 700;
    font-size: 11px;
    padding: 5px 10px;
    border-radius: 8px;
    border: none;
    cursor: pointer;
  }
</style>
</head>
<body>
<div id="map"></div>
<script>
  var map = null;
  var tileLayer = null;
  var currentTileStyleIdx = 0;
  var isBatterySaverTileMode = false;
  var routeLayerGroup = null;
  var markersById = {};
  var currentBounds = null;
  var hasFittedInitialBounds = false;

  var tileUrls = [
    "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png",
    "https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png",
    "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
  ];

  function buildTileLayerOptions() {
    if (isBatterySaverTileMode) {
      return {
        maxZoom: 18,
        updateWhenIdle: true,
        updateWhenZooming: false,
        updateInterval: 950,
        keepBuffer: 1
      };
    } else {
      return {
        maxZoom: 19,
        updateWhenIdle: false,
        updateWhenZooming: true,
        updateInterval: 180,
        keepBuffer: 2
      };
    }
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function initMap() {
    if (typeof L === 'undefined') return;
    map = L.map('map', {
      zoomControl: false,
      attributionControl: false,
      preferCanvas: true
    }).setView([37.7955, -122.4029], 16);

    L.control.zoom({ position: 'bottomright' }).addTo(map);
    tileLayer = L.tileLayer(tileUrls[currentTileStyleIdx], buildTileLayerOptions()).addTo(map);
    routeLayerGroup = L.layerGroup().addTo(map);
  }

  window.triggerPopupNarrate = function(epId) {
    if (window.WanderCastMapBridge && window.WanderCastMapBridge.onNarrateHistoricalPoint) {
      window.WanderCastMapBridge.onNarrateHistoricalPoint(epId);
    }
  };

  window.setTileStyle = function(idx) {
    if (!map || typeof L === 'undefined') return;
    currentTileStyleIdx = idx % tileUrls.length;
    var url = tileUrls[currentTileStyleIdx];
    if (tileLayer) {
      map.removeLayer(tileLayer);
    }
    tileLayer = L.tileLayer(url, buildTileLayerOptions()).addTo(map);
  };

  window.setBatterySaverTileProfile = function(enabled) {
    isBatterySaverTileMode = !!enabled;
    if (!map || typeof L === 'undefined') return;
    var url = tileUrls[currentTileStyleIdx % tileUrls.length];
    if (tileLayer) {
      map.removeLayer(tileLayer);
    }
    tileLayer = L.tileLayer(url, buildTileLayerOptions()).addTo(map);
  };

  window.renderTourData = function(jsonStr) {
    if (!map) initMap();
    if (!map || !routeLayerGroup) return;

    routeLayerGroup.clearLayers();
    markersById = {};

    var data = JSON.parse(jsonStr);
    var waypoints = data.waypoints || [];
    var points = data.points || [];
    var currentGps = data.currentGps || null;
    var latlngs = [];

    for (var i = 0; i < waypoints.length; i++) {
      latlngs.push([waypoints[i].lat, waypoints[i].lng]);
    }

    if (latlngs.length >= 2) {
      // Outer glowing amber trail
      L.polyline(latlngs, {
        color: '#F5A623',
        weight: 11,
        opacity: 0.28,
        lineCap: 'round'
      }).addTo(routeLayerGroup);

      // Crisp dashed walk path
      L.polyline(latlngs, {
        color: '#F5A623',
        weight: 4,
        opacity: 0.96,
        dashArray: '10, 7',
        lineCap: 'round'
      }).addTo(routeLayerGroup);

      // Start marker
      var startIcon = L.divIcon({
        className: '',
        html: '<div class="endpoint-badge" style="background:#38D399;">S</div>',
        iconSize: [24, 24],
        iconAnchor: [12, 12]
      });
      L.marker(latlngs[0], { icon: startIcon })
        .bindPopup('<div class="popup-cat">Walk Start</div><div class="popup-title">' + escapeHtml(waypoints[0].label || 'Origin') + '</div>')
        .addTo(routeLayerGroup);

      // End marker
      var endIdx = latlngs.length - 1;
      var endIcon = L.divIcon({
        className: '',
        html: '<div class="endpoint-badge" style="background:#38BDF8;">E</div>',
        iconSize: [24, 24],
        iconAnchor: [12, 12]
      });
      L.marker(latlngs[endIdx], { icon: endIcon })
        .bindPopup('<div class="popup-cat">Walk Finish</div><div class="popup-title">' + escapeHtml(waypoints[endIdx].label || 'Destination') + '</div>')
        .addTo(routeLayerGroup);
    }

    // Current Walker GPS Beacon & Active Geofence Radius
    if (currentGps && typeof currentGps.lat === 'number' && typeof currentGps.lng === 'number') {
      var gpsPos = [currentGps.lat, currentGps.lng];
      L.circle(gpsPos, {
        radius: currentGps.radius || 85,
        color: '#38BDF8',
        weight: 1.5,
        dashArray: '5, 5',
        fillColor: '#38BDF8',
        fillOpacity: 0.10
      }).addTo(routeLayerGroup);

      var gpsIcon = L.divIcon({
        className: '',
        html: '<div class="gps-walker-beacon"></div>',
        iconSize: [18, 18],
        iconAnchor: [9, 9]
      });
      L.marker(gpsPos, { icon: gpsIcon })
        .bindPopup('<div class="popup-cat">LIVE GPS RECEIVER</div><div class="popup-title">Your Current Position (' + (currentGps.radius || 85) + 'm Geofence)</div>')
        .addTo(routeLayerGroup);
    }

    for (var j = 0; j < points.length; j++) {
      var pt = points[j];
      var pos = [pt.lat, pt.lng];
      latlngs.push(pos);
      var isVisited = !!pt.visited;
      var ringColor = isVisited ? '#38D399' : '#F5A623';

      // 42m Geofence trigger zone circle
      L.circle(pos, {
        radius: 42,
        color: ringColor,
        weight: isVisited ? 2.5 : 2,
        fillColor: ringColor,
        fillOpacity: isVisited ? 0.24 : 0.14
      }).addTo(routeLayerGroup);

      var numLabel = pt.stopNum < 10 ? ('0' + pt.stopNum) : ('' + pt.stopNum);
      var badgeClass = isVisited ? 'stop-badge stop-badge-visited' : 'stop-badge';
      var badgeContent = isVisited ? ('✓' + pt.stopNum) : numLabel;
      var stopIcon = L.divIcon({
        className: '',
        html: '<div class="' + badgeClass + '">' + badgeContent + '</div>',
        iconSize: [32, 32],
        iconAnchor: [16, 16]
      });

      var visitedPillHtml = isVisited
        ? '<div class="popup-visited-pill" style="background:rgba(56,211,153,0.22);color:#38D399;">✓ VISITED • GEOFENCE TRIGGERED</div>'
        : '<div class="popup-visited-pill" style="background:rgba(245,166,35,0.18);color:#F5A623;">⏳ UPCOMING STOP</div>';

      var popupHtml = '<div class="popup-cat">POINT ' + numLabel + ' • ' + escapeHtml(pt.category) + '</div>' +
        visitedPillHtml +
        '<div class="popup-title">' + escapeHtml(pt.title) + '</div>' +
        '<div class="popup-hook">“' + escapeHtml(pt.hookLine) + '”</div>' +
        '<button class="popup-narrate-btn" onclick="window.triggerPopupNarrate(' + pt.id + ')">▶ Narrate (' + (pt.durationSeconds || 60) + 's • ' + escapeHtml(pt.voicePersona || 'Host') + ')</button>';

      (function(epId) {
        var m = L.marker(pos, { icon: stopIcon })
          .bindPopup(popupHtml)
          .on('click', function() {
            if (window.WanderCastMapBridge && window.WanderCastMapBridge.onHistoricalMarkerClicked) {
              window.WanderCastMapBridge.onHistoricalMarkerClicked(epId);
            }
          })
          .addTo(routeLayerGroup);
        markersById[epId] = m;
      })(pt.id);
    }

    if (latlngs.length >= 2) {
      currentBounds = L.latLngBounds(latlngs);
      if (!hasFittedInitialBounds) {
        hasFittedInitialBounds = true;
        map.fitBounds(currentBounds, { padding: [36, 36] });
      }
    } else if (latlngs.length === 1) {
      map.setView(latlngs[0], 16);
    }
  };

  window.focusPointById = function(id, lat, lng) {
    if (!map) return;
    map.flyTo([lat, lng], 17, { duration: 0.55 });
    var marker = markersById[id];
    if (marker) {
      marker.openPopup();
    }
  };

  window.fitTourBounds = function() {
    if (map && currentBounds) {
      map.fitBounds(currentBounds, { padding: [36, 36] });
    }
  };

  initMap();
</script>
</body>
</html>
"""
