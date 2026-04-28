package com.smartsleep.alarm.data.sensors;

import androidx.health.services.client.PassiveListenerService;
import androidx.health.services.client.data.PassiveMonitoringUpdate;
import androidx.health.services.client.data.UserActivityState;
import com.smartsleep.alarm.domain.model.SleepState;

@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0007\u00a2\u0006\u0004\b\u0002\u0010\u0003J\u0010\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u0007H\u0016\u00a8\u0006\b"}, d2 = {"Lcom/smartsleep/alarm/data/sensors/SleepPassiveListenerService;", "Landroidx/health/services/client/PassiveListenerService;", "<init>", "()V", "onPassiveMonitoringUpdate", "", "update", "Landroidx/health/services/client/data/PassiveMonitoringUpdate;", "app_debug"})
public final class SleepPassiveListenerService extends androidx.health.services.client.PassiveListenerService {
    
    public SleepPassiveListenerService() {
        super();
    }
    
    public void onPassiveMonitoringUpdate(@org.jetbrains.annotations.NotNull()
    androidx.health.services.client.data.PassiveMonitoringUpdate update) {
    }
}