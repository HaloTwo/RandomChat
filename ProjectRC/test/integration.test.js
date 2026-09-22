'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const crypto = require('node:crypto');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const ffmpeg = require('ffmpeg-static');
const { DatabaseSync } = require('node:sqlite');
const sharp = require('sharp');
const { createApp, createReviewer, assignReport, assignPhoto, assignProfilePhoto, assignVideo, kstDay } = require('../server');
const { cutoffForDays, previewEndedRooms, purgeEndedRooms } = require('../retention');

async function withApp(run, options = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'random-chat-test-'));
  const app = createApp({ ...options, dbPath: path.join(dir, 'test.sqlite') });
  await new Promise(resolve => app.server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${app.server.address().port}`;
  async function call(url, method = 'GET', body, token) {
    const response = await fetch(base + url, { method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined });
    return { status: response.status, data: await response.json() };
  }
  async function user(nickname) {
    const result = await call('/api/dev/users', 'POST', { nickname, gender: 'female', intro: `${nickname}의 비공개 소개`, adultAttested: true });
    assert.equal(result.status, 201);
    return result.data;
  }
  try { await run({ app, call, user, base }); }
  finally { await app.close(); fs.rmSync(dir, { recursive: true, force: true }); }
}

test('공기계 APK 다운로드는 파일 바이트를 그대로 전달하고 없으면 404를 보낸다', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'random-chat-apk-test-'));
  const apkPath = path.join(dir, 'test.apk');
  const bytes = Buffer.from('PK\x03\x04demo apk bytes');
  try {
    await withApp(async ({ base }) => {
      let response = await fetch(`${base}/app.apk`);
      assert.equal(response.status, 404);
      fs.writeFileSync(apkPath, bytes);
      response = await fetch(`${base}/app.apk`);
      assert.equal(response.status, 200);
      assert.equal(response.headers.get('content-type'), 'application/vnd.android.package-archive');
      assert.match(response.headers.get('content-disposition'), /moment-local-debug\.apk/);
      assert.deepEqual(Buffer.from(await response.arrayBuffer()), bytes);
    }, { apkPath });
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});

test('라운지 플래그는 Android가 읽을 수 있는 boolean이다', async () => withApp(async ({ app, call, user }) => {
  const a = await user('작성자'), b = await user('독자');
  const post = (await call('/api/posts', 'POST', { body: '본문' }, a.token)).data;
  const storyCreated = (await call('/api/stories', 'POST', { body: '스토리' }, a.token)).data;
  app.db.prepare('UPDATE posts SET image=? WHERE id=?').run(Buffer.from('fixture'), post.id);
  app.db.prepare('UPDATE stories SET image=? WHERE id=?').run(Buffer.from('fixture'), storyCreated.id);
  const own = (await call('/api/feed', 'GET', null, a.token)).data.posts[0];
  assert.equal(own.mine, true);
  assert.equal(own.hasImage, true);
  const other = (await call('/api/feed', 'GET', null, b.token)).data.posts[0];
  assert.equal(other.mine, false);
  const story = (await call('/api/stories', 'GET', null, a.token)).data.stories[0];
  assert.equal(story.mine, true);
  assert.equal(story.hasImage, true);
  assert.match(story.storyOwnerKey, /^[a-f0-9]{16}$/);
}));

test('같은 작성자의 사진 스토리는 안전한 동일 묶음 키로 내려온다', async () => withApp(async ({ app, call, user }) => {
  const author = await user('여러스토리작성자'), reader = await user('스토리독자');
  const first = await call('/api/stories', 'POST', { body: '첫 번째' }, author.token);
  const second = await call('/api/stories', 'POST', { body: '두 번째' }, author.token);
  app.db.prepare('UPDATE stories SET image = ? WHERE id IN (?, ?)').run(Buffer.from('fixture'), first.data.id, second.data.id);
  const stories = (await call('/api/stories', 'GET', null, reader.token)).data.stories;
  assert.equal(stories.length, 2);
  assert.equal(stories[0].storyOwnerKey, stories[1].storyOwnerKey);
  assert.equal(JSON.stringify(stories).includes('여러스토리작성자'), false);
}));

test('공개 라운지는 익명 번호·성별만 보여 주고 댓글과 쪽지를 전달한다', async () => withApp(async ({ call, user }) => {
  const author = await user('실명노출금지');
  const reader = await user('댓글작성자');
  const post = (await call('/api/posts', 'POST', { body: '익명 글' }, author.token)).data;
  const feed = (await call('/api/feed', 'GET', null, reader.token)).data.posts[0];
  assert.match(feed.anonymousLabel, /^익명 #\d{4}$/);
  assert.equal(feed.gender, 'female');
  assert.equal(JSON.stringify(feed).includes('실명노출금지'), false);
  const comment = await call(`/api/posts/${post.id}/comments`, 'POST', { body: '익명 댓글' }, reader.token);
  assert.equal(comment.status, 201);
  assert.equal((await call(`/api/posts/${post.id}/comments`, 'GET', null, author.token)).data.comments[0].body, '익명 댓글');
  assert.equal((await call(`/api/comments/${comment.data.id}/replies`, 'POST', { body: '댓글 답글' }, author.token)).status, 201);
  assert.equal((await call(`/api/comments/${comment.data.id}/replies`, 'GET', null, reader.token)).data.replies[0].body, '댓글 답글');
  assert.equal((await call(`/api/posts/${post.id}/comments`, 'GET', null, author.token)).data.comments[0].replyCount, 1);
  assert.equal((await call(`/api/posts/${post.id}/message`, 'POST', { body: '익명 쪽지' }, reader.token)).status, 201);
  assert.equal((await call('/api/inbox', 'GET', null, author.token)).data.notes[0].body, '익명 쪽지');
  assert.equal((await call('/api/online', 'GET', null, reader.token)).data.count >= 2, true);
}));

test('스토리는 사진이 있어야 공개되고 상세 열람만 조회 기록으로 남긴다', async () => withApp(async ({ app, call, user }) => {
  const author = await user('스토리작성자'), reader = await user('스토리독자');
  const created = await call('/api/stories', 'POST', { body: '사진 스토리' }, author.token);
  assert.equal((await call('/api/stories', 'GET', null, reader.token)).data.stories.length, 0);
  app.db.prepare('UPDATE stories SET image = ? WHERE id = ?').run(Buffer.from('fixture'), created.data.id);
  assert.equal((await call('/api/stories', 'GET', null, reader.token)).data.stories.length, 1);
  const viewed = await call(`/api/stories/${created.data.id}/view`, 'POST', null, reader.token);
  assert.equal(viewed.data.recorded, true);
  assert.equal(viewed.data.viewCount, 1);
  const viewedAgain = await call(`/api/stories/${created.data.id}/view`, 'POST', null, reader.token);
  assert.equal(viewedAgain.data.viewCount, 2);
  assert.equal(viewedAgain.data.viewerCount, 1);
  const viewers = await call(`/api/stories/${created.data.id}/viewers`, 'GET', null, author.token);
  assert.equal(viewers.data.viewCount, 2);
  assert.equal(viewers.data.viewerCount, 1);
  assert.equal(viewers.data.viewers[0].viewCount, 2);
}));

test('게시물 조회·댓글 수와 대화 읽음·입력 상태를 전달한다', async () => withApp(async ({ call, user }) => {
  const a = await user('작성자'), b = await user('독자');
  const post = (await call('/api/posts', 'POST', { title: '제목', body: '본문' }, a.token)).data;
  assert.equal((await call(`/api/posts/${post.id}/view`, 'POST', null, b.token)).data.viewCount, 1);
  await call(`/api/posts/${post.id}/comments`, 'POST', { body: '댓글' }, b.token);
  const feed = (await call('/api/feed', 'GET', null, a.token)).data.posts[0];
  assert.equal(feed.viewCount, 1);
  assert.equal(feed.commentCount, 1);
  const activity = (await call('/api/me/activity', 'GET', null, b.token)).data;
  assert.equal(activity.comments[0].postId, post.id);
  assert.equal(activity.posts.length, 0);
  await call('/api/queue', 'POST', null, a.token);
  const roomId = (await call('/api/queue', 'POST', null, b.token)).data.roomId;
  await call(`/api/rooms/${roomId}/typing`, 'POST', { typing: true }, a.token);
  assert.equal((await call(`/api/rooms/${roomId}`, 'GET', null, b.token)).data.peerTyping, true);
  await call(`/api/rooms/${roomId}/messages`, 'POST', { body: '읽음 확인', clientId: crypto.randomUUID() }, a.token);
  await call(`/api/rooms/${roomId}`, 'GET', null, b.token);
  const room = (await call(`/api/rooms/${roomId}`, 'GET', null, a.token)).data;
  assert.equal(room.messages[0].read, true);
}));

test('관리자 대화 조회는 토큰 권한·페이지·열람 기록을 검증한다', async () => withApp(async ({ app, call, user }) => {
  const a = await user('A'), b = await user('B');
  await call('/api/queue', 'POST', null, a.token);
  const roomId = (await call('/api/queue', 'POST', null, b.token)).data.roomId;
  await call(`/api/rooms/${roomId}/messages`, 'POST', { body: '관리자 열람 확인', clientId: crypto.randomUUID() }, a.token);
  const reviewer = createReviewer(app.db, '로컬 관리자');
  assert.equal((await call('/api/admin/rooms')).status, 401);
  assert.equal((await call('/api/admin/rooms', 'GET', null, a.token)).status, 401);
  assert.equal((await call(`/api/admin/rooms/${roomId}`, 'GET', null, b.token)).status, 401);
  const list = (await call('/api/admin/rooms', 'GET', null, reviewer.token)).data;
  assert.equal(list.rooms[0].id, roomId);
  const detail = (await call(`/api/admin/rooms/${roomId}`, 'GET', null, reviewer.token)).data;
  assert.equal(detail.messages[0].body, '관리자 열람 확인');
  assert.equal((await call(`/api/admin/rooms/${roomId}?offset=1`, 'GET', null, reviewer.token)).data.messages.length, 0);
  assert.equal(app.db.prepare('SELECT count(*) AS n FROM conversation_access_audit WHERE reviewer_id=?').get(reviewer.id).n, 2);
  app.db.prepare('UPDATE admin_accounts SET active=0 WHERE id=?').run(reviewer.id);
  assert.equal((await call(`/api/admin/rooms/${roomId}`, 'GET', null, reviewer.token)).status, 401);
}));

test('공기계 설치 안내 페이지는 APK 다운로드와 현재 서버 주소 안내를 포함한다', async () => withApp(async ({ base }) => {
  const response = await fetch(`${base}/install`);
  assert.equal(response.status, 200);
  const html = await response.text();
  assert.match(html, /href="\/app\.apk"/);
  assert.match(html, /location\.origin/);
  assert.match(html, /서버 연결 확인/);
}));

test('로그인한 사용자끼리만 게시물과 24시간 스토리를 보고 내 콘텐츠만 지운다', async () => withApp(async ({ app, call, user }) => {
  const a = await user('피드가'), b = await user('피드나');
  const post = await call('/api/posts', 'POST', { body: '오늘 만든 화면 공유' }, a.token);
  const story = await call('/api/stories', 'POST', { body: '잠깐 남기는 이야기' }, a.token);
  assert.equal(post.status, 201);
  assert.equal(story.status, 201);
  app.db.prepare('UPDATE stories SET image=? WHERE id=?').run(Buffer.from('fixture'), story.data.id);
  assert.equal((await call('/api/feed', 'GET', null, b.token)).data.posts[0].body, '오늘 만든 화면 공유');
  assert.equal((await call('/api/stories', 'GET', null, b.token)).data.stories[0].body, '잠깐 남기는 이야기');
  assert.equal((await call(`/api/posts/${post.data.id}`, 'DELETE', null, b.token)).status, 404);
  app.db.prepare('UPDATE stories SET expires_at = 0 WHERE id = ?').run(story.data.id);
  assert.equal((await call('/api/stories', 'GET', null, b.token)).data.stories.length, 0);
  assert.equal((await call(`/api/posts/${post.data.id}`, 'DELETE', null, a.token)).status, 200);
}));

test('게시물과 스토리 사진은 인증된 로그인 사용자에게만 JPEG로 전달한다', async () => withApp(async ({ app, call, user }) => {
  const a = await user('사진가'), b = await user('사진나');
  const post = await call('/api/posts', 'POST', { body: '사진이 있는 게시물' }, a.token);
  const story = await call('/api/stories', 'POST', { body: '사진이 있는 스토리' }, a.token);
  const png = await sharp({ create: { width: 40, height: 30, channels: 3, background: '#64a987' } }).png().toBuffer();
  const base = `http://127.0.0.1:${app.server.address().port}`;
  async function upload(url, token, body = png, type = 'image/png') {
    const response = await fetch(base + url, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': type }, body });
    return { status: response.status, data: await response.json() };
  }
  assert.equal((await upload(`/api/posts/${post.data.id}/image`, a.token)).status, 200);
  assert.equal((await upload(`/api/stories/${story.data.id}/image`, a.token)).status, 200);
  assert.equal((await upload(`/api/posts/${post.data.id}/image`, b.token)).status, 404);
  const image = await fetch(`${base}/api/posts/${post.data.id}/image`, { headers: { Authorization: `Bearer ${b.token}` } });
  assert.equal(image.status, 200);
  assert.equal(image.headers.get('content-type'), 'image/jpeg');
  assert.deepEqual(Buffer.from(await image.arrayBuffer()).subarray(0, 3), Buffer.from([0xff, 0xd8, 0xff]));
  assert.equal((await fetch(`${base}/api/stories/${story.data.id}/image`)).status, 401);
}));

test('랜덤 매칭, 비공개 소개, 요청 수락, 무료 차감과 메시지', async () => withApp(async ({ call, user }) => {
  const a = await user('가'), b = await user('나');
  assert.equal((await call('/api/queue', 'POST', null, a.token)).data.waiting, true);
  const match = await call('/api/queue', 'POST', null, b.token);
  assert.equal(match.data.waiting, false);
  const roomId = match.data.roomId;
  const before = (await call(`/api/rooms/${roomId}`, 'GET', null, a.token)).data;
  assert.equal(before.peer.displayName, '랜덤 상대');
  assert.equal(before.peer.intro, null);
  assert.equal((await call(`/api/rooms/${roomId}/messages`, 'POST', { body: '안녕하세요', clientId: crypto.randomUUID() }, a.token)).status, 201);
  assert.equal((await call(`/api/rooms/${roomId}`, 'GET', null, b.token)).data.messages[0].body, '안녕하세요');
  assert.equal((await call(`/api/rooms/${roomId}/request`, 'POST', null, a.token)).status, 201);
  assert.equal((await call('/api/me', 'GET', null, a.token)).data.quota.reserved, 1);
  assert.equal((await call(`/api/rooms/${roomId}/request/decision`, 'POST', { decision: 'accept' }, b.token)).status, 200);
  for (let i = 0; i < 100; i++) {
    const repeated = await call(`/api/rooms/${roomId}/request`, 'PATCH', { decision: 'accept' }, b.token);
    assert.equal(repeated.status, 200);
    assert.equal(repeated.data.duplicate, true);
  }
  const after = (await call(`/api/rooms/${roomId}`, 'GET', null, a.token)).data;
  assert.equal(after.status, 'connected');
  assert.equal(after.peer.displayName, '나');
  assert.equal(after.peer.intro, '나의 비공개 소개');
  assert.equal((await call('/api/me', 'GET', null, a.token)).data.quota.used, 1);
  assert.equal((await call('/api/me', 'GET', null, b.token)).data.quota.used, 0);
}));

test('거절과 취소는 무료권을 돌려주고, 수락 두 번 뒤 세 번째 요청은 거부', async () => withApp(async ({ call, user }) => {
  const a = await user('가');
  for (let i = 0; i < 4; i++) {
    const b = await user(`상대${i}`);
    await call('/api/queue', 'POST', null, a.token);
    const match = await call('/api/queue', 'POST', null, b.token);
    const room = match.data.roomId;
    const requested = await call(`/api/rooms/${room}/request`, 'POST', null, a.token);
    assert.equal(requested.status, i === 3 ? 409 : 201);
    if (i === 0) await call(`/api/rooms/${room}/request`, 'PATCH', { decision: 'reject' }, b.token);
    if (i === 1 || i === 2) await call(`/api/rooms/${room}/request`, 'PATCH', { decision: 'accept' }, b.token);
    if (i === 0 || i === 3) await call(`/api/rooms/${room}/leave`, 'POST', null, a.token);
  }
  const quota = (await call('/api/me', 'GET', null, a.token)).data.quota;
  assert.equal(quota.used, 2);
  assert.equal(quota.reserved, 0);
  assert.equal(quota.available, 0);
}));

test('차단한 상대와 다시 매칭되지 않고 종료 후에도 신고 가능', async () => withApp(async ({ call, user }) => {
  const a = await user('가'), b = await user('나');
  await call('/api/queue', 'POST', null, a.token);
  const room = (await call('/api/queue', 'POST', null, b.token)).data.roomId;
  assert.equal((await call(`/api/rooms/${room}/block`, 'POST', null, a.token)).status, 200);
  assert.equal((await call(`/api/rooms/${room}/messages`, 'POST', { body: '재전송', clientId: crypto.randomUUID() }, b.token)).status, 409);
  assert.equal((await call(`/api/rooms/${room}/report`, 'POST', { reason: '테스트 신고' }, a.token)).status, 201);
  await call('/api/queue', 'POST', null, a.token);
  assert.equal((await call('/api/queue', 'POST', null, b.token)).data.waiting, true);
  assert.equal((await call('/api/state', 'GET', null, a.token)).data.waiting, true);
}));

test('인증 없이 다른 대화에 접근할 수 없다', async () => withApp(async ({ call, user }) => {
  const a = await user('가'), b = await user('나'), c = await user('다');
  await call('/api/queue', 'POST', null, a.token);
  const room = (await call('/api/queue', 'POST', null, b.token)).data.roomId;
  assert.equal((await call(`/api/rooms/${room}`, 'GET')).status, 401);
  assert.equal((await call(`/api/rooms/${room}`, 'GET', null, c.token)).status, 404);
  assert.equal((await call(`/api/rooms/${room}/messages`, 'POST', { body: '침입', clientId: crypto.randomUUID() }, c.token)).status, 404);
}));

test('메시지 재전송 100회는 한 건만 저장하고 식별자 충돌을 거부한다', async () => withApp(async ({ app, call, user }) => {
  const a = await user('가'), b = await user('나');
  await call('/api/queue', 'POST', null, a.token);
  const room = (await call('/api/queue', 'POST', null, b.token)).data.roomId;
  const clientId = crypto.randomUUID();
  let firstId;
  for (let i = 0; i < 100; i++) {
    const sent = await call(`/api/rooms/${room}/messages`, 'POST', { body: '같은 메시지', clientId }, a.token);
    assert.equal(sent.status, i === 0 ? 201 : 200);
    firstId ||= sent.data.id;
    assert.equal(sent.data.id, firstId);
  }
  assert.equal(app.db.prepare('SELECT count(*) AS n FROM messages WHERE room_id = ?').get(room).n, 1);
  assert.equal((await call(`/api/rooms/${room}/messages`, 'POST', { body: '다른 메시지', clientId }, a.token)).status, 409);
  assert.equal((await call(`/api/rooms/${room}`, 'GET', null, a.token)).data.messages[0].clientId, clientId);
  await call(`/api/rooms/${room}/leave`, 'POST', null, a.token);
  assert.equal((await call(`/api/rooms/${room}/messages`, 'POST', { body: '같은 메시지', clientId }, a.token)).status, 200);
}));

test('계정 삭제는 토큰·소개·본인 메시지를 없애고 상대 대화를 닫는다', async () => withApp(async ({ app, call, user }) => {
  const a = await user('가'), b = await user('나');
  await call('/api/queue', 'POST', null, a.token);
  const room = (await call('/api/queue', 'POST', null, b.token)).data.roomId;
  await call(`/api/rooms/${room}/messages`, 'POST', { body: '가의 메시지', clientId: crypto.randomUUID() }, a.token);
  await call(`/api/rooms/${room}/messages`, 'POST', { body: '나의 메시지', clientId: crypto.randomUUID() }, b.token);
  await call(`/api/rooms/${room}/request`, 'POST', null, a.token);
  await call(`/api/rooms/${room}/request`, 'PATCH', { decision: 'accept' }, b.token);
  assert.equal((await call(`/api/rooms/${room}`, 'GET', null, b.token)).data.peer.intro, '가의 비공개 소개');
  assert.equal((await call('/api/me', 'DELETE', null, a.token)).status, 200);
  assert.equal((await call('/api/me', 'GET', null, a.token)).status, 401);
  const remaining = (await call(`/api/rooms/${room}`, 'GET', null, b.token)).data;
  assert.equal(remaining.status, 'ended');
  assert.deepEqual(remaining.peer, { displayName: '탈퇴한 사용자', intro: null });
  assert.deepEqual(remaining.messages.map(message => message.body), ['나의 메시지']);
  assert.equal(app.db.prepare('SELECT intro, deleted_at FROM users WHERE id = ?').get(a.id).intro, '');
  assert.equal((await call(`/api/rooms/${room}/messages`, 'POST', { body: '추가', clientId: crypto.randomUUID() }, b.token)).status, 409);
}));

test('신고 내용은 배정 담당자만 열람·처리하며 접근 기록을 남긴다', async () => withApp(async ({ app, call, user }) => {
  const a = await user('가'), b = await user('나');
  await call('/api/queue', 'POST', null, a.token);
  const room = (await call('/api/queue', 'POST', null, b.token)).data.roomId;
  const reported = await call(`/api/rooms/${room}/report`, 'POST', { reason: '검토할 신고 사유' }, a.token);
  assert.equal(reported.status, 201);
  const reportId = reported.data.id;
  const assigned = createReviewer(app.db, '담당자 1');
  const other = createReviewer(app.db, '담당자 2');
  assert.equal((await call('/api/admin/reports', 'GET', null, a.token)).status, 401);
  assert.deepEqual((await call('/api/admin/reports', 'GET', null, assigned.token)).data.cases, []);
  assert.equal((await call(`/api/admin/reports/${reportId}`, 'GET', null, assigned.token)).status, 404);
  assignReport(app.db, reportId, assigned.id);
  const list = await call('/api/admin/reports', 'GET', null, assigned.token);
  assert.equal(list.data.cases.length, 1);
  assert.equal(JSON.stringify(list.data).includes('검토할 신고 사유'), false);
  assert.deepEqual((await call('/api/admin/reports', 'GET', null, other.token)).data.cases, []);
  assert.equal((await call(`/api/admin/reports/${reportId}`, 'GET', null, other.token)).status, 404);
  assert.equal((await call(`/api/admin/reports/${reportId}/close`, 'POST', { resolution: '검토 완료' }, other.token)).status, 404);
  assert.equal((await call(`/api/admin/reports/${reportId}`, 'GET', null, assigned.token)).data.reason, '검토할 신고 사유');
  assert.equal(app.db.prepare("SELECT count(*) AS n FROM admin_access_audit WHERE report_id = ? AND action = 'view'").get(reportId).n, 1);
  assert.equal((await call(`/api/admin/reports/${reportId}/close`, 'POST', { resolution: '검토 완료' }, assigned.token)).status, 200);
  assert.equal((await call(`/api/admin/reports/${reportId}/close`, 'POST', { resolution: '검토 완료' }, assigned.token)).data.duplicate, true);
  assert.equal((await call(`/api/admin/reports/${reportId}/close`, 'POST', { resolution: '다른 처리' }, assigned.token)).status, 409);
  assert.equal(app.db.prepare("SELECT count(*) AS n FROM admin_access_audit WHERE report_id = ? AND action = 'close'").get(reportId).n, 1);
  app.db.prepare('UPDATE admin_accounts SET active = 0 WHERE id = ?').run(assigned.id);
  assert.equal((await call('/api/admin/reports', 'GET', null, assigned.token)).status, 401);
}));

test('신고 검토 화면은 열리며 사건 데이터는 인증 API에서만 읽는다', async () => withApp(async ({ app }) => {
  const base = `http://127.0.0.1:${app.server.address().port}`;
  const page = await fetch(`${base}/review`);
  assert.equal(page.status, 200);
  const html = await page.text();
  assert.match(html, /운영 검토/);
  assert.match(html, /프로필 등록 사진/);
  const script = await fetch(`${base}/review.js`);
  assert.equal(script.status, 200);
  assert.match(await script.text(), /\/api\/admin\/reports/);
  const protectedList = await fetch(`${base}/api/admin/reports`);
  assert.equal(protectedList.status, 401);
}));

test('사진은 실제 정지 이미지만 격리 저장하고 종료·차단 시 삭제한다', async () => withApp(async ({ app, call, user }) => {
  const a = await user('가'), b = await user('나'), outsider = await user('다');
  await call('/api/queue', 'POST', null, a.token);
  const room = (await call('/api/queue', 'POST', null, b.token)).data.roomId;
  const base = `http://127.0.0.1:${app.server.address().port}`;
  async function photo(token, bytes, type = 'image/png') {
    const response = await fetch(`${base}/api/rooms/${room}/photos`, { method: 'POST', headers: { 'Content-Type': type, ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: bytes });
    return { status: response.status, data: await response.json() };
  }
  const png = await sharp({ create: { width: 40, height: 30, channels: 3, background: '#64a987' } }).png().toBuffer();
  assert.equal((await photo(null, png)).status, 401);
  assert.equal((await photo(outsider.token, png)).status, 404);
  assert.equal((await photo(a.token, png, 'image/gif')).status, 415);
  assert.equal((await photo(a.token, png, 'image/jpeg')).status, 400);
  const gif = await sharp({ create: { width: 20, height: 20, channels: 3, background: '#326441' } }).gif().toBuffer();
  assert.equal((await photo(a.token, gif, 'image/png')).status, 400);
  assert.equal((await photo(a.token, Buffer.from('not an image'))).status, 400);
  assert.equal((await photo(a.token, Buffer.alloc(5 * 1024 * 1024 + 1))).status, 413);
  const uploaded = await photo(a.token, png);
  assert.equal(uploaded.status, 201);
  assert.equal(uploaded.data.status, 'pending');
  const stored = app.db.prepare('SELECT status, image, width, height FROM photo_uploads WHERE id = ?').get(uploaded.data.id);
  assert.equal(stored.status, 'pending');
  assert.deepEqual([stored.width, stored.height], [40, 30]);
  assert.deepEqual(Buffer.from(stored.image).subarray(0, 3), Buffer.from([0xff, 0xd8, 0xff]));
  assert.equal((await call(`/api/rooms/${room}/photos`, 'GET', null, a.token)).data.photos.length, 1);
  assert.deepEqual((await call(`/api/rooms/${room}/photos`, 'GET', null, b.token)).data.photos, []);
  assert.equal((await call(`/api/photos/${uploaded.data.id}`, 'DELETE', null, b.token)).status, 404);
  assert.equal((await call(`/api/photos/${uploaded.data.id}`, 'DELETE', null, a.token)).status, 200);
  assert.equal(app.db.prepare('SELECT count(*) AS n FROM photo_uploads').get().n, 0);
  assert.equal((await photo(a.token, png)).status, 201);
  await call(`/api/rooms/${room}/block`, 'POST', null, b.token);
  assert.equal(app.db.prepare('SELECT count(*) AS n FROM photo_uploads').get().n, 0);
  assert.equal((await photo(a.token, png)).status, 409);
  await call('/api/queue', 'POST', null, a.token);
  const nextRoom = (await call('/api/queue', 'POST', null, outsider.token)).data.roomId;
  const nextUpload = await fetch(`${base}/api/rooms/${nextRoom}/photos`, { method: 'POST', headers: { 'Content-Type': 'image/png', Authorization: `Bearer ${a.token}` }, body: png });
  assert.equal(nextUpload.status, 201);
  assert.equal((await call('/api/me', 'DELETE', null, a.token)).status, 200);
  assert.equal(app.db.prepare('SELECT count(*) AS n FROM photo_uploads').get().n, 0);
}));

test('배정 담당자가 열람한 사진만 승인하고 현재 방 참가자에게만 전달한다', async () => withApp(async ({ app, call, user }) => {
  const a = await user('가'), b = await user('나'), outsider = await user('다');
  await call('/api/queue', 'POST', null, a.token);
  const room = (await call('/api/queue', 'POST', null, b.token)).data.roomId;
  const reviewer = createReviewer(app.db, '사진 담당자');
  const other = createReviewer(app.db, '다른 담당자');
  const base = `http://127.0.0.1:${app.server.address().port}`;
  const png = await sharp({ create: { width: 32, height: 24, channels: 3, background: '#e63946' } }).png().toBuffer();
  async function upload() {
    const response = await fetch(`${base}/api/rooms/${room}/photos`, { method: 'POST', headers: { Authorization: `Bearer ${a.token}`, 'Content-Type': 'image/png' }, body: png });
    assert.equal(response.status, 201);
    return (await response.json()).id;
  }
  async function image(url, token) {
    return fetch(base + url, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  }
  const id = await upload();
  assert.deepEqual((await call(`/api/rooms/${room}/photos`, 'GET', null, b.token)).data.photos, []);
  assert.equal((await image(`/api/photos/${id}`, b.token)).status, 404);
  assert.equal((await call('/api/admin/photos', 'GET', null, a.token)).status, 401);
  assert.deepEqual((await call('/api/admin/photos', 'GET', null, reviewer.token)).data.photos, []);
  assignPhoto(app.db, id, reviewer.id);
  assert.equal((await call(`/api/admin/photos/${id}/decision`, 'POST', { decision: 'approve' }, reviewer.token)).status, 409);
  assert.equal((await image(`/api/admin/photos/${id}/image`, other.token)).status, 404);
  const candidate = await image(`/api/admin/photos/${id}/image`, reviewer.token);
  assert.equal(candidate.status, 200);
  assert.equal(candidate.headers.get('cache-control'), 'private, no-store');
  assert.deepEqual(Buffer.from(await candidate.arrayBuffer()).subarray(0, 3), Buffer.from([0xff, 0xd8, 0xff]));
  assert.equal(app.db.prepare("SELECT count(*) AS n FROM photo_access_audit WHERE photo_id = ? AND action = 'view'").get(id).n, 1);
  assert.equal((await call(`/api/admin/photos/${id}/decision`, 'POST', { decision: 'approve' }, other.token)).status, 404);
  assert.equal((await call(`/api/admin/photos/${id}/decision`, 'POST', { decision: 'approve' }, reviewer.token)).data.status, 'approved');
  const visible = (await call(`/api/rooms/${room}/photos`, 'GET', null, b.token)).data.photos;
  assert.equal(visible.length, 1);
  assert.equal(visible[0].mine, 0);
  assert.equal(visible[0].status, 'approved');
  assert.equal((await image(`/api/photos/${id}`, outsider.token)).status, 404);
  assert.equal((await image(`/api/photos/${id}`)).status, 401);
  const approved = await image(`/api/photos/${id}`, b.token);
  assert.equal(approved.status, 200);
  assert.equal(approved.headers.get('cache-control'), 'private, no-store');
  assert.deepEqual(Buffer.from(await approved.arrayBuffer()).subarray(0, 3), Buffer.from([0xff, 0xd8, 0xff]));
  const rejectedId = await upload();
  assignPhoto(app.db, rejectedId, reviewer.id);
  await image(`/api/admin/photos/${rejectedId}/image`, reviewer.token);
  assert.equal((await call(`/api/admin/photos/${rejectedId}/decision`, 'POST', { decision: 'reject' }, reviewer.token)).data.status, 'rejected');
  assert.equal(app.db.prepare('SELECT 1 FROM photo_uploads WHERE id = ?').get(rejectedId), undefined);
  assert.equal((await image(`/api/photos/${rejectedId}`, b.token)).status, 404);
  await call(`/api/rooms/${room}/block`, 'POST', null, b.token);
  assert.equal((await image(`/api/photos/${id}`, a.token)).status, 404);
  assert.equal(app.db.prepare('SELECT 1 FROM photo_uploads WHERE id = ?').get(id), undefined);
}));

test('프로필 사진은 채팅 첨부와 분리하고 연결 수락 뒤에만 상대가 읽는다', async () => withApp(async ({ app, call, user }) => {
  const a = await user('가'), b = await user('나'), outsider = await user('다');
  const reviewer = createReviewer(app.db, '프로필 담당자');
  const other = createReviewer(app.db, '미배정 담당자');
  const base = `http://127.0.0.1:${app.server.address().port}`;
  const png = await sharp({ create: { width: 28, height: 21, channels: 3, background: '#4277ba' } }).png().toBuffer();
  const upload = async token => {
    const response = await fetch(`${base}/api/profile/photos`, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'image/png' }, body: png });
    return { status: response.status, data: await response.json() };
  };
  const image = (path, token) => fetch(base + path, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  const created = await upload(a.token);
  assert.equal(created.status, 201);
  const id = created.data.id;
  await call('/api/queue', 'POST', null, a.token);
  const room = (await call('/api/queue', 'POST', null, b.token)).data.roomId;
  const peerList = `/api/rooms/${room}/peer/profile-photos`;
  assert.equal((await call(peerList, 'GET', null, b.token)).status, 404);
  assert.equal((await call(`/api/photos/${id}`, 'GET', null, b.token)).status, 404);
  assert.deepEqual((await call(`/api/rooms/${room}/photos`, 'GET', null, b.token)).data.photos, []);
  assert.equal((await call('/api/admin/profile-photos', 'GET', null, a.token)).status, 401);
  assert.deepEqual((await call('/api/admin/profile-photos', 'GET', null, reviewer.token)).data.photos, []);
  assignProfilePhoto(app.db, id, reviewer.id);
  assert.equal((await call(`/api/admin/profile-photos/${id}/decision`, 'POST', { decision: 'approve' }, reviewer.token)).status, 409);
  assert.equal((await image(`/api/admin/profile-photos/${id}/image`, other.token)).status, 404);
  const candidate = await image(`/api/admin/profile-photos/${id}/image`, reviewer.token);
  assert.equal(candidate.status, 200);
  assert.equal(candidate.headers.get('cache-control'), 'private, no-store');
  assert.deepEqual(Buffer.from(await candidate.arrayBuffer()).subarray(0, 3), Buffer.from([0xff, 0xd8, 0xff]));
  assert.equal((await call(`/api/admin/profile-photos/${id}/decision`, 'POST', { decision: 'approve' }, reviewer.token)).data.status, 'approved');
  assert.equal((await call(peerList, 'GET', null, b.token)).status, 404);
  assert.equal((await call(`/api/rooms/${room}/request`, 'POST', null, a.token)).status, 201);
  assert.equal((await call(`/api/rooms/${room}/request/decision`, 'POST', { decision: 'accept' }, b.token)).status, 200);
  const list = await call(peerList, 'GET', null, b.token);
  assert.deepEqual(list.data.photos.map(photo => photo.id), [id]);
  assert.equal((await call(peerList, 'GET', null, outsider.token)).status, 404);
  assert.equal((await image(`${peerList}/${id}`, outsider.token)).status, 404);
  assert.equal((await image(`${peerList}/${id}`)).status, 401);
  const approved = await image(`${peerList}/${id}`, b.token);
  assert.equal(approved.status, 200);
  assert.deepEqual(Buffer.from(await approved.arrayBuffer()).subarray(0, 3), Buffer.from([0xff, 0xd8, 0xff]));
  const rejected = (await upload(a.token)).data.id;
  assignProfilePhoto(app.db, rejected, reviewer.id);
  await image(`/api/admin/profile-photos/${rejected}/image`, reviewer.token);
  assert.equal((await call(`/api/admin/profile-photos/${rejected}/decision`, 'POST', { decision: 'reject' }, reviewer.token)).data.status, 'rejected');
  assert.equal(app.db.prepare('SELECT 1 FROM profile_photos WHERE id = ?').get(rejected), undefined);
  assert.equal((await call(`/api/profile/photos/${id}`, 'DELETE', null, b.token)).status, 404);
  assert.equal((await call(`/api/profile/photos`, 'GET', null, a.token)).data.photos.length, 1);
  await call(`/api/rooms/${room}/block`, 'POST', null, b.token);
  assert.equal((await image(`${peerList}/${id}`, b.token)).status, 404);
  assert.ok(app.db.prepare('SELECT 1 FROM profile_photos WHERE id = ?').get(id));
  await call('/api/me', 'DELETE', null, a.token);
  assert.equal(app.db.prepare('SELECT 1 FROM profile_photos WHERE id = ?').get(id), undefined);
  assert.equal(app.db.prepare("SELECT count(*) AS n FROM profile_photo_access_audit WHERE photo_id = ? AND action = 'view'").get(id).n, 1);
}));

test('영상은 수락 후 MP4만 검수하고 승인된 방 참가자에게만 전달한다', async () => withApp(async ({ app, call, user }) => {
  const a = await user('가'), b = await user('나'), outsider = await user('다');
  const reviewer = createReviewer(app.db, '영상 담당자');
  const other = createReviewer(app.db, '다른 담당자');
  await call('/api/queue', 'POST', null, a.token);
  const room = (await call('/api/queue', 'POST', null, b.token)).data.roomId;
  const base = `http://127.0.0.1:${app.server.address().port}`;
  const fixtureDir = fs.mkdtempSync(path.join(os.tmpdir(), 'random-chat-video-fixture-'));
  const fixture = path.join(fixtureDir, 'sample.mp4');
  try {
    execFileSync(ffmpeg, ['-hide_banner','-loglevel','error','-f','lavfi','-i','color=c=blue:s=160x120:d=1',
      '-c:v','libx264','-pix_fmt','yuv420p','-movflags','+faststart',fixture], { timeout: 15000, windowsHide: true });
    const mp4 = fs.readFileSync(fixture);
    const upload = (token, bytes = mp4, type = 'video/mp4') => fetch(`${base}/api/rooms/${room}/videos`, {
      method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': type }, body: bytes });
    const file = (url, token) => fetch(base + url, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
    assert.equal((await upload(a.token)).status, 409);
    assert.equal((await call(`/api/rooms/${room}/videos`, 'GET', null, b.token)).status, 404);
    await call(`/api/rooms/${room}/request`, 'POST', null, a.token);
    await call(`/api/rooms/${room}/request/decision`, 'POST', { decision: 'accept' }, b.token);
    assert.equal((await upload(outsider.token)).status, 404);
    assert.equal((await upload(a.token, mp4, 'image/jpeg')).status, 415);
    assert.equal((await upload(a.token, Buffer.from('fake video'))).status, 400);
    const tooLong = path.join(fixtureDir, 'too-long.mp4');
    execFileSync(ffmpeg, ['-hide_banner','-loglevel','error','-f','lavfi','-i','color=c=blue:s=160x120:r=1:d=21',
      '-c:v','libx264','-pix_fmt','yuv420p',tooLong], { timeout: 15000, windowsHide: true });
    assert.equal((await upload(a.token, fs.readFileSync(tooLong))).status, 400);
    const tooTall = path.join(fixtureDir, 'too-tall.mp4');
    execFileSync(ffmpeg, ['-hide_banner','-loglevel','error','-f','lavfi','-i','color=c=blue:s=1280x800:r=1:d=1',
      '-c:v','libx264','-pix_fmt','yuv420p',tooTall], { timeout: 15000, windowsHide: true });
    assert.equal((await upload(a.token, fs.readFileSync(tooTall))).status, 400);
    const uploaded = await upload(a.token);
    assert.equal(uploaded.status, 201);
    const id = (await uploaded.json()).id;
    assert.equal(app.db.prepare('SELECT status FROM video_uploads WHERE id = ?').get(id).status, 'pending');
    assert.deepEqual((await call(`/api/rooms/${room}/videos`, 'GET', null, b.token)).data.videos, []);
    assert.equal((await file(`/api/videos/${id}`, b.token)).status, 404);
    assert.equal((await call('/api/admin/videos', 'GET', null, a.token)).status, 401);
    assignVideo(app.db, id, reviewer.id);
    assert.equal((await call(`/api/admin/videos/${id}/decision`, 'POST', { decision: 'approve' }, reviewer.token)).status, 409);
    assert.equal((await file(`/api/admin/videos/${id}/file`, other.token)).status, 404);
    const reviewFile = await file(`/api/admin/videos/${id}/file`, reviewer.token);
    assert.equal(reviewFile.status, 200);
    assert.equal(reviewFile.headers.get('content-type'), 'video/mp4');
    assert.equal(reviewFile.headers.get('cache-control'), 'private, no-store');
    assert.equal((await reviewFile.arrayBuffer()).byteLength > 0, true);
    assert.equal((await call(`/api/admin/videos/${id}/decision`, 'POST', { decision: 'approve' }, reviewer.token)).status, 200);
    assert.equal((await call(`/api/rooms/${room}/videos`, 'GET', null, b.token)).data.videos[0].id, id);
    assert.equal((await file(`/api/videos/${id}`, outsider.token)).status, 404);
    assert.equal((await file(`/api/videos/${id}`)).status, 401);
    const approved = await file(`/api/videos/${id}`, b.token);
    assert.equal(approved.status, 200);
    assert.equal(Buffer.from(await approved.arrayBuffer()).toString('ascii', 4, 8), 'ftyp');
    const rejectedUpload = await upload(a.token);
    const rejectedId = (await rejectedUpload.json()).id;
    assignVideo(app.db, rejectedId, reviewer.id);
    await file(`/api/admin/videos/${rejectedId}/file`, reviewer.token);
    assert.equal((await call(`/api/admin/videos/${rejectedId}/decision`, 'POST', { decision: 'reject' }, reviewer.token)).data.status, 'rejected');
    assert.equal(app.db.prepare('SELECT 1 FROM video_uploads WHERE id = ?').get(rejectedId), undefined);
    await call(`/api/rooms/${room}/block`, 'POST', null, b.token);
    assert.equal((await file(`/api/videos/${id}`, a.token)).status, 404);
    assert.equal(app.db.prepare('SELECT 1 FROM video_uploads WHERE id = ?').get(id), undefined);
  } finally { fs.rmSync(fixtureDir, { recursive: true, force: true }); }
}));

test('날짜 기준은 KST', () => {
  assert.equal(kstDay(new Date('2026-09-15T14:59:59Z')), '2026-09-15');
  assert.equal(kstDay(new Date('2026-09-15T15:00:00Z')), '2026-09-16');
});

test('종료 방 정리는 미리보기·백업 뒤 신고 없는 방만 삭제한다', async () => withApp(async ({ app, call, user }) => {
  const a = await user('정리 사용자'), b = await user('상대');
  async function endedRoom(withReport) {
    await call('/api/queue', 'POST', null, a.token);
    const room = (await call('/api/queue', 'POST', null, b.token)).data.roomId;
    await call(`/api/rooms/${room}/messages`, 'POST', { body: '보관 확인', clientId: crypto.randomUUID() }, a.token);
    if (withReport) await call(`/api/rooms/${room}/report`, 'POST', { reason: '증거 보존' }, a.token);
    await call(`/api/rooms/${room}/leave`, 'POST', null, a.token);
    app.db.prepare('UPDATE rooms SET ended_at = ? WHERE id = ?').run(Date.now() - 3 * 86400000, room);
    return room;
  }
  const removable = await endedRoom(false), held = await endedRoom(true);
  assert.throws(() => cutoffForDays(1), /2~3650/);
  const cutoff = cutoffForDays(2);
  const preview = previewEndedRooms(app.db, cutoff);
  assert.equal(preview.rooms, 1);
  assert.equal(preview.messages, 1);
  assert.equal(preview.heldForReports, 1);
  assert.ok(app.db.prepare('SELECT 1 FROM rooms WHERE id = ?').get(removable));
  const backup = path.join(os.tmpdir(), `random-chat-retention-${crypto.randomUUID()}.sqlite`);
  try {
    const result = purgeEndedRooms(app.db, cutoff, backup);
    assert.equal(result.deleted.rooms, 1);
    assert.equal(result.deleted.messages, 1);
    assert.equal(app.db.prepare('SELECT 1 FROM rooms WHERE id = ?').get(removable), undefined);
    assert.ok(app.db.prepare('SELECT 1 FROM rooms WHERE id = ?').get(held));
    assert.ok(app.db.prepare('SELECT 1 FROM messages WHERE room_id = ?').get(held));
    const snapshot = new DatabaseSync(backup);
    try {
      assert.ok(snapshot.prepare('SELECT 1 FROM rooms WHERE id = ?').get(removable));
      assert.ok(snapshot.prepare('SELECT 1 FROM messages WHERE room_id = ?').get(removable));
    } finally { snapshot.close(); }
  } finally { fs.rmSync(backup, { force: true }); }
}));

test('이전 로컬 DB의 사용자와 메시지를 유지하며 새 컬럼을 추가한다', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'random-chat-migrate-'));
  const dbPath = path.join(dir, 'old.sqlite');
  const old = new DatabaseSync(dbPath);
  old.exec(`CREATE TABLE users(id TEXT PRIMARY KEY, token_hash TEXT NOT NULL UNIQUE, nickname TEXT NOT NULL, intro TEXT NOT NULL, adult_attested INTEGER NOT NULL, created_at INTEGER NOT NULL);
    CREATE TABLE messages(id TEXT PRIMARY KEY, room_id TEXT NOT NULL, sender_id TEXT NOT NULL, body TEXT NOT NULL, created_at INTEGER NOT NULL);
    CREATE TABLE reports(id TEXT PRIMARY KEY, room_id TEXT NOT NULL, reporter_id TEXT NOT NULL, target_id TEXT NOT NULL, reason TEXT NOT NULL, created_at INTEGER NOT NULL);`);
  old.prepare('INSERT INTO users VALUES (?,?,?,?,?,?)').run('user-1', 'hash', '기존 사용자', '기존 소개', 1, 1);
  old.prepare('INSERT INTO messages VALUES (?,?,?,?,?)').run('message-1', 'room-1', 'user-1', '기존 메시지', 1);
  old.prepare('INSERT INTO reports VALUES (?,?,?,?,?,?)').run('report-1', 'room-1', 'user-1', 'user-2', '기존 신고', 1);
  old.close();
  const app = createApp({ dbPath });
  try {
    assert.equal(app.db.prepare('SELECT nickname FROM users WHERE id = ?').get('user-1').nickname, '기존 사용자');
    assert.equal(app.db.prepare('SELECT body FROM messages WHERE id = ?').get('message-1').body, '기존 메시지');
    assert.ok(app.db.prepare('PRAGMA table_info(users)').all().some(column => column.name === 'deleted_at'));
    assert.ok(app.db.prepare('PRAGMA table_info(messages)').all().some(column => column.name === 'client_id'));
    const migratedCase = app.db.prepare('SELECT status, reviewer_id FROM report_cases WHERE report_id = ?').get('report-1');
    assert.equal(migratedCase.status, 'open');
    assert.equal(migratedCase.reviewer_id, null);
  } finally { app.db.close(); fs.rmSync(dir, { recursive: true, force: true }); }
});
