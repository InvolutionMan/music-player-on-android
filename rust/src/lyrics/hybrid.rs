//! /lyric/new 混合歌词解析。
//!
//! 网关返回的 yrc / lrc 字段可能混有三种行格式：
//! 1. JSON 行：`{"t":0,"c":[{"tx":"作词: "},{"tx":"唐恬"}]}`（无逐字时间）
//! 2. 经典 YRC 行：`[11140,3260](11140,270,0)录(11410,270,0)音...`（真实逐字时间）
//! 3. 经典 LRC 行：`[00:12.34]歌词文本`
//!
//! JSON / LRC 行无逐字时间：words 保持为空（UI 做整句高亮，**不伪造逐字同步**），
//! end_time_ms 置 0 由 [crate::lyrics::LyricTimeline] 按下一句真实 start 补齐；
//! YRC 行保留真实逐字时间与显式行结束时间。

use crate::api::models::{Lyric, LyricLine, LyricWord};
use serde::Deserialize;

#[derive(Debug, Deserialize)]
struct JsonLine {
    #[serde(default)]
    t: Option<u64>,
    #[serde(default)]
    c: Option<Vec<JsonItem>>,
}

#[derive(Debug, Deserialize)]
struct JsonItem {
    #[serde(default)]
    tx: Option<String>,
}

/// 一行解析结果：起始/结束时间、文本与逐字（空 = 无逐字时间 → 整句高亮）。
/// `end == 0` 表示无显式结束时间，由时间轴按下一句 start 补齐。
struct RawLine {
    start: u64,
    end: u64,
    text: String,
    words: Vec<LyricWord>,
}

pub fn parse_hybrid(text: &str) -> Lyric {
    let mut raw: Vec<RawLine> = Vec::new();
    for l in text.lines() {
        let line = l.trim();
        if line.is_empty() {
            continue;
        }
        // JSON 行（无逐字时间）
        if line.starts_with('{') {
            if let Ok(o) = serde_json::from_str::<JsonLine>(line) {
                let t = o.t.unwrap_or(0);
                let items: Vec<String> = o
                    .c
                    .unwrap_or_default()
                    .into_iter()
                    .filter_map(|i| i.tx)
                    .filter(|s| !s.trim().is_empty())
                    .collect();
                if items.is_empty() {
                    continue;
                }
                raw.push(RawLine {
                    start: t,
                    end: 0,
                    text: items.concat(),
                    words: Vec::new(),
                });
                continue;
            }
        }
        // 经典 YRC 行（带真实逐字括号）
        if line.starts_with('[') && line.contains('(') {
            if let Some((start, end, text, words)) = crate::lyrics::yrc::parse_yrc_line(line) {
                raw.push(RawLine {
                    start,
                    end,
                    text,
                    words,
                });
                continue;
            }
        }
        // 经典 LRC 行（[mm:ss.xx] 时间戳前缀，无逐字时间）
        if let Some((times, rest)) = crate::lyrics::lrc::extract_lrc_times(line) {
            if !rest.is_empty() {
                // 一行多时间戳只取第一个（避免重复歌词行）
                raw.push(RawLine {
                    start: times[0],
                    end: 0,
                    text: rest.to_string(),
                    words: Vec::new(),
                });
            }
        }
    }
    raw.sort_by_key(|r| r.start);

    // 直接映射（单行 Group）；end_time_ms 补齐与排序兜底统一交给 LyricTimeline::build
    let lines: Vec<LyricLine> = raw
        .into_iter()
        .map(|r| LyricLine {
            start_time_ms: r.start,
            end_time_ms: r.end,
            lines: vec![r.text],
            words: r.words,
        })
        .collect();
    Lyric { lines }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_json_lines() {
        let lyric = parse_hybrid(
            r#"{"t":0,"c":[{"tx":"作词: "},{"tx":"唐恬"}]}
{"t":10000,"c":[{"tx":"都 "},{"tx":"是勇敢的"}]}"#,
        );
        assert_eq!(lyric.lines.len(), 2);
        assert_eq!(lyric.lines[0].lines, vec!["作词: 唐恬".to_string()]);
        assert_eq!(lyric.lines[0].start_time_ms, 0);
        // JSON 行无逐字时间 → 不伪造逐字同步，words 为空（整句高亮）
        assert!(lyric.lines[0].words.is_empty());
        assert!(lyric.lines[1].words.is_empty());
        // 无显式结束时间 → end 占位 0，由时间轴补齐
        assert_eq!(lyric.lines[0].end_time_ms, 0);
    }

    #[test]
    fn parses_mixed_json_and_yrc() {
        let lyric = parse_hybrid(
            "{\"t\":0,\"c\":[{\"tx\":\"制作人: \"},{\"tx\":\"钱雷\"}]}\n\
             [11140,3260](11140,270,0)录(11410,270,0)音",
        );
        assert_eq!(lyric.lines.len(), 2);
        assert_eq!(lyric.lines[0].lines, vec!["制作人: 钱雷".to_string()]);
        let yrc = &lyric.lines[1];
        assert_eq!(yrc.lines, vec!["录音".to_string()]);
        // YRC 行保留真实逐字时间
        assert_eq!(yrc.words.len(), 2);
        assert_eq!(yrc.words[0].start_time_ms, 11140);
        assert_eq!(yrc.words[1].start_time_ms, 11410);
        // YRC 行头第二项是时长：end = start + duration
        assert_eq!(yrc.end_time_ms, 11140 + 3260);
    }

    #[test]
    fn parses_lrc_lines_in_hybrid() {
        let lyric = parse_hybrid("[00:05.50]故事的小黄花");
        assert_eq!(lyric.lines.len(), 1);
        assert_eq!(lyric.lines[0].start_time_ms, 5500);
        assert_eq!(lyric.lines[0].lines, vec!["故事的小黄花".to_string()]);
        assert!(lyric.lines[0].words.is_empty());
    }
}
