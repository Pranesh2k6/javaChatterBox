/**
 * api.js — All REST API calls for ChatterBox
 * Uses fetch with credentials: 'include' so session cookies are sent.
 */

const API = {
  BASE: '',  // same origin

  /** Generic fetch helpers */
  async _fetch(method, path, body) {
    const opts = {
      method,
      credentials: 'include',
      headers: {},
    };
    if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    const res = await fetch(this.BASE + path, opts);
    // For 204 No Content, return null
    if (res.status === 204) return null;
    // Try to parse JSON
    let data;
    try { data = await res.json(); } catch { data = null; }
    if (!res.ok) {
      const msg = (data && (data.message || data.error)) || `HTTP ${res.status}`;
      throw new Error(msg);
    }
    return data;
  },

  async get(path)         { return this._fetch('GET',    path); },
  async post(path, body)  { return this._fetch('POST',   path, body); },
  async put(path, body)   { return this._fetch('PUT',    path, body); },
  async del(path)         { return this._fetch('DELETE', path); },

  /** Auth endpoints */
  auth: {
    me()                          { return API.get('/api/auth/me'); },
    login(username, password)     { return API.post('/api/auth/login',    { username, password }); },
    register(username, password)  { return API.post('/api/auth/register', { username, password }); },
    logout()                      { return API.post('/api/auth/logout',   {}); },
  },

  /** Room endpoints */
  rooms: {
    list()                              { return API.get('/api/rooms'); },
    create(roomName, description)       { return API.post('/api/rooms', { roomName, description }); },
    members(roomId)                     { return API.get(`/api/rooms/${roomId}/members`); },
    history(roomId, limit = 50)         { return API.get(`/api/rooms/${roomId}/history?limit=${limit}`); },
    older(roomId, before, limit = 20)   { return API.get(`/api/rooms/${roomId}/history/older?before=${before}&limit=${limit}`); },
  },

  /** Private message endpoints */
  pm: {
    start(targetUsername)             { return API.post('/api/pm/start', { targetUsername }); },
    list()                            { return API.get('/api/pm/list'); },
    history(pmId, limit = 50)         { return API.get(`/api/pm/${pmId}/history?limit=${limit}`); },
    older(pmId, before, limit = 20)   { return API.get(`/api/pm/${pmId}/history/older?before=${before}&limit=${limit}`); },
  },

  /** User endpoints */
  users: {
    search(q)      { return API.get(`/api/users/search?q=${encodeURIComponent(q)}`); },
    online()       { return API.get('/api/users/online'); },
    profile(id)    { return API.get(`/api/users/${id}`); },
  },

  /** Message endpoints */
  messages: {
    edit(id, content)  { return API.put(`/api/messages/${id}`, { content }); },
    delete(id)         { return API.del(`/api/messages/${id}`); },
  },
};
