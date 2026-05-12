# ProGuard Rules for DreamPulse 
-keep class com.x13labs.dreampulse.** { *; } 
-keep class androidx.health.** { *; } 
-dontwarn kotlin.** 
-keep class com.x13labs.dreampulse.ui.AlarmActivity { *; }
-keep class com.x13labs.dreampulse.service.AlarmService { *; }