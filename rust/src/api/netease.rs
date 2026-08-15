use std::time::Duration;

use reqwest::blocking::Client;
use serde::Deserialize;

use crate::api::models::{Lyric, MatchedSong, MusicError, Song, SongList};

/// 网易云音乐客户端：请求本地运行的 NeteaseCloudMusicApi 网关服务。
///
/// 网关格式（NeteaseCloudMusicApi）：GET {base_url}/search?keywords=... 等。
/// base_url 由 Kotlin 传入（模拟器 http://10.0.2.2:3000，真机为局域网地址）。
pub struct NeteaseClient {
    base_url: String,
    http: Client,
}

impl NeteaseClient {
    pub fn new(base_url: String) -> Self {
        let http = Client::builder()
            .timeout(Duration::from_secs(8))
            .user_agent("Mozilla/5.0 (Linux; Android 13) resound")
            .build()
            .expect("构建 HTTP 客户端失败");
        Self {
            base_url: base_url.trim_end_matches('/').to_string(),
            http,
        }
    }

    /// GET 请求 + JSON 解析的统一入口（网关错误统一映射为 MusicError）
    fn get_json<T: for<'de> Deserialize<'de>>(
        &self,
        path: &str,
        query: &[(&str, String)],
    ) -> Result<T, MusicError> {
        let url = format!("{}{}", self.base_url, path);
        let resp = self
            .http
            .get(&url)
            .query(query)
            .send()
            .map_err(|e| MusicError::NetworkError(format!("无法连接网易云网关 {url}: {e}")))?;
        if !resp.status().is_success() {
            return Err(MusicError::NetworkError(format!(
                "网关返回错误 {url}: HTTP {}",
                resp.status()
            )));
        }
        resp.json::<T>()
            .map_err(|e| MusicError::ParseError(format!("响应解析失败 {url}: {e}")))
    }

    /// 搜索歌曲
    pub fn search(&self, keyword: &str) -> Result<SongList, MusicError> {
        let resp: SearchResponse = self.get_json(
            "/search",
            &[("keywords", keyword.to_string()), ("limit", "30".into())],
        )?;
        let songs: Vec<Song> = resp
            .result
            .songs
            .into_iter()
            .filter_map(RawSong::into_song)
            .collect();
        if songs.is_empty() {
            return Err(MusicError::SongNotFound);
        }
        Ok(SongList {
            total: resp.result.song_count.unwrap_or(songs.len() as u64),
            songs,
        })
    }

    /// 歌曲详情
    pub fn song_detail(&self, id: u64) -> Result<Song, MusicError> {
        let resp: DetailResponse =
            self.get_json("/song/detail", &[("ids", id.to_string())])?;
        resp.songs
            .into_iter()
            .next()
            .and_then(RawSong::into_song)
            .ok_or(MusicError::SongNotFound)
    }

    /// 播放地址（不缓存，可能过期）
    pub fn song_url(&self, id: u64) -> Result<String, MusicError> {
        let resp: SongUrlResponse =
            self.get_json("/song/url", &[("id", id.to_string())])?;
        resp.data
            .into_iter()
            .find_map(|d| d.url.filter(|u| !u.is_empty()))
            .ok_or(MusicError::PlayUrlUnavailable)
    }

    /// 歌词：优先 /lyric/new（yrc 逐字），失败回退 /lyric。
    /// /lyric/new 的 yrc/lrc 字段可能是 JSON 行与经典 LRC/YRC 混合 → hybrid 解析。
    pub fn lyric(&self, id: u64) -> Result<Lyric, MusicError> {
        if let Ok(resp) = self.get_json::<LyricResponse>("/lyric/new", &[("id", id.to_string())]) {
            if let Some(text) = resp.yrc.as_ref().and_then(|l| l.lyric.as_deref()) {
                if !text.trim().is_empty() {
                    return Ok(crate::lyrics::hybrid::parse_hybrid(text));
                }
            }
            if let Some(text) = resp.lrc.as_ref().and_then(|l| l.lyric.as_deref()) {
                if !text.trim().is_empty() {
                    return Ok(crate::lyrics::hybrid::parse_hybrid(text));
                }
            }
            return Err(MusicError::LyricUnavailable);
        }

        // 旧接口回退：纯 LRC / YRC 文本
        let resp: LyricResponse =
            self.get_json("/lyric", &[("id", id.to_string())])?;
        if let Some(text) = resp.yrc.as_ref().and_then(|l| l.lyric.as_deref()) {
            if !text.trim().is_empty() {
                return Ok(crate::lyrics::yrc::parse_yrc(text));
            }
        }
        if let Some(text) = resp.lrc.as_ref().and_then(|l| l.lyric.as_deref()) {
            if !text.trim().is_empty() {
                return Ok(crate::lyrics::lrc::parse_lrc(text));
            }
        }
        Err(MusicError::LyricUnavailable)
    }

    /// 歌单详情
    pub fn playlist_detail(&self, id: u64) -> Result<SongList, MusicError> {
        let resp: PlaylistDetailResponse = self
            .get_json("/playlist/detail", &[("id", id.to_string())])?;
        let songs: Vec<Song> = resp
            .playlist
            .tracks
            .into_iter()
            .filter_map(RawSong::into_song)
            .collect();
        if songs.is_empty() {
            return Err(MusicError::SongNotFound);
        }
        Ok(SongList {
            total: songs.len() as u64,
            songs,
        })
    }

    /// 局域网内自动发现 NeteaseCloudMusicApi 网关（见 discovery 模块）。
    pub fn discover_gateway(
        &self,
        local_ip: &str,
        prefix_len: u8,
        port: u16,
        timeout_ms: u32,
    ) -> Result<String, MusicError> {
        crate::api::discovery::discover_gateway(&self.http, local_ip, prefix_len, port, timeout_ms)
    }

    /// 按「歌名 + 歌手」匹配网易云曲库：搜索 → 打分选最佳 → 详情 + 歌词。
    /// 用于「网易云 App 正在播放」场景（媒体会话只提供文本元数据，无歌曲 id）。
    pub fn match_song(&self, title: &str, artist: &str) -> Result<MatchedSong, MusicError> {
        let t = title.trim();
        if t.is_empty() {
            return Err(MusicError::SongNotFound);
        }
        let a = artist.trim();
        let keywords = if a.is_empty() { t.to_string() } else { format!("{t} {a}") };
        let songs = self.search(&keywords)?.songs;
        let best = best_match(t, a, &songs).ok_or(MusicError::SongNotFound)?;
        let song = self.song_detail(best.id)?;
        let lyric = self.lyric(song.id)?;
        Ok(MatchedSong { song, lyric })
    }
}

/// 匹配评分：歌名完全一致 > 互相包含；歌手一致 / 包含加分。中文与 ASCII 均大小写不敏感。
fn match_score(title: &str, artist: &str, song: &Song) -> i64 {
    let mut score = 0i64;
    let t = title.trim();
    let a = artist.trim();
    let sn = song.name.trim();
    if sn.eq_ignore_ascii_case(t) {
        score += 100;
    } else if sn.contains(t) || t.contains(sn) {
        score += 60;
    }
    if !a.is_empty() {
        let sa = song.artist.trim();
        if sa.eq_ignore_ascii_case(a) {
            score += 40;
        } else if sa.contains(a) || a.contains(sa) {
            score += 20;
        }
    }
    score
}

fn best_match(title: &str, artist: &str, songs: &[Song]) -> Option<Song> {
    songs
        .iter()
        .max_by_key(|s| match_score(title, artist, s))
        .cloned()
}

// ---------------------------------------------------------------------------
// 网关响应模型（serde）
// ---------------------------------------------------------------------------

#[derive(Debug, Deserialize)]
struct SearchResponse {
    result: SearchResult,
}

#[derive(Debug, Deserialize)]
struct SearchResult {
    #[serde(default)]
    songs: Vec<RawSong>,
    #[serde(default)]
    song_count: Option<u64>,
}

#[derive(Debug, Deserialize)]
struct DetailResponse {
    #[serde(default)]
    songs: Vec<RawSong>,
}

#[derive(Debug, Deserialize)]
struct SongUrlResponse {
    #[serde(default)]
    data: Vec<SongUrlData>,
}

#[derive(Debug, Deserialize)]
struct SongUrlData {
    #[serde(default)]
    url: Option<String>,
}

#[derive(Debug, Deserialize)]
struct LyricResponse {
    #[serde(default)]
    lrc: Option<RawLyric>,
    #[serde(default)]
    yrc: Option<RawLyric>,
}

#[derive(Debug, Deserialize)]
struct RawLyric {
    #[serde(default)]
    lyric: Option<String>,
}

#[derive(Debug, Deserialize)]
struct PlaylistDetailResponse {
    playlist: PlaylistData,
}

#[derive(Debug, Deserialize)]
struct PlaylistData {
    #[serde(default)]
    tracks: Vec<RawSong>,
}

/// 歌曲原始结构：兼容 /search（ar/al 简写）与 /song/detail（artists/album）两种字段。
#[derive(Debug, Deserialize)]
struct RawSong {
    #[serde(default)]
    id: Option<u64>,
    #[serde(default)]
    name: Option<String>,
    #[serde(default)]
    ar: Option<Vec<RawArtist>>,
    #[serde(default)]
    artists: Option<Vec<RawArtist>>,
    #[serde(default)]
    al: Option<RawAlbum>,
    #[serde(default)]
    album: Option<RawAlbum>,
    #[serde(default)]
    duration: Option<u64>,
    #[serde(default)]
    dt: Option<u64>,
}

impl RawSong {
    fn into_song(self) -> Option<Song> {
        let id = self.id?;
        let name = self.name?;
        let artist = self
            .ar
            .or(self.artists)
            .unwrap_or_default()
            .into_iter()
            .filter_map(|a| a.name)
            .collect::<Vec<_>>()
            .join(" / ");
        let album = self.al.or(self.album);
        let cover_url = album.as_ref().and_then(|a| a.pic_url.clone()).unwrap_or_default();
        let duration_ms = self.duration.or(self.dt).unwrap_or(0);
        Some(Song {
            id,
            name,
            artist,
            album: album.and_then(|a| a.name).unwrap_or_default(),
            cover_url,
            duration_ms,
        })
    }
}

#[derive(Debug, Deserialize)]
struct RawArtist {
    #[serde(default)]
    name: Option<String>,
}

#[derive(Debug, Deserialize)]
struct RawAlbum {
    #[serde(default)]
    name: Option<String>,
    #[serde(default)]
    pic_url: Option<String>,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn song(id: u64, name: &str, artist: &str) -> Song {
        Song {
            id,
            name: name.into(),
            artist: artist.into(),
            album: String::new(),
            cover_url: String::new(),
            duration_ms: 0,
        }
    }

    #[test]
    fn exact_title_and_artist_scores_highest() {
        let songs = vec![
            song(1, "晴天", "刘瑞琦"),
            song(2, "晴天", "周杰伦"),
            song(3, "晴天雨天", "周杰伦"),
        ];
        let best = best_match("晴天", "周杰伦", &songs).unwrap();
        assert_eq!(best.id, 2);
    }

    #[test]
    fn title_contains_fallback() {
        let songs = vec![
            song(1, "晴天 (Live)", "周杰伦"),
            song(2, "告白气球", "周杰伦"),
        ];
        let best = best_match("晴天", "周杰伦", &songs).unwrap();
        assert_eq!(best.id, 1);
    }
}
