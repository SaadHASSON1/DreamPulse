package com.smartsleep.alarm.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.smartsleep.alarm.data.repository.SleepRepository
import com.smartsleep.alarm.domain.model.SleepState
import com.smartsleep.alarm.receiver.AlarmReceiver
import com.smartsleep.alarm.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SleepMonitorService : Service() {

    @Inject lateinit var sleepRepository: SleepRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var healthServicesManager: com.smartsleep.alarm.data.sensors.HealthServicesManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sleepDurationMillis: Long = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.widget.Toast.makeText(this, "SERVICE STARTED!", android.widget.Toast.LENGTH_SHORT).show()
        sleepDurationMillis = intent?.getLongExtra(EXTRA_SLEEP_DURATION, 8 * 3600 * 1000L) ?: 0
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notificationHelper.getTrackingNotification("Waiting for you to fall asleep..."),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notificationHelper.getTrackingNotification("Waiting for you to fall asleep...")
            )
        }

        observeSleepState()
        sleepRepository.startMonitoring()

        return START_STICKY
    }

    private var wakeUpTime: Long = 0
    private var isSmartWindowActive = false

    private fun observeSleepState() {
        serviceScope.launch {
            sleepRepository.sleepState.collectLatest { state ->
                android.util.Log.d("SleepMonitor", "Current State: $state")
                
                if (state == SleepState.ASLEEP && !isSmartWindowActive && wakeUpTime == 0L) {
                    // اكتشاف النوم لأول مرة
                    serviceScope.launch {
                        sleepRepository.saveSleepStartTime(System.currentTimeMillis())
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(this@SleepMonitorService, "Sleep Detected!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    scheduleAlarm()
                } else if (isSmartWindowActive && state != SleepState.ASLEEP) {
                    // استيقاظ مبكر داخل النافذة الذكية!
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(this@SleepMonitorService, "Smart Wakeup Detected!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    triggerAlarmNow()
                }
            }
        }
    }

    private fun triggerAlarmNow() {
        val intent = Intent(this, com.smartsleep.alarm.receiver.AlarmReceiver::class.java)
        sendBroadcast(intent)
        stopSelf()
    }

    private fun scheduleAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        wakeUpTime = System.currentTimeMillis() + sleepDurationMillis
        val durationMin = sleepDurationMillis / 60000

        // تحديث الإشعار ليعرف المستخدم أننا نراقب الآن بذكاء
        updateNotification("Sleeping... Smart window active later.")

        // 1. الشاشة الكاملة (Activity)
        val activityIntent = Intent(this, com.smartsleep.alarm.ui.AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            // المحرك الأساسي: نرسل الإشارة للـ Receiver وهو يتولى كل شيء (الشاشة + الصوت)
            val receiverIntent = Intent(this, com.smartsleep.alarm.receiver.AlarmReceiver::class.java)
            val receiverPendingIntent = PendingIntent.getBroadcast(
                this, System.currentTimeMillis().toInt(), receiverIntent, 
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // نستخدم setAlarmClock لأنها الأقوى في إيقاظ النظام
            val alarmClockInfo = AlarmManager.AlarmClockInfo(wakeUpTime, receiverPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, receiverPendingIntent)
            
            // تفعيل النافذة الذكية قبل الموعد بـ 20 دقيقة
            serviceScope.launch {
                val smartWindowStartDelay = (sleepDurationMillis - 20 * 60 * 1000L).coerceAtLeast(0)
                kotlinx.coroutines.delay(smartWindowStartDelay)
                isSmartWindowActive = true
                updateNotification("Smart Window Active: Looking for light sleep...")
            }

            serviceScope.launch {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(this@SleepMonitorService, "Alarm set for ${durationMin} min!", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            val receiverIntent = Intent(this, com.smartsleep.alarm.receiver.AlarmReceiver::class.java)
            val receiverPendingIntent = PendingIntent.getBroadcast(
                this, 1, receiverIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wakeUpTime, receiverPendingIntent)
        }
    }

    private fun updateNotification(text: String) {
        val notification = notificationHelper.getTrackingNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NotificationHelper.NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        healthServicesManager.setTracking(false)
        healthServicesManager.updateSleepState(SleepState.UNKNOWN)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_SLEEP_DURATION = "extra_sleep_duration"
    }
}
