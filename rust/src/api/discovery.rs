//! 局域网网关自动发现：扫描指定子网内监听指定端口的 HTTP 服务，
//! 并用 /search 接口验证是否为 NeteaseCloudMusicApi 网关。

use std::net::{Ipv4Addr, SocketAddr, TcpStream};
use std::sync::mpsc;
use std::time::{Duration, Instant};

use reqwest::blocking::Client;
use serde::Deserialize;

use crate::api::models::MusicError;

/// 扫描 local_ip 所在子网（prefix_len 位前缀）内监听 port 的主机，
/// 返回第一个通过 HTTP 验证的网关地址（http://ip:port）。
///
/// 本机 IP 与子网前缀由 Kotlin 枚举网络接口后传入（避免 Rust 侧处理移动网络/双 Wi-Fi）。
pub fn discover_gateway(
    http: &Client,
    local_ip: &str,
    prefix_len: u8,
    port: u16,
    timeout_ms: u32,
) -> Result<String, MusicError> {
    let local: Ipv4Addr = local_ip
        .parse()
        .map_err(|_| MusicError::ParseError(format!("无效的本机地址: {local_ip}")))?;
    if prefix_len > 30 {
        return Err(MusicError::ParseError("子网前缀过长".into()));
    }
    let host_count = 1u64 << (32 - prefix_len as u64);
    if host_count > 1024 {
        return Err(MusicError::ParseError("子网过大，不支持扫描".into()));
    }
    let mask = u32::MAX << (32 - prefix_len as u32);
    let base = u32::from(local) & mask;
    let local_host = u32::from(local) & !mask;

    // 候选主机：网关地址（.1）优先，其余顺序扫描
    let mut hosts: Vec<u32> = (0..host_count as u32)
        .filter(|&h| h != local_host)
        .collect();
    hosts.sort_by_key(|&h| if h == 1 { 0 } else { 1 });

    let (tx, rx) = mpsc::channel::<Ipv4Addr>();
    let mut handles = Vec::new();
    for chunk in hosts.chunks(16) {
        let tx = tx.clone();
        let chunk = chunk.to_vec();
        let base = base;
        handles.push(std::thread::spawn(move || {
            for h in chunk {
                let ip = Ipv4Addr::from(base | h);
                let addr = SocketAddr::new(ip.into(), port);
                if TcpStream::connect_timeout(&addr, Duration::from_millis(150)).is_ok() {
                    let _ = tx.send(ip);
                }
            }
        }));
    }
    drop(tx);

    // 逐个验证候选（/search 返回 result.songs 即为网易云网关）
    let deadline = Instant::now() + Duration::from_secs(6);
    let mut verified = None;
    while verified.is_none() && Instant::now() < deadline {
        match rx.recv_timeout(Duration::from_millis(300)) {
            Ok(ip) => {
                let url = format!("http://{ip}:{port}");
                if is_netease_gateway(http, &url, Duration::from_millis(timeout_ms as u64)) {
                    verified = Some(url);
                }
            }
            Err(_) => {
                // 通道超时或扫描线程全部结束；继续等至 deadline（可能有慢连接）
            }
        }
    }
    for h in handles {
        let _ = h.join();
    }

    verified.ok_or_else(|| {
        MusicError::NetworkError(
            "局域网内未找到 NeteaseCloudMusicApi 网关，请确认电脑已启动服务且与手机同一 Wi-Fi".into(),
        )
    })
}

/// 通过 /search 接口验证候选地址是否为 NeteaseCloudMusicApi 网关。
fn is_netease_gateway(http: &Client, base_url: &str, timeout: Duration) -> bool {
    #[derive(Deserialize)]
    struct Probe {
        result: Option<ProbeResult>,
    }
    #[derive(Deserialize)]
    struct ProbeResult {
        songs: Option<Vec<serde_json::Value>>,
    }

    let Ok(resp) = http
        .get(format!("{base_url}/search?keywords=resound&limit=1"))
        .timeout(timeout)
        .send()
    else {
        return false;
    };
    match resp.json::<Probe>() {
        Ok(p) => p.result.and_then(|r| r.songs).is_some(),
        Err(_) => false,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_invalid_ip() {
        let http = Client::new();
        assert!(discover_gateway(&http, "not-an-ip", 24, 3000, 100).is_err());
    }

    #[test]
    fn rejects_huge_subnet() {
        let http = Client::new();
        assert!(discover_gateway(&http, "192.168.10.49", 8, 3000, 100).is_err());
    }
}
