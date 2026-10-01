package com.daily.nexamartpartner.features.delivery.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.daily.nexamartpartner.MainActivity
import com.daily.nexamartpartner.R
import com.daily.nexamartpartner.features.auth.domain.model.UserRole
import com.daily.nexamartpartner.features.auth.domain.session.SessionManager
import com.daily.nexamartpartner.features.delivery.notifications.data.model.DeliveryNotificationPageDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

interface DeliveryPresenceApi {
    @PUT("delivery/location")
    suspend fun updateLocation(@Body body: Map<String, Double>): Response<Unit>

    @GET("delivery/notifications")
    suspend fun unreadNotifications(
        @Query("unreadOnly") unreadOnly: Boolean = true,
        @Query("page") page: Int = 0,
        @Query("pageSize") pageSize: Int = 20
    ): Response<DeliveryNotificationPageDto>
}

/**
 * Keeps the backend dispatcher supplied with a fresh partner location (the backend ignores stale
 * locations when auto-assigning orders) and surfaces new ORDER_ASSIGNED notifications as system
 * notifications that open the order. Runs while the app process is alive and a delivery partner
 * is signed in.
 */
class DeliveryPresenceReporter(
    context: Context,
    private val api: DeliveryPresenceApi,
    private val sessionManager: SessionManager
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val announcedNotificationIds = mutableSetOf<String>()
    private var loopJob: Job? = null
    private var lastLocation: Location? = null
    private var listening = false

    private val listener = LocationListener { location ->
        if (isNewer(location)) lastLocation = location
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (!isDeliveryPartner()) return
        startLocationUpdates()
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                if (!isDeliveryPartner()) {
                    stop()
                    return@launch
                }
                startLocationUpdates()
                reportLocation()
                announceNewAssignments()
                delay(REPORT_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        if (listening) {
            locationManager.removeUpdates(listener)
            listening = false
        }
        lastLocation = null
        announcedNotificationIds.clear()
    }

    private fun isDeliveryPartner(): Boolean = sessionManager.currentSession.value?.role == UserRole.DELIVERY_PARTNER

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (listening || !hasLocationPermission()) return
        val providers = locationManager.getProviders(true).filter {
            it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER
        }
        providers.forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(provider, LOCATION_MIN_TIME_MS, 0f, listener, Looper.getMainLooper())
            }
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()?.let { if (isNewer(it)) lastLocation = it }
        }
        listening = providers.isNotEmpty()
    }

    private fun isNewer(candidate: Location): Boolean {
        val current = lastLocation ?: return true
        return candidate.time >= current.time
    }

    private suspend fun reportLocation() {
        val location = lastLocation ?: return
        // Never send an old fix as if it were current; the backend also enforces freshness.
        if (System.currentTimeMillis() - location.time > MAX_FIX_AGE_MS) return
        val body = buildMap {
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            if (location.hasAccuracy()) put("accuracyMeters", location.accuracy.toDouble())
        }
        runCatching { api.updateLocation(body) }
    }

    private suspend fun announceNewAssignments() {
        val page = runCatching { api.unreadNotifications() }.getOrNull()?.takeIf { it.isSuccessful }?.body() ?: return
        page.items.orEmpty()
            .filter { it.type.equals("ORDER_ASSIGNED", ignoreCase = true) && !it.id.isNullOrBlank() && !it.orderId.isNullOrBlank() }
            .filter { announcedNotificationIds.add(it.id!!) }
            .forEach { showSystemNotification(it.id!!, it.title, it.message, it.orderId!!) }
    }

    @SuppressLint("MissingPermission")
    private fun showSystemNotification(id: String, title: String?, message: String?, orderId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Delivery assignments", NotificationManager.IMPORTANCE_HIGH)
            (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_DELIVERY_ORDER_ID, orderId)
        }
        val pending = PendingIntent.getActivity(
            appContext, orderId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = message?.takeIf { it.isNotBlank() } ?: "Order #$orderId • Pickup: VJoyKart Store"
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title?.takeIf { it.isNotBlank() } ?: "New delivery assigned")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(appContext).notify(id.hashCode(), notification) }
    }

    companion object {
        private const val CHANNEL_ID = "delivery_assignments"
        private const val REPORT_INTERVAL_MS = 30_000L
        private const val LOCATION_MIN_TIME_MS = 15_000L
        private const val MAX_FIX_AGE_MS = 5 * 60_000L
    }
}
