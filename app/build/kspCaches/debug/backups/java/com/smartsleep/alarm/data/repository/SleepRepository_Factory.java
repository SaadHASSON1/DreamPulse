package com.smartsleep.alarm.data.repository;

import com.smartsleep.alarm.data.local.PreferencesManager;
import com.smartsleep.alarm.data.sensors.HealthServicesManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
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
public final class SleepRepository_Factory implements Factory<SleepRepository> {
  private final Provider<HealthServicesManager> healthServicesManagerProvider;

  private final Provider<PreferencesManager> preferencesManagerProvider;

  private SleepRepository_Factory(Provider<HealthServicesManager> healthServicesManagerProvider,
      Provider<PreferencesManager> preferencesManagerProvider) {
    this.healthServicesManagerProvider = healthServicesManagerProvider;
    this.preferencesManagerProvider = preferencesManagerProvider;
  }

  @Override
  public SleepRepository get() {
    return newInstance(healthServicesManagerProvider.get(), preferencesManagerProvider.get());
  }

  public static SleepRepository_Factory create(
      Provider<HealthServicesManager> healthServicesManagerProvider,
      Provider<PreferencesManager> preferencesManagerProvider) {
    return new SleepRepository_Factory(healthServicesManagerProvider, preferencesManagerProvider);
  }

  public static SleepRepository newInstance(HealthServicesManager healthServicesManager,
      PreferencesManager preferencesManager) {
    return new SleepRepository(healthServicesManager, preferencesManager);
  }
}
