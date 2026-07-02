# ─────────────────────────────────────────────────────────────────────
# BlackBox Virtual Engine — ProGuard Keep Rules
#
# Real BlackBox AAR classes from top.niunaijun.blackbox.*
# Native library: libblackbox.so (built by NDK via Android.mk)
# ─────────────────────────────────────────────────────────────────────

# Keep ALL BlackBox core classes (auto-merged from AAR)
-keep class top.niunaijun.blackbox.** { *; }
-keep class top.niunaijun.blackbox.proxy.** { *; }

# Keep our wrapper classes
-keep class com.utility.toolbox.service.BlackBoxEngine { *; }
-keep class com.utility.toolbox.service.BlackBoxEngine$DeviceIdentity { *; }
-keep class com.utility.toolbox.service.AntiDetectionManager { *; }

# Keep all native methods (JNI bridge into libblackbox.so)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Android framework classes that BlackBox intercepts via reflection
-keep class android.app.ActivityManager { *; }
-keep class android.app.IActivityManager { *; }
-keep class android.content.pm.IPackageManager { *; }
-keep class android.content.pm.PackageManager { *; }
-keep class android.telephony.TelephonyManager { *; }
-keep class android.os.Build { *; }
-keep class android.provider.Settings$Secure { *; }
-keep class android.net.wifi.WifiManager { *; }
-keep class android.os.Process { *; }
-keep class android.os.UserHandle { *; }

# Keep FreeReflection library (used by BlackBox)
-keep class com.github.tiann.FreeReflection.** { *; }

# Keep Room entities and DAOs
-keep class com.utility.toolbox.data.local.entity.** { *; }
-keep class com.utility.toolbox.data.local.dao.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep reflection-related attributes
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep Parcelable/Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
