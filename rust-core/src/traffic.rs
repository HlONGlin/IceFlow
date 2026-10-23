//! Byte accounting at the TUN boundary, independent of proxy callbacks and idle periods.

use std::io;
use std::pin::Pin;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::task::{Context, Poll};
use tokio::io::{AsyncRead, AsyncWrite, ReadBuf};

#[derive(Default)]
pub(crate) struct TrafficCounters {
    up: AtomicU64,
    down: AtomicU64,
}

impl TrafficCounters {
    pub(crate) fn snapshot(&self) -> (u64, u64) {
        (self.up.load(Ordering::Relaxed), self.down.load(Ordering::Relaxed))
    }
}

#[cfg_attr(not(any(unix, test)), allow(dead_code))]
pub(crate) struct CountedIo<D> {
    device: D,
    counters: Arc<TrafficCounters>,
}

#[cfg_attr(not(any(unix, test)), allow(dead_code))]
impl<D> CountedIo<D> {
    pub(crate) fn new(device: D, counters: Arc<TrafficCounters>) -> Self {
        Self { device, counters }
    }
}

impl<D: AsyncRead + Unpin> AsyncRead for CountedIo<D> {
    fn poll_read(mut self: Pin<&mut Self>, cx: &mut Context<'_>, buf: &mut ReadBuf<'_>) -> Poll<io::Result<()>> {
        let before = buf.filled().len();
        let result = Pin::new(&mut self.device).poll_read(cx, buf);
        if let Poll::Ready(Ok(())) = &result {
            // Reading TUN receives packets from Android apps: upload, not download.
            self.counters.up.fetch_add((buf.filled().len() - before) as u64, Ordering::Relaxed);
        }
        result
    }
}

impl<D: AsyncWrite + Unpin> AsyncWrite for CountedIo<D> {
    fn poll_write(mut self: Pin<&mut Self>, cx: &mut Context<'_>, bytes: &[u8]) -> Poll<io::Result<usize>> {
        let result = Pin::new(&mut self.device).poll_write(cx, bytes);
        if let Poll::Ready(Ok(n)) = &result {
            self.counters.down.fetch_add(*n as u64, Ordering::Relaxed);
        }
        result
    }

    fn poll_flush(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut self.device).poll_flush(cx)
    }

    fn poll_shutdown(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut self.device).poll_shutdown(cx)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use tokio::io::{AsyncReadExt, AsyncWriteExt};

    #[tokio::test]
    async fn counts_short_transfers_in_both_directions_immediately() {
        let counters = Arc::new(TrafficCounters::default());
        let (device, mut android) = tokio::io::duplex(64);
        let mut counted = CountedIo::new(device, counters.clone());
        android.write_all(b"upload packet").await.unwrap();
        let mut buf = [0; 13];
        counted.read_exact(&mut buf).await.unwrap();
        assert_eq!(&buf, b"upload packet");
        assert_eq!(counters.snapshot(), (13, 0));
        counted.write_all(b"download").await.unwrap();
        assert_eq!(counters.snapshot(), (13, 8));
        let mut reply = [0; 8];
        android.read_exact(&mut reply).await.unwrap();
        assert_eq!(&reply, b"download");
        // No further traffic is needed to flush these counts.
        assert_eq!(counters.snapshot(), (13, 8));
        assert_eq!(TrafficCounters::default().snapshot(), (0, 0));
    }

    #[tokio::test]
    async fn partial_writes_and_eof_do_not_double_count() {
        let counters = Arc::new(TrafficCounters::default());
        let (device, mut android) = tokio::io::duplex(4);
        let mut counted = CountedIo::new(device, counters.clone());
        assert_eq!(counted.write(b"12345678").await.unwrap(), 4);
        assert_eq!(counters.snapshot(), (0, 4));
        let mut buf = [0; 4];
        android.read_exact(&mut buf).await.unwrap();
        assert_eq!(counted.write(b"5678").await.unwrap(), 4);
        drop(android);
        assert_eq!(counted.read(&mut buf).await.unwrap(), 0);
        assert_eq!(counters.snapshot(), (0, 8));
    }
}
