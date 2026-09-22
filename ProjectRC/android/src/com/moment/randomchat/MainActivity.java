package com.moment.randomchat;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.VideoView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_PHOTO = 1;
    private static final int PICK_PROFILE_PHOTO = 2;
    private static final int PICK_VIDEO = 3;
    private static final int PICK_POST_IMAGE = 4;
    private static final int PICK_STORY_IMAGE = 5;
    private static final int MAX_UPLOAD = 5 * 1024 * 1024;
    private static final int MAX_VIDEO_UPLOAD = 20 * 1024 * 1024;
    private static final int MAX_VIDEO_DOWNLOAD = 8 * 1024 * 1024;
    private static final String PREF_SERVER_ADDRESS = "serverAddress";
    private static final String PREF_TOKEN = "sessionToken";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler storyHandler = new Handler();
    private volatile String token = "";
    private volatile String roomId = "";
    private volatile String serverAddress = "http://192.168.0.2:3000";
    private volatile boolean foreground;
    private EditText serverInput, nicknameInput, messageInput, reportInput, storyInput, postTitleInput, postInput;
    private Spinner genderInput;
    private TextView statusView, pageTitle, pageSubtitle;
    private LinearLayout roomsView, messagesView, photosView, videosView, ownProfilePhotosView, peerProfilePhotosView, storiesView, postsView;
    private ImageView photoView;
    private VideoView videoView;
    private File videoFile;
    private String pendingSocialKind = "";
    private String pendingSocialBody = "";
    private String pendingSocialTitle = "";
    private String pendingSocialId = "";
    private String pendingSocialToken = "";
    private JSONArray storySequence = new JSONArray();
    private LinearLayout loungeSection, chatSection, profileSection;
    private Button loungeTab, chatTab, profileTab;
    private volatile long lastTypingAt;
    private static final int COLOR_BG = Color.rgb(23, 23, 29);
    private static final int COLOR_PANEL = Color.rgb(36, 36, 45);
    private static final int COLOR_FIELD = Color.rgb(48, 48, 58);
    private static final int COLOR_TEXT = Color.rgb(245, 244, 247);
    private static final int COLOR_MUTED = Color.rgb(174, 171, 182);
    private static final int COLOR_BLUE = Color.rgb(96, 216, 255);
    private static final int COLOR_PINK = Color.rgb(255, 154, 193);

    private interface Work { void run() throws Exception; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // 로컬 대화·사진 화면의 캡처와 최근 앱 미리보기를 OS에 억제 요청한다.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        token = getPreferences(MODE_PRIVATE).getString(PREF_TOKEN, "");
        File[] oldPreviews = getCacheDir().listFiles((dir, name) -> name.startsWith("moment-preview-") && name.endsWith(".mp4"));
        if (oldPreviews != null) for (File oldPreview : oldPreviews) oldPreview.delete();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(COLOR_BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        page.setPadding(pad, pad, pad, pad);
        scroll.addView(page);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(screen);
        LinearLayout hero = hero(page);
        pageTitle = new TextView(this);
        pageTitle.setText("모먼트");
        pageTitle.setTextColor(Color.WHITE);
        pageTitle.setTextSize(32);
        pageTitle.setTypeface(null, Typeface.BOLD);
        hero.addView(pageTitle);
        pageSubtitle = new TextView(this);
        pageSubtitle.setText("익명으로 남기고, 대화로 연결돼요");
        pageSubtitle.setTextColor(COLOR_MUTED);
        pageSubtitle.setTextSize(16);
        pageSubtitle.setPadding(0, dp(4), 0, dp(14));
        hero.addView(pageSubtitle);
        statusView = new TextView(this);
        statusView.setText("서버와 연결 전");
        statusView.setTextColor(COLOR_BG);
        statusView.setTextSize(14);
        statusView.setTypeface(null, Typeface.BOLD);
        statusView.setPadding(dp(12), dp(8), dp(12), dp(8));
        statusView.setBackground(round(COLOR_BLUE, 14));
        hero.addView(statusView);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(0, dp(16), 0, dp(8));
        page.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        loungeTab = tabButton(navigation, "게시물", () -> selectTab("lounge"));
        chatTab = tabButton(navigation, "내 대화", () -> selectTab("chat"));
        profileTab = tabButton(navigation, "내 활동", () -> selectTab("profile"));

        loungeSection = section(page);
        heading(loungeSection, "지금의 순간", 26);
        label(loungeSection, "사진과 함께 남기는 24시간 스토리");
        storyInput = new EditText(this);
        postTitleInput = new EditText(this);
        postInput = new EditText(this);
        HorizontalScrollView storyScroll = new HorizontalScrollView(this);
        storyScroll.setHorizontalScrollBarEnabled(false);
        storiesView = new LinearLayout(this);
        storiesView.setOrientation(LinearLayout.HORIZONTAL);
        storyScroll.addView(storiesView);
        loungeSection.addView(storyScroll, new LinearLayout.LayoutParams(-1, dp(118)));
        button(loungeSection, "＋ 게시물 또는 스토리 작성", this::showComposerChoice);
        heading(loungeSection, "게시물", 22);
        postsView = column(loungeSection);

        chatSection = section(page);
        heading(chatSection, "내 대화", 26);
        label(chatSection, "진행 중이거나 최근에 끝난 대화를 확인합니다.");
        LinearLayout chatTools = new LinearLayout(this); chatTools.setOrientation(LinearLayout.HORIZONTAL);
        Button visitors = compactButton("프로필 방문", this::showProfileVisitors);
        Button inbox = compactButton("받은 쪽지", this::showInbox);
        chatTools.addView(visitors, new LinearLayout.LayoutParams(0, dp(44), 1f));
        chatTools.addView(inbox, new LinearLayout.LayoutParams(0, dp(44), 1f));
        chatSection.addView(chatTools, new LinearLayout.LayoutParams(-1, -2));
        button(chatSection, "랜덤 대화 찾기", this::showRandomMatchDialog);
        button(chatSection, "목록 새로고침", this::refresh);
        heading(chatSection, "내 대화방", 20);
        roomsView = column(chatSection);
        heading(chatSection, "현재 대화", 20);
        messagesView = column(chatSection);
        heading(chatSection, "상대 프로필 사진", 20);
        label(chatSection, "연결을 수락한 뒤에만 표시됩니다.");
        peerProfilePhotosView = column(chatSection);
        messageInput = input(chatSection, "보낼 메시지", "");
        messageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (roomId.isEmpty() || text.toString().trim().isEmpty() || System.currentTimeMillis() - lastTypingAt < 1500) return;
                lastTypingAt = System.currentTimeMillis();
                background(() -> json("POST", "/api/rooms/" + roomId + "/typing", new JSONObject().put("typing", true)));
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        button(chatSection, "메시지 보내기", () -> {
            String body = messageInput.getText().toString().trim();
            background(() -> {
            String id = requireRoom();
            if (body.isEmpty()) throw new IllegalArgumentException("메시지를 입력하세요.");
            json("POST", "/api/rooms/" + id + "/messages", new JSONObject().put("body", body).put("clientId", UUID.randomUUID().toString()));
            json("POST", "/api/rooms/" + id + "/typing", new JSONObject().put("typing", false));
            runOnUiThread(() -> messageInput.setText(""));
            openRoom(id);
            });
        });
        button(chatSection, "계속 대화 요청", () -> roomAction("POST", "request", null));
        button(chatSection, "요청 수락", () -> roomAction("POST", "request/decision", "accept"));
        button(chatSection, "요청 거절", () -> roomAction("POST", "request/decision", "reject"));
        button(chatSection, "대화 종료", () -> roomAction("POST", "leave", null));
        button(chatSection, "상대 차단", () -> roomAction("POST", "block", null));
        reportInput = input(chatSection, "신고 사유", "");
        button(chatSection, "신고 접수", () -> {
            String reason = reportInput.getText().toString().trim();
            background(() -> {
            if (reason.isEmpty()) throw new IllegalArgumentException("신고 사유를 입력하세요.");
            json("POST", "/api/rooms/" + requireRoom() + "/report", new JSONObject().put("reason", reason));
            notice("신고를 접수했습니다.");
            });
        });
        heading(chatSection, "사진", 20);
        label(chatSection, "사진은 담당자 승인 후 상대에게 보입니다.");
        button(chatSection, "사진 선택·업로드", this::pickPhoto);
        photosView = column(chatSection);
        heading(chatSection, "영상", 20);
        label(chatSection, "20초·720p·20MB 이하 MP4만 올릴 수 있습니다.");
        button(chatSection, "영상 선택·업로드", this::pickVideo);
        videosView = column(chatSection);
        photoView = new ImageView(this);
        photoView.setAdjustViewBounds(true);
        chatSection.addView(photoView, new LinearLayout.LayoutParams(-1, dp(300)));
        button(chatSection, "사진 보기 닫기", () -> photoView.setImageDrawable(null));
        videoView = new VideoView(this);
        videoView.setVisibility(View.GONE);
        chatSection.addView(videoView, new LinearLayout.LayoutParams(-1, dp(240)));
        videoView.setOnCompletionListener(player -> stopVideo());
        videoView.setOnErrorListener((player, what, extra) -> { stopVideo(); notice("영상을 재생할 수 없습니다."); return true; });
        button(chatSection, "영상 재생 닫기", this::stopVideo);

        profileSection = section(page);
        heading(profileSection, "내 활동 · 설정", 26);
        String savedAddress = getPreferences(MODE_PRIVATE).getString(PREF_SERVER_ADDRESS, "http://192.168.0.2:3000");
        serverAddress = savedAddress;
        if (token.isEmpty()) {
        label(profileSection, "처음 한 번만 PC 서버와 테스트 계정을 연결합니다.");
        serverInput = input(profileSection, "서버 주소", savedAddress);
        serverInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { serverAddress = text.toString().trim(); }
            @Override public void afterTextChanged(Editable text) { }
        });
        button(profileSection, "서버 연결 확인", () -> background(() -> {
            JSONObject health = new JSONObject(new String(requestRaw("GET", "/api/health", null, null, false), StandardCharsets.UTF_8));
            if (!health.optBoolean("ok")) throw new IllegalStateException("서버 응답을 확인하지 못했습니다.");
            saveServerAddress();
            notice("서버 연결 성공. 이제 테스트 계정을 만드세요.");
        }));
        nicknameInput = input(profileSection, "테스트 닉네임", "Android 사용자");
        genderInput = new Spinner(this);
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[] { "성별 선택", "남성 · 연한 파랑", "여성 · 연한 분홍" });
        genderInput.setAdapter(genderAdapter);
        profileSection.addView(genderInput, new LinearLayout.LayoutParams(-1, dp(52)));
        button(profileSection, "테스트 계정 만들기", () -> {
            String nickname = nicknameInput.getText().toString().trim();
            int selectedGender = genderInput.getSelectedItemPosition();
            background(() -> {
            if (selectedGender == 0) throw new IllegalArgumentException("성별을 한 번 선택하세요.");
            JSONObject body = new JSONObject().put("nickname", nickname).put("gender", selectedGender == 1 ? "male" : "female").put("adultAttested", true);
            JSONObject user = new JSONObject(new String(requestRaw("POST", "/api/dev/users", body.toString().getBytes(StandardCharsets.UTF_8), "application/json", false), StandardCharsets.UTF_8));
            token = user.getString("token");
            getPreferences(MODE_PRIVATE).edit().putString(PREF_TOKEN, token).apply();
            saveServerAddress();
            notice("계정을 만들었습니다. 매칭을 시작하세요.");
            runOnUiThread(this::recreate);
            });
        });
        } else {
            label(profileSection, "이 기기는 PC 서버와 연결되어 있습니다.");
            button(profileSection, "내 글 · 내 댓글", this::showMyActivity);
            button(profileSection, "프로필 방문자", this::showProfileVisitors);
            button(profileSection, "연결 설정", this::showConnectionSettings);
        }
        heading(profileSection, "내 프로필 사진", 20);
        label(profileSection, "연결 수락 뒤 상대에게만 공개됩니다.");
        button(profileSection, "프로필 사진 선택·등록", this::pickProfilePhoto);
        ownProfilePhotosView = column(profileSection);
        LinearLayout quickBar = new LinearLayout(this);
        quickBar.setOrientation(LinearLayout.HORIZONTAL); quickBar.setGravity(Gravity.CENTER); quickBar.setPadding(dp(18), dp(8), dp(18), dp(12)); quickBar.setBackground(round(COLOR_PANEL, 24));
        Button quickMatch = compactButton("⌕  찾기", () -> { if (token.isEmpty()) { selectTab("profile"); notice("먼저 서버 연결과 테스트 계정을 완료하세요."); } else { selectTab("chat"); showRandomMatchDialog(); } });
        Button quickCreate = compactButton("＋", () -> { if (token.isEmpty()) { selectTab("profile"); notice("먼저 서버 연결과 테스트 계정을 완료하세요."); } else { selectTab("lounge"); showComposerChoice(); } });
        quickCreate.setTextSize(24); quickCreate.setTextColor(COLOR_BG); quickCreate.setBackground(round(COLOR_BLUE, 24));
        quickBar.addView(quickMatch, new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(dp(72), dp(52)); createParams.leftMargin = dp(10); quickBar.addView(quickCreate, createParams);
        screen.addView(quickBar, new LinearLayout.LayoutParams(-1, -2));
        selectTab(token.isEmpty() ? "profile" : "lounge");
        if (!token.isEmpty()) refresh();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column(LinearLayout parent) {
        LinearLayout child = new LinearLayout(this);
        child.setOrientation(LinearLayout.VERTICAL);
        parent.addView(child);
        return child;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        return background;
    }
    private LinearLayout hero(LinearLayout parent) {
        LinearLayout child = new LinearLayout(this);
        child.setOrientation(LinearLayout.VERTICAL);
        child.setPadding(dp(22), dp(22), dp(22), dp(22));
        child.setBackground(round(COLOR_PANEL, 24));
        parent.addView(child, new LinearLayout.LayoutParams(-1, -2));
        return child;
    }
    private LinearLayout section(LinearLayout parent) {
        LinearLayout child = new LinearLayout(this);
        child.setOrientation(LinearLayout.VERTICAL);
        child.setPadding(dp(18), dp(10), dp(18), dp(18));
        child.setBackground(round(COLOR_PANEL, 22));
        child.setElevation(dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        parent.addView(child, params);
        return child;
    }
    private TextView label(LinearLayout parent, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(COLOR_MUTED);
        view.setPadding(0, dp(8), 0, dp(8));
        parent.addView(view);
        return view;
    }
    private void heading(LinearLayout parent, String text, int size) {
        TextView view = label(parent, text);
        view.setTextSize(size);
        view.setTextColor(COLOR_TEXT);
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(18), 0, dp(6));
    }
    private EditText input(LinearLayout parent, String hint, String value) {
        EditText view = new EditText(this);
        view.setSingleLine(true);
        view.setHint(hint);
        view.setText(value);
        view.setTextSize(16);
        view.setTextColor(COLOR_TEXT);
        view.setHintTextColor(COLOR_MUTED);
        GradientDrawable background = round(COLOR_FIELD, 14);
        background.setStroke(dp(1), Color.rgb(75, 74, 86));
        view.setBackground(background);
        view.setPadding(dp(14), dp(4), dp(14), dp(4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.topMargin = dp(4);
        parent.addView(view, params);
        return view;
    }
    private void button(LinearLayout parent, String title, Runnable action) {
        Button view = new Button(this);
        view.setText(title);
        view.setAllCaps(false);
        view.setTextColor(Color.WHITE);
        view.setTextSize(15);
        view.setTypeface(null, Typeface.BOLD);
        view.setBackground(round(COLOR_BLUE, 14));
        view.setTextColor(Color.rgb(20, 35, 44));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.topMargin = dp(8);
        parent.addView(view, params);
        view.setOnClickListener(v -> action.run());
    }
    private Button tabButton(LinearLayout parent, String title, Runnable action) {
        Button view = new Button(this);
        view.setText(title);
        view.setAllCaps(false);
        view.setTextSize(15);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        parent.addView(view, params);
        view.setOnClickListener(v -> action.run());
        return view;
    }
    private void selectTab(String tab) {
        // 계정 생성 전에는 앱 기능 화면을 열지 않고 연결 설정으로 돌린다.
        if (token.isEmpty() && !"profile".equals(tab)) tab = "profile";
        boolean lounge = "lounge".equals(tab);
        boolean chat = "chat".equals(tab);
        loungeSection.setVisibility(lounge ? View.VISIBLE : View.GONE);
        chatSection.setVisibility(chat ? View.VISIBLE : View.GONE);
        profileSection.setVisibility(!lounge && !chat ? View.VISIBLE : View.GONE);
        styleTab(loungeTab, lounge);
        styleTab(chatTab, chat);
        styleTab(profileTab, !lounge && !chat);
    }
    private void styleTab(Button view, boolean selected) {
        view.setTextColor(selected ? Color.rgb(20, 35, 44) : COLOR_TEXT);
        view.setTypeface(null, Typeface.BOLD);
        view.setBackground(round(selected ? COLOR_BLUE : COLOR_FIELD, 14));
    }
    private void notice(String text) { runOnUiThread(() -> statusView.setText(text)); }
    private void background(Work work) {
        worker.execute(() -> {
            try { work.run(); }
            catch (Exception error) { notice(error.getMessage() == null ? "요청에 실패했습니다." : error.getMessage()); }
        });
    }
    private String requireRoom() {
        if (roomId.isEmpty()) throw new IllegalArgumentException("대화방을 먼저 선택하세요.");
        return roomId;
    }
    private String baseUrl() throws Exception {
        URL url = new URL(serverAddress);
        String host = url.getHost();
        String[] octets = host.split("\\.");
        int[] ip = new int[4];
        boolean numericIp = octets.length == 4;
        if (numericIp) for (int i = 0; i < 4; i++) {
            if (!octets[i].matches("[0-9]{1,3}")) { numericIp = false; break; }
            ip[i] = Integer.parseInt(octets[i]);
            if (ip[i] > 255) { numericIp = false; break; }
        }
        boolean privateIp = numericIp && (ip[0] == 10 || (ip[0] == 172 && ip[1] >= 16 && ip[1] <= 31)
                || (ip[0] == 192 && ip[1] == 168));
        if (!"http".equals(url.getProtocol()) || url.getPort() != 3000 ||
                !(privateIp || "127.0.0.1".equals(host)) || url.getUserInfo() != null ||
                !(url.getPath().isEmpty() || "/".equals(url.getPath())) || url.getQuery() != null || url.getRef() != null) {
            throw new IllegalArgumentException("PC의 사설 IPv4 주소를 http://192.168.x.x:3000 형식으로 입력하세요.");
        }
        return url.toString().replaceAll("/+$", "");
    }

    // 연결 확인에 성공한 사설 서버 주소만 저장해 다음 실행 때 다시 입력하지 않게 한다.
    private void saveServerAddress() throws Exception {
        getPreferences(MODE_PRIVATE).edit().putString(PREF_SERVER_ADDRESS, baseUrl()).apply();
    }

    // 네트워크 요청은 작업 스레드에서 실행하고 응답 크기를 제한한다.
    private byte[] request(String method, String path, byte[] body, String contentType) throws Exception {
        if (token.isEmpty()) throw new IllegalStateException("먼저 테스트 계정을 만드세요.");
        return requestRaw(method, path, body, contentType, true);
    }
    private byte[] requestRaw(String method, String path, byte[] body, String contentType, boolean authenticated) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl() + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(6000);
        // 영상 업로드는 서버의 형식 검사와 최대 30초 변환을 기다려야 한다.
        connection.setReadTimeout("POST".equals(method) && path.endsWith("/videos") ? 60000 : 10000);
        connection.setRequestProperty("Cache-Control", "no-store");
        if (authenticated) connection.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", contentType);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(body); }
        }
        try {
            int status = connection.getResponseCode();
            InputStream source = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            byte[] bytes = readLimited(source, path.startsWith("/api/videos/") ? MAX_VIDEO_DOWNLOAD : 2 * 1024 * 1024);
            if (status >= 400) {
                String message = new JSONObject(new String(bytes, StandardCharsets.UTF_8)).optString("error", "요청 실패");
                throw new IllegalStateException(message + " (" + status + ")");
            }
            return bytes;
        } finally { connection.disconnect(); }
    }
    private byte[] readLimited(InputStream source, int limit) throws Exception {
        try (InputStream input = source; ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (result.size() + count > limit) throw new IllegalArgumentException("파일 크기 제한을 초과했습니다.");
                result.write(buffer, 0, count);
            }
            return result.toByteArray();
        }
    }
    private JSONObject json(String method, String path, JSONObject body) throws Exception {
        byte[] bytes = body == null ? null : body.toString().getBytes(StandardCharsets.UTF_8);
        return new JSONObject(new String(request(method, path, bytes, "application/json"), StandardCharsets.UTF_8));
    }

    private void showRandomMatchDialog() {
        if (token.isEmpty()) { selectTab("profile"); notice("먼저 서버 연결과 테스트 계정을 완료하세요."); return; }
        new android.app.AlertDialog.Builder(this)
            .setTitle("랜덤 대화 상대를 찾을까요?")
            .setMessage("확인을 누르면 익명 상대를 찾습니다. 대화를 나가면 방이 종료됩니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("확인", (dialog, which) -> background(() -> {
                JSONObject result = json("POST", "/api/queue", null);
                notice(result.optBoolean("waiting") ? "상대를 기다리는 중입니다." : "매칭되었습니다.");
                refresh();
            })).show();
    }

    // 연결 설정은 첫 실행 뒤에는 접어 두고, Wi-Fi 주소가 바뀔 때만 다시 연다.
    private void showConnectionSettings() {
        EditText address = dialogInput("http://192.168.x.x:3000", false);
        address.setText(serverAddress);
        new android.app.AlertDialog.Builder(this).setTitle("연결 설정").setView(address)
            .setNegativeButton("닫기", null)
            .setNeutralButton("계정 교체", (dialog, which) -> {
                token = ""; roomId = "";
                getPreferences(MODE_PRIVATE).edit().remove(PREF_TOKEN).apply();
                recreate();
            })
            .setPositiveButton("연결 확인·저장", (dialog, which) -> background(() -> {
                String previous = serverAddress;
                serverAddress = address.getText().toString().trim();
                try {
                    JSONObject health = new JSONObject(new String(requestRaw("GET", "/api/health", null, null, false), StandardCharsets.UTF_8));
                    if (!health.optBoolean("ok")) throw new IllegalStateException("서버 응답을 확인하지 못했습니다.");
                    saveServerAddress();
                    notice("서버 주소를 저장했습니다.");
                } catch (Exception error) {
                    serverAddress = previous;
                    throw error;
                }
            })).show();
    }

    private void showInbox() {
        background(() -> {
            JSONArray notes = json("GET", "/api/inbox", null).optJSONArray("notes");
            runOnUiThread(() -> {
                LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(18), dp(8), dp(18), dp(8));
                if (notes == null || notes.length() == 0) label(list, "받은 쪽지가 없습니다.");
                else for (int i = 0; i < notes.length(); i++) { JSONObject note = notes.optJSONObject(i); if (note != null) { TextView row = label(list, note.optString("body")); row.setTextColor(COLOR_TEXT); row.setTextSize(16); row.setBackground(round(COLOR_FIELD, 12)); row.setPadding(dp(12), dp(10), dp(12), dp(10)); } }
                new android.app.AlertDialog.Builder(this).setTitle("받은 쪽지").setView(list).setPositiveButton("닫기", null).show();
            });
        });
    }

    private void showProfileVisitors() {
        background(() -> {
            JSONArray visitors = json("GET", "/api/profile/visitors", null).optJSONArray("visitors");
            runOnUiThread(() -> {
                LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(18), dp(8), dp(18), dp(8));
                if (visitors == null || visitors.length() == 0) label(list, "아직 프로필 방문자가 없습니다.");
                else for (int i = 0; i < visitors.length(); i++) { JSONObject visitor = visitors.optJSONObject(i); if (visitor != null) label(list, ("male".equals(visitor.optString("gender")) ? "● 남성 방문" : "● 여성 방문")); }
                new android.app.AlertDialog.Builder(this).setTitle("프로필 방문자").setView(list).setPositiveButton("닫기", null).show();
            });
        });
    }

    private void showMyActivity() {
        background(() -> {
            JSONObject activity = json("GET", "/api/me/activity", null);
            JSONArray posts = activity.optJSONArray("posts"), comments = activity.optJSONArray("comments");
            runOnUiThread(() -> {
                ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(18), dp(8), dp(18), dp(8)); scroll.addView(list);
                TextView postTitle = new TextView(this); postTitle.setText("내 글"); postTitle.setTextColor(COLOR_TEXT); postTitle.setTextSize(19); postTitle.setTypeface(null, Typeface.BOLD); list.addView(postTitle);
                if (posts == null || posts.length() == 0) label(list, "작성한 게시물이 없습니다.");
                else for (int i = 0; i < posts.length(); i++) { JSONObject post = posts.optJSONObject(i); if (post != null) list.addView(compactButton(post.optString("title").isEmpty() ? post.optString("body") : post.optString("title"), () -> openPost(post)), new LinearLayout.LayoutParams(-1, dp(44))); }
                TextView commentTitle = new TextView(this); commentTitle.setText("내 댓글"); commentTitle.setTextColor(COLOR_TEXT); commentTitle.setTextSize(19); commentTitle.setTypeface(null, Typeface.BOLD); commentTitle.setPadding(0, dp(18), 0, dp(4)); list.addView(commentTitle);
                if (comments == null || comments.length() == 0) label(list, "작성한 댓글이 없습니다.");
                else for (int i = 0; i < comments.length(); i++) { JSONObject comment = comments.optJSONObject(i); if (comment != null) list.addView(compactButton("↳ " + comment.optString("body"), () -> openPostById(comment.optString("postId"))), new LinearLayout.LayoutParams(-1, dp(44))); }
                new android.app.AlertDialog.Builder(this).setTitle("내 활동").setView(scroll).setPositiveButton("닫기", null).show();
            });
        });
    }

    private void openPostById(String postId) {
        background(() -> {
            JSONArray posts = json("GET", "/api/feed", null).optJSONArray("posts");
            if (posts == null) return;
            for (int i = 0; i < posts.length(); i++) { JSONObject post = posts.optJSONObject(i); if (post != null && postId.equals(post.optString("id"))) { openPost(post); return; } }
            notice("게시물을 찾을 수 없습니다.");
        });
    }

    // 라운지 작성은 피드 밖의 다이얼로그에서 시작해 화면이 입력 폼으로 길어지지 않게 한다.
    private void showComposerChoice() {
        new android.app.AlertDialog.Builder(this).setTitle("새로 만들기")
            .setItems(new String[] { "사진 스토리", "게시물" }, (dialog, which) -> {
                if (which == 0) showStoryComposer(); else showPostComposer();
            }).show();
    }

    private EditText dialogInput(String hint, boolean multiline) {
        EditText input = new EditText(this);
        input.setHint(hint); input.setTextColor(COLOR_TEXT); input.setHintTextColor(COLOR_MUTED);
        input.setTextSize(16); input.setSingleLine(!multiline);
        if (multiline) input.setMinLines(3);
        input.setPadding(dp(16), dp(8), dp(16), dp(8));
        input.setBackground(round(COLOR_FIELD, 14));
        return input;
    }

    private void showStoryComposer() {
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(20), dp(8), dp(20), dp(8));
        TextView guide = new TextView(this); guide.setText("사진은 필수이며 24시간 뒤 사라집니다."); guide.setTextColor(COLOR_MUTED); body.addView(guide);
        storyInput = dialogInput("스토리에 남길 글", true); body.addView(storyInput, new LinearLayout.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle("새 스토리") .setView(body)
            .setNegativeButton("취소", null).setPositiveButton("사진 선택", (dialog, which) -> publishSocial("stories", true)).show();
    }

    private void showPostComposer() {
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(20), dp(8), dp(20), dp(8));
        postTitleInput = dialogInput("제목", false); body.addView(postTitleInput, new LinearLayout.LayoutParams(-1, -2));
        postInput = dialogInput("내용", true); body.addView(postInput, new LinearLayout.LayoutParams(-1, -2));
        new android.app.AlertDialog.Builder(this).setTitle("게시물 작성").setView(body)
            .setNegativeButton("취소", null).setNeutralButton("사진 추가", (dialog, which) -> publishSocial("posts", true))
            .setPositiveButton("올리기", (dialog, which) -> publishSocial("posts", false)).show();
    }

    private void addStoryComposerCircle() {
        LinearLayout item = new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
        TextView plus = new TextView(this); plus.setText("+"); plus.setTextSize(32); plus.setTextColor(COLOR_BG); plus.setGravity(Gravity.CENTER);
        item.addView(plus, new LinearLayout.LayoutParams(dp(64), dp(64)));
        plus.setBackground(round(COLOR_BLUE, 32));
        TextView caption = new TextView(this); caption.setText("내 스토리"); caption.setTextColor(COLOR_TEXT); caption.setTextSize(11); caption.setGravity(Gravity.CENTER);
        item.addView(caption, new LinearLayout.LayoutParams(dp(76), dp(30)));
        item.setPadding(dp(4), 0, dp(6), 0); item.setOnClickListener(view -> showStoryComposer());
        storiesView.addView(item, new LinearLayout.LayoutParams(dp(84), -1));
    }

    private void addStoryCircle(JSONObject story) {
        String id = story.optString("id");
        LinearLayout item = new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER); item.setPadding(dp(4), 0, dp(6), 0);
        ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setBackground(round(story.optBoolean("mine") ? COLOR_BLUE : COLOR_PINK, 34)); image.setClipToOutline(true);
        item.addView(image, new LinearLayout.LayoutParams(dp(68), dp(68)));
        TextView caption = new TextView(this); caption.setText(story.optBoolean("mine") ? "내 스토리" : "스토리"); caption.setTextColor(COLOR_TEXT); caption.setTextSize(11); caption.setGravity(Gravity.CENTER);
        item.addView(caption, new LinearLayout.LayoutParams(dp(82), dp(30)));
        item.setOnClickListener(view -> openStory(story)); storiesView.addView(item, new LinearLayout.LayoutParams(dp(88), -1));
        background(() -> {
            byte[] bytes = request("GET", "/api/stories/" + id + "/image", null, null);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            runOnUiThread(() -> { if (bitmap != null && item.isAttachedToWindow()) image.setImageBitmap(bitmap); });
        });
    }

    private void openStory(JSONObject story) {
        String id = story.optString("id");
        background(() -> {
            JSONObject storyViewResponse = story.optBoolean("mine") ? null : json("POST", "/api/stories/" + id + "/view", null);
            final int displayedViews = storyViewResponse == null ? story.optInt("viewCount") : storyViewResponse.optInt("viewCount", story.optInt("viewCount"));
            byte[] bytes = request("GET", "/api/stories/" + id + "/image", null, null);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            runOnUiThread(() -> {
                LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(12), dp(8), dp(12), dp(8));
                ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setIndeterminate(false); progress.setMax(5000); progress.setProgress(0); body.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
                ImageView image = new ImageView(this); image.setAdjustViewBounds(true); image.setImageBitmap(bitmap); body.addView(image, new LinearLayout.LayoutParams(-1, dp(360)));
                TextView text = new TextView(this); text.setText(story.optString("body")); text.setTextColor(COLOR_TEXT); text.setTextSize(18); text.setPadding(0, dp(16), 0, dp(8)); body.addView(text);
                TextView meta = new TextView(this); meta.setText("◉ " + displayedViews + " · " + remainingStoryTime(story.optLong("expiresAt"))); meta.setTextColor(COLOR_MUTED); body.addView(meta);
                Button menu = new Button(this); menu.setText("☰"); menu.setTextColor(COLOR_TEXT); menu.setBackgroundColor(Color.TRANSPARENT); body.addView(menu);
                android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setView(body).setNegativeButton("닫기", null).create();
                menu.setOnClickListener(view -> { if (story.optBoolean("mine")) showMyStoryMenu(id, dialog); else showStoryMenu(id, dialog); });
                dialog.show();
                if (story.optBoolean("mine")) dialog.setButton(android.app.AlertDialog.BUTTON_POSITIVE, "조회 " + story.optInt("viewerCount"), (d, w) -> showStoryViewers(id));
                final long startedAt = System.currentTimeMillis();
                final Runnable[] tick = new Runnable[1];
                final Runnable[] advance = new Runnable[1];
                tick[0] = () -> {
                    if (!dialog.isShowing()) return;
                    progress.setProgress((int) Math.min(5000, System.currentTimeMillis() - startedAt));
                    storyHandler.postDelayed(tick[0], 40);
                };
                advance[0] = () -> {
                    if (!dialog.isShowing()) return;
                    dialog.dismiss();
                    openNextStory(id);
                };
                dialog.setOnDismissListener(ignored -> { storyHandler.removeCallbacks(tick[0]); storyHandler.removeCallbacks(advance[0]); });
                storyHandler.post(tick[0]);
                storyHandler.postDelayed(advance[0], 5000);
            });
        });
    }

    // 스토리는 현재 목록 순서대로 5초마다 다음 사진으로 넘기고 마지막이면 닫는다.
    private void openNextStory(String currentId) {
        for (int i = 0; i < storySequence.length(); i++) {
            JSONObject current = storySequence.optJSONObject(i);
            if (current == null || !currentId.equals(current.optString("id"))) continue;
            JSONObject next = storySequence.optJSONObject(i + 1);
            if (next != null) openStory(next);
            return;
        }
    }

    private String remainingStoryTime(long expiresAt) {
        long minutes = Math.max(0, (expiresAt - System.currentTimeMillis() + 59999) / 60000);
        return minutes >= 60 ? ((minutes + 59) / 60) + "시간 남음" : minutes + "분 남음";
    }

    private void showStoryMenu(String storyId, android.app.AlertDialog parent) {
        new android.app.AlertDialog.Builder(this).setItems(new String[] { "쪽지 보내기", "차단하기", "신고하기" }, (dialog, which) -> {
            if (which == 0) showNoteComposer("/api/stories/" + storyId + "/message");
            else if (which == 1) background(() -> { json("POST", "/api/stories/" + storyId + "/block", null); parent.dismiss(); refresh(); });
            else showReportComposer("/api/stories/" + storyId + "/report");
        }).show();
    }

    private void showMyStoryMenu(String storyId, android.app.AlertDialog parent) {
        new android.app.AlertDialog.Builder(this).setItems(new String[] { "스토리 삭제" }, (dialog, which) -> background(() -> {
            json("DELETE", "/api/stories/" + storyId, null);
            runOnUiThread(parent::dismiss);
            refresh();
        })).show();
    }

    private void showStoryViewers(String storyId) {
        background(() -> {
            JSONObject result = json("GET", "/api/stories/" + storyId + "/viewers", null);
            runOnUiThread(() -> {
                LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(20), dp(8), dp(20), dp(8));
                TextView total = new TextView(this); total.setText("누적 조회 " + result.optInt("viewCount") + " · 방문자 " + result.optInt("viewerCount")); total.setTextColor(COLOR_TEXT); list.addView(total);
                JSONArray viewers = result.optJSONArray("viewers");
                if (viewers == null || viewers.length() == 0) label(list, "아직 조회한 사람이 없습니다.");
                else for (int i = 0; i < viewers.length(); i++) { JSONObject viewer = viewers.optJSONObject(i); if (viewer != null) label(list, ("male".equals(viewer.optString("gender")) ? "● 남성 방문자" : "● 여성 방문자") + " · " + viewer.optInt("viewCount") + "회"); }
                new android.app.AlertDialog.Builder(this).setTitle("스토리 조회").setView(list).setPositiveButton("닫기", null).show();
            });
        });
    }

    private void showNoteComposer(String path) {
        EditText input = dialogInput("보낼 쪽지", true);
        new android.app.AlertDialog.Builder(this).setTitle("익명 쪽지").setView(input).setNegativeButton("취소", null)
            .setPositiveButton("보내기", (dialog, which) -> background(() -> json("POST", path, new JSONObject().put("body", input.getText().toString().trim())))).show();
    }

    private void showReportComposer(String path) {
        EditText input = dialogInput("신고 사유", true);
        new android.app.AlertDialog.Builder(this).setTitle("신고하기").setView(input).setNegativeButton("취소", null)
            .setPositiveButton("신고", (dialog, which) -> background(() -> json("POST", path, new JSONObject().put("reason", input.getText().toString().trim())))).show();
    }

    // 피드에는 핵심 정보만 두고, 누르면 상세 화면에서 본문과 댓글을 이어서 읽는다.
    private void addPostCard(JSONObject post) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable background = round(COLOR_FIELD, 18);
        background.setStroke(dp(1), Color.rgb(73, 72, 84));
        card.setBackground(background);
        String title = post.optString("title").trim();
        String body = post.optString("body").trim();
        TextView titleView = new TextView(this);
        titleView.setText(title.isEmpty() ? body : title);
        titleView.setTextColor(COLOR_TEXT); titleView.setTextSize(19); titleView.setTypeface(null, Typeface.BOLD);
        card.addView(titleView);
        if (!title.isEmpty() && !body.isEmpty()) {
            TextView preview = new TextView(this);
            preview.setText(body); preview.setTextColor(COLOR_MUTED); preview.setTextSize(15); preview.setMaxLines(2);
            preview.setPadding(0, dp(6), 0, 0); card.addView(preview);
        }
        TextView stats = new TextView(this);
        stats.setText("◉ " + post.optInt("viewCount") + "    ◌ " + post.optInt("commentCount"));
        stats.setTextColor(COLOR_MUTED); stats.setTextSize(13); stats.setPadding(0, dp(12), 0, 0); card.addView(stats);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8); postsView.addView(card, params);
        card.setOnClickListener(view -> openPost(post));
    }

    private Button compactButton(String title, Runnable action) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false); button.setTextSize(13);
        button.setTextColor(COLOR_TEXT); button.setBackground(round(COLOR_FIELD, 12));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void openPost(JSONObject post) {
        final String id = post.optString("id");
        background(() -> {
            JSONObject view = post.optBoolean("mine") ? null : json("POST", "/api/posts/" + id + "/view", null);
            final int displayedViews = view == null ? post.optInt("viewCount") : view.optInt("viewCount", post.optInt("viewCount"));
            JSONObject commentResult = json("GET", "/api/posts/" + id + "/comments", null);
            Bitmap bitmap = null;
            if (post.optBoolean("hasImage")) {
                byte[] bytes = request("GET", "/api/posts/" + id + "/image", null, null);
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
            final Bitmap loadedImage = bitmap;
            runOnUiThread(() -> {
                ScrollView scroll = new ScrollView(this);
                LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(18), dp(8), dp(18), dp(8));
                scroll.addView(body);
                TextView title = new TextView(this); title.setText(post.optString("title").trim().isEmpty() ? post.optString("body") : post.optString("title"));
                title.setTextColor(COLOR_TEXT); title.setTextSize(24); title.setTypeface(null, Typeface.BOLD); body.addView(title);
                if (!post.optString("title").trim().isEmpty()) {
                    TextView text = new TextView(this); text.setText(post.optString("body")); text.setTextColor(COLOR_TEXT); text.setTextSize(17); text.setPadding(0, dp(14), 0, dp(8)); body.addView(text);
                }
                if (loadedImage != null) { ImageView image = new ImageView(this); image.setImageBitmap(loadedImage); image.setAdjustViewBounds(true); body.addView(image, new LinearLayout.LayoutParams(-1, -2)); }
                JSONArray comments = commentResult.optJSONArray("comments");
                int commentCount = comments == null ? 0 : comments.length();
                TextView stats = new TextView(this); stats.setText("◉ " + displayedViews + "    ◌ " + commentCount); stats.setTextColor(COLOR_MUTED); stats.setPadding(0, dp(14), 0, dp(8)); body.addView(stats);
                TextView commentsTitle = new TextView(this); commentsTitle.setText("댓글"); commentsTitle.setTextColor(COLOR_TEXT); commentsTitle.setTextSize(18); commentsTitle.setTypeface(null, Typeface.BOLD); body.addView(commentsTitle);
                if (comments == null || comments.length() == 0) label(body, "아직 댓글이 없습니다.");
                else for (int i = 0; i < comments.length(); i++) addCommentRow(body, comments.optJSONObject(i));
                Button comment = compactButton("댓글 남기기", () -> showCommentComposer(id)); body.addView(comment, new LinearLayout.LayoutParams(-1, dp(44)));
                android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setView(scroll).setNegativeButton("닫기", null).create();
                dialog.show();
                if (post.optBoolean("mine")) dialog.setButton(android.app.AlertDialog.BUTTON_POSITIVE, "게시물 삭제", (ignored, which) -> background(() -> { json("DELETE", "/api/posts/" + id, null); refresh(); }));
            });
        });
    }

    private void addCommentRow(LinearLayout parent, JSONObject comment) {
        if (comment == null) return;
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(12), dp(8), dp(12), dp(8)); row.setBackground(round(COLOR_PANEL, 12));
        TextView text = new TextView(this); text.setText(("male".equals(comment.optString("gender")) ? "● " : "● ") + comment.optString("body")); text.setTextColor(COLOR_TEXT); text.setTextSize(15); row.addView(text);
        Button reply = compactButton("답글 " + comment.optInt("replyCount"), () -> showReplies(comment));
        row.addView(reply, new LinearLayout.LayoutParams(-2, dp(38)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(6); parent.addView(row, params);
    }

    private void showCommentComposer(String postId) {
        EditText input = dialogInput("댓글을 남겨 주세요", true);
        new android.app.AlertDialog.Builder(this).setTitle("댓글").setView(input).setNegativeButton("취소", null)
            .setPositiveButton("등록", (dialog, which) -> background(() -> { json("POST", "/api/posts/" + postId + "/comments", new JSONObject().put("body", input.getText().toString().trim())); notice("댓글을 남겼습니다."); refresh(); })).show();
    }

    private void showReplies(JSONObject comment) {
        String id = comment.optString("id");
        background(() -> {
            JSONArray replies = json("GET", "/api/comments/" + id + "/replies", null).optJSONArray("replies");
            runOnUiThread(() -> {
                LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(18), dp(8), dp(18), dp(8));
                TextView original = new TextView(this); original.setText(comment.optString("body")); original.setTextColor(COLOR_TEXT); original.setTextSize(17); original.setTypeface(null, Typeface.BOLD); list.addView(original);
                if (replies == null || replies.length() == 0) label(list, "아직 답글이 없습니다.");
                else for (int i = 0; i < replies.length(); i++) { JSONObject reply = replies.optJSONObject(i); if (reply != null) label(list, "↳ " + reply.optString("body")); }
                new android.app.AlertDialog.Builder(this).setTitle("답글").setView(list).setNegativeButton("닫기", null)
                    .setPositiveButton("답글 작성", (dialog, which) -> showReplyComposer(id)).show();
            });
        });
    }

    private void showReplyComposer(String commentId) {
        EditText input = dialogInput("답글을 남겨 주세요", true);
        new android.app.AlertDialog.Builder(this).setTitle("답글").setView(input).setNegativeButton("취소", null)
            .setPositiveButton("등록", (dialog, which) -> background(() -> { json("POST", "/api/comments/" + commentId + "/replies", new JSONObject().put("body", input.getText().toString().trim())); notice("답글을 남겼습니다."); refresh(); })).show();
    }

    private void addRoomCard(JSONObject room, Runnable action) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(dp(16), dp(12), dp(16), dp(12)); card.setBackground(round(COLOR_FIELD, 16));
        TextView icon = new TextView(this); icon.setText("●"); icon.setTextSize(22); icon.setTextColor("connected".equals(room.optString("status")) ? COLOR_BLUE : COLOR_PINK); card.addView(icon, new LinearLayout.LayoutParams(dp(30), -2));
        TextView detail = new TextView(this); detail.setText("connected".equals(room.optString("status")) ? "계속 대화 중" : ("ended".equals(room.optString("status")) ? "종료된 대화" : "랜덤 대화")); detail.setTextColor(COLOR_TEXT); detail.setTextSize(16); detail.setTypeface(null, Typeface.BOLD); card.addView(detail, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = new TextView(this); arrow.setText("›"); arrow.setTextColor(COLOR_MUTED); arrow.setTextSize(28); card.addView(arrow);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(60)); params.topMargin = dp(6); roomsView.addView(card, params); card.setOnClickListener(view -> action.run());
    }

    private void addMessageBubble(JSONObject message) {
        boolean mine = message.optBoolean("mine");
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT); row.setPadding(0, dp(3), 0, dp(3));
        TextView bubble = new TextView(this); bubble.setText(message.optString("body") + (mine ? (message.optBoolean("read") ? "  ✓✓" : "  ✓") : "")); bubble.setTextColor(mine ? COLOR_BG : COLOR_TEXT); bubble.setTextSize(16); bubble.setPadding(dp(14), dp(10), dp(14), dp(10)); bubble.setBackground(round(mine ? COLOR_BLUE : COLOR_FIELD, 18));
        row.addView(bubble, new LinearLayout.LayoutParams(-2, -2)); messagesView.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    // 라운지 글은 랜덤 대화와 별개이며, 사진을 고른 경우에는 본문 생성 뒤 정규화 사진을 연결한다.
    private void publishSocial(String kind, boolean chooseImage) {
        if (token.isEmpty()) { notice("테스트 계정을 먼저 만드세요."); return; }
        EditText input = "stories".equals(kind) ? storyInput : postInput;
        String body = input.getText().toString().trim();
        if (body.isEmpty()) { notice("내용을 입력하세요."); return; }
        String title = "posts".equals(kind) ? postTitleInput.getText().toString().trim() : "";
        if ("posts".equals(kind) && title.isEmpty()) { notice("게시물 제목을 입력하세요."); return; }
        if ("stories".equals(kind) && !chooseImage) { notice("스토리는 사진을 반드시 추가하세요."); return; }
        if (chooseImage) {
            if (!kind.equals(pendingSocialKind) || !body.equals(pendingSocialBody) || !title.equals(pendingSocialTitle) || !token.equals(pendingSocialToken)) pendingSocialId = "";
            pendingSocialKind = kind;
            pendingSocialBody = body;
            pendingSocialTitle = title;
            pendingSocialToken = token;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, "stories".equals(kind) ? PICK_STORY_IMAGE : PICK_POST_IMAGE);
            return;
        }
        background(() -> {
            JSONObject payload = new JSONObject().put("body", body);
            if ("posts".equals(kind)) payload.put("title", title);
            json("POST", "/api/" + kind, payload);
            runOnUiThread(() -> input.setText(""));
            notice("stories".equals(kind) ? "24시간 스토리를 올렸습니다." : "게시물을 올렸습니다.");
            refresh();
        });
    }

    // 서버 상태를 다시 읽어 매칭·방 목록을 동기화한다.
    private void refresh() {
        background(() -> {
            JSONObject state = json("GET", "/api/state", null);
            JSONArray rooms = state.getJSONArray("rooms");
            JSONArray ownProfilePhotos = json("GET", "/api/profile/photos", null).getJSONArray("photos");
            JSONArray stories = json("GET", "/api/stories", null).getJSONArray("stories");
            JSONArray posts = json("GET", "/api/feed", null).getJSONArray("posts");
            runOnUiThread(() -> {
                storySequence = stories;
                storiesView.removeAllViews();
                addStoryComposerCircle();
                if (stories.length() == 0) {
                    TextView empty = new TextView(this); empty.setText("첫 스토리"); empty.setTextColor(COLOR_MUTED); empty.setGravity(Gravity.CENTER); empty.setTextSize(12);
                    storiesView.addView(empty, new LinearLayout.LayoutParams(dp(92), -1));
                }
                for (int i = 0; i < stories.length(); i++) {
                    JSONObject story = stories.optJSONObject(i);
                    if (story == null) continue;
                    if (story.optBoolean("hasImage")) addStoryCircle(story);
                }
                postsView.removeAllViews();
                if (posts.length() == 0) label(postsView, "아직 게시물이 없습니다.");
                for (int i = 0; i < posts.length(); i++) {
                    JSONObject post = posts.optJSONObject(i);
                    if (post == null) continue;
                    addPostCard(post);
                }
                roomsView.removeAllViews();
                if (rooms.length() == 0) label(roomsView, state.optBoolean("waiting") ? "상대를 기다리는 중" : "대화방 없음");
                for (int i = 0; i < rooms.length(); i++) {
                    JSONObject room = rooms.optJSONObject(i);
                    if (room == null) continue;
                    String id = room.optString("id");
                    addRoomCard(room, () -> openRoom(id));
                }
                if (!roomId.isEmpty()) openRoom(roomId);
                else if (rooms.length() > 0) openRoom(rooms.optJSONObject(0).optString("id"));
                ownProfilePhotosView.removeAllViews();
                if (ownProfilePhotos.length() == 0) label(ownProfilePhotosView, "등록된 사진 없음");
                for (int i = 0; i < ownProfilePhotos.length(); i++) {
                    JSONObject photo = ownProfilePhotos.optJSONObject(i);
                    if (photo == null) continue;
                    String id = photo.optString("id");
                    label(ownProfilePhotosView, ("approved".equals(photo.optString("status")) ? "승인됨" : "검토 대기") + " · " + id.substring(0, Math.min(8, id.length())));
                    button(ownProfilePhotosView, "이 프로필 사진 삭제", () -> background(() -> {
                        json("DELETE", "/api/profile/photos/" + id, null);
                        refresh();
                    }));
                }
            });
        });
    }

    // 방을 바꿀 때 이전 사진을 즉시 지우고 현재 방의 메시지·승인 사진만 표시한다.
    private void openRoom(String id) {
        roomId = id;
        runOnUiThread(() -> { photoView.setImageDrawable(null); stopVideo(); });
        background(() -> {
            JSONObject room = json("GET", "/api/rooms/" + id, null);
            JSONArray photos = "ended".equals(room.optString("status")) ? new JSONArray() : json("GET", "/api/rooms/" + id + "/photos", null).getJSONArray("photos");
            JSONArray videos = "connected".equals(room.optString("status")) ? json("GET", "/api/rooms/" + id + "/videos", null).getJSONArray("videos") : new JSONArray();
            JSONArray peerProfilePhotos = "connected".equals(room.optString("status")) ? json("GET", "/api/rooms/" + id + "/peer/profile-photos", null).getJSONArray("photos") : new JSONArray();
            runOnUiThread(() -> {
                if (!id.equals(roomId)) return;
                messagesView.removeAllViews();
                label(messagesView, "connected".equals(room.optString("status")) ? "연결된 대화" : "랜덤 대화");
                JSONArray messages = room.optJSONArray("messages");
                if (messages != null) for (int i = 0; i < messages.length(); i++) {
                    JSONObject message = messages.optJSONObject(i);
                    if (message != null) addMessageBubble(message);
                }
                if (room.optBoolean("peerTyping")) label(messagesView, "상대가 입력 중입니다…");
                peerProfilePhotosView.removeAllViews();
                if (peerProfilePhotos.length() == 0) label(peerProfilePhotosView, "공개된 프로필 사진 없음");
                for (int i = 0; i < peerProfilePhotos.length(); i++) {
                    JSONObject photo = peerProfilePhotos.optJSONObject(i);
                    if (photo == null) continue;
                    String photoId = photo.optString("id");
                    button(peerProfilePhotosView, "상대 프로필 사진 보기", () -> showImage(id, "/api/rooms/" + id + "/peer/profile-photos/" + photoId));
                }
                photosView.removeAllViews();
                for (int i = 0; i < photos.length(); i++) {
                    JSONObject photo = photos.optJSONObject(i);
                    if (photo == null) continue;
                    String photoId = photo.optString("id");
                    if ("approved".equals(photo.optString("status"))) button(photosView, "승인 사진 보기 · " + photoId.substring(0, Math.min(8, photoId.length())), () -> showImage(id, "/api/photos/" + photoId));
                    else label(photosView, "내 사진 · 승인 대기");
                }
                videosView.removeAllViews();
                if (videos.length() == 0) label(videosView, "공개된 영상 없음");
                for (int i = 0; i < videos.length(); i++) {
                    JSONObject video = videos.optJSONObject(i);
                    if (video == null) continue;
                    String videoId = video.optString("id");
                    if ("approved".equals(video.optString("status"))) button(videosView, "승인 영상 재생 · " + videoId.substring(0, Math.min(8, videoId.length())), () -> showVideo(id, videoId));
                    else label(videosView, "내 영상 · 승인 대기");
                }
            });
        });
    }
    private void roomAction(String method, String action, String decision) {
        background(() -> {
            String id = requireRoom();
            json(method, "/api/rooms/" + id + "/" + action, decision == null ? null : new JSONObject().put("decision", decision));
            if ("leave".equals(action) || "block".equals(action)) runOnUiThread(() -> { photoView.setImageDrawable(null); stopVideo(); });
            openRoom(id);
        });
    }
    private void showImage(String selectedRoom, String path) {
        background(() -> {
            byte[] bytes = request("GET", path, null, null);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) throw new IllegalStateException("사진을 열 수 없습니다.");
            runOnUiThread(() -> { if (selectedRoom.equals(roomId)) photoView.setImageBitmap(bitmap); });
        });
    }
    private void showPublicImage(String path) {
        background(() -> {
            byte[] bytes = request("GET", path, null, null);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) throw new IllegalStateException("사진을 열 수 없습니다.");
            runOnUiThread(() -> {
                if (!foreground || isFinishing()) return;
                ImageView preview = new ImageView(this);
                preview.setAdjustViewBounds(true);
                preview.setImageBitmap(bitmap);
                android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                    .setTitle("라운지 사진").setView(preview).setPositiveButton("닫기", null).create();
                dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                dialog.show();
            });
        });
    }
    // 승인 영상은 인증 요청으로 받은 뒤 앱 전용 임시 파일에서 재생하고 화면 이탈 시 지운다.
    private void showVideo(String selectedRoom, String videoId) {
        background(() -> {
            byte[] bytes = request("GET", "/api/videos/" + videoId, null, null);
            JSONObject current = json("GET", "/api/rooms/" + selectedRoom, null);
            if (!"connected".equals(current.optString("status"))) return;
            File file = File.createTempFile("moment-preview-", ".mp4", getCacheDir());
            try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); }
            runOnUiThread(() -> {
                if (!selectedRoom.equals(roomId) || !foreground) { file.delete(); return; }
                stopVideo();
                videoFile = file;
                videoView.setVisibility(android.view.View.VISIBLE);
                videoView.setVideoPath(file.getAbsolutePath());
                videoView.start();
            });
        });
    }
    private void stopVideo() {
        videoView.stopPlayback();
        videoView.setVisibility(android.view.View.GONE);
        if (videoFile != null) { videoFile.delete(); videoFile = null; }
    }
    private void pickPhoto() {
        if (roomId.isEmpty()) { notice("대화방을 먼저 선택하세요."); return; }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_PHOTO);
    }
    private void pickProfilePhoto() {
        if (token.isEmpty()) { notice("테스트 계정을 먼저 만드세요."); return; }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_PROFILE_PHOTO);
    }
    private void pickVideo() {
        if (roomId.isEmpty()) { notice("대화방을 먼저 선택하세요."); return; }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/mp4");
        startActivityForResult(intent, PICK_VIDEO);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode != PICK_PHOTO && requestCode != PICK_PROFILE_PHOTO && requestCode != PICK_VIDEO && requestCode != PICK_POST_IMAGE && requestCode != PICK_STORY_IMAGE) || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        String selectedRoom = roomId;
        android.net.Uri uri = data.getData();
        background(() -> {
            String type = getContentResolver().getType(uri);
            if (requestCode == PICK_POST_IMAGE || requestCode == PICK_STORY_IMAGE) {
                String kind = requestCode == PICK_STORY_IMAGE ? "stories" : "posts";
                if (!kind.equals(pendingSocialKind) || pendingSocialBody.isEmpty()) throw new IllegalStateException("다시 사진 선택을 시작하세요.");
                if (!("image/jpeg".equals(type) || "image/png".equals(type))) throw new IllegalArgumentException("JPEG 또는 PNG만 보낼 수 있습니다.");
                byte[] bytes = readLimited(getContentResolver().openInputStream(uri), MAX_UPLOAD);
                if (!token.equals(pendingSocialToken)) throw new IllegalStateException("계정이 변경되었습니다. 다시 사진을 선택하세요.");
                if (pendingSocialId.isEmpty()) {
                    JSONObject payload = new JSONObject().put("body", pendingSocialBody);
                    if ("posts".equals(kind)) payload.put("title", pendingSocialTitle);
                    pendingSocialId = json("POST", "/api/" + kind, payload).getString("id");
                }
                request("POST", "/api/" + kind + "/" + pendingSocialId + "/image", bytes, type);
                pendingSocialId = "";
                pendingSocialKind = "";
                pendingSocialBody = "";
                pendingSocialTitle = "";
                runOnUiThread(() -> {
                    ("stories".equals(kind) ? storyInput : postInput).setText("");
                    if ("posts".equals(kind)) postTitleInput.setText("");
                });
                notice("stories".equals(kind) ? "사진 스토리를 올렸습니다." : "사진 게시물을 올렸습니다.");
                refresh();
                return;
            }
            if (requestCode == PICK_VIDEO) {
                if (!"video/mp4".equals(type)) throw new IllegalArgumentException("MP4 영상만 보낼 수 있습니다.");
                byte[] bytes = readLimited(getContentResolver().openInputStream(uri), MAX_VIDEO_UPLOAD);
                request("POST", "/api/rooms/" + selectedRoom + "/videos", bytes, type);
                notice("영상을 보냈습니다. 담당자 승인 대기 중입니다.");
                openRoom(selectedRoom);
                return;
            }
            if (!("image/jpeg".equals(type) || "image/png".equals(type))) throw new IllegalArgumentException("JPEG 또는 PNG만 보낼 수 있습니다.");
            byte[] bytes = readLimited(getContentResolver().openInputStream(uri), MAX_UPLOAD);
            if (requestCode == PICK_PROFILE_PHOTO) {
                request("POST", "/api/profile/photos", bytes, type);
                notice("프로필 사진을 등록했습니다. 담당자 승인 대기 중입니다.");
                refresh();
            } else {
                request("POST", "/api/rooms/" + selectedRoom + "/photos", bytes, type);
                notice("채팅 사진을 보냈습니다. 담당자 승인 대기 중입니다.");
                openRoom(selectedRoom);
            }
        });
    }
    @Override protected void onDestroy() {
        photoView.setImageDrawable(null);
        stopVideo();
        worker.shutdownNow();
        super.onDestroy();
    }
    @Override protected void onStop() {
        foreground = false;
        photoView.setImageDrawable(null);
        stopVideo();
        super.onStop();
    }
    @Override protected void onStart() {
        super.onStart();
        foreground = true;
    }
}
