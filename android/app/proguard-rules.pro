# Rules para el modo release. Anade aqui reglas especificas segun aparezcan.

# NewPipeExtractor usa reflection para sus services
-keep class org.schabi.newpipe.extractor.** { *; }
-keep interface org.schabi.newpipe.extractor.** { *; }

# Kotlin metadata
-keep class kotlin.Metadata { *; }
