# Métodos chamados pelo JavaScript dos WebViews
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
