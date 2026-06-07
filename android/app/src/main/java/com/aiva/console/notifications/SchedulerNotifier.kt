package com.aiva.console.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aiva.console.R

/** System notification raised when a scheduled agent job finishes. */
object SchedulerNotifier {

    private const val CHANNEL_ID = "aiva_scheduler"

    fun notify(context: Context, name: String, result: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Scheduled jobs",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Results of jobs Aiva runs on schedule" }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return // user declined; never crash over a notification
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_aiva)
            .setContentTitle("Schedule finished: $name")
            .setContentText(result.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(result.take(500)))
            .setAutoCancel(true)
            .build()
        nm.notify(name.hashCode(), notification)
    }
}
