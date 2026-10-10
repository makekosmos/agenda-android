# JNA: reflection + native callbacks look up these by name.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# UniFFI-generated bindings (Structure fields, callback interfaces and
# Library methods are resolved reflectively by JNA).
-keep class com.mundus.engine.** { *; }
-keep class uniffi.** { *; }
-keep class com.mundus.agenda.** { *; }
