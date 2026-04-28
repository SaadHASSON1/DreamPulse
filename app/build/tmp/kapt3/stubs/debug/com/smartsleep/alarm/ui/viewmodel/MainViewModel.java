package com.smartsleep.alarm.ui.viewmodel;

import android.content.Context;
import android.content.Intent;
import androidx.lifecycle.ViewModel;
import com.smartsleep.alarm.data.repository.SleepRepository;
import com.smartsleep.alarm.domain.model.SleepState;
import com.smartsleep.alarm.service.SleepMonitorService;
import com.smartsleep.alarm.data.local.PreferencesManager;
import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;
import kotlinx.coroutines.flow.StateFlow;
import javax.inject.Inject;

@kotlin.Metadata(mv = {2, 2, 0}, k = 1, xi = 48, d1 = {"\u0000H\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0004\b\u0007\u0018\u00002\u00020\u0001B#\b\u0007\u0012\b\b\u0001\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0004\b\b\u0010\tJ\u000e\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u0011J\u0006\u0010\u001b\u001a\u00020\u0019J\u0006\u0010\u001c\u001a\u00020\u0019R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\n\u001a\b\u0012\u0004\u0012\u00020\f0\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\r\u001a\b\u0012\u0004\u0012\u00020\f0\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000fR\u0014\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00110\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00110\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u000fR\u0014\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00150\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0017\u0010\u0016\u001a\b\u0012\u0004\u0012\u00020\u00150\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u000f\u00a8\u0006\u001d"}, d2 = {"Lcom/smartsleep/alarm/ui/viewmodel/MainViewModel;", "Landroidx/lifecycle/ViewModel;", "context", "Landroid/content/Context;", "sleepRepository", "Lcom/smartsleep/alarm/data/repository/SleepRepository;", "preferencesManager", "Lcom/smartsleep/alarm/data/local/PreferencesManager;", "<init>", "(Landroid/content/Context;Lcom/smartsleep/alarm/data/repository/SleepRepository;Lcom/smartsleep/alarm/data/local/PreferencesManager;)V", "_isTracking", "Lkotlinx/coroutines/flow/MutableStateFlow;", "", "isTracking", "Lkotlinx/coroutines/flow/StateFlow;", "()Lkotlinx/coroutines/flow/StateFlow;", "_sleepDurationHours", "", "sleepDurationHours", "getSleepDurationHours", "_currentSleepState", "Lcom/smartsleep/alarm/domain/model/SleepState;", "currentSleepState", "getCurrentSleepState", "setDuration", "", "hours", "startTracking", "stopTracking", "app_debug"})
@dagger.hilt.android.lifecycle.HiltViewModel()
public final class MainViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context context = null;
    @org.jetbrains.annotations.NotNull()
    private final com.smartsleep.alarm.data.repository.SleepRepository sleepRepository = null;
    @org.jetbrains.annotations.NotNull()
    private final com.smartsleep.alarm.data.local.PreferencesManager preferencesManager = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Boolean> _isTracking = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> isTracking = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.lang.Integer> _sleepDurationHours = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.lang.Integer> sleepDurationHours = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.smartsleep.alarm.domain.model.SleepState> _currentSleepState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.smartsleep.alarm.domain.model.SleepState> currentSleepState = null;
    
    @javax.inject.Inject()
    public MainViewModel(@dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    com.smartsleep.alarm.data.repository.SleepRepository sleepRepository, @org.jetbrains.annotations.NotNull()
    com.smartsleep.alarm.data.local.PreferencesManager preferencesManager) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Boolean> isTracking() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.lang.Integer> getSleepDurationHours() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.smartsleep.alarm.domain.model.SleepState> getCurrentSleepState() {
        return null;
    }
    
    public final void setDuration(int hours) {
    }
    
    public final void startTracking() {
    }
    
    public final void stopTracking() {
    }
}