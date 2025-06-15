# Keep VPN service classes
-keep class com.xanspot.net.VpnServiceImpl { *; }
-keep class android.net.VpnService { *; }

# Keep Flutter native channel classes
-keep class io.flutter.plugin.common.MethodChannel { *; }
-keep class io.flutter.embedding.engine.FlutterEngine { *; }
-keep class io.flutter.embedding.android.FlutterActivity { *; }

# Prevent R8 from removing ParcelFileDescriptor used in VpnServiceImpl
-keep class android.os.ParcelFileDescriptor { *; }

# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*, *Signature*

# Standard Flutter ProGuard rules
-dontwarn io.flutter.embedding.**
-keep class io.flutter.embedding.** { *; }
-dontoptimize
-dontpreverify