/**
 * login.js — Authentication logic for Chatterbox
 */
/* global API */

(async function () {
  'use strict';

  /* --- Check if already authenticated --- */
  try {
    const me = await API.auth.me();
    if (me && me.userId) {
      window.location.href = '/chat.html';
      return;
    }
  } catch {
    // Not logged in, stay on auth page
  }

  /* --- Tab switching --- */
  const tabLogin    = document.getElementById('tab-login');
  const tabRegister = document.getElementById('tab-register');
  const tabSlider   = document.getElementById('tab-slider');
  const formLogin   = document.getElementById('form-login');
  const formReg     = document.getElementById('form-register');

  function switchTab(tab) {
    if (tab === 'login') {
      tabLogin.classList.add('active');
      tabRegister.classList.remove('active');
      tabSlider.classList.remove('right');
      formLogin.classList.add('active');
      formReg.classList.remove('active');
      hideError('reg-error');
      document.getElementById('reg-success').classList.add('hidden');
      document.getElementById('login-username').focus();
    } else {
      tabRegister.classList.add('active');
      tabLogin.classList.remove('active');
      tabSlider.classList.add('right');
      formReg.classList.add('active');
      formLogin.classList.remove('active');
      hideError('login-error');
      document.getElementById('reg-username').focus();
    }
  }

  tabLogin.addEventListener('click', () => switchTab('login'));
  tabRegister.addEventListener('click', () => switchTab('register'));

  /* --- Password visibility toggles --- */
  function setupToggle(inputId, toggleId) {
    const input  = document.getElementById(inputId);
    const toggle = document.getElementById(toggleId);
    if (!input || !toggle) return;
    toggle.addEventListener('click', () => {
      const show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      toggle.querySelector('.eye-open').classList.toggle('hidden', show);
      toggle.querySelector('.eye-closed').classList.toggle('hidden', !show);
    });
  }
  setupToggle('login-password', 'login-toggle');
  setupToggle('reg-password',   'reg-toggle');

  /* --- Helpers: Feedback --- */
  function showError(id, msg) {
    const el = document.getElementById(id);
    el.textContent = msg;
    el.classList.remove('hidden');
  }
  function hideError(id) {
    const el = document.getElementById(id);
    el.classList.add('hidden');
  }
  function showSuccess(id, msg) {
    const el = document.getElementById(id);
    el.textContent = msg;
    el.classList.remove('hidden');
  }

  /* --- Helpers: Button Loading --- */
  function setLoading(btnId, loading) {
    const btn = document.getElementById(btnId);
    btn.disabled = loading;
    btn.querySelector('.btn-text').classList.toggle('hidden', loading);
    btn.querySelector('.btn-spinner').classList.toggle('hidden', !loading);
  }

  /* --- Login Submission --- */
  formLogin.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideError('login-error');

    const username = document.getElementById('login-username').value.trim();
    const password = document.getElementById('login-password').value;

    if (!username) {
      showError('login-error', 'Please enter your username.');
      return;
    }
    if (!password) {
      showError('login-error', 'Please enter your password.');
      return;
    }

    setLoading('login-btn', true);
    try {
      await API.auth.login(username, password);
      window.location.href = '/chat.html';
    } catch (err) {
      showError('login-error', err.message || 'Login failed. Check your credentials.');
      setLoading('login-btn', false);
    }
  });

  /* --- Register Submission --- */
  formReg.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideError('reg-error');
    document.getElementById('reg-success').classList.add('hidden');

    const username = document.getElementById('reg-username').value.trim();
    const password = document.getElementById('reg-password').value;

    if (!username) {
      showError('reg-error', 'Please choose a username.');
      return;
    }
    if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) {
      showError('reg-error', 'Username must be 3-20 characters (letters, numbers, underscores).');
      return;
    }
    if (!password || password.length < 4) {
      showError('reg-error', 'Password must be at least 4 characters long.');
      return;
    }

    setLoading('reg-btn', true);
    try {
      await API.auth.register(username, password);
      showSuccess('reg-success', 'Account created! Signing you in...');
      // Auto login immediately
      await API.auth.login(username, password);
      window.location.href = '/chat.html';
    } catch (err) {
      showError('reg-error', err.message || 'Registration failed. Try a different username.');
      setLoading('reg-btn', false);
    }
  });

  document.getElementById('login-username').focus();
})();
