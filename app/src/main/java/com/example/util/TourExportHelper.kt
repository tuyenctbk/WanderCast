package com.example.util

import android.content.Context
import android.content.Intent
import com.example.data.EpisodeEntity
import com.example.data.RouteWaypoint
import com.example.data.WalkRouteEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TourExportHelper {

    /**
     * Builds a standard GPX 1.1 XML string containing:
     * - `<wpt>` waypoints for each historical or architectural audio narrative triggered during the session
     * - `<trk>` / `<trkseg>` / `<trkpt>` breadcrumb path coordinates of the walk route
     */
    fun buildGpxXml(
        route: WalkRouteEntity,
        narratives: List<EpisodeEntity>
    ): String {
        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val exportedAtIso = isoFormatter.format(Date(route.completedAtTimestamp))
        val waypoints: List<RouteWaypoint> = route.waypoints.ifEmpty {
            listOf(
                RouteWaypoint(route.startLatitude, route.startLongitude, route.startBlockLabel),
                RouteWaypoint(route.endLatitude, route.endLongitude, route.endBlockLabel)
            )
        }

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<gpx version="1.1" creator="WanderCast 99.1 FM AI Walking Guide" xmlns="http://www.topografix.com/GPX/1/1">""")
            appendLine("  <metadata>")
            appendLine("    <name>${escapeXml(route.routeTitle)}</name>")
            appendLine("    <desc>${escapeXml("${route.neighborhoodName} (${route.cityRegion}) • ${route.totalDistanceMeters}m walked • ${narratives.size} historical dispatches")}</desc>")
            appendLine("    <time>$exportedAtIso</time>")
            appendLine("  </metadata>")

            // Triggered Historical / Architectural Points from Room DB
            narratives.forEachIndexed { idx, ep ->
                val epIso = isoFormatter.format(Date(ep.timestamp))
                appendLine("""  <wpt lat="${String.format(Locale.US, "%.6f", ep.latitude)}" lon="${String.format(Locale.US, "%.6f", ep.longitude)}">""")
                appendLine("    <time>$epIso</time>")
                appendLine("    <name>${escapeXml("Stop ${idx + 1}: ${ep.title}")}</name>")
                appendLine("    <cmt>${escapeXml(ep.hookLine)}</cmt>")
                appendLine("    <desc>${escapeXml("${ep.category} (${ep.voicePersona}) — ${ep.fieldFacts.joinToString(" | ")}")}</desc>")
                appendLine("    <sym>Historical Marker</sym>")
                appendLine("  </wpt>")
            }

            // Walk Path Track Points
            appendLine("  <trk>")
            appendLine("    <name>${escapeXml(route.routeTitle)}</name>")
            appendLine("    <type>${escapeXml(route.editorialLens)}</type>")
            appendLine("    <trkseg>")
            waypoints.forEachIndexed { i, pt ->
                val pointTime = route.startedAtTimestamp +
                    ((route.completedAtTimestamp - route.startedAtTimestamp) * i / waypoints.size.coerceAtLeast(1))
                val ptIso = isoFormatter.format(Date(pointTime))
                appendLine("""      <trkpt lat="${String.format(Locale.US, "%.6f", pt.latitude)}" lon="${String.format(Locale.US, "%.6f", pt.longitude)}">""")
                appendLine("        <time>$ptIso</time>")
                appendLine("        <name>${escapeXml(pt.label)}</name>")
                appendLine("      </trkpt>")
            }
            appendLine("    </trkseg>")
            appendLine("  </trk>")
            appendLine("</gpx>")
        }
    }

    /**
     * Builds a formatted Story-Map Postcard summary suitable for sharing with friends or notes apps.
     */
    fun buildStoryPostcardText(
        route: WalkRouteEntity,
        narratives: List<EpisodeEntity>
    ): String {
        val dateStr = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US)
            .format(Date(route.completedAtTimestamp))
        val durationMin = (route.durationSeconds.coerceAtLeast(60) / 60)

        return buildString {
            appendLine("🎙️ WANDERCAST 99.1 FM • FIELD WALK DISPATCH")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📍 Tour: ${route.routeTitle}")
            appendLine("🏙️ Neighborhood: ${route.neighborhoodName} (${route.cityRegion})")
            appendLine("🧭 Lens: ${route.editorialLens} • $dateStr")
            appendLine("🚶 Telemetry: ${route.totalDistanceMeters}m walked • ${durationMin} min • ${route.blocksVisitedCount} blocks")
            appendLine()
            if (narratives.isNotEmpty()) {
                appendLine("🏛️ KEY HISTORICAL & ARCHITECTURAL POINTS TRIGGERED (${narratives.size}):")
                narratives.forEachIndexed { index, ep ->
                    appendLine()
                    appendLine("${index + 1}. ${ep.title} [${ep.category}]")
                    appendLine("   📌 ${ep.locationLabel} (${String.format(Locale.US, "%.4f, %.4f", ep.latitude, ep.longitude)})")
                    appendLine("   💬 “${ep.hookLine}”")
                    ep.fieldFacts.take(2).forEach { fact ->
                        appendLine("   • $fact")
                    }
                    if (ep.explorerPrompt.isNotBlank()) {
                        appendLine("   👁️ Spot Detail: ${ep.explorerPrompt}")
                    }
                }
            }
            if (route.googleMapsUrl.isNotBlank()) {
                appendLine()
                appendLine("🗺️ Retrace Walk in Google Maps:")
                appendLine(route.googleMapsUrl)
            }
        }
    }

    fun launchShareSheet(
        context: Context,
        subject: String,
        content: String
    ) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        val chooser = Intent.createChooser(sendIntent, subject)
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {
        }
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
