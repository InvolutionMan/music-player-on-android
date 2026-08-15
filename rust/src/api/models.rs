use serde::{Deserialize, Serialize};

/// 歌曲（UniFFI Record，Kotlin 类型安全）
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct Song {
    pub id: u64,
    pub name: String,
    pub artist: String,
    pub album: String,
    pub cover_url: String,
    pub duration_ms: u64,
}

/// 搜索结果列表
#[derive(Debug, Clone, Serialize, Deserialize, uniffi::Record)]
pub struct SongList {
    pub songs: Vec<Song>,
    pub total: u64,
}

/// 「网易云正在播放」匹配结果：歌曲详情 + 歌词
#[derive(Debug, Clone, uniffi::Record)]
pub struct MatchedSong {
    pub song: Song,
    pub lyric: Lyric,
}

/// 逐字歌词（LRC 无逐字时为空）
#[derive(Debug, Clone, Default, Serialize, Deserialize, uniffi::Record)]
pub struct LyricWord {
    pub start_time_ms: u64,
    pub end_time_ms: u64,
    pub text: String,
}

/// 一句歌词 = 时间轴上的一个 Group（同一时间戳节点的双行歌词合并为一个 Group）。
#[derive(Debug, Clone, Default, Serialize, Deserialize, uniffi::Record)]
pub struct LyricLine {
    pub start_time_ms: u64,
    pub end_time_ms: u64,
    /// 组内各行文本：双行歌词（同一时间戳）为 2 行；普通歌词为 1 行。
    /// 整个 Group 共享同一条时间轴：统一移动 / 高亮 / 反光，绝不拆成两条独立歌词。
    pub lines: Vec<String>,
    /// 逐字时间戳（覆盖整个 Group 的拼接文本；LRC 无逐字时为空）
    pub words: Vec<LyricWord>,
}

/// 整首歌词
#[derive(Debug, Clone, Default, Serialize, Deserialize, uniffi::Record)]
pub struct Lyric {
    pub lines: Vec<LyricLine>,
}

/// 当前歌词状态（由播放位置实时计算）
#[derive(Debug, Clone, Default, uniffi::Record)]
pub struct CurrentLyric {
    pub current_line_index: i64,
    pub current_word_index: i64,
    pub word_progress: f64,
}

/// 帧级歌词同步结果（Kotlin 每帧传入播放位置计算，驱动 UI 连续更新）
#[derive(Debug, Clone, Default, uniffi::Record)]
pub struct LyricFrame {
    /// 当前歌词行索引
    pub current_index: i32,
    /// 当前行开始时间（毫秒）
    pub start_time_ms: i64,
    /// 当前行结束时间（毫秒）
    pub end_time_ms: i64,
    /// 行内连续进度 0.0~1.0（非整数秒）
    pub progress: f32,
    /// 当前行歌词文本
    pub text: String,
}

/// 统一错误类型（Kotlin 可捕获并显示提示）
#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum MusicError {
    #[error("网络错误: {0}")]
    NetworkError(String),
    #[error("API 返回错误: {0}")]
    ApiError(String),
    #[error("未找到歌曲")]
    SongNotFound,
    #[error("播放地址不可用")]
    PlayUrlUnavailable,
    #[error("歌词不可用")]
    LyricUnavailable,
    #[error("解析错误: {0}")]
    ParseError(String),
}