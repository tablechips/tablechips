# R8 shrinks and obfuscates the release build. Ktor, kotlinx.serialization,
# CameraX and ZXing ship their own consumer rules; this file only holds what
# they leave out.

# Ktor's IntelliJ debugger detector reaches for java.lang.management, which
# Android does not have. It is only ever called under a debugger on a desktop
# JVM, and fails the same way with or without R8.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
