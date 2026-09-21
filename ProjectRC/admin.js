'use strict';

const path = require('node:path');
const fs = require('node:fs');
const { createDatabase, createReviewer, assignReport, assignPhoto, assignProfilePhoto, assignVideo } = require('./server');

const [command, ...args] = process.argv.slice(2);
const db = createDatabase(path.join(__dirname, 'data', 'chat.sqlite'));

try {
  if (command === 'create-reviewer-to-file') {
    const reviewer = createReviewer(db, args.join(' '));
    const privateDir = path.join(__dirname, 'private');
    fs.mkdirSync(privateDir, { recursive: true, mode: 0o700 });
    const note = path.join(privateDir, 'LOCAL_TOKENS.txt');
    if (!fs.existsSync(note)) fs.writeFileSync(note,
      '모먼트 로컬 테스트 토큰 메모\r\n이 파일은 private 폴더에 있으며 Git에서 제외됩니다. 다른 사람에게 보내지 마세요.\r\n사용자 토큰: Android 앱이 테스트 계정 생성 시 메모리에 보관하며 API 호출에 자동 사용합니다.\r\n운영 담당자 토큰: 아래 값을 PC의 서버 주소 뒤 /review 에 입력합니다.\r\n\r\n', { mode: 0o600 });
    fs.appendFileSync(note, `담당자: ${reviewer.id} (${args.join(' ')})\r\n토큰: ${reviewer.token}\r\n\r\n`);
    console.log(`담당자 ID: ${reviewer.id}`);
    console.log(`토큰을 ${note}에 저장했습니다. 이 파일은 PC에서만 보관하세요.`);
  } else if (command === 'create-reviewer') {
    const reviewer = createReviewer(db, args.join(' '));
    console.log(`담당자 ID: ${reviewer.id}`);
    console.log(`1회 표시 토큰: ${reviewer.token}`);
    console.log('토큰을 코드·노션·Git에 저장하지 마세요. 분실 시 새 담당자를 만들고 기존 담당자를 폐기하세요.');
  } else if (command === 'list-reviewers') {
    const rows = db.prepare('SELECT id, label, active FROM admin_accounts ORDER BY created_at').all();
    for (const row of rows) console.log(`${row.id}\t${row.label}\t${row.active ? '활성' : '폐기'}`);
  } else if (command === 'list-reports') {
    const rows = db.prepare('SELECT report_id, status, reviewer_id FROM report_cases ORDER BY updated_at DESC').all();
    for (const row of rows) console.log(`${row.report_id}\t${row.status}\t${row.reviewer_id || '미배정'}`);
  } else if (command === 'list-photos') {
    const rows = db.prepare("SELECT id, status, reviewer_id, created_at FROM photo_uploads WHERE status = 'pending' ORDER BY created_at DESC").all();
    for (const row of rows) console.log(`${row.id}\t${row.status}\t${row.reviewer_id || '미배정'}\t${new Date(row.created_at).toLocaleString('ko-KR')}`);
  } else if (command === 'assign-photo') {
    if (args.length !== 2) throw Error('사용법: node admin.js assign-photo <사진 ID> <담당자 ID>');
    assignPhoto(db, args[0], args[1]);
    console.log('사진을 담당자에게 배정했습니다.');
  } else if (command === 'list-profile-photos') {
    const rows = db.prepare("SELECT id,status,reviewer_id,created_at FROM profile_photos WHERE status = 'pending' ORDER BY created_at DESC").all();
    for (const row of rows) console.log(`${row.id}\t${row.status}\t${row.reviewer_id || '미배정'}\t${new Date(row.created_at).toLocaleString('ko-KR')}`);
  } else if (command === 'assign-profile-photo') {
    if (args.length !== 2) throw Error('사용법: node admin.js assign-profile-photo <사진 ID> <담당자 ID>');
    assignProfilePhoto(db, args[0], args[1]);
    console.log('프로필 사진을 담당자에게 배정했습니다.');
  } else if (command === 'list-videos') {
    const rows = db.prepare("SELECT id,status,reviewer_id,created_at FROM video_uploads WHERE status = 'pending' ORDER BY created_at DESC").all();
    for (const row of rows) console.log(`${row.id}\t${row.status}\t${row.reviewer_id || '미배정'}\t${new Date(row.created_at).toLocaleString('ko-KR')}`);
  } else if (command === 'assign-video') {
    if (args.length !== 2) throw Error('사용법: node admin.js assign-video <영상 ID> <담당자 ID>');
    assignVideo(db, args[0], args[1]);
    console.log('영상을 담당자에게 배정했습니다.');
  } else if (command === 'assign') {
    if (args.length !== 2) throw Error('사용법: node admin.js assign <신고 ID> <담당자 ID>');
    assignReport(db, args[0], args[1]);
    console.log('사건을 담당자에게 배정했습니다.');
  } else if (command === 'revoke-reviewer') {
    if (args.length !== 1) throw Error('사용법: node admin.js revoke-reviewer <담당자 ID>');
    const result = db.prepare('UPDATE admin_accounts SET active = 0 WHERE id = ? AND active = 1').run(args[0]);
    if (result.changes !== 1) throw Error('활성 담당자를 찾을 수 없습니다.');
    console.log('담당자 토큰을 폐기했습니다.');
  } else {
    console.log('사용법:');
    console.log('  node admin.js create-reviewer <담당자 이름>');
    console.log('  node admin.js create-reviewer-to-file <담당자 이름>');
    console.log('  node admin.js list-reviewers');
    console.log('  node admin.js list-reports');
    console.log('  node admin.js assign <신고 ID> <담당자 ID>');
    console.log('  node admin.js list-photos');
    console.log('  node admin.js assign-photo <사진 ID> <담당자 ID>');
    console.log('  node admin.js list-profile-photos');
    console.log('  node admin.js assign-profile-photo <사진 ID> <담당자 ID>');
    console.log('  node admin.js list-videos');
    console.log('  node admin.js assign-video <영상 ID> <담당자 ID>');
    console.log('  node admin.js revoke-reviewer <담당자 ID>');
    process.exitCode = 1;
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  db.close();
}
