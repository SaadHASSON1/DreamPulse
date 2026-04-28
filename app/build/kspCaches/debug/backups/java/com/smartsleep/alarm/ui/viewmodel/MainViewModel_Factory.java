package com.smartsleep.alarm.ui.viewmodel;

import android.content.Context;
import com.smartsleep.alarm.data.local.PreferencesManager;
import com.smartsleep.alarm.data.repository.SleepRepository;
import com.smartsleep.alarm.data.sensors.HealthServicesManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class MainViewModel_Factory implements Factory<MainViewModel> {
  private final Provider<Context> contextProvider;

  private final Provider<SleepRepository> sleepRepositoryProvider;

  private final Provider<PreferencesManager> preferencesManagerProvider;

  private final Provider<HealthServicesManager> healthServicesManagerProvider;

  private MainViewModel_Factory(Provider<Context> contextProvider,
      Provider<SleepRepository> sleepRepositoryProvider,
      Provider<PreferencesManager> preferencesManagerProvider,
      Provider<HealthServicesManager> healthServicesManagerProvider) {
    this.contextProvider = contextProvider;
    this.sleepRepositoryProvider = sleepRepositoryProvider;
    this.preferencesManagerProvider = preferencesManagerProvider;
    this.healthServicesManagerProvider = healthServicesManagerProvider;
  }

  @Override
  public MainViewModel get() {
    return newInstance(contextProvider.get(), sleepRepositoryProvider.get(), preferencesManagerProvider.get(), healthServicesManagerProvider.get());
  }

  public static MainViewModel_Factory create(Provider<Context> contextProvider,
      Provider<SleepRepository> sleepRepositoryProvider,
      Provider<PreferencesManager> preferencesManagerProvider,
      Provider<HealthServicesManager> healthServicesManagerProvider) {
    return new MainViewModel_Factory(contextProvider, sleepRepositoryProvider, preferencesManagerProvider, healthServicesManagerProvider);
  }

  public static MainViewModel newInstance(Context context, SleepRepository sleepRepository,
      PreferencesManager preferencesManager, HealthServicesManager healthServicesManager) {
    return new MainViewModel(context, sleepRepository, preferencesManager, healthServicesManager);
  }
}
