/**
 * websocket.js — WebSocket management with auto-reconnect and message parsing
 */

class ChatWebSocket {
  /**
   * @param {function} onMessage - Called with parsed message object
   * @param {function} onOpen    - Called when connection is established
   * @param {function} onClose   - Called when connection closes unexpectedly
   */
  constructor(onMessage, onOpen, onClose) {
    this._onMessage = onMessage;
    this._onOpen    = onOpen;
    this._onClose   = onClose;

    this._ws           = null;
    this._reconnectAttempts = 0;
    this._maxReconnects = 10;
    this._reconnectDelay = 3000;  // ms (capped)
    this._reconnectTimer = null;
    this._intentionalClose = false;
  }

  /** Open the WebSocket connection */
  connect() {
    this._intentionalClose = false;
    this._doConnect();
  }

  _doConnect() {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    const url = `${proto}://${location.host}/ws/chat`;

    try {
      this._ws = new WebSocket(url);
    } catch (e) {
      console.error('[WS] Cannot create WebSocket:', e);
      this._scheduleReconnect();
      return;
    }

    this._ws.onopen = () => {
      console.log('[WS] Connected');
      this._reconnectAttempts = 0;
      this._onOpen && this._onOpen();
    };

    this._ws.onmessage = (evt) => {
      const parsed = this._parse(evt.data);
      this._onMessage && this._onMessage(parsed);
    };

    this._ws.onclose = (evt) => {
      console.log('[WS] Closed:', evt.code, evt.reason);
      if (!this._intentionalClose) {
        this._onClose && this._onClose();
        this._scheduleReconnect();
      }
    };

    this._ws.onerror = (err) => {
      console.warn('[WS] Error:', err);
      // onclose will fire next
    };
  }

  _scheduleReconnect() {
    if (this._reconnectAttempts >= this._maxReconnects) {
      console.error('[WS] Max reconnect attempts reached');
      return;
    }
    clearTimeout(this._reconnectTimer);
    // Capped exponential backoff: min 1s, max 3s
    const delay = Math.min(1000 * Math.pow(1.5, this._reconnectAttempts), this._reconnectDelay);
    this._reconnectAttempts++;
    console.log(`[WS] Reconnecting in ${Math.round(delay)}ms (attempt ${this._reconnectAttempts})`);
    this._reconnectTimer = setTimeout(() => this._doConnect(), delay);
  }

  /** Send a raw command string */
  send(text) {
    if (this._ws && this._ws.readyState === WebSocket.OPEN) {
      this._ws.send(text);
    } else {
      console.warn('[WS] send() called but socket not open. Buffered message dropped.');
    }
  }

  /** Close intentionally (no reconnect) */
  disconnect() {
    this._intentionalClose = true;
    clearTimeout(this._reconnectTimer);
    if (this._ws) {
      this._ws.close();
      this._ws = null;
    }
  }

  get isOpen() {
    return this._ws && this._ws.readyState === WebSocket.OPEN;
  }

  /**
   * Parse a raw server text frame into a structured object.
   *
   * Server formats:
   *   Connected successfully as <username>
   *   ERROR: <description>
   *   [ALL:42] username: content
   *   [ROOM:roomName:42] username: content
   *   [PRIVATE:42] fromUser: content
   *   [PRIVATE_SENT:42:username]: content
   *   [SYSTEM] text
   *   [HISTORY] text
   *   [TYPING] username in roomName
   *   [TYPING_PM] username
   *   [ONLINE] user1,user2,...
   *   [EDIT] messageId:newContent
   *   [DELETE] messageId
   */
  _parse(raw) {
    // ── CONNECTED ──────────────────────────────────────
    let m = raw.match(/^Connected successfully as (.+)$/);
    if (m) return { type: 'CONNECTED', username: m[1].trim(), raw };

    // ── ERROR ──────────────────────────────────────────
    m = raw.match(/^ERROR: (.+)$/);
    if (m) return { type: 'ERROR', message: m[1].trim(), raw };

    // ── ALL broadcast ──────────────────────────────────
    m = raw.match(/^\[ALL:(\d+)\] ([^:]+): (.*)$/s);
    if (m) return { type: 'ALL', messageId: Number(m[1]), username: m[2].trim(), content: m[3], raw };

    // ── ROOM ───────────────────────────────────────────
    m = raw.match(/^\[ROOM:([^:]+):(\d+)\] ([^:]+): (.*)$/s);
    if (m) return { type: 'ROOM', roomName: m[1].trim(), messageId: Number(m[2]), username: m[3].trim(), content: m[4], raw };

    // ── PRIVATE (received) ─────────────────────────────
    m = raw.match(/^\[PRIVATE:(\d+)\] ([^:]+): (.*)$/s);
    if (m) return { type: 'PRIVATE', messageId: Number(m[1]), from: m[2].trim(), content: m[3], raw };

    // ── PRIVATE (sent confirmation) ────────────────────
    m = raw.match(/^\[PRIVATE_SENT:(\d+):([^\]]+)\]: (.*)$/s);
    if (m) return { type: 'PRIVATE_SENT', messageId: Number(m[1]), to: m[2].trim(), content: m[3], raw };

    // ── SYSTEM ─────────────────────────────────────────
    m = raw.match(/^\[SYSTEM\] (.*)$/s);
    if (m) return { type: 'SYSTEM', content: m[1], raw };

    // ── HISTORY ────────────────────────────────────────
    m = raw.match(/^\[HISTORY\] (.*)$/s);
    if (m) return { type: 'HISTORY', content: m[1], raw };

    // ── TYPING in room ─────────────────────────────────
    m = raw.match(/^\[TYPING\] ([^ ]+) in (.+)$/);
    if (m) return { type: 'TYPING_ROOM', username: m[1], roomName: m[2].trim(), raw };

    // ── TYPING PM ──────────────────────────────────────
    m = raw.match(/^\[TYPING_PM\] (.+)$/);
    if (m) return { type: 'TYPING_PM', username: m[1].trim(), raw };

    // ── ONLINE list ────────────────────────────────────
    m = raw.match(/^\[ONLINE\] (.*)$/);
    if (m) {
      const users = m[1] ? m[1].split(',').map(u => u.trim()).filter(Boolean) : [];
      return { type: 'ONLINE', users, raw };
    }

    // ── EDIT ───────────────────────────────────────────
    m = raw.match(/^\[EDIT\] (\d+):(.*)$/s);
    if (m) return { type: 'EDIT', messageId: m[1], newContent: m[2], raw };

    // ── DELETE ─────────────────────────────────────────
    m = raw.match(/^\[DELETE\] (\d+)$/);
    if (m) return { type: 'DELETE', messageId: m[1], raw };

    // ── READ PM receipt ────────────────────────────────
    m = raw.match(/^\[READ_PM\] (.+)$/);
    if (m) return { type: 'READ_PM', username: m[1].trim(), raw };

    // ── READ ROOM receipt ──────────────────────────────
    m = raw.match(/^\[READ_ROOM\] ([^:]+):(.+)$/);
    if (m) return { type: 'READ_ROOM', roomName: m[1].trim(), username: m[2].trim(), raw };

    // ── KICKED from room ───────────────────────────────
    m = raw.match(/^\[KICKED\] ([^:]+):(.+)$/);
    if (m) return { type: 'KICKED', roomName: m[1].trim(), username: m[2].trim(), raw };

    // ── LEFT room ──────────────────────────────────────
    m = raw.match(/^\[LEFT_ROOM\] ([^:]+):(.+)$/);
    if (m) return { type: 'LEFT_ROOM', roomName: m[1].trim(), username: m[2].trim(), raw };

    // ── Fallback ───────────────────────────────────────
    return { type: 'UNKNOWN', raw };
  }
}
