//! IceFlow Rust 流量层。
//!
//! TUN fd（来自 Android VpnService）→ tun2proxy 用户态协议栈 → 本地 SOCKS5（xray-core）。
//! 对外只暴露 JNI 接口（见 [`ffi`]），核心逻辑在 [`tunnel`] 中，可在主机上单元测试。

pub mod ffi;
pub mod tunnel;
mod traffic;
