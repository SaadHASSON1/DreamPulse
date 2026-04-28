package com.smartsleep.alarm.service;

import com.smartsleep.alarm.data.repository.SleepRepository;
import com.smartsleep.alarm.data.sensors.HealthServicesManager;
import com.smartsleep.alarm.util.NotificationHelper;
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
public final class SleepMonitorService_MembersInjector implements MembersInjector<SleepMonitorService> {
  private final Provider<SleepRepository> sleepRepositoryProvider;

  private final Provider<NotificationHelper> notificationHelperProvider;

  private final Provider<HealthServicesManager> healthServicesManagerProvider;

  private SleepMonitorService_MembersInjector(Provider<SleepRepository> sleepRepositoryProvider,
      Provider<NotificationHelper> notificationHelperProvider,
      Provider<HealthServicesManager> healthServicesManagerProvider) {
    this.sleepRepositoryProvider = sleepRepositoryProvider;
    this.notificationHelperProvider = notificationHelperProvider;
    this.healthServicesManagerProvider = healthServicesManagerProvider;
  }

  @Override
  public void injectMembers(SleepMonitorService instance) {
    injectSleepRepository(instance, sleepRepositoryProvider.get());
    injectNotificationHelper(instance, notificationHelperProvider.get());
    injectHealthServicesManager(instance, healthServicesManagerProvider.get());
  }

  public static MembersInjector<SleepMonitorService> create(
      Provider<SleepRepository> sleepRepositoryProvider,
      Provider<NotificationHelper> notificationHelperProvider,
      Provider<HealthServicesManager> healthServicesManagerProvider) {
    return new SleepMonitorService_MembersInjector(sleepRepositoryProvider, notificationHelperProvider, healthServicesManagerProvider);
  }

  @InjectedFieldSignature("com.smartsleep.alarm.service.SleepMonitorService.sleepRepository")
  public static void injectSleepRepository(SleepMonitorService instance,
      SleepRepository sleepRepository) {
    instance.sleepRepository = sleepRepository;
  }

  @InjectedFieldSignature("com.smartsleep.alarm.service.SleepMonitorService.notificationHelper")
  public static void injectNotificationHelper(SleepMonitorService instance,
      NotificationHelper notificationHelper) {
    instance.notificationHelper = notificationHelper;
  }

  @InjectedFieldSignature("com.smartsleep.alarm.service.SleepMonitorService.healthServicesManager")
  public static void injectHealthServicesManager(SleepMonitorService instance,
      HealthServicesManager healthServicesManager) {
    instance.healthServicesManager = healthServicesManager;
  }
}
