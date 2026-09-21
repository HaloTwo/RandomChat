'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');

const DAY_MS = 24 * 60 * 60 * 1000;

function cutoffForDays(days, now = Date.now()) {
  if (!Number.isSafeInteger(days) || days < 2 || days > 3650) throw Error('보관 일수는 2~3650 사이의 정수로 입력하세요.');
  return now - days * DAY_MS;
}

// 신고가 있는 방은 처리 상태와 관계없이 보존 대상으로 제외한다.
function eligibleRooms(db, cutoff) {
  return db.prepare(`SELECT r.id FROM rooms r WHERE r.status = 'ended' AND r.ended_at IS NOT NULL
    AND r.ended_at < ? AND NOT EXISTS (SELECT 1 FROM reports WHERE room_id = r.id)
    ORDER BY r.ended_at, r.id`).all(cutoff).map(row => row.id);
}

function previewEndedRooms(db, cutoff) {
  const ids = eligibleRooms(db, cutoff);
  const counts = { rooms: ids.length, messages: 0, requests: 0, photos: 0, videos: 0, heldForReports: 0 };
  const countFor = (table, roomId) => db.prepare(`SELECT count(*) AS n FROM ${table} WHERE room_id = ?`).get(roomId).n;
  for (const id of ids) {
    counts.messages += countFor('messages', id);
    counts.requests += countFor('requests', id);
    counts.photos += countFor('photo_uploads', id);
    counts.videos += countFor('video_uploads', id);
  }
  counts.heldForReports = db.prepare(`SELECT count(*) AS n FROM rooms r WHERE r.status = 'ended'
    AND r.ended_at IS NOT NULL AND r.ended_at < ? AND EXISTS (SELECT 1 FROM reports WHERE room_id = r.id)`).get(cutoff).n;
  return counts;
}

// 백업을 만든 뒤 한 트랜잭션에서 관련 기록을 정리한다. 최신 상태를 다시 조회해 신규 신고 방을 피한다.
function purgeEndedRooms(db, cutoff, backupPath) {
  const target = path.resolve(backupPath);
  if (fs.existsSync(target)) throw Error('백업 파일이 이미 있습니다. 다른 경로를 입력하세요.');
  fs.mkdirSync(path.dirname(target), { recursive: true });
  db.prepare('VACUUM INTO ?').run(target);
  const snapshot = new DatabaseSync(target);
  try {
    if (snapshot.prepare('PRAGMA integrity_check').get().integrity_check !== 'ok') throw Error('백업 무결성 검사에 실패해 삭제를 중단했습니다.');
  } finally { snapshot.close(); }
  db.exec('BEGIN IMMEDIATE');
  try {
    const ids = eligibleRooms(db, cutoff);
    const deleted = { rooms: 0, messages: 0, requests: 0, photos: 0, videos: 0 };
    for (const id of ids) {
      for (const [table, key] of [['messages','messages'],['requests','requests'],['photo_uploads','photos'],['video_uploads','videos']]) {
        deleted[key] += db.prepare(`DELETE FROM ${table} WHERE room_id = ?`).run(id).changes;
      }
      deleted.rooms += db.prepare("DELETE FROM rooms WHERE id = ? AND status = 'ended' AND NOT EXISTS (SELECT 1 FROM reports WHERE room_id = ?)").run(id, id).changes;
    }
    db.exec('COMMIT');
    return { deleted, backupPath: target };
  } catch (error) {
    db.exec('ROLLBACK');
    throw error;
  }
}

if (require.main === module) {
  const [command, daysText, flag] = process.argv.slice(2);
  let db;
  try {
    if (!['preview-ended', 'purge-ended'].includes(command) || !/^\d+$/.test(daysText || '')) {
      throw Error('사용법: node retention.js preview-ended <보관 일수> | purge-ended <보관 일수> --apply');
    }
    if (command === 'purge-ended' && flag !== '--apply') throw Error('삭제하려면 --apply를 명시하세요.');
    if (command === 'preview-ended' && flag) throw Error('미리보기에는 추가 옵션이 필요하지 않습니다.');
    const cutoff = cutoffForDays(Number(daysText));
    const dbPath = path.join(__dirname, 'data', 'chat.sqlite');
    if (!fs.existsSync(dbPath)) throw Error('기존 채팅 DB가 없습니다. 먼저 서버를 실행해 DB를 만드세요.');
    db = new DatabaseSync(dbPath);
    db.exec('PRAGMA foreign_keys = ON');
    const preview = previewEndedRooms(db, cutoff);
    console.log(JSON.stringify({ cutoff: new Date(cutoff).toISOString(), ...preview }, null, 2));
    if (command === 'purge-ended') {
      const backup = path.join(__dirname, 'data', 'backups', `chat-before-purge-${Date.now()}.sqlite`);
      console.log(JSON.stringify(purgeEndedRooms(db, cutoff, backup), null, 2));
    }
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  } finally {
    if (db) db.close();
  }
}

module.exports = { cutoffForDays, previewEndedRooms, purgeEndedRooms };
