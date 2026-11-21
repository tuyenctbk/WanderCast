# WanderCast ProGuard / R8 Rules

# Keep WebView JavaScriptInterface bridge methods for KeylessStreetMapView
-keepclassmembers class com.example.ui.components.StreetMapJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# Preserve line numbers for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
