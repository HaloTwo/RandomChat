'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');

const root = __dirname;
const source = path.join(root, 'data', 'chat.sqlite');
const backupDir = path.join(root, 'data', 'backups');

if (!fs.existsSync(source)) throw new Error('백업할 data/chat.sqlite 파일이 없습니다. 서버를 한 번 실행하세요.');
fs.mkdirSync(backupDir, { recursive: true });

// WAL을 본 파일에 반영한 뒤 새 파일로 복사해 실행 중인 서버의 일관된 시점을 보관한다.
const db = new DatabaseSync(source);
try {
  db.exec('PRAGMA wal_checkpoint(FULL)');
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  const target = path.join(backupDir, `chat-${stamp}.sqlite`);
  fs.copyFileSync(source, target, fs.constants.COPYFILE_EXCL);
  console.log(`백업 완료: ${target}`);
} finally {
  db.close();
}
