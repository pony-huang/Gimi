# R8 / APK shrinkage

`app/build.gradle.kts` enables `isMinifyEnabled = true` and `isShrinkResources = true`
on the `release` build type. The hand-written rules in `app/proguard-rules.pro` cover
the few SDKs whose consumer rules are not sufficient for this project. Everything else
relies on the consumer-rules.pro embedded in each AAR (Compose, Hilt, Room,
AppFunctions, Ktor, google-adk, openai-java, vosk-android — merged automatically by
AGP).

## Baseline sizes

| Build              | APK             | Size   | Excl. voice |
| ------------------ | --------------- | ------ | ----------- |
| `assembleDebug`    | universal       | 221 MB | ~180 MB     |
| `assembleRelease`  | arm64-v8a       | 92 MB  | ~50 MB      |
| `assembleRelease`  | x86_64          | 92 MB  | ~50 MB      |
| `assembleRelease`  | universal       | 121 MB | ~79 MB      |

Voice model (`assets/voice/vosk-model-small-cn-0.22.zip`, 42 MB) is bundled for
offline wake-word; see `bluetooth-voice-wake.md`. The "Excl. voice" column is what
a hypothetical Play Asset Delivery split would show for the code-only download.

Before this change the `release` block had `optimization { enable = false }` and no
`proguardFiles`. Combined with `material-icons-extended` (~36 K icons, only 32 used)
and four-ABI native libs, the dex alone was 144 MB across 25 dex files. R8 brings
dex down to ~32 MB across 2 dex files. The AI SDKs (anthropic-java, openai-java)
are force-kept and account for the bulk of the remaining dex — see the rules
below.

## Other build configuration in this change

- `splits.abi` block emits separate `arm64-v8a` and `x86_64` APKs. `minSdk = 35`
  so the 32-bit ABIs are no longer needed. `isUniversalApk = true` is kept so the
  shared Meizu 20 Pro can `adb install` without picking an ABI.
- `packaging.resources.excludes` adds `META-INF/native-image/**`,
  `META-INF/proguard/**`, `META-INF/LICENSE*`, `META-INF/NOTICE*`. The first one
  strips ~4 MB of GraalVM native-image metadata shipped by google-genai,
  google-auth, google-http-client, jansi, opentelemetry — unused on Android.
- `release.signingConfig = signingConfigs.getByName("debug")` so `assembleRelease`
  produces an installable APK for local verification. Replace with a real upload
  keystore before publishing.
- `gradle.properties` `org.gradle.jvmargs` bumped from `-Xmx2048m` to
  `-Xmx4096m` because AGP 9.2.1 + R8 full mode OOMs on this dependency tree at 2 GB.
- `ktor-client-okhttp` is NOT removed — `McpToolRegistry` constructs
  `HttpClient(OkHttp)` explicitly to share the OkHttp pool used by openai-java and
  anthropic-java. The engine artifact is required, not redundant with
  ktor-client-core.

## Why each keep rule is needed

R8 in full mode (AGP 8+ default) is aggressive about removing code that is not
provably reachable. The bundled consumer-rules handle the obvious cases; the rules
below patch the holes that surfaced on the Meizu smoke test.

### kxml2 / `org.xmlpull.v1.XmlPullParser`

```proguard
-dontwarn org.xmlpull.v1.**
-dontwarn org.kxml2.**
```

The `kxml2` jar ships a copy of the `XmlPullParser` interface for non-Android
targets, and Android's framework already provides `android.content.res.XmlResourceParser`
implementing the same interface. With both present, R8 fails with:

> Library class android.content.res.XmlResourceParser implements program class
> org.xmlpull.v1.XmlPullParser (origin: .../kxml2-2.3.0.jar)

The fix is to exclude `kxml2` from the release runtime configurations (not from
`lint` / `test`, which need XmlPullParserException at build time):

```kotlin
listOf("releaseRuntimeClasspath", "releaseCompileClasspath", "releaseRuntimeOnly").forEach { configName ->
    configurations.findByName(configName)?.exclude(group = "net.sf.kxml", module = "kxml2")
}
```

### Gson-reflected data classes

```proguard
-keep class github.ponyhuang.asssistantai.data.** { *; }
-keepclassmembers class github.ponyhuang.asssistantai.data.** {
    <init>(...);
    <fields>;
}
```

Gson drives `LLMModelSelectionCodec`, `LLMModelServiceDatabase` (`StoredModel*`),
`McpServerRepository`, and `SpeechSynthesis` through `Class<T>::class.java`. R8
sees the typed reference but cannot prove the reflective construction, so the
field names get renamed and the JSON ⇄ object mapping silently breaks. Keeping
constructors + field names preserves both sides.

### kotlinx-serialization + Navigation 3

```proguard
-keep,includedescriptorclasses class github.ponyhuang.asssistantai.ui.navigation.** { *; }
-keepclassmembers class github.ponyhuang.asssistantai.ui.navigation.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
```

The kotlinx-serialization compiler plugin emits companion serializers at build
time, but Navigation 3 looks up `AppRoute` (and its 11 nested `data object` /
`data class` children) by class name from saved state. Keep the sealed interface
and its companions so the `KSerializer<T>` lookup succeeds.

### anthropic-java SDK

```proguard
-keep class com.anthropic.** { *; }
-keepclassmembers class com.anthropic.** { *; }
-dontwarn com.anthropic.**
```

The SDK's bundled `META-INF/proguard/anthropic-java-core.pro` only keeps members
annotated with `@com.fasterxml.jackson.annotation.*`. R8 still renames Kotlin
synthetic accessors (`access$getString$p`, etc.) and Companion methods that
Jackson reaches through reflection. Failure mode observed on Meizu:

> IllegalStateException: JsonMissing cannot be serialized (through reference chain:
> com.anthropic.models.messages.MessageCreateParams$a["messages"]
> → MessageParam["content"]
> → ContentBlockParam["cache_control"])

`JsonMissing$Serializer` always throws — it exists only as a tripwire. The real
bug is that the SDK's "skip missing JsonField" logic could no longer reach the
unwrapped value because its synthetic accessors were renamed. Force-keeping
`com.anthropic.**` adds ~7 MB back to the dex but restores correct serialization.
This is the highest single SDK we deliberately do not obfuscate.

### openai-java SDK

```proguard
-keep class com.openai.** { *; }
-keepclassmembers class com.openai.** { *; }
-dontwarn com.openai.**
```

Same root cause as anthropic-java, with the additional complication that
openai-java-core ships **no consumer rules at all** (the `META-INF/proguard/`
directory is absent from the jar). Failure mode observed on Meizu with the
default deepseek model:

> IllegalStateException: JsonMissing cannot be serialized (through reference chain:
> com.openai.models.chat.completions.ChatCompletionCreateParams$a["messages"]
> → ChatCompletionSystemMessageParam["name"])

Keeping the whole `com.openai.**` tree costs ~16 MB of dex (the SDK ships
~20 970 schema classes; obfuscation savings are negligible). Combined with the
anthropic keep, this is the largest single block in the release dex.

### Victools jsonschema-generator

```proguard
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType
```

`com.github.victools.jsonschema.generator` (transitive via google-genai /
anthropic-java) references `java.lang.reflect.AnnotatedParameterizedType`, which
exists on JDK 8+ but is not on Android's reflection surface. R8 reports it as a
missing class unless warned.

## Adding a new SDK reflection path

When a new feature hits an R8-driven runtime error (typically a `ClassNotFoundException`,
`NoSuchFieldException`, or `JsonMappingException` referencing a renamed class):

1. Build with `-info` and read the `missing_rules.txt` AGP writes to
   `app/build/outputs/mapping/release/missing_rules.txt`. Most "missing class"
   errors resolve with a `-dontwarn`.
2. For Jackson / Gson reflection, look at the field name in the stack trace. If
   it is a single-letter name like `f0["cache_control"]`, R8 renamed the class —
   the consumer rules did not match the access pattern, and you need a broader
   `-keep class com.<sdk>.** { *; }` covering that package.
3. Confirm with `assembleRelease` on a clean state (`adb shell pm clear
   <package>`) so cached error messages from earlier sessions do not mislead.
4. Update this file with the new rule + the failure mode that prompted it, so the
   next person hitting the same exception knows which line to grep for.