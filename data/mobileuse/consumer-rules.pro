# ML Kit 从 Manifest 的组件注册名反射调用无参构造函数；R8 必须保留这些入口。
-keep class com.google.mlkit.common.internal.CommonComponentRegistrar { public <init>(); }
-keep class com.google.mlkit.vision.common.internal.VisionCommonRegistrar { public <init>(); }
-keep class com.google.mlkit.vision.text.internal.TextRegistrar { public <init>(); }
