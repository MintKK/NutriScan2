package com.nutriscan.sensor

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Google Activity Recognition Transition API.
 *
 * Registers for ENTER/EXIT transitions on: WALKING, RUNNING, ON_BICYCLE, STILL.
 * When transitions occur, [ActivityTransitionReceiver] receives them and
 * persists to the database via [ActivityRepository].
 *
 * Also exposes a companion [MutableStateFlow] for live activity state so
 * the ViewModel can observe it without a database round-trip.
 */
class ActivityTransitionManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "ActivityTransitionMgr"
        private const val REQUEST_CODE = 2001

        /** Live current activity — updated by [ActivityTransitionReceiver]. */
        private val _currentActivity = MutableStateFlow("UNKNOWN")
        val currentActivity: StateFlow<String> = _currentActivity.asStateFlow()

        /** Called by the BroadcastReceiver when a transition event arrives. */
        fun updateActivity(activityType: String) {
            _currentActivity.value = activityType
            Log.d(TAG, "Activity updated: $activityType")
        }

        /** Convert DetectedActivity type int to readable string. */
        fun activityTypeToString(type: Int): String = when (type) {
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.ON_BICYCLE -> "CYCLING"
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.ON_FOOT -> "WALKING" // ON_FOOT is parent of WALKING/RUNNING
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            else -> "UNKNOWN"
        }
    }

    private var isRegistered = false

    /**
     * Start listening for activity transitions.
     * Requires ACTIVITY_RECOGNITION permission on API 29+.
     */
    fun start() {
        if (isRegistered) {
            Log.w(TAG, "Already registered")
            return
        }

        // Check permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "ACTIVITY_RECOGNITION permission not granted, skipping")
                return
            }
        }

        val transitions = buildTransitionList()
        val request = ActivityTransitionRequest(transitions)
        val pendingIntent = getPendingIntent()

        try {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(request, pendingIntent)
                .addOnSuccessListener {
                    isRegistered = true
                    Log.i(TAG, "Activity transition updates registered")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to register activity transitions", e)
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException registering transitions", e)
        }
    }

    /** Stop listening for activity transitions. */
    fun stop() {
        if (!isRegistered) return

        try {
            ActivityRecognition.getClient(context)
                .removeActivityTransitionUpdates(getPendingIntent())
                .addOnSuccessListener {
                    isRegistered = false
                    Log.i(TAG, "Activity transition updates removed")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to remove activity transitions", e)
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException removing transitions", e)
        }
    }

    private fun buildTransitionList(): List<ActivityTransition> {
        val activityTypes = listOf(
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.STILL,
            DetectedActivity.IN_VEHICLE
        )

        return activityTypes.flatMap { activityType ->
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build()
            )
        }
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}
