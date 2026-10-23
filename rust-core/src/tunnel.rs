//! 隧道生命周期管理：启动 / 停止 tun2proxy，并维护本次会话的流量统计。

use std::net::IpAddr;
use std::sync::{Arc, Mutex};
use std::thread::JoinHandle;

use tun2proxy::{ArgDns, Args, CancellationToken, ProxyParameters};
use crate::traffic::TrafficCounters;
#[cfg(unix)]
use crate::traffic::CountedIo;

/// DNS 处理方式，数值与 Kotlin 侧 `RustBridge.DNS_*` 一致。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DnsMode {
    /// Fake-IP：域名随连接交给 SOCKS5，由 xray 远端解析，无 DNS 泄漏
    Virtual = 0,
    /// UDP DNS 转成 TCP 经代理发往 `dns_addr`
    OverTcp = 1,
    /// 不处理 DNS，按普通 UDP 转发
    Direct = 2,
}

impl DnsMode {
    pub fn from_i32(v: i32) -> Option<Self> {
        match v {
            0 => Some(Self::Virtual),
            1 => Some(Self::OverTcp),
            2 => Some(Self::Direct),
            _ => None,
        }
    }

    fn to_arg(self) -> ArgDns {
        match self {
            Self::Virtual => ArgDns::Virtual,
            Self::OverTcp => ArgDns::OverTcp,
            Self::Direct => ArgDns::Direct,
        }
    }
}

/// 错误码与 Kotlin 侧 `RustBridge.describe()` 一致。
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum TunnelError {
    AlreadyRunning,
    InvalidFd,
    InvalidProxy(String),
    InvalidDns,
    InvalidMtu,
    Spawn(String),
    Unsupported,
}

impl TunnelError {
    pub fn code(&self) -> i32 {
        match self {
            Self::AlreadyRunning => -1,
            Self::InvalidFd => -2,
            Self::InvalidProxy(_) => -3,
            Self::InvalidDns => -4,
            Self::InvalidMtu => -5,
            Self::Spawn(_) => -6,
            Self::Unsupported => -7,
        }
    }
}

#[derive(Debug, Clone)]
pub struct TunnelConfig {
    pub fd: i32,
    pub mtu: u16,
    pub proxy_url: String,
    pub dns_mode: DnsMode,
    pub dns_addr: String,
}

struct Running {
    token: CancellationToken,
    handle: JoinHandle<()>,
    counters: Arc<TrafficCounters>,
}

static RUNNING: Mutex<Option<Running>> = Mutex::new(None);

/// 校验配置并构造 tun2proxy 参数（不触碰 fd，可在任意平台测试）。
pub fn build_args(cfg: &TunnelConfig) -> Result<Args, TunnelError> {
    if cfg.fd < 0 {
        return Err(TunnelError::InvalidFd);
    }
    if !(576..=9000).contains(&cfg.mtu) {
        return Err(TunnelError::InvalidMtu);
    }
    let proxy = ProxyParameters::try_from(cfg.proxy_url.as_str())
        .map_err(|e| TunnelError::InvalidProxy(format!("{e:?}")))?;
    let dns_addr: IpAddr = cfg.dns_addr.parse().map_err(|_| TunnelError::InvalidDns)?;

    let mut args = Args::default();
    args.proxy(proxy).dns(cfg.dns_mode.to_arg()).dns_addr(dns_addr);
    // Android 上路由由 VpnService 负责，不能让 tun2proxy 改系统路由
    args.setup(false);
    args.mtu = cfg.mtu;
    #[cfg(unix)]
    {
        // fd 归 Kotlin 侧的 ParcelFileDescriptor 所有，这里不能关闭
        args.tun_fd(Some(cfg.fd)).close_fd_on_drop(false);
    }
    Ok(args)
}

pub fn start(cfg: TunnelConfig) -> Result<(), TunnelError> {
    let args = build_args(&cfg)?;
    start_with_args(args, cfg.mtu)
}

#[cfg(unix)]
fn start_with_args(args: Args, mtu: u16) -> Result<(), TunnelError> {
    let mut guard = RUNNING.lock().unwrap_or_else(|e| e.into_inner());
    if let Some(r) = guard.as_ref() {
        if !r.handle.is_finished() {
            return Err(TunnelError::AlreadyRunning);
        }
    }
    // 上一次会话已自行退出，回收线程
    if let Some(old) = guard.take() {
        let _ = old.handle.join();
    }

    let counters = Arc::new(TrafficCounters::default());
    let thread_counters = counters.clone();
    let token = CancellationToken::new();
    let child = token.clone();
    let handle = std::thread::Builder::new()
        .name("iceflow-tun".into())
        .spawn(move || {
            let rt = match tokio::runtime::Builder::new_multi_thread()
                .worker_threads(2)
                .enable_all()
                .thread_name("iceflow-io")
                .build()
            {
                Ok(rt) => rt,
                Err(e) => {
                    log::error!("tokio runtime build failed: {e}");
                    return;
                }
            };
            let result = rt.block_on(async move {
                let mut config = tun::Configuration::default();
                config.mtu(mtu);
                config.raw_fd(args.tun_fd.expect("validated TUN fd"));
                config.close_fd_on_drop(false);
                #[cfg(target_os = "linux")]
                config.platform_config(|platform| { platform.ensure_root_privileges(false); });
                let device = tun::create_as_async(&config)?;
                let device = CountedIo::new(device, thread_counters);
                tun2proxy::run(device, mtu, args, child).await.map_err(std::io::Error::from)
            });
            match result {
                Ok(sessions) => log::info!("tun2proxy exited, sessions={sessions}"),
                Err(e) => log::error!("tun2proxy exited with error: {e:?}"),
            }
        })
        .map_err(|e| TunnelError::Spawn(e.to_string()))?;

    *guard = Some(Running { token, handle, counters });
    log::info!("tunnel started");
    Ok(())
}

#[cfg(not(unix))]
fn start_with_args(_args: Args, _mtu: u16) -> Result<(), TunnelError> {
    Err(TunnelError::Unsupported)
}

/// 停止隧道并等待工作线程退出。重复调用安全。
pub fn stop() {
    let running = RUNNING.lock().unwrap_or_else(|e| e.into_inner()).take();
    if let Some(r) = running {
        r.token.cancel();
        if r.handle.join().is_err() {
            log::error!("tunnel thread panicked");
        }
        log::info!("tunnel stopped");
    }
}

pub fn is_running() -> bool {
    RUNNING
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .as_ref()
        .is_some_and(|r| !r.handle.is_finished())
}

/// 本次会话累计 (上行, 下行) 字节数。
pub fn stats() -> (u64, u64) {
    RUNNING.lock().unwrap_or_else(|e| e.into_inner()).as_ref()
        .map(|running| running.counters.snapshot()).unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn cfg() -> TunnelConfig {
        TunnelConfig {
            fd: 3,
            mtu: 1500,
            proxy_url: "socks5://127.0.0.1:10808".into(),
            dns_mode: DnsMode::Virtual,
            dns_addr: "1.1.1.1".into(),
        }
    }

    #[test]
    fn dns_mode_mapping() {
        assert_eq!(DnsMode::from_i32(0), Some(DnsMode::Virtual));
        assert_eq!(DnsMode::from_i32(1), Some(DnsMode::OverTcp));
        assert_eq!(DnsMode::from_i32(2), Some(DnsMode::Direct));
        assert_eq!(DnsMode::from_i32(9), None);
    }

    #[test]
    fn valid_config_builds_args() {
        let args = build_args(&cfg()).expect("valid config");
        assert_eq!(args.mtu, 1500);
        assert!(!args.setup);
        assert_eq!(args.dns, ArgDns::Virtual);
    }

    #[test]
    fn rejects_invalid_input() {
        let mut c = cfg();
        c.fd = -1;
        assert_eq!(build_args(&c).err(), Some(TunnelError::InvalidFd));

        let mut c = cfg();
        c.mtu = 100;
        assert_eq!(build_args(&c).err(), Some(TunnelError::InvalidMtu));

        let mut c = cfg();
        c.proxy_url = "not a url".into();
        assert!(matches!(build_args(&c).err(), Some(TunnelError::InvalidProxy(_))));

        let mut c = cfg();
        c.dns_addr = "dns.google".into();
        assert_eq!(build_args(&c).err(), Some(TunnelError::InvalidDns));
    }

    #[test]
    fn error_codes_match_kotlin() {
        assert_eq!(TunnelError::AlreadyRunning.code(), -1);
        assert_eq!(TunnelError::InvalidFd.code(), -2);
        assert_eq!(TunnelError::InvalidProxy(String::new()).code(), -3);
        assert_eq!(TunnelError::InvalidDns.code(), -4);
        assert_eq!(TunnelError::InvalidMtu.code(), -5);
        assert_eq!(TunnelError::Spawn(String::new()).code(), -6);
        assert_eq!(TunnelError::Unsupported.code(), -7);
    }

    #[test]
    fn stop_without_start_is_noop() {
        stop();
        assert!(!is_running());
        assert_eq!(stats(), (0, 0));
    }
}
