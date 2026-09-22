'use strict';

const $ = id => document.getElementById(id);
const sessionKey = 'moment.local.token';
const pendingKey = 'moment.local.pending-message';
let token = sessionStorage.getItem(sessionKey);
let pendingSend = null;
try { pendingSend = JSON.parse(sessionStorage.getItem(pendingKey) || 'null'); } catch { sessionStorage.removeItem(pendingKey); }
let state = null;
let selectedRoom = null;
let roomDetail = null;
let flashTimer;
let mediaRoomId = null;
let mediaGeneration = 0;
let mediaUrls = [];
let mediaLoading = false;
let feed = [];
let stories = [];
let online = { count: 0, users: [] };
let inbox = [];
let activePostId = null;
let activeStory = null;
let socialUrls = [];
let socialSignature = '';
let socialGeneration = 0;
let refreshing = false;
const pendingPosts = {};

// LAN HTTP에서도 사용 가능한 난수로 UUID v4를 만든다.
function messageId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}

// 방을 바꾸거나 로그아웃하면 인증된 파일의 임시 URL을 폐기한다.
function clearMedia(roomId = null) {
  mediaGeneration++;
  for (const url of mediaUrls) URL.revokeObjectURL(url);
  mediaUrls = [];
  mediaLoading = false;
  mediaRoomId = roomId;
  $('media-list').replaceChildren();
  $('media-status').textContent = '운영자 승인 후 여기에 표시됩니다.';
  $('load-media').disabled = false;
}

function clearSocialMedia() {
  socialGeneration++;
  socialSignature = '';
  for (const url of socialUrls) URL.revokeObjectURL(url);
  socialUrls = [];
}

// API 오류를 화면에 그대로 보여 주되, 인증 토큰은 서버로만 전달한다.
async function api(path, method = 'GET', body) {
  const response = await fetch(path, {
    method,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || `요청 실패 (${response.status})`);
  return data;
}

function flash(message) {
  $('flash').textContent = message;
  $('flash').classList.remove('hidden');
  clearTimeout(flashTimer);
  flashTimer = setTimeout(() => $('flash').classList.add('hidden'), 5000);
}

function formatTime(value) { return new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit' }).format(new Date(value)); }

async function uploadPhoto(path, file) {
  if (!file) return;
  if (!['image/jpeg', 'image/png'].includes(file.type)) throw new Error('JPEG 또는 PNG 사진만 올릴 수 있습니다.');
  const response = await fetch(path, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': file.type }, body: file });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || `사진 업로드 실패 (${response.status})`);
}

async function socialImage(path, alt) {
  const generation = socialGeneration;
  const response = await fetch(path, { headers: { Authorization: `Bearer ${token}` }, cache: 'no-store' });
  if (!response.ok) throw new Error('사진을 불러오지 못했습니다.');
  const blob = await response.blob();
  if (generation !== socialGeneration) return null;
  const url = URL.createObjectURL(blob);
  socialUrls.push(url);
  const image = document.createElement('img');
  image.src = url;
  image.alt = alt;
  return image;
}

async function refreshSocial() {
  if (!token) return;
  const [feedResult, storyResult, inboxResult] = await Promise.all([api('/api/feed'), api('/api/stories'), api('/api/inbox')]);
  feed = feedResult.posts;
  stories = storyResult.stories;
  inbox = inboxResult.notes;
}

async function refresh() {
  if (!token || refreshing) return;
  refreshing = true;
  try {
    state = await api('/api/state');
    await refreshSocial();
    const active = state.rooms.find(r => r.status === 'random' || r.status === 'connected');
    if (active && (!selectedRoom || roomDetail?.status === 'ended')) selectedRoom = active.id;
    if (selectedRoom) roomDetail = await api(`/api/rooms/${selectedRoom}`);
    if (pendingSend && roomDetail?.messages.some(message => message.mine && message.clientId === pendingSend.clientId)) {
      if ($('message-input').value.trim() === pendingSend.body) $('message-input').value = '';
      pendingSend = null;
      sessionStorage.removeItem(pendingKey);
    }
    if (pendingSend && pendingSend.roomId === selectedRoom && !$('message-input').value) $('message-input').value = pendingSend.body;
    render();
  } catch (error) {
    if (error.message.includes('로그인')) { token = null; pendingSend = null; clearMedia(); clearSocialMedia(); sessionStorage.removeItem(sessionKey); sessionStorage.removeItem(pendingKey); render(); }
    else flash(error.message);
  } finally { refreshing = false; }
}

function render() {
  const loggedIn = !!token && !!state;
  $('welcome').classList.toggle('hidden', loggedIn);
  $('dashboard').classList.toggle('hidden', !loggedIn);
  if (!loggedIn) return;
  $('profile-name').textContent = state.user.nickname;
  $('profile-intro').textContent = state.user.intro || '소개가 비어 있습니다.';
  $('quota-left').textContent = state.quota.available;
  $('quota-bar').style.width = `${state.quota.available * 50}%`;
  $('quota-detail').textContent = `사용 ${state.quota.used}건 · 요청 대기 ${state.quota.reserved}건 · 기준일 ${state.quota.day} (KST)`;
  const active = state.rooms.some(r => r.status === 'random');
  $('match-btn').disabled = state.waiting || active;
  $('match-btn').textContent = active ? '진행 중인 대화가 있습니다' : '랜덤 대화 찾기 ↗';
  $('cancel-btn').classList.toggle('hidden', !state.waiting);
  $('waiting').classList.toggle('hidden', !state.waiting);
  renderRoomList();
  renderRoom();
  renderSocial();
  renderInbox();
}

function renderSocial() {
  const signature = JSON.stringify([token, feed, stories]);
  if (signature === socialSignature) return;
  clearSocialMedia();
  socialSignature = signature;
  const storyList = $('story-list');
  storyList.replaceChildren();
  if (!stories.length) storyList.append(Object.assign(document.createElement('p'), { className: 'muted', textContent: '아직 올라온 스토리가 없습니다. 첫 이야기를 남겨 보세요.' }));
  for (const item of stories) {
    const card = document.createElement('article'); card.className = 'story-card';
    const name = document.createElement('b'); name.className = `gender-${item.gender || 'unknown'}`; name.textContent = item.anonymousLabel || '익명 사용자';
    const body = document.createElement('p'); body.textContent = item.body;
    const time = document.createElement('small'); time.textContent = `${formatTime(item.createdAt)} · 24시간 후 사라짐`;
    card.append(name, body);
    card.addEventListener('click', () => openStory(item));
    socialImage(`/api/stories/${item.id}/image`, '익명 스토리 사진').then(image => { if (image && card.isConnected) card.insertBefore(image, time); }).catch(error => flash(error.message));
    if (item.mine) { const remove = document.createElement('button'); remove.className = 'text-button danger'; remove.textContent = '삭제'; remove.addEventListener('click', () => act(`/api/stories/${item.id}`, 'DELETE')); card.append(remove); }
    card.append(time); storyList.append(card);
  }
  const feedList = $('feed-list');
  feedList.replaceChildren();
  if (!feed.length) feedList.append(Object.assign(document.createElement('p'), { className: 'muted', textContent: '아직 게시물이 없습니다. 오늘의 첫 글을 남겨 보세요.' }));
  for (const item of feed) {
    const card = document.createElement('article'); card.className = 'post-card';
    const meta = document.createElement('div'); meta.className = 'post-meta';
    const name = document.createElement('b'); name.className = `gender-${item.gender || 'unknown'}`; name.textContent = item.anonymousLabel || '익명 사용자';
    const time = document.createElement('small'); time.textContent = formatTime(item.createdAt); meta.append(name, time);
    const title = document.createElement('h3'); title.textContent = item.title || '익명 이야기';
    const body = document.createElement('p'); body.className = 'post-preview'; body.textContent = item.body;
    const more = document.createElement('button');
    more.type = 'button'; more.className = 'more-button'; more.textContent = '⋯'; more.setAttribute('aria-label', '게시물 메뉴');
    more.addEventListener('click', () => openPostDialog(item.id));
    meta.append(more); card.append(meta, title, body);
    card.addEventListener('click', event => { if (!event.target.closest('button')) openPostDialog(item.id); });
    if (item.hasImage) socialImage(`/api/posts/${item.id}/image`, '익명 게시물 사진').then(image => { if (image && card.isConnected) card.append(image); }).catch(error => flash(error.message));
    if (item.mine) { const remove = document.createElement('button'); remove.className = 'text-button danger'; remove.textContent = '내 게시물 삭제'; remove.addEventListener('click', () => act(`/api/posts/${item.id}`, 'DELETE')); card.append(remove); }
    feedList.append(card);
  }
}

function renderInbox() {
  const list = $('inbox-list');
  list.replaceChildren();
  if (!inbox.length) { list.textContent = '받은 익명 쪽지가 없습니다.'; return; }
  for (const note of inbox) {
    const item = document.createElement('article'); item.className = 'inbox-note';
    item.textContent = note.body;
    const time = document.createElement('small'); time.textContent = formatTime(note.createdAt);
    item.append(time); list.append(item);
  }
}

async function openPostDialog(postId) {
  activePostId = postId;
  $('post-action-body').value = '';
  $('post-comments').textContent = '댓글을 불러오는 중입니다.';
  $('post-dialog').showModal();
  try {
    const result = await api(`/api/posts/${postId}/comments`);
    const list = $('post-comments'); list.replaceChildren();
    if (!result.comments.length) list.textContent = '첫 댓글을 남겨 보세요.';
    for (const comment of result.comments) {
      const row = document.createElement('p');
      row.className = `gender-${comment.gender || 'unknown'}`;
      row.textContent = `${comment.anonymousLabel || '익명 사용자'} · ${comment.body}`;
      list.append(row);
    }
  } catch (error) { $('post-comments').textContent = error.message; }
}

async function openStory(item) {
  activeStory = item;
  $('story-dialog-author').textContent = item.anonymousLabel || '익명 사용자';
  $('story-dialog-body').textContent = item.body;
  $('story-actions').classList.add('hidden');
  $('story-dialog').showModal();
  try {
    const image = await socialImage(`/api/stories/${item.id}/image`, '스토리 사진');
    if (activeStory?.id === item.id && image) $('story-dialog-image').src = image.src;
  } catch (error) { flash(error.message); }
}

function renderRoomList() {
  const list = $('room-list');
  list.replaceChildren();
  if (!state.rooms.length) { list.textContent = '아직 대화가 없습니다.'; list.classList.add('empty'); return; }
  list.classList.remove('empty');
  for (const room of state.rooms) {
    const button = document.createElement('button');
    button.className = 'room-item';
    const left = document.createElement('span');
    const name = document.createElement('b'); name.textContent = room.peer.displayName;
    const sub = document.createElement('small'); sub.textContent = `${room.status === 'connected' ? '이어진 대화' : room.status === 'random' ? '랜덤 대화' : '종료된 대화'} · ${formatTime(room.createdAt)}`;
    left.append(name, sub);
    const arrow = document.createElement('span'); arrow.textContent = '↗';
    button.append(left, arrow);
    button.addEventListener('click', async () => { selectedRoom = room.id; roomDetail = await api(`/api/rooms/${room.id}`); renderRoom(); });
    list.append(button);
  }
}

function renderRoom() {
  const room = roomDetail;
  $('room-panel').classList.toggle('hidden', !room);
  if (!room) { if (mediaRoomId) clearMedia(); return; }
  if (mediaRoomId !== room.id || (room.status === 'ended' && (mediaUrls.length || mediaLoading))) clearMedia(room.id);
  const connected = room.status === 'connected';
  const ended = room.status === 'ended';
  $('media-panel').classList.toggle('hidden', ended);
  $('room-phase').textContent = connected ? '02 / 이어진 대화' : ended ? '종료된 대화' : '01 / 랜덤 대화';
  $('room-peer').textContent = room.peer.displayName;
  $('room-subtitle').textContent = connected ? (room.peer.intro || '상대가 소개를 작성하지 않았습니다.') : ended ? '이 대화는 종료되었습니다. 최근 대화 신고는 가능합니다.' : '서로 수락하기 전까지 소개는 비공개입니다.';
  $('room-badge').textContent = connected ? '연결됨' : ended ? '종료' : '익명 대화';
  $('message-form').classList.toggle('hidden', ended);
  $('leave-btn').classList.toggle('hidden', ended);
  $('block-btn').classList.toggle('hidden', ended);
  $('request-btn').classList.toggle('hidden', connected || ended || !!(room.request && room.request.status === 'pending'));
  const banner = $('request-banner');
  banner.replaceChildren();
  const pending = room.request && room.request.status === 'pending';
  banner.classList.toggle('hidden', !pending);
  if (pending) {
    const p = document.createElement('p');
    p.textContent = room.request.direction === 'received' ? '상대가 계속 대화를 요청했습니다. 수락하면 서로 소개가 공개됩니다. 무료권은 요청한 사람에게만 차감됩니다.' : '상대의 수락을 기다리는 중입니다. 수락될 때만 무료권 1건이 사용됩니다.';
    banner.append(p);
    const actions = room.request.direction === 'received' ? [['수락', 'accept'], ['거절', 'reject']] : [['요청 취소', 'cancel']];
    for (const [label, decision] of actions) {
      const b = document.createElement('button'); b.textContent = label;
      if (decision !== 'accept') b.className = 'reject';
      b.addEventListener('click', () => act(`/api/rooms/${room.id}/request`, 'PATCH', { decision }));
      banner.append(b);
    }
  }
  const messages = $('messages');
  const atBottom = messages.scrollHeight - messages.scrollTop - messages.clientHeight < 80;
  messages.replaceChildren();
  if (!room.messages.length) { const empty = document.createElement('div'); empty.className = 'empty'; empty.textContent = '첫 메시지를 보내 보세요.'; messages.append(empty); }
  for (const item of room.messages) {
    const bubble = document.createElement('div'); bubble.className = `message${item.mine ? ' mine' : ''}`;
    bubble.append(document.createTextNode(item.body));
    const small = document.createElement('small'); small.textContent = formatTime(item.createdAt); bubble.append(small);
    messages.append(bubble);
  }
  if (atBottom) messages.scrollTop = messages.scrollHeight;
}

// 승인된 파일만 인증 헤더로 내려받아 현재 대화방 안에서 보여 준다.
async function loadMedia() {
  const room = roomDetail;
  if (!room || room.status === 'ended' || mediaLoading) return;
  clearMedia(room.id);
  const generation = mediaGeneration;
  mediaLoading = true;
  $('load-media').disabled = true;
  $('media-status').textContent = '사진·영상을 확인하는 중입니다.';
  try {
    const [photoResult, videoResult, profileResult] = await Promise.all([
      api(`/api/rooms/${room.id}/photos`),
      room.status === 'connected' ? api(`/api/rooms/${room.id}/videos`) : { videos: [] },
      room.status === 'connected' ? api(`/api/rooms/${room.id}/peer/profile-photos`) : { photos: [] }
    ]);
    const entries = [
      ...profileResult.photos.map(item => ({ item, label: '상대 프로필 사진', path: `/api/rooms/${room.id}/peer/profile-photos/${item.id}`, kind: 'image' })),
      ...photoResult.photos.map(item => ({ item, label: item.mine ? '내 사진' : '상대 사진', path: `/api/photos/${item.id}`, kind: 'image' })),
      ...videoResult.videos.map(item => ({ item, label: item.mine ? '내 영상' : '상대 영상', path: `/api/videos/${item.id}`, kind: 'video' }))
    ];
    if (generation !== mediaGeneration || roomDetail?.id !== room.id || roomDetail.status === 'ended') return;
    const approved = entries.filter(entry => entry.item.status === undefined || entry.item.status === 'approved');
    const pending = entries.length - approved.length;
    $('media-status').textContent = `${approved.length}개 승인됨${pending ? ` · 내 미승인 파일 ${pending}개` : ''}`;
    if (!entries.length) $('media-status').textContent = '이 방에는 아직 사진·영상이 없습니다.';
    for (const entry of approved) {
      const response = await fetch(entry.path, { headers: { Authorization: `Bearer ${token}` }, cache: 'no-store' });
      if (!response.ok) throw new Error(`미디어를 열 수 없습니다 (${response.status}). 다시 새로고침하세요.`);
      const blob = await response.blob();
      if (generation !== mediaGeneration || roomDetail?.id !== room.id || roomDetail.status === 'ended') return;
      const url = URL.createObjectURL(blob);
      mediaUrls.push(url);
      const figure = document.createElement('figure');
      const media = document.createElement(entry.kind === 'video' ? 'video' : 'img');
      media.src = url;
      if (entry.kind === 'video') { media.controls = true; media.preload = 'metadata'; }
      else media.alt = entry.label;
      const caption = document.createElement('figcaption');
      caption.textContent = `${entry.label} · ${formatTime(entry.item.createdAt)}`;
      figure.append(media, caption);
      $('media-list').append(figure);
    }
  } catch (error) {
    if (generation === mediaGeneration) $('media-status').textContent = error.message;
  } finally {
    if (generation === mediaGeneration) { mediaLoading = false; $('load-media').disabled = false; }
  }
}

async function act(path, method = 'POST', body) {
  try { await api(path, method, body); await refresh(); }
  catch (error) { flash(error.message); }
}

$('join-form').addEventListener('submit', async event => {
  event.preventDefault();
  try {
    const user = await api('/api/dev/users', 'POST', { nickname: $('nickname').value, gender: $('gender').value, intro: $('intro').value, adultAttested: $('adult').checked });
    token = user.token; sessionStorage.setItem(sessionKey, token); await refresh();
  } catch (error) { alert(error.message); }
});
$('match-btn').addEventListener('click', () => $('match-dialog').showModal());
$('match-dialog-cancel').addEventListener('click', () => $('match-dialog').close());
$('match-confirm').addEventListener('click', () => { $('match-dialog').close(); act('/api/queue'); });
$('story-dialog-close').addEventListener('click', () => $('story-dialog').close());
$('story-more').addEventListener('click', () => $('story-actions').classList.toggle('hidden'));
$('story-note').addEventListener('click', async () => {
  const body = prompt('익명 쪽지 내용');
  if (!body?.trim() || !activeStory) return;
  try { await api(`/api/stories/${activeStory.id}/message`, 'POST', { body: body.trim() }); flash('익명 쪽지를 보냈습니다.'); }
  catch (error) { flash(error.message); }
});
$('story-block').addEventListener('click', async () => {
  if (!activeStory || !confirm('이 스토리 작성자를 차단할까요?')) return;
  try { await api(`/api/stories/${activeStory.id}/block`, 'POST'); $('story-dialog').close(); await refresh(); flash('차단했습니다.'); }
  catch (error) { flash(error.message); }
});
$('story-report').addEventListener('click', async () => {
  const reason = prompt('신고 사유');
  if (!reason?.trim() || !activeStory) return;
  try { await api(`/api/stories/${activeStory.id}/report`, 'POST', { reason: reason.trim() }); flash('신고를 접수했습니다.'); }
  catch (error) { flash(error.message); }
});
$('post-dialog-close').addEventListener('click', () => $('post-dialog').close());
$('send-comment').addEventListener('click', async () => {
  const body = $('post-action-body').value.trim(); if (!activePostId || !body) return;
  try { await api(`/api/posts/${activePostId}/comments`, 'POST', { body }); $('post-action-body').value = ''; await openPostDialog(activePostId); flash('익명 댓글을 남겼습니다.'); }
  catch (error) { flash(error.message); }
});
$('send-note').addEventListener('click', async () => {
  const body = $('post-action-body').value.trim(); if (!activePostId || !body) return;
  try { await api(`/api/posts/${activePostId}/message`, 'POST', { body }); $('post-action-body').value = ''; $('post-dialog').close(); flash('익명 쪽지를 보냈습니다.'); }
  catch (error) { flash(error.message); }
});
$('cancel-btn').addEventListener('click', () => act('/api/queue', 'DELETE'));
$('refresh-feed').addEventListener('click', async () => { try { await refreshSocial(); renderSocial(); } catch (error) { flash(error.message); } });
$('post-form').addEventListener('submit', async event => {
  event.preventDefault();
  const title = $('post-title').value.trim(), body = $('post-input').value.trim(), file = $('post-image').files[0];
  if (!title || !body) return;
  const button = $('post-form').querySelector('button'); button.disabled = true;
  try {
    if (file && (!['image/jpeg','image/png'].includes(file.type) || file.size > 5 * 1024 * 1024)) throw new Error('사진은 5MB 이하 JPEG/PNG를 선택하세요.');
    const saved = pendingPosts.posts;
    const created = saved && saved.body === body && saved.title === title && saved.token === token ? saved : await api('/api/posts', 'POST', { title, body });
    pendingPosts.posts = { id: created.id, title, body, token };
    await uploadPhoto(`/api/posts/${created.id}/image`, file);
    delete pendingPosts.posts;
    $('post-title').value = ''; $('post-input').value = ''; $('post-image').value = '';
    await refreshSocial(); renderSocial(); flash('게시물을 올렸습니다.');
  } catch (error) { flash(error.message); }
  finally { button.disabled = false; }
});
$('story-form').addEventListener('submit', async event => {
  event.preventDefault();
  const body = $('story-input').value.trim(), file = $('story-image').files[0];
  if (!body || !file) { flash('스토리에는 사진을 반드시 추가하세요.'); return; }
  const button = $('story-form').querySelector('button'); button.disabled = true;
  try {
    if (file && (!['image/jpeg','image/png'].includes(file.type) || file.size > 5 * 1024 * 1024)) throw new Error('사진은 5MB 이하 JPEG/PNG를 선택하세요.');
    const saved = pendingPosts.stories;
    const created = saved && saved.body === body && saved.token === token ? saved : await api('/api/stories', 'POST', { body });
    pendingPosts.stories = { id: created.id, body, token };
    await uploadPhoto(`/api/stories/${created.id}/image`, file);
    delete pendingPosts.stories;
    $('story-input').value = ''; $('story-image').value = '';
    await refreshSocial(); renderSocial(); flash('24시간 스토리를 올렸습니다.');
  } catch (error) { flash(error.message); }
  finally { button.disabled = false; }
});
$('reset-user').addEventListener('click', () => { clearMedia(); clearSocialMedia(); sessionStorage.removeItem(sessionKey); sessionStorage.removeItem(pendingKey); token = null; state = null; selectedRoom = null; roomDetail = null; pendingSend = null; render(); });
$('delete-user').addEventListener('click', async () => {
  if (!confirm('이 로컬 계정을 삭제할까요? 토큰과 소개, 내가 보낸 메시지가 지워지고 진행 중인 대화가 종료됩니다. 되돌릴 수 없습니다.')) return;
  try {
    await api('/api/me', 'DELETE');
    sessionStorage.removeItem(sessionKey);
    sessionStorage.removeItem(pendingKey);
    clearMedia(); clearSocialMedia(); token = null; state = null; selectedRoom = null; roomDetail = null; pendingSend = null;
    render();
    alert('로컬 계정이 삭제되었습니다.');
  } catch (error) { flash(error.message); }
});
$('message-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (!selectedRoom) return;
  const input = $('message-input');
  const body = input.value.trim();
  if (!body) return;
  if (!pendingSend || pendingSend.roomId !== selectedRoom || pendingSend.body !== body) {
    pendingSend = { roomId: selectedRoom, body, clientId: messageId() };
    sessionStorage.setItem(pendingKey, JSON.stringify(pendingSend));
  }
  const button = $('message-form').querySelector('button');
  button.disabled = true;
  try {
    await api(`/api/rooms/${selectedRoom}/messages`, 'POST', { body, clientId: pendingSend.clientId });
    pendingSend = null;
    sessionStorage.removeItem(pendingKey);
    input.value = '';
    await refresh();
  } catch (error) { flash(`${error.message} 같은 메시지로 다시 전송하면 중복 저장되지 않습니다.`); }
  finally { button.disabled = false; }
});
$('request-btn').addEventListener('click', () => { if (confirm('상대가 수락하면 내 소개가 공개되고 오늘의 무료 연결 1건이 사용됩니다. 요청할까요?')) act(`/api/rooms/${selectedRoom}/request`); });
$('load-media').addEventListener('click', loadMedia);
$('leave-btn').addEventListener('click', () => { if (confirm('이 대화를 끝낼까요?')) act(`/api/rooms/${selectedRoom}/leave`); });
$('block-btn').addEventListener('click', () => { if (confirm('이 상대를 차단하고 대화를 끝낼까요?')) act(`/api/rooms/${selectedRoom}/block`); });
$('report-btn').addEventListener('click', () => $('report-dialog').showModal());
$('report-cancel').addEventListener('click', () => $('report-dialog').close());
$('report-form').addEventListener('submit', async event => { event.preventDefault(); const reason = $('report-reason').value.trim(); if (!reason) return; $('report-dialog').close(); await act(`/api/rooms/${selectedRoom}/report`, 'POST', { reason }); $('report-reason').value = ''; flash('신고가 로컬 DB에 접수되었습니다. 실제 운영자 심사는 구현 전입니다.'); });
$('edit-profile').addEventListener('click', () => { $('edit-nickname').value = state.user.nickname; $('edit-intro').value = state.user.intro; $('profile-dialog').showModal(); });
$('profile-cancel').addEventListener('click', () => $('profile-dialog').close());
$('profile-form').addEventListener('submit', async event => { event.preventDefault(); try { await api('/api/profile', 'PATCH', { nickname: $('edit-nickname').value, intro: $('edit-intro').value }); $('profile-dialog').close(); await refresh(); } catch (error) { flash(error.message); } });
$('theme-toggle').addEventListener('click', () => {
  document.body.classList.toggle('dark');
  const dark = document.body.classList.contains('dark');
  localStorage.setItem('moment.dark', dark ? '1' : '0');
  $('theme-toggle').textContent = dark ? '라이트 모드' : '다크 모드';
});
if (localStorage.getItem('moment.dark') === '1') { document.body.classList.add('dark'); $('theme-toggle').textContent = '라이트 모드'; }

document.querySelectorAll('.nav-item').forEach(button => button.addEventListener('click', () => {
  const selected = button.dataset.tab;
  document.querySelectorAll('.app-tab').forEach(tab => tab.classList.toggle('hidden', tab.id !== `tab-${selected}`));
  document.querySelectorAll('.nav-item').forEach(item => item.classList.toggle('selected', item === button));
}));

render();
refresh();
setInterval(refresh, 2000);
