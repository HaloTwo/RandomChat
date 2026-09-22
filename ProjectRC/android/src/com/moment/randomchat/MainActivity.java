package com.moment.randomchat;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile String token = "";
    private volatile String roomId = "";
    private volatile String serverAddress = "http://192.168.0.2:3000";
    private volatile boolean foreground;
    private EditText serverInput, nicknameInput, messageInput, reportInput, storyInput, postInput;
    private Spinner genderInput;
    private TextView statusView, pageTitle, pageSubtitle;
    private LinearLayout roomsView, messagesView, photosView, videosView, ownProfilePhotosView, peerProfilePhotosView, storiesView, postsView;
    private ImageView photoView;
    private VideoView videoView;
    private File videoFile;
    private String pendingSocialKind = "";
    private String pendingSocialBody = "";
    private String pendingSocialId = "";
    private String pendingSocialToken = "";
    private LinearLayout loungeSection, chatSection, profileSection;
    private Button loungeTab, chatTab, profileTab;

    private interface Work { void run() throws Exception; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // 로컬 대화·사진 화면의 캡처와 최근 앱 미리보기를 OS에 억제 요청한다.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        File[] oldPreviews = getCacheDir().listFiles((dir, name) -> name.startsWith("moment-preview-") && name.endsWith(".mp4"));
        if (oldPreviews != null) for (File oldPreview : oldPreviews) oldPreview.delete();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(244, 247, 243));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        page.setPadding(pad, pad, pad, pad);
        scroll.addView(page);
        setContentView(scroll);
        LinearLayout hero = hero(page);
        pageTitle = new TextView(this);
        pageTitle.setText("모먼트");
        pageTitle.setTextColor(Color.WHITE);
        pageTitle.setTextSize(32);
        pageTitle.setTypeface(null, Typeface.BOLD);
        hero.addView(pageTitle);
        pageSubtitle = new TextView(this);
        pageSubtitle.setText("가벼운 이야기, 편안한 대화");
        pageSubtitle.setTextColor(Color.rgb(215, 237, 223));
        pageSubtitle.setTextSize(16);
        pageSubtitle.setPadding(0, dp(4), 0, dp(14));
        hero.addView(pageSubtitle);
        statusView = new TextView(this);
        statusView.setText("서버와 연결 전");
        statusView.setTextColor(Color.rgb(23, 61, 48));
        statusView.setTextSize(14);
        statusView.setTypeface(null, Typeface.BOLD);
        statusView.setPadding(dp(12), dp(8), dp(12), dp(8));
        statusView.setBackground(round(Color.rgb(218, 241, 224), 14));
        hero.addView(statusView);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(0, dp(16), 0, dp(8));
        page.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        loungeTab = tabButton(navigation, "게시물", () -> selectTab("lounge"));
        chatTab = tabButton(navigation, "접속자", () -> selectTab("chat"));
        profileTab = tabButton(navigation, "내 설정", () -> selectTab("profile"));

        loungeSection = section(page);
        heading(loungeSection, "오늘의 라운지", 24);
        label(loungeSection, "로컬 테스트 사용자끼리만 보입니다. 스토리는 24시간 뒤 사라집니다.");
        storyInput = input(loungeSection, "24시간 스토리 (120자)", "");
        button(loungeSection, "스토리 올리기", () -> publishSocial("stories", false));
        button(loungeSection, "사진과 함께 올리기", () -> publishSocial("stories", true));
        heading(loungeSection, "새로운 게시물", 22);
        postInput = input(loungeSection, "게시물 (500자)", "");
        button(loungeSection, "게시물 올리기", () -> publishSocial("posts", false));
        button(loungeSection, "사진과 함께 올리기", () -> publishSocial("posts", true));
        heading(loungeSection, "최근 스토리", 20);
        storiesView = column(loungeSection);
        heading(loungeSection, "최근 게시물", 20);
        postsView = column(loungeSection);

        chatSection = section(page);
        heading(chatSection, "현재 접속자 · 랜덤 대화", 24);
        label(chatSection, "상대를 찾고, 이어서 대화할지 직접 결정하세요.");
        button(chatSection, "랜덤 대화 찾기", () -> new android.app.AlertDialog.Builder(this)
            .setTitle("랜덤 대화 상대를 찾을까요?")
            .setMessage("확인을 누르면 익명 상대를 찾습니다. 대화를 나가면 방이 종료됩니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("확인", (dialog, which) -> background(() -> {
                JSONObject result = json("POST", "/api/queue", null);
                notice(result.optBoolean("waiting") ? "상대를 기다리는 중입니다." : "매칭되었습니다.");
                refresh();
            })).show());
        button(chatSection, "목록 새로고침", this::refresh);
        heading(chatSection, "내 대화방", 20);
        roomsView = column(chatSection);
        heading(chatSection, "현재 대화", 20);
        messagesView = column(chatSection);
        heading(chatSection, "상대 프로필 사진", 20);
        label(chatSection, "연결을 수락한 뒤에만 표시됩니다.");
        peerProfilePhotosView = column(chatSection);
        messageInput = input(chatSection, "보낼 메시지", "");
        button(chatSection, "메시지 보내기", () -> {
            String body = messageInput.getText().toString().trim();
            background(() -> {
            String id = requireRoom();
            if (body.isEmpty()) throw new IllegalArgumentException("메시지를 입력하세요.");
            json("POST", "/api/rooms/" + id + "/messages", new JSONObject().put("body", body).put("clientId", UUID.randomUUID().toString()));
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
        heading(profileSection, "내 설정", 24);
        label(profileSection, "같은 Wi-Fi의 PC 서버 주소를 사용합니다.");
        String savedAddress = getPreferences(MODE_PRIVATE).getString(PREF_SERVER_ADDRESS, "http://192.168.0.2:3000");
        serverAddress = savedAddress;
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
            saveServerAddress();
            notice("계정을 만들었습니다. 매칭을 시작하세요.");
            refresh();
            runOnUiThread(() -> selectTab("lounge"));
            });
        });
        heading(profileSection, "내 프로필 사진", 20);
        label(profileSection, "연결 수락 뒤 상대에게만 공개됩니다.");
        button(profileSection, "프로필 사진 선택·등록", this::pickProfilePhoto);
        ownProfilePhotosView = column(profileSection);
        selectTab("profile");
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
        child.setBackground(round(Color.rgb(23, 61, 48), 24));
        parent.addView(child, new LinearLayout.LayoutParams(-1, -2));
        return child;
    }
    private LinearLayout section(LinearLayout parent) {
        LinearLayout child = new LinearLayout(this);
        child.setOrientation(LinearLayout.VERTICAL);
        child.setPadding(dp(18), dp(10), dp(18), dp(18));
        child.setBackground(round(Color.WHITE, 22));
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
        view.setTextColor(Color.rgb(79, 101, 88));
        view.setPadding(0, dp(8), 0, dp(8));
        parent.addView(view);
        return view;
    }
    private void heading(LinearLayout parent, String text, int size) {
        TextView view = label(parent, text);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(23, 61, 48));
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(18), 0, dp(6));
    }
    private EditText input(LinearLayout parent, String hint, String value) {
        EditText view = new EditText(this);
        view.setSingleLine(true);
        view.setHint(hint);
        view.setText(value);
        view.setTextSize(16);
        view.setTextColor(Color.rgb(29, 55, 44));
        view.setHintTextColor(Color.rgb(125, 142, 132));
        GradientDrawable background = round(Color.rgb(248, 250, 248), 14);
        background.setStroke(dp(1), Color.rgb(212, 222, 215));
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
        view.setBackground(round(Color.rgb(31, 111, 74), 14));
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
        view.setTextColor(selected ? Color.WHITE : Color.rgb(23, 61, 48));
        view.setTypeface(null, Typeface.BOLD);
        view.setBackground(round(selected ? Color.rgb(23, 61, 48) : Color.rgb(225, 234, 227), 14));
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

    // 라운지 글은 랜덤 대화와 별개이며, 사진을 고른 경우에는 본문 생성 뒤 정규화 사진을 연결한다.
    private void publishSocial(String kind, boolean chooseImage) {
        if (token.isEmpty()) { notice("테스트 계정을 먼저 만드세요."); return; }
        EditText input = "stories".equals(kind) ? storyInput : postInput;
        String body = input.getText().toString().trim();
        if (body.isEmpty()) { notice("내용을 입력하세요."); return; }
        if (chooseImage) {
            if (!kind.equals(pendingSocialKind) || !body.equals(pendingSocialBody) || !token.equals(pendingSocialToken)) pendingSocialId = "";
            pendingSocialKind = kind;
            pendingSocialBody = body;
            pendingSocialToken = token;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, "stories".equals(kind) ? PICK_STORY_IMAGE : PICK_POST_IMAGE);
            return;
        }
        background(() -> {
            json("POST", "/api/" + kind, new JSONObject().put("body", body));
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
                storiesView.removeAllViews();
                if (stories.length() == 0) label(storiesView, "아직 스토리가 없습니다.");
                for (int i = 0; i < stories.length(); i++) {
                    JSONObject story = stories.optJSONObject(i);
                    if (story == null) continue;
                    String id = story.optString("id");
                    label(storiesView, story.optString("anonymousLabel", "익명 사용자") + " · " + story.optString("body"));
                    if (story.optBoolean("hasImage")) button(storiesView, "스토리 사진 보기", () -> showPublicImage("/api/stories/" + id + "/image"));
                    if (story.optBoolean("mine")) button(storiesView, "내 스토리 삭제", () -> background(() -> { json("DELETE", "/api/stories/" + id, null); refresh(); }));
                }
                postsView.removeAllViews();
                if (posts.length() == 0) label(postsView, "아직 게시물이 없습니다.");
                for (int i = 0; i < posts.length(); i++) {
                    JSONObject post = posts.optJSONObject(i);
                    if (post == null) continue;
                    String id = post.optString("id");
                    label(postsView, post.optString("anonymousLabel", "익명 사용자") + " · " + post.optString("body"));
                    if (post.optBoolean("hasImage")) button(postsView, "게시물 사진 보기", () -> showPublicImage("/api/posts/" + id + "/image"));
                    if (post.optBoolean("mine")) button(postsView, "내 게시물 삭제", () -> background(() -> { json("DELETE", "/api/posts/" + id, null); refresh(); }));
                }
                roomsView.removeAllViews();
                if (rooms.length() == 0) label(roomsView, state.optBoolean("waiting") ? "상대를 기다리는 중" : "대화방 없음");
                for (int i = 0; i < rooms.length(); i++) {
                    JSONObject room = rooms.optJSONObject(i);
                    if (room == null) continue;
                    String id = room.optString("id");
                    button(roomsView, room.optString("status") + " · " + room.optJSONObject("peer").optString("displayName"), () -> openRoom(id));
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
                JSONObject peer = room.optJSONObject("peer");
                label(messagesView, room.optString("status") + " · " + (peer == null ? "상대" : peer.optString("displayName")));
                JSONArray messages = room.optJSONArray("messages");
                if (messages != null) for (int i = 0; i < messages.length(); i++) {
                    JSONObject message = messages.optJSONObject(i);
                    if (message != null) label(messagesView, (message.optBoolean("mine") ? "나: " : "상대: ") + message.optString("body"));
                }
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
                if (pendingSocialId.isEmpty()) pendingSocialId = json("POST", "/api/" + kind, new JSONObject().put("body", pendingSocialBody)).getString("id");
                request("POST", "/api/" + kind + "/" + pendingSocialId + "/image", bytes, type);
                pendingSocialId = "";
                pendingSocialKind = "";
                pendingSocialBody = "";
                runOnUiThread(() -> ("stories".equals(kind) ? storyInput : postInput).setText(""));
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
