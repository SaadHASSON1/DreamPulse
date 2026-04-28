package com.smartsleep.alarm.data.repository;

import com.smartsleep.alarm.data.sensors.HealthServicesManager;
import com.smartsleep.alarm.domain.model.SleepState;
import kotlinx.coroutines.flow.StateFlow;
import javax.inject.Inject;
import javax.inject.Singleton;

@javax.inject.Singleton()
@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\b\u0007\u0018\u00002\u00020\u0001B\u0011\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0004\b\u0004\u0010\u0005J\u000e\u0010\u000b\u001a\u00020\fH\u0086@\u00a2\u0006\u0002\u0010\rJ\u0006\u0010\u000e\u001a\u00020\u000fJ\u0006\u0010\u0010\u001a\u00020\u000fR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\b0\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u0011"}, d2 = {"Lcom/smartsleep/alarm/data/repository/SleepRepository;", "", "healthServicesManager", "Lcom/smartsleep/alarm/data/sensors/HealthServicesManager;", "<init>", "(Lcom/smartsleep/alarm/data/sensors/HealthServicesManager;)V", "sleepState", "Lkotlinx/coroutines/flow/StateFlow;", "Lcom/smartsleep/alarm/domain/model/SleepState;", "getSleepState", "()Lkotlinx/coroutines/flow/StateFlow;", "isTrackingSupported", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "startMonitoring", "", "stopMonitoring", "app_debug"})
public final class SleepRepository {
    @org.jetbrains.annotations.NotNull()
    private final com.smartsleep.alarm.data.sensors.HealthServicesManager healthServicesManager = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.smartsleep.alarm.domain.model.SleepState> sleepState = null;
    
    @javax.inject.Inject()
    public SleepRepository(@org.jetbrains.annotations.NotNull()
    com.smartsleep.alarm.data.sensors.HealthServicesManager healthServicesManager) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.smartsleep.alarm.domain.model.SleepState> getSleepState() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object isTrackingSupported(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Boolean> $completion) {
        return null;
    }
    
    public final void startMonitoring() {
    }
    
    public final void stopMonitoring() {
    }
}