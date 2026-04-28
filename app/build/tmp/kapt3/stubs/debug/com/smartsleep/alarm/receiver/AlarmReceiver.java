package com.smartsleep.alarm.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Vibrator;
import android.os.VibrationEffect;
import com.smartsleep.alarm.util.NotificationHelper;
import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@dagger.hilt.android.AndroidEntryPoint()
@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0007\u0018\u00002\u00020\u0001B\u0007\u00a2\u0006\u0004\b\u0002\u0010\u0003J\u0018\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000fH\u0016R\u001e\u0010\u0004\u001a\u00020\u00058\u0006@\u0006X\u0087.\u00a2\u0006\u000e\n\u0000\u001a\u0004\b\u0006\u0010\u0007\"\u0004\b\b\u0010\t\u00a8\u0006\u0010"}, d2 = {"Lcom/smartsleep/alarm/receiver/AlarmReceiver;", "Landroid/content/BroadcastReceiver;", "<init>", "()V", "notificationHelper", "Lcom/smartsleep/alarm/util/NotificationHelper;", "getNotificationHelper", "()Lcom/smartsleep/alarm/util/NotificationHelper;", "setNotificationHelper", "(Lcom/smartsleep/alarm/util/NotificationHelper;)V", "onReceive", "", "context", "Landroid/content/Context;", "intent", "Landroid/content/Intent;", "app_debug"})
public final class AlarmReceiver extends android.content.BroadcastReceiver {
    @javax.inject.Inject()
    public com.smartsleep.alarm.util.NotificationHelper notificationHelper;
    
    public AlarmReceiver() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.smartsleep.alarm.util.NotificationHelper getNotificationHelper() {
        return null;
    }
    
    public final void setNotificationHelper(@org.jetbrains.annotations.NotNull()
    com.smartsleep.alarm.util.NotificationHelper p0) {
    }
    
    @java.lang.Override()
    public void onReceive(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    android.content.Intent intent) {
    }
}