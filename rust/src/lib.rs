//! 余音 · 音乐核心（Rust Backend）
//!
//! 通过 UniFFI 暴露给 Kotlin，统一入口为 [MusicCore]。
//! 网易云数据全部经本地 NeteaseCloudMusicApi 网关获取（base_url 由 Kotlin 传入）。
//!
//! 架构约束：
//! - Rust 只负责网易云 API、歌曲数据、歌词解析、播放 URL 获取与**歌词同步**。
//! - 不负责 UI、不播放音频（音频由 Kotlin / Media3 ExoPlayer 负责）。
//! - 播放时间源唯一来自播放器 position，通过 [MusicCore::get_lyric_frame] 每帧传入。
//! - 歌词换句**完全由真实 position + 歌词时间戳二分查找决定**：
//!   无定时器、无「上一句时长到期」、无游标自增；纯音乐/间奏期间当前句保持不变。

pub mod api;
pub mod lyrics;

use api::models::{CurrentLyric, Lyric, LyricFrame, MatchedSong, MusicError, Song, SongList};
use api::netease::NeteaseClient;
use lyrics::LyricTimeline;
use std::collections::HashMap;
use std::sync::{Arc, Mutex};

/// 内部状态：客户端 + 缓存 + 当前歌词时间轴
struct MusicCoreInner {
    client: NeteaseClient,
    /// 歌词缓存（歌曲详情 / 歌词可缓存；播放 URL 不缓存，可能过期）
    lyric_cache: HashMap<u64, Arc<Lyric>>,
    /// 当前正在播放的歌曲歌词时间轴（已排序、end 已补齐，换句判断的唯一依据）
    current_timeline: Option<Arc<LyricTimeline>>,
    /// 最近一次 update_position 的位置快照（仅供 get_current_lyric 兼容接口使用）
    current_position_ms: u64,
}

impl MusicCoreInner {
    /// 构建当前歌词时间轴（排序 + 补齐 end_ms），并返回归一化后的歌词。
    fn load_timeline(&mut self, lyric: Lyric) -> Lyric {
        let timeline = LyricTimeline::build(lyric.lines);
        let normalized = timeline.to_lyric();
        self.current_timeline = Some(Arc::new(timeline));
        normalized
    }
}

/// 统一的 Kotlin ↔ Rust 入口（UniFFI 自动生成 Kotlin 绑定）
#[derive(uniffi::Object)]
pub struct MusicCore {
    inner: Mutex<MusicCoreInner>,
}

#[uniffi::export]
impl MusicCore {
    /// 构造：base_url 为 NeteaseCloudMusicApi 网关地址（如 http://10.0.2.2:3000）
    #[uniffi::constructor]
    pub fn new(base_url: String) -> Arc<Self> {
        Arc::new(Self {
            inner: Mutex::new(MusicCoreInner {
                client: NeteaseClient::new(base_url),
                lyric_cache: HashMap::new(),
                current_timeline: None,
                current_position_ms: 0,
            }),
        })
    }

    /// 搜索歌曲
    pub fn search(&self, keyword: String) -> Result<SongList, MusicError> {
        self.inner.lock().unwrap().client.search(&keyword)
    }

    /// 歌曲详情
    pub fn get_song_detail(&self, id: u64) -> Result<Song, MusicError> {
        self.inner.lock().unwrap().client.song_detail(id)
    }

    /// 获取播放地址（不缓存，URL 可能过期）
    pub fn get_play_url(&self, id: u64) -> Result<String, MusicError> {
        self.inner.lock().unwrap().client.song_url(id)
    }

    /// 获取并缓存歌词（详情 / 歌词可缓存）。
    /// 加载时即构建时间轴：换句依据只有「position + 真实时间戳」，与播放进度无关。
    pub fn get_lyric(&self, id: u64) -> Result<Lyric, MusicError> {
        let mut inner = self.inner.lock().unwrap();
        if let Some(cached) = inner.lyric_cache.get(&id) {
            let lyric = (**cached).clone();
            inner.current_timeline =
                Some(Arc::new(LyricTimeline::build(lyric.lines.clone())));
            return Ok(lyric);
        }
        let lyric = inner.client.lyric(id)?;
        let normalized = inner.load_timeline(lyric);
        inner.lyric_cache.insert(id, Arc::new(normalized.clone()));
        Ok(normalized)
    }

    /// 按「歌名 + 歌手」匹配网易云曲库并返回详情 + 歌词。
    /// 用于「网易云 App 正在播放」场景（媒体会话只有文本元数据，无歌曲 id）。
    pub fn match_song(&self, title: String, artist: String) -> Result<MatchedSong, MusicError> {
        let mut inner = self.inner.lock().unwrap();
        let mut matched = inner.client.match_song(&title, &artist)?;
        let normalized = inner.load_timeline(matched.lyric);
        matched.lyric = normalized.clone();
        inner.lyric_cache.insert(matched.song.id, Arc::new(normalized));
        Ok(matched)
    }

    /// 本地曲目歌词推入 Rust（歌词只有 timeMs，无逐字时间时 words 传空）。
    /// Rust 据此构建时间轴，成为本地曲目歌词同步的**唯一** index 来源。
    pub fn set_local_lyric(&self, lyric: Lyric) {
        let mut inner = self.inner.lock().unwrap();
        let timeline = LyricTimeline::build(lyric.lines);
        inner.current_timeline = Some(Arc::new(timeline));
    }

    /// 局域网内自动发现 NeteaseCloudMusicApi 网关：
    /// 扫描 local_ip 所在子网（prefix_len 前缀）内监听 port 的主机并验证。
    /// 返回首个验证通过的网关地址（http://ip:port）。
    pub fn discover_gateway(
        &self,
        local_ip: String,
        prefix_len: u8,
        port: u16,
        timeout_ms: u32,
    ) -> Result<String, MusicError> {
        let inner = self.inner.lock().unwrap();
        inner
            .client
            .discover_gateway(&local_ip, prefix_len, port, timeout_ms)
    }

    /// 帧级歌词同步：根据播放位置（毫秒）计算当前歌词帧（Kotlin 每帧 60/120Hz 调用）。
    ///
    /// 换句规则（无状态、无定时器）：
    /// `current = 最后一个 start_time_ms <= position_ms 的歌词行`（二分查找，O(log n)）。
    /// - 纯音乐 / 间奏（position 落在两句之间）→ 保持前一句，直到 position 到达下一句 start；
    /// - 暂停 → position 停止推进 → 歌词状态保持；
    /// - seek（前后跳）→ 每次调用都按 position 重新二分定位，不复用 seek 前的 index。
    pub fn get_lyric_frame(&self, position_ms: i64) -> Option<LyricFrame> {
        let inner = self.inner.lock().unwrap();
        let timeline = inner.current_timeline.as_ref()?;
        if timeline.is_empty() {
            return None;
        }
        let pos = position_ms.max(0) as u64;

        // 二分查找：最后一个 start_time_ms <= pos 的行
        let idx = timeline.find_current_lyric(pos);
        let line = timeline.get(idx)?;

        let span = line.end_time_ms.saturating_sub(line.start_time_ms);
        // 连续进度：毫秒级计算，clamp 到 0.0~1.0（时间戳重复/不连续/超界时安全）
        let progress = if span == 0 {
            1.0
        } else {
            ((pos.saturating_sub(line.start_time_ms)) as f32 / span as f32).clamp(0.0, 1.0)
        };
        Some(LyricFrame {
            current_index: idx as i32,
            start_time_ms: line.start_time_ms as i64,
            end_time_ms: line.end_time_ms as i64,
            progress,
            text: line.lines.concat(),
        })
    }

    /// 根据最近一次 [MusicCore::update_position] 的位置返回 歌词行 / 逐字索引 / 逐字进度。
    /// （兼容旧接口：同样走时间轴 + 二分查找，绝不按定时器/时长推进）
    pub fn get_current_lyric(&self) -> Option<CurrentLyric> {
        let inner = self.inner.lock().unwrap();
        let timeline = inner.current_timeline.as_ref()?;
        Some(lyrics::current(timeline, inner.current_position_ms))
    }

    /// 由播放器驱动的位置快照（兼容旧接口；帧级同步请直接调 [MusicCore::get_lyric_frame]）。
    /// 仅存储位置，不做任何歌词推进。
    pub fn update_position(&self, position_ms: u64) {
        let mut inner = self.inner.lock().unwrap();
        inner.current_position_ms = position_ms;
    }

    /// 清空缓存
    pub fn clear_cache(&self) {
        let mut inner = self.inner.lock().unwrap();
        inner.lyric_cache.clear();
        inner.current_timeline = None;
    }
}

uniffi::setup_scaffolding!();
