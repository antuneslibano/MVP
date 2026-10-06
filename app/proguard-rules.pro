# Room e Compose já fornecem suas regras (consumer rules).
# Mantém nomes das entidades usadas no backup JSON (usa campos explícitos, sem reflexão).
-keepattributes *Annotation*

# PdfBox-Android (leitura do PDF da nota fiscal): carrega classes e recursos por nome.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn com.tom_roush.pdfbox.**
