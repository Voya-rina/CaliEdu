# Chaquopy 的 Java 类是运行时按类名反射找的，R8 看不到任何引用，会被整个删掉。
-keep class com.chaquo.python.** { *; }
-keep class com.chaquo.python.android.** { *; }

# kotlinx.serialization：保留生成的 serializer 与 Companion，
# 否则 Record / Plan 的 JSON 读写会在 release 里直接崩。
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 导出用的 FileProvider 与打印 Activity 由系统按名字启动
-keep class com.voyarina.caliedu.ui.PrintActivity { *; }
