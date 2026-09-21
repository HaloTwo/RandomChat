'use strict';

const $ = id => document.getElementById(id);
let reviewerToken = '';
let selectedReportId = null;
let selectedPhotoId = null;
let selectedPhotoKind = null;
let photoObjectUrl = null;
let selectedVideoId = null;
let videoObjectUrl = null;
let adminRoomId = null;
let roomOffset = 0;
let messageOffset = 0;

// 인증된 관리자만 방 목록을 읽고, 선택한 방의 메시지를 200개씩 확인한다.
async function loadRooms(reset = true) {
  if (reset) roomOffset = 0;
  const auth = reviewerToken;
  const data = await reviewerApi(`/api/admin/rooms?offset=${roomOffset}`);
  if (auth !== reviewerToken) return;
  const list = $('admin-rooms');
  if (reset) list.replaceChildren();
  for (const room of data.rooms) {
    const button = document.createElement('button'); button.className = 'review-item';
    button.textContent = `${room.userA} ↔ ${room.userB} · ${room.status} · ${formatDate(room.createdAt)}`;
    button.addEventListener('click', () => {
      adminRoomId = room.id; messageOffset = 0;
      $('admin-room-title').textContent = `${room.userA} ↔ ${room.userB}`;
      $('admin-messages').replaceChildren();
      loadMessages().catch(error => showError(error.message));
    });
    list.append(button);
  }
  if (reset && !data.rooms.length) list.textContent = '저장된 대화가 없습니다.';
  roomOffset += data.rooms.length;
  $('rooms-more').disabled = data.rooms.length < 50;
}
async function loadMessages() {
  if (!adminRoomId) return;
  const selected = adminRoomId, auth = reviewerToken;
  const data = await reviewerApi(`/api/admin/rooms/${selected}?offset=${messageOffset}`);
  if (selected !== adminRoomId || auth !== reviewerToken) return;
  for (const message of data.messages) {
    const line = document.createElement('p'); line.className = 'review-reason';
    line.textContent = `${message.sender} · ${formatDate(message.createdAt)}\n${message.body}`;
    $('admin-messages').append(line);
  }
  if (!messageOffset && !data.messages.length) $('admin-messages').textContent = '저장된 메시지가 없습니다.';
  messageOffset += data.messages.length;
  $('messages-more').classList.toggle('hidden', data.messages.length < 200);
}

// 담당자 토큰은 이 탭의 메모리에만 보관하고 모든 요청에 명시적으로 전달한다.
async function reviewerApi(path, method = 'GET', body) {
  const response = await fetch(path, {
    method,
    headers: { Authorization: `Bearer ${reviewerToken}`, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined
  });
  const data = await response.json();
  if (!response.ok) {
    if (response.status === 401) clearReviewer();
    throw new Error(data.error || `요청 실패 (${response.status})`);
  }
  return data;
}

function showError(message) {
  $('review-flash').textContent = message;
  $('review-flash').classList.remove('hidden');
}

function clearError() { $('review-flash').classList.add('hidden'); }

function clearReviewer() {
  adminRoomId = null;
  $('admin-rooms').replaceChildren();
  $('admin-messages').replaceChildren();
  $('admin-room-title').textContent = '대화방을 선택하세요';
  clearPhotoPreview();
  clearVideoPreview();
  reviewerToken = '';
  selectedReportId = null;
  selectedPhotoId = null;
  selectedPhotoKind = null;
  $('review-token').value = '';
  $('review-login').classList.remove('hidden');
  $('review-workspace').classList.add('hidden');
  $('review-detail').classList.add('hidden');
  $('review-empty').classList.remove('hidden');
  $('review-list').replaceChildren();
  $('photo-list').replaceChildren();
  $('profile-photo-list').replaceChildren();
  $('video-list').replaceChildren();
  $('photo-detail').classList.add('hidden');
  $('photo-empty').classList.remove('hidden');
  $('profile-photo-detail').classList.add('hidden');
  $('profile-photo-empty').classList.remove('hidden');
  $('video-detail').classList.add('hidden');
  $('video-empty').classList.remove('hidden');
}

function clearPhotoPreview() {
  $('photo-preview').removeAttribute('src');
  $('profile-photo-preview').removeAttribute('src');
  if (photoObjectUrl) URL.revokeObjectURL(photoObjectUrl);
  photoObjectUrl = null;
}

function clearVideoPreview() {
  const video = $('video-preview');
  video.pause();
  video.removeAttribute('src');
  video.load();
  if (videoObjectUrl) URL.revokeObjectURL(videoObjectUrl);
  videoObjectUrl = null;
  selectedVideoId = null;
}

function formatDate(value) { return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)); }

// 목록에는 접수 시각과 처리 상태만 보여 주고, 사유는 선택 시 별도 열람한다.
async function loadReports() {
  const { cases } = await reviewerApi('/api/admin/reports');
  const list = $('review-list');
  list.replaceChildren();
  if (!cases.length) { const empty = document.createElement('p'); empty.className = 'review-empty'; empty.textContent = '배정된 신고가 없습니다.'; list.append(empty); return; }
  for (const item of cases) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `review-item${item.id === selectedReportId ? ' selected' : ''}`;
    const top = document.createElement('span'); top.className = 'review-item-top';
    const status = document.createElement('b'); status.textContent = item.status === 'closed' ? '처리 완료' : '검토 대기';
    const time = document.createElement('time'); time.textContent = formatDate(item.createdAt);
    top.append(status, time);
    const id = document.createElement('small'); id.textContent = item.id;
    button.append(top, id);
    button.addEventListener('click', () => openReport(item.id).catch(error => showError(error.message)));
    list.append(button);
  }
}

// 사건 열람은 서버에서 감사 기록을 남긴 뒤 응답하므로 명시적인 선택에서만 호출한다.
async function openReport(id) {
  clearError();
  const item = await reviewerApi(`/api/admin/reports/${encodeURIComponent(id)}`);
  selectedReportId = id;
  $('review-status').textContent = item.status === 'closed' ? '처리 완료' : '검토 대기';
  $('review-created').textContent = formatDate(item.createdAt);
  $('review-reason').textContent = item.reason;
  $('review-resolution').textContent = item.resolution || '';
  $('review-closed').classList.toggle('hidden', item.status !== 'closed');
  $('review-close-form').classList.toggle('hidden', item.status === 'closed');
  $('review-close-reason').value = '';
  $('review-empty').classList.add('hidden');
  $('review-detail').classList.remove('hidden');
  await loadReports();
}

// 사진 바이트는 배정된 담당자만 요청하고 화면을 닫을 때 객체 URL을 폐기한다.
async function loadPhotos(kind) {
  const prefix = kind === 'profile-photos' ? 'profile-photo' : 'photo';
  const { photos } = await reviewerApi(`/api/admin/${kind}`);
  const list = $(`${prefix}-list`);
  list.replaceChildren();
  if (!photos.length) { const empty = document.createElement('p'); empty.className = 'review-empty'; empty.textContent = '배정된 사진이 없습니다.'; list.append(empty); return; }
  for (const item of photos) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `review-item${item.id === selectedPhotoId && kind === selectedPhotoKind ? ' selected' : ''}`;
    const top = document.createElement('span'); top.className = 'review-item-top';
    const status = document.createElement('b'); status.textContent = item.status === 'approved' ? '승인 완료' : '검토 대기';
    const time = document.createElement('time'); time.textContent = formatDate(item.createdAt);
    top.append(status, time);
    const detail = document.createElement('small'); detail.textContent = `${item.width}×${item.height} · ${item.id}`;
    button.append(top, detail);
    button.disabled = item.status !== 'pending';
    button.addEventListener('click', () => openPhoto(item, kind).catch(error => showError(error.message)));
    list.append(button);
  }
}

async function openPhoto(item, kind) {
  clearError();
  clearPhotoPreview();
  selectedPhotoId = null;
  selectedPhotoKind = null;
  for (const prefix of ['photo','profile-photo']) {
    $(`${prefix}-detail`).classList.add('hidden');
    $(`${prefix}-empty`).classList.remove('hidden');
  }
  const activeToken = reviewerToken;
  const response = await fetch(`/api/admin/${kind}/${encodeURIComponent(item.id)}/image`, { headers: { Authorization: `Bearer ${activeToken}` }, cache: 'no-store' });
  if (!response.ok) {
    const data = await response.json();
    if (response.status === 401) clearReviewer();
    throw new Error(data.error || `요청 실패 (${response.status})`);
  }
  const bytes = await response.blob();
  if (activeToken !== reviewerToken) return;
  const prefix = kind === 'profile-photos' ? 'profile-photo' : 'photo';
  photoObjectUrl = URL.createObjectURL(bytes);
  selectedPhotoId = item.id;
  selectedPhotoKind = kind;
  $(`${prefix}-preview`).src = photoObjectUrl;
  $(`${prefix}-meta`).textContent = `${formatDate(item.createdAt)} · ${item.width}×${item.height}`;
  $(`${prefix}-empty`).classList.add('hidden');
  $(`${prefix}-detail`).classList.remove('hidden');
  await loadPhotos(kind);
}

async function decidePhoto(decision, kind) {
  if (!selectedPhotoId || selectedPhotoKind !== kind) return;
  const id = selectedPhotoId;
  await reviewerApi(`/api/admin/${kind}/${encodeURIComponent(id)}/decision`, 'POST', { decision });
  clearPhotoPreview();
  selectedPhotoId = null;
  selectedPhotoKind = null;
  const prefix = kind === 'profile-photos' ? 'profile-photo' : 'photo';
  $(`${prefix}-detail`).classList.add('hidden');
  $(`${prefix}-empty`).classList.remove('hidden');
  await loadPhotos(kind);
}

// 영상도 명시적 열람 후에만 결정할 수 있고, 선택을 바꾸면 재생·객체 URL을 정리한다.
async function loadVideos() {
  const { videos } = await reviewerApi('/api/admin/videos');
  const list = $('video-list');
  list.replaceChildren();
  if (!videos.length) { const empty = document.createElement('p'); empty.className = 'review-empty'; empty.textContent = '배정된 영상이 없습니다.'; list.append(empty); return; }
  for (const item of videos) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `review-item${item.id === selectedVideoId ? ' selected' : ''}`;
    button.textContent = `${item.status === 'approved' ? '승인 완료' : '검토 대기'} · ${item.duration.toFixed(1)}초 · ${item.width}×${item.height} · ${item.id}`;
    button.disabled = item.status !== 'pending';
    button.addEventListener('click', () => openVideo(item).catch(error => showError(error.message)));
    list.append(button);
  }
}

async function openVideo(item) {
  clearError();
  clearVideoPreview();
  $('video-detail').classList.add('hidden');
  $('video-empty').classList.remove('hidden');
  const activeToken = reviewerToken;
  const response = await fetch(`/api/admin/videos/${encodeURIComponent(item.id)}/file`, { headers: { Authorization: `Bearer ${activeToken}` }, cache: 'no-store' });
  if (!response.ok) {
    const data = await response.json();
    if (response.status === 401) clearReviewer();
    throw new Error(data.error || `요청 실패 (${response.status})`);
  }
  const bytes = await response.blob();
  if (activeToken !== reviewerToken) return;
  videoObjectUrl = URL.createObjectURL(bytes);
  selectedVideoId = item.id;
  $('video-preview').src = videoObjectUrl;
  $('video-meta').textContent = `${formatDate(item.createdAt)} · ${item.duration.toFixed(1)}초 · ${item.width}×${item.height}`;
  $('video-empty').classList.add('hidden');
  $('video-detail').classList.remove('hidden');
  await loadVideos();
}

async function decideVideo(decision) {
  if (!selectedVideoId) return;
  await reviewerApi(`/api/admin/videos/${encodeURIComponent(selectedVideoId)}/decision`, 'POST', { decision });
  clearVideoPreview();
  $('video-detail').classList.add('hidden');
  $('video-empty').classList.remove('hidden');
  await loadVideos();
}

$('review-login-form').addEventListener('submit', async event => {
  event.preventDefault();
  clearError();
  reviewerToken = $('review-token').value.trim();
  try {
    await loadReports();
    await loadRooms();
    await loadPhotos('photos');
    await loadPhotos('profile-photos');
    await loadVideos();
    $('review-token').value = '';
    $('review-login').classList.add('hidden');
    $('review-workspace').classList.remove('hidden');
  } catch (error) { showError(error.message); }
});
$('review-refresh').addEventListener('click', () => Promise.all([loadReports(), loadPhotos('photos'), loadPhotos('profile-photos'), loadVideos()]).catch(error => showError(error.message)));
$('review-logout').addEventListener('click', () => { clearReviewer(); clearError(); });
$('rooms-reload').addEventListener('click', () => loadRooms().catch(error => showError(error.message)));
$('rooms-more').addEventListener('click', () => loadRooms(false).catch(error => showError(error.message)));
$('messages-more').addEventListener('click', () => loadMessages().catch(error => showError(error.message)));
for (const [prefix, kind] of [['photo','photos'], ['profile-photo','profile-photos']]) {
  $(`${prefix}-approve`).addEventListener('click', () => decidePhoto('approve', kind).catch(error => showError(error.message)));
  $(`${prefix}-reject`).addEventListener('click', () => decidePhoto('reject', kind).catch(error => showError(error.message)));
}
$('video-approve').addEventListener('click', () => decideVideo('approve').catch(error => showError(error.message)));
$('video-reject').addEventListener('click', () => decideVideo('reject').catch(error => showError(error.message)));
$('review-close-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (!selectedReportId) return;
  const resolution = $('review-close-reason').value.trim();
  if (!resolution) return;
  try {
    await reviewerApi(`/api/admin/reports/${encodeURIComponent(selectedReportId)}/close`, 'POST', { resolution });
    await openReport(selectedReportId);
  } catch (error) { showError(error.message); }
});
