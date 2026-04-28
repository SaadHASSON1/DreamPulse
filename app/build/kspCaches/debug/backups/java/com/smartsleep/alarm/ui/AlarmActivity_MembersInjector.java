package com.smartsleep.alarm.ui;

import com.smartsleep.alarm.data.repository.SleepRepository;
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
public final class AlarmActivity_MembersInjector implements MembersInjector<AlarmActivity> {
  private final Provider<SleepRepository> sleepRepositoryProvider;

  private AlarmActivity_MembersInjector(Provider<SleepRepository> sleepRepositoryProvider) {
    this.sleepRepositoryProvider = sleepRepositoryProvider;
  }

  @Override
  public void injectMembers(AlarmActivity instance) {
    injectSleepRepository(instance, sleepRepositoryProvider.get());
  }

  public static MembersInjector<AlarmActivity> create(
      Provider<SleepRepository> sleepRepositoryProvider) {
    return new AlarmActivity_MembersInjector(sleepRepositoryProvider);
  }

  @InjectedFieldSignature("com.smartsleep.alarm.ui.AlarmActivity.sleepRepository")
  public static void injectSleepRepository(AlarmActivity instance,
      SleepRepository sleepRepository) {
    instance.sleepRepository = sleepRepository;
  }
}
