package com.smartsleep.alarm.service;

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
public final class AlarmService_MembersInjector implements MembersInjector<AlarmService> {
  private final Provider<NotificationHelper> notificationHelperProvider;

  private AlarmService_MembersInjector(Provider<NotificationHelper> notificationHelperProvider) {
    this.notificationHelperProvider = notificationHelperProvider;
  }

  @Override
  public void injectMembers(AlarmService instance) {
    injectNotificationHelper(instance, notificationHelperProvider.get());
  }

  public static MembersInjector<AlarmService> create(
      Provider<NotificationHelper> notificationHelperProvider) {
    return new AlarmService_MembersInjector(notificationHelperProvider);
  }

  @InjectedFieldSignature("com.smartsleep.alarm.service.AlarmService.notificationHelper")
  public static void injectNotificationHelper(AlarmService instance,
      NotificationHelper notificationHelper) {
    instance.notificationHelper = notificationHelper;
  }
}
