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
    private volatile boolean waitingForMatch;
    private volatile String token = "";
    private volatile String roomId = "";
    private volatile String serverAddress = "http://192.168.0.2:3000";
    private volatile boolean foreground;
    private EditText serverInput, nicknameInput, messageInput, reportInput, storyInput, postTitleInput, postInput;
    private Spinner genderInput;
    private TextView statusView, pageTitle, pageSubtitle;
    private LinearLayout roomsView, messagesView, photosView, videosView, ownProfilePhotosView, peerProfilePhotosView, storiesView, postsView, visitorsView;
    private ImageView photoView;
    private VideoView videoView;
    private File videoFile;
    private String pendingSocialKind = "";
    private String pendingSocialBody = "";
    private String pendingSocialTitle = "";
    private String pendingSocialId = "";
    private String pendingSocialToken = "";
    private JSONArray storySequence = new JSONArray();
    private LinearLayout loungeSection, chatSection, profileSection, visitorsSection, quickBar;
    private Button loungeTab, visitorsTab, chatTab, profileTab;
    private boolean referenceUi;
    private LinearLayout referenceFeed, referenceActivity, referenceChats, referenceAccount, referenceAccountRows;
    private TextView accountNameView, accountStateView;
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
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
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
        // 참고 화면처럼 첫 화면은 피드에 집중한다. 연결 상태는 알림 칩으로만 남기고 큰 소개 영역은 숨긴다.
        hero.setVisibility(View.GONE);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(0, dp(16), 0, dp(8));
        page.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        loungeTab = tabButton(navigation, "게시물", () -> selectTab("lounge"));
        chatTab = tabButton(navigation, "내 대화", () -> selectTab("chat"));
        profileTab = tabButton(navigation, "내 활동", () -> selectTab("profile"));
        navigation.setVisibility(View.GONE);

        loungeSection = section(page);
        LinearLayout homeTop = new LinearLayout(this); homeTop.setGravity(Gravity.CENTER_VERTICAL); homeTop.setPadding(0, dp(4), 0, dp(8));
        Button refreshHome = compactButton("●", this::refresh); refreshHome.setTextColor(COLOR_BLUE); homeTop.addView(refreshHome, new LinearLayout.LayoutParams(dp(52), dp(44)));
        TextView homeTitle = new TextView(this); homeTitle.setText("게시물"); homeTitle.setTextColor(COLOR_TEXT); homeTitle.setTextSize(23); homeTitle.setTypeface(null, Typeface.BOLD); homeTop.addView(homeTitle, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button openVisitors = compactButton("◉", () -> selectTab("visitors")); openVisitors.setTextColor(COLOR_TEXT); homeTop.addView(openVisitors, new LinearLayout.LayoutParams(dp(52), dp(44)));
        loungeSection.addView(homeTop, new LinearLayout.LayoutParams(-1, -2));
        storyInput = new EditText(this);
        postTitleInput = new EditText(this);
        postInput = new EditText(this);
        HorizontalScrollView storyScroll = new HorizontalScrollView(this);
        storyScroll.setHorizontalScrollBarEnabled(false);
        storiesView = new LinearLayout(this);
        storiesView.setOrientation(LinearLayout.HORIZONTAL);
        storyScroll.addView(storiesView);
        loungeSection.addView(storyScroll, new LinearLayout.LayoutParams(-1, dp(118)));
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

        visitorsSection = section(page);
        LinearLayout visitorTop = new LinearLayout(this); visitorTop.setGravity(Gravity.CENTER_VERTICAL); visitorTop.setPadding(0, dp(6), 0, dp(10));
        Button backHome = compactButton("‹", () -> selectTab("lounge")); backHome.setTextSize(30); visitorTop.addView(backHome, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView visitorTitle = new TextView(this); visitorTitle.setText("프로필 방문자"); visitorTitle.setTextColor(COLOR_TEXT); visitorTitle.setTextSize(23); visitorTitle.setTypeface(null, Typeface.BOLD); visitorTitle.setGravity(Gravity.CENTER); visitorTop.addView(visitorTitle, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button visitorRefresh = compactButton("↻", this::refreshVisitors); visitorTop.addView(visitorRefresh, new LinearLayout.LayoutParams(dp(48), dp(48)));
        visitorsSection.addView(visitorTop, new LinearLayout.LayoutParams(-1, -2));
        visitorsView = column(visitorsSection);

        profileSection = section(page);
        heading(profileSection, "내 계정", 26);
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
            label(profileSection, "내 설정과 활동을 관리합니다.");
            button(profileSection, "내 글 · 내 댓글", this::showMyActivity);
            button(profileSection, "프로필 방문자", () -> selectTab("visitors"));
            button(profileSection, "설정", this::showConnectionSettings);
        }
        heading(profileSection, "내 프로필 사진", 20);
        label(profileSection, "연결 수락 뒤 상대에게만 공개됩니다.");
        button(profileSection, "프로필 사진 선택·등록", this::pickProfilePhoto);
        ownProfilePhotosView = column(profileSection);
        quickBar = new LinearLayout(this);
        quickBar.setOrientation(LinearLayout.HORIZONTAL); quickBar.setGravity(Gravity.CENTER_VERTICAL); quickBar.setPadding(dp(16), dp(8), dp(16), dp(8)); quickBar.setBackgroundColor(Color.TRANSPARENT);
        Button quickMatch = compactButton("⌕", () -> { if (token.isEmpty()) { selectTab("profile"); notice("먼저 서버 연결과 테스트 계정을 완료하세요."); } else { showRandomMatchDialog(); } });
        quickMatch.setTextSize(27); quickMatch.setTextColor(COLOR_BG); quickMatch.setBackground(round(COLOR_BLUE, 28));
        Button quickCreate = compactButton("＋", () -> { if (token.isEmpty()) { selectTab("profile"); notice("먼저 서버 연결과 테스트 계정을 완료하세요."); } else { showComposerChoice(); } });
        quickCreate.setTextSize(30); quickCreate.setTextColor(COLOR_BG); quickCreate.setBackground(round(COLOR_BLUE, 28));
        quickBar.addView(quickMatch, new LinearLayout.LayoutParams(dp(56), dp(56)));
        quickBar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(1), 1f));
        quickBar.addView(quickCreate, new LinearLayout.LayoutParams(dp(56), dp(56)));
        screen.addView(quickBar, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout bottomNavigation = new LinearLayout(this);
        bottomNavigation.setOrientation(LinearLayout.HORIZONTAL); bottomNavigation.setGravity(Gravity.CENTER); bottomNavigation.setPadding(dp(8), dp(4), dp(8), dp(10)); bottomNavigation.setBackground(round(COLOR_PANEL, 26));
        loungeTab = tabButton(bottomNavigation, "▤\n게시물", () -> selectTab("lounge"));
        visitorsTab = tabButton(bottomNavigation, "♥\n방문", () -> selectTab("visitors"));
        chatTab = tabButton(bottomNavigation, "●\n대화", () -> selectTab("chat"));
        profileTab = tabButton(bottomNavigation, "♙\n내 계정", () -> selectTab("profile"));
        loungeTab.setTextSize(11); visitorsTab.setTextSize(11); chatTab.setTextSize(11); profileTab.setTextSize(11);
        screen.addView(bottomNavigation, new LinearLayout.LayoutParams(-1, -2));
        if (token.isEmpty()) {
            selectTab("profile");
        } else {
            buildReferenceUi();
            selectTab("feed");
            refresh();
        }
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
        if (referenceUi) { selectReferenceTab(tab); return; }
        // 계정 생성 전에는 앱 기능 화면을 열지 않고 연결 설정으로 돌린다.
        if (token.isEmpty() && !"profile".equals(tab)) tab = "profile";
        boolean lounge = "lounge".equals(tab);
        boolean visitors = "visitors".equals(tab);
        boolean chat = "chat".equals(tab);
        loungeSection.setVisibility(lounge ? View.VISIBLE : View.GONE);
        visitorsSection.setVisibility(visitors ? View.VISIBLE : View.GONE);
        chatSection.setVisibility(chat ? View.VISIBLE : View.GONE);
        profileSection.setVisibility(!lounge && !visitors && !chat ? View.VISIBLE : View.GONE);
        quickBar.setVisibility(lounge ? View.VISIBLE : View.GONE);
        styleTab(loungeTab, lounge);
        styleTab(visitorsTab, visitors);
        styleTab(chatTab, chat);
        styleTab(profileTab, !lounge && !visitors && !chat);
        if (visitors) refreshVisitors();
    }

    // 참고 화면의 정보 구조대로 피드·활동·대화 목록·내 프로필을 한 화면 껍데기에서 전환한다.
    private void buildReferenceUi() {
        referenceUi = true;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(COLOR_BG);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(COLOR_BG);
        LinearLayout pages = new LinearLayout(this); pages.setOrientation(LinearLayout.VERTICAL); pages.setPadding(dp(14), dp(8), dp(14), dp(8)); scroll.addView(pages);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        referenceFeed = referencePanel(pages);
        LinearLayout feedTop = referenceHeader("●", "", "게시물", "◉", () -> showVisitorsScreen());
        feedTop.getChildAt(0).setOnClickListener(view -> refresh());
        Button feedRequests = compactButton("ϟ", this::showReceivedRequestsScreen); feedRequests.setTextSize(23); feedRequests.setBackgroundColor(Color.TRANSPARENT); feedTop.addView(feedRequests, new LinearLayout.LayoutParams(dp(42), dp(50)));
        referenceFeed.addView(feedTop);
        HorizontalScrollView storyScroll = new HorizontalScrollView(this); storyScroll.setHorizontalScrollBarEnabled(false);
        storiesView = new LinearLayout(this); storiesView.setOrientation(LinearLayout.HORIZONTAL); storyScroll.addView(storiesView);
        referenceFeed.addView(storyScroll, new LinearLayout.LayoutParams(-1, dp(106)));
        TextView notice = new TextView(this); notice.setText("새로운 이야기를 남기고, 익명으로 대화를 시작해요."); notice.setTextColor(COLOR_TEXT); notice.setTextSize(16); notice.setPadding(dp(16), dp(14), dp(16), dp(14)); notice.setBackground(round(COLOR_FIELD, 16));
        LinearLayout.LayoutParams noticeParams = new LinearLayout.LayoutParams(-1, -2); noticeParams.setMargins(0, dp(6), 0, dp(8)); referenceFeed.addView(notice, noticeParams);
        postsView = column(referenceFeed);
        LinearLayout homeActions = new LinearLayout(this); homeActions.setGravity(Gravity.CENTER_VERTICAL); homeActions.setPadding(0, dp(12), 0, dp(4));
        Button match = compactButton("⌕", this::showRandomMatchDialog); match.setTextSize(27); match.setTextColor(COLOR_BG); match.setBackground(round(COLOR_BLUE, 28)); homeActions.addView(match, new LinearLayout.LayoutParams(dp(56), dp(56)));
        homeActions.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        Button create = compactButton("＋", this::showComposerChoice); create.setTextSize(30); create.setTextColor(COLOR_BG); create.setBackground(round(COLOR_BLUE, 28)); homeActions.addView(create, new LinearLayout.LayoutParams(dp(56), dp(56))); referenceFeed.addView(homeActions);

        referenceActivity = referencePanel(pages);
        referenceActivity.addView(referenceHeader("", "", "활동", "", null));
        addReferenceRow(referenceActivity, "내 글 · 내 댓글", "내가 남긴 게시물과 댓글", this::showMyActivity);
        addReferenceRow(referenceActivity, "받은 쪽지", "게시물과 스토리로 받은 쪽지", this::showInbox);

        referenceChats = referencePanel(pages);
        LinearLayout chatTop = new LinearLayout(this); chatTop.setGravity(Gravity.CENTER_VERTICAL); chatTop.setPadding(0, dp(2), 0, dp(8));
        TextView chatTitle = new TextView(this); chatTitle.setText("대화"); chatTitle.setTextColor(COLOR_TEXT); chatTitle.setTextSize(24); chatTitle.setTypeface(null, Typeface.BOLD); chatTop.addView(chatTitle, new LinearLayout.LayoutParams(0, dp(50), 1f));
        Button visitors = compactButton("◉", this::showVisitorsScreen); visitors.setTextSize(22); visitors.setBackgroundColor(Color.TRANSPARENT); chatTop.addView(visitors, new LinearLayout.LayoutParams(dp(46), dp(50)));
        Button requests = compactButton("ϟ", this::showReceivedRequestsScreen); requests.setTextSize(24); requests.setBackgroundColor(Color.TRANSPARENT); chatTop.addView(requests, new LinearLayout.LayoutParams(dp(46), dp(50)));
        Button settings = compactButton("⚙", this::showSettingsScreen); settings.setTextSize(21); settings.setBackgroundColor(Color.TRANSPARENT); chatTop.addView(settings, new LinearLayout.LayoutParams(dp(46), dp(50)));
        referenceChats.addView(chatTop);
        roomsView = column(referenceChats);

        referenceAccount = referencePanel(pages);
        referenceAccount.addView(referenceHeader("", "", "내 프로필", "⚙", this::showSettingsScreen));
        LinearLayout profileCard = new LinearLayout(this); profileCard.setGravity(Gravity.CENTER_VERTICAL); profileCard.setPadding(dp(16), dp(16), dp(16), dp(16)); profileCard.setBackground(round(COLOR_FIELD, 20));
        TextView avatar = new TextView(this); avatar.setText("●"); avatar.setTextSize(34); avatar.setTextColor(COLOR_BLUE); avatar.setGravity(Gravity.CENTER); avatar.setBackground(round(COLOR_PANEL, 34)); profileCard.addView(avatar, new LinearLayout.LayoutParams(dp(68), dp(68)));
        LinearLayout profileText = new LinearLayout(this); profileText.setOrientation(LinearLayout.VERTICAL); profileText.setPadding(dp(14), 0, 0, 0);
        accountNameView = new TextView(this); accountNameView.setText("내 프로필"); accountNameView.setTextColor(COLOR_TEXT); accountNameView.setTextSize(20); accountNameView.setTypeface(null, Typeface.BOLD); profileText.addView(accountNameView);
        accountStateView = new TextView(this); accountStateView.setText("프로필 수정"); accountStateView.setTextColor(COLOR_MUTED); accountStateView.setTextSize(14); profileText.addView(accountStateView);
        profileCard.addView(profileText, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = new TextView(this); arrow.setText("›"); arrow.setTextColor(COLOR_TEXT); arrow.setTextSize(34); profileCard.addView(arrow);
        profileCard.setOnClickListener(view -> showProfileEditor()); referenceAccount.addView(profileCard);
        referenceAccountRows = column(referenceAccount);
        addReferenceRow(referenceAccountRows, "내 활동 보기", "내가 작성한 게시물과 댓글", this::showMyActivity);
        addReferenceRow(referenceAccountRows, "내 프로필 조회자", "내 프로필을 확인한 사용자", this::showVisitorsScreen);
        addReferenceRow(referenceAccountRows, "프로필 사진 관리", "연결 수락 뒤 상대에게 공개", this::pickProfilePhoto);
        ownProfilePhotosView = column(referenceAccountRows);

        // 이전 미디어 흐름이 호출돼도 UI 상태가 깨지지 않도록 비표시 컨테이너를 유지한다.
        messagesView = new LinearLayout(this); photosView = new LinearLayout(this); videosView = new LinearLayout(this); peerProfilePhotosView = new LinearLayout(this);
        visitorsSection = new LinearLayout(this); visitorsView = new LinearLayout(this);
        loungeSection = referenceFeed; chatSection = referenceChats; profileSection = referenceAccount; quickBar = new LinearLayout(this);
        statusView = accountStateView;

        LinearLayout bottom = new LinearLayout(this); bottom.setGravity(Gravity.CENTER); bottom.setPadding(dp(10), dp(5), dp(10), dp(9)); bottom.setBackground(round(COLOR_PANEL, 28));
        loungeTab = tabButton(bottom, "▤\n게시물", () -> selectTab("feed"));
        chatTab = tabButton(bottom, "●\n대화", () -> selectTab("chat"));
        profileTab = tabButton(bottom, "♙\n프로필", () -> selectTab("profile"));
        loungeTab.setTextSize(11); chatTab.setTextSize(11); profileTab.setTextSize(11);
        root.addView(bottom, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        refreshAccountHeader();
    }

    private LinearLayout referencePanel(LinearLayout parent) {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setBackgroundColor(COLOR_BG); parent.addView(panel, new LinearLayout.LayoutParams(-1, -2)); return panel;
    }
    private LinearLayout referenceHeader(String left, String unused, String title, String right, Runnable rightAction) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(2), 0, dp(6));
        Button leftButton = compactButton(left, () -> { }); leftButton.setTextSize(22); leftButton.setBackgroundColor(Color.TRANSPARENT); row.addView(leftButton, new LinearLayout.LayoutParams(dp(48), dp(50)));
        TextView text = new TextView(this); text.setText(title); text.setTextColor(COLOR_TEXT); text.setTextSize(23); text.setTypeface(null, Typeface.BOLD); text.setGravity(Gravity.CENTER); row.addView(text, new LinearLayout.LayoutParams(0, dp(50), 1f));
        Button rightButton = compactButton(right, rightAction == null ? () -> { } : rightAction); rightButton.setTextSize(22); rightButton.setBackgroundColor(Color.TRANSPARENT); row.addView(rightButton, new LinearLayout.LayoutParams(dp(48), dp(50)));
        return row;
    }
    private void selectReferenceTab(String tab) {
        String selected = "lounge".equals(tab) ? "feed" : tab;
        if ("visitors".equals(selected)) { showVisitorsScreen(); return; }
        boolean feed = "feed".equals(selected), chat = "chat".equals(selected);
        referenceFeed.setVisibility(feed ? View.VISIBLE : View.GONE); referenceActivity.setVisibility(View.GONE); referenceChats.setVisibility(chat ? View.VISIBLE : View.GONE); referenceAccount.setVisibility(!feed && !chat ? View.VISIBLE : View.GONE);
        styleTab(loungeTab, feed); styleTab(chatTab, chat); styleTab(profileTab, !feed && !chat);
    }
    private void addReferenceRow(LinearLayout parent, String title, String detail, Runnable action) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(16), dp(12), dp(14), dp(12)); row.setBackground(round(COLOR_FIELD, 18));
        LinearLayout text = new LinearLayout(this); text.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = new TextView(this); titleView.setText(title); titleView.setTextColor(COLOR_TEXT); titleView.setTextSize(17); titleView.setTypeface(null, Typeface.BOLD); text.addView(titleView);
        if (!detail.isEmpty()) { TextView detailView = new TextView(this); detailView.setText(detail); detailView.setTextColor(COLOR_MUTED); detailView.setTextSize(13); detailView.setPadding(0, dp(3), 0, 0); text.addView(detailView); }
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f)); TextView arrow = new TextView(this); arrow.setText("›"); arrow.setTextColor(COLOR_TEXT); arrow.setTextSize(28); row.addView(arrow);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(8); parent.addView(row, params); row.setOnClickListener(view -> action.run());
    }
    private void addReferenceToggle(LinearLayout parent, String title, String key) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(16), dp(13), dp(14), dp(13)); row.setBackground(round(COLOR_FIELD, 18));
        TextView label = new TextView(this); label.setText(title); label.setTextColor(COLOR_TEXT); label.setTextSize(17); label.setTypeface(null, Typeface.BOLD); row.addView(label, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView toggle = new TextView(this); toggle.setGravity(Gravity.CENTER); toggle.setTextSize(13); row.addView(toggle, new LinearLayout.LayoutParams(dp(66), dp(38)));
        Runnable update = () -> { boolean enabled = getPreferences(MODE_PRIVATE).getBoolean(key, false); toggle.setText(enabled ? "ON" : "OFF"); toggle.setTextColor(enabled ? COLOR_BG : COLOR_TEXT); toggle.setBackground(round(enabled ? COLOR_BLUE : COLOR_PANEL, 19)); };
        update.run(); row.setOnClickListener(view -> { boolean enabled = getPreferences(MODE_PRIVATE).getBoolean(key, false); getPreferences(MODE_PRIVATE).edit().putBoolean(key, !enabled).apply(); update.run(); });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(8); parent.addView(row, params);
    }
    private LinearLayout auxiliaryRoot(String title, final android.app.Dialog[] dialog) {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(14), dp(10), dp(14), dp(10)); root.setBackgroundColor(COLOR_BG);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        Button close = compactButton("‹", () -> { }); close.setTextSize(34); close.setBackgroundColor(Color.TRANSPARENT); header.addView(close, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView heading = new TextView(this); heading.setText(title); heading.setTextColor(COLOR_TEXT); heading.setTextSize(21); heading.setTypeface(null, Typeface.BOLD); heading.setGravity(Gravity.CENTER); header.addView(heading, new LinearLayout.LayoutParams(0, dp(52), 1f));
        header.addView(new View(this), new LinearLayout.LayoutParams(dp(52), dp(52))); root.addView(header);
        close.setOnClickListener(view -> { if (dialog[0] != null) dialog[0].dismiss(); }); return root;
    }
    private void showVisitorsScreen() {
        background(() -> {
            JSONArray visitors = json("GET", "/api/profile/visitors", null).optJSONArray("visitors");
            runOnUiThread(() -> {
                final android.app.Dialog[] dialog = new android.app.Dialog[1]; LinearLayout root = auxiliaryRoot("프로필 방문자", dialog);
                ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(8), dp(20), dp(8), dp(8)); scroll.addView(list);
                if (visitors == null || visitors.length() == 0) { TextView empty = label(list, "아직 방문자가 없어요\n누군가 내 프로필을 보면 여기에 표시돼요."); empty.setTextSize(17); empty.setGravity(Gravity.CENTER); empty.setPadding(0, dp(180), 0, 0); }
                else for (int i = 0; i < visitors.length(); i++) { JSONObject visitor = visitors.optJSONObject(i); if (visitor == null) continue;
                    LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(10), dp(12), dp(8), dp(12));
                    TextView avatar = new TextView(this); avatar.setText("●"); avatar.setTextColor("male".equals(visitor.optString("gender")) ? COLOR_BLUE : COLOR_PINK); avatar.setTextSize(30); avatar.setGravity(Gravity.CENTER); row.addView(avatar, new LinearLayout.LayoutParams(dp(54), dp(54)));
                    TextView description = new TextView(this); description.setText("내 프로필을 확인했어요\n" + relativeTime(visitor.optLong("visitedAt"))); description.setTextColor(COLOR_TEXT); description.setTextSize(16); row.addView(description, new LinearLayout.LayoutParams(0, -2, 1f)); list.addView(row);
                }
                root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f)); dialog[0] = fullScreenDialog(root);
            });
        });
    }
    private void showReceivedRequestsScreen() {
        background(() -> {
            JSONArray requests = json("GET", "/api/requests/received", null).optJSONArray("requests");
            runOnUiThread(() -> {
                final android.app.Dialog[] dialog = new android.app.Dialog[1]; LinearLayout root = auxiliaryRoot("받은 채팅 요청", dialog);
                ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(8), dp(16), dp(8), dp(8)); scroll.addView(list);
                if (requests == null || requests.length() == 0) { TextView empty = label(list, "받은 채팅 요청이 없어요."); empty.setTextSize(17); empty.setGravity(Gravity.CENTER); empty.setPadding(0, dp(180), 0, 0); }
                else for (int i = 0; i < requests.length(); i++) { JSONObject request = requests.optJSONObject(i); if (request == null) continue;
                    String room = request.optString("roomId"); LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(14), dp(14), dp(14), dp(12)); row.setBackground(round(COLOR_FIELD, 18));
                    LinearLayout first = new LinearLayout(this); first.setGravity(Gravity.CENTER_VERTICAL); TextView avatar = new TextView(this); avatar.setText("●"); avatar.setTextColor("male".equals(request.optString("gender")) ? COLOR_BLUE : COLOR_PINK); avatar.setTextSize(28); first.addView(avatar, new LinearLayout.LayoutParams(dp(42), dp(40)));
                    TextView name = new TextView(this); name.setText("익명 사용자"); name.setTextColor(COLOR_TEXT); name.setTextSize(17); name.setTypeface(null, Typeface.BOLD); first.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
                    TextView time = new TextView(this); time.setText(relativeTime(request.optLong("createdAt"))); time.setTextColor(COLOR_MUTED); time.setTextSize(12); first.addView(time); row.addView(first);
                    TextView message = new TextView(this); message.setText(request.optString("initialMessage").isEmpty() ? "요청 메시지가 없습니다." : request.optString("initialMessage")); message.setTextColor(COLOR_TEXT); message.setTextSize(15); message.setPadding(dp(42), dp(4), 0, dp(10)); row.addView(message);
                    LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.RIGHT); Button reject = compactButton("거절", () -> background(() -> { json("POST", "/api/rooms/" + room + "/request/decision", new JSONObject().put("decision", "reject")); runOnUiThread(() -> { dialog[0].dismiss(); showReceivedRequestsScreen(); }); })); Button accept = compactButton("수락", () -> background(() -> { json("POST", "/api/rooms/" + room + "/request/decision", new JSONObject().put("decision", "accept")); runOnUiThread(() -> { dialog[0].dismiss(); selectTab("chat"); showRoomConversation(room); }); refresh(); })); accept.setTextColor(COLOR_BG); accept.setBackground(round(COLOR_BLUE, 14)); actions.addView(reject, new LinearLayout.LayoutParams(dp(78), dp(42))); actions.addView(accept, new LinearLayout.LayoutParams(dp(78), dp(42))); row.addView(actions);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(8); list.addView(row, params);
                }
                root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f)); dialog[0] = fullScreenDialog(root);
            });
        });
    }
    private void showSettingsScreen() {
        final android.app.Dialog[] dialog = new android.app.Dialog[1]; LinearLayout root = auxiliaryRoot("설정", dialog); ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(8), dp(8), dp(8), dp(20)); scroll.addView(list);
        settingsGroup(list, "알림"); addReferenceToggle(list, "대화 알림", "chatNotifications");
        settingsGroup(list, "앱 설정"); addReferenceRow(list, "서버 연결", "현재 서버 주소를 확인하거나 바꿉니다", this::showConnectionSettings);
        settingsGroup(list, "계정"); addReferenceRow(list, "계정 교체", "이 기기의 테스트 계정을 바꿉니다", () -> { if (dialog[0] != null) dialog[0].dismiss(); token = ""; roomId = ""; getPreferences(MODE_PRIVATE).edit().remove(PREF_TOKEN).apply(); recreate(); });
        settingsGroup(list, "정보"); addReferenceRow(list, "커뮤니티 가이드라인", "안전한 대화를 위한 기준", () -> notice("커뮤니티 가이드라인은 준비 중입니다."));
        TextView version = new TextView(this); version.setText("앱 버전 0.2"); version.setTextColor(COLOR_MUTED); version.setTextSize(14); version.setPadding(dp(16), dp(18), dp(16), dp(10)); list.addView(version);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f)); dialog[0] = fullScreenDialog(root);
    }
    private void settingsGroup(LinearLayout parent, String name) { TextView group = new TextView(this); group.setText(name); group.setTextColor(COLOR_MUTED); group.setTextSize(14); group.setTypeface(null, Typeface.BOLD); group.setPadding(dp(8), dp(18), 0, dp(3)); parent.addView(group); }
    private void showProfileEditor() {
        EditText nickname = dialogInput("닉네임", false); nickname.setText(accountNameView == null ? "" : accountNameView.getText());
        new android.app.AlertDialog.Builder(this).setTitle("프로필 수정").setView(nickname).setNegativeButton("취소", null).setPositiveButton("저장", (d, w) -> background(() -> { json("PATCH", "/api/profile", new JSONObject().put("nickname", nickname.getText().toString().trim()).put("intro", "")); refreshAccountHeader(); })).show();
    }
    private void refreshAccountHeader() {
        if (token.isEmpty()) return;
        background(() -> { JSONObject me = json("GET", "/api/me", null).optJSONObject("user"); runOnUiThread(() -> { if (me != null && accountNameView != null) { accountNameView.setText(me.optString("nickname", "내 프로필")); if (accountStateView != null) accountStateView.setText("프로필 수정"); } }); });
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
    // 피드와 대화는 작은 팝업이 아니라 앱 화면을 덮는 전용 화면으로 연다.
    // 랜덤 대화의 종료·요청 선택은 화면 아래에서 명확히 고르게 한다.
    private android.app.Dialog bottomSheet(LinearLayout content) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(COLOR_PANEL));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(-1, -2);
        }
        return dialog;
    }

    private void showConnectionRequestComposer(String id) {
        EditText input = dialogInput("첫 메시지를 입력하세요", false);
        new android.app.AlertDialog.Builder(this).setTitle("계속 대화 요청").setMessage("오늘 무료 요청 2회를 먼저 사용합니다.")
            .setView(input).setNegativeButton("취소", null).setPositiveButton("요청 보내기", (dialog, which) -> background(() -> {
                String message = input.getText().toString().trim(); if (message.isEmpty()) throw new IllegalArgumentException("첫 메시지를 입력하세요.");
                json("POST", "/api/rooms/" + id + "/request", new JSONObject().put("message", message));
                notice("대화 요청을 보냈습니다."); refresh();
            })).show();
    }

    private void showRandomExitOptions(String id, android.app.Dialog conversation) {
        LinearLayout sheet = new LinearLayout(this); sheet.setOrientation(LinearLayout.VERTICAL); sheet.setPadding(dp(20), dp(18), dp(20), dp(22)); sheet.setBackgroundColor(COLOR_PANEL);
        TextView title = new TextView(this); title.setText("랜덤 대화"); title.setTextColor(COLOR_TEXT); title.setTextSize(20); title.setTypeface(null, Typeface.BOLD); sheet.addView(title);
        TextView description = new TextView(this); description.setText("나가면 랜덤 대화 기록은 대화 목록에 남지 않습니다."); description.setTextColor(COLOR_MUTED); description.setTextSize(14); description.setPadding(0, dp(5), 0, dp(14)); sheet.addView(description);
        final android.app.Dialog[] popup = new android.app.Dialog[1];
        Button request = compactButton("계속 대화 요청", () -> { popup[0].dismiss(); showConnectionRequestComposer(id); }); request.setTextColor(COLOR_BG); request.setBackground(round(COLOR_BLUE, 16)); sheet.addView(request, new LinearLayout.LayoutParams(-1, dp(50)));
        Button block = compactButton("차단하고 나가기", () -> background(() -> { json("POST", "/api/rooms/" + id + "/block", null); runOnUiThread(() -> { popup[0].dismiss(); conversation.dismiss(); }); refresh(); })); block.setTextColor(COLOR_PINK); block.setBackground(round(COLOR_FIELD, 16)); LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(-1, dp(50)); blockParams.topMargin = dp(8); sheet.addView(block, blockParams);
        Button leave = compactButton("그냥 나가기", () -> background(() -> { json("POST", "/api/rooms/" + id + "/leave", null); runOnUiThread(() -> { popup[0].dismiss(); conversation.dismiss(); }); refresh(); })); leave.setTextColor(COLOR_TEXT); leave.setBackground(round(COLOR_FIELD, 16)); LinearLayout.LayoutParams leaveParams = new LinearLayout.LayoutParams(-1, dp(50)); leaveParams.topMargin = dp(8); sheet.addView(leave, leaveParams);
        Button cancel = compactButton("취소", () -> popup[0].dismiss()); cancel.setTextColor(COLOR_MUTED); cancel.setBackgroundColor(Color.TRANSPARENT); sheet.addView(cancel, new LinearLayout.LayoutParams(-1, dp(46)));
        popup[0] = bottomSheet(sheet);
    }

    private android.app.Dialog fullScreenDialog(View content) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(COLOR_BG));
            window.setLayout(-1, -1);
        }
        return dialog;
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
                if (result.optBoolean("waiting")) { waitForRandomMatch(); }
                else if (!result.optString("roomId").isEmpty()) {
                    String matchedRoomId = result.getString("roomId");
                    runOnUiThread(() -> { selectTab("chat"); showRoomConversation(matchedRoomId); });
                }
            })).show();
    }

    // 먼저 대기열에 들어간 사용자도 상대가 매칭하면 바로 대화 화면으로 이동한다.
    private void waitForRandomMatch() {
        if (waitingForMatch) return;
        waitingForMatch = true;
        background(() -> {
            JSONObject state = json("GET", "/api/state", null);
            JSONArray rooms = state.optJSONArray("rooms");
            String matchedRoom = "";
            if (rooms != null) for (int i = 0; i < rooms.length(); i++) {
                JSONObject room = rooms.optJSONObject(i);
                if (room != null && "random".equals(room.optString("status"))) { matchedRoom = room.optString("id"); break; }
            }
            if (!matchedRoom.isEmpty()) {
                String roomId = matchedRoom; waitingForMatch = false;
                runOnUiThread(() -> { selectTab("chat"); showRoomConversation(roomId); });
                return;
            }
            storyHandler.postDelayed(() -> { waitingForMatch = false; waitForRandomMatch(); }, 1800);
        });
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
        selectTab("visitors");
    }

    private void refreshVisitors() {
        if (token.isEmpty()) return;
        background(() -> {
            JSONArray visitors = json("GET", "/api/profile/visitors", null).optJSONArray("visitors");
            runOnUiThread(() -> {
                visitorsView.removeAllViews();
                if (visitors == null || visitors.length() == 0) {
                    TextView empty = label(visitorsView, "아직 방문자가 없어요\n누군가 내 프로필을 보면 여기에 표시돼요.");
                    empty.setTextSize(17); empty.setGravity(Gravity.CENTER); empty.setPadding(0, dp(160), 0, dp(160));
                    return;
                }
                for (int i = 0; i < visitors.length(); i++) {
                    JSONObject visitor = visitors.optJSONObject(i); if (visitor == null) continue;
                    LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14), dp(14), dp(14), dp(14)); row.setBackground(round(COLOR_FIELD, 18));
                    TextView icon = new TextView(this); icon.setText("●"); icon.setTextSize(28); icon.setTextColor("male".equals(visitor.optString("gender")) ? COLOR_BLUE : COLOR_PINK); row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(44)));
                    TextView text = new TextView(this); text.setText("프로필을 확인했어요\n" + relativeTime(visitor.optLong("visitedAt"))); text.setTextColor(COLOR_TEXT); text.setTextSize(15); row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(8); visitorsView.addView(row, params);
                }
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

    private String relativeTime(long time) {
        long minutes = Math.max(1, (System.currentTimeMillis() - time) / 60000);
        return minutes < 60 ? minutes + "분 전" : (minutes / 60) + "시간 전";
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

    // 동일 작성자의 스토리를 한 원으로 묶고, 열면 5초 간격으로 그 묶음 안에서만 넘긴다.
    private void addStoryCircle(JSONArray group) {
        JSONObject story = group.optJSONObject(0); if (story == null) return;
        String id = story.optString("id");
        LinearLayout item = new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER); item.setPadding(dp(4), 0, dp(6), 0);
        ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.CENTER_CROP); image.setBackground(round(story.optBoolean("mine") ? COLOR_BLUE : COLOR_PINK, 34)); image.setClipToOutline(true);
        item.addView(image, new LinearLayout.LayoutParams(dp(68), dp(68)));
        TextView caption = new TextView(this); caption.setText(story.optBoolean("mine") ? "내 스토리  +" : "스토리"); caption.setTextColor(COLOR_TEXT); caption.setTextSize(11); caption.setGravity(Gravity.CENTER);
        item.addView(caption, new LinearLayout.LayoutParams(dp(82), dp(30)));
        if (story.optBoolean("mine")) caption.setOnClickListener(view -> showStoryComposer());
        item.setOnClickListener(view -> { storySequence = group; openStory(story); }); storiesView.addView(item, new LinearLayout.LayoutParams(dp(88), -1));
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

    // 게시물 메뉴는 작성자 여부에 따라 실제 가능한 작업만 보여 준다.
    private void showPostMenu(JSONObject post, android.app.Dialog parent) {
        String id = post.optString("id");
        if (post.optBoolean("mine")) {
            new android.app.AlertDialog.Builder(this).setItems(new String[] { "게시물 삭제" }, (dialog, which) ->
                new android.app.AlertDialog.Builder(this).setTitle("게시물을 삭제할까요?").setMessage("댓글도 함께 삭제되며 되돌릴 수 없습니다.")
                    .setNegativeButton("취소", null).setPositiveButton("삭제", (confirm, ignored) -> background(() -> {
                        json("DELETE", "/api/posts/" + id, null);
                        runOnUiThread(() -> { parent.dismiss(); notice("게시물을 삭제했습니다."); }); refresh();
                    })).show()).show();
            return;
        }
        new android.app.AlertDialog.Builder(this).setItems(new String[] { "익명 쪽지 보내기", "차단하기", "신고하기" }, (dialog, which) -> {
            if (which == 0) showNoteComposer("/api/posts/" + id + "/message");
            else if (which == 1) background(() -> { json("POST", "/api/posts/" + id + "/block", null); runOnUiThread(parent::dismiss); refresh(); });
            else showReportComposer("/api/posts/" + id + "/report");
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
        if (referenceUi) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(12), dp(16), dp(12), dp(15));
            String title = post.optString("title").trim(), body = post.optString("body").trim();
            TextView titleView = new TextView(this); titleView.setText(title.isEmpty() ? body : title); titleView.setTextColor(COLOR_TEXT); titleView.setTextSize(21); titleView.setTypeface(null, Typeface.BOLD); row.addView(titleView);
            if (!title.isEmpty()) { TextView preview = new TextView(this); preview.setText(body); preview.setTextColor(COLOR_TEXT); preview.setTextSize(16); preview.setMaxLines(2); preview.setPadding(0, dp(8), 0, 0); row.addView(preview); }
            if (post.optBoolean("hasImage")) {
                ImageView previewImage = new ImageView(this); previewImage.setAdjustViewBounds(true); previewImage.setScaleType(ImageView.ScaleType.CENTER_CROP); previewImage.setBackground(round(COLOR_FIELD, 12));
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, dp(188)); imageParams.topMargin = dp(12); row.addView(previewImage, imageParams);
                String postId = post.optString("id");
                background(() -> {
                    byte[] bytes = request("GET", "/api/posts/" + postId + "/image", null, null);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    runOnUiThread(() -> previewImage.setImageBitmap(bitmap));
                });
            }
            TextView author = new TextView(this); author.setText(("male".equals(post.optString("gender")) ? "• " : "• ") + relativeTime(post.optLong("createdAt"))); author.setTextColor(COLOR_MUTED); author.setTextSize(13); author.setPadding(0, dp(12), 0, dp(8)); row.addView(author);
            TextView stats = new TextView(this); stats.setText("◉  " + post.optInt("viewCount") + "                         ◌  " + post.optInt("commentCount")); stats.setTextColor(COLOR_TEXT); stats.setTextSize(15); row.addView(stats);
            View divider = new View(this); divider.setBackgroundColor(COLOR_FIELD); LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1)); dividerParams.topMargin = dp(14); row.addView(divider, dividerParams);
            postsView.addView(row, new LinearLayout.LayoutParams(-1, -2)); row.setOnClickListener(view -> openPost(post)); return;
        }
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
                final android.app.Dialog[] postScreen = new android.app.Dialog[1];
                LinearLayout screen = new LinearLayout(this); screen.setOrientation(LinearLayout.VERTICAL); screen.setPadding(dp(12), dp(10), dp(12), dp(8)); screen.setBackgroundColor(COLOR_BG);
                LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
                Button back = compactButton("‹", () -> { }); back.setTextSize(34); back.setTextColor(COLOR_TEXT); back.setBackgroundColor(Color.TRANSPARENT); top.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));
                TextView topTitle = new TextView(this); topTitle.setText("게시물"); topTitle.setTextColor(COLOR_TEXT); topTitle.setTextSize(19); topTitle.setGravity(Gravity.CENTER); topTitle.setTypeface(null, Typeface.BOLD); top.addView(topTitle, new LinearLayout.LayoutParams(0, dp(52), 1f));
                Button options = compactButton("⋮", () -> { }); options.setTextSize(26); options.setBackgroundColor(Color.TRANSPARENT); top.addView(options, new LinearLayout.LayoutParams(dp(52), dp(52)));
                screen.addView(top, new LinearLayout.LayoutParams(-1, -2));
                ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
                LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(12), dp(16), dp(12), dp(14)); scroll.addView(body);
                TextView title = new TextView(this); title.setText(post.optString("title").trim().isEmpty() ? post.optString("body") : post.optString("title"));
                title.setTextColor(COLOR_TEXT); title.setTextSize(25); title.setTypeface(null, Typeface.BOLD); body.addView(title);
                if (!post.optString("title").trim().isEmpty()) {
                    TextView text = new TextView(this); text.setText(post.optString("body")); text.setTextColor(COLOR_TEXT); text.setTextSize(18); text.setPadding(0, dp(16), 0, dp(10)); body.addView(text);
                }
                if (loadedImage != null) { ImageView image = new ImageView(this); image.setImageBitmap(loadedImage); image.setAdjustViewBounds(true); image.setPadding(0, 0, 0, dp(12)); body.addView(image, new LinearLayout.LayoutParams(-1, -2)); }
                JSONArray comments = commentResult.optJSONArray("comments");
                int commentCount = comments == null ? 0 : comments.length();
                TextView stats = new TextView(this); stats.setText("◉  " + displayedViews + "                         ◌  " + commentCount); stats.setTextColor(COLOR_TEXT); stats.setTextSize(17); stats.setPadding(0, dp(16), 0, dp(18)); body.addView(stats);
                View divider = new View(this); divider.setBackgroundColor(COLOR_FIELD); body.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
                if (comments == null || comments.length() == 0) label(body, "아직 댓글이 없습니다.");
                else for (int i = 0; i < comments.length(); i++) addCommentRow(body, comments.optJSONObject(i));
                screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
                LinearLayout commentBar = new LinearLayout(this); commentBar.setGravity(Gravity.CENTER_VERTICAL); commentBar.setPadding(0, dp(8), 0, 0);
                EditText commentInput = dialogInput("댓글을 남겨주세요", false); commentBar.addView(commentInput, new LinearLayout.LayoutParams(0, dp(52), 1f));
                Button submit = compactButton("↑", () -> background(() -> {
                    String comment = commentInput.getText().toString().trim(); if (comment.isEmpty()) throw new IllegalArgumentException("댓글을 입력하세요.");
                    json("POST", "/api/posts/" + id + "/comments", new JSONObject().put("body", comment));
                    runOnUiThread(() -> { if (postScreen[0] != null) postScreen[0].dismiss(); openPost(post); }); refresh();
                })); submit.setTextSize(25); submit.setTextColor(COLOR_BG); submit.setBackground(round(COLOR_BLUE, 26)); commentBar.addView(submit, new LinearLayout.LayoutParams(dp(58), dp(52)));
                screen.addView(commentBar, new LinearLayout.LayoutParams(-1, -2));
                postScreen[0] = fullScreenDialog(screen);
                back.setOnClickListener(ignored -> postScreen[0].dismiss());
                options.setOnClickListener(ignored -> showPostMenu(post, postScreen[0]));
            });
        });
    }

    // 댓글과 한 단계 답글을 게시물 상세 안에서 바로 펼쳐 읽고 작성한다.
    private void addCommentRow(LinearLayout parent, JSONObject comment) {
        if (comment == null) return;
        String commentId = comment.optString("id");
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(0, dp(14), 0, dp(8));
        LinearLayout first = new LinearLayout(this); first.setGravity(Gravity.CENTER_VERTICAL);
        TextView marker = new TextView(this); marker.setText("•"); marker.setTextSize(26); marker.setTextColor("male".equals(comment.optString("gender")) ? COLOR_BLUE : COLOR_PINK); first.addView(marker, new LinearLayout.LayoutParams(dp(20), dp(30)));
        TextView text = new TextView(this); text.setText(comment.optString("body")); text.setTextColor(COLOR_TEXT); text.setTextSize(17); first.addView(text, new LinearLayout.LayoutParams(0, -2, 1f)); row.addView(first);
        LinearLayout replies = new LinearLayout(this); replies.setOrientation(LinearLayout.VERTICAL); replies.setPadding(dp(24), dp(4), 0, 0); row.addView(replies);
        Button reply = compactButton("↳ 답글 " + comment.optInt("replyCount"), () -> loadRepliesInline(replies, commentId)); reply.setTextColor(COLOR_MUTED); reply.setBackgroundColor(Color.TRANSPARENT);
        row.addView(reply, new LinearLayout.LayoutParams(-2, dp(34)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(6); parent.addView(row, params);
    }

    private void loadRepliesInline(LinearLayout container, String commentId) {
        if (container.getChildCount() > 0) { container.removeAllViews(); return; }
        background(() -> {
            JSONArray replies = json("GET", "/api/comments/" + commentId + "/replies", null).optJSONArray("replies");
            runOnUiThread(() -> {
                container.removeAllViews();
                if (replies != null) for (int i = 0; i < replies.length(); i++) {
                    JSONObject item = replies.optJSONObject(i); if (item == null) continue;
                    TextView line = new TextView(this); line.setText("↳ " + item.optString("body")); line.setTextColor(COLOR_TEXT); line.setTextSize(15); line.setPadding(0, dp(6), 0, dp(2)); container.addView(line);
                }
                EditText input = dialogInput("답글을 남겨주세요", false); input.setTextSize(14); LinearLayout compose = new LinearLayout(this); compose.setGravity(Gravity.CENTER_VERTICAL); compose.setPadding(0, dp(6), 0, 0); compose.addView(input, new LinearLayout.LayoutParams(0, dp(46), 1f));
                Button cancel = compactButton("취소", container::removeAllViews); cancel.setTextColor(COLOR_MUTED); cancel.setBackgroundColor(Color.TRANSPARENT); compose.addView(cancel, new LinearLayout.LayoutParams(dp(54), dp(46)));
                Button send = compactButton("↑", () -> background(() -> {
                    String body = input.getText().toString().trim(); if (body.isEmpty()) throw new IllegalArgumentException("답글을 입력하세요.");
                    json("POST", "/api/comments/" + commentId + "/replies", new JSONObject().put("body", body));
                    runOnUiThread(() -> loadRepliesInline(container, commentId)); refresh();
                })); send.setTextColor(COLOR_BG); send.setTextSize(20); send.setBackground(round(COLOR_BLUE, 22)); compose.addView(send, new LinearLayout.LayoutParams(dp(46), dp(46))); container.addView(compose);
            });
        });
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
        if (referenceUi) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(10), dp(10), dp(8), dp(10));
            JSONObject peer = room.optJSONObject("peer");
            TextView avatar = new TextView(this); avatar.setText("●"); avatar.setTextColor(peer != null && "male".equals(peer.optString("gender")) ? COLOR_BLUE : COLOR_PINK); avatar.setTextSize(34); avatar.setGravity(Gravity.CENTER); avatar.setBackground(round(COLOR_PANEL, 30)); row.addView(avatar, new LinearLayout.LayoutParams(dp(60), dp(60)));
            LinearLayout text = new LinearLayout(this); text.setOrientation(LinearLayout.VERTICAL); text.setPadding(dp(12), 0, dp(8), 0);
            String name = peer == null ? "대화 상대" : peer.optString("displayName", "대화 상대");
            TextView nameView = new TextView(this); nameView.setText(name); nameView.setTextColor(COLOR_TEXT); nameView.setTextSize(17); nameView.setTypeface(null, Typeface.BOLD); text.addView(nameView);
            String preview = room.optString("lastMessage"); if (preview.isEmpty()) preview = "random".equals(room.optString("status")) ? "랜덤 대화가 시작됐어요" : "대화를 시작해 보세요.";
            TextView previewView = new TextView(this); previewView.setText(preview); previewView.setTextColor(COLOR_MUTED); previewView.setTextSize(14); previewView.setSingleLine(true); previewView.setPadding(0, dp(4), 0, 0); text.addView(previewView);
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout side = new LinearLayout(this); side.setOrientation(LinearLayout.VERTICAL); side.setGravity(Gravity.RIGHT); TextView time = new TextView(this); time.setText(relativeTime(room.optLong("lastMessageAt", room.optLong("createdAt")))); time.setTextColor(COLOR_MUTED); time.setTextSize(11); side.addView(time);
            if (room.optInt("unreadCount") > 0) { TextView unread = new TextView(this); unread.setText(String.valueOf(room.optInt("unreadCount"))); unread.setTextColor(COLOR_BG); unread.setTextSize(11); unread.setGravity(Gravity.CENTER); unread.setBackground(round(COLOR_PINK, 12)); LinearLayout.LayoutParams unreadParams = new LinearLayout.LayoutParams(dp(24), dp(24)); unreadParams.topMargin = dp(5); side.addView(unread, unreadParams); }
            row.addView(side, new LinearLayout.LayoutParams(dp(54), dp(60))); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(82)); roomsView.addView(row, params); row.setOnClickListener(view -> action.run()); return;
        }
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

    // 대화 목록을 벗어나 방을 열 때만 메시지 입력과 전송 영역을 보여 준다.
    private void showRoomConversation(String id) {
        roomId = id;
        background(() -> {
            JSONObject room = json("GET", "/api/rooms/" + id, null);
            JSONArray messages = room.optJSONArray("messages");
            runOnUiThread(() -> {
                final android.app.Dialog[] conversation = new android.app.Dialog[1];
                LinearLayout screen = new LinearLayout(this); screen.setOrientation(LinearLayout.VERTICAL); screen.setPadding(dp(12), dp(10), dp(12), dp(8)); screen.setBackgroundColor(COLOR_BG);
                LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
                Button close = compactButton("‹", () -> { }); close.setTextSize(34); close.setTextColor(COLOR_TEXT); close.setBackgroundColor(Color.TRANSPARENT); top.addView(close, new LinearLayout.LayoutParams(dp(52), dp(52)));
                TextView title = new TextView(this); title.setText("대화"); title.setTextColor(COLOR_TEXT); title.setTextSize(20); title.setTypeface(null, Typeface.BOLD); title.setGravity(Gravity.CENTER); top.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1f));
                Button menu = compactButton("⋮", () -> { }); menu.setTextSize(26); menu.setBackgroundColor(Color.TRANSPARENT); top.addView(menu, new LinearLayout.LayoutParams(dp(52), dp(52)));
                screen.addView(top, new LinearLayout.LayoutParams(-1, -2));
                ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
                LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(8), dp(8), dp(8), dp(12)); scroll.addView(body);
                if (messages != null) for (int i = 0; i < messages.length(); i++) {
                    JSONObject message = messages.optJSONObject(i); if (message == null) continue;
                    boolean mine = message.optBoolean("mine");
                    LinearLayout row = new LinearLayout(this); row.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT); row.setPadding(0, dp(4), 0, dp(4));
                    LinearLayout bubbleStack = new LinearLayout(this); bubbleStack.setOrientation(LinearLayout.VERTICAL); bubbleStack.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT);
                    TextView bubble = new TextView(this); bubble.setText(message.optString("body")); bubble.setTextSize(16); bubble.setTextColor(mine ? COLOR_BG : COLOR_TEXT); bubble.setPadding(dp(14), dp(10), dp(14), dp(10)); bubble.setBackground(round(mine ? COLOR_BLUE : COLOR_FIELD, 18)); bubbleStack.addView(bubble);
                    TextView meta = new TextView(this); meta.setText(mine ? (message.optBoolean("read") ? "읽음  ✓✓" : "전송됨  ✓") : ""); meta.setTextColor(COLOR_MUTED); meta.setTextSize(11); meta.setPadding(dp(4), dp(2), dp(4), 0); bubbleStack.addView(meta);
                    row.addView(bubbleStack); body.addView(row);
                }
                if (room.optBoolean("peerTyping")) label(body, "상대가 입력 중입니다…");
                screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
                LinearLayout composeRow = new LinearLayout(this); composeRow.setGravity(Gravity.CENTER_VERTICAL); composeRow.setPadding(0, dp(8), 0, 0);
                Button photo = compactButton("＋", () -> { roomId = id; pickPhoto(); }); photo.setTextSize(26); photo.setBackgroundColor(Color.TRANSPARENT); composeRow.addView(photo, new LinearLayout.LayoutParams(dp(50), dp(52)));
                EditText compose = dialogInput("메시지를 입력해 주세요", false); composeRow.addView(compose, new LinearLayout.LayoutParams(0, dp(52), 1f));
                compose.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
                    @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                        if (text.toString().trim().isEmpty() || System.currentTimeMillis() - lastTypingAt < 1500) return;
                        lastTypingAt = System.currentTimeMillis(); background(() -> json("POST", "/api/rooms/" + id + "/typing", new JSONObject().put("typing", true)));
                    }
                    @Override public void afterTextChanged(Editable text) { }
                });
                Button send = compactButton("➤", () -> background(() -> {
                    String text = compose.getText().toString().trim(); if (text.isEmpty()) throw new IllegalArgumentException("메시지를 입력하세요.");
                    json("POST", "/api/rooms/" + id + "/messages", new JSONObject().put("body", text).put("clientId", UUID.randomUUID().toString()));
                    json("POST", "/api/rooms/" + id + "/typing", new JSONObject().put("typing", false));
                    runOnUiThread(() -> { if (conversation[0] != null) conversation[0].dismiss(); showRoomConversation(id); }); refresh();
                })); send.setTextSize(21); send.setTextColor(COLOR_BG); send.setBackground(round(COLOR_BLUE, 26)); composeRow.addView(send, new LinearLayout.LayoutParams(dp(58), dp(52)));
                screen.addView(composeRow, new LinearLayout.LayoutParams(-1, -2));
                conversation[0] = fullScreenDialog(screen);
                close.setOnClickListener(view -> conversation[0].dismiss());
                menu.setOnClickListener(view -> {
                    if ("random".equals(room.optString("status"))) showRandomExitOptions(id, conversation[0]);
                    else new android.app.AlertDialog.Builder(this).setTitle("대화 나가기").setMessage("대화방을 종료할까요?")
                        .setNegativeButton("취소", null).setPositiveButton("나가기", (dialog, which) -> background(() -> {
                            json("POST", "/api/rooms/" + id + "/leave", null);
                            runOnUiThread(conversation[0]::dismiss); refresh();
                        })).show();
                });
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            });
        });
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
                storiesView.removeAllViews();
                java.util.LinkedHashMap<String, JSONArray> storyGroups = new java.util.LinkedHashMap<>();
                for (int i = 0; i < stories.length(); i++) {
                    JSONObject story = stories.optJSONObject(i);
                    if (story == null) continue;
                    String ownerKey = story.optString("storyOwnerKey");
                    JSONArray group = storyGroups.get(ownerKey);
                    if (group == null) { group = new JSONArray(); storyGroups.put(ownerKey, group); }
                    group.put(story);
                }
                boolean haveMine = false;
                for (java.util.Map.Entry<String, JSONArray> entry : storyGroups.entrySet()) {
                    if (entry.getValue().optJSONObject(0).optBoolean("mine")) { addStoryCircle(entry.getValue()); haveMine = true; }
                }
                if (!haveMine) addStoryComposerCircle();
                for (java.util.Map.Entry<String, JSONArray> entry : storyGroups.entrySet()) {
                    if (!entry.getValue().optJSONObject(0).optBoolean("mine")) addStoryCircle(entry.getValue());
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
                int persistentRoomCount = 0;
                for (int i = 0; i < rooms.length(); i++) {
                    JSONObject room = rooms.optJSONObject(i);
                    if (room == null || !"connected".equals(room.optString("status"))) continue;
                    String id = room.optString("id");
                    addRoomCard(room, () -> showRoomConversation(id));
                    persistentRoomCount++;
                }
                if (persistentRoomCount == 0 && rooms.length() > 0) label(roomsView, "계속 대화 중인 상대가 없습니다.");
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
        storyHandler.removeCallbacksAndMessages(null);
        photoView.setImageDrawable(null);
        stopVideo();
        worker.shutdownNow();
        super.onDestroy();
    }
    @Override protected void onStop() {
        foreground = false;
        // 백그라운드에서는 스토리를 넘기거나 다음 다이얼로그를 열지 않는다.
        storyHandler.removeCallbacksAndMessages(null);
        photoView.setImageDrawable(null);
        stopVideo();
        super.onStop();
    }
    @Override protected void onStart() {
        super.onStart();
        foreground = true;
    }
}
