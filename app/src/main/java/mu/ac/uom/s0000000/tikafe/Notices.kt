package mu.ac.uom.s0000000.tikafe

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationServices
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

fun Context.granted(permission: String) = ContextCompat.checkSelfPermission(this,permission) == PackageManager.PERMISSION_GRANTED

object Notices {
    private const val ORDERS = "orders"
    private const val OFFERS = "offers"

    fun channels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            NotificationChannel(ORDERS, context.getString(R.string.orders), NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(OFFERS, context.getString(R.string.offers), NotificationManager.IMPORTANCE_DEFAULT)
        ))
    }

    fun ready(context: Context, order: Order) {
        val link = orderLink(context, order.id)
        show(context, order.id.hashCode(), ORDERS, R.string.order_ready,
            context.getString(R.string.ready_text,branch(order.branch).name,order.code),link,
            NotificationCompat.Action(0,context.getString(R.string.show_code),link))
    }

    fun offer(context: Context, branch: Branch) = show(context, Int.MIN_VALUE, OFFERS,
        R.string.offer_title,context.getString(R.string.offer_text,branch.name))

    fun arrival(context: Context, id: Long) = show(context, id.hashCode() xor Int.MIN_VALUE, ORDERS,
        R.string.arrival_notice,null,orderLink(context,id))

    private fun orderLink(context: Context, id: Long): PendingIntent {
        val intent = Intent(context,ShopActivity::class.java).apply {
            data = android.net.Uri.parse("tikafe://orders/$id")
            putExtra("order",id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    private fun show(context: Context, id: Int, channel: String, title: Int, message: String?,
                     link: PendingIntent? = null, action: NotificationCompat.Action? = null) {
        if (Build.VERSION.SDK_INT >= 33 && !context.granted(Manifest.permission.POST_NOTIFICATIONS)) return
        val builder = NotificationCompat.Builder(context,channel).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(title)).setContentText(message).setContentIntent(link).setAutoCancel(true)
        if (message != null) builder.setStyle(NotificationCompat.BigTextStyle().bigText(message))
        if (action != null) builder.addAction(action)
        context.getSystemService(NotificationManager::class.java).notify(id,builder.build())
    }
}

object Arrival {
    private val changes = MutableStateFlow(0)
    val arrivals = changes.asStateFlow()

    fun notifyChange() { changes.update { it + 1 } }

    private fun callback(context: Context): PendingIntent =
        PendingIntent.getBroadcast(context,0,Intent(context,ArrivalReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)

    fun allowed(context: Context) = context.granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
        (Build.VERSION.SDK_INT < 29 || context.granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION))

    @SuppressLint("MissingPermission")
    fun register(context: Context, order: Order) {
        if (!allowed(context) || order.arriving || order.status !in listOf(Status.RECEIVED,Status.PREPARING,Status.READY)) return
        val branch = branch(order.branch)
        val fence = Geofence.Builder().setRequestId(order.id.toString()).setCircularRegion(branch.lat,branch.lon,200f)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER).setExpirationDuration(Geofence.NEVER_EXPIRE).build()
        val request = GeofencingRequest.Builder().setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(fence).build()
        LocationServices.getGeofencingClient(context).addGeofences(request,callback(context))
    }

    fun remove(context: Context, id: Long) {
        LocationServices.getGeofencingClient(context).removeGeofences(listOf(id.toString()))
    }
}

class ArrivalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val pending = goAsync()
        val app = context.applicationContext
        executor.execute {
            try {
                ShopDb(app).use { db ->
                    event.triggeringGeofences.orEmpty().forEach { fence ->
                        val id = fence.requestId.toLongOrNull() ?: return@forEach
                        if (db.markArriving(id)) {
                            Notices.arrival(app,id)
                            Arrival.notifyChange()
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
    companion object {
        private val executor = Executors.newSingleThreadExecutor()
    }
}
