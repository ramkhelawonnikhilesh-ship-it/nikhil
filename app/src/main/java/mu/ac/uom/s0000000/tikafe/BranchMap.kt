package mu.ac.uom.s0000000.tikafe

import android.Manifest
import android.annotation.SuppressLint
import android.location.Geocoder
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.Collator
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
@Composable
fun BranchMap(selected: Branch, onChoose: (Branch) -> Unit) {
    val context = LocalContext.current
    val locale = locale()
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(muNow()) }
    LaunchedEffect(Unit) { while (true) { now = muNow(); delay(30_000) } }
    var reason by remember { mutableStateOf(false) }
    fun hasLocation() = context.granted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
        context.granted(Manifest.permission.ACCESS_FINE_LOCATION)
    var allowed by remember { mutableStateOf(hasLocation()) }
    var location by remember { mutableStateOf<Location?>(null) }
    var centre by remember { mutableStateOf<GeoPoint?>(null) }
    var town by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { allowed = hasLocation() }
    DisposableEffect(allowed) {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) { location = result.lastLocation }
        }
        if (allowed) {
            try {
                client.lastLocation.addOnSuccessListener { if (location == null) location = it }
                client.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY,5_000).build(),
                    callback,android.os.Looper.getMainLooper())
            } catch (_: SecurityException) { location = null }
        }
        onDispose { client.removeLocationUpdates(callback) }
    }
    val format = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val closed = stringResource(R.string.closed)
    val hours = branches.associateWith { branch ->
        branch.hours(now.toLocalDate())?.let { (first,last) -> "${format.format(first)}–${format.format(last)}" } ?: closed
    }
    val here = stringResource(R.string.you)
    val mapLabel = stringResource(R.string.map)
    val map = remember(context) {
        Configuration.getInstance().load(context,context.getSharedPreferences("map",0))
        Configuration.getInstance().userAgentValue = "${context.packageName}/TiKafe (Android)"
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(9.7)
            controller.setCenter(GeoPoint(-20.22,57.53))
            contentDescription = mapLabel
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }
    LaunchedEffect(centre) {
        centre?.let { map.controller.setZoom(13.0); map.controller.animateTo(it) }
    }
    LaunchedEffect(hours,location,here) {
        map.overlays.clear()
        branches.forEach { branch ->
            map.overlays.add(Marker(map).apply {
                position = GeoPoint(branch.lat,branch.lon)
                title = branch.name
                snippet = hours.getValue(branch)
                setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM)
            })
        }
        location?.let {
            map.overlays.add(Marker(map).apply {
                position = GeoPoint(it.latitude,it.longitude)
                title = here
                icon = androidx.core.content.ContextCompat.getDrawable(context,android.R.drawable.ic_menu_mylocation)
                setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM)
            })
        }
        map.invalidate()
    }
    Page {
        if (!allowed) OutlinedButton(onClick = { reason = true }) { Text(stringResource(R.string.location)) }
        if (reason) AlertDialog(onDismissRequest = { reason = false },
            text = { Text(stringResource(R.string.location_reason)) },
            confirmButton = {
                TextButton(onClick = {
                    reason = false
                    permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text(stringResource(R.string.choose)) }
            },
            dismissButton = { TextButton(onClick = { reason = false }) { Text(stringResource(R.string.back)) } })
        AndroidView(factory = { map },modifier = Modifier.fillMaxWidth().height(275.dp))
        Text(stringResource(R.string.osm),style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(town,{ town = it; missing = false },label = { Text(stringResource(R.string.town)) },
            modifier = Modifier.fillMaxWidth(),singleLine = true,isError = missing,
            supportingText = { if (missing) Text(stringResource(R.string.town_missing)) })
        OutlinedButton(enabled = town.isNotBlank(),onClick = {
            val query = town.trim()
            scope.launch {
                centre = withContext(Dispatchers.IO) {
                    val matchingBranch = branches.firstOrNull { it.name.contains(query,ignoreCase = true) }
                    if (matchingBranch != null) return@withContext GeoPoint(matchingBranch.lat,matchingBranch.lon)
                    try {
                        @Suppress("DEPRECATION")
                        Geocoder(context,locale).getFromLocationName("$query, Mauritius",1)?.firstOrNull()?.let {
                            GeoPoint(it.latitude,it.longitude)
                        }
                    } catch (_: java.io.IOException) { null }
                    catch (_: IllegalArgumentException) { null }
                }
                missing = centre == null
            }
        }) { Text(stringResource(R.string.search)) }
        val distances = location?.let { point ->
            branches.associateWith {
                val result = FloatArray(1)
                Location.distanceBetween(point.latitude,point.longitude,it.lat,it.lon,result)
                result[0] / 1000.0
            }
        }
        val sorted = if (distances == null) branches.sortedWith(compareBy(Collator.getInstance(locale)) { it.name })
            else branches.sortedBy { distances.getValue(it) }
        val distanceFormat = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        sorted.forEach { branch ->
            FilterChip(selected = selected == branch,onClick = { onChoose(branch) },label = {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(branch.name)
                    Text(stringResource(if (branch.open(now)) R.string.open_now else R.string.closed))
                    Text(hours.getValue(branch))
                    distances?.let { Text(stringResource(R.string.distance,distanceFormat.format(it.getValue(branch)))) }
                }
            },modifier = Modifier.fillMaxWidth())
        }
    }
}
