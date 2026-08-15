use crate::api::models::{Lyric, LyricLine};

/// 解析 LRC 歌词文本（形如 `[mm:ss.xx]歌词`，一行可含多个时间戳）。
/// 无逐字信息，words 为空；end_time_ms 置 0（占位），由 [crate::lyrics::LyricTimeline]
/// 在加载完成后按「下一句真实 start_time_ms」补齐——纯音乐/间奏期间不会误推进。
pub fn parse_lrc(text: &str) -> Lyric {
    let mut lines = Vec::new();
    for raw in text.lines() {
        let line = raw.trim();
        if line.is_empty() {
            continue;
        }
        let Some((times, rest)) = extract_lrc_times(line) else { continue };
        if rest.is_empty() {
            continue;
        }
        // 一行可能带多个时间戳（重复段落）：每个时间戳对应一个 Group（单行文本）
        for ms in times {
            lines.push(LyricLine {
                start_time_ms: ms,
                end_time_ms: 0, // 由 LyricTimeline::build 补齐为下一句 start
                lines: vec![rest.to_string()],
                words: Vec::new(),
            });
        }
    }
    Lyric { lines }
}

/// 提取一行 LRC 的 `[mm:ss.xx]` 时间戳（毫秒）与剩余歌词文本。
/// 返回 None 表示该行不含有效时间戳（元数据行 / 空行）。
pub fn extract_lrc_times(line: &str) -> Option<(Vec<u64>, &str)> {
    let line = line.trim();
    let mut rest = line;
    let mut times = Vec::new();
    // 逐个提取 `[...]` 时间戳，剩余部分为歌词文本
    while let Some(start) = rest.find('[') {
        if let Some(end) = rest[start..].find(']') {
            let ts = &rest[start + 1..start + end];
            if let Some(ms) = parse_timestamp(ts) {
                times.push(ms);
            }
            rest = &rest[start + end + 1..];
        } else {
            break;
        }
    }
    if times.is_empty() {
        return None;
    }
    Some((times, rest.trim()))
}

/// 解析 `mm:ss.xx` / `mm:ss.xxx` 为毫秒
fn parse_timestamp(ts: &str) -> Option<u64> {
    let (min, sec) = ts.split_once(':')?;
    let minutes: u64 = min.trim().parse().ok()?;
    let seconds: f64 = sec.trim().parse().ok()?;
    Some(minutes.checked_mul(60_000)? + (seconds * 1000.0) as u64)
}

/// 供测试用的示例解析（可在单测中验证）
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_single_line() {
        let lyric = parse_lrc("[00:05.50]故事的小黄花");
        assert_eq!(lyric.lines.len(), 1);
        assert_eq!(lyric.lines[0].start_time_ms, 5500);
        assert_eq!(lyric.lines[0].lines, vec!["故事的小黄花".to_string()]);
    }
}