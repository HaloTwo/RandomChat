'use strict';

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const os = require('node:os');
const { execFile } = require('node:child_process');
const { promisify } = require('node:util');
const { DatabaseSync } = require('node:sqlite');
const sharp = require('sharp');
const ffmpeg = require('ffmpeg-static');
const ffprobe = require('ffprobe-static').path;
const execFileAsync = promisify(execFile);

const ROOT = __dirname;
const MAX_BODY = 1024 * 1024;
const MAX_PHOTO_UPLOAD = 5 * 1024 * 1024;
const MAX_PHOTO_PIXELS = 16 * 1000 * 1000;
const MAX_APPROVED_PHOTO = 1024 * 1024;
const MAX_VIDEO_UPLOAD = 20 * 1024 * 1024;
const MAX_APPROVED_VIDEO = 8 * 1024 * 1024;
const REQUEST_TTL_MS = 5 * 60 * 1000;
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

function createDatabase(dbPath) {
  fs.mkdirSync(path.dirname(dbPath), { recursive: true });
  const db = new DatabaseSync(dbPath);
  db.exec(`
    PRAGMA foreign_keys = ON;
    PRAGMA journal_mode = WAL;
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY, token_hash TEXT NOT NULL UNIQUE,
      nickname TEXT NOT NULL CHECK(length(nickname) BETWEEN 1 AND 24),
      intro TEXT NOT NULL DEFAULT '', adult_attested INTEGER NOT NULL CHECK(adult_attested = 1),
      created_at INTEGER NOT NULL, deleted_at INTEGER
    );
    CREATE TABLE IF NOT EXISTS queue (
      user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
      entered_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS rooms (
      id TEXT PRIMARY KEY, a_id TEXT NOT NULL REFERENCES users(id),
      b_id TEXT NOT NULL REFERENCES users(id),
      status TEXT NOT NULL CHECK(status IN ('random','connected','ended')),
      created_at INTEGER NOT NULL, ended_at INTEGER,
      CHECK(a_id <> b_id)
    );
    CREATE INDEX IF NOT EXISTS rooms_a ON rooms(a_id, status);
    CREATE INDEX IF NOT EXISTS rooms_b ON rooms(b_id, status);
    CREATE TABLE IF NOT EXISTS messages (
      id TEXT PRIMARY KEY, room_id TEXT NOT NULL REFERENCES rooms(id),
      sender_id TEXT NOT NULL REFERENCES users(id), client_id TEXT,
      body TEXT NOT NULL CHECK(length(body) BETWEEN 1 AND 1000),
      created_at INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS messages_room ON messages(room_id, created_at);
    CREATE TABLE IF NOT EXISTS requests (
      id TEXT PRIMARY KEY, room_id TEXT NOT NULL REFERENCES rooms(id),
      requester_id TEXT NOT NULL REFERENCES users(id), receiver_id TEXT NOT NULL REFERENCES users(id),
      status TEXT NOT NULL CHECK(status IN ('pending','accepted','rejected','cancelled','expired')),
      quota_day TEXT NOT NULL, expires_at INTEGER NOT NULL, created_at INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS requests_quota ON requests(requester_id, quota_day, status);
    CREATE TABLE IF NOT EXISTS blocks (
      user_id TEXT NOT NULL REFERENCES users(id), target_id TEXT NOT NULL REFERENCES users(id),
      created_at INTEGER NOT NULL, PRIMARY KEY(user_id, target_id), CHECK(user_id <> target_id)
    );
    CREATE TABLE IF NOT EXISTS reports (
      id TEXT PRIMARY KEY, room_id TEXT NOT NULL REFERENCES rooms(id),
      reporter_id TEXT NOT NULL REFERENCES users(id), target_id TEXT NOT NULL REFERENCES users(id),
      reason TEXT NOT NULL CHECK(length(reason) BETWEEN 1 AND 500), created_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS admin_accounts (
      id TEXT PRIMARY KEY, token_hash TEXT NOT NULL UNIQUE,
      label TEXT NOT NULL CHECK(length(label) BETWEEN 1 AND 40),
      active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)), created_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS report_cases (
      report_id TEXT PRIMARY KEY REFERENCES reports(id) ON DELETE CASCADE,
      reviewer_id TEXT REFERENCES admin_accounts(id),
      status TEXT NOT NULL DEFAULT 'open' CHECK(status IN ('open','closed')),
      resolution TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS report_cases_reviewer ON report_cases(reviewer_id,status);
    CREATE TABLE IF NOT EXISTS admin_access_audit (
      id TEXT PRIMARY KEY, actor TEXT NOT NULL, report_id TEXT NOT NULL,
      action TEXT NOT NULL CHECK(action IN ('assign','view','close','revoke')),
      created_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS photo_uploads (
      id TEXT PRIMARY KEY, room_id TEXT NOT NULL REFERENCES rooms(id),
      sender_id TEXT NOT NULL REFERENCES users(id),
      status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','approved','rejected')),
      image BLOB NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL,
      created_at INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS photo_uploads_room ON photo_uploads(room_id, created_at);
    CREATE TABLE IF NOT EXISTS photo_access_audit (
      id TEXT PRIMARY KEY, actor TEXT NOT NULL, photo_id TEXT NOT NULL,
      action TEXT NOT NULL CHECK(action IN ('assign','view','approve','reject')),
      created_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS profile_photos (
      id TEXT PRIMARY KEY, owner_id TEXT NOT NULL REFERENCES users(id),
      reviewer_id TEXT REFERENCES admin_accounts(id),
      status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','approved')),
      image BLOB NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL,
      created_at INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS profile_photos_owner ON profile_photos(owner_id,created_at);
    CREATE INDEX IF NOT EXISTS profile_photos_reviewer ON profile_photos(reviewer_id,status);
    CREATE TABLE IF NOT EXISTS profile_photo_access_audit (
      id TEXT PRIMARY KEY, actor TEXT NOT NULL, photo_id TEXT NOT NULL,
      action TEXT NOT NULL CHECK(action IN ('assign','view','approve','reject')),
      created_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS video_uploads (
      id TEXT PRIMARY KEY, room_id TEXT NOT NULL REFERENCES rooms(id),
      sender_id TEXT NOT NULL REFERENCES users(id),
      reviewer_id TEXT REFERENCES admin_accounts(id),
      status TEXT NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','approved')),
      video BLOB NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL,
      duration REAL NOT NULL, created_at INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS video_uploads_room ON video_uploads(room_id,created_at);
    CREATE INDEX IF NOT EXISTS video_uploads_reviewer ON video_uploads(reviewer_id,status);
    CREATE TABLE IF NOT EXISTS video_access_audit (
      id TEXT PRIMARY KEY, actor TEXT NOT NULL, video_id TEXT NOT NULL,
      action TEXT NOT NULL CHECK(action IN ('assign','view','approve','reject')),
      created_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS posts (
      id TEXT PRIMARY KEY, author_id TEXT NOT NULL REFERENCES users(id),
      body TEXT NOT NULL CHECK(length(body) BETWEEN 1 AND 500),
      image BLOB, image_width INTEGER, image_height INTEGER,
      created_at INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS posts_feed ON posts(created_at DESC);
    CREATE TABLE IF NOT EXISTS stories (
      id TEXT PRIMARY KEY, author_id TEXT NOT NULL REFERENCES users(id),
      body TEXT NOT NULL CHECK(length(body) BETWEEN 1 AND 120),
      image BLOB, image_width INTEGER, image_height INTEGER,
      created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS stories_active ON stories(expires_at DESC, created_at DESC);
    CREATE TABLE IF NOT EXISTS conversation_access_audit (
      id TEXT PRIMARY KEY, reviewer_id TEXT NOT NULL REFERENCES admin_accounts(id),
      room_id TEXT NOT NULL, created_at INTEGER NOT NULL
    );
  `);
  // 기존 로컬 DB의 대화 기록은 유지하고 재전송 식별자만 추가한다.
  const messageColumns = db.prepare('PRAGMA table_info(messages)').all();
  if (!messageColumns.some(column => column.name === 'client_id')) db.exec('ALTER TABLE messages ADD COLUMN client_id TEXT');
  db.exec('CREATE UNIQUE INDEX IF NOT EXISTS messages_client_id ON messages(room_id, sender_id, client_id)');
  const userColumns = db.prepare('PRAGMA table_info(users)').all();
  if (!userColumns.some(column => column.name === 'deleted_at')) db.exec('ALTER TABLE users ADD COLUMN deleted_at INTEGER');
  const photoColumns = db.prepare('PRAGMA table_info(photo_uploads)').all();
  if (!photoColumns.some(column => column.name === 'reviewer_id')) db.exec('ALTER TABLE photo_uploads ADD COLUMN reviewer_id TEXT REFERENCES admin_accounts(id)');
  db.exec('CREATE INDEX IF NOT EXISTS photo_uploads_reviewer ON photo_uploads(reviewer_id,status)');
  // 예전 버전에서 접수된 신고도 내용 공개 없이 미배정 사건으로 등록한다.
  db.exec('INSERT OR IGNORE INTO report_cases(report_id,updated_at) SELECT id,created_at FROM reports');
  return db;
}

const uuid = () => crypto.randomUUID();
const tokenHash = token => crypto.createHash('sha256').update(token).digest('hex');
const kstDay = now => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
const nextKstMidnight = now => (Math.floor((now + KST_OFFSET_MS) / DAY_MS) + 1) * DAY_MS - KST_OFFSET_MS;
const httpError = (status, message) => Object.assign(new Error(message), { status });

// 여러 테이블의 상태 변경은 함께 성공하거나 함께 취소한다.
function transaction(db, action) {
  db.exec('BEGIN IMMEDIATE');
  try {
    const result = action();
    db.exec('COMMIT');
    return result;
  } catch (error) {
    db.exec('ROLLBACK');
    throw error;
  }
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', chunk => {
      size += chunk.length;
      if (size > MAX_BODY) { reject(httpError(413, '요청 크기가 너무 큽니다.')); req.destroy(); return; }
      chunks.push(chunk);
    });
    req.on('end', () => {
      try { resolve(chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {}); }
      catch { reject(httpError(400, 'JSON 형식이 올바르지 않습니다.')); }
    });
    req.on('error', reject);
  });
}

// 원본은 메모리 상한 안에서만 읽고, 한도를 넘으면 응답 전까지 나머지 스트림을 비운다.
function readBytes(req, limit, kind = '사진') {
  return new Promise((resolve, reject) => {
    let size = 0;
    let tooLarge = false;
    const chunks = [];
    req.on('data', chunk => {
      size += chunk.length;
      if (size > limit) { tooLarge = true; chunks.length = 0; }
      else if (!tooLarge) chunks.push(chunk);
    });
    req.on('end', () => tooLarge ? reject(httpError(413, `${kind} 크기 제한을 초과했습니다.`)) : resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

async function probeVideo(file) {
  const { stdout } = await execFileAsync(ffprobe, ['-v', 'error', '-show_streams', '-show_format', '-of', 'json', file], { timeout: 10000, maxBuffer: 1024 * 1024, windowsHide: true });
  return JSON.parse(stdout);
}

// 실제 MP4 영상만 검사하고 다시 인코딩해 원본 컨테이너·메타데이터를 전달하지 않는다.
async function normalizeVideo(input, declaredType) {
  if (declaredType !== 'video/mp4') throw httpError(415, 'MP4 영상만 보낼 수 있습니다.');
  const brand = input.length >= 12 ? input.toString('ascii', 8, 12) : '';
  if (input.toString('ascii', 4, 8) !== 'ftyp' || !['isom','iso2','mp41','mp42','avc1','M4V '].includes(brand)) throw httpError(400, 'MP4 파일을 읽을 수 없습니다.');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'moment-video-'));
  const source = path.join(dir, 'source.mp4'), target = path.join(dir, 'approved.mp4');
  try {
    fs.writeFileSync(source, input, { flag: 'wx' });
    const info = await probeVideo(source);
    const streams = info.streams || [];
    const videos = streams.filter(stream => stream.codec_type === 'video');
    const audios = streams.filter(stream => stream.codec_type === 'audio');
    const width = videos[0]?.width, height = videos[0]?.height;
    const duration = Number(info.format?.duration);
    const streamDurations = streams.map(stream => Number(stream.duration)).filter(Number.isFinite);
    if (videos.length !== 1 || audios.length > 1 || streams.length !== videos.length + audios.length ||
        !Number.isFinite(duration) || duration <= 0 || duration > 20 || streamDurations.some(seconds => seconds > 20) ||
        !Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1 || width > 1280 || height > 720) {
      throw httpError(400, '20초·720p 이하 MP4 영상만 보낼 수 있습니다.');
    }
    await execFileAsync(ffmpeg, ['-hide_banner','-loglevel','error','-nostdin','-i',source,
      '-map','0:v:0','-map','0:a:0?','-map_metadata','-1','-map_chapters','-1',
      '-c:v','libx264','-preset','veryfast','-b:v','1500k','-maxrate','2000k','-bufsize','4000k','-pix_fmt','yuv420p',
      '-c:a','aac','-b:a','96k','-movflags','+faststart','-t','20','-f','mp4',target],
    { timeout: 30000, maxBuffer: 1024 * 1024, windowsHide: true });
    const stat = fs.statSync(target);
    if (!stat.size || stat.size > MAX_APPROVED_VIDEO) throw httpError(413, '변환된 영상이 8MB를 초과했습니다.');
    const output = await probeVideo(target);
    const outputDuration = Number(output.format?.duration);
    if (!Number.isFinite(outputDuration) || outputDuration <= 0 || outputDuration > 20.1 ||
        output.streams.filter(stream => stream.codec_type === 'video').length !== 1) throw httpError(400, '영상 변환 결과가 올바르지 않습니다.');
    return { data: fs.readFileSync(target), width, height, duration: outputDuration };
  } catch (error) {
    if (error.status) throw error;
    throw httpError(400, '영상 파일을 검사하거나 변환할 수 없습니다.');
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
}

// 실제 정지 이미지인지 디코딩하고 위치정보 등 원본 메타데이터를 제거한다.
async function normalizePhoto(input, declaredType) {
  const expected = { 'image/jpeg': 'jpeg', 'image/png': 'png' }[declaredType];
  if (!expected) throw httpError(415, 'JPEG 또는 PNG 사진만 보낼 수 있습니다.');
  let metadata;
  try { metadata = await sharp(input, { limitInputPixels: MAX_PHOTO_PIXELS, failOn: 'warning' }).metadata(); }
  catch { throw httpError(400, '사진 파일을 읽을 수 없습니다.'); }
  if (metadata.format !== expected || !metadata.width || !metadata.height || (metadata.pages || 1) !== 1 || metadata.width * metadata.height > MAX_PHOTO_PIXELS) {
    throw httpError(400, '정지 JPEG 또는 PNG 파일만 보낼 수 있습니다.');
  }
  try {
    const { data, info } = await sharp(input, { limitInputPixels: MAX_PHOTO_PIXELS, failOn: 'warning' })
      .rotate().resize(1600, 1600, { fit: 'inside', withoutEnlargement: true })
      .flatten({ background: '#ffffff' }).jpeg({ quality: 78 }).toBuffer({ resolveWithObject: true });
    if (data.length > MAX_APPROVED_PHOTO) throw httpError(413, '변환된 사진이 너무 큽니다. 다른 사진을 선택하세요.');
    return { data, width: info.width, height: info.height };
  } catch (error) {
    if (error.status) throw error;
    throw httpError(400, '사진 변환에 실패했습니다.');
  }
}

function send(res, status, data) {
  const body = JSON.stringify(data);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'Content-Length': Buffer.byteLength(body) });
  res.end(body);
}

// 정규화한 JPEG만 인증된 요청에 전달하고 브라우저·중간 캐시를 막는다.
function sendPhoto(res, image) {
  res.writeHead(200, { 'Content-Type': 'image/jpeg', 'Content-Length': image.length,
    'Content-Disposition': 'inline', 'Cache-Control': 'private, no-store',
    'X-Content-Type-Options': 'nosniff' });
  res.end(image);
}

function sendVideo(res, video) {
  res.writeHead(200, { 'Content-Type': 'video/mp4', 'Content-Length': video.length,
    'Content-Disposition': 'inline', 'Cache-Control': 'private, no-store',
    'X-Content-Type-Options': 'nosniff', 'Accept-Ranges': 'none' });
  res.end(video);
}

function requireUser(db, req) {
  const token = /^Bearer (.+)$/.exec(req.headers.authorization || '')?.[1];
  if (!token) throw httpError(401, '로그인이 필요합니다.');
  const user = db.prepare('SELECT id, nickname, intro FROM users WHERE token_hash = ? AND deleted_at IS NULL').get(tokenHash(token));
  if (!user) throw httpError(401, '로그인이 만료되었습니다.');
  return user;
}

function requireReviewer(db, req) {
  const token = /^Bearer (.+)$/.exec(req.headers.authorization || '')?.[1];
  if (!token) throw httpError(401, '담당자 인증이 필요합니다.');
  const reviewer = db.prepare('SELECT id, label FROM admin_accounts WHERE token_hash = ? AND active = 1').get(tokenHash(token));
  if (!reviewer) throw httpError(401, '담당자 인증이 유효하지 않습니다.');
  return reviewer;
}

// 담당자 토큰은 로컬 명령에서만 발급하며 HTTP 경로에서는 만들지 않는다.
function createReviewer(db, label) {
  const clean = String(label || '').trim();
  if (!clean || clean.length > 40) throw Error('담당자 이름은 1~40자여야 합니다.');
  const id = uuid(), token = crypto.randomBytes(32).toString('base64url');
  db.prepare('INSERT INTO admin_accounts(id,token_hash,label,created_at) VALUES (?,?,?,?)').run(id, tokenHash(token), clean, Date.now());
  return { id, token };
}

function assignReport(db, reportId, reviewerId) {
  return transaction(db, () => {
    const reviewer = db.prepare('SELECT id FROM admin_accounts WHERE id = ? AND active = 1').get(reviewerId);
    if (!reviewer) throw Error('활성 담당자를 찾을 수 없습니다.');
    const changed = db.prepare('UPDATE report_cases SET reviewer_id = ?, updated_at = ? WHERE report_id = ? AND status = ?').run(reviewerId, Date.now(), reportId, 'open');
    if (changed.changes !== 1) throw Error('열린 신고 사건을 찾을 수 없습니다.');
    db.prepare('INSERT INTO admin_access_audit(id,actor,report_id,action,created_at) VALUES (?,?,?,?,?)').run(uuid(), 'local-owner', reportId, 'assign', Date.now());
    return { assigned: true };
  });
}

// 사진은 담당자에게 명시적으로 배정하고, 배정 변경도 감사 기록에 남긴다.
function assignPhoto(db, photoId, reviewerId) {
  return transaction(db, () => {
    const reviewer = db.prepare('SELECT id FROM admin_accounts WHERE id = ? AND active = 1').get(reviewerId);
    if (!reviewer) throw Error('활성 담당자를 찾을 수 없습니다.');
    const changed = db.prepare(`UPDATE photo_uploads SET reviewer_id = ? WHERE id = ? AND status = 'pending'
      AND room_id IN (SELECT id FROM rooms WHERE status IN ('random','connected'))`).run(reviewerId, photoId);
    if (changed.changes !== 1) throw Error('검토 대기 중인 활성 방 사진을 찾을 수 없습니다.');
    db.prepare('INSERT INTO photo_access_audit(id,actor,photo_id,action,created_at) VALUES (?,?,?,?,?)').run(uuid(), 'local-owner', photoId, 'assign', Date.now());
    return { assigned: true };
  });
}

// 프로필 사진은 채팅 첨부와 다른 배정·감사 기록을 사용한다.
function assignProfilePhoto(db, photoId, reviewerId) {
  return transaction(db, () => {
    const reviewer = db.prepare('SELECT id FROM admin_accounts WHERE id = ? AND active = 1').get(reviewerId);
    if (!reviewer) throw Error('활성 담당자를 찾을 수 없습니다.');
    const changed = db.prepare(`UPDATE profile_photos SET reviewer_id = ? WHERE id = ? AND status = 'pending'
      AND owner_id IN (SELECT id FROM users WHERE deleted_at IS NULL)`).run(reviewerId, photoId);
    if (changed.changes !== 1) throw Error('검토 대기 중인 프로필 사진을 찾을 수 없습니다.');
    db.prepare('INSERT INTO profile_photo_access_audit(id,actor,photo_id,action,created_at) VALUES (?,?,?,?,?)').run(uuid(), 'local-owner', photoId, 'assign', Date.now());
    return { assigned: true };
  });
}

// 영상은 연결된 방의 검토 대기 항목만 활성 담당자에게 배정한다.
function assignVideo(db, videoId, reviewerId) {
  return transaction(db, () => {
    if (!db.prepare('SELECT 1 FROM admin_accounts WHERE id = ? AND active = 1').get(reviewerId)) throw Error('활성 담당자를 찾을 수 없습니다.');
    const changed = db.prepare(`UPDATE video_uploads SET reviewer_id = ? WHERE id = ? AND status = 'pending'
      AND room_id IN (SELECT id FROM rooms WHERE status = 'connected')`).run(reviewerId, videoId);
    if (changed.changes !== 1) throw Error('검토 대기 중인 연결 방 영상을 찾을 수 없습니다.');
    db.prepare('INSERT INTO video_access_audit(id,actor,video_id,action,created_at) VALUES (?,?,?,?,?)').run(uuid(), 'local-owner', videoId, 'assign', Date.now());
    return { assigned: true };
  });
}

function roomFor(db, roomId, userId) {
  const room = db.prepare('SELECT * FROM rooms WHERE id = ? AND (a_id = ? OR b_id = ?)').get(roomId, userId, userId);
  if (!room) throw httpError(404, '대화방을 찾을 수 없습니다.');
  return room;
}

function otherId(room, userId) { return room.a_id === userId ? room.b_id : room.a_id; }

// 만료된 요청은 무료권 예약에서 즉시 제외한다.
function expireRequests(db, now) {
  db.prepare("UPDATE requests SET status = 'expired' WHERE status = 'pending' AND expires_at <= ?").run(now);
}

function quota(db, userId, now) {
  expireRequests(db, now);
  const day = kstDay(new Date(now));
  const used = db.prepare("SELECT count(*) AS n FROM requests WHERE requester_id = ? AND quota_day = ? AND status = 'accepted'").get(userId, day).n;
  const reserved = db.prepare("SELECT count(*) AS n FROM requests WHERE requester_id = ? AND quota_day = ? AND status = 'pending'").get(userId, day).n;
  return { day, limit: 2, used, reserved, available: Math.max(0, 2 - used - reserved) };
}

function roomView(db, room, userId, includeMessages = false) {
  const peerId = otherId(room, userId);
  const peer = db.prepare('SELECT nickname, intro, deleted_at FROM users WHERE id = ?').get(peerId);
  const request = db.prepare("SELECT id, requester_id, receiver_id, status, expires_at FROM requests WHERE room_id = ? ORDER BY created_at DESC LIMIT 1").get(room.id);
  const view = {
    id: room.id, status: room.status, createdAt: room.created_at,
    peer: { displayName: peer.deleted_at ? '탈퇴한 사용자' : room.status === 'connected' ? peer.nickname : '랜덤 상대', intro: peer.deleted_at ? null : room.status === 'connected' ? peer.intro : null },
    request: request ? { id: request.id, direction: request.requester_id === userId ? 'sent' : 'received', status: request.status, expiresAt: request.expires_at } : null
  };
  if (includeMessages) view.messages = db.prepare('SELECT id, sender_id, client_id, body, created_at FROM messages WHERE room_id = ? ORDER BY created_at ASC, rowid ASC LIMIT 500').all(room.id).map(m => ({ id: m.id, mine: m.sender_id === userId, clientId: m.sender_id === userId ? m.client_id : undefined, body: m.body, createdAt: m.created_at }));
  return view;
}

// 서버가 현재 상태를 기준으로 매칭한다. 차단 관계와 활성 대화를 제외한다.
function enterQueue(db, userId, now) {
  return transaction(db, () => {
    const active = db.prepare("SELECT id FROM rooms WHERE status = 'random' AND (a_id = ? OR b_id = ?) ORDER BY created_at DESC LIMIT 1").get(userId, userId);
    if (active) throw httpError(409, '진행 중인 대화가 있습니다.');
    const waiting = db.prepare(`SELECT q.user_id FROM queue q JOIN users u ON u.id = q.user_id AND u.deleted_at IS NULL WHERE q.user_id <> ?
      AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.user_id = q.user_id AND b.target_id = ?) OR (b.user_id = ? AND b.target_id = q.user_id))
      ORDER BY q.entered_at ASC LIMIT 1`).get(userId, userId, userId);
    if (!waiting) {
      db.prepare('INSERT INTO queue(user_id, entered_at) VALUES (?, ?) ON CONFLICT(user_id) DO NOTHING').run(userId, now);
      return { waiting: true };
    }
    db.prepare('DELETE FROM queue WHERE user_id IN (?, ?)').run(userId, waiting.user_id);
    const id = uuid();
    db.prepare("INSERT INTO rooms(id,a_id,b_id,status,created_at) VALUES (?,?,?,'random',?)").run(id, waiting.user_id, userId, now);
    return { waiting: false, roomId: id };
  });
}

function createApp(options = {}) {
  const dbPath = options.dbPath || path.join(ROOT, 'data', 'chat.sqlite');
  const db = createDatabase(dbPath);
  const staticDir = options.staticDir || path.join(ROOT, 'public');
  const apkPath = options.apkPath || path.join(ROOT, 'android', 'build', 'moment-local-debug.apk');
  let activeVideoConversions = 0;
  const server = http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url, 'http://localhost');
      const pathname = url.pathname;
      if (req.method === 'GET' && (pathname === '/' || pathname === '/review' || pathname === '/install' || pathname === '/app.js' || pathname === '/review.js' || pathname === '/style.css')) {
        const file = path.join(staticDir, pathname === '/' ? 'index.html' : pathname === '/review' ? 'review.html' : pathname === '/install' ? 'install.html' : pathname.slice(1));
        const type = pathname.endsWith('.js') ? 'text/javascript' : pathname.endsWith('.css') ? 'text/css' : 'text/html';
        res.writeHead(200, { 'Content-Type': `${type}; charset=utf-8`, 'Cache-Control': 'no-store' });
        fs.createReadStream(file).pipe(res);
        return;
      }
      // 공기계 브라우저에서 디버그 APK를 직접 받을 수 있게 한다. 파일이 없으면 명확한 404를 보낸다.
      if (pathname === '/app.apk' && req.method === 'GET') {
        if (!fs.existsSync(apkPath)) throw httpError(404, 'APK가 없습니다. PC에서 android/build.ps1로 먼저 빌드하세요.');
        const size = fs.statSync(apkPath).size;
        res.writeHead(200, {
          'Content-Type': 'application/vnd.android.package-archive',
          'Content-Disposition': 'attachment; filename="moment-local-debug.apk"',
          'Content-Length': size,
          'Cache-Control': 'no-store',
          'X-Content-Type-Options': 'nosniff'
        });
        const stream = fs.createReadStream(apkPath);
        stream.on('error', () => res.destroy());
        stream.pipe(res);
        return;
      }
      if (pathname === '/api/health' && req.method === 'GET') return send(res, 200, { ok: true });
      if (pathname === '/api/dev/users' && req.method === 'POST') {
        const body = await readJson(req);
        const nickname = String(body.nickname || '').trim();
        const intro = String(body.intro || '').trim();
        if (!body.adultAttested) throw httpError(400, '성인 확인에 동의해야 합니다. 이 확인은 개발용 자기확인입니다.');
        if (nickname.length < 1 || nickname.length > 24 || intro.length > 300) throw httpError(400, '닉네임은 1~24자, 소개는 300자 이하로 입력하세요.');
        const id = uuid(), token = crypto.randomBytes(32).toString('base64url');
        db.prepare('INSERT INTO users(id,token_hash,nickname,intro,adult_attested,created_at) VALUES (?,?,?,?,1,?)').run(id, tokenHash(token), nickname, intro, Date.now());
        return send(res, 201, { id, token, nickname });
      }
      if (!pathname.startsWith('/api/')) throw httpError(404, '페이지를 찾을 수 없습니다.');
      if (pathname.startsWith('/api/admin/')) {
        const reviewer = requireReviewer(db, req);
        // 로컬 관리자 인증을 통과한 경우에만 대화 목록과 본문을 제공한다.
        if (pathname === '/api/admin/rooms' && req.method === 'GET') {
          const offset = Math.max(0, Math.min(1000000, Number(url.searchParams.get('offset')) || 0));
          const rooms = db.prepare(`SELECT r.id,r.status,r.created_at AS createdAt,
            a.nickname AS userA,b.nickname AS userB FROM rooms r
            JOIN users a ON a.id=r.a_id JOIN users b ON b.id=r.b_id
            ORDER BY r.created_at DESC,r.id LIMIT 50 OFFSET ?`).all(Math.floor(offset));
          return send(res, 200, { rooms });
        }
        const adminRoom = /^\/api\/admin\/rooms\/([^/]+)$/.exec(pathname);
        if (adminRoom && req.method === 'GET') {
          const room = db.prepare('SELECT id,status FROM rooms WHERE id=?').get(adminRoom[1]);
          if (!room) throw httpError(404, '대화방을 찾을 수 없습니다.');
          const offset = Math.max(0, Math.min(1000000, Number(url.searchParams.get('offset')) || 0));
          const messages = db.prepare(`SELECT m.id,m.body,m.created_at AS createdAt,u.nickname AS sender
            FROM messages m JOIN users u ON u.id=m.sender_id WHERE m.room_id=?
            ORDER BY m.created_at,m.rowid LIMIT 200 OFFSET ?`).all(room.id, Math.floor(offset));
          db.prepare('INSERT INTO conversation_access_audit VALUES (?,?,?,?)').run(uuid(),reviewer.id,room.id,Date.now());
          return send(res, 200, { room, messages });
        }
        if (pathname === '/api/admin/videos' && req.method === 'GET') {
          const videos = db.prepare(`SELECT v.id,v.status,v.width,v.height,v.duration,v.created_at AS createdAt
            FROM video_uploads v JOIN rooms r ON r.id = v.room_id
            WHERE v.reviewer_id = ? AND r.status = 'connected' ORDER BY v.created_at DESC LIMIT 100`).all(reviewer.id);
          return send(res, 200, { videos });
        }
        const adminVideo = /^\/api\/admin\/videos\/([^/]+)\/(file|decision)$/.exec(pathname);
        if (adminVideo && adminVideo[2] === 'file' && req.method === 'GET') {
          const video = transaction(db, () => {
            const row = db.prepare(`SELECT v.video FROM video_uploads v JOIN rooms r ON r.id = v.room_id
              WHERE v.id = ? AND v.reviewer_id = ? AND v.status = 'pending' AND r.status = 'connected'`).get(adminVideo[1], reviewer.id);
            if (!row) throw httpError(404, '배정된 검토 영상을 찾을 수 없습니다.');
            db.prepare('INSERT INTO video_access_audit(id,actor,video_id,action,created_at) VALUES (?,?,?,?,?)').run(uuid(), reviewer.id, adminVideo[1], 'view', Date.now());
            return row.video;
          });
          return sendVideo(res, video);
        }
        if (adminVideo && adminVideo[2] === 'decision' && req.method === 'POST') {
          const decision = (await readJson(req)).decision;
          if (!['approve','reject'].includes(decision)) throw httpError(400, '승인 또는 거절을 선택하세요.');
          const result = transaction(db, () => {
            const row = db.prepare(`SELECT v.id FROM video_uploads v JOIN rooms r ON r.id = v.room_id
              WHERE v.id = ? AND v.reviewer_id = ? AND v.status = 'pending' AND r.status = 'connected'`).get(adminVideo[1], reviewer.id);
            if (!row) throw httpError(404, '배정된 검토 영상을 찾을 수 없습니다.');
            if (!db.prepare("SELECT 1 FROM video_access_audit WHERE video_id = ? AND actor = ? AND action = 'view'").get(row.id, reviewer.id)) throw httpError(409, '영상을 먼저 열어 검토하세요.');
            if (decision === 'approve') db.prepare("UPDATE video_uploads SET status = 'approved' WHERE id = ?").run(row.id);
            else db.prepare('DELETE FROM video_uploads WHERE id = ?').run(row.id);
            db.prepare('INSERT INTO video_access_audit(id,actor,video_id,action,created_at) VALUES (?,?,?,?,?)').run(uuid(), reviewer.id, row.id, decision, Date.now());
            return { status: decision === 'approve' ? 'approved' : 'rejected' };
          });
          return send(res, 200, result);
        }
        if (pathname === '/api/admin/profile-photos' && req.method === 'GET') {
          const photos = db.prepare(`SELECT p.id, p.status, p.width, p.height, p.created_at AS createdAt
            FROM profile_photos p JOIN users u ON u.id = p.owner_id AND u.deleted_at IS NULL
            WHERE p.reviewer_id = ? ORDER BY p.created_at DESC LIMIT 100`).all(reviewer.id);
          return send(res, 200, { photos });
        }
        const adminProfilePhoto = /^\/api\/admin\/profile-photos\/([^/]+)\/(image|decision)$/.exec(pathname);
        if (adminProfilePhoto && adminProfilePhoto[2] === 'image' && req.method === 'GET') {
          const image = transaction(db, () => {
            const row = db.prepare(`SELECT p.image FROM profile_photos p JOIN users u ON u.id = p.owner_id AND u.deleted_at IS NULL
              WHERE p.id = ? AND p.reviewer_id = ? AND p.status = 'pending'`).get(adminProfilePhoto[1], reviewer.id);
            if (!row) throw httpError(404, '배정된 프로필 사진을 찾을 수 없습니다.');
            db.prepare('INSERT INTO profile_photo_access_audit(id,actor,photo_id,action,created_at) VALUES (?,?,?,?,?)').run(uuid(), reviewer.id, adminProfilePhoto[1], 'view', Date.now());
            return row.image;
          });
          return sendPhoto(res, image);
        }
        if (adminProfilePhoto && adminProfilePhoto[2] === 'decision' && req.method === 'POST') {
          const decision = (await readJson(req)).decision;
          if (!['approve','reject'].includes(decision)) throw httpError(400, '승인 또는 거절을 선택하세요.');
          const result = transaction(db, () => {
            const row = db.prepare(`SELECT p.id FROM profile_photos p JOIN users u ON u.id = p.owner_id AND u.deleted_at IS NULL
              WHERE p.id = ? AND p.reviewer_id = ? AND p.status = 'pending'`).get(adminProfilePhoto[1], reviewer.id);
            if (!row) throw httpError(404, '배정된 프로필 사진을 찾을 수 없습니다.');
            if (!db.prepare("SELECT 1 FROM profile_photo_access_audit WHERE photo_id = ? AND actor = ? AND action = 'view'").get(row.id, reviewer.id)) throw httpError(409, '프로필 사진을 먼저 열어 검토하세요.');
            if (decision === 'approve') db.prepare("UPDATE profile_photos SET status = 'approved' WHERE id = ?").run(row.id);
            else db.prepare('DELETE FROM profile_photos WHERE id = ?').run(row.id);
            db.prepare('INSERT INTO profile_photo_access_audit(id,actor,photo_id,action,created_at) VALUES (?,?,?,?,?)').run(uuid(), reviewer.id, row.id, decision, Date.now());
            return { status: decision === 'approve' ? 'approved' : 'rejected' };
          });
          return send(res, 200, result);
        }
        if (pathname === '/api/admin/photos' && req.method === 'GET') {
          const photos = db.prepare(`SELECT p.id, p.status, p.width, p.height, p.created_at AS createdAt
            FROM photo_uploads p JOIN rooms r ON r.id = p.room_id
            WHERE p.reviewer_id = ? AND r.status IN ('random','connected')
            ORDER BY p.created_at DESC LIMIT 100`).all(reviewer.id);
          return send(res, 200, { photos });
        }
        const adminPhoto = /^\/api\/admin\/photos\/([^/]+)\/(image|decision)$/.exec(pathname);
        if (adminPhoto && adminPhoto[2] === 'image' && req.method === 'GET') {
          // 열람 기록과 이미지 조회를 같은 트랜잭션에서 처리한다.
          const image = transaction(db, () => {
            const row = db.prepare(`SELECT p.image FROM photo_uploads p JOIN rooms r ON r.id = p.room_id
              WHERE p.id = ? AND p.reviewer_id = ? AND p.status = 'pending' AND r.status IN ('random','connected')`).get(adminPhoto[1], reviewer.id);
            if (!row) throw httpError(404, '배정된 검토 사진을 찾을 수 없습니다.');
            db.prepare('INSERT INTO photo_access_audit(id,actor,photo_id,action,created_at) VALUES (?,?,?,?,?)').run(uuid(), reviewer.id, adminPhoto[1], 'view', Date.now());
            return row.image;
          });
          return sendPhoto(res, image);
        }
        if (adminPhoto && adminPhoto[2] === 'decision' && req.method === 'POST') {
          const decision = (await readJson(req)).decision;
          if (!['approve', 'reject'].includes(decision)) throw httpError(400, '승인 또는 거절을 선택하세요.');
          const result = transaction(db, () => {
            const row = db.prepare(`SELECT p.id FROM photo_uploads p JOIN rooms r ON r.id = p.room_id
              WHERE p.id = ? AND p.reviewer_id = ? AND p.status = 'pending' AND r.status IN ('random','connected')`).get(adminPhoto[1], reviewer.id);
            if (!row) throw httpError(404, '배정된 검토 사진을 찾을 수 없습니다.');
            const viewed = db.prepare("SELECT 1 FROM photo_access_audit WHERE photo_id = ? AND actor = ? AND action = 'view'").get(row.id, reviewer.id);
            if (!viewed) throw httpError(409, '사진을 먼저 열어 검토하세요.');
            if (decision === 'approve') db.prepare("UPDATE photo_uploads SET status = 'approved' WHERE id = ?").run(row.id);
            else db.prepare('DELETE FROM photo_uploads WHERE id = ?').run(row.id);
            db.prepare('INSERT INTO photo_access_audit(id,actor,photo_id,action,created_at) VALUES (?,?,?,?,?)').run(uuid(), reviewer.id, row.id, decision, Date.now());
            return { status: decision === 'approve' ? 'approved' : 'rejected' };
          });
          return send(res, 200, result);
        }
        if (pathname === '/api/admin/reports' && req.method === 'GET') {
          const cases = db.prepare(`SELECT c.report_id AS id, c.status, r.created_at AS createdAt
            FROM report_cases c JOIN reports r ON r.id = c.report_id
            WHERE c.reviewer_id = ? ORDER BY r.created_at DESC LIMIT 100`).all(reviewer.id);
          return send(res, 200, { cases });
        }
        const reportRoute = /^\/api\/admin\/reports\/([^/]+)(?:\/(close))?$/.exec(pathname);
        if (!reportRoute) throw httpError(404, '담당자 기능을 찾을 수 없습니다.');
        const reportId = reportRoute[1];
        if (!reportRoute[2] && req.method === 'GET') {
          // 열람과 감사 기록을 한 트랜잭션으로 묶어 누락을 막는다.
          const detail = transaction(db, () => {
            const row = db.prepare(`SELECT c.report_id AS id, c.status, c.resolution, r.reason, r.created_at AS createdAt
              FROM report_cases c JOIN reports r ON r.id = c.report_id
              WHERE c.report_id = ? AND c.reviewer_id = ?`).get(reportId, reviewer.id);
            if (!row) throw httpError(404, '배정된 사건을 찾을 수 없습니다.');
            db.prepare('INSERT INTO admin_access_audit(id,actor,report_id,action,created_at) VALUES (?,?,?,?,?)').run(uuid(), reviewer.id, reportId, 'view', Date.now());
            return row;
          });
          return send(res, 200, detail);
        }
        if (reportRoute[2] === 'close' && req.method === 'POST') {
          const resolution = String((await readJson(req)).resolution || '').trim();
          if (!resolution || resolution.length > 300) throw httpError(400, '처리 기록은 1~300자로 입력하세요.');
          const result = transaction(db, () => {
            const row = db.prepare('SELECT status, resolution FROM report_cases WHERE report_id = ? AND reviewer_id = ?').get(reportId, reviewer.id);
            if (!row) throw httpError(404, '배정된 사건을 찾을 수 없습니다.');
            if (row.status === 'closed') {
              if (row.resolution !== resolution) throw httpError(409, '이미 다른 내용으로 처리된 사건입니다.');
              return { status: 'closed', duplicate: true };
            }
            db.prepare("UPDATE report_cases SET status = 'closed', resolution = ?, updated_at = ? WHERE report_id = ?").run(resolution, Date.now(), reportId);
            db.prepare('INSERT INTO admin_access_audit(id,actor,report_id,action,created_at) VALUES (?,?,?,?,?)').run(uuid(), reviewer.id, reportId, 'close', Date.now());
            return { status: 'closed', duplicate: false };
          });
          return send(res, 200, result);
        }
        throw httpError(404, '담당자 기능을 찾을 수 없습니다.');
      }
      const user = requireUser(db, req);
      const now = Date.now();
      if (pathname === '/api/me' && req.method === 'GET') return send(res, 200, { user, quota: quota(db, user.id, now) });
      if (pathname === '/api/me' && req.method === 'DELETE') {
        // 탈퇴는 인증 권한을 먼저 없애고 대기·대화·본인 작성 내용을 한 트랜잭션에서 정리한다.
        transaction(db, () => {
          db.prepare('UPDATE users SET token_hash = ?, nickname = ?, intro = ?, deleted_at = ? WHERE id = ? AND deleted_at IS NULL').run(tokenHash(crypto.randomBytes(32).toString('hex')), '탈퇴한 사용자', '', now, user.id);
          db.prepare('DELETE FROM queue WHERE user_id = ?').run(user.id);
          db.prepare("UPDATE rooms SET status = 'ended', ended_at = ? WHERE status <> 'ended' AND (a_id = ? OR b_id = ?)").run(now, user.id, user.id);
          db.prepare("UPDATE requests SET status = 'cancelled' WHERE status = 'pending' AND (requester_id = ? OR receiver_id = ?)").run(user.id, user.id);
          db.prepare('DELETE FROM messages WHERE sender_id = ?').run(user.id);
          db.prepare('DELETE FROM photo_uploads WHERE sender_id = ? OR room_id IN (SELECT id FROM rooms WHERE a_id = ? OR b_id = ?)').run(user.id, user.id, user.id);
          db.prepare('DELETE FROM video_uploads WHERE sender_id = ? OR room_id IN (SELECT id FROM rooms WHERE a_id = ? OR b_id = ?)').run(user.id, user.id, user.id);
          db.prepare('DELETE FROM profile_photos WHERE owner_id = ?').run(user.id);
          db.prepare('DELETE FROM posts WHERE author_id = ?').run(user.id);
          db.prepare('DELETE FROM stories WHERE author_id = ?').run(user.id);
          db.prepare('DELETE FROM reports WHERE reporter_id = ? OR target_id = ?').run(user.id, user.id);
          db.prepare('DELETE FROM blocks WHERE user_id = ? OR target_id = ?').run(user.id, user.id);
        });
        return send(res, 200, { deleted: true });
      }
      // 공개 피드와 스토리는 로그인한 로컬 테스트 사용자끼리만 본다. 랜덤 대화방 미디어와는 저장·권한을 분리한다.
      if (pathname === '/api/posts' && req.method === 'GET') {
        const posts = db.prepare(`SELECT p.id,p.body,p.created_at AS createdAt,p.image IS NOT NULL AS hasImage
          FROM posts p
          ORDER BY p.created_at DESC LIMIT 40`).all();
        return send(res, 200, { posts: posts.map(p => ({ ...p, hasImage: !!p.hasImage })) });
      }

      if (pathname === '/api/posts' && req.method === 'POST') {
        const body = String((await readJson(req)).body || '').trim();
        if (!body || body.length > 500) throw httpError(400, '게시물은 1~500자로 입력하세요.');
        const id = uuid();
        db.prepare('INSERT INTO posts(id,author_id,body,created_at) VALUES (?,?,?,?)').run(id, user.id, body, now);
        return send(res, 201, { id });
      }
      const postRoute = /^\/api\/posts\/([^/]+)(?:\/(image))?$/.exec(pathname);
      if (postRoute && postRoute[2] === 'image' && req.method === 'POST') {
        const declaredType = String(req.headers['content-type'] || '').split(';')[0].trim().toLowerCase();
        const original = await readBytes(req, MAX_PHOTO_UPLOAD);
        if (!original.length) throw httpError(400, '사진이 비어 있습니다.');
        const photo = await normalizePhoto(original, declaredType);
        const changed = db.prepare('UPDATE posts SET image = ?, image_width = ?, image_height = ? WHERE id = ? AND author_id = ?').run(photo.data, photo.width, photo.height, postRoute[1], user.id);
        if (changed.changes !== 1) throw httpError(404, '내 게시물을 찾을 수 없습니다.');
        return send(res, 200, { uploaded: true });
      }
      if (postRoute && postRoute[2] === 'image' && req.method === 'GET') {
        const post = db.prepare(`SELECT p.image FROM posts p JOIN users u ON u.id = p.author_id AND u.deleted_at IS NULL WHERE p.id = ?`).get(postRoute[1]);
        if (!post?.image) throw httpError(404, '게시물 사진을 찾을 수 없습니다.');
        return sendPhoto(res, post.image);
      }
      if (postRoute && !postRoute[2] && req.method === 'DELETE') {
        const changed = db.prepare('DELETE FROM posts WHERE id = ? AND author_id = ?').run(postRoute[1], user.id);
        if (changed.changes !== 1) throw httpError(404, '내 게시물을 찾을 수 없습니다.');
        return send(res, 200, { deleted: true });
      }
      if (pathname === '/api/stories' && req.method === 'GET') {
        db.prepare('DELETE FROM stories WHERE expires_at <= ?').run(now);
        const stories = db.prepare(`SELECT s.id,s.body,s.created_at AS createdAt,s.expires_at AS expiresAt,s.image IS NOT NULL AS hasImage,
          s.author_id = ? AS mine,u.nickname AS author
          FROM stories s JOIN users u ON u.id = s.author_id AND u.deleted_at IS NULL
          WHERE s.expires_at > ? ORDER BY s.created_at DESC LIMIT 30`).all(user.id, now);
        return send(res, 200, { stories: stories.map(s => ({ ...s, mine: !!s.mine, hasImage: !!s.hasImage })) });
      }
      if (pathname === '/api/stories' && req.method === 'POST') {
        const body = String((await readJson(req)).body || '').trim();
        if (!body || body.length > 120) throw httpError(400, '스토리는 1~120자로 입력하세요.');
        const id = uuid();
        db.prepare('INSERT INTO stories(id,author_id,body,created_at,expires_at) VALUES (?,?,?,?,?)').run(id, user.id, body, now, now + DAY_MS);
        return send(res, 201, { id, expiresAt: now + DAY_MS });
      }
      const storyRoute = /^\/api\/stories\/([^/]+)(?:\/(image))?$/.exec(pathname);
      if (storyRoute && storyRoute[2] === 'image' && req.method === 'POST') {
        const declaredType = String(req.headers['content-type'] || '').split(';')[0].trim().toLowerCase();
        const original = await readBytes(req, MAX_PHOTO_UPLOAD);
        if (!original.length) throw httpError(400, '사진이 비어 있습니다.');
        const photo = await normalizePhoto(original, declaredType);
        const changed = db.prepare('UPDATE stories SET image = ?, image_width = ?, image_height = ? WHERE id = ? AND author_id = ? AND expires_at > ?').run(photo.data, photo.width, photo.height, storyRoute[1], user.id, now);
        if (changed.changes !== 1) throw httpError(404, '올릴 수 있는 내 스토리를 찾을 수 없습니다.');
        return send(res, 200, { uploaded: true });
      }
      if (storyRoute && storyRoute[2] === 'image' && req.method === 'GET') {
        const story = db.prepare(`SELECT s.image FROM stories s JOIN users u ON u.id = s.author_id AND u.deleted_at IS NULL WHERE s.id = ? AND s.expires_at > ?`).get(storyRoute[1], now);
        if (!story?.image) throw httpError(404, '스토리 사진을 찾을 수 없습니다.');
        return sendPhoto(res, story.image);
      }
      if (storyRoute && !storyRoute[2] && req.method === 'DELETE') {
        const changed = db.prepare('DELETE FROM stories WHERE id = ? AND author_id = ?').run(storyRoute[1], user.id);
        if (changed.changes !== 1) throw httpError(404, '내 스토리를 찾을 수 없습니다.');
        return send(res, 200, { deleted: true });
      }
      if (pathname === '/api/profile/photos' && req.method === 'POST') {
        const declaredType = String(req.headers['content-type'] || '').split(';')[0].trim().toLowerCase();
        if (!['image/jpeg','image/png'].includes(declaredType)) throw httpError(415, 'JPEG 또는 PNG 사진만 보낼 수 있습니다.');
        const original = await readBytes(req, MAX_PHOTO_UPLOAD);
        if (!original.length) throw httpError(400, '사진이 비어 있습니다.');
        const photo = await normalizePhoto(original, declaredType);
        const id = transaction(db, () => {
          const count = db.prepare('SELECT count(*) AS n FROM profile_photos WHERE owner_id = ?').get(user.id).n;
          if (count >= 5) throw httpError(409, '프로필 사진은 최대 5장입니다. 기존 사진을 삭제하세요.');
          const photoId = uuid();
          db.prepare('INSERT INTO profile_photos(id,owner_id,image,width,height,created_at) VALUES (?,?,?,?,?,?)').run(photoId, user.id, photo.data, photo.width, photo.height, Date.now());
          return photoId;
        });
        return send(res, 201, { id, status: 'pending' });
      }
      if (pathname === '/api/profile/photos' && req.method === 'GET') {
        const photos = db.prepare('SELECT id,status,width,height,created_at AS createdAt FROM profile_photos WHERE owner_id = ? ORDER BY created_at DESC').all(user.id);
        return send(res, 200, { photos });
      }
      const ownProfilePhoto = /^\/api\/profile\/photos\/([^/]+)$/.exec(pathname);
      if (ownProfilePhoto && req.method === 'DELETE') {
        const result = db.prepare('DELETE FROM profile_photos WHERE id = ? AND owner_id = ?').run(ownProfilePhoto[1], user.id);
        if (result.changes !== 1) throw httpError(404, '프로필 사진을 찾을 수 없습니다.');
        return send(res, 200, { deleted: true });
      }
      if (pathname === '/api/profile' && req.method === 'PATCH') {
        const body = await readJson(req);
        const nickname = String(body.nickname || '').trim(), intro = String(body.intro || '').trim();
        if (nickname.length < 1 || nickname.length > 24 || intro.length > 300) throw httpError(400, '닉네임은 1~24자, 소개는 300자 이하로 입력하세요.');
        db.prepare('UPDATE users SET nickname = ?, intro = ? WHERE id = ?').run(nickname, intro, user.id);
        return send(res, 200, { nickname, intro });
      }
      if (pathname === '/api/state' && req.method === 'GET') {
        expireRequests(db, now);
        const waiting = !!db.prepare('SELECT 1 FROM queue WHERE user_id = ?').get(user.id);
        const rooms = db.prepare('SELECT * FROM rooms WHERE (a_id = ? OR b_id = ?) ORDER BY created_at DESC LIMIT 30').all(user.id, user.id).map(r => roomView(db, r, user.id));
        return send(res, 200, { user, quota: quota(db, user.id, now), waiting, rooms });
      }
      if (pathname === '/api/queue' && req.method === 'POST') return send(res, 200, enterQueue(db, user.id, now));
      if (pathname === '/api/queue' && req.method === 'DELETE') {
        db.prepare('DELETE FROM queue WHERE user_id = ?').run(user.id);
        return send(res, 200, { waiting: false });
      }
      const videoFile = /^\/api\/videos\/([^/]+)$/.exec(pathname);
      if (videoFile && req.method === 'GET') {
        const item = db.prepare("SELECT room_id,video FROM video_uploads WHERE id = ? AND status = 'approved'").get(videoFile[1]);
        if (!item) throw httpError(404, '승인된 영상을 찾을 수 없습니다.');
        const current = roomFor(db, item.room_id, user.id);
        const peerId = otherId(current, user.id);
        if (current.status !== 'connected' || db.prepare('SELECT 1 FROM blocks WHERE (user_id = ? AND target_id = ?) OR (user_id = ? AND target_id = ?)').get(user.id, peerId, peerId, user.id)) throw httpError(404, '승인된 영상을 찾을 수 없습니다.');
        return sendVideo(res, item.video);
      }
      if (videoFile && req.method === 'DELETE') {
        const result = db.prepare('DELETE FROM video_uploads WHERE id = ? AND sender_id = ?').run(videoFile[1], user.id);
        if (result.changes !== 1) throw httpError(404, '영상을 찾을 수 없습니다.');
        return send(res, 200, { deleted: true });
      }
      const photoDelete = /^\/api\/photos\/([^/]+)$/.exec(pathname);
      if (photoDelete && req.method === 'GET') {
        const photo = db.prepare(`SELECT p.image, p.room_id FROM photo_uploads p
          WHERE p.id = ? AND p.status = 'approved'`).get(photoDelete[1]);
        if (!photo) throw httpError(404, '승인된 사진을 찾을 수 없습니다.');
        const current = roomFor(db, photo.room_id, user.id);
        if (!['random','connected'].includes(current.status)) throw httpError(404, '승인된 사진을 찾을 수 없습니다.');
        if (db.prepare('SELECT 1 FROM blocks WHERE (user_id = ? AND target_id = ?) OR (user_id = ? AND target_id = ?)').get(user.id, otherId(current, user.id), otherId(current, user.id), user.id)) throw httpError(404, '승인된 사진을 찾을 수 없습니다.');
        return sendPhoto(res, photo.image);
      }
      if (photoDelete && req.method === 'DELETE') {
        const result = db.prepare('DELETE FROM photo_uploads WHERE id = ? AND sender_id = ?').run(photoDelete[1], user.id);
        if (result.changes !== 1) throw httpError(404, '사진을 찾을 수 없습니다.');
        return send(res, 200, { deleted: true });
      }
      const peerProfilePhoto = /^\/api\/rooms\/([^/]+)\/peer\/profile-photos(?:\/([^/]+))?$/.exec(pathname);
      if (peerProfilePhoto && req.method === 'GET') {
        const current = roomFor(db, peerProfilePhoto[1], user.id);
        if (current.status !== 'connected') throw httpError(404, '공개된 프로필 사진을 찾을 수 없습니다.');
        const peerId = otherId(current, user.id);
        if (!db.prepare('SELECT 1 FROM users WHERE id = ? AND deleted_at IS NULL').get(peerId)) throw httpError(404, '공개된 프로필 사진을 찾을 수 없습니다.');
        if (db.prepare('SELECT 1 FROM blocks WHERE (user_id = ? AND target_id = ?) OR (user_id = ? AND target_id = ?)').get(user.id, peerId, peerId, user.id)) throw httpError(404, '공개된 프로필 사진을 찾을 수 없습니다.');
        if (!peerProfilePhoto[2]) {
          const photos = db.prepare("SELECT id,width,height,created_at AS createdAt FROM profile_photos WHERE owner_id = ? AND status = 'approved' ORDER BY created_at DESC LIMIT 5").all(peerId);
          return send(res, 200, { photos });
        }
        const photo = db.prepare("SELECT image FROM profile_photos WHERE id = ? AND owner_id = ? AND status = 'approved'").get(peerProfilePhoto[2], peerId);
        if (!photo) throw httpError(404, '공개된 프로필 사진을 찾을 수 없습니다.');
        return sendPhoto(res, photo.image);
      }
      const roomMatch = /^\/api\/rooms\/([^/]+)(?:\/(messages|photos|videos|request\/decision|request|leave|block|report))?$/.exec(pathname);
      if (!roomMatch) throw httpError(404, '기능을 찾을 수 없습니다.');
      const room = roomFor(db, roomMatch[1], user.id);
      const action = roomMatch[2];
      if (!action && req.method === 'GET') {
        expireRequests(db, now);
        return send(res, 200, roomView(db, room, user.id, true));
      }
      if (action === 'messages' && req.method === 'POST') {
        const input = await readJson(req);
        const body = String(input.body || '').trim();
        const clientId = String(input.clientId || '');
        if (!body || body.length > 1000) throw httpError(400, '메시지는 1~1000자로 입력하세요.');
        if (!/^[a-f0-9-]{36}$/i.test(clientId)) throw httpError(400, '메시지 식별자가 올바르지 않습니다.');
        const result = transaction(db, () => {
          const current = roomFor(db, room.id, user.id);
          const existing = db.prepare('SELECT id, body FROM messages WHERE room_id = ? AND sender_id = ? AND client_id = ?').get(room.id, user.id, clientId);
          if (existing) {
            if (existing.body !== body) throw httpError(409, '같은 메시지 식별자로 다른 본문을 보낼 수 없습니다.');
            return { id: existing.id, duplicate: true };
          }
          if (!['random','connected'].includes(current.status)) throw httpError(409, '종료된 대화에는 메시지를 보낼 수 없습니다.');
          const id = uuid();
          db.prepare('INSERT INTO messages(id,room_id,sender_id,client_id,body,created_at) VALUES (?,?,?,?,?,?)').run(id, room.id, user.id, clientId, body, now);
          return { id, duplicate: false };
        });
        return send(res, result.duplicate ? 200 : 201, result);
      }
      if (action === 'photos' && req.method === 'POST') {
        if (!['random', 'connected'].includes(room.status)) throw httpError(409, '종료된 대화에는 사진을 보낼 수 없습니다.');
        const declaredType = String(req.headers['content-type'] || '').split(';')[0].trim().toLowerCase();
        if (!['image/jpeg', 'image/png'].includes(declaredType)) throw httpError(415, 'JPEG 또는 PNG 사진만 보낼 수 있습니다.');
        const original = await readBytes(req, MAX_PHOTO_UPLOAD);
        if (!original.length) throw httpError(400, '사진이 비어 있습니다.');
        const photo = await normalizePhoto(original, declaredType);
        const id = transaction(db, () => {
          const current = roomFor(db, room.id, user.id);
          if (!['random', 'connected'].includes(current.status)) throw httpError(409, '종료된 대화에는 사진을 보낼 수 없습니다.');
          const photoId = uuid();
          db.prepare('INSERT INTO photo_uploads(id,room_id,sender_id,image,width,height,created_at) VALUES (?,?,?,?,?,?,?)').run(photoId, room.id, user.id, photo.data, photo.width, photo.height, Date.now());
          return photoId;
        });
        return send(res, 201, { id, status: 'pending' });
      }
      if (action === 'photos' && req.method === 'GET') {
        if (!['random', 'connected'].includes(room.status)) throw httpError(409, '종료된 대화의 사진은 볼 수 없습니다.');
        const photos = db.prepare("SELECT id, status, width, height, created_at AS createdAt, sender_id = ? AS mine FROM photo_uploads WHERE room_id = ? AND (sender_id = ? OR status = 'approved') ORDER BY created_at DESC LIMIT 30").all(user.id, room.id, user.id);
        return send(res, 200, { photos });
      }
      if (action === 'videos' && req.method === 'POST') {
        if (room.status !== 'connected') throw httpError(409, '계속 대화 수락 후에만 영상을 보낼 수 있습니다.');
        const declaredType = String(req.headers['content-type'] || '').split(';')[0].trim().toLowerCase();
        if (declaredType !== 'video/mp4') throw httpError(415, 'MP4 영상만 보낼 수 있습니다.');
        const original = await readBytes(req, MAX_VIDEO_UPLOAD, '영상');
        if (!original.length) throw httpError(400, '영상이 비어 있습니다.');
        if (activeVideoConversions >= 1) throw httpError(429, '다른 영상 변환이 끝난 뒤 다시 시도하세요.');
        activeVideoConversions++;
        let video;
        try { video = await normalizeVideo(original, declaredType); }
        finally { activeVideoConversions--; }
        const id = transaction(db, () => {
          const current = roomFor(db, room.id, user.id);
          if (current.status !== 'connected') throw httpError(409, '계속 대화 수락 후에만 영상을 보낼 수 있습니다.');
          const count = db.prepare('SELECT count(*) AS n FROM video_uploads WHERE room_id = ?').get(room.id).n;
          if (count >= 10) throw httpError(409, '한 대화방에는 영상을 최대 10개까지 보낼 수 있습니다.');
          const videoId = uuid();
          db.prepare('INSERT INTO video_uploads(id,room_id,sender_id,video,width,height,duration,created_at) VALUES (?,?,?,?,?,?,?,?)')
            .run(videoId, room.id, user.id, video.data, video.width, video.height, video.duration, Date.now());
          return videoId;
        });
        return send(res, 201, { id, status: 'pending' });
      }
      if (action === 'videos' && req.method === 'GET') {
        if (room.status !== 'connected') throw httpError(404, '공개된 영상을 찾을 수 없습니다.');
        const videos = db.prepare("SELECT id,status,width,height,duration,created_at AS createdAt,sender_id = ? AS mine FROM video_uploads WHERE room_id = ? AND (sender_id = ? OR status = 'approved') ORDER BY created_at DESC LIMIT 30").all(user.id, room.id, user.id);
        return send(res, 200, { videos });
      }
      if (action === 'request' && req.method === 'POST') {
        const result = transaction(db, () => {
          expireRequests(db, now);
          const current = roomFor(db, room.id, user.id);
          if (current.status !== 'random') throw httpError(409, '랜덤 대화에서만 요청할 수 있습니다.');
          const existing = db.prepare("SELECT id, requester_id FROM requests WHERE room_id = ? AND status = 'pending'").get(room.id);
          if (existing) throw httpError(409, existing.requester_id === user.id ? '이미 요청을 보냈습니다.' : '상대의 요청을 먼저 확인하세요.');
          const q = quota(db, user.id, now);
          if (!q.available) throw httpError(409, '오늘 사용할 수 있는 무료 연결권이 없습니다.');
          const id = uuid(), peerId = otherId(room, user.id);
          const expiresAt = Math.min(now + REQUEST_TTL_MS, nextKstMidnight(now));
          db.prepare("INSERT INTO requests(id,room_id,requester_id,receiver_id,status,quota_day,expires_at,created_at) VALUES (?,?,?,?,'pending',?,?,?)").run(id, room.id, user.id, peerId, q.day, expiresAt, now);
          return { id, expiresAt };
        });
        return send(res, 201, result);
      }
      if ((action === 'request' && req.method === 'PATCH') || (action === 'request/decision' && req.method === 'POST')) {
        const body = await readJson(req);
        if (!['accept','reject','cancel'].includes(body.decision)) throw httpError(400, '요청 처리 종류가 잘못되었습니다.');
        const result = transaction(db, () => {
          expireRequests(db, now);
          const current = roomFor(db, room.id, user.id);
          const request = db.prepare('SELECT * FROM requests WHERE room_id = ? ORDER BY created_at DESC LIMIT 1').get(room.id);
          if (!request) throw httpError(409, '처리할 요청이 없습니다.');
          if (body.decision === 'cancel' && request.requester_id !== user.id) throw httpError(403, '보낸 사람만 취소할 수 있습니다.');
          if (body.decision !== 'cancel' && request.receiver_id !== user.id) throw httpError(403, '받은 사람만 처리할 수 있습니다.');
          const status = { accept: 'accepted', reject: 'rejected', cancel: 'cancelled' }[body.decision];
          if (request.status === status) return { status, duplicate: true };
          if (request.status !== 'pending') throw httpError(409, '이미 처리되거나 만료된 요청입니다.');
          if (current.status !== 'random') throw httpError(409, '랜덤 대화가 이미 끝났습니다.');
          db.prepare('UPDATE requests SET status = ? WHERE id = ?').run(status, request.id);
          if (status === 'accepted') db.prepare("UPDATE rooms SET status = 'connected' WHERE id = ?").run(room.id);
          return { status, duplicate: false };
        });
        return send(res, 200, result);
      }
      if (action === 'leave' && req.method === 'POST') {
        transaction(db, () => {
          db.prepare('DELETE FROM photo_uploads WHERE room_id = ?').run(room.id);
          db.prepare('DELETE FROM video_uploads WHERE room_id = ?').run(room.id);
          db.prepare("UPDATE rooms SET status = 'ended', ended_at = ? WHERE id = ? AND status <> 'ended'").run(now, room.id);
          db.prepare("UPDATE requests SET status = 'cancelled' WHERE room_id = ? AND status = 'pending'").run(room.id);
        });
        return send(res, 200, { status: 'ended' });
      }
      if (action === 'block' && req.method === 'POST') {
        transaction(db, () => {
          db.prepare('DELETE FROM photo_uploads WHERE room_id = ?').run(room.id);
          db.prepare('DELETE FROM video_uploads WHERE room_id = ?').run(room.id);
          db.prepare('INSERT OR IGNORE INTO blocks(user_id,target_id,created_at) VALUES (?,?,?)').run(user.id, otherId(room, user.id), now);
          db.prepare("UPDATE rooms SET status = 'ended', ended_at = ? WHERE id = ? AND status <> 'ended'").run(now, room.id);
          db.prepare("UPDATE requests SET status = 'cancelled' WHERE room_id = ? AND status = 'pending'").run(room.id);
        });
        return send(res, 200, { blocked: true });
      }
      if (action === 'report' && req.method === 'POST') {
        const reason = String((await readJson(req)).reason || '').trim();
        if (!reason || reason.length > 500) throw httpError(400, '신고 사유는 1~500자로 입력하세요.');
        const id = transaction(db, () => {
          const reportId = uuid();
          db.prepare('INSERT INTO reports(id,room_id,reporter_id,target_id,reason,created_at) VALUES (?,?,?,?,?,?)').run(reportId, room.id, user.id, otherId(room, user.id), reason, now);
          db.prepare('INSERT INTO report_cases(report_id,updated_at) VALUES (?,?)').run(reportId, now);
          return reportId;
        });
        return send(res, 201, { reported: true, id });
      }
      throw httpError(404, '기능을 찾을 수 없습니다.');
    } catch (error) {
      if (!res.headersSent) send(res, error.status || 500, { error: error.status ? error.message : '서버 오류가 발생했습니다.' });
      if (!error.status) console.error(error);
    }
  });
  return { server, db, close: () => new Promise(resolve => server.close(() => { db.close(); resolve(); })) };
}

if (require.main === module) {
  const app = createApp();
  const port = Number(process.env.PORT || 3000);
  const host = process.env.RANDOMCHAT_HOST || '127.0.0.1';
  const parts = host.split('.').map(Number);
  const privateAddress = parts.length === 4 && parts.every((part, index) => /^\d{1,3}$/.test(host.split('.')[index]) && part >= 0 && part <= 255)
    && (parts[0] === 10 || (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) || (parts[0] === 192 && parts[1] === 168));
  if (host !== '127.0.0.1' && !privateAddress) {
    console.error('RANDOMCHAT_HOST에는 PC의 사설 IPv4 주소만 입력하세요.');
    app.db.close();
    process.exitCode = 1;
  } else {
    app.server.listen(port, host, () => console.log(`RandomChat: http://${host}:${port}`));
  }
}

module.exports = { createApp, createDatabase, createReviewer, assignReport, assignPhoto, assignProfilePhoto, assignVideo, kstDay };


