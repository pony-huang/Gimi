# ---------------------------------------------------------------------------
# R8 / ProGuard keep rules for github.ponyhuang.gimi
#
# Each entry below covers a case where R8 cannot prove a class is reachable
# even though the runtime does (reflection, manifest lookup, native binding).
# Everything else is handled by consumer-rules.pro embedded in each AAR
# (Compose, Hilt, Room, openai-java, anthropic-java, google-adk,
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

# ---------------------------------------------------------------------------
# Jackson Databind
#
# OpenAI-compatible providers may return vendor extension objects nested inside
# tool-call payloads. R8 optimization of Jackson 2.19's container deserializers
# can clear MapDeserializer's contextual value deserializer in release builds;
# the next nested object then crashes in JsonDeserializer.getObjectIdReader().
# Keep the deserializer implementation from being rewritten while still
# allowing class-name obfuscation. The screenshot-visible failure path is:
# Map["lineList"] -> List[0] -> Map["pointInfoList"].
# ---------------------------------------------------------------------------
-keep,allowobfuscation class com.fasterxml.jackson.databind.JsonDeserializer { *; }
-keep,allowobfuscation class com.fasterxml.jackson.databind.deser.** { *; }

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
#
# java.beans.* (Introspector/BeanInfo/PropertyDescriptor) 是桌面 JVM 的类，Android
# 上没有；snakeyaml 2.x 的 PropertyUtils.getPropertiesMap 在 DEFAULT bean access 分支
# 里引用它们，R8 因此报 Missing class（由 ADK plugin ABI 的 -keep 规则把该代码路径
# 拖成可达后触发）。运行时可安全忽略：snakeyaml 的 PlatformFeatureDetector 在 ART 上
# 会把 beanAccess 自动切到 FIELD，永不执行 Introspector 分支（SKILL.md 只做 map/list
# 解析，不做类型化 bean 构造）。
# ---------------------------------------------------------------------------
-keeppackagenames org.yaml.snakeyaml.**
-dontwarn java.beans.**

# ---------------------------------------------------------------------------
# ADK plugin ABI (dynamic plugin loading)
#
# Plugins are loaded at runtime via DexClassLoader and resolve ADK types through
# the host classloader. The plugin APK is compiled against the original ADK
# class/member names, so the host's R8 must not strip or rename the ADK types
# reachable from com.google.adk.kt.plugins.Plugin (the plugin author contract),
# even when the host itself does not call every member.
# ---------------------------------------------------------------------------
-keep class com.google.adk.kt.plugins.** { *; }
-keep class com.google.adk.kt.callbacks.** { *; }
-keep class com.google.adk.kt.agents.** { *; }
-keep class com.google.adk.kt.events.** { *; }
-keep class com.google.adk.kt.models.** { *; }
-keep class com.google.adk.kt.tools.** { *; }
-keep class com.google.adk.kt.types.** { *; }
# 上面的 `{ *; }` 只保字段/方法，不含构造函数。插件构造这些 Kotlin 数据类时走带
# DefaultConstructorMarker 的合成默认参构造函数；host 自身可能不调用同一变体，R8 会把它
# 剥掉，插件加载即抛 NoSuchMethodError。这里显式保留所有 <init>，补齐插件 ABI。
-keepclassmembers class com.google.adk.kt.** { <init>(...); }
-keepclassmembers class github.ponyhuang.gimi.pluginapi.** { <init>(...); }

# ---------------------------------------------------------------------------
# Kotlin / coroutines ABI 桥接（插件编译期 compileOnly，运行时从 host 解析）
#
# 插件 APK 自带 kotlin-stdlib，但 kotlinx-coroutines 是 compileOnly、不打包；且
# plugin-api / ADK 的合成构造函数、挂起方法描述符里引用的 DefaultConstructorMarker、
# Continuation、CoroutineContext 也必须在 host 侧保持原名。R8 默认会把它们混淆成
# hz0/ht0 之类短名，导致插件按原名链接时 NoSuchMethodError / ClassNotFoundException。
# 这里显式保持这些运行时桥接类型的类名与成员名不变（即不让 R8 改名/收缩）。
# ---------------------------------------------------------------------------
-keep class kotlin.jvm.internal.DefaultConstructorMarker { *; }
-keep class kotlin.jvm.functions.** { *; }
-keep class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }
