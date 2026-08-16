# ---------------------------------------------------------------------------
# R8 / ProGuard keep rules for github.ponyhuang.gimi
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

# Reactor exposes optional Micrometer context propagation and BlockHound service hooks. The app
# does not enable either integration; suppress only the exact optional API types reported by R8.
-dontwarn io.micrometer.context.ContextAccessor
-dontwarn io.micrometer.context.ContextRegistry
-dontwarn io.micrometer.context.ContextSnapshot$Scope
-dontwarn io.micrometer.context.ContextSnapshot
-dontwarn io.micrometer.context.ContextSnapshotFactory$Builder
-dontwarn io.micrometer.context.ContextSnapshotFactory
-dontwarn io.micrometer.context.ThreadLocalAccessor
-dontwarn reactor.blockhound.integration.BlockHoundIntegration

# ---------------------------------------------------------------------------
# App code: no obfuscation, no member stripping
#
# This is an open-source, exploration-stage project, so obfuscation buys
# nothing here — but a missing keep rule repeatedly breaks reflection-based
# serialization (Gson fields + generic signatures, kotlinx.serializer lookup,
# enum names). Example: R8 renamed ConversationToolConfiguration's fields and
# stripped the Map<String, Map<String, Set<String>>> signature, so Gson decoded
# the nested function-id sets as ArrayList and the Set checkcast in
# enabledOfficialFunctionIds crashed with ClassCastException on session restore.
# Keeping the whole app package also keeps release stack traces readable
# without a mapping file. R8 still shrinks and obfuscates third-party code.
# ---------------------------------------------------------------------------
-keep class github.ponyhuang.gimi.** { *; }

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
# SnakeYAML (transitive via google-adk skills)
#
# R8 flattens obfuscated classes into the default package (TypeDescription -> mo6),
# and a class in the default package makes Class.getPackage() return null on ART.
# TypeDescription / PropertySubstitute call getPackage().getName() unconditionally
# in their initializers, so the first SKILL.md parse dies with
# ExceptionInInitializerError(NPE) and the class stays poisoned for the process.
# Keep snakeyaml's package names so getPackage() stays non-null; class-name
# obfuscation and shrinking inside the packages are unaffected.
# ---------------------------------------------------------------------------
-keeppackagenames org.yaml.snakeyaml.**
