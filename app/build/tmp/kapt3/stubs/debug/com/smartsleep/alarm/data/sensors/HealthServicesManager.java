package com.smartsleep.alarm.data.sensors;

import android.content.Context;
import androidx.health.services.client.HealthServices;
import androidx.health.services.client.data.DataType;
import androidx.health.services.client.data.PassiveListenerConfig;
import com.smartsleep.alarm.domain.model.SleepState;
import dagger.hilt.android.qualifiers.ApplicationContext;
import kotlinx.coroutines.flow.StateFlow;
import javax.inject.Inject;
import javax.inject.Singleton;

@javax.inject.Singleton()
@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0004\b\u0007\u0018\u00002\u00020\u0001B\u0013\b\u0007\u0012\b\b\u0001\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0004\b\u0004\u0010\u0005J\u000e\u0010\u0011\u001a\u00020\u0012H\u0086@\u00a2\u0006\u0002\u0010\u0013J\u0006\u0010\u0014\u001a\u00020\u0015J\u0006\u0010\u0016\u001a\u00020\u0015J\u000e\u0010\u0017\u001a\u00020\u00152\u0006\u0010\u0018\u001a\u00020\fR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\n\u001a\b\u0012\u0004\u0012\u00020\f0\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\r\u001a\b\u0012\u0004\u0012\u00020\f0\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u0019"}, d2 = {"Lcom/smartsleep/alarm/data/sensors/HealthServicesManager;", "", "context", "Landroid/content/Context;", "<init>", "(Landroid/content/Context;)V", "healthServicesClient", "Landroidx/health/services/client/HealthServicesClient;", "passiveMonitoringClient", "Landroidx/health/services/client/PassiveMonitoringClient;", "_sleepState", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/smartsleep/alarm/domain/model/SleepState;", "sleepState", "Lkotlinx/coroutines/flow/StateFlow;", "getSleepState", "()Lkotlinx/coroutines/flow/StateFlow;", "isSleepTrackingAvailable", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "startPassiveSleepMonitoring", "", "stopPassiveSleepMonitoring", "updateSleepState", "state", "app_debug"})
public final class HealthServicesManager {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.health.services.client.HealthServicesClient healthServicesClient = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.health.services.client.PassiveMonitoringClient passiveMonitoringClient = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.smartsleep.alarm.domain.model.SleepState> _sleepState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.smartsleep.alarm.domain.model.SleepState> sleepState = null;
    
    @javax.inject.Inject()
    public HealthServicesManager(@dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.smartsleep.alarm.domain.model.SleepState> getSleepState() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object isSleepTrackingAvailable(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion) {
        return null;
    }
    
    public final void startPassiveSleepMonitoring() {
    }
    
    public final void stopPassiveSleepMonitoring() {
    }
    
    public final void updateSleepState(@org.jetbrains.annotations.NotNull()
    com.smartsleep.alarm.domain.model.SleepState state) {
    }
}