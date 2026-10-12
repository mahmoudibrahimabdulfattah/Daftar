# Every rule here is backed by evidence from the release mapping (androidApp/build/outputs/mapping/release).
# Library consumer rules (SQLCipher, Koin, kotlinx.serialization, Room, Compose, Ktor) ship with their artifacts and
# are not repeated here.

# JNA is the native bridge behind libsodium on Android. libjnidispatch.so finds JNA's members by name over JNI, and
# the bindings bind the libsodium functions by interface method name. Without these rules R8 renames
# com.sun.jna.Pointer.peer, so every libsodium call fails at runtime in release.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keepclasseswithmembers class * {
    native <methods>;
}
-keep interface com.ionspin.kotlin.crypto.JnaLibsodiumInterface { *; }

# JNA's desktop AWT helper references these classes. Android has no AWT, and the helper never runs there, so the
# references are suppressed by name (the four R8 reported as missing), not by package.
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window
