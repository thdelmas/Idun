# ProGuard rules. Keep minimal — Idun has no heavy reflection-based libs.

# Room generates code; keep its generated DAOs and entities.
-keep class com.idun.app.data.** { *; }

# Companion-write side effects rely on string metric keys reaching ContentResolver.
-keep class com.idun.app.bios.** { *; }
