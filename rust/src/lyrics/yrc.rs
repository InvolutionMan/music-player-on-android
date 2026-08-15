use crate::api::models::{Lyric, LyricLine, LyricWord};

/// 解析 YRC（逐字）歌词。
///
/// 格式：`[startMs,durationMs](wordStart,wordDuration,0)字1(wordStart,wordDuration,0)字2...`
/// 例：`[420,4440](420,1320,0)Lately(1740,0,0), ...`
///
/// 行头第二项是**时长**（非绝对结束时间）：行结束 = start + duration；
/// 逐字 `(start, duration, 0)`：字结束 = start + duration。均为真实时间戳，不推测。
/// 行文本为所有字拼接，words 提供逐字时间轴。
pub fn parse_yrc(text: &str) -> Lyric {
    let mut lines = Vec::new();
    for raw in text.lines() {
        if let Some((start, end, text, words)) = parse_yrc_line(raw.trim()) {
            lines.push(LyricLine {
                start_time_ms: start,
                end_time_ms: end,
                lines: vec![text],
                words,
            });
        }
    }
    lines.sort_by_key(|l| l.start_time_ms);
    Lyric { lines }
}

/// 解析单行 YRC：返回 (行开始, 行结束, 行文本, 逐字列表)。
/// 行头 `[start,duration]` → 行结束 = start + duration；duration 非法时置 0（由时间轴按下一句补齐）。
pub fn parse_yrc_line(line: &str) -> Option<(u64, u64, String, Vec<LyricWord>)> {
    let line = line.trim();
    if line.is_empty() {
        return None;
    }
    // 行首 [startMs,durationMs]
    let close = line.find(']')?;
    let header = &line[1..close];
    let (start, dur) = header.split_once(',')?;
    let (start, dur) = (
        start.trim().parse::<u64>().unwrap_or(0),
        dur.trim().parse::<u64>().unwrap_or(0),
    );

    // 逐字 (start,duration,0)word
    let rest = &line[close + 1..];
    let bytes = rest.as_bytes();
    let mut words = Vec::new();
    let mut text = String::new();
    let mut i = 0usize;
    while i < bytes.len() {
        if bytes[i] == b'(' {
            let rel = rest[i + 1..].find(')')?;
            let mut parts = rest[i + 1..i + 1 + rel].split(',');
            let word_start = parts.next().and_then(|s| s.trim().parse::<u64>().ok()).unwrap_or(0);
            let word_dur = parts.next().and_then(|s| s.trim().parse::<u64>().ok()).unwrap_or(0);
            i += rel + 2;
            let word_text_start = i;
            while i < bytes.len() && bytes[i] != b'(' {
                i += 1;
            }
            let word_text = rest[word_text_start..i].to_string();
            if !word_text.is_empty() {
                text.push_str(&word_text);
                words.push(LyricWord {
                    start_time_ms: word_start,
                    end_time_ms: word_start + word_dur,
                    text: word_text,
                });
            }
        } else {
            i += 1;
        }
    }
    if text.is_empty() {
        return None;
    }
    Some((
        start,
        // 行头第二项是时长：行结束 = start + duration（真实时间戳）
        if dur > 0 { start + dur } else { 0 },
        text,
        words,
    ))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_single_line_with_words() {
        let lyric = parse_yrc("[9351,13175](9351,450,0)对(9801,450,0)不(10251,450,0)起");
        assert_eq!(lyric.lines.len(), 1);
        let line = &lyric.lines[0];
        assert_eq!(line.start_time_ms, 9351);
        // 行头第二项是时长：end = start + duration
        assert_eq!(line.end_time_ms, 9351 + 13175);
        assert_eq!(line.lines, vec!["对不起".to_string()]);
        assert_eq!(line.words.len(), 3);
        assert_eq!(line.words[0].text, "对");
        assert_eq!(line.words[0].start_time_ms, 9351);
        assert_eq!(line.words[0].end_time_ms, 9801);
        assert_eq!(line.words[2].text, "起");
        assert_eq!(line.words[2].start_time_ms, 10251);
    }

    #[test]
    fn parses_multiple_lines() {
        let lyric = parse_yrc(
            "[1000,2000](1000,500,0)你(1500,500,0)好\n[3000,4000](3000,500,0)世(3500,500,0)界\n",
        );
        assert_eq!(lyric.lines.len(), 2);
        assert_eq!(lyric.lines[0].lines, vec!["你好".to_string()]);
        assert_eq!(lyric.lines[1].lines, vec!["世界".to_string()]);
        assert_eq!(lyric.lines[1].start_time_ms, 3000);
    }

    #[test]
    fn skips_empty_and_malformed_lines() {
        let lyric = parse_yrc("\n[abc,def]无有效字\n[1000,2000](1000,500,0)好");
        assert_eq!(lyric.lines.len(), 1);
        assert_eq!(lyric.lines[0].lines, vec!["好".to_string()]);
    }
}
