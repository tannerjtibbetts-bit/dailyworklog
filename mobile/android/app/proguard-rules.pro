# The text-reading plugin can use extra ML Kit language packs (Chinese, Devanagari, Japanese,
# Korean). The app only reads Latin text and doesn't include them, so the shrinker shouldn't
# expect them.
-dontwarn com.google.mlkit.vision.text.chinese.**
-dontwarn com.google.mlkit.vision.text.devanagari.**
-dontwarn com.google.mlkit.vision.text.japanese.**
-dontwarn com.google.mlkit.vision.text.korean.**
