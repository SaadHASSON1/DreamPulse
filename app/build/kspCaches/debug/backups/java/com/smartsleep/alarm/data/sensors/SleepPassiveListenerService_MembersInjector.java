package com.smartsleep.alarm.data.sensors;

import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class SleepPassiveListenerService_MembersInjector implements MembersInjector<SleepPassiveListenerService> {
  private final Provider<HealthServicesManager> healthServicesManagerProvider;

  private SleepPassiveListenerService_MembersInjector(
      Provider<HealthServicesManager> healthServicesManagerProvider) {
    this.healthServicesManagerProvider = healthServicesManagerProvider;
  }

  @Override
  public void injectMembers(SleepPassiveListenerService instance) {
    injectHealthServicesManager(instance, healthServicesManagerProvider.get());
  }

  public static MembersInjector<SleepPassiveListenerService> create(
      Provider<HealthServicesManager> healthServicesManagerProvider) {
    return new SleepPassiveListenerService_MembersInjector(healthServicesManagerProvider);
  }

  @InjectedFieldSignature("com.smartsleep.alarm.data.sensors.SleepPassiveListenerService.healthServicesManager")
  public static void injectHealthServicesManager(SleepPassiveListenerService instance,
      HealthServicesManager healthServicesManager) {
    instance.healthServicesManager = healthServicesManager;
  }
}
