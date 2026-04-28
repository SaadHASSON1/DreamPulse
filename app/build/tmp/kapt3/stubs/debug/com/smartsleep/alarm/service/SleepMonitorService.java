package com.smartsleep.alarm.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import com.smartsleep.alarm.data.repository.SleepRepository;
import com.smartsleep.alarm.domain.model.SleepState;
import com.smartsleep.alarm.receiver.AlarmReceiver;
import com.smartsleep.alarm.util.NotificationHelper;
import dagger.hilt.android.AndroidEntryPoint;
import kotlinx.coroutines.Dispatchers;
import javax.inject.Inject;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000N\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0007\u0018\u0000 #2\u00020\u0001:\u0001#B\u0007\u00a2\u0006\u0004\b\u0002\u0010\u0003J\"\u0010\u0014\u001a\u00020\u00152\b\u0010\u0016\u001a\u0004\u0018\u00010\u00172\u0006\u0010\u0018\u001a\u00020\u00152\u0006\u0010\u0019\u001a\u00020\u0015H\u0016J\b\u0010\u001a\u001a\u00020\u001bH\u0002J\b\u0010\u001c\u001a\u00020\u001bH\u0002J\u0010\u0010\u001d\u001a\u00020\u001b2\u0006\u0010\u001e\u001a\u00020\u001fH\u0002J\b\u0010 \u001a\u00020\u001bH\u0016J\u0014\u0010!\u001a\u0004\u0018\u00010\"2\b\u0010\u0016\u001a\u0004\u0018\u00010\u0017H\u0016R\u001e\u0010\u0004\u001a\u00020\u00058\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0006\u0010\u0007\"\u0004\b\b\u0010\tR\u001e\u0010\n\u001a\u00020\u000b8\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\f\u0010\r\"\u0004\b\u000e\u0010\u000fR\u000e\u0010\u0010\u001a\u00020\u0011X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006$"}, d2 = {"Lcom/smartsleep/alarm/service/SleepMonitorService;", "Landroid/app/Service;", "<init>", "()V", "sleepRepository", "Lcom/smartsleep/alarm/data/repository/SleepRepository;", "getSleepRepository", "()Lcom/smartsleep/alarm/data/repository/SleepRepository;", "setSleepRepository", "(Lcom/smartsleep/alarm/data/repository/SleepRepository;)V", "notificationHelper", "Lcom/smartsleep/alarm/util/NotificationHelper;", "getNotificationHelper", "()Lcom/smartsleep/alarm/util/NotificationHelper;", "setNotificationHelper", "(Lcom/smartsleep/alarm/util/NotificationHelper;)V", "serviceScope", "Lkotlinx/coroutines/CoroutineScope;", "sleepDurationMillis", "", "onStartCommand", "", "intent", "Landroid/content/Intent;", "flags", "startId", "observeSleepState", "", "scheduleAlarm", "updateNotification", "text", "", "onDestroy", "onBind", "Landroid/os/IBinder;", "Companion", "app_debug"})
public final class SleepMonitorService extends android.app.Service {
    @javax.inject.Inject()
    public com.smartsleep.alarm.data.repository.SleepRepository sleepRepository;
    @javax.inject.Inject()
    public com.smartsleep.alarm.util.NotificationHelper notificationHelper;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope serviceScope = null;
    private long sleepDurationMillis = 0L;
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_SLEEP_DURATION = "extra_sleep_duration";
    @org.jetbrains.annotations.NotNull()
    public static final com.smartsleep.alarm.service.SleepMonitorService.Companion Companion = null;
    
    public SleepMonitorService() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.smartsleep.alarm.data.repository.SleepRepository getSleepRepository() {
        return null;
    }
    
    public final void setSleepRepository(@org.jetbrains.annotations.NotNull()
    com.smartsleep.alarm.data.repository.SleepRepository p0) {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.smartsleep.alarm.util.NotificationHelper getNotificationHelper() {
        return null;
    }
    
    public final void setNotificationHelper(@org.jetbrains.annotations.NotNull()
    com.smartsleep.alarm.util.NotificationHelper p0) {
    }
    
    @java.lang.Override()
    public int onStartCommand(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent, int flags, int startId) {
        return 0;
    }
    
    private final void observeSleepState() {
    }
    
    private final void scheduleAlarm() {
    }
    
    private final void updateNotification(java.lang.String text) {
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public android.os.IBinder onBind(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent) {
        return null;
    }
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003R\u000e\u0010\u0004\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0006"}, d2 = {"Lcom/smartsleep/alarm/service/SleepMonitorService$Companion;", "", "<init>", "()V", "EXTRA_SLEEP_DURATION", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}