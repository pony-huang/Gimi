# ---------------------------------------------------------------------------
# R8 / ProGuard keep rules for github.ponyhuang.gimi
#
# Each entry below covers a case where R8 cannot prove a class is reachable
# even though the runtime does (reflection, manifest lookup, native binding).
# Everything else is handled by consumer-rules.pro embedded in each AAR
# (Compose, Hilt, Room, openai-java, anthropic-java, google-adk,
# vosk-android, ktor) — those are merged automatically.
#
# 发版策略：只收缩、不混淆。历史上多次发版被 R8 改名坑（插件 ABI 的
# DefaultConstructorMarker/Continuation/coroutines 被改短名、反射/序列化改名），
# 而本项目已整树 keep github.ponyhuang.** / com.openai.** / com.anthropic.** /
# com.google.adk.kt.**，混淆本就不省多少。禁用改名后这些「改名类」问题彻底消失，
# 死代码收缩与资源收缩仍保留。缺失类告警（如 java.beans）仍靠下方 -dontwarn 处理。
# ---------------------------------------------------------------------------
-dontobfuscate

# Preserve Kotlin metadata + annotation attributes used by reflection.
-keepattributes Signature, InnerClasses, EnclosingMethod

# Preserve generic type information + annotations — kotlinx.serialization and Gson need them.
-keepattributes *Annotation*

# Don't warn about optional Kotlin coroutines internals — referenced reflectively
# at runtime by kotlinx-coroutines debug agents, but harmless if absent.
-dontwarn kotlinx.coroutines.debug.**

# ---------------------------------------------------------------------------
# App code: allow shrinking, block optimization, keep names
#
# This is an open-source, exploration-stage project, so obfuscation buys
# nothing here — names are preserved by -dontobfuscate. The rule below lets R8
# remove genuinely-unused first-party classes/members (shrinking) while still
# blocking R8 *optimization* of app code: a documented release inlining bug
# (JSONObject.put overload fabricated by a forEach inlining) crashed
# plugin-config saving, so app code is kept optimization-free. The
# reflection-serialization surfaces that must NOT shrink are protected
# individually below (Gson fields + generic signatures, kotlinx.serializer
# lookup, enum names). Example: R8 renamed ConversationToolConfiguration's
# fields and stripped the Map<String, Map<String, Set<String>>> signature, so
# Gson decoded the nested function-id sets as ArrayList and the Set checkcast
# in enabledOfficialFunctionIds crashed with ClassCastException on session
# restore.
# ---------------------------------------------------------------------------
-keep,allowshrinking class github.ponyhuang.gimi.** { *; }

# Feature destination serializers are resolved reflectively by Navigation3.
# Other @Serializable models use compile-time serializers whose field accesses
# remain visible to R8 and need no dedicated keep rule.
-keep class github.ponyhuang.gimi.feature.**Destination$* { *; }

# ObjectBox entity + generated box (native metadata binding).
-keep class github.ponyhuang.gimi.data.agent.tools.search.ToolVectorEntity { *; }
-keep class github.ponyhuang.gimi.data.agent.tools.search.MyObjectBox { *; }

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
# plugin-api ABI classes must be full shrink roots in the host APK: plugins
# compile against the original names and resolve them parent-first via the host
# classloader. (Previously this was implicit via the whole-app `{ *; }` keep.)
-keep class github.ponyhuang.gimi.pluginapi.** { *; }

# ---------------------------------------------------------------------------
# ADK 会话检查点编解码（反射桥接 internal JsonConverters）
#
# data:conversation 的 AdkEventCodec 通过 Class.forName + getMethod 反射调用
# com.google.adk.kt.sessions.room.JsonConverters 的 eventToJson/eventFromJson
# （Kotlin internal、仅 JVM public）。ADK 自身只直接调用 fromStateJson/toStateJson，
# R8 看不到反射链，release 收缩会剥掉 eventToJson/eventFromJson，导致
# AdkEventCodec 的 object 初始化抛 NoSuchMethodException，对话恢复时表现为
# ExceptionInInitializerError。全局已 -dontobfuscate，这条主要防收缩。
# ---------------------------------------------------------------------------
-keep class com.google.adk.kt.sessions.room.JsonConverters { *; }

# ---------------------------------------------------------------------------
# Kotlin / coroutines ABI 桥接（插件运行时从 host 解析）
#
# 插件 APK 自带 kotlin-stdlib，但 DexClassLoader 是 parent-first：只要 host 里同名
# 类存在，插件引用就解析到 host 副本。所以 host 必须完整保留 kotlin-stdlib 的类名
# 与成员（lazy / Intrinsics.checkNotNullParameter 等都可能被 R8 收缩掉）；否则插件
# 加载即 NoSuchMethodError（lazy/Intrinsics）或 NoSuchMethodError（合成构造器描述符
# 里的 DefaultConstructorMarker/Continuation 被改名）。kotlinx-coroutines 是 compileOnly、
# 插件不打包，同样需完整保留。全局已 -dontobfuscate，这两条主要防收缩。
# ---------------------------------------------------------------------------
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# ---------------------------------------------------------------------------
# Vosk 语音唤醒（JNA 原生绑定）
#
# vosk-android 0.3.75 依赖 net.java.dev.jna:jna:5.18.1（aar），两个 AAR 均不携带
# consumer-rules.pro。org.vosk.LibVosk 是 JNA Library 接口，Model/Recognizer 经
# com.sun.jna.Native 反射加载 libvosk.so / libjnidispatch.so；若 R8 收缩掉 JNA 内部类
# 或 vosk 接口方法，release 构建里 Model(modelPath) 会抛 UnsatisfiedLinkError /
# ClassNotFoundException，表现为「唤醒模型已下载但 switch 始终启动失败（无法加载模型）」。
# 全局已 -dontobfuscate，这里主要防收缩。
# ---------------------------------------------------------------------------
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-dontwarn com.sun.jna.**
-dontwarn org.vosk.**
