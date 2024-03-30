/*
* Copyright (C) 2021 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

//! Defines a backing task to keep a HTTP/3 connection running

use crate::boot_time;
use crate::boot_time::BootTime;
use log::{debug, warn};
use quiche::h3;
use std::collections::HashMap;
use std::default::Default;
use std::future;
use std::io;
use std::pin::Pin;
use thiserror::Error;
use tokio::net::UdpSocket;
use tokio::select;
use tokio::sync::{mpsc, oneshot, watch};

use super::Status;

#[derive(Error, Debug)]
pub enum Error {
    #[error("network IO error: {0}")]
    Network(#[from] io::Error),
    #[error("QUIC error: {0}")]
    Quic(#[from] quiche::Error),
    #[error("HTTP/3 error: {0}")]
    H3(#[from] h3::Error),
    #[error("Response delivery error: {0}")]
    StreamSend(#[from] mpsc::error::SendError<Stream>),
    #[error("Connection closed")]
    Closed,
}

pub type Result<T> = std::result::Result<T, Error>;

#[derive(Debug)]
/// HTTP/3 Request to be sent on the connection
pub struct Request {
    /// Request headers
    pub headers: Vec<h3::Header>,
    /// Expiry time for the request, relative to `CLOCK_BOOTTIME`
    pub expiry: Option<BootTime>,
    /// Channel to send the response to
    pub response_tx: oneshot::Sender<Stream>,
}

#[derive(Debug)]
/// HTTP/3 Response
pub struct Stream {
    /// Response headers
    pub headers: Vec<h3::Header>,
    /// Response body
    pub data: Vec<u8>,
    /// Error code if stream was reset
    pub error: Option<u64>,
}

impl Stream {
    fn new(headers: Vec<h3::Header>) -> Self {
        Self { headers, data: Vec::new(), error: None }
    }
}

const MAX_UDP_PACKET_SIZE: usize = 65536;

struct Driver {
    request_rx: mpsc::Receiver<Request>,
    status_tx: watch::Sender<Status>,
    quiche_conn: Pin<Box<quiche::Connection>>,
    socket: UdpSocket,
    // This buffer is large, boxing it will keep it
    // off the stack and prevent it being copied during
    // moves of the driver.
    buffer: Box<[u8; MAX_UDP_PACKET_SIZE]>,
    net_id: u32,
    // Used to check if the connection has entered closing or draining state. A connection can
    // enter closing state if the sender of request_rx's channel has been dropped.
    // Note that we can't check if a receiver is dead without potentially receiving a message, and
    // if we poll on a dead receiver in a select! it will immediately return None. As a result, we
    // need this to gate whether or not to include .recv() in our select!
    closing: bool,
}

struct H3Driver {
    driver: Driver,
    // h3_conn sometimes can't "fit" a request in its available windows.
    // This value holds a peeked request in that case, waiting for
    // transmission to become possible.
    buffered_request: Option<Request>,
    h3_conn: h3::Connection,
    requests: HashMap<u64, Request>,
    streams: HashMap<u64, Stream>,
}

async fn optional_timeout(timeout: Option<boot_time::Duration>, net_id: u32) {
    debug!("optional_timeout: timeout={:?}, network {}", timeout, net_id);
    match timeout {
        Some(timeout) => boot_time::sleep(timeout).await,
        None => future::pending().await,
    }
}

/// Creates a future which when polled will handle events related to a HTTP/3 connection.
/// The returned error code will explain why the connection terminated.
pub async fn drive(
    request_rx: mpsc::Receiver<Request>,
    status_tx: watch::Sender<Status>,
    quiche_conn: Pin<Box<quiche::Connection>>,
    socket: UdpSocket,
    net_id: u32,
) -> Result<()> {
    Driver::new(request_rx, status_tx, quiche_conn, socket, net_id).drive().await
}

impl Driver {
    fn new(
        request_rx: mpsc::Receiver<Request>,
        status_tx: watch::Sender<Status>,
        quiche_conn: Pin<Box<quiche::Connection>>,
        socket: UdpSocket,
        net_id: u32,
    ) -> Self {
        Self {
            request_rx,
            status_tx,
            quiche_conn,
            socket,
            buffer: Box::new([0; MAX_UDP_PACKET_SIZE]),
            net_id,
            closing: false,
        }
    }

    async fn drive(mut self) -> Result<()> {
        // Prime connection
        self.flush_tx().await?;
        loop {
            self = self.drive_once().await?
        }
    }

    fn handle_closed(&self) -> Result<()> {
        if self.quiche_conn.is_closed() {
            // TODO: Also log local_error() once Quiche 0.10.0 is available.
            debug!(
                "Connection {} closed on network {}, peer_error={:x?}",
                self.quiche_conn.trace_id(),
                self.net_id,
                self.quiche_conn.peer_error()
            );
            // We don't care if the receiver has hung up
            let _ = self.status_tx.send(Status::Dead { session: self.quiche_conn.session() });
            Err(Error::Closed)
        } else {
            Ok(())
        }
    }

    fn handle_draining(&mut self) {
        if self.quiche_conn.is_draining() && !self.closing {
            // TODO: Also log local_error() once Quiche 0.10.0 is available.
            debug!(
                "Connection {} is draining on network {}, peer_error={:x?}",
                self.quiche_conn.trace_id(),
                self.net_id,
                self.quiche_conn.peer_error()
            );
            // We don't care if the receiver has hung up
            let _ = self.status_tx.send(Status::Dead { session: self.quiche_conn.session() });

            self.request_rx.close();
            // Drain the pending DNS requests from the queue to make their corresponding future
            // tasks return some error quickly rather than timeout. However, the DNS requests
            // that has been sent will still time out.
            // TODO: re-issue the outstanding DNS requests, such as passing H3Driver.requests
            // along with Status::Dead to the `Network` that can re-issue the DNS requests.
            while self.request_rx.try_recv().is_ok() {}
            self.closing = true;
        }
    }

    async fn drive_once(mut self) -> Result<Self> {
        let timer = optional_timeout(self.quiche_conn.timeout(), self.net_id);
        select! {
            // If a quiche timer would fire, call their callback
            _ = timer => {
                debug!("Driver: Timer expired on network {}", self.net_id);
                self.quiche_conn.on_timeout()
            }
            // If we got packets from our peer, pass them to quiche
            Ok((size, from)) = self.socket.recv_from(self.buffer.as_mut()) => {
                self.quiche_conn.recv(&mut self.buffer[..size], quiche::RecvInfo { from })?;
                debug!("Received {} bytes on network {}", size, self.net_id);
            }
        };
        // Any of the actions in the select could require us to send packets to the peer
        self.flush_tx().await?;

        // If the QUIC connection is live, but the HTTP/3 is not, try to bring it up
        if self.quiche_conn.is_established() {
            debug!(
                "Connection {} established on network {}",
                self.quiche_conn.trace_id(),
                self.net_id
            );
            let h3_config = h3::Config::new()?;
            let h3_conn = h3::Connection::with_transport(&mut self.quiche_conn, &h3_config)?;
            self = H3Driver::new(self, h3_conn).drive().await?;
            let _ = self.status_tx.send(Status::QUIC);
        }

        // If the connection has entered draining state (the server is closing the connection),
        // tell the status watcher not to use the connection. Besides, per Quiche document,
        // the connection should not be dropped until is_closed() returns true.
        // This tokio task will become unowned and get dropped when is_closed() returns true.
        self.handle_draining();

        // If the connection has closed, tear down
        self.handle_closed()?;

        Ok(self)
    }

    async fn flush_tx(&mut self) -> Result<()> {
        let send_buf = self.buffer.as_mut();
        loop {
            match self.quiche_conn.send(send_buf) {
                Err(quiche::Error::Done) => return Ok(()),
                Err(e) => return Err(e.into()),
                Ok((valid_len, send_info)) => {
                    self.socket.send_to(&send_buf[..valid_len], send_info.to).await?;
                    debug!("Sent {} bytes on network {}", valid_len, self.net_id);
                }
            }
        }
    }
}

impl H3Driver {
    fn new(driver: Driver, h3_conn: h3::Connection) -> Self {
        Self {
            driver,
            h3_conn,
            requests: HashMap::new(),
            streams: HashMap::new(),
            buffered_request: None,
        }
    }

    async fn drive(mut self) -> Result<Driver> {
        let _ = self.driver.status_tx.send(Status::H3);
        loop {
            if let Err(e) = self.drive_once().await {
                let _ = self
                    .driver
                    .status_tx
                    .send(Status::Dead { session: self.driver.quiche_conn.session() });
                return Err(e)
            }
        }
    }

    async fn drive_once(&mut self) -> Result<()> {
        // We can't call self.driver.drive_once at the same time as
        // self.driver.request_rx.recv() due to ownership
        let timer = optional_timeout(self.driver.quiche_conn.timeout(), self.driver.net_id);
        // If we've buffered a request (due to the connection being full)
        // try to resend that first
        if let Some(request) = self.buffered_request.take() {
            self.handle_request(request)?;
        }
        select! {
            // Only attempt to enqueue new requests if we have no buffered request and aren't
            // closing
            msg = self.driver.request_rx.recv(), if !self.driver.closing && self.buffered_request.is_none() => {
                match msg {
                    Some(request) => self.handle_request(request)?,
                    None => self.shutdown(true, b"DONE").await?,
                }
            },
            // If a quiche timer would fire, call their callback
            _ = timer => {
                debug!("H3Driver: Timer expired on network {}", self.driver.net_id);
                self.driver.quiche_conn.on_timeout()
            }
            // If we got packets from our peer, pass them to quiche
            Ok((size, from)) = self.driver.socket.recv_from(self.driver.buffer.as_mut()) => {
                self.driver.quiche_conn.recv(&mut self.driver.buffer[..size], quiche::RecvInfo { from }).map(|_| ())?;
                debug!("Received {} bytes on network {}", size, self.driver.net_id);
            }
        };

        // Any of the actions in the select could require us to send packets to the peer
        self.driver.flush_tx().await?;

        // Process any incoming HTTP/3 events
        self.flush_h3().await?;

        // If the connection has entered draining state (the server is closing the connection),
        // tell the status watcher not to use the connection. Besides, per Quiche document,
        // the connection should not be dropped until is_closed() returns true.
        // This tokio task will become unowned and get dropped when is_closed() returns true.
        self.driver.handle_draining();

        // If the connection has closed, tear down
        self.driver.handle_closed()
    }

    fn handle_request(&mut self, request: Request) -> Result<()> {
        debug!("Handling DNS request on network {}, stats={:?}, peer_streams_left_bidi={}, peer_streams_left_uni={}",
                self.driver.net_id, self.driver.quiche_conn.stats(), self.driver.quiche_conn.peer_streams_left_bidi(), self.driver.quiche_conn.peer_streams_left_uni());
        // If the request has already timed out, don't issue it to the server.
        if let Some(expiry) = request.expiry {
            if BootTime::now() > expiry {
                warn!("Abandoning expired DNS request");
                return Ok(());
            }
        }
        let stream_id =
            // If h3_conn says the stream is blocked, this error is recoverable just by trying
            // again once the stream has made progress. Buffer the request for a later retry.
            match self.h3_conn.send_request(&mut self.driver.quiche_conn, &request.headers, true) {
                Err(h3::Error::StreamBlocked) | Err(h3::Error::TransportError(quiche::Error::StreamLimit)) => {
                    // We only call handle_request on a value that has just come out of
                    // buffered_request, or when buffered_request is empty. This assert just
                    // validates that we don't break that assumption later, as it could result in
                    // requests being dropped on the floor under high load.
                    debug!("Stream has become blocked, buffering one request.");
                    assert!(self.buffered_request.is_none());
                    self.buffered_request = Some(request);
                    return Ok(())
                }
                result => result?,
            };
        debug!(
            "Handled DNS request: stream ID {}, network {}, stream_capacity={:?}",
            stream_id,
            self.driver.net_id,
            self.driver.quiche_conn.stream_capacity(stream_id)
        );
        self.requests.insert(stream_id, request);
        Ok(())
    }

    async fn recv_body(&mut self, stream_id: u64) -> Result<()> {
        const STREAM_READ_CHUNK: usize = 4096;
        if let Some(stream) = self.streams.get_mut(&stream_id) {
            loop {
                let base_len = stream.data.len();
                stream.data.resize(base_len + STREAM_READ_CHUNK, 0);
                match self.h3_conn.recv_body(
                    &mut self.driver.quiche_conn,
                    stream_id,
                    &mut stream.data[base_len..],
                ) {
                    Err(h3::Error::Done) => {
                        stream.data.truncate(base_len);
                        return Ok(());
                    }
                    Err(e) => {
                        debug!("recv_body: Error={:?}", e);
                        stream.data.truncate(base_len);
                        return Err(e.into());
                    }
                    Ok(recvd) => {
                        stream.data.truncate(base_len + recvd);
                        debug!(
                            "Got {} bytes of response data from stream ID {} on network {}",
                            recvd, stream_id, self.driver.net_id
                        );
                    }
                }
            }
        } else {
            warn!("Received body for untracked stream ID {}", stream_id);
        }
        Ok(())
    }

    fn discard_datagram(&mut self, _flow_id: u64) -> Result<()> {
        loop {
            match self.h3_conn.recv_dgram(&mut self.driver.quiche_conn, self.driver.buffer.as_mut())
            {
                Err(h3::Error::Done) => return Ok(()),
                Err(e) => return Err(e.into()),
                _ => (),
            }
        }
    }

    async fn flush_h3(&mut self) -> Result<()> {
        loop {
            match self.h3_conn.poll(&mut self.driver.quiche_conn) {
                Err(h3::Error::Done) => return Ok(()),
                Err(e) => return Err(e.into()),
                Ok((stream_id, event)) => self.process_h3_event(stream_id, event).await?,
            }
        }
    }

    async fn process_h3_event(&mut self, stream_id: u64, event: h3::Event) -> Result<()> {
        if !self.requests.contains_key(&stream_id) {
            warn!("Received event {:?} for stream_id {} without a request.", event, stream_id);
        }
        match event {
            h3::Event::Headers { list, has_body } => {
                debug!(
                    "process_h3_event: h3::Event::Headers on stream ID {}, network {}",
                    stream_id, self.driver.net_id
                );
                let stream = Stream::new(list);
                if self.streams.insert(stream_id, stream).is_some() {
                    warn!("Re-using stream ID {} before it was completed.", stream_id)
                }
                if !has_body {
                    self.respond(stream_id);
                }
            }
            h3::Event::Data => {
                debug!(
                    "process_h3_event: h3::Event::Data on stream ID {}, network {}",
                    stream_id, self.driver.net_id
                );
                self.recv_body(stream_id).await?;
            }
            h3::Event::Finished => {
                debug!(
                    "process_h3_event: h3::Event::Finished on stream ID {}, network {}",
                    stream_id, self.driver.net_id
                );
                self.respond(stream_id)
            }
            // This clause is for quiche 0.10.x, we're still on 0.9.x
            //h3::Event::Reset(e) => {
            //    self.streams.get_mut(&stream_id).map(|stream| stream.error = Some(e));
            //    self.respond(stream_id);
            //}
            h3::Event::Datagram => {
                warn!("Unexpected Datagram received");
                // We don't care if something went wrong with the datagram, we didn't
                // want it anyways.
                let _ = self.discard_datagram(stream_id);
            }
            h3::Event::GoAway => self.shutdown(false, b"SERVER GOAWAY").await?,
        }
        Ok(())
    }

    async fn shutdown(&mut self, send_goaway: bool, msg: &[u8]) -> Result<()> {
        debug!(
            "Closing connection {} on network {} with msg {:?}",
            self.driver.quiche_conn.trace_id(),
            self.driver.net_id,
            msg
        );
        self.driver.request_rx.close();
        while self.driver.request_rx.recv().await.is_some() {}
        self.driver.closing = true;
        if send_goaway {
            self.h3_conn.send_goaway(&mut self.driver.quiche_conn, 0)?;
        }
        if self.driver.quiche_conn.close(true, 0, msg).is_err() {
            warn!("Trying to close already closed QUIC connection");
        }
        Ok(())
    }

    fn respond(&mut self, stream_id: u64) {
        match (self.streams.remove(&stream_id), self.requests.remove(&stream_id)) {
            (Some(stream), Some(request)) => {
                debug!(
                    "Sending answer back to resolv, stream ID: {}, network {}",
                    stream_id, self.driver.net_id
                );
                // We don't care about the error, because it means the requestor has left.
                let _ = request.response_tx.send(stream);
            }
            (None, _) => warn!("Tried to deliver untracked stream {}", stream_id),
            (_, None) => warn!("Tried to deliver stream {} to untracked requestor", stream_id),
        }
    }
}
