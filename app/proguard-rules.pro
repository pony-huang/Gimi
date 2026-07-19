# ---------------------------------------------------------------------------
# R8 / ProGuard keep rules for github.ponyhuang.asssistantai
#
# Each entry below covers a case where R8 cannot prove a class is reachable
# even though the runtime does (reflection, manifest lookup, native binding).
# Everything else is handled by consumer-rules.pro embedded in each AAR
# (Compose, Hilt, Room, AppFunctions, openai-java, anthropic-java, google-adk,
# vosk-android, ktor) — those are merged automatically.
# ---------------------------------------------------------------------------

# Preserve Kotlin metadata + annotation attributes used by reflection.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Preserve generic type information — kotlinx.serialization and Gson need it.
-keepattributes Signature
-keepattributes *Annotation*

# Don't warn about optional Kotlin coroutines internals — referenced reflectively
# at runtime by kotlinx-coroutines debug agents, but harmless if absent.
-dontwarn kotlinx.coroutines.debug.**

# ---------------------------------------------------------------------------
# kxml2 / XmlPullParser
#
# kxml2 jar is excluded via `configurations.all { exclude(...) }` in
# app/build.gradle.kts. Android's framework provides XmlResourceParser which
# implements XmlPullParser, so excluding kxml2 is safe and prevents R8 from
# erroring on "Library class implements program class".
# ---------------------------------------------------------------------------
-dontwarn org.xmlpull.v1.**
-dontwarn org.kxml2.**

# ---------------------------------------------------------------------------
# Gson-reflected data classes
#
# Gson instantiates these via java.lang.reflect without calling constructors
# directly. -keep + -keepclassmembers ensures field names survive renaming so
# the JSON ↔ object mapping still matches.
#
# Sites: LLMModelSelectionCodec, LLMModelServiceDatabase,
#        McpServerRepository, SpeechSynthesis.
# ---------------------------------------------------------------------------
-keep class github.ponyhuang.asssistantai.data.** { *; }
-keepclassmembers class github.ponyhuang.asssistantai.data.** {
    <init>(...);
    <fields>;
}

# ---------------------------------------------------------------------------
# kotlinx-serialization + Navigation 3
#
# The kotlinx-serialization compiler plugin emits serializer companions at
# build time, but Navigation 3 looks them up by class name from saved state.
# Keep the sealed interface + nested data objects / data classes.
# ---------------------------------------------------------------------------
-keep,includedescriptorclasses class github.ponyhuang.asssistantai.ui.navigation.** { *; }
-keepclassmembers class github.ponyhuang.asssistantai.ui.navigation.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# anthropic-java SDK
#
# The SDK ships consumer-rules that keep Jackson-annotated members, but R8
# still renames Kotlin synthetic methods and Companion accessors that the
# Jackson serialization layer reaches through reflection. The failure mode
# is `IllegalStateException("JsonMissing cannot be serialized")` raised from
# JsonMissing$Serializer — meaning the SDK's field-skipping logic no longer
# recognizes JsonField wrappers because their inner accessors were renamed.
#
# Force-keep the whole models tree (already huge via openai-style schema
# classes; obfuscation saves almost nothing here anyway).
# ---------------------------------------------------------------------------
-keep class com.anthropic.** { *; }
-keepclassmembers class com.anthropic.** { *; }
-dontwarn com.anthropic.**

# ---------------------------------------------------------------------------
# openai-java SDK
#
# Same JsonMissing failure mode as anthropic-java, with no bundled consumer
# rules at all (META-INF/proguard/ is absent from openai-java-core). Without
# these, even an empty "hello" message produces:
#
#   IllegalStateException: JsonMissing cannot be serialized (through reference
#   chain: com.openai.models.chat.completions.ChatCompletionCreateParams$a["messages"]
#   → ChatCompletionSystemMessageParam["name"])
#
# Keeping the entire `com.openai.**` tree costs another ~6 MB dex (the SDK
# already ships ~20 970 schema classes; obfuscation savings are minimal).
# ---------------------------------------------------------------------------
-keep class com.openai.** { *; }
-keepclassmembers class com.openai.** { *; }
-dontwarn com.openai.**

# Victools jsonschema-generator (transitive via google-genai / anthropic-java)
# references these on types that don't exist on Android. Suppress the warning
# so R8 doesn't treat them as missing classes.
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType

# ---------------------------------------------------------------------------
# Manifest entry points (belt-and-suspenders — Hilt plugin already keeps these)
# ---------------------------------------------------------------------------
-keep class github.ponyhuang.asssistantai.MainActivity { *; }
-keep class github.ponyhuang.asssistantai.voice.BluetoothVoiceService { *; }