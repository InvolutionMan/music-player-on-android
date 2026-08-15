# 余音 · Rust 音乐核心（resound_music）

Rust 作为 Backend/Core，负责网易云 API、歌曲数据、歌词解析、播放 URL 获取。
Kotlin 负责 UI / 交互 / Media3 ExoPlayer 播放 / 网易云媒体会话监听。两者通过 **UniFFI** 桥接。

网络数据统一来自本地运行的 **NeteaseCloudMusicApi** 网关（base_url 由 Kotlin 传入，
默认 `http://10.0.2.2:3000`，可在 App 设置页修改）。

## 目录结构

```
rust/
├── Cargo.toml
└── src/
    ├── lib.rs              # MusicCore（UniFFI 导出，统一入口）
    ├── api/
    │   ├── mod.rs
    │   ├── models.rs       # Song / SongList / MatchedSong / Lyric / LyricLine / LyricWord / CurrentLyric / MusicError
    │   └── netease.rs      # NeteaseClient（search / song_detail / song_url / lyric / playlist_detail / match_song）
    └── lyrics/
        ├── mod.rs          # current()：按播放位置返回 行/字/进度
        ├── lrc.rs          # LRC 解析（已实现 + 单测）
        └── yrc.rs          # YRC 逐字歌词解析（已实现 + 单测）
```

## 基础检查

```sh
cargo check      # 编译验证
cargo test       # 运行单测（LRC / YRC 解析、匹配评分等）
```

## Android（UniFFI）接入

- `cargo ndk` 为 arm64-v8a / armeabi-v7a / x86_64 构建 `libresound_music.so` → `app/src/main/jniLibs/`。
- `uniffi-bindgen generate --library … --language kotlin` 生成 bindings → `app/src/main/java/uniffi/resound_music/`。
- app 依赖 `net.java.dev.jna:jna:5.15.0@aar`（bindings 用 JNA）。
- Kotlin 适配层：`music/NativeMusicCore.kt`（实现 `MusicCoreApi`）。

改动 Rust 接口后重新生成：

```sh
# 1) 构建 .so（在 rust/ 目录）
ANDROID_NDK_HOME=/opt/homebrew/share/android-ndk \
  cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../app/src/main/jniLibs build --release
# 2) 重新生成 bindings（在 rust/ 目录，uniffi-bindgen 需要 cargo metadata）
uniffi-bindgen generate --library ../app/src/main/jniLibs/arm64-v8a/libresound_music.so \
  --language kotlin --out-dir ../app/src/main/java
# 3) 重新编译
cd .. && ./gradlew :app:assembleDebug
```

## 网易云「正在播放」链路

```
网易云 App（媒体会话）
  → Kotlin NeteaseObserver（通知使用权 + MediaSessionManager 轮询：歌名/歌手/专辑/封面/进度）
  → Rust MusicCore.matchSong(title, artist)：网关 /search → 评分选最佳 → /song/detail + /lyric
  → Kotlin NeteaseLyricsScreen：歌词按网易云进度高亮（YRC 逐字 / LRC 行级）
```

## 时间轴一致性

播放时间源**唯一**来自 ExoPlayer：
`Kotlin: exoController.positionMs → MusicCore.updatePosition(ms) → Rust lyrics::current(...) → CurrentLyric`
`→ Kotlin Compose 更新歌词 UI / 进度条`。

网易云歌词页的时间源独立：来自媒体会话 PlaybackState（轮询 + 外推），不写入 `update_position`。
