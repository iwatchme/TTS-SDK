# TTS SDK KMP + iOS Combine 独立模块完整方案

## 本地沙盒路径

- 当前项目路径：`/Users/iwatchme/Desktop/TTS`
- 建议方案文档路径：`/Users/iwatchme/Desktop/TTS/docs/kmp-ios-combine-migration-plan.md`
- 建议新增 iOS Swift Package 路径：`/Users/iwatchme/Desktop/TTS/ios/TtsCombine`
- 建议 XCFramework 输出路径：`/Users/iwatchme/Desktop/TTS/build/ios-framework/TtsSdk.xcframework`

以上是本地沙盒内的建议落地路径；当前只给方案，不写入文件。

## 总体结论

该项目迁移到 KMP 可行性高。现有核心逻辑基本是 Kotlin + coroutines，真实 Android API 依赖很少；主要改造点是 `java.io.File`、`MessageDigest`、JVM 并发类型和 Swift 侧 API 体验。

最终架构建议：

- Android 继续直接使用 KMP core。
- iOS 不直接使用原始 KMP API。
- iOS 单独提供一个 Swift Combine 模块 `TtsCombine`。
- KMP 提供核心能力和 iOS bridge。
- Swift Combine 层把 KMP 的 callback/cancellable 包装成 `AnyPublisher`。

核心原则：**共享业务能力，不强迫 iOS 使用 Kotlin 风格 API。**

## 目标模块结构

- `tts-core`
  - 改为 Kotlin Multiplatform module。
  - 包含 TTS 引擎、缓存、SDK pool、参数模型、错误模型、批量生成、流式预览核心逻辑。
  - targets：`androidTarget()`、`iosArm64()`、`iosSimulatorArm64()`，可选 `iosX64()`。

- `tts-player`
  - 改为 Kotlin Multiplatform module。
  - common 层保留播放器状态机、队列、`AudioSink` 抽象。
  - Android/iOS 各自提供真实音频 sink。
  - iOS sink 使用 AVFoundation。

- `tts-ios-bridge`
  - 新增 KMP module。
  - 专门给 Swift 层用。
  - 不暴露 `Flow`、`CoroutineScope`、Kotlin `Result`。
  - 暴露 callback + cancellable task 风格接口。

- `ios/TtsCombine`
  - Swift-only module。
  - 作为 Swift Package target。
  - 依赖 `TtsSdk.xcframework`。
  - 对外提供 Combine API：`AnyPublisher`、Swift `Error`、Swift event enum。

- `tts-testing`
  - 可迁移为 KMP testing module。
  - 主要服务 common tests，不进入 iOS 生产产物。

## 公共 API 调整

- 缓存目录：
  - 现有 `java.io.File` 不适合 KMP 公共 API。
  - 对外改为 `cacheDirPath: String`。
  - 内部建议使用 Okio `FileSystem` / `Path`。

- 缓存策略：
  - 当前 `TtsCacheStrategy` 接收 `List<File>`。
  - 改为接收跨平台 metadata：
    - `path: String`
    - `sizeBytes: Long`
    - `lastModifiedMillis: Long?`
    - `isTemporary: Boolean`

- 音频事件：
  - common 层继续保留 `TtsEvent.AudioChunk(ByteArray)`。
  - iOS Combine 层映射成 Swift `Data`。
  - 建议增加明确音频格式模型：
    - `sampleRate`
    - `channelCount`
    - `pcmEncoding`
    - `encodeType`

- iOS bridge：
  - `generate`：callback 返回生成结果或错误。
  - `preview`：callback 返回事件流，并返回 cancellable handle。
  - `close`：释放 coroutine、SDK pool、音频资源。

- Swift Combine：
  - `generate(...) -> AnyPublisher<[TtsGenerateResult], TtsError>`
  - `preview(...) -> AnyPublisher<TtsPreviewEvent, TtsError>`
  - subscription cancel 时必须反向调用 KMP cancel。

## iOS 使用方式

Swift 侧推荐只使用 `TtsCombine`：

```swift
import TtsCombine

let client = TtsCombineClient(cacheDirPath: cachePath)

client.generate(
    texts: ["你好", "欢迎使用"],
    source: 1,
    voice: voice
)
.sink(
    receiveCompletion: { completion in
        // success / failure
    },
    receiveValue: { results in
        // results[i].filePath
    }
)
.store(in: &cancellables)
```

流式预览：

```swift
client.preview(
    text: "你好",
    source: 1,
    voice: voice
)
.sink(
    receiveCompletion: { completion in
        // done / error / cancelled
    },
    receiveValue: { event in
        switch event {
        case .audioChunk(let data):
            player.enqueue(data)
        case .done(let filePath):
            print(filePath)
        case .progress(let completed, let total):
            print(completed, total)
        }
    }
)
.store(in: &cancellables)
```

取消方式：

```swift
cancellables.removeAll()
```

或显式持有 subscription 并 cancel。取消时 `TtsCombine` 必须调用 KMP bridge 的 cancel handle。

## 实施步骤

1. 建立迁移前基线
   - 在 `/Users/iwatchme/Desktop/TTS` 跑现有测试。
   - 当前我尝试 `./gradlew test` 时被沙箱拦截在 `~/.gradle`，所以正式实施前需要先得到一次干净测试结果。

2. 改造 Gradle
   - 根工程加入 `kotlin("multiplatform")`。
   - `tts-core` 从 Android library 改为 KMP library。
   - 保留 Android AAR 产物，同时增加 iOS framework 产物。
   - 配置统一 framework baseName，例如 `TtsSdk`。

3. 迁移 `tts-core`
   - 把纯 Kotlin 模型和核心逻辑移动到 `commonMain`。
   - 替换 `java.io.File` 为跨平台路径/Okio。
   - 替换 `MessageDigest` 为跨平台 hash。
   - 替换 `AtomicInteger`、`CopyOnWriteArrayList`、`@Volatile` 为协程安全实现。
   - 避免 common 层硬编码 `Dispatchers.IO`。

4. 增加 `tts-ios-bridge`
   - 封装 `TtsEngine`。
   - 暴露 Swift 易包装的 callback API。
   - 每个异步任务返回 `TtsCancellable`。
   - 统一错误映射为 code/message/domain。

5. 迁移 `tts-player`
   - common 层保留状态机和队列。
   - iOS actual 或 Swift 层提供 AVFoundation sink。
   - 首版只承诺 PCM 播放，明确采样率和编码格式。

6. 新建 Swift Package `ios/TtsCombine`
   - binary target：`TtsSdk.xcframework`。
   - Swift target：`TtsCombine`。
   - 包装 generate、preview、cancel、player。
   - 对 iOS 用户隐藏 Kotlin 类型。

7. 构建发布流程
   - 本地开发：Gradle 生成 framework，Xcode/SwiftPM 引用本地 xcframework。
   - CI 发布：macOS runner 构建 `TtsSdk.xcframework`。
   - 远程消费：Swift Package binaryTarget 或 CocoaPods 二进制 pod。

8. 文档和 Demo
   - README 增加 Android KMP 用法。
   - 新增 iOS Combine quick start。
   - 新增 iOS demo：批量生成、预览、播放、取消、释放。

## 测试计划

- common tests：
  - cache key 一致性。
  - LRU / LargestFirst 策略。
  - pool 并发上限。
  - generate 去重。
  - 单条失败不影响其他条目。
  - cancel 后 scope 可复用。

- Android tests：
  - 原有 quick start 兼容。
  - AAR 能正常被 Android app 引用。
  - 文件缓存路径正常。

- iOS tests：
  - 真机和模拟器都能 import `TtsSdk`。
  - Swift 能 import `TtsCombine`。
  - `generate` publisher 成功返回文件路径。
  - `preview` publisher 按顺序返回 audio chunk。
  - Combine cancel 能取消 KMP 任务。
  - `close` 后不会再回调。
  - 播放器 play/pause/resume/stop/release 状态正确。

## 风险与默认决策

- `File` API 会产生破坏性变更；默认接受一次 API 调整，并给 Android 提供辅助适配入口。
- Swift 直接使用 KMP API 体验较差；默认必须提供 `TtsCombine`。
- Combine 模块不写在 Kotlin 里；默认使用 Swift-only module。
- iOS framework 构建需要 macOS；默认 CI 增加 macOS job。
- 音频格式必须文档化；默认首版支持 PCM streaming。
- iOS 生产分发默认使用 `XCFramework + Swift Package`。
