/**
 * chat.js — Main chat controller for Chatterbox
 *
 * Responsibilities:
 *  - Auth check on load, redirect to login if not authenticated
 *  - Load rooms + DMs from REST
 *  - Manage WebSocket (live messages only)
 *  - Room/DM switching with REST history
 *  - Typing indicators (send + display)
 *  - Edit / delete messages via REST
 *  - Unread badges
 *  - Members panel, New Room modal, Kick modal
 *  - User search with debounce
 *  - Emoji picker
 *  - Reconnect banner
 *  - Toast notifications
 */
/* global API, ChatWebSocket */

(async function () {
  'use strict';

  /* ==========================================================================
     STATE
     ========================================================================== */
  let currentUser = null;   // { userId, username }
  let ws = null;

  const state = {
    currentChat: null,      // { type: 'room'|'pm', id, name }
    rooms:  [],             // [{ roomId, roomName, description, ownerId, createdAt }]
    dms:    [],             // [{ privateChatId, otherUsername, otherUserId }]
    online: new Set(),      // set of online usernames
    messages: [],           // messages in current view
    oldestMessageId: null,
    hasOlder: true,
    unread: {},             // { 'room-<id>': n, 'pm-<id>': n }
    typingTimers: {},       // username -> timer id (auto-clear display)
    typingUsers: new Set(),
    activeTypingKey: null,  // 'room-<name>' or 'pm-<username>'
  };

  /* ==========================================================================
     DOM REFERENCES
     ========================================================================== */
  const $ = id => document.getElementById(id);

  const els = {
    sidebarAvatar:    $('sidebar-avatar'),
    sidebarUsername:  $('sidebar-username'),
    logoutBtn:        $('logout-btn'),
    userSearch:       $('user-search'),
    searchDropdown:   $('search-dropdown'),
    roomsList:        $('rooms-list'),
    dmsList:          $('dms-list'),
    onlineList:       $('online-list'),
    onlineCountBadge: $('online-count-badge'),
    onlineArrow:      $('online-arrow'),
    onlineToggle:     $('online-toggle'),
    newRoomBtn:       $('new-room-btn'),
    emptyState:       $('empty-state'),
    chatView:         $('chat-view'),
    headerAvatar:     $('header-avatar'),
    headerName:       $('header-name'),
    headerMeta:       $('header-meta'),
    membersBtn:       $('members-btn'),
    roomActionsWrap:  $('room-actions-wrap'),
    roomActionsBtn:   $('room-actions-btn'),
    roomActionsMenu:  $('room-actions-menu'),
    addMembersMenuItem:$('add-members-menu-item'),
    kickMenuItem:     $('kick-menu-item'),
    leaveMenuItem:    $('leave-menu-item'),
    membersFooter:    $('members-footer'),
    addMembersPanelBtn:$('add-members-panel-btn'),
    leaveRoomPanelBtn:$('leave-room-panel-btn'),
    messagesWrap:     $('messages-wrap'),
    messagesArea:     $('messages-area'),
    loadOlderBtn:     $('load-older-btn'),
    typingBar:        $('typing-bar'),
    messageInput:     $('message-input'),
    sendBtn:          $('send-btn'),
    emojiBtn:         $('emoji-btn'),
    emojiPicker:      $('emoji-picker'),
    membersPanel:     $('members-panel'),
    membersList:      $('members-list'),
    closeMembersBtn:  $('close-members-btn'),
    reconnectBanner:  $('reconnect-banner'),
    reconnectText:    $('reconnect-text'),
    // Modals
    newRoomOverlay:       $('new-room-overlay'),
    newRoomName:          $('new-room-name'),
    newRoomDesc:          $('new-room-desc'),
    newRoomUserSearch:    $('new-room-user-search'),
    newRoomSelectedCount: $('new-room-selected-count'),
    newRoomUsersList:     $('new-room-users-list'),
    newRoomError:         $('new-room-error'),
    closeRoomModal:       $('close-room-modal'),
    cancelRoomModal:      $('cancel-room-modal'),
    createRoomBtn:        $('create-room-btn'),
    addMembersOverlay:       $('add-members-overlay'),
    addMembersTitle:         $('add-members-title'),
    addMembersUserSearch:    $('add-members-user-search'),
    addMembersSelectedCount: $('add-members-selected-count'),
    addMembersUsersList:     $('add-members-users-list'),
    addMembersError:         $('add-members-error'),
    closeAddMembersModal:    $('close-add-members-modal'),
    cancelAddMembersModal:   $('cancel-add-members-modal'),
    confirmAddMembersBtn:    $('confirm-add-members-btn'),
    kickOverlay:      $('kick-overlay'),
    kickUsername:     $('kick-username'),
    kickError:        $('kick-error'),
    closeKickModal:   $('close-kick-modal'),
    cancelKickModal:  $('cancel-kick-modal'),
    confirmKickBtn:   $('confirm-kick-btn'),
  };

  /* ==========================================================================
     UTILITIES
     ========================================================================== */

  function esc(str) {
    if (str == null) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function el(tag, classes, attrs) {
    const e = document.createElement(tag);
    if (classes) e.className = classes;
    if (attrs) Object.entries(attrs).forEach(([k, v]) => {
      if (k === 'textContent') e.textContent = v;
      else e.setAttribute(k, v);
    });
    return e;
  }

  function debounce(fn, ms) {
    let t;
    return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), ms); };
  }

  function relativeTime(dateStr) {
    const d = new Date(dateStr);
    const now = Date.now();
    const diff = Math.floor((now - d.getTime()) / 1000);
    if (diff < 5)   return 'just now';
    if (diff < 60)  return `${diff}s ago`;
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
    if (diff < 172800) return 'yesterday';
    return d.toLocaleDateString([], { month: 'short', day: 'numeric' });
  }

  function fullTime(dateStr) {
    const d = new Date(dateStr);
    return d.toLocaleString([], { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  }

  function initials(name) {
    return name ? name[0].toUpperCase() : '?';
  }

  let toastContainer = null;
  function showToast(msg, type = 'info', duration = 3500) {
    if (!toastContainer) {
      toastContainer = el('div', 'toast-container');
      document.body.appendChild(toastContainer);
    }
    const toast = el('div', `toast ${type}`);
    toast.textContent = msg;
    toastContainer.appendChild(toast);
    setTimeout(() => {
      toast.classList.add('out');
      setTimeout(() => toast.remove(), 250);
    }, duration);
  }

  /* ==========================================================================
     AUTHENTICATION CHECK
     ========================================================================== */
  try {
    const me = await API.auth.me();
    if (!me || !me.userId) throw new Error('Not authenticated');
    currentUser = me;
  } catch {
    window.location.href = '/login.html';
    return;
  }

  /* Populate Sidebar Profile */
  els.sidebarAvatar.textContent   = initials(currentUser.username);
  els.sidebarUsername.textContent = currentUser.username;

  /* Logout */
  els.logoutBtn.addEventListener('click', async () => {
    try { await API.auth.logout(); } catch { /* ignore */ }
    if (ws) ws.disconnect();
    window.location.href = '/login.html';
  });

  /* ==========================================================================
     ROOMS MANAGEMENT
     ========================================================================== */
  async function loadRooms() {
    try {
      const rooms = await API.rooms.list();
      state.rooms = rooms || [];
      renderRooms();
    } catch (e) {
      console.error('Failed to load rooms:', e);
    }
  }

  function renderRooms() {
    els.roomsList.innerHTML = '';
    const sorted = [...state.rooms].sort((a, b) => {
      if (a.roomId === 1 || a.roomName.toLowerCase() === 'general') return -1;
      if (b.roomId === 1 || b.roomName.toLowerCase() === 'general') return 1;
      return a.roomName.localeCompare(b.roomName);
    });

    sorted.forEach(room => {
      const key = `room-${room.roomId}`;
      const unread = state.unread[key] || 0;
      const isActive = state.currentChat && state.currentChat.type === 'room' && state.currentChat.id === room.roomId;

      const li = el('li', `chat-item${isActive ? ' active' : ''}`);
      li.dataset.roomId = room.roomId;

      const icon = el('span', 'chat-item-icon', { textContent: '#' });
      li.appendChild(icon);

      const name = el('span', 'chat-item-name', { textContent: room.roomName });
      li.appendChild(name);

      if (unread > 0) {
        const badge = el('span', 'unread-badge', { textContent: unread > 99 ? '99+' : String(unread) });
        li.appendChild(badge);
      }

      li.addEventListener('click', () => openRoom(room));
      els.roomsList.appendChild(li);
    });
  }

  /* ==========================================================================
     DIRECT MESSAGES MANAGEMENT
     ========================================================================== */
  async function loadDMs() {
    try {
      const dms = await API.pm.list();
      state.dms = dms || [];
      renderDMs();
    } catch (e) {
      console.error('Failed to load DMs:', e);
    }
  }

  function renderDMs() {
    els.dmsList.innerHTML = '';
    state.dms.forEach(dm => {
      const key = `pm-${dm.privateChatId}`;
      const unread = state.unread[key] || 0;
      const isOnline = state.online.has(dm.otherUsername);
      const isActive = state.currentChat && state.currentChat.type === 'pm' && state.currentChat.id === dm.privateChatId;

      const li = el('li', `chat-item${isActive ? ' active' : ''}`);
      li.dataset.pmId = dm.privateChatId;

      const dot = el('span', `online-dot ${isOnline ? 'online' : 'offline'}`);
      li.appendChild(dot);

      const name = el('span', 'chat-item-name', { textContent: dm.otherUsername });
      li.appendChild(name);

      if (unread > 0) {
        const badge = el('span', 'unread-badge', { textContent: unread > 99 ? '99+' : String(unread) });
        li.appendChild(badge);
      }

      li.addEventListener('click', () => openDM(dm));
      els.dmsList.appendChild(li);
    });
  }

  /* ==========================================================================
     ONLINE USERS
     ========================================================================== */
  async function loadOnlineUsers() {
    try {
      const users = await API.users.online();
      state.online = new Set(Array.isArray(users) ? users : []);
      renderOnlineList();
    } catch (e) {
      console.error('Failed to load online users:', e);
    }
  }

  function renderOnlineList() {
    els.onlineList.innerHTML = '';
    els.onlineCountBadge.textContent = state.online.size;
    state.online.forEach(username => {
      const li = el('li', 'chat-item');
      const dot = el('span', 'online-dot online');
      const name = el('span', 'chat-item-name', { textContent: username });
      li.appendChild(dot);
      li.appendChild(name);
      li.addEventListener('click', () => startPMByUsername(username));
      els.onlineList.appendChild(li);
    });
    renderDMs();
  }

  let onlineExpanded = false;
  els.onlineToggle.addEventListener('click', () => {
    onlineExpanded = !onlineExpanded;
    els.onlineList.style.display = onlineExpanded ? '' : 'none';
    els.onlineArrow.classList.toggle('open', onlineExpanded);
  });

  /* ==========================================================================
     OPEN ROOM
     ========================================================================== */
  async function openRoom(room) {
    state.currentChat = { type: 'room', id: room.roomId, name: room.roomName };
    state.messages = [];
    state.oldestMessageId = null;
    state.hasOlder = true;

    const key = `room-${room.roomId}`;
    state.unread[key] = 0;

    showChatView();
    updateChatHeader();
    clearMessages();
    renderRooms();

    // Show room actions menu for all custom rooms (or if user is owner)
    const isGeneral = room.roomId === 1 || room.roomName.toLowerCase() === 'general';
    let isOwner = Number(room.ownerId) === Number(currentUser.userId);
    state.isCurrentRoomAdmin = isOwner;

    if (els.addMembersMenuItem) els.addMembersMenuItem.style.display = (isOwner && !isGeneral) ? '' : 'none';
    if (els.addMembersPanelBtn) els.addMembersPanelBtn.style.display = (isOwner && !isGeneral) ? '' : 'none';
    els.kickMenuItem.style.display = isOwner ? '' : 'none';
    if (els.leaveMenuItem) els.leaveMenuItem.style.display = isGeneral ? 'none' : '';
    if (els.membersFooter) els.membersFooter.style.display = isGeneral ? 'none' : 'flex';
    els.roomActionsWrap.style.display = (!isGeneral || isOwner) ? '' : 'none';
    els.membersBtn.style.display = '';

    await loadRoomHistory(room.roomId);

    if (ws) {
      ws.send(`/join ${room.roomName}`);
      ws.send(`/read_room ${room.roomName}`);
    }

    // Check room members' roles and lastSeen to update admin status and read ticks
    try {
      const members = await API.rooms.members(room.roomId);
      if (Array.isArray(members)) {
        const myMembership = members.find(m =>
          Number(m.userId) === Number(currentUser.userId) || m.username === currentUser.username
        );
        if (myMembership && (myMembership.role === 'OWNER' || myMembership.role === 'ADMIN')) {
          isOwner = true;
          state.isCurrentRoomAdmin = true;
          if (els.addMembersMenuItem && !isGeneral) els.addMembersMenuItem.style.display = '';
          if (els.addMembersPanelBtn && !isGeneral) els.addMembersPanelBtn.style.display = '';
          els.kickMenuItem.style.display = '';
          els.roomActionsWrap.style.display = '';
        }
        const otherMembers = members.filter(m => Number(m.userId) !== Number(currentUser.userId));
        for (const m of otherMembers) {
          try {
            const profile = await API.users.profile(m.userId);
            if (profile && profile.lastSeen) updateReadTicks(profile.lastSeen);
          } catch { /* ignore */ }
        }
      }
    } catch { /* ignore */ }

    clearTypingIndicator();
    state.activeTypingKey = `room-${room.roomName}`;
    els.messageInput.focus();
  }

  async function loadRoomHistory(roomId, prepend = false) {
    try {
      const history = await API.rooms.history(roomId, 50);
      processHistory(history, prepend);
    } catch (e) {
      console.error('Failed to load room history:', e);
    }
  }

  /* ==========================================================================
     OPEN DM
     ========================================================================== */
  async function openDM(dm) {
    state.currentChat = { type: 'pm', id: dm.privateChatId, name: dm.otherUsername };
    state.messages = [];
    state.oldestMessageId = null;
    state.hasOlder = true;

    const key = `pm-${dm.privateChatId}`;
    state.unread[key] = 0;

    showChatView();
    updateChatHeader();
    clearMessages();
    renderDMs();

    els.membersBtn.style.display = 'none';
    els.roomActionsWrap.style.display = 'none';

    await loadDMHistory(dm.privateChatId);

    // Tell the server we've read this conversation (updates our last_seen)
    if (ws) ws.send(`/read_pm ${dm.otherUsername}`);

    // Fetch partner profile to update read ticks
    const partnerId = (dm.user1Id === currentUser.userId || dm.user1Id === Number(currentUser.userId))
      ? dm.user2Id : dm.user1Id;
    if (partnerId) {
      try {
        const profile = await API.users.profile(partnerId);
        if (profile && profile.lastSeen) updateReadTicks(profile.lastSeen);
      } catch { /* ignore */ }
    }

    clearTypingIndicator();
    state.activeTypingKey = `pm-${dm.otherUsername}`;
    els.messageInput.focus();
  }

  async function loadDMHistory(pmId, prepend = false) {
    try {
      const history = await API.pm.history(pmId, 50);
      processHistory(history, prepend);
    } catch (e) {
      console.error('Failed to load DM history:', e);
    }
  }

  /* ==========================================================================
     START PM BY USERNAME
     ========================================================================== */
  async function startPMByUsername(username) {
    if (username === currentUser.username) return;
    try {
      const dm = await API.pm.start(username);
      if (!state.dms.find(d => d.privateChatId === dm.privateChatId)) {
        state.dms.push(dm);
      }
      renderDMs();
      openDM(dm);
    } catch (e) {
      showToast('Could not start conversation: ' + e.message, 'error');
    }
  }

  /* ==========================================================================
     HISTORY PROCESSING
     ========================================================================== */
  function processHistory(messages, prepend = false) {
    if (!Array.isArray(messages)) return;
    if (messages.length === 0) {
      state.hasOlder = false;
      els.loadOlderBtn.classList.add('hidden');
      return;
    }

    if (prepend) {
      state.messages = [...messages, ...state.messages];
    } else {
      state.messages = messages;
    }

    const ids = messages.map(m => Number(m.messageId)).filter(Boolean);
    if (ids.length > 0) {
      const minId = Math.min(...ids);
      if (state.oldestMessageId === null || minId < state.oldestMessageId) {
        state.oldestMessageId = minId;
      }
    }

    state.hasOlder = messages.length >= 20;

    if (prepend) {
      prependMessages(messages);
    } else {
      renderAllMessages();
      scrollToBottom(false);
    }

    els.loadOlderBtn.classList.toggle('hidden', !state.hasOlder);
  }

  function showChatView() {
    els.emptyState.classList.add('hidden');
    els.chatView.classList.remove('hidden');
  }

  function updateChatHeader() {
    const chat = state.currentChat;
    if (!chat) return;

    if (chat.type === 'room') {
      const room = state.rooms.find(r => r.roomId === chat.id);
      els.headerAvatar.textContent = '#';
      els.headerAvatar.className = 'chat-header-avatar';
      els.headerName.textContent = chat.name;
      els.headerMeta.textContent = room ? (room.description || 'Public channel') : 'Public channel';
    } else {
      const isOnline = state.online.has(chat.name);
      els.headerAvatar.textContent = initials(chat.name);
      els.headerAvatar.className = `chat-header-avatar has-status ${isOnline ? 'online' : 'offline'}`;
      els.headerName.textContent = chat.name;
      els.headerMeta.textContent = isOnline ? 'Online now' : 'Offline';
    }
  }

  function clearMessages() {
    els.messagesArea.innerHTML = '';
    els.loadOlderBtn.classList.add('hidden');
  }

  /* ==========================================================================
     MESSAGE RENDERING
     ========================================================================== */
  function deduplicateDateDividers() {
    const seenLabels = new Set();
    els.messagesArea.querySelectorAll('.date-divider').forEach(div => {
      const label = (div.textContent || '').trim();
      if (seenLabels.has(label)) {
        div.remove();
      } else {
        seenLabels.add(label);
      }
    });
  }

  function renderAllMessages() {
    els.messagesArea.innerHTML = '';
    let lastDateLabel = null;
    let lastSender = null;
    let lastSenderTime = null;

    state.messages.forEach((msg) => {
      const msgDate = new Date(msg.createdAt || Date.now());
      const dateLabel = formatDateDivider(msgDate);
      if (dateLabel !== lastDateLabel) {
        lastDateLabel = dateLabel;
        lastSender = null;
        const divider = el('div', 'date-divider', { textContent: dateLabel });
        els.messagesArea.appendChild(divider);
      }

      const isConsecutive = lastSender === msg.senderUsername &&
        lastSenderTime && (msgDate - lastSenderTime) < 2 * 60 * 1000;

      const msgEl = buildMessageEl(msg, isConsecutive);
      msgEl.dataset.sender = msg.senderUsername;
      els.messagesArea.appendChild(msgEl);
      lastSender = msg.senderUsername;
      lastSenderTime = msgDate;
    });

    deduplicateDateDividers();
  }

  function prependMessages(messages) {
    const wrap = els.messagesArea;
    const previousHeight = els.messagesWrap.scrollHeight;
    const previousTop = els.messagesWrap.scrollTop;

    let lastDateLabel = null;
    let lastSender = null;
    let lastSenderTime = null;
    const fragment = document.createDocumentFragment();

    messages.forEach(msg => {
      const msgDate = new Date(msg.createdAt || Date.now());
      const dateLabel = formatDateDivider(msgDate);
      if (dateLabel !== lastDateLabel) {
        lastDateLabel = dateLabel;
        lastSender = null;
        const divider = el('div', 'date-divider', { textContent: dateLabel });
        fragment.appendChild(divider);
      }
      const isConsecutive = lastSender === msg.senderUsername &&
        lastSenderTime && (msgDate - lastSenderTime) < 2 * 60 * 1000;
      const msgEl = buildMessageEl(msg, isConsecutive);
      msgEl.dataset.sender = msg.senderUsername;
      fragment.appendChild(msgEl);
      lastSender = msg.senderUsername;
      lastSenderTime = msgDate;
    });

    wrap.insertBefore(fragment, wrap.firstChild);
    deduplicateDateDividers();
    els.messagesWrap.scrollTop = els.messagesWrap.scrollHeight - previousHeight + previousTop;
  }

  function formatDateDivider(date) {
    const now = new Date();
    const isToday = date.toDateString() === now.toDateString();
    const yesterday = new Date(now);
    yesterday.setDate(now.getDate() - 1);
    const isYesterday = date.toDateString() === yesterday.toDateString();
    if (isToday) return 'Today';
    if (isYesterday) return 'Yesterday';
    return date.toLocaleDateString([], { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
  }

  function buildMessageEl(msg, isConsecutive = false) {
    const isOwn = msg.senderUsername === currentUser.username || Number(msg.senderId) === Number(currentUser.userId);
    const isDeleted = msg.deletedAt != null;
    const isEdited = !isDeleted && msg.updatedAt && msg.updatedAt !== msg.createdAt;
    const isSystem = !msg.senderUsername;

    const row = el('div', `msg-row${isOwn ? ' own' : isSystem ? ' system' : ''}`);
    row.dataset.messageId = msg.messageId;

    if (isSystem) {
      const bubble = el('div', 'msg-bubble system', { textContent: msg.content });
      row.appendChild(bubble);
      return row;
    }

    if (!isOwn) {
      const avatar = el('div', `msg-avatar${isConsecutive ? ' placeholder' : ''}`);
      if (!isConsecutive) avatar.textContent = initials(msg.senderUsername);
      row.appendChild(avatar);
    }

    const col = el('div', 'msg-col');

    if (!isOwn && !isConsecutive) {
      const uname = el('div', 'msg-username', { textContent: msg.senderUsername });
      col.appendChild(uname);
    }

    const bubble = el('div', `msg-bubble ${isOwn ? 'own' : 'other'}${isDeleted ? ' deleted' : ''}`);
    bubble.textContent = isDeleted ? '[Message deleted]' : msg.content;

    // Action buttons for own messages
    if (isOwn && !isDeleted) {
      const actions = el('div', 'msg-actions');

      const editBtn = el('button', 'msg-action-btn', { title: 'Edit message', 'aria-label': 'Edit message' });
      editBtn.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>';
      editBtn.addEventListener('click', (e) => { e.stopPropagation(); startInlineEdit(row, msg); });
      actions.appendChild(editBtn);

      const delBtn = el('button', 'msg-action-btn delete', { title: 'Delete message', 'aria-label': 'Delete message' });
      delBtn.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>';
      delBtn.addEventListener('click', (e) => { e.stopPropagation(); deleteMessage(msg.messageId, row); });
      actions.appendChild(delBtn);

      row.appendChild(actions);
    }

    col.appendChild(bubble);

    // Footer timestamp
    const footer = el('div', 'msg-footer');
    if (isEdited) {
      const editedLabel = el('span', 'msg-edited', { textContent: '(edited)' });
      footer.appendChild(editedLabel);
    }
    const ts = el('span', 'msg-timestamp', {
      textContent: relativeTime(msg.createdAt),
      'data-full': fullTime(msg.createdAt),
    });
    footer.appendChild(ts);

    // Add read ticks for own messages in PM and Room chats
    if (isOwn && !isDeleted && state.currentChat) {
      // ✓ = sent (saved), ✓✓ grey = delivered (recipient/member online), ✓✓ blue = read
      const delivered = state.currentChat.type === 'pm'
        ? state.online.has(state.currentChat.name)
        : Array.from(state.online).some(u => u !== currentUser.username);
      const ticks = el('span', `msg-ticks ${delivered ? 'delivered' : 'sent'}`, {
        textContent: delivered ? '✓✓' : '✓',
      });
      ticks.dataset.createdAt = msg.createdAt || new Date().toISOString();
      footer.appendChild(ticks);
    }

    col.appendChild(footer);

    row.appendChild(col);
    return row;
  }

  function markAllTicksRead() {
    document.querySelectorAll('.msg-ticks').forEach(tick => {
      tick.textContent = '✓✓';
      tick.className = 'msg-ticks read';
    });
  }

  function upgradeDeliveredTicks() {
    if (!state.currentChat) return;
    const isDelivered = state.currentChat.type === 'pm'
      ? state.online.has(state.currentChat.name)
      : Array.from(state.online).some(u => u !== currentUser.username);
    if (!isDelivered) return;
    document.querySelectorAll('.msg-ticks.sent').forEach(tick => {
      tick.textContent = '✓✓';
      tick.className = 'msg-ticks delivered';
    });
  }

  function updateReadTicks(partnerLastSeenStr) {
    if (!partnerLastSeenStr) return;
    const partnerLastSeen = new Date(partnerLastSeenStr);
    if (isNaN(partnerLastSeen)) return;
    document.querySelectorAll('.msg-ticks').forEach(tick => {
      const msgTime = new Date(tick.dataset.createdAt);
      // Allow 1s tolerance because MySQL CURRENT_TIMESTAMP has second-level precision
      if (!isNaN(msgTime) && msgTime.getTime() <= partnerLastSeen.getTime() + 1000) {
        tick.textContent = '✓✓';
        tick.className = 'msg-ticks read';
      }
    });
  }

  function appendLiveMessage(msg) {
    const msgDate = new Date(msg.createdAt || Date.now());
    const thisDateLabel = formatDateDivider(msgDate);

    const existingSameDay = Array.from(els.messagesArea.querySelectorAll('.date-divider'))
      .some(div => (div.textContent || '').trim() === thisDateLabel);
    if (!existingSameDay) {
      const divider = el('div', 'date-divider', { textContent: thisDateLabel });
      els.messagesArea.appendChild(divider);
    }

    const rows = els.messagesArea.querySelectorAll('.msg-row:not(.system)');
    const lastRow = rows.length > 0 ? rows[rows.length - 1] : null;
    const lastSender = lastRow ? lastRow.dataset.sender : null;
    const isConsecutive = lastSender === msg.senderUsername;

    const msgEl = buildMessageEl(msg, isConsecutive);
    msgEl.dataset.sender = msg.senderUsername;
    els.messagesArea.appendChild(msgEl);

    deduplicateDateDividers();
    state.messages.push(msg);
    scrollToBottomIfAtBottom();
  }

  function scrollToBottom(smooth = true) {
    els.messagesWrap.scrollTo({
      top: els.messagesWrap.scrollHeight,
      behavior: smooth ? 'smooth' : 'instant',
    });
  }

  function scrollToBottomIfAtBottom() {
    const wrap = els.messagesWrap;
    const atBottom = wrap.scrollHeight - wrap.scrollTop - wrap.clientHeight < 100;
    if (atBottom) {
      requestAnimationFrame(() => scrollToBottom());
    } else {
      showScrollToBottomBtn();
    }
  }

  let scrollBtn = null;
  function showScrollToBottomBtn() {
    if (scrollBtn) return;
    const main = document.getElementById('main-panel');
    scrollBtn = el('button', 'scroll-btn', { title: 'Scroll to bottom', 'aria-label': 'Scroll to bottom' });
    scrollBtn.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M12 5v14m-7-7 7 7 7-7"/></svg>';
    scrollBtn.addEventListener('click', () => { scrollToBottom(); removeScrollBtn(); });
    main.style.position = 'relative';
    main.appendChild(scrollBtn);
  }

  function removeScrollBtn() {
    if (scrollBtn) { scrollBtn.remove(); scrollBtn = null; }
  }

  els.messagesWrap.addEventListener('scroll', () => {
    const wrap = els.messagesWrap;
    const atBottom = wrap.scrollHeight - wrap.scrollTop - wrap.clientHeight < 100;
    if (atBottom) removeScrollBtn();
  });

  function appendSystemMessage(text) {
    const row = el('div', 'msg-row system');
    const bubble = el('div', 'msg-bubble system', { textContent: text });
    row.appendChild(bubble);
    els.messagesArea.appendChild(row);
    scrollToBottomIfAtBottom();
  }

  /* ==========================================================================
     EDIT / DELETE
     ========================================================================== */
  function startInlineEdit(row, msg) {
    const col = row.querySelector('.msg-col');
    const bubble = col.querySelector('.msg-bubble');
    const footer = col.querySelector('.msg-footer');
    const originalContent = msg.content;

    const wrap = el('div', 'inline-edit-wrap');
    const textarea = el('textarea', 'inline-edit-input');
    textarea.value = originalContent;
    textarea.rows = 1;
    autoResize(textarea);
    wrap.appendChild(textarea);

    const hint = el('div', 'inline-edit-hint', { textContent: 'Enter to save · Escape to cancel' });
    wrap.appendChild(hint);

    bubble.replaceWith(wrap);
    footer.style.display = 'none';

    textarea.focus();
    textarea.selectionStart = textarea.value.length;

    async function saveEdit() {
      const newContent = textarea.value.trim();
      if (!newContent || newContent === originalContent) {
        cancelEdit();
        return;
      }
      try {
        const updated = await API.messages.edit(msg.messageId, newContent);
        msg.content = newContent;
        msg.updatedAt = (updated && updated.updatedAt) || new Date().toISOString();
        const newBubble = el('div', 'msg-bubble own', { textContent: newContent });
        wrap.replaceWith(newBubble);
        footer.style.display = '';

        let editedLabel = footer.querySelector('.msg-edited');
        if (!editedLabel) {
          editedLabel = el('span', 'msg-edited', { textContent: '(edited)' });
          footer.insertBefore(editedLabel, footer.firstChild);
        }
      } catch (e) {
        showToast('Failed to edit: ' + e.message, 'error');
        cancelEdit();
      }
    }

    function cancelEdit() {
      const restoredBubble = el('div', 'msg-bubble own', { textContent: originalContent });
      wrap.replaceWith(restoredBubble);
      footer.style.display = '';
    }

    textarea.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); saveEdit(); }
      if (e.key === 'Escape') cancelEdit();
    });
    textarea.addEventListener('input', () => autoResize(textarea));
  }

  async function deleteMessage(messageId, row) {
    if (!confirm('Are you sure you want to delete this message?')) return;
    try {
      await API.messages.delete(messageId);
      const bubble = row.querySelector('.msg-bubble');
      if (bubble) {
        bubble.textContent = '[Message deleted]';
        bubble.classList.add('deleted');
      }
      const actions = row.querySelector('.msg-actions');
      if (actions) actions.remove();
    } catch (e) {
      showToast('Failed to delete: ' + e.message, 'error');
    }
  }

  /* ==========================================================================
     LOAD OLDER MESSAGES
     ========================================================================== */
  els.loadOlderBtn.addEventListener('click', async () => {
    if (!state.currentChat || !state.oldestMessageId || !state.hasOlder) return;
    els.loadOlderBtn.textContent = 'Loading...';
    els.loadOlderBtn.disabled = true;

    try {
      let older;
      if (state.currentChat.type === 'room') {
        older = await API.rooms.older(state.currentChat.id, state.oldestMessageId, 20);
      } else {
        older = await API.pm.older(state.currentChat.id, state.oldestMessageId, 20);
      }

      if (!older || older.length === 0) {
        state.hasOlder = false;
        els.loadOlderBtn.classList.add('hidden');
      } else {
        processHistory(older, true);
        const ids = older.map(m => Number(m.messageId)).filter(Boolean);
        state.oldestMessageId = Math.min(...ids, state.oldestMessageId);
      }
    } catch (e) {
      showToast('Failed to load older messages', 'error');
    } finally {
      els.loadOlderBtn.textContent = 'Load older messages';
      els.loadOlderBtn.disabled = false;
    }
  });

  /* ==========================================================================
     SEND MESSAGE
     ========================================================================== */
  function sendMessage() {
    if (!ws || !state.currentChat) return;
    const content = els.messageInput.value.trim();
    if (!content) return;

    const chat = state.currentChat;
    let command;
    if (chat.type === 'room') {
      if (chat.name.toLowerCase() === 'general' || chat.id === 1) {
        command = `/all ${content}`;
      } else {
        command = `/room ${chat.name} ${content}`;
      }
    } else {
      command = `/msg ${chat.name} ${content}`;
    }

    ws.send(command);
    els.messageInput.value = '';
    autoResize(els.messageInput);
    stopTypingIndicatorSend();
  }

  els.sendBtn.addEventListener('click', sendMessage);

  els.messageInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  });

  function autoResize(ta) {
    ta.style.height = 'auto';
    ta.style.height = Math.min(ta.scrollHeight, 140) + 'px';
  }
  els.messageInput.addEventListener('input', () => autoResize(els.messageInput));

  /* ==========================================================================
     TYPING INDICATORS
     ========================================================================== */
  let typingSendTimer = null;

  els.messageInput.addEventListener('input', debounce(() => {
    if (!ws || !state.currentChat || !els.messageInput.value.trim()) return;
    sendTypingIndicator();
  }, 400));

  function sendTypingIndicator() {
    if (!ws || !state.currentChat) return;
    if (state.currentChat.type === 'room') {
      ws.send(`/typing_room ${state.currentChat.name}`);
    } else {
      ws.send(`/typing_pm ${state.currentChat.name}`);
    }
  }

  function stopTypingIndicatorSend() {
    clearTimeout(typingSendTimer);
  }

  function showTypingIndicator(username) {
    state.typingUsers.add(username);
    renderTypingBar();
    clearTimeout(state.typingTimers[username]);
    state.typingTimers[username] = setTimeout(() => {
      state.typingUsers.delete(username);
      delete state.typingTimers[username];
      renderTypingBar();
    }, 3000);
  }

  function renderTypingBar() {
    const users = [...state.typingUsers];
    if (users.length === 0) {
      els.typingBar.innerHTML = '';
      return;
    }
    const names = users.slice(0, 3).map(u => `<strong>${esc(u)}</strong>`).join(', ');
    const verb = users.length === 1 ? 'is' : 'are';
    els.typingBar.innerHTML = `${names} ${verb} typing<span class="typing-dots"><span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span></span>`;
  }

  function clearTypingIndicator() {
    state.typingUsers.clear();
    Object.values(state.typingTimers).forEach(clearTimeout);
    state.typingTimers = {};
    els.typingBar.innerHTML = '';
  }

  /* ==========================================================================
     WEBSOCKET INITIALIZATION & EVENT DISPATCH
     ========================================================================== */
  function initWebSocket() {
    ws = new ChatWebSocket(onWsMessage, onWsOpen, onWsClose);
    ws.connect();
  }

  function onWsOpen() {
    hideReconnectBanner();
    if (state.currentChat) {
      clearMessages();
      if (state.currentChat.type === 'room') {
        loadRoomHistory(state.currentChat.id);
        ws.send(`/read_room ${state.currentChat.name}`);
      } else {
        loadDMHistory(state.currentChat.id);
        ws.send(`/read_pm ${state.currentChat.name}`);
      }
    }
    loadOnlineUsers();
  }

  window.addEventListener('focus', () => {
    if (ws && ws.isOpen && state.currentChat) {
      if (state.currentChat.type === 'pm') {
        ws.send(`/read_pm ${state.currentChat.name}`);
      } else if (state.currentChat.type === 'room') {
        ws.send(`/read_room ${state.currentChat.name}`);
      }
    }
  });

  function onWsClose() {
    showReconnectBanner();
  }

  function onWsMessage(msg) {
    switch (msg.type) {

      case 'CONNECTED':
        break;

      case 'ALL': {
        const generalRoom = state.rooms.find(r => r.roomName.toLowerCase() === 'general' || r.roomId === 1);
        if (!generalRoom) break;
        const inGeneral = state.currentChat && state.currentChat.type === 'room' &&
          (state.currentChat.name.toLowerCase() === 'general' || state.currentChat.id === 1);
        const liveMsg = {
          messageId: msg.messageId,
          senderUsername: msg.username,
          senderId: null,
          content: msg.content,
          createdAt: new Date().toISOString(),
          updatedAt: null,
          deletedAt: null
        };
        if (inGeneral) {
          appendLiveMessage(liveMsg);
          if (msg.username !== currentUser.username) {
            markAllTicksRead();
            ws.send(`/read_room ${generalRoom.roomName}`);
          }
        } else {
          incrementUnread(`room-${generalRoom.roomId}`);
        }
        break;
      }

      case 'ROOM': {
        const room = state.rooms.find(r => r.roomName === msg.roomName);
        if (!room) break;
        const isCurrentRoom = state.currentChat && state.currentChat.type === 'room' &&
          state.currentChat.name === msg.roomName;
        const liveMsg = {
          messageId: msg.messageId,
          senderUsername: msg.username,
          senderId: null,
          content: msg.content,
          createdAt: new Date().toISOString(),
          updatedAt: null,
          deletedAt: null
        };
        if (isCurrentRoom) {
          appendLiveMessage(liveMsg);
          if (msg.username !== currentUser.username) {
            markAllTicksRead();
            ws.send(`/read_room ${msg.roomName}`);
          }
        } else {
          incrementUnread(`room-${room.roomId}`);
        }
        break;
      }

      case 'PRIVATE': {
        const dm = state.dms.find(d => d.otherUsername === msg.from);
        const isCurrentDM = state.currentChat && state.currentChat.type === 'pm' &&
          state.currentChat.name === msg.from;
        const liveMsg = {
          messageId: msg.messageId,
          senderUsername: msg.from,
          senderId: null,
          content: msg.content,
          createdAt: new Date().toISOString(),
          updatedAt: null,
          deletedAt: null
        };

        if (isCurrentDM) {
          appendLiveMessage(liveMsg);
          markAllTicksRead();
          ws.send(`/read_pm ${msg.from}`);
          if (!dm) loadDMs();
        } else if (dm) {
          incrementUnread(`pm-${dm.privateChatId}`);
        } else {
          loadDMs().then(() => {
            const newDm = state.dms.find(d => d.otherUsername === msg.from);
            if (newDm) incrementUnread(`pm-${newDm.privateChatId}`);
          });
          showToast(`New message from ${msg.from}`, 'info');
        }
        break;
      }

      case 'PRIVATE_SENT': {
        const isCurrentDM = state.currentChat && state.currentChat.type === 'pm' &&
          state.currentChat.name === msg.to;
        if (isCurrentDM) {
          const liveMsg = {
            messageId: msg.messageId,
            senderUsername: currentUser.username,
            senderId: currentUser.userId,
            content: msg.content,
            createdAt: new Date().toISOString(),
            updatedAt: null,
            deletedAt: null
          };
          appendLiveMessage(liveMsg);
        }
        break;
      }

      case 'SYSTEM': {
        if (msg.content && msg.content.startsWith('You are already a member of')) break;
        if (state.currentChat) appendSystemMessage(msg.content);
        break;
      }

      case 'TYPING_ROOM': {
        if (!state.currentChat || state.currentChat.type !== 'room') break;
        if (state.currentChat.name !== msg.roomName) break;
        if (msg.username === currentUser.username) break;
        showTypingIndicator(msg.username);
        break;
      }

      case 'TYPING_PM': {
        if (!state.currentChat || state.currentChat.type !== 'pm') break;
        if (state.currentChat.name !== msg.username) break;
        if (msg.username === currentUser.username) break;
        showTypingIndicator(msg.username);
        break;
      }

      case 'ONLINE': {
        state.online = new Set(msg.users);
        renderOnlineList();
        updateChatHeader();
        upgradeDeliveredTicks();
        break;
      }

      case 'READ_PM': {
        if (state.currentChat && state.currentChat.type === 'pm' &&
            state.currentChat.name === msg.username) {
          markAllTicksRead();
          setTimeout(markAllTicksRead, 60);
        }
        break;
      }

      case 'READ_ROOM': {
        if (state.currentChat && state.currentChat.type === 'room' &&
            state.currentChat.name.toLowerCase() === msg.roomName.toLowerCase() &&
            msg.username !== currentUser.username) {
          markAllTicksRead();
          setTimeout(markAllTicksRead, 60);
        }
        break;
      }

      case 'KICKED': {
        if (msg.username === currentUser.username) {
          showToast(`You were kicked from #${msg.roomName}`, 'error');
          els.membersPanel.classList.add('hidden');
          state.rooms = state.rooms.filter(r => r.roomName.toLowerCase() !== msg.roomName.toLowerCase());
          renderRooms();
          loadRooms();
          if (state.currentChat && state.currentChat.type === 'room' &&
              state.currentChat.name.toLowerCase() === msg.roomName.toLowerCase()) {
            const generalRoom = state.rooms.find(r => r.roomName.toLowerCase() === 'general' || r.roomId === 1);
            if (generalRoom) openRoom(generalRoom);
          }
        } else {
          showToast(`Kicked @${msg.username} from #${msg.roomName}`, 'success');
          if (!els.membersPanel.classList.contains('hidden') &&
              state.currentChat && state.currentChat.type === 'room' &&
              state.currentChat.name === msg.roomName) {
            loadMembers(state.currentChat.id);
          }
        }
        break;
      }

      case 'LEFT_ROOM': {
        if (msg.username === currentUser.username) {
          els.membersPanel.classList.add('hidden');
          state.rooms = state.rooms.filter(r => r.roomName.toLowerCase() !== msg.roomName.toLowerCase());
          renderRooms();
          loadRooms();
          if (state.currentChat && state.currentChat.type === 'room' &&
              state.currentChat.name.toLowerCase() === msg.roomName.toLowerCase()) {
            const generalRoom = state.rooms.find(r => r.roomName.toLowerCase() === 'general' || r.roomId === 1);
            if (generalRoom) openRoom(generalRoom);
          }
        }
        break;
      }

      case 'ROOM_ADDED': {
        loadRooms();
        showToast(`You were added to #${msg.roomName}`, 'info');
        break;
      }

      case 'EDIT': {
        const row = els.messagesArea.querySelector(`[data-message-id="${msg.messageId}"]`);
        if (row) {
          const bubble = row.querySelector('.msg-bubble');
          if (bubble && !bubble.classList.contains('deleted')) {
            bubble.textContent = msg.newContent;
            const footer = row.querySelector('.msg-footer');
            if (footer && !footer.querySelector('.msg-edited')) {
              const editedLabel = el('span', 'msg-edited', { textContent: '(edited)' });
              footer.insertBefore(editedLabel, footer.firstChild);
            }
          }
        }
        break;
      }

      case 'DELETE': {
        const row = els.messagesArea.querySelector(`[data-message-id="${msg.messageId}"]`);
        if (row) {
          const bubble = row.querySelector('.msg-bubble');
          if (bubble) {
            bubble.textContent = '[Message deleted]';
            bubble.classList.add('deleted');
          }
          const actions = row.querySelector('.msg-actions');
          if (actions) actions.remove();
        }
        break;
      }

      case 'ERROR':
        showToast(msg.message, 'error');
        break;

      default:
        break;
    }
  }

  function incrementUnread(key) {
    state.unread[key] = (state.unread[key] || 0) + 1;
    if (key.startsWith('room-')) renderRooms();
    else renderDMs();
  }

  /* Reconnect Banner */
  let reconnectAttemptDisplay = 0;
  function showReconnectBanner() {
    reconnectAttemptDisplay++;
    els.reconnectBanner.classList.remove('hidden');
    els.reconnectText.textContent = `Reconnecting to server... (attempt ${reconnectAttemptDisplay})`;
  }

  function hideReconnectBanner() {
    reconnectAttemptDisplay = 0;
    els.reconnectBanner.classList.add('hidden');
  }

  /* Members Drawer */
  els.membersBtn.addEventListener('click', async () => {
    if (!state.currentChat || state.currentChat.type !== 'room') return;
    els.membersPanel.classList.remove('hidden');
    await loadMembers(state.currentChat.id);
  });

  els.closeMembersBtn.addEventListener('click', () => {
    els.membersPanel.classList.add('hidden');
  });

  async function loadMembers(roomId) {
    els.membersList.innerHTML = '<li style="padding:12px;color:var(--text-muted);font-size:13px;">Loading...</li>';
    try {
      const members = await API.rooms.members(roomId);
      els.membersList.innerHTML = '';

      const roomObj = state.rooms.find(r => r.roomId === roomId);
      const myMembership = members.find(m =>
        Number(m.userId) === Number(currentUser.userId) || m.username === currentUser.username
      );
      const canKick = state.isCurrentRoomAdmin ||
        (roomObj && Number(roomObj.ownerId) === Number(currentUser.userId)) ||
        (myMembership && (myMembership.role === 'OWNER' || myMembership.role === 'ADMIN'));

      const isGeneral = roomId === 1 || (roomObj && roomObj.roomName.toLowerCase() === 'general');
      if (els.addMembersPanelBtn) {
        els.addMembersPanelBtn.style.display = (canKick && !isGeneral) ? '' : 'none';
      }
      if (els.addMembersMenuItem) {
        els.addMembersMenuItem.style.display = (canKick && !isGeneral) ? '' : 'none';
      }

      members.forEach(m => {
        const li = el('li', 'member-item');
        const avatar = el('div', 'item-avatar', { textContent: initials(m.username) });
        const info = el('div', 'member-info');
        const name = el('div', 'member-name', { textContent: m.username });
        const role = el('div', 'member-role', { textContent: m.role || 'Member' });
        const dot = el('span', `online-dot ${state.online.has(m.username) ? 'online' : 'offline'}`);
        info.appendChild(name);
        info.appendChild(role);
        li.appendChild(dot);
        li.appendChild(avatar);
        li.appendChild(info);

        if (canKick && m.username !== currentUser.username && m.role !== 'OWNER') {
          const kickBtn = el('button', 'btn-danger', { textContent: 'Kick', type: 'button' });
          kickBtn.style.padding = '4px 10px';
          kickBtn.style.fontSize = '11px';
          kickBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            if (ws && state.currentChat) {
              ws.send(`/kick ${state.currentChat.name} ${m.username}`);
            }
          });
          li.appendChild(kickBtn);
        }

        els.membersList.appendChild(li);
      });
    } catch (e) {
      els.membersList.innerHTML = '<li style="padding:12px;color:var(--error);font-size:13px;">Failed to load members.</li>';
    }
  }

  /* Create Room Modal */
  const selectedNewRoomUsers = new Set();
  let allSelectableUsers = [];

  function updateSelectedUsersCount() {
    if (els.newRoomSelectedCount) {
      els.newRoomSelectedCount.textContent = `${selectedNewRoomUsers.size} selected`;
    }
  }

  function renderSelectableUsers(filterQuery = '') {
    if (!els.newRoomUsersList) return;
    els.newRoomUsersList.innerHTML = '';
    const q = filterQuery.trim().toLowerCase();
    const filtered = allSelectableUsers.filter(u =>
      u.username !== currentUser.username &&
      (!q || u.username.toLowerCase().includes(q))
    );

    if (filtered.length === 0) {
      const empty = el('div', 'user-select-empty', {
        textContent: q ? 'No matching users found' : 'No other users available'
      });
      els.newRoomUsersList.appendChild(empty);
      return;
    }

    filtered.forEach(u => {
      const isSelected = selectedNewRoomUsers.has(u.username);
      const item = el('div', `user-select-item${isSelected ? ' selected' : ''}`);

      const check = el('span', 'user-select-check', { textContent: '✓' });
      const isOnline = u.online || state.online.has(u.username);
      const dot = el('span', `online-dot ${isOnline ? 'online' : 'offline'}`);
      const name = el('span', 'user-select-name', { textContent: u.username });

      item.appendChild(check);
      item.appendChild(dot);
      item.appendChild(name);

      item.addEventListener('click', () => {
        if (selectedNewRoomUsers.has(u.username)) {
          selectedNewRoomUsers.delete(u.username);
          item.classList.remove('selected');
        } else {
          selectedNewRoomUsers.add(u.username);
          item.classList.add('selected');
        }
        updateSelectedUsersCount();
      });

      els.newRoomUsersList.appendChild(item);
    });
  }

  if (els.newRoomUserSearch) {
    els.newRoomUserSearch.addEventListener('input', (e) => {
      renderSelectableUsers(e.target.value);
    });
  }

  els.newRoomBtn.addEventListener('click', async () => {
    els.newRoomOverlay.classList.remove('hidden');
    els.newRoomName.value = '';
    els.newRoomDesc.value = '';
    if (els.newRoomUserSearch) els.newRoomUserSearch.value = '';
    selectedNewRoomUsers.clear();
    updateSelectedUsersCount();
    els.newRoomError.classList.add('hidden');
    els.newRoomName.focus();

    if (els.newRoomUsersList) {
      els.newRoomUsersList.innerHTML = '<div class="user-select-empty">Loading users...</div>';
    }
    try {
      const users = await API.users.list();
      allSelectableUsers = Array.isArray(users) ? users : [];
      renderSelectableUsers(els.newRoomUserSearch ? els.newRoomUserSearch.value : '');
    } catch {
      allSelectableUsers = [];
      renderSelectableUsers('');
    }
  });

  function closeNewRoomModal() {
    els.newRoomOverlay.classList.add('hidden');
  }

  els.closeRoomModal.addEventListener('click', closeNewRoomModal);
  els.cancelRoomModal.addEventListener('click', closeNewRoomModal);
  els.newRoomOverlay.addEventListener('click', (e) => { if (e.target === els.newRoomOverlay) closeNewRoomModal(); });

  els.createRoomBtn.addEventListener('click', async () => {
    const name = els.newRoomName.value.trim();
    const desc = els.newRoomDesc.value.trim();
    if (!name) {
      els.newRoomError.textContent = 'Room name is required.';
      els.newRoomError.classList.remove('hidden');
      return;
    }
    els.newRoomError.classList.add('hidden');
    els.createRoomBtn.disabled = true;
    els.createRoomBtn.querySelector('.btn-text').classList.add('hidden');
    els.createRoomBtn.querySelector('.btn-spinner').classList.remove('hidden');

    try {
      const members = Array.from(selectedNewRoomUsers);
      const room = await API.rooms.create(name, desc, members);
      if (!state.rooms.some(r => r.roomId === room.roomId)) {
        state.rooms.push(room);
      }
      renderRooms();
      closeNewRoomModal();
      openRoom(room);
      showToast(`Room #${name} created!`, 'success');
    } catch (e) {
      els.newRoomError.textContent = e.message || 'Failed to create room.';
      els.newRoomError.classList.remove('hidden');
    } finally {
      els.createRoomBtn.disabled = false;
      els.createRoomBtn.querySelector('.btn-text').classList.remove('hidden');
      els.createRoomBtn.querySelector('.btn-spinner').classList.add('hidden');
    }
  });

  /* Add Members to Existing Room Modal */
  const selectedAddRoomUsers = new Set();
  let addRoomCandidates = [];

  function updateAddMembersCount() {
    if (els.addMembersSelectedCount) {
      els.addMembersSelectedCount.textContent = `${selectedAddRoomUsers.size} selected`;
    }
  }

  function renderAddMembersCandidates(filterQuery = '') {
    if (!els.addMembersUsersList) return;
    els.addMembersUsersList.innerHTML = '';
    const q = filterQuery.trim().toLowerCase();
    const filtered = addRoomCandidates.filter(u =>
      !q || u.username.toLowerCase().includes(q)
    );

    if (filtered.length === 0) {
      const empty = el('div', 'user-select-empty', {
        textContent: q ? 'No matching users found' : 'All users are already in this room'
      });
      els.addMembersUsersList.appendChild(empty);
      return;
    }

    filtered.forEach(u => {
      const isSelected = selectedAddRoomUsers.has(u.username);
      const item = el('div', `user-select-item${isSelected ? ' selected' : ''}`);

      const check = el('span', 'user-select-check', { textContent: '✓' });
      const isOnline = u.online || state.online.has(u.username);
      const dot = el('span', `online-dot ${isOnline ? 'online' : 'offline'}`);
      const name = el('span', 'user-select-name', { textContent: u.username });

      item.appendChild(check);
      item.appendChild(dot);
      item.appendChild(name);

      item.addEventListener('click', () => {
        if (selectedAddRoomUsers.has(u.username)) {
          selectedAddRoomUsers.delete(u.username);
          item.classList.remove('selected');
        } else {
          selectedAddRoomUsers.add(u.username);
          item.classList.add('selected');
        }
        updateAddMembersCount();
      });

      els.addMembersUsersList.appendChild(item);
    });
  }

  if (els.addMembersUserSearch) {
    els.addMembersUserSearch.addEventListener('input', (e) => {
      renderAddMembersCandidates(e.target.value);
    });
  }

  async function openAddMembersModal() {
    if (!state.currentChat || state.currentChat.type !== 'room') return;
    els.roomActionsMenu.classList.add('hidden');
    if (els.addMembersTitle) {
      els.addMembersTitle.textContent = `Add Members to #${state.currentChat.name}`;
    }
    if (els.addMembersUserSearch) els.addMembersUserSearch.value = '';
    selectedAddRoomUsers.clear();
    updateAddMembersCount();
    if (els.addMembersError) els.addMembersError.classList.add('hidden');
    if (els.addMembersOverlay) els.addMembersOverlay.classList.remove('hidden');

    if (els.addMembersUsersList) {
      els.addMembersUsersList.innerHTML = '<div class="user-select-empty">Loading users...</div>';
    }

    try {
      const [allUsers, currentMembers] = await Promise.all([
        API.users.list(),
        API.rooms.members(state.currentChat.id),
      ]);
      const existingSet = new Set(
        (Array.isArray(currentMembers) ? currentMembers : []).map(m => m.username)
      );
      existingSet.add(currentUser.username);

      addRoomCandidates = (Array.isArray(allUsers) ? allUsers : []).filter(
        u => !existingSet.has(u.username)
      );
      renderAddMembersCandidates(els.addMembersUserSearch ? els.addMembersUserSearch.value : '');
    } catch {
      addRoomCandidates = [];
      renderAddMembersCandidates('');
    }
  }

  function closeAddMembersModal() {
    if (els.addMembersOverlay) els.addMembersOverlay.classList.add('hidden');
  }

  if (els.addMembersMenuItem) {
    els.addMembersMenuItem.addEventListener('click', openAddMembersModal);
  }
  if (els.addMembersPanelBtn) {
    els.addMembersPanelBtn.addEventListener('click', openAddMembersModal);
  }
  if (els.closeAddMembersModal) {
    els.closeAddMembersModal.addEventListener('click', closeAddMembersModal);
  }
  if (els.cancelAddMembersModal) {
    els.cancelAddMembersModal.addEventListener('click', closeAddMembersModal);
  }
  if (els.addMembersOverlay) {
    els.addMembersOverlay.addEventListener('click', (e) => {
      if (e.target === els.addMembersOverlay) closeAddMembersModal();
    });
  }

  if (els.confirmAddMembersBtn) {
    els.confirmAddMembersBtn.addEventListener('click', async () => {
      if (!state.currentChat || state.currentChat.type !== 'room') return;
      const members = Array.from(selectedAddRoomUsers);
      if (members.length === 0) {
        if (els.addMembersError) {
          els.addMembersError.textContent = 'Select at least one user to add.';
          els.addMembersError.classList.remove('hidden');
        }
        return;
      }
      if (els.addMembersError) els.addMembersError.classList.add('hidden');
      els.confirmAddMembersBtn.disabled = true;
      els.confirmAddMembersBtn.querySelector('.btn-text').classList.add('hidden');
      els.confirmAddMembersBtn.querySelector('.btn-spinner').classList.remove('hidden');

      try {
        await API.rooms.addMembers(state.currentChat.id, members);
        closeAddMembersModal();
        showToast(`Added ${members.join(', ')} to #${state.currentChat.name}`, 'success');
        if (!els.membersPanel.classList.contains('hidden')) {
          await loadMembers(state.currentChat.id);
        }
      } catch (e) {
        if (els.addMembersError) {
          els.addMembersError.textContent = e.message || 'Failed to add members.';
          els.addMembersError.classList.remove('hidden');
        }
      } finally {
        els.confirmAddMembersBtn.disabled = false;
        els.confirmAddMembersBtn.querySelector('.btn-text').classList.remove('hidden');
        els.confirmAddMembersBtn.querySelector('.btn-spinner').classList.add('hidden');
      }
    });
  }

  /* Room Actions Dropdown */
  els.roomActionsBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    els.roomActionsMenu.classList.toggle('hidden');
  });

  document.addEventListener('click', (e) => {
    if (!els.roomActionsWrap.contains(e.target)) {
      els.roomActionsMenu.classList.add('hidden');
    }
  });

  els.kickMenuItem.addEventListener('click', () => {
    els.roomActionsMenu.classList.add('hidden');
    els.kickOverlay.classList.remove('hidden');
    els.kickUsername.value = '';
    els.kickError.classList.add('hidden');
    els.kickUsername.focus();
  });

  function closeKickModal() { els.kickOverlay.classList.add('hidden'); }
  els.closeKickModal.addEventListener('click', closeKickModal);
  els.cancelKickModal.addEventListener('click', closeKickModal);
  els.kickOverlay.addEventListener('click', (e) => { if (e.target === els.kickOverlay) closeKickModal(); });

  async function leaveCurrentRoom() {
    if (!state.currentChat || state.currentChat.type !== 'room') return;
    if (state.currentChat.id === 1 || state.currentChat.name.toLowerCase() === 'general') {
      showToast('You cannot leave the #General room.', 'error');
      return;
    }
    const leavingRoomId = state.currentChat.id;
    const leavingRoomName = state.currentChat.name;

    els.roomActionsMenu.classList.add('hidden');
    els.membersPanel.classList.add('hidden');

    // Immediately remove from sidebar & switch to General
    state.rooms = state.rooms.filter(r => r.roomId !== leavingRoomId && r.roomName.toLowerCase() !== leavingRoomName.toLowerCase());
    renderRooms();
    const generalRoom = state.rooms.find(r => r.roomName.toLowerCase() === 'general' || r.roomId === 1);
    if (generalRoom) {
      openRoom(generalRoom);
    }

    try {
      await API.rooms.leave(leavingRoomId);
    } catch {
      if (ws) ws.send(`/leave ${leavingRoomName}`);
    }
    showToast(`You left #${leavingRoomName}`, 'info');
    loadRooms();
  }

  if (els.leaveMenuItem) {
    els.leaveMenuItem.addEventListener('click', leaveCurrentRoom);
  }
  if (els.leaveRoomPanelBtn) {
    els.leaveRoomPanelBtn.addEventListener('click', leaveCurrentRoom);
  }

  els.confirmKickBtn.addEventListener('click', () => {
    const rawUsername = els.kickUsername.value.trim();
    const username = rawUsername.replace(/^@/, '').trim();
    if (!username || !state.currentChat) {
      els.kickError.textContent = 'Please enter a username.';
      els.kickError.classList.remove('hidden');
      return;
    }
    if (ws) {
      ws.send(`/kick ${state.currentChat.name} ${username}`);
    }
    closeKickModal();
  });

  /* User Search with Debounce */
  const debouncedSearch = debounce(async (q) => {
    if (!q || q.length < 1) {
      els.searchDropdown.classList.add('hidden');
      return;
    }
    try {
      const results = await API.users.search(q);
      renderSearchDropdown(results || []);
    } catch { /* ignore */ }
  }, 250);

  els.userSearch.addEventListener('input', (e) => {
    debouncedSearch(e.target.value.trim());
  });

  els.userSearch.addEventListener('focus', (e) => {
    if (e.target.value.trim()) debouncedSearch(e.target.value.trim());
  });

  document.addEventListener('click', (e) => {
    if (!document.getElementById('search-wrap').contains(e.target)) {
      els.searchDropdown.classList.add('hidden');
    }
  });

  function renderSearchDropdown(users) {
    els.searchDropdown.innerHTML = '';
    if (users.length === 0) {
      const noResult = el('div', 'search-no-result', { textContent: 'No users found' });
      els.searchDropdown.appendChild(noResult);
    } else {
      users.forEach(u => {
        if (u.username === currentUser.username) return;
        const item = el('div', 'search-result');
        const dot = el('span', `online-dot ${u.online ? 'online' : 'offline'}`);
        const name = el('span', 'search-result-name', { textContent: u.username });
        item.appendChild(dot);
        item.appendChild(name);
        item.addEventListener('click', () => {
          els.searchDropdown.classList.add('hidden');
          els.userSearch.value = '';
          startPMByUsername(u.username);
        });
        els.searchDropdown.appendChild(item);
      });
    }
    els.searchDropdown.classList.remove('hidden');
  }

  /* Emoji Picker */
  const EMOJIS = [
    '😀','😂','😊','😍','😎','🤔','😢','😡','🎉','❤️',
    '👍','👎','🙌','🔥','💯','✨','🚀','🎊','😴','🤗',
    '😮','😱','🤣','😇','🥳','🥺','🤝','💪','🙏','👀',
    '⭐','💫','🎵','🎶','🌈','☀️','🌙','💎','🏆','🍕',
    '🎂','🍻','☕','🌺','🌸','🦋','🐱','🐶','🦄','💻'
  ];

  EMOJIS.forEach(emoji => {
    const btn = el('button', 'emoji-item', { textContent: emoji, type: 'button' });
    btn.addEventListener('click', () => {
      insertAtCursor(els.messageInput, emoji);
      autoResize(els.messageInput);
      els.emojiPicker.classList.add('hidden');
      els.messageInput.focus();
    });
    els.emojiPicker.appendChild(btn);
  });

  els.emojiBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    els.emojiPicker.classList.toggle('hidden');
  });

  document.addEventListener('click', (e) => {
    if (!els.emojiBtn.contains(e.target) && !els.emojiPicker.contains(e.target)) {
      els.emojiPicker.classList.add('hidden');
    }
  });

  function insertAtCursor(textarea, text) {
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const before = textarea.value.substring(0, start);
    const after  = textarea.value.substring(end);
    textarea.value = before + text + after;
    textarea.selectionStart = textarea.selectionEnd = start + text.length;
    textarea.dispatchEvent(new Event('input'));
  }

  /* Escape to close modals/popovers */
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      els.newRoomOverlay.classList.add('hidden');
      if (els.addMembersOverlay) els.addMembersOverlay.classList.add('hidden');
      els.kickOverlay.classList.add('hidden');
      els.membersPanel.classList.add('hidden');
      els.emojiPicker.classList.add('hidden');
      els.roomActionsMenu.classList.add('hidden');
    }
  });

  /* Periodic background refresh */
  setInterval(() => {
    document.querySelectorAll('.msg-timestamp').forEach(ts => {
      const full = ts.getAttribute('data-full');
      if (full) {
        const d = new Date(full);
        if (!isNaN(d.getTime())) {
          ts.textContent = relativeTime(d.toISOString());
        }
      }
    });
  }, 30000);

  setInterval(() => {
    if (ws && ws.isOpen) ws.send('/online');
  }, 30000);

  /* ==========================================================================
     BOOT SEQUENCE
     ========================================================================== */
  await Promise.all([loadRooms(), loadDMs(), loadOnlineUsers()]);
  initWebSocket();

  const generalRoom = state.rooms.find(r => r.roomName.toLowerCase() === 'general' || r.roomId === 1);
  if (generalRoom) {
    openRoom(generalRoom);
  }
})();
