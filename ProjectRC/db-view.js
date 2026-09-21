'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');

const dbPath = path.join(__dirname, 'data', 'chat.sqlite');
const command = process.argv[2] || 'summary';
const tables = ['users', 'rooms', 'messages', 'requests', 'reports', 'photo_uploads', 'profile_photos', 'video_uploads', 'posts', 'stories'];

function help() {
  console.log('사용법: node db-view.js [summary|users|rooms|messages <방 ID>|posts|stories|schema|sql "SELECT ..."]');
  console.log('모든 조회는 읽기 전용이며 목록은 최근 20건까지만 표시합니다.');
}

function compact(value) {
  if (value instanceof Uint8Array) return `[파일 ${value.length}바이트]`;
  if (typeof value === 'string' && value.length > 120) return `${value.slice(0, 120)}…`;
  return value;
}

function printRows(rows) {
  const list = rows.map(row => Object.fromEntries(Object.entries(row).map(([key, value]) => [key, compact(value)])));
  if (!list.length) console.log('조회 결과가 없습니다.');
  else console.table(list);
  if (list.length === 20) console.log('최대 20건만 표시했습니다.');
}

if (!fs.existsSync(dbPath)) {
  console.error('DB가 없습니다. start.cmd 또는 start-phone.cmd로 서버를 한 번 실행하세요.');
  process.exitCode = 1;
} else {
  const db = new DatabaseSync(dbPath, { readOnly: true });
  try {
    db.exec('PRAGMA query_only = ON');
    console.log(`DB 위치: ${dbPath}`);
    if (command === 'summary') {
      for (const table of tables) {
        const count = db.prepare(`SELECT count(*) AS count FROM ${table}`).get().count;
        console.log(`${table}: ${count}건`);
      }
      console.log('본문·신고 사유·토큰은 기본 조회에서 출력하지 않습니다. DB 파일은 공유하지 마세요.');
    } else if (command === 'users') {
      printRows(db.prepare(`SELECT id, nickname, datetime(created_at / 1000, 'unixepoch', '+9 hours') AS created_kst,
        CASE WHEN deleted_at IS NULL THEN '활성' ELSE '삭제됨' END AS state
        FROM users ORDER BY created_at DESC LIMIT 20`).all());
    } else if (command === 'rooms') {
      printRows(db.prepare(`SELECT r.id, r.status, a.nickname AS user_a, b.nickname AS user_b,
        datetime(r.created_at / 1000, 'unixepoch', '+9 hours') AS created_kst
        FROM rooms r JOIN users a ON a.id = r.a_id JOIN users b ON b.id = r.b_id
        ORDER BY r.created_at DESC LIMIT 20`).all());
    } else if (command === 'messages') {
      const roomId = process.argv[3] || '';
      if (!/^[a-f0-9-]{36}$/i.test(roomId)) throw new Error('node db-view.js messages <rooms 목록의 방 ID> 형식으로 입력하세요.');
      printRows(db.prepare(`SELECT m.id, u.nickname AS sender, m.body,
        datetime(m.created_at / 1000, 'unixepoch', '+9 hours') AS created_kst
        FROM messages m JOIN users u ON u.id = m.sender_id WHERE m.room_id = ?
        ORDER BY m.created_at DESC LIMIT 20`).all(roomId));
      console.log('메시지 본문이 표시됩니다. 화면이나 출력을 외부에 공유하지 마세요.');
    } else if (command === 'posts') {
      printRows(db.prepare(`SELECT p.id, u.nickname AS author, p.body, p.image IS NOT NULL AS has_image,
        datetime(p.created_at / 1000, 'unixepoch', '+9 hours') AS created_kst
        FROM posts p JOIN users u ON u.id = p.author_id ORDER BY p.created_at DESC LIMIT 20`).all());
      console.log('게시물 본문이 표시됩니다. 화면이나 출력을 외부에 공유하지 마세요.');
    } else if (command === 'stories') {
      printRows(db.prepare(`SELECT s.id, u.nickname AS author, s.body, s.image IS NOT NULL AS has_image,
        datetime(s.created_at / 1000, 'unixepoch', '+9 hours') AS created_kst,
        datetime(s.expires_at / 1000, 'unixepoch', '+9 hours') AS expires_kst
        FROM stories s JOIN users u ON u.id = s.author_id ORDER BY s.created_at DESC LIMIT 20`).all());
      console.log('스토리 본문이 표시됩니다. 만료된 항목은 다음 스토리 조회 때 서버가 정리합니다.');
    } else if (command === 'schema') {
      for (const table of tables) {
        const columns = db.prepare(`PRAGMA table_info(${table})`).all().map(column => `${column.name} ${column.type}`);
        console.log(`${table}: ${columns.join(', ')}`);
      }
    } else if (command === 'sql') {
      const query = process.argv.slice(3).join(' ').trim();
      if (!/^select\s/i.test(query)) throw new Error('sql 명령에는 SELECT 조회문만 입력하세요.');
      const statement = db.prepare(query);
      const rows = [];
      for (const row of statement.iterate()) {
        rows.push(row);
        if (rows.length >= 20) break;
      }
      printRows(rows);
      console.log('직접 입력한 SELECT 결과에는 개인정보가 포함될 수 있습니다. 출력을 공유하지 마세요.');
    } else {
      help();
      process.exitCode = 1;
    }
  } catch (error) {
    console.error(`조회 실패: ${error.message}`);
    help();
    process.exitCode = 1;
  } finally { db.close(); }
}
