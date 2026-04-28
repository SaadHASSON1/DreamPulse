package com.smartsleep.alarm.receiver;

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
public final class AlarmReceiver_MembersInjector implements MembersInjector<AlarmReceiver> {
  private final Provider<NotificationHelper> notificationHelperProvider;

  private AlarmReceiver_MembersInjector(Provider<NotificationHelper> notificationHelperProvider) {
    this.notificationHelperProvider = notificationHelperProvider;
  }

  @Override
  public void injectMembers(AlarmReceiver instance) {
    injectNotificationHelper(instance, notificationHelperProvider.get());
  }

  public static MembersInjector<AlarmReceiver> create(
      Provider<NotificationHelper> notificationHelperProvider) {
    return new AlarmReceiver_MembersInjector(notificationHelperProvider);
  }

  @InjectedFieldSignature("com.smartsleep.alarm.receiver.AlarmReceiver.notificationHelper")
  public static void injectNotificationHelper(AlarmReceiver instance,
      NotificationHelper notificationHelper) {
    instance.notificationHelper = notificationHelper;
  }
}
