package com.smartsleep.alarm.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import com.smartsleep.alarm.R;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;
import javax.inject.Singleton;

@javax.inject.Singleton()
@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u0007\u0018\u0000 \u000f2\u00020\u0001:\u0001\u000fB\u0013\b\u0007\u0012\b\b\u0001\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0004\b\u0004\u0010\u0005J\b\u0010\b\u001a\u00020\tH\u0002J\u000e\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rJ\u0006\u0010\u000e\u001a\u00020\u000bR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0010"}, d2 = {"Lcom/smartsleep/alarm/util/NotificationHelper;", "", "context", "Landroid/content/Context;", "<init>", "(Landroid/content/Context;)V", "notificationManager", "Landroid/app/NotificationManager;", "createNotificationChannels", "", "getTrackingNotification", "Landroid/app/Notification;", "content", "", "getAlarmNotification", "Companion", "app_debug"})
public final class NotificationHelper {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final android.app.NotificationManager notificationManager = null;
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String TRACKING_CHANNEL_ID = "tracking_channel";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ALARM_CHANNEL_ID = "alarm_channel";
    public static final int NOTIFICATION_ID = 1001;
    @org.jetbrains.annotations.NotNull()
    public static final com.smartsleep.alarm.util.NotificationHelper.Companion Companion = null;
    
    @javax.inject.Inject()
    public NotificationHelper(@dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    private final void createNotificationChannels() {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final android.app.Notification getTrackingNotification(@org.jetbrains.annotations.NotNull()
    java.lang.String content) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final android.app.Notification getAlarmNotification() {
        return null;
    }
    
    @kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002\u00a2\u0006\u0004\b\u0002\u0010\u0003R\u000e\u0010\u0004\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0005X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\t"}, d2 = {"Lcom/smartsleep/alarm/util/NotificationHelper$Companion;", "", "<init>", "()V", "TRACKING_CHANNEL_ID", "", "ALARM_CHANNEL_ID", "NOTIFICATION_ID", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}