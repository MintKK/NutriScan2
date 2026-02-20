package com.nutriscan.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.nutriscan.data.local.AppDatabase
import com.nutriscan.data.local.entity.ActivityLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BroadcastReceiver for Activity Recognition Transition events.
 *
 * When Google's ML-based activity detection identifies a transition
 * (e.g., user starts walking, stops running), this receiver:
 * 1. Persists the event to Room via [AppDatabase]
 * 2. Updates the live [ActivityTransitionManager.currentActivity] StateFlow
 *
 * Note: BroadcastReceivers cannot use Hilt @AndroidEntryPoint,
 * so we obtain the database instance directly.
 */
class ActivityTransitionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ActivityTransitionRcvr"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) {
            Log.d(TAG, "No transition result in intent")
            return
        }

        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val pendingResult = goAsync()

        // Get DAO directly from the database singleton (no Hilt in BroadcastReceiver)
        val db = androidx.room.Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "nutriscan_database"
        ).fallbackToDestructiveMigration().build()
        val activityLogDao = db.activityLogDao()

        scope.launch {
            try {
                for (event in result.transitionEvents) {
                    val activityType = ActivityTransitionManager.activityTypeToString(
                        event.activityType
                    )
                    val transitionType = when (event.transitionType) {
                        ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "ENTER"
                        ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "EXIT"
                        else -> "UNKNOWN"
                    }

                    Log.i(TAG, "Transition: $activityType $transitionType")

                    // Persist to database
                    activityLogDao.insert(
                        ActivityLog(
                            activityType = activityType,
                            transitionType = transitionType,
                            date = dateFormat.format(Date())
                        )
                    )

                    // Update live state (only for ENTER transitions)
                    if (transitionType == "ENTER") {
                        ActivityTransitionManager.updateActivity(activityType)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing transitions", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
