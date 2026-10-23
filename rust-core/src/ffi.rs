//! JNI 入口，对应 Kotlin `com.iceflow.core.RustBridge` 中的 external 函数。
//! 所有入口都用 catch_unwind 包裹：panic 跨越 FFI 边界是未定义行为。

use std::panic::{catch_unwind, AssertUnwindSafe};

use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlongArray, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

use crate::tunnel::{self, DnsMode, TunnelConfig};

const ERR_INTERNAL: jint = -8;

fn init_logging() {
    #[cfg(target_os = "android")]
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("IceFlowCore")
            .with_max_level(log::LevelFilter::Info),
    );
}

#[no_mangle]
pub extern "system" fn Java_com_iceflow_core_RustBridge_nativeStartTun(
    mut env: JNIEnv,
    _class: JClass,
    fd: jint,
    mtu: jint,
    proxy_url: JString,
    dns_mode: jint,
    dns_addr: JString,
) -> jint {
    init_logging();
    let proxy_url: String = match env.get_string(&proxy_url) {
        Ok(s) => s.into(),
        Err(_) => return tunnel::TunnelError::InvalidProxy(String::new()).code(),
    };
    let dns_addr: String = match env.get_string(&dns_addr) {
        Ok(s) => s.into(),
        Err(_) => return tunnel::TunnelError::InvalidDns.code(),
    };
    let Some(dns_mode) = DnsMode::from_i32(dns_mode) else {
        return tunnel::TunnelError::InvalidDns.code();
    };
    let Ok(mtu) = u16::try_from(mtu) else {
        return tunnel::TunnelError::InvalidMtu.code();
    };

    let cfg = TunnelConfig { fd, mtu, proxy_url, dns_mode, dns_addr };
    match catch_unwind(AssertUnwindSafe(|| tunnel::start(cfg))) {
        Ok(Ok(())) => 0,
        Ok(Err(e)) => {
            log::error!("start failed: {e:?}");
            e.code()
        }
        Err(_) => ERR_INTERNAL,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_iceflow_core_RustBridge_nativeStopTun(_env: JNIEnv, _class: JClass) {
    let _ = catch_unwind(tunnel::stop);
}

#[no_mangle]
pub extern "system" fn Java_com_iceflow_core_RustBridge_nativeStats(
    env: JNIEnv,
    _class: JClass,
) -> jlongArray {
    let (tx, rx) = catch_unwind(tunnel::stats).unwrap_or((0, 0));
    let values = [tx.min(i64::MAX as u64) as i64, rx.min(i64::MAX as u64) as i64];
    match env.new_long_array(2) {
        Ok(arr) => {
            if env.set_long_array_region(&arr, 0, &values).is_err() {
                return std::ptr::null_mut();
            }
            arr.into_raw()
        }
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_iceflow_core_RustBridge_nativeIsRunning(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    if catch_unwind(tunnel::is_running).unwrap_or(false) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_iceflow_core_RustBridge_nativeVersion(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let version = concat!("iceflow_core ", env!("CARGO_PKG_VERSION"), " (tun2proxy 0.8.6)");
    env.new_string(version)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}
