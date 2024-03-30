/*
 * Copyright (C) 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//! Client management, including the communication with quiche I/O.

use anyhow::{anyhow, bail, ensure, Result};
use log::{debug, error, info, warn};
use quiche::h3::NameValue;
use ring::hmac;
use ring::rand::SystemRandom;
use std::collections::{hash_map, HashMap};
use std::net::SocketAddr;
use std::pin::Pin;
use std::time::Duration;

pub const DNS_HEADER_SIZE: usize = 12;
pub const MAX_UDP_PAYLOAD_SIZE: usize = 1350;

pub type ConnectionID = Vec<u8>;

const URL_PATH_PREFIX: &str = "/dns-query?dns=";

/// Manages a QUIC and HTTP/3 connection. No socket I/O operations.
pub struct Client {
    /// QUIC connection.
    conn: Pin<Box<quiche::Connection>>,

    /// HTTP/3 connection.
    h3_conn: Option<quiche::h3::Connection>,

    /// Socket address the client from.
    addr: SocketAddr,

    /// The unique ID for the client.
    id: ConnectionID,

    /// Queues the DNS queries being processed in backend.
    /// <Query ID, Stream ID>
    in_flight_queries: HashMap<[u8; 2], u64>,

    /// Queues the second part DNS answers needed to be sent after first part.
    /// <Stream ID, ans>
    pending_answers: Vec<(u64, Vec<u8>)>,
}

impl Client {
    fn new(conn: Pin<Box<quiche::Connection>>, addr: &SocketAddr, id: ConnectionID) -> Client {
        Client {
            conn,
            h3_conn: None,
            addr: *addr,
            id,
            in_flight_queries: HashMap::new(),
            pending_answers: Vec::new(),
        }
    }

    fn create_http3_connection(&mut self) -> Result<()> {
        ensure!(self.h3_conn.is_none(), "HTTP/3 connection is already created");

        let config = quiche::h3::Config::new()?;
        let conn = quiche::h3::Connection::with_transport(&mut self.conn, &config)?;
        self.h3_conn = Some(conn);
        Ok(())
    }

    // Processes HTTP/3 request and returns the wire format DNS query or an empty vector.
    fn handle_http3_request(&mut self) -> Result<Vec<u8>> {
        ensure!(self.h3_conn.is_some(), "HTTP/3 connection not created");

        let h3_conn = self.h3_conn.as_mut().unwrap();
        let mut ret = vec![];

        loop {
            match h3_conn.poll(&mut self.conn) {
                Ok((stream_id, quiche::h3::Event::Headers { list, has_body })) => {
                    info!(
                        "Processing HTTP/3 Headers {:?} on stream id {} has_body {}",
                        list, stream_id, has_body
                    );

                    // Find ":path" field to get the query.
                    if let Some(target) = list.iter().find(|e| {
                        e.name() == b":path" && e.value().starts_with(URL_PATH_PREFIX.as_bytes())
                    }) {
                        let b64url_query = &target.value()[URL_PATH_PREFIX.len()..];
                        let decoded = base64::decode_config(b64url_query, base64::URL_SAFE_NO_PAD)?;
                        self.in_flight_queries.insert([decoded[0], decoded[1]], stream_id);
                        ret = decoded;
                    }
                }
                Ok((stream_id, quiche::h3::Event::Data)) => {
                    warn!("Received unexpected HTTP/3 data");
                    let mut buf = [0; 65535];
                    if let Ok(read) = h3_conn.recv_body(&mut self.conn, stream_id, &mut buf) {
                        warn!("Got {} bytes of response data on stream {}", read, stream_id);
                    }
                }
                Ok(n) => {
                    debug!("Got event {:?}", n);
                }
                Err(quiche::h3::Error::Done) => {
                    debug!("quiche::h3::Error::Done");
                    break;
                }
                Err(e) => bail!("HTTP/3 processing failed: {:?}", e),
            }
        }

        Ok(ret)
    }

    // Converts the clear-text DNS response to a DoH response, and sends it to the quiche.
    pub fn handle_backend_message(&mut self, response: &[u8]) -> Result<()> {
        ensure!(self.h3_conn.is_some(), "HTTP/3 connection not created");
        ensure!(response.len() >= DNS_HEADER_SIZE, "Insufficient bytes of DNS response");

        let len = response.len();
        let headers = vec![
            quiche::h3::Header::new(b":status", b"200"),
            quiche::h3::Header::new(b"content-type", b"application/dns-message"),
            quiche::h3::Header::new(b"content-length", len.to_string().as_bytes()),
            // TODO: need to add cache-control?
        ];

        let h3_conn = self.h3_conn.as_mut().unwrap();
        let query_id = u16::from_be_bytes([response[0], response[1]]);
        let stream_id = self
            .in_flight_queries
            .remove(&[response[0], response[1]])
            .ok_or_else(|| anyhow!("query_id {:x} not found", query_id))?;

        info!("Preparing HTTP/3 response {:?} on stream {}", headers, stream_id);

        h3_conn.send_response(&mut self.conn, stream_id, &headers, false)?;

        // In order to simulate the case that server send multiple packets for a DNS answer,
        // only send half of the answer here. The remaining one will be cached here and then
        // processed later in process_pending_answers().
        let (first, second) = response.split_at(len / 2);
        h3_conn.send_body(&mut self.conn, stream_id, first, false)?;
        self.pending_answers.push((stream_id, second.to_vec()));

        Ok(())
    }

    pub fn process_pending_answers(&mut self) -> Result<()> {
        if let Some((stream_id, ans)) = self.pending_answers.pop() {
            let h3_conn = self.h3_conn.as_mut().unwrap();
            info!("process the remaining response for stream {}", stream_id);
            h3_conn.send_body(&mut self.conn, stream_id, &ans, true)?;
        }
        Ok(())
    }

    // Returns the data the client wants to send.
    pub fn flush_egress(&mut self) -> Result<Vec<u8>> {
        let mut ret = vec![];
        let mut buf = [0; MAX_UDP_PAYLOAD_SIZE];

        let (write, _) = match self.conn.send(&mut buf) {
            Ok(v) => v,
            Err(quiche::Error::Done) => bail!(quiche::Error::Done),
            Err(e) => {
                error!("flush_egress failed: {}", e);
                bail!(e)
            }
        };
        ret.append(&mut buf[..write].to_vec());

        Ok(ret)
    }

    // Processes the packet received from the frontend socket. If |data| is a DoH query,
    // the function returns the wire format DNS query; otherwise, it returns empty vector.
    pub fn handle_frontend_message(&mut self, data: &mut [u8]) -> Result<Vec<u8>> {
        let recv_info = quiche::RecvInfo { from: self.addr };
        self.conn.recv(data, recv_info)?;

        if (self.conn.is_in_early_data() || self.conn.is_established()) && self.h3_conn.is_none() {
            // Create a HTTP3 connection as soon as the QUIC connection is established.
            self.create_http3_connection()?;
            info!("HTTP/3 connection created");
        }

        if self.h3_conn.is_some() {
            return self.handle_http3_request();
        }

        Ok(vec![])
    }

    pub fn is_waiting_for_query(&self, query_id: &[u8; 2]) -> bool {
        self.in_flight_queries.contains_key(query_id)
    }

    pub fn addr(&self) -> SocketAddr {
        self.addr
    }

    pub fn connection_id(&self) -> &ConnectionID {
        self.id.as_ref()
    }

    pub fn timeout(&self) -> Option<Duration> {
        self.conn.timeout()
    }

    pub fn on_timeout(&mut self) {
        self.conn.on_timeout();
    }

    pub fn is_alive(&self) -> bool {
        self.conn.is_established() && !self.conn.is_closed()
    }

    pub fn is_resumed(&self) -> bool {
        self.conn.is_resumed()
    }

    pub fn close(&mut self) {
        let _ = self.conn.close(false, 0, b"Graceful shutdown");
    }
}

impl std::fmt::Debug for Client {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        f.debug_struct("Client")
            .field("addr", &self.addr())
            .field("conn_id", &self.conn.trace_id())
            .finish()
    }
}

pub struct ClientMap {
    clients: HashMap<ConnectionID, Client>,
    conn_id_seed: hmac::Key,
    config: quiche::Config,
}

impl ClientMap {
    pub fn new(config: quiche::Config) -> Result<ClientMap> {
        let conn_id_seed = match hmac::Key::generate(hmac::HMAC_SHA256, &SystemRandom::new()) {
            Ok(v) => v,
            Err(e) => bail!("Failed to generate a seed: {}", e),
        };

        Ok(ClientMap { clients: HashMap::new(), conn_id_seed, config })
    }

    pub fn get_or_create(
        &mut self,
        hdr: &quiche::Header,
        addr: &SocketAddr,
    ) -> Result<&mut Client> {
        let dcid = hdr.dcid.as_ref().to_vec();
        let client = if !self.clients.contains_key(&dcid) {
            ensure!(hdr.ty == quiche::Type::Initial, "Packet is not Initial");
            ensure!(quiche::version_is_supported(hdr.version), "Protocol version not supported");

            let scid = generate_conn_id(&self.conn_id_seed, &dcid);
            let conn = quiche::accept(
                &quiche::ConnectionId::from_ref(&scid),
                None, /* odcid */
                *addr,
                &mut self.config,
            )?;
            let client = Client::new(conn, addr, scid.clone());

            info!("New client: {:?}", client);
            self.clients.insert(scid.clone(), client);
            self.clients.get_mut(&scid).unwrap()
        } else {
            self.clients.get_mut(&dcid).unwrap()
        };

        Ok(client)
    }

    pub fn get_mut(&mut self, id: &[u8]) -> Option<&mut Client> {
        self.clients.get_mut(&id.to_vec())
    }

    pub fn iter_mut(&mut self) -> hash_map::IterMut<ConnectionID, Client> {
        self.clients.iter_mut()
    }

    pub fn iter(&mut self) -> hash_map::Iter<ConnectionID, Client> {
        self.clients.iter()
    }

    pub fn len(&mut self) -> usize {
        self.clients.len()
    }
}

fn generate_conn_id(conn_id_seed: &hmac::Key, dcid: &[u8]) -> ConnectionID {
    let conn_id = hmac::sign(conn_id_seed, dcid);
    let conn_id = &conn_id.as_ref()[..quiche::MAX_CONN_ID_LEN];
    conn_id.to_vec()
}
