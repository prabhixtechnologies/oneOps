-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class com.prabhix.operator.data.api.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
