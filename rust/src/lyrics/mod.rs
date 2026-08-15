pub mod hybrid;
pub mod lrc;
pub mod yrc;

use crate::api::models::{CurrentLyric, Lyric, LyricLine, LyricWord};

/// 末句没有下一句时的回退时长：仅用于末句的进度填充，不参与换句判断。
const LAST_LINE_FALLBACK_MS: u64 = 4000;

/// 无逐字数据时，逐字高亮的单字时长估算（约 4 字/秒的常见演唱速度）。
/// 逐字高亮只在「演唱估算窗口」内推进，之后整句保持已唱状态——
/// 绝不在整段间奏/伴奏里继续爬行（避免看起来像歌词仍在向前滚动）。
const EVEN_CHAR_MS: u64 = 260;

/// 歌词时间轴：歌词同步的**唯一**时间基准。
///
/// 歌词加载完成后一次性构建：
/// 1. 按 `start_time_ms` 升序排序；
/// 2. 补齐 `end_time_ms`：无显式结束时间的行（`end_time_ms <= start_time_ms`）
///    取**下一句的真实 start_time_ms**；末句回退 `LAST_LINE_FALLBACK_MS`。
///
/// 换句判断完全由播放器真实 position 驱动：
/// `current = 最后一个 start_time_ms <= position_ms 的行`，用二分查找定位。
/// 纯音乐 / 间奏期间 position 停留在两句之间，current 保持不变，绝不会因
/// 「上一句时长到期」或任何定时器推进。
pub struct LyricTimeline {
    lines: Vec<LyricLine>,
}

impl LyricTimeline {
    /// 从解析结果构建时间轴（排序 + 内嵌换行拆分 + 双行合并 + 补齐 end_time_ms）。
    pub fn build(mut lines: Vec<LyricLine>) -> Self {
        // 去掉空文本行
        lines.retain(|l| !l.lines.is_empty());

        // 内嵌换行拆分：单行文本若含 '\n'/'\r'，拆成同一时间轴节点下的多行。
        // 归一化 words（去掉换行），保证「words 拼接文本 == 各子行拼接文本」，
        // 供 UI 按文本长度把逐字时间戳重新映射到各子行（第一行先高亮、第二行后高亮）。
        let mut expanded: Vec<LyricLine> = Vec::with_capacity(lines.len());
        for mut line in lines {
            for w in line.words.iter_mut() {
                w.text = w.text.replace('\n', "").replace('\r', "");
            }
            let mut sub: Vec<String> = Vec::new();
            for t in line.lines {
                for part in t.split(|c| c == '\n' || c == '\r') {
                    let p = part.trim().to_string();
                    if !p.is_empty() {
                        sub.push(p);
                    }
                }
            }
            if sub.is_empty() {
                continue;
            }
            line.lines = sub;
            expanded.push(line);
        }
        expanded.sort_by_key(|l| l.start_time_ms);

        // 双行合并：同一 start_time_ms 的连续行合并为一个 Group（同一时间轴节点）。
        // 合并后 lines/words 顺序与文档一致：Group 统一移动/高亮/反光，逐字时间戳跨行连续。
        let mut grouped: Vec<LyricLine> = Vec::with_capacity(expanded.len());
        for mut line in expanded {
            if let Some(last) = grouped.last_mut() {
                if last.start_time_ms == line.start_time_ms {
                    last.lines.append(&mut line.lines);
                    last.words.append(&mut line.words);
                    last.end_time_ms = last.end_time_ms.max(line.end_time_ms);
                    continue;
                }
            }
            grouped.push(line);
        }

        // 补齐 end_ms：无显式结束（end <= start）→ 下一句真实 start；末句 → 回退时长
        let n = grouped.len();
        for i in 0..n {
            let start = grouped[i].start_time_ms;
            if grouped[i].end_time_ms <= start {
                let end = grouped
                    .get(i + 1)
                    .map(|next| next.start_time_ms)
                    .unwrap_or(start + LAST_LINE_FALLBACK_MS);
                grouped[i].end_time_ms = end.max(start + 1);
            }
        }

        // 逐字高亮：无逐字时间戳（普通 LRC）的行，按组时长把文本均分为逐字时间戳。
        // 这样「歌词进行到哪个字就高亮哪个字」对所有歌词统一成立；
        // 已有真实逐字时间戳（YRC）的行保持不变。
        for line in grouped.iter_mut() {
            if line.words.is_empty() {
                let text: String = line.lines.concat();
                line.words = even_words(&text, line.start_time_ms, line.end_time_ms);
            }
        }
        Self { lines: grouped }
    }

    pub fn is_empty(&self) -> bool {
        self.lines.is_empty()
    }

    pub fn len(&self) -> usize {
        self.lines.len()
    }

    pub fn get(&self, index: usize) -> Option<&LyricLine> {
        self.lines.get(index)
    }

    /// 转回 [Lyric]（已排序、end_time_ms 已补齐）。
    pub fn to_lyric(&self) -> Lyric {
        Lyric {
            lines: self.lines.clone(),
        }
    }

    /// 二分查找当前句：最后一个 `start_time_ms <= position_ms` 的索引（O(log n)）。
    ///
    /// - position 恰好落在某句 start 上 → 该句；
    /// - position 落在两句之间（含纯音乐/间奏）→ 前一句保持不变；
    /// - position 早于第一句（前奏）→ 返回 0，保持首句待唱状态。
    pub fn find_current_lyric(&self, position_ms: u64) -> usize {
        if self.lines.is_empty() {
            return 0;
        }
        self.lines
            .partition_point(|l| l.start_time_ms <= position_ms)
            .saturating_sub(1)
    }
}

/// 把文本按「演唱估算窗口」均分为逐字时间戳（每字等长，末字收尾到窗口结束）。
/// 窗口 = min(行跨度, 单字时长 × 字数)：只在演唱窗口内推进逐字高亮，
/// 之后整句保持已唱状态，不在纯伴奏/间奏阶段继续向前滚动。
fn even_words(text: &str, start: u64, end: u64) -> Vec<LyricWord> {
    let chars: Vec<char> = text.chars().collect();
    if chars.is_empty() || end <= start {
        return Vec::new();
    }
    let vocal_end = start + EVEN_CHAR_MS * chars.len() as u64;
    let effective_end = end.min(vocal_end);
    let span = effective_end.saturating_sub(start);
    if span == 0 {
        return Vec::new();
    }
    let step = span / chars.len() as u64;
    chars
        .iter()
        .enumerate()
        .map(|(i, c)| {
            let s = start + step * i as u64;
            let e = if i + 1 == chars.len() {
                effective_end
            } else {
                start + step * (i as u64 + 1)
            };
            LyricWord {
                start_time_ms: s,
                end_time_ms: e,
                text: c.to_string(),
            }
        })
        .collect()
}

/// 根据播放位置（唯一时间源：播放器 position）返回当前歌词行 / 逐字索引 / 逐字进度。
///
/// 行与逐字均用二分查找；无逐字时间轴时 `word_progress` 恒为 0（由 UI 做整句高亮，
/// 不伪造逐字同步）。
pub fn current(timeline: &LyricTimeline, position_ms: u64) -> CurrentLyric {
    let line_idx = timeline.find_current_lyric(position_ms);

    let mut word_idx = 0usize;
    let mut word_progress = 0.0;
    if let Some(line) = timeline.get(line_idx) {
        if !line.words.is_empty() {
            word_idx = line
                .words
                .partition_point(|w| w.start_time_ms <= position_ms)
                .saturating_sub(1);
            if let Some(word) = line.words.get(word_idx) {
                let span = word.end_time_ms.saturating_sub(word.start_time_ms);
                let elapsed = position_ms.saturating_sub(word.start_time_ms);
                word_progress = if span > 0 {
                    (elapsed as f64 / span as f64).min(1.0)
                } else {
                    1.0
                };
            }
        }
    }

    CurrentLyric {
        current_line_index: line_idx as i64,
        current_word_index: word_idx as i64,
        word_progress,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn line(start: u64, text: &str) -> LyricLine {
        LyricLine {
            start_time_ms: start,
            end_time_ms: 0,
            lines: vec![text.to_string()],
            words: Vec::new(),
        }
    }

    /// 核心回归：纯音乐/间奏期间（两句之间）必须保持当前句，不因时长推进而跳转。
    #[test]
    fn instrumental_gap_keeps_current_line() {
        let timeline = LyricTimeline::build(vec![line(10000, "我爱你"), line(40000, "你爱我")]);

        // 00:10 → 00:39.999 之间（含 30 秒纯音乐）始终是第 0 句
        assert_eq!(timeline.find_current_lyric(10000), 0);
        assert_eq!(timeline.find_current_lyric(25000), 0);
        assert_eq!(timeline.find_current_lyric(39999), 0);
        // 只有 position >= 40000 才切换到第 1 句
        assert_eq!(timeline.find_current_lyric(40000), 1);
        assert_eq!(timeline.find_current_lyric(99999), 1);
    }

    /// 内嵌换行：一行文本含 '\n' 时拆成同一节点下的多行（避免两行被当成一个文本整体一起高亮）。
    #[test]
    fn embedded_newline_splits_into_sub_lines() {
        use crate::api::models::LyricWord;
        let mut l = line(10000, "我想和你一起去看海\n在黄昏的时候慢慢走");
        l.words = vec![
            LyricWord { start_time_ms: 10000, end_time_ms: 10400, text: "我".into() },
            LyricWord { start_time_ms: 10400, end_time_ms: 10800, text: "想".into() },
            LyricWord { start_time_ms: 10800, end_time_ms: 11200, text: "海\n".into() },
            LyricWord { start_time_ms: 11500, end_time_ms: 11900, text: "在".into() },
            LyricWord { start_time_ms: 11900, end_time_ms: 12300, text: "黄".into() },
            LyricWord { start_time_ms: 12300, end_time_ms: 12700, text: "昏".into() },
        ];
        let timeline = LyricTimeline::build(vec![l, line(18000, "下一句")]);
        assert_eq!(timeline.len(), 2);
        let g = timeline.get(0).unwrap();
        assert_eq!(g.lines.len(), 2);
        assert_eq!(g.lines[0], "我想和你一起去看海");
        assert_eq!(g.lines[1], "在黄昏的时候慢慢走");
        // words 里的换行被归一化，拼接后与各子行一致（供 UI 按文本长度重映射）
        assert_eq!(g.words.iter().map(|w| w.text.as_str()).collect::<String>(), "我想海在黄昏");
    }

    /// 逐字高亮只在演唱窗口内推进，间奏期间保持已唱状态（不持续爬行/不提前跳句）。
    #[test]
    fn even_words_hold_during_instrumental() {
        let timeline = LyricTimeline::build(vec![line(10000, "我爱你"), line(40000, "下一句")]);
        let g = timeline.get(0).unwrap();
        // 3 个字 → 演唱窗口 10000..(10000 + 3*EVEN_CHAR_MS)
        assert!(!g.words.is_empty());
        assert!(g.words.last().unwrap().end_time_ms <= 10000 + EVEN_CHAR_MS * 3);
        // 间奏中（远超演唱窗口）：仍在第 0 句，且逐字进度已到 1.0（整句已唱、保持不动）
        let at = current(&timeline, 25000);
        assert_eq!(at.current_line_index, 0);
        assert!((at.word_progress - 1.0).abs() < 1e-9);
    }

    /// 双行歌词：同一时间戳的两行合并为一个 Group（同一时间轴节点），
    /// 共享一次换句 / 移动 / 高亮，绝不拆成两条独立歌词。
    #[test]
    fn same_timestamp_lines_merge_into_one_group() {
        let timeline = LyricTimeline::build(vec![
            line(10000, "我想和你一起去看海"),
            line(10000, "在黄昏的时候慢慢走"),
            line(18000, "如果时间可以停留"),
            line(18000, "我希望就在这一刻"),
            line(26000, "最后一句"),
        ]);
        assert_eq!(timeline.len(), 3);
        let g0 = timeline.get(0).unwrap();
        assert_eq!(
            g0.lines,
            vec!["我想和你一起去看海".to_string(), "在黄昏的时候慢慢走".to_string()]
        );
        assert_eq!(g0.end_time_ms, 18000);
        let g1 = timeline.get(1).unwrap();
        assert_eq!(g1.lines.len(), 2);
        assert_eq!(g1.end_time_ms, 26000);
        // 双行 Group 是一个节点：期间 index 保持不变，到下一 Group start 才切换
        assert_eq!(timeline.find_current_lyric(10000), 0);
        assert_eq!(timeline.find_current_lyric(17999), 0);
        assert_eq!(timeline.find_current_lyric(18000), 1);
        assert_eq!(timeline.find_current_lyric(26000), 2);
    }

    /// 双行合并后逐字时间戳跨行连续（第一行先高亮，第二行到自己的时间戳后再开始）。
    #[test]
    fn merged_group_words_span_both_lines() {
        use crate::api::models::LyricWord;
        let mut l1 = line(10000, "我爱你");
        l1.words = vec![
            LyricWord { start_time_ms: 10000, end_time_ms: 10400, text: "我".into() },
            LyricWord { start_time_ms: 10400, end_time_ms: 10800, text: "爱".into() },
            LyricWord { start_time_ms: 10800, end_time_ms: 11200, text: "你".into() },
        ];
        let mut l2 = line(10000, "在黄昏");
        l2.words = vec![
            LyricWord { start_time_ms: 11500, end_time_ms: 11900, text: "在".into() },
            LyricWord { start_time_ms: 11900, end_time_ms: 12300, text: "黄".into() },
            LyricWord { start_time_ms: 12300, end_time_ms: 12700, text: "昏".into() },
        ];
        let timeline = LyricTimeline::build(vec![l1, l2]);
        assert_eq!(timeline.len(), 1);
        let g = timeline.get(0).unwrap();
        assert_eq!(g.lines.len(), 2);
        assert_eq!(g.words.len(), 6);
        // 10000ms 唱第一行；11500ms 起唱第二行（第一行保持，第二行开始高亮）
        let at = |ms: u64| current(&timeline, ms);
        assert_eq!(at(10000).current_line_index, 0);
        assert_eq!(at(10900).current_word_index, 2);
        assert_eq!(at(11499).current_word_index, 2);
        assert_eq!(at(11500).current_word_index, 3);
        assert_eq!(at(12700).current_word_index, 5);
    }

    /// 前奏早于第一句：保持首句（进度由调用方按 saturating_sub 归零）。
    #[test]
    fn before_first_line_returns_first() {
        let timeline = LyricTimeline::build(vec![line(5000, "第一句"), line(9000, "第二句")]);
        assert_eq!(timeline.find_current_lyric(0), 0);
        assert_eq!(timeline.find_current_lyric(4999), 0);
    }

    /// end_time_ms 补齐：无显式结束的行 → 下一句真实 start；末句 → start + 回退时长。
    #[test]
    fn end_time_filled_from_next_line_start() {
        let timeline = LyricTimeline::build(vec![
            line(10000, "A"),
            line(40000, "B"),
            line(55000, "C"),
        ]);
        assert_eq!(timeline.get(0).unwrap().end_time_ms, 40000);
        assert_eq!(timeline.get(1).unwrap().end_time_ms, 55000);
        assert_eq!(timeline.get(2).unwrap().end_time_ms, 55000 + LAST_LINE_FALLBACK_MS);
    }

    /// 显式结束时间（YRC 自带）不被覆盖：句内演唱结束后进度保持 1.0，直到下一句开始。
    #[test]
    fn explicit_end_time_preserved() {
        let mut lines = vec![line(10000, "A"), line(40000, "B")];
        lines[0].end_time_ms = 13000; // YRC：真实唱完时间
        let timeline = LyricTimeline::build(lines);
        assert_eq!(timeline.get(0).unwrap().end_time_ms, 13000);
        assert_eq!(timeline.find_current_lyric(25000), 0);
        assert_eq!(timeline.find_current_lyric(40000), 1);
    }

    /// 二分查找与线性扫描等价（乱序输入也会先排序；重复时间戳会先合并，故用不重复值）。
    #[test]
    fn binary_search_matches_linear_scan() {
        let starts = [0u64, 800, 3000, 9500, 12000, 30000, 88000, 120000];
        let timeline = LyricTimeline::build(
            starts.iter().enumerate().map(|(i, s)| line(*s, &format!("L{i}"))).collect(),
        );
        for pos in (0..130_000).step_by(137) {
            let expected = starts
                .iter()
                .rposition(|s| *s <= pos)
                .unwrap_or(0);
            assert_eq!(
                timeline.find_current_lyric(pos),
                expected,
                "pos={pos} 二分结果与线性扫描不一致"
            );
        }
    }

    /// 逐字（卡拉OK）进度：按字时间戳二分，不伪造。
    #[test]
    fn word_progress_from_real_word_timestamps() {
        use crate::api::models::LyricWord;
        let mut l = line(10000, "你好");
        l.end_time_ms = 11600;
        l.words = vec![
            LyricWord { start_time_ms: 10000, end_time_ms: 10800, text: "你".into() },
            LyricWord { start_time_ms: 10800, end_time_ms: 11600, text: "好".into() },
        ];
        let timeline = LyricTimeline::build(vec![l]);

        let at = |ms: u64| current(&timeline, ms);
        assert_eq!(at(10000).current_word_index, 0);
        assert_eq!(at(10799).current_word_index, 0);
        assert_eq!(at(10800).current_word_index, 1);
        // 间奏（唱完后）保持在最后一个字，进度 1.0
        let after = at(30000);
        assert_eq!(after.current_line_index, 0);
        assert_eq!(after.current_word_index, 1);
        assert!((after.word_progress - 1.0).abs() < 1e-9);
    }
}
