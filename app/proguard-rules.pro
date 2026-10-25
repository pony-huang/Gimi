# ---------------------------------------------------------------------------
# R8 / ProGuard keep rules for github.ponyhuang.gimi
#
# Each entry below covers a case where R8 cannot prove a class is reachable
# even though the runtime does (reflection, manifest lookup, native binding).
# Everything else is handled by consumer-rules.pro embedded in each AAR
# (Compose, Hilt, Room, openai-java, anthropic-java, google-adk, ktor) —
# those are merged automatically.
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

# OpenAI/Anthropic 的 JsonNull 通过 @JsonSerialize 反射创建 NullSerializer。
# 取消工具后重发时，历史工具结果的 error 可能包含该 JSON 空值。R8 会把
# NullSerializer 合并到 RawSerializer 并将无参构造改为带分派参数的构造，
# 导致 Map["error"] 编码时报 "has no default (no arg) constructor"。
# 保留这个反射入口，防止类合并与构造裁剪；不扩大到整个 serializer 包。
-keep class com.fasterxml.jackson.databind.ser.std.NullSerializer { *; }

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
# ADK 的 Room 会话转换器在运行时通过 kotlinx.serialization 的 contextual
# `Any` serializer 编码工具返回的 JSON-native Map。该注册点藏在库的序列化 DSL
# lambda 中，R8 无法可靠追踪；一旦收缩，正式版会在工具响应落库时报
# "Serializer for class 'Any' is not found"。完整保留这个很小的运行时包，
# 保证正常工具响应仍可持久化，而不是将其误判为会话错误丢弃。
-keep class com.google.adk.kt.serialization.** { *; }
# 上面的 `{ *; }` 只保字段/方法，不含构造函数。插件构造这些 Kotlin 数据类时走带
# DefaultConstructorMarker 的合成默认参构造函数；host 自身可能不调用同一变体，R8 会把它
# 剥掉，插件加载即抛 NoSuchMethodError。这里显式保留所有 <init>，补齐插件 ABI。
-keepclassmembers class com.google.adk.kt.** { <init>(...); }

# A2A 线协议 SDK（a2a-java，google-adk-kotlin-a2a 的底层依赖）。AgentCard 等请求/
# 响应 record 由 Gson 反射序列化，改名或收缩都会让 JSON-RPC 载荷与字段名错位；
# 与 com.google.adk.kt.** 的处理一致，整个包禁用改名并保留成员。
-keep class org.a2aproject.sdk.** { *; }
# proto-google-common-protos / protobuf 运行时会被描述符注册与 JsonFormat 反射读取：
# a2a.proto 引用 google.api.FieldBehavior 字段注解扩展（注册时反射查枚举 valueOf），
# A2A 消息 metadata 等 well-known 类型（Struct/Value/Any…）在解析时反射调 newBuilder。
# 丢失即 release 运行时 IllegalStateException（首次为闪退，后被聊天失败态捕获）。
# 本仓库 packaging 排除了 META-INF/proguard/**，protobuf 自带 consumer 规则不生效，
# 需手工整体保留；proto-google-common-protos 生成类一并保留。
-keep class com.google.protobuf.** { *; }
-keep class !com.google.api.client.**,com.google.api.** { *; }
-keep class com.google.rpc.** { *; }
-keep class com.google.type.** { *; }
-keep class com.google.logging.type.** { *; }
-keep class com.google.longrunning.** { *; }
-keep class com.google.geo.type.** { *; }
# a2a-java 的可选 HTTP 传输实现（vertx）在 compileOnly/provided scope，运行时不存在，
# 类路径扫描时的引用由 R8 忽略即可；spec-grpc 生成代码以 provided scope 引用
# io.grpc/javax.annotation，JSON-RPC 路径不触达，一并忽略。
-dontwarn org.a2aproject.sdk.client.http.vertx.**
-dontwarn io.vertx.**
-dontwarn io.grpc.**
-dontwarn javax.annotation.**
# JdkA2AHttpClient 被 keep 规则保留，其方法体引用桌面 JVM 的 java.net.http（Android
# 无此类）：应用路径永远注入 AndroidA2AHttpClient，JdkA2AHttpClient 不会被实例化或
# 加载，缺失类可安全忽略。org.mapstruct.* 是 spec-grpc 生成 mapper 引用的 provided
# 注解，运行时反射不读取，同样忽略。
-dontwarn java.net.http.**
-dontwarn org.mapstruct.**

# Official Android MCP checks these compileOnly runtime dependencies with
# Class.forName through a loader lambda before creating its Toolset. The ADK
# tools keep rule above does not make the external SDK/engine classes keep
# roots. Keep these two lookup targets against removal and class merging;
# -dontobfuscate alone only preserves names of classes that survive shrinking.
-keep class io.modelcontextprotocol.kotlin.sdk.client.Client { *; }
-keep class io.ktor.client.engine.okhttp.OkHttp { *; }

# plugin-api ABI classes must be full shrink roots in the host APK: plugins
# compile against the original names and resolve them parent-first via the host
# classloader. (Previously this was implicit via the whole-app `{ *; }` keep.)
-keep class github.ponyhuang.gimi.pluginapi.** { *; }

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
# Shizuku 在独立 shell 进程中按类名反射构造 UserService；保留入口及销毁事务。
-keep class github.ponyhuang.gimi.data.mobileuse.ShellMobileUseService {
    public <init>();
    public <init>(android.content.Context);
    public void destroy();
}
