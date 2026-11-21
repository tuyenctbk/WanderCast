package com.example

import com.example.data.EpisodeEntity
import com.example.data.RouteWaypoint
import com.example.data.WalkRouteEntity
import com.example.location.LocationBlockState
import com.example.location.LocationGeofenceManager
import com.example.util.SmartEngagementTelemetryState
import com.example.util.TourExportHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun haversineDistanceMatchesKnownBlockDistance() {
        // ~111m per 0.001 deg latitude
        val dist = LocationGeofenceManager.haversineMeters(37.7955, -122.4029, 37.7965, -122.4029)
        assertTrue("Expected ~111m, got $dist", dist in 105.0..118.0)
    }

    @Test
    fun bearingAndCardinalPointNorthAndEastCorrectly() {
        val northBearing = LocationGeofenceManager.bearingDegrees(37.7955, -122.4029, 37.8055, -122.4029)
        assertEquals("N", LocationGeofenceManager.cardinalFromBearing(northBearing))

        val eastBearing = LocationGeofenceManager.bearingDegrees(37.7955, -122.4029, 37.7955, -122.3929)
        assertEquals("E", LocationGeofenceManager.cardinalFromBearing(eastBearing))
    }

    @Test
    fun initialLocationAndTelemetryStatesContainNoSampleData() {
        val initialLocation = LocationBlockState()
        assertFalse(initialLocation.hasLocationFix)
        assertEquals(0.0, initialLocation.latitude, 0.0)
        assertEquals(0.0, initialLocation.longitude, 0.0)
        assertEquals("", initialLocation.streetBlockLabel)
        assertEquals(0, initialLocation.blocksExploredCount)
        assertTrue(initialLocation.activeGeofenceNodes.isEmpty())

        val initialTelemetry = SmartEngagementTelemetryState()
        assertEquals(0, initialTelemetry.delightScore)
        assertEquals(0, initialTelemetry.lifetimePositiveActionsCount)
    }

    @Test
    fun gpxExportContainsValidWaypointsAndTrack() {
        val waypoints = listOf(
            RouteWaypoint(37.7955, -122.4029, "Start & Corner"),
            RouteWaypoint(37.7965, -122.4036, "Jackson Square")
        )
        val route = WalkRouteEntity(
            routeTitle = "Sentinel & Jackson Walk",
            neighborhoodName = "North Beach",
            cityRegion = "San Francisco, CA",
            startBlockLabel = "Columbus & Kearny",
            endBlockLabel = "Jackson St",
            startLatitude = 37.7955,
            startLongitude = -122.4029,
            endLatitude = 37.7965,
            endLongitude = -122.4036,
            waypointsEncoded = WalkRouteEntity.encodeWaypoints(waypoints),
            totalDistanceMeters = 220,
            durationSeconds = 420,
            blocksVisitedCount = 2,
            narrativesGeneratedCount = 1,
            editorialLens = "Architecture",
            googleMapsUrl = "https://maps.google.com",
            startedAtTimestamp = 1_700_000_000_000L,
            completedAtTimestamp = 1_700_000_420_000L
        )
        val ep = EpisodeEntity(
            id = 1L,
            title = "Sentinel Flatiron",
            stationCallSign = "99.1 FM",
            locationLabel = "Columbus & Kearny",
            latitude = 37.7955,
            longitude = -122.4029,
            category = "Architecture",
            hookLine = "Look up at the copper dome.",
            narrativeScript = "Built in 1907.",
            fieldFactsJoined = "Fact 1||Fact 2",
            explorerPrompt = "Check the cornice."
        )
        val gpx = TourExportHelper.buildGpxXml(route, listOf(ep))
        assertTrue(gpx.contains("<gpx version=\"1.1\""))
        assertTrue(gpx.contains("Sentinel &amp; Jackson Walk"))
        assertTrue(gpx.contains("<wpt lat=\"37.795500\" lon=\"-122.402900\">"))
        assertTrue(gpx.contains("<trkpt lat=\"37.796500\" lon=\"-122.403600\">"))
    }
}
