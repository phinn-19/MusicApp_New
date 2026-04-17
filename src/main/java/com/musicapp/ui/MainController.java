package com.musicapp.ui;

import com.musicapp.dao.PlaylistDao;
import com.musicapp.dao.SongDao;
import com.musicapp.model.ArtistPlayStat;
import com.musicapp.model.LibraryStats;
import com.musicapp.model.Playlist;
import com.musicapp.model.Song;
import com.musicapp.service.YtDlpSearchService;
import com.musicapp.util.AppState;
import com.musicapp.util.AppStateManager;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainController {

    @FXML private BorderPane mainRoot;
    @FXML private StackPane vinylPane;
    @FXML private Circle vinylDisc;
    @FXML private Circle albumArtCircle;
    @FXML private Label albumArtLabel;
    @FXML private StackPane squareArtBox;
    @FXML private ImageView squareAlbumArt;
    @FXML private Label squareArtLabel;
    @FXML private Label nowPlayingTitle;
    @FXML private Label nowPlayingArtist;
    @FXML private Label nowPlayingAlbum;
    @FXML private Slider progressSlider;
    @FXML private Label currentTimeLabel;
    @FXML private Label totalTimeLabel;
    @FXML private Button playPauseBtn;
    @FXML private Button shuffleBtn;
    @FXML private Button repeatBtn;
    @FXML private Button favoriteBtn;
    @FXML private Slider volumeSlider;
    @FXML private TextField streamUrlField;
    @FXML private Button miniPlayerBtn;
    @FXML private Button infoTabBtn;
    @FXML private Button lyricsTabBtn;
    @FXML private Button focusModeBtn;
    @FXML private Button immersiveModeBtn;

    @FXML private VBox sidebar;
    @FXML private VBox panelSongs;
    @FXML private VBox panelPlaylists;
    @FXML private Button tabSongsBtn;
    @FXML private Button tabPlaylistsBtn;
    @FXML private Button themeToggleBtn;
    @FXML private VBox playerView;
    @FXML private VBox searchView;

    @FXML private ListView<Song> songListView;
    @FXML private ListView<Playlist> playlistListView;
    @FXML private ListView<Song> playlistSongsView;
    @FXML private ComboBox<Playlist> playlistComboBox;

    @FXML private ComboBox<String> sortComboBox;
    @FXML private ComboBox<String> filterComboBox;

    @FXML private TextField searchField;
    @FXML private TextField internetSearchField;
    @FXML private ListView<Song> searchResultView;
    @FXML private Label searchResultTitle;
    @FXML private Label searchResultCount;
    @FXML private ComboBox<Playlist> searchPlaylistCombo;

    @FXML private VBox lyricsSidebar;
    @FXML private Label lyricsSongTitle;
    @FXML private Label lyricsArtistName;
    @FXML private TextArea lyricsArea;

    @FXML private ListView<Song> queueListView;
    @FXML private Label queueCountLabel;

    @FXML private Label recommendationTitleLabel;
    @FXML private ListView<Song> recommendationListView;

    @FXML private Label statusLabel;

    private final SongDao songDao = new SongDao();
    private final PlaylistDao playlistDao = new PlaylistDao();
    private final YtDlpSearchService ytDlpSearchService = new YtDlpSearchService();

    private final ObservableList<Song> songList = FXCollections.observableArrayList();
    private final ObservableList<Playlist> playlistList = FXCollections.observableArrayList();
    private final ObservableList<Song> playlistSongList = FXCollections.observableArrayList();
    private final ObservableList<Song> searchResultList = FXCollections.observableArrayList();
    private final ObservableList<Song> queueList = FXCollections.observableArrayList();
    private final ObservableList<Song> recommendationList = FXCollections.observableArrayList();
    private final Map<String, Image> songThumbnailCache = new ConcurrentHashMap<>();
    private final Set<String> songThumbnailLoading = ConcurrentHashMap.newKeySet();

    private RotateTransition vinylSpin;
    private Timeline progressTimeline;
    private MediaPlayer mediaPlayer;

    private boolean isPlaying = false;
    private boolean shuffleOn = false;
    private boolean repeatOn = false;
    private boolean sidebarVisible = true;
    private boolean lyricsVisible = true;
    private boolean darkMode = false;
    private boolean restoringState = false;
    private boolean focusMode = false;
    private boolean immersiveMode = false;
    private RightPanelMode rightPanelMode = RightPanelMode.INFO;
    private long lastProgressUiUpdateMillis = 0L;

    private Song currentSong;
    private double progressSeconds = 0;
    private long playbackToken = 0;
    private boolean isSeeking = false;

    private PlaybackSource currentPlaybackSource = PlaybackSource.ALL;

    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2, namedFactory("music-io"));
    private final ExecutorService metadataExecutor = Executors.newSingleThreadExecutor(namedFactory("music-meta"));
    private final PauseTransition saveStateDebounce = new PauseTransition(Duration.millis(350));

    private static final String CSS_LIGHT = "/com/musicapp/ui/musicapp.css";
    private static final String CSS_DARK = "/com/musicapp/ui/musicapp-dark.css";
    private static final String CSS_FOCUS = "/com/musicapp/ui/focus-mode.css";
    private static final String CSS_IMMERSIVE = "/com/musicapp/ui/immersive-mode.css";
    private static final Pattern YT_ID_PATTERN = Pattern.compile("(?:v=|youtu\\.be/|shorts/|embed/)([A-Za-z0-9_-]{11})");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "wav", "flac", "m4a", "ogg", "aac", "wma");
    private static final List<String> DESCRIPTION_NOISE_PREFIXES = List.of(
            "provided to youtube",
            "released on:",
            "lyricist:",
            "composer:",
            "auto-generated by youtube",
            "artist:",
            "album:",
            "genre:"
    );

    private Stage miniPlayerStage;
    private Label miniTitleLabel;
    private Label miniArtistLabel;
    private Button miniPlayPauseInnerBtn;
    private Slider miniProgressSlider;
    private Label miniCurrentTimeLabel;
    private Label miniTotalTimeLabel;

    private enum PlaybackSource {
        ALL, PLAYLIST, SEARCH
    }

    private enum RightPanelMode {
        INFO, LYRICS
    }

    @FXML
    public void initialize() {
        configureListViews();
        configureSortAndFilter();
        configureEvents();
        configureAnimation();
        configureProgress();
        configureProgressSlider();
        configureVolume();

        saveStateDebounce.setOnFinished(e -> saveAppStateNow());

        int duplicatesRemoved = songDao.removeDuplicates();
        if (duplicatesRemoved > 0) {
            System.out.println("Đã xóa " + duplicatesRemoved + " bài hát trùng khỏi database");
        }

        loadSongs();
        loadPlaylists();
        showSongsPanel();
        setAlbumArtDefault();
        updateFavoriteButton();
        updateQueueInfo();
        refreshRightPanelButtons();
        recommendationTitleLabel.setText("Đề xuất tiếp theo");
        recommendationListView.setItems(recommendationList);
        recommendationListView.setCellFactory(lv -> createSongCell());

        Runtime.getRuntime().addShutdownHook(new Thread(this::saveAppStateNow));
        Platform.runLater(this::restoreAppState);
    }

    private static ThreadFactory namedFactory(String baseName) {
        return r -> {
            Thread t = new Thread(r, baseName + "-" + UUID.randomUUID());
            t.setDaemon(true);
            return t;
        };
    }

    private void configureListViews() {
        songListView.setItems(songList);
        playlistListView.setItems(playlistList);
        playlistSongsView.setItems(playlistSongList);
        playlistComboBox.setItems(playlistList);
        searchResultView.setItems(searchResultList);
        searchPlaylistCombo.setItems(playlistList);
        queueListView.setItems(queueList);
        recommendationListView.setItems(recommendationList);

        songListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        playlistSongsView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        searchResultView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        queueListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        recommendationListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        playlistListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        songListView.setCellFactory(lv -> createSongCell());
        playlistSongsView.setCellFactory(lv -> createSongCell());
        searchResultView.setCellFactory(lv -> createSongCell());
        queueListView.setCellFactory(lv -> createSongCell());
        recommendationListView.setCellFactory(lv -> createSongCell());
    }

    private ListCell<Song> createSongCell() {
        return new ListCell<>() {
            private final StackPane thumbWrap = new StackPane();
            private final ImageView thumbView = new ImageView();
            private final Label thumbFallback = new Label("♪");
            private final Label titleLabel = new Label();
            private final Label metaLabel = new Label();
            private final VBox textBox = new VBox(3, titleLabel, metaLabel);
            private final HBox root = new HBox(12, thumbWrap, textBox);

            {
                thumbWrap.getStyleClass().add("song-thumb-wrap");
                thumbFallback.getStyleClass().add("song-thumb-fallback");
                thumbView.setFitWidth(48);
                thumbView.setFitHeight(48);
                thumbView.setPreserveRatio(false);
                thumbView.setSmooth(true);
                thumbView.setCache(true);

                Rectangle clip = new Rectangle(48, 48);
                clip.setArcWidth(12);
                clip.setArcHeight(12);
                thumbView.setClip(clip);

                thumbWrap.getChildren().addAll(thumbView, thumbFallback);

                titleLabel.getStyleClass().add("song-cell-title");
                metaLabel.getStyleClass().add("song-cell-meta");
                textBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(textBox, Priority.ALWAYS);
                root.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Song song, boolean empty) {
                super.updateItem(song, empty);
                if (empty || song == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                normalizeSongTextFields(song);
                titleLabel.setText(song.getTitle());

                String source = isYouTubeUrl(song.getFilePath()) ? "YouTube" : "Local";
                String meta = song.getArtist();
                if (song.getDuration() > 0) meta += "  |  " + song.getDurationFormatted();
                if (song.getPlayCount() > 0) meta += "  |  ▶ " + song.getPlayCount();
                meta += "  |  " + source;
                metaLabel.setText(meta);

                Image image = buildSongListThumbnail(song);
                thumbView.setImage(image);
                thumbFallback.setVisible(image == null || image.isError());

                setText(null);
                setGraphic(root);
            }
        };
    }

    private Image buildSongListThumbnail(Song song) {
        if (song == null) {
            return createPlaceholderImage(48, 48);
        }

        String key = song.getThumbnailUrl();
        if (key == null || key.isBlank()) {
            return createPlaceholderImage(48, 48);
        }

        Image cached = songThumbnailCache.get(key);
        if (cached != null) {
            return cached;
        }

        if (songThumbnailLoading.add(key)) {
            metadataExecutor.submit(() -> {
                try {
                    Image original = new Image(key, 96, 96, true, true, false);
                    Image cropped = (!original.isError() && original.getWidth() > 0)
                            ? cropToSquare(original)
                            : createPlaceholderImage(48, 48);
                    songThumbnailCache.put(key, cropped);
                } catch (Exception ignored) {
                    songThumbnailCache.put(key, createPlaceholderImage(48, 48));
                } finally {
                    songThumbnailLoading.remove(key);
                    Platform.runLater(this::refreshSongViews);
                }
            });
        }

        return createPlaceholderImage(48, 48);
    }

    private Image createPlaceholderImage(int width, int height) {
        WritableImage image = new WritableImage(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double ratio = (double) y / Math.max(1, height - 1);
                image.getPixelWriter().setColor(x, y, Color.color(0.18 + ratio * 0.10, 0.22 + ratio * 0.08, 0.26 + ratio * 0.10));
            }
        }
        return image;
    }

    private void configureSortAndFilter() {
        sortComboBox.setItems(FXCollections.observableArrayList(
                "Mới thêm", "Tên A-Z", "Ca sĩ A-Z", "Nghe gần đây", "Nghe nhiều nhất"
        ));
        sortComboBox.getSelectionModel().select("Mới thêm");
        sortComboBox.setOnAction(e -> reloadCurrentSongPanel());

        filterComboBox.setItems(FXCollections.observableArrayList(
                "Tất cả", "Yêu thích", "Nhạc local", "Online/Stream"
        ));
        filterComboBox.getSelectionModel().select("Tất cả");
        filterComboBox.setOnAction(e -> reloadCurrentSongPanel());
    }

    private void configureEvents() {
        songListView.setOnMouseClicked(e -> {
            Song selected = songListView.getSelectionModel().getSelectedItem();
            if (selected != null && e.getClickCount() >= 2) {
                currentPlaybackSource = PlaybackSource.ALL;
                playSong(selected);
            }
        });

        playlistSongsView.setOnMouseClicked(e -> {
            Song selected = playlistSongsView.getSelectionModel().getSelectedItem();
            if (selected != null && e.getClickCount() >= 2) {
                currentPlaybackSource = PlaybackSource.PLAYLIST;
                playSong(selected);
            }
        });

        playlistListView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                loadPlaylistSongs(newValue);
                scheduleSaveAppState();
            }
        });

        searchResultView.setOnMouseClicked(e -> {
            Song selected = searchResultView.getSelectionModel().getSelectedItem();
            if (selected != null && e.getClickCount() >= 2) {
                Song playable = ensureSongInLibrary(selected);
                currentPlaybackSource = PlaybackSource.SEARCH;
                playSong(playable);
            }
        });

        queueListView.setOnMouseClicked(e -> {
            Song selected = queueListView.getSelectionModel().getSelectedItem();
            if (selected != null && e.getClickCount() >= 2) {
                queueList.remove(selected);
                updateQueueInfo();
                playSong(selected);
            }
        });

        recommendationListView.setOnMouseClicked(e -> {
            Song selected = recommendationListView.getSelectionModel().getSelectedItem();
            if (selected != null && e.getClickCount() >= 2) {
                playSong(selected);
            }
        });

        if (streamUrlField != null) {
            streamUrlField.setOnAction(e -> onStreamUrl());
        }
        if (internetSearchField != null) {
            internetSearchField.setOnAction(e -> onSearchInternet());
        }
    }

    private void configureAnimation() {
        vinylSpin = new RotateTransition(Duration.seconds(4), vinylPane);
        vinylSpin.setByAngle(360);
        vinylSpin.setCycleCount(RotateTransition.INDEFINITE);
        vinylSpin.setInterpolator(Interpolator.LINEAR);
    }

    private void configureProgress() {
        progressTimeline = new Timeline(new KeyFrame(Duration.millis(250), e -> tickProgress()));
        progressTimeline.setCycleCount(Timeline.INDEFINITE);
    }

    private void configureProgressSlider() {
        progressSlider.setMin(0);
        progressSlider.setMax(100);
        progressSlider.setValue(0);

        progressSlider.setOnMousePressed(e -> isSeeking = true);
        progressSlider.setOnMouseReleased(e -> {
            if (currentSong == null) {
                isSeeking = false;
                return;
            }
            trySeekToSliderValue();
            isSeeking = false;
            scheduleSaveAppState();
        });
    }

    private void configureVolume() {
        volumeSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(newValue.doubleValue() / 100.0);
            }
            scheduleSaveAppState();
        });
    }

    private void trySeekToSliderValue() {
        if (currentSong == null) return;

        double percent = Math.max(0, Math.min(1, progressSlider.getValue() / 100.0));

        if (mediaPlayer != null) {
            Duration total = mediaPlayer.getTotalDuration();
            if (total != null && !total.isUnknown() && total.toSeconds() > 0) {
                double targetSeconds = total.toSeconds() * percent;
                mediaPlayer.seek(Duration.seconds(targetSeconds));
                progressSeconds = targetSeconds;
                currentTimeLabel.setText(formatTime((int) targetSeconds));
                refreshMiniPlayerUI();
                return;
            }
        }

        int total = currentSong.getDuration();
        if (total > 0) {
            progressSeconds = total * percent;
            currentTimeLabel.setText(formatTime((int) progressSeconds));
            refreshMiniPlayerUI();
        }
    }

    private void playSong(Song song) {
        playSong(song, 0, true);
    }

    private void playSong(Song song, double startSeconds, boolean autoStart) {
        if (song == null) return;

        playbackToken++;
        long currentToken = playbackToken;

        stopMedia();
        currentSong = song;
        progressSeconds = Math.max(0, startSeconds);

        if (startSeconds <= 0 && currentSong.getId() > 0) {
            songDao.markAsPlayed(song.getId());
            currentSong.setPlayCount(currentSong.getPlayCount() + 1);
            currentSong.setLastPlayedAt(LocalDateTime.now());
        }

        refreshSongViews();

        progressSlider.setValue(0);
        currentTimeLabel.setText(formatTime((int) progressSeconds));
        totalTimeLabel.setText(song.getDuration() > 0 ? song.getDurationFormatted() : "0:00");
        nowPlayingTitle.setText(song.getTitle());
        nowPlayingArtist.setText(song.getArtist());
        nowPlayingAlbum.setText(song.getAlbum());

        setAlbumArtDefault();
        updateLyricsPanel(song);
        applyBestArtwork(song, currentToken);
        updateFavoriteButton();
        loadRecommendations(song);

        setStatus("Đang phát: " + song.getTitle() + " - " + song.getArtist());
        scheduleSaveAppState();
        refreshMiniPlayerUI();

        String path = song.getFilePath();
        if (path == null || path.isBlank()) {
            if (autoStart) startProgressTimer(); else preparePausedState();
            seekToSavedProgressIfNeeded();
            return;
        }

        if (isYouTubeUrl(path)) {
            playYouTube(song, currentToken, autoStart);
        } else {
            String uri = isHttpUrl(path) ? path : new File(path).toURI().toString();
            playMedia(uri, song.getDuration(), currentToken, null, autoStart);
        }
    }

    private void preparePausedState() {
        isPlaying = false;
        playPauseBtn.setText("▶");
        if (miniPlayPauseInnerBtn != null) miniPlayPauseInnerBtn.setText("▶");
        if (vinylSpin != null) vinylSpin.pause();
        progressTimeline.pause();
    }

    private void seekToSavedProgressIfNeeded() {
        if (progressSeconds <= 0) return;

        if (mediaPlayer != null) {
            mediaPlayer.seek(Duration.seconds(progressSeconds));
        } else if (currentSong != null && currentSong.getDuration() > 0) {
            progressSlider.setValue((progressSeconds / currentSong.getDuration()) * 100.0);
        }
        currentTimeLabel.setText(formatTime((int) progressSeconds));
        refreshMiniPlayerUI();
    }

    private ObservableList<Song> getCurrentPlaybackList() {
        return switch (currentPlaybackSource) {
            case PLAYLIST -> playlistSongList;
            case SEARCH -> searchResultList;
            default -> songList;
        };
    }

    private void playYouTube(Song song, long token, boolean autoStart) {
        setStatus("Đang xử lý link YouTube...");
        ioExecutor.submit(() -> {
            YtDlpCommand ytDlp = findYtDlp();
            if (ytDlp == null) {
                Platform.runLater(() -> {
                    if (token != playbackToken) return;
                    showError("Máy này chưa có yt-dlp hoặc app chưa tìm thấy nó.\n\n1. Mở terminal\n2. Chạy: pip install yt-dlp\n3. Chạy lại app");
                    setStatus("Thiếu yt-dlp để xử lý YouTube");
                });
                return;
            }

            File cached = resolveCachedFile(song.getFilePath());
            if (cached != null && cached.exists()) {
                Platform.runLater(() -> {
                    if (token != playbackToken) return;
                    setStatus("Đang phát từ cache...");
                    playMedia(cached.toURI().toString(), song.getDuration(), token, null, autoStart);
                });
                return;
            }

            Platform.runLater(() -> setStatus("Đang tải bản cache local..."));
            File downloaded = downloadYoutubeAudio(ytDlp, song.getFilePath());

            if (downloaded != null && downloaded.exists()) {
                Platform.runLater(() -> {
                    if (token != playbackToken) return;
                    setStatus("Đang phát file YouTube đã tải...");
                    playMedia(downloaded.toURI().toString(), song.getDuration(), token, null, autoStart);
                });
                return;
            }

            String directUrl = extractDirectAudioUrl(ytDlp, song.getFilePath());
            if (directUrl != null) {
                Platform.runLater(() -> {
                    if (token != playbackToken) return;
                    setStatus("Đang stream YouTube...");
                    playMedia(directUrl, song.getDuration(), token, null, autoStart);
                });
                return;
            }

            Platform.runLater(() -> {
                if (token != playbackToken) return;
                handlePlaybackError("Không xử lý được link YouTube.");
            });
        });
    }

    private void playMedia(String uri, int durationHint, long token, Runnable onErrorFallback, boolean autoStart) {
        stopMedia();
        try {
            Media media = new Media(uri);
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);

            mediaPlayer.setOnReady(() -> {
                if (token != playbackToken) return;
                double total = mediaPlayer.getTotalDuration().toSeconds();
                int seconds = total > 0 ? (int) total : durationHint;
                totalTimeLabel.setText(seconds > 0 ? formatTime(seconds) : "0:00");
                if (!isSeeking) {
                    double sliderValue = 0;
                    if (seconds > 0 && progressSeconds > 0) {
                        sliderValue = (progressSeconds / seconds) * 100.0;
                    }
                    progressSlider.setValue(sliderValue);
                }

                if (autoStart) startPlayback(); else preparePausedState();
                seekToSavedProgressIfNeeded();
            });

            mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (token != playbackToken || mediaPlayer == null) return;
                double total = mediaPlayer.getTotalDuration().toSeconds();
                double current = newTime.toSeconds();
                progressSeconds = current;

                long now = System.currentTimeMillis();
                if (now - lastProgressUiUpdateMillis < 120) {
                    return;
                }
                lastProgressUiUpdateMillis = now;

                currentTimeLabel.setText(formatTime((int) current));
                if (total > 0 && !isSeeking) {
                    progressSlider.setValue((current / total) * 100.0);
                }
                if (miniPlayerStage != null && miniPlayerStage.isShowing()) {
                    refreshMiniPlayerUI();
                }
            });

            mediaPlayer.setOnEndOfMedia(() -> {
                if (token != playbackToken) return;
                stopPlayback();
                progressSeconds = 0;
                if (repeatOn) playSong(currentSong); else onNext();
            });

            mediaPlayer.setOnError(() -> {
                if (token != playbackToken) return;
                stopMedia();
                if (onErrorFallback != null) onErrorFallback.run();
                else handlePlaybackError(mediaPlayer.getError() != null ? mediaPlayer.getError().getMessage() : "Không phát được file.");
            });
        } catch (Exception e) {
            stopMedia();
            if (onErrorFallback != null) onErrorFallback.run();
            else handlePlaybackError(e.getMessage());
        }
    }

    private void stopMedia() {
        progressTimeline.stop();
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            try { mediaPlayer.dispose(); } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    private void startPlayback() {
        isPlaying = true;
        playPauseBtn.setText("⏸");
        if (miniPlayPauseInnerBtn != null) miniPlayPauseInnerBtn.setText("⏸");
        if (vinylSpin != null) vinylSpin.play();
        if (mediaPlayer != null) mediaPlayer.play(); else progressTimeline.play();
        scheduleSaveAppState();
        refreshMiniPlayerUI();
    }

    private void stopPlayback() {
        isPlaying = false;
        playPauseBtn.setText("▶");
        if (miniPlayPauseInnerBtn != null) miniPlayPauseInnerBtn.setText("▶");
        if (vinylSpin != null) vinylSpin.pause();

        if (mediaPlayer != null) {
            try { mediaPlayer.pause(); } catch (Exception ignored) {}
        } else {
            progressTimeline.pause();
        }

        refreshMiniPlayerUI();
        scheduleSaveAppState();
    }

    private void startProgressTimer() {
        isPlaying = true;
        playPauseBtn.setText("⏸");
        if (miniPlayPauseInnerBtn != null) miniPlayPauseInnerBtn.setText("⏸");
        if (vinylSpin != null) vinylSpin.play();
        progressTimeline.play();
        scheduleSaveAppState();
        refreshMiniPlayerUI();
    }

    private void tickProgress() {
        if (currentSong == null || !isPlaying) return;

        progressSeconds += 0.25;
        int total = currentSong.getDuration();
        if (total <= 0) {
            currentTimeLabel.setText(formatTime((int) progressSeconds));
            refreshMiniPlayerUI();
            return;
        }

        double progress = progressSeconds / total;
        if (progress >= 1.0) {
            if (!isSeeking) progressSlider.setValue(100.0);
            if (repeatOn) playSong(currentSong); else onNext();
            return;
        }

        if (!isSeeking) progressSlider.setValue(progress * 100.0);
        currentTimeLabel.setText(formatTime((int) progressSeconds));
        refreshMiniPlayerUI();
    }

    private void loadRecommendations(Song song) {
        if (song == null) {
            recommendationList.clear();
            return;
        }
        recommendationTitleLabel.setText("Đề xuất gần giống với: " + song.getTitle());
        recommendationList.setAll(songDao.getSimilarSongs(song, 8));
    }

    private void applyBestArtwork(Song song, long token) {
        if (song == null) return;

        if (song.getThumbnailUrl() != null && !song.getThumbnailUrl().isBlank()) {
            applyThumbnailIfAny(song);
            return;
        }

        if (isYouTubeUrl(song.getFilePath())) {
            metadataExecutor.submit(() -> {
                YouTubeMetadata metadata = fetchYouTubeMetadata(song.getFilePath());
                if (metadata != null && metadata.thumbnailUrl != null && !metadata.thumbnailUrl.isBlank()) {
                    song.setThumbnailUrl(metadata.thumbnailUrl);
                    if (song.getId() > 0) songDao.updateSong(song);
                    Platform.runLater(() -> {
                        if (currentSong != null && currentSong.getId() == song.getId() && token == playbackToken) {
                            applyThumbnailIfAny(song);
                        }
                    });
                } else {
                    metadataExecutor.submit(() -> fetchAlbumArtForSong(song, true, token));
                }
            });
        } else {
            metadataExecutor.submit(() -> fetchAlbumArtForSong(song, true, token));
        }
    }

    private boolean fetchAlbumArtForSong(Song song, boolean updateUiIfCurrent, long token) {
        try {
            if (song.getTitle().isBlank() || song.getArtist().isBlank()) return false;

            String query = URLEncoder.encode(song.getTitle() + " " + song.getArtist(), StandardCharsets.UTF_8);
            String apiUrl = "https://itunes.apple.com/search?term=" + query + "&media=music&limit=1";

            HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "MusicApp/1.0");

            try {
                if (conn.getResponseCode() != 200) return false;

                String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int idx = body.indexOf("\"artworkUrl100\":");
                if (idx < 0) return false;

                int start = body.indexOf('"', idx + 16) + 1;
                int end = body.indexOf('"', start);
                if (start <= 0 || end <= start) return false;

                String artUrl = body.substring(start, end).replace("100x100bb", "300x300bb");
                song.setThumbnailUrl(artUrl);
                if (song.getId() > 0) songDao.updateSong(song);

                if (updateUiIfCurrent) {
                    Platform.runLater(() -> {
                        if (token != playbackToken) return;
                        if (currentSong == null || currentSong.getId() != song.getId()) return;
                        applyThumbnailIfAny(song);
                    });
                }
                return true;
            } finally {
                conn.disconnect();
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private void setAlbumArtDefault() {
        albumArtCircle.setFill(Color.web("#1DB954"));
        albumArtLabel.setText("♪");
        albumArtLabel.setVisible(true);

        if (squareAlbumArt != null) {
            squareAlbumArt.setImage(null);
        }
        if (squareArtLabel != null) {
            squareArtLabel.setText("♪");
            squareArtLabel.setVisible(true);
            squareArtLabel.setManaged(true);
        }
    }

    @FXML
    private void onPlayPause() {
        if (currentSong == null) {
            if (!songList.isEmpty()) {
                currentPlaybackSource = PlaybackSource.ALL;
                songListView.getSelectionModel().select(0);
                playSong(songList.get(0));
            }
            return;
        }

        if (isPlaying) stopPlayback(); else startPlayback();
    }

    @FXML
    private void onPrev() {
        ObservableList<Song> list = getCurrentPlaybackList();
        if (list.isEmpty() || currentSong == null) return;

        int idx = findSongIndex(list, currentSong.getId());
        if (idx == -1) idx = 0;

        int prev = idx <= 0 ? list.size() - 1 : idx - 1;
        Song prevSong = list.get(prev);
        selectSongInCurrentContext(prevSong);
        playSong(prevSong);
    }

    @FXML
    private void onNext() {
        if (!queueList.isEmpty()) {
            Song queued = queueList.remove(0);
            updateQueueInfo();
            playSong(queued);
            return;
        }

        ObservableList<Song> list = getCurrentPlaybackList();
        if (list.isEmpty()) return;

        int idx = currentSong == null ? -1 : findSongIndex(list, currentSong.getId());
        if (idx == -1) idx = 0;

        int next = shuffleOn ? (int) (Math.random() * list.size()) : (idx >= list.size() - 1 ? 0 : idx + 1);

        Song nextSong = list.get(next);
        selectSongInCurrentContext(nextSong);
        playSong(nextSong);
    }

    @FXML private void onToggleShuffle() { shuffleOn = !shuffleOn; applyGhostToggle(shuffleBtn, shuffleOn); scheduleSaveAppState(); }
    @FXML private void onToggleRepeat() { repeatOn = !repeatOn; applyGhostToggle(repeatBtn, repeatOn); scheduleSaveAppState(); }

    @FXML
    private void onToggleFavorite() {
        if (currentSong == null || currentSong.getId() <= 0) {
            showError("Vui lòng chọn bài hát local / đã lưu trước!");
            return;
        }

        boolean newFavorite = !currentSong.isFavorite();
        if (songDao.updateFavorite(currentSong.getId(), newFavorite)) {
            currentSong.setFavorite(newFavorite);
            updateFavoriteButton();
            refreshSongViews();
            setStatus(newFavorite ? "Đã thêm vào yêu thích" : "Đã bỏ khỏi yêu thích");
        } else {
            showError("Không cập nhật được yêu thích!");
        }
    }

    private void updateFavoriteButton() {
        if (currentSong == null) {
            favoriteBtn.setText("♡");
            favoriteBtn.getStyleClass().removeAll("btn-ghost", "btn-ghost-on");
            favoriteBtn.getStyleClass().add("btn-ghost");
            return;
        }

        favoriteBtn.setText(currentSong.isFavorite() ? "♥" : "♡");
        applyGhostToggle(favoriteBtn, currentSong.isFavorite());
    }

    private void applyGhostToggle(Button btn, boolean on) {
        btn.getStyleClass().removeAll("btn-ghost", "btn-ghost-on");
        btn.getStyleClass().add(on ? "btn-ghost-on" : "btn-ghost");
    }

    @FXML
    private void onStreamUrl() {
        if (streamUrlField == null) return;
        String url = streamUrlField.getText().trim();
        if (url.isBlank()) {
            showError("Vui lòng dán link nhạc vào!");
            return;
        }

        boolean isYouTube = isYouTubeUrl(url);
        boolean isSoundCloud = url.contains("soundcloud.com");
        String tempTitle = buildTempTitle(url, isYouTube);

        Song streamSong = new Song(tempTitle, isYouTube ? "YouTube" : (isSoundCloud ? "SoundCloud" : "Online"), "", 0, url);

        ioExecutor.submit(() -> {
            Song target = ensureSongInLibrary(streamSong);
            Platform.runLater(() -> {
                addSongToListIfMissing(target);
                currentPlaybackSource = PlaybackSource.ALL;
                songListView.getSelectionModel().select(target);
                streamUrlField.clear();
                setStatus("Đã thêm: " + target.getTitle());
                scheduleSaveAppState();
            });

            if (isYouTube) refreshMetadataForSong(target, false);
        });
    }

    @FXML
    private void onToggleTheme() {
        darkMode = !darkMode;
        applyTheme();
        setStatus(darkMode ? "Đã bật dark mode" : "Đã bật light mode");
        scheduleSaveAppState();
    }

    @FXML
    private void onToggleFocusMode() {
        onToggleImmersiveMode();
    }

    @FXML
    private void onToggleImmersiveMode() {
        immersiveMode = !immersiveMode;
        focusMode = false;
        applyTheme();
        setStatus(immersiveMode ? "Đã bật chế độ đắm chìm" : "Đã trở lại giao diện thường");
        scheduleSaveAppState();
    }

    private void applyTheme() {
        if (sidebar == null || sidebar.getScene() == null) return;

        Scene scene = sidebar.getScene();
        List<String> sheets = new ArrayList<>();
        sheets.add(Objects.requireNonNull(getClass().getResource(darkMode ? CSS_DARK : CSS_LIGHT)).toExternalForm());
        if (focusMode) sheets.add(Objects.requireNonNull(getClass().getResource(CSS_FOCUS)).toExternalForm());
        if (immersiveMode) sheets.add(Objects.requireNonNull(getClass().getResource(CSS_IMMERSIVE)).toExternalForm());
        scene.getStylesheets().setAll(sheets);

        if (mainRoot != null) {
            mainRoot.getStyleClass().removeAll("focus-mode", "immersive-mode");
            if (focusMode) mainRoot.getStyleClass().add("focus-mode");
            if (immersiveMode) mainRoot.getStyleClass().add("immersive-mode");
        }

        if (themeToggleBtn != null) themeToggleBtn.setText(darkMode ? "☀" : "🌙");
        if (focusModeBtn != null) focusModeBtn.setVisible(false);
        if (immersiveModeBtn != null) immersiveModeBtn.setText(immersiveMode ? "⤫ Thoát đắm chìm" : "◎ Đắm chìm");

        updateLayoutMode();
        refreshMiniPlayerTheme();

        Platform.runLater(() -> {
            if (currentSong != null) {
                if (currentSong.getThumbnailUrl() != null && !currentSong.getThumbnailUrl().isBlank()) {
                    applyThumbnailIfAny(currentSong);
                } else {
                    setAlbumArtDefault();
                }
            } else {
                setAlbumArtDefault();
            }
        });
    }

    private void updateLayoutMode() {
        if (sidebar == null || lyricsSidebar == null || playerView == null) return;

        boolean immersive = immersiveMode;
        if (vinylPane != null) {
            vinylPane.setVisible(immersive);
            vinylPane.setManaged(immersive);
        }
        if (squareArtBox != null) {
            squareArtBox.setVisible(!immersive);
            squareArtBox.setManaged(!immersive);
        }

        if (immersive) {
            sidebar.setVisible(false);
            sidebar.setManaged(false);
            lyricsSidebar.setVisible(true);
            lyricsSidebar.setManaged(true);
            lyricsSidebar.setPrefWidth(420);
            lyricsSidebar.setMinWidth(420);
            lyricsSidebar.setMaxWidth(420);
            playerView.setSpacing(22);
            if (vinylPane != null) {
                vinylPane.setPrefSize(420, 420);
                vinylPane.setMaxSize(420, 420);
            }
            if (squareArtBox != null) {
                squareArtBox.setPrefSize(320, 320);
                squareArtBox.setMaxSize(320, 320);
            }
        } else {
            sidebar.setVisible(sidebarVisible);
            sidebar.setManaged(sidebarVisible);
            sidebar.setPrefWidth(350);
            sidebar.setMinWidth(350);
            sidebar.setMaxWidth(350);
            lyricsSidebar.setVisible(lyricsVisible);
            lyricsSidebar.setManaged(lyricsVisible);
            lyricsSidebar.setPrefWidth(280);
            lyricsSidebar.setMinWidth(280);
            lyricsSidebar.setMaxWidth(280);
            playerView.setSpacing(16);
            if (squareArtBox != null) {
                squareArtBox.setPrefSize(260, 260);
                squareArtBox.setMaxSize(260, 260);
            }
        }
    }

    private void refreshMiniPlayerTheme() {
        if (miniPlayerStage == null || miniPlayerStage.getScene() == null) return;
        List<String> sheets = new ArrayList<>();
        sheets.add(Objects.requireNonNull(getClass().getResource(darkMode ? CSS_DARK : CSS_LIGHT)).toExternalForm());
        miniPlayerStage.getScene().getStylesheets().setAll(sheets);
    }

    @FXML
    private void onToggleSidebar() {
        sidebarVisible = !sidebarVisible;
        if (!focusMode && !immersiveMode) {
            sidebar.setVisible(sidebarVisible);
            sidebar.setManaged(sidebarVisible);
        }
        scheduleSaveAppState();
    }

    @FXML private void onTabSongs() { showSongsPanel(); }
    @FXML private void onTabPlaylists() { showPlaylistsPanel(); }

    private void showSongsPanel() {
        setVisible(panelSongs, true);
        setVisible(panelPlaylists, false);
        setTabStyle(tabSongsBtn, true);
        setTabStyle(tabPlaylistsBtn, false);
    }

    private void showPlaylistsPanel() {
        setVisible(panelSongs, false);
        setVisible(panelPlaylists, true);
        setTabStyle(tabSongsBtn, false);
        setTabStyle(tabPlaylistsBtn, true);
    }

    private void setTabStyle(Button btn, boolean active) {
        btn.getStyleClass().removeAll("btn-tab-active", "btn-tab-inactive");
        btn.getStyleClass().add(active ? "btn-tab-active" : "btn-tab-inactive");
    }

    @FXML
    private void onToggleLyrics() {
        lyricsVisible = !lyricsVisible;
        if (!immersiveMode) {
            lyricsSidebar.setVisible(lyricsVisible);
            lyricsSidebar.setManaged(lyricsVisible);
        }
        scheduleSaveAppState();
    }

    private void updateLyricsPanel(Song song) {
        normalizeSongTextFields(song);
        lyricsSongTitle.setText(song.getTitle());
        lyricsArtistName.setText(song.getArtist());
        refreshRightPanelContent(song);
    }

    @FXML
    private void onShowSongInfo() {
        rightPanelMode = RightPanelMode.INFO;
        refreshRightPanelButtons();
        refreshRightPanelContent(currentSong);
    }

    @FXML
    private void onShowSongLyrics() {
        rightPanelMode = RightPanelMode.LYRICS;
        refreshRightPanelButtons();
        refreshRightPanelContent(currentSong);
    }

    private void refreshRightPanelButtons() {
        if (infoTabBtn != null) {
            infoTabBtn.getStyleClass().removeAll("lyrics-edit-btn", "lyrics-tab-active");
            infoTabBtn.getStyleClass().add(rightPanelMode == RightPanelMode.INFO ? "lyrics-tab-active" : "lyrics-edit-btn");
        }
        if (lyricsTabBtn != null) {
            lyricsTabBtn.getStyleClass().removeAll("lyrics-edit-btn", "lyrics-tab-active");
            lyricsTabBtn.getStyleClass().add(rightPanelMode == RightPanelMode.LYRICS ? "lyrics-tab-active" : "lyrics-edit-btn");
        }
        if (lyricsArea != null) {
            lyricsArea.setEditable(rightPanelMode == RightPanelMode.LYRICS);
            lyricsArea.setPromptText(rightPanelMode == RightPanelMode.INFO
                    ? "Thông tin bài hát sẽ hiện ở đây..."
                    : "Nhập hoặc chỉnh sửa lời bài hát...");
        }
    }

    private void refreshRightPanelContent(Song song) {
        refreshRightPanelButtons();
        if (lyricsArea == null) return;

        if (song == null) {
            lyricsArea.clear();
            return;
        }

        if (rightPanelMode == RightPanelMode.INFO) {
            lyricsArea.setText(buildSongInfoText(song));
        } else {
            lyricsArea.setText(song.getLyrics() == null ? "" : song.getLyrics());
        }
    }

    private String buildSongInfoText(Song song) {
        if (song == null) return "";
        String description = song.getDescription();
        if (description != null && !description.isBlank()) {
            return description.trim();
        }

        StringBuilder info = new StringBuilder();
        info.append("Tên bài: ").append(song.getTitle()).append("\n");
        info.append("Nghệ sĩ: ").append(song.getArtist()).append("\n");
        if (song.getAlbum() != null && !song.getAlbum().isBlank()) {
            info.append("Album: ").append(song.getAlbum()).append("\n");
        }
        if (song.getDuration() > 0) {
            info.append("Thời lượng: ").append(song.getDurationFormatted()).append("\n");
        }
        info.append("Nguồn: ").append(isYouTubeUrl(song.getFilePath()) ? "YouTube/Online" : "Local").append("\n");
        info.append("Lượt nghe: ").append(song.getPlayCount()).append("\n");
        if (song.getLastPlayedAt() != null) {
            info.append("Nghe gần nhất: ").append(song.getLastPlayedAt()).append("\n");
        }
        return info.toString().trim();
    }

    @FXML
    private void onSaveLyricsInline() {
        if (currentSong == null) {
            showError("Vui lòng chọn bài hát trước!");
            return;
        }

        Song editableSong = ensureSongInLibrary(currentSong);
        rightPanelMode = RightPanelMode.LYRICS;
        String text = lyricsArea.getText() == null ? "" : lyricsArea.getText().trim();
        editableSong.setLyrics(text);
        currentSong = editableSong;

        if (songDao.updateLyrics(editableSong.getId(), text)) {
            setStatus("Đã lưu lời: " + editableSong.getTitle());
            refreshSongViews();
            refreshRightPanelContent(editableSong);
        } else {
            showError("Lưu lời vào database thất bại!");
        }
    }

    @FXML
    private void onEditLyrics() {
        if (currentSong == null) {
            showError("Vui lòng chọn bài hát trước!");
            return;
        }

        Song editableSong = ensureSongInLibrary(currentSong);

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Nhập lời bài hát");
        dialog.setHeaderText(editableSong.getTitle() + " - " + editableSong.getArtist());

        ButtonType saveBtn = new ButtonType("Lưu", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        TextArea input = new TextArea(editableSong.getLyrics());
        input.setWrapText(true);
        input.setPrefRowCount(18);

        dialog.getDialogPane().setContent(input);
        dialog.setResultConverter(button -> button == saveBtn ? input.getText() : null);

        dialog.showAndWait().ifPresent(text -> {
            editableSong.setLyrics(text);
            currentSong = editableSong;
            rightPanelMode = RightPanelMode.LYRICS;
            refreshRightPanelContent(editableSong);

            if (songDao.updateLyrics(editableSong.getId(), text)) {
                refreshSongViews();
                setStatus("Đã lưu lời: " + editableSong.getTitle());
            } else {
                showError("Lưu lời vào database thất bại!");
            }
        });
    }

    @FXML
    private void onClearLyrics() {
        if (currentSong == null) return;

        Song editableSong = ensureSongInLibrary(currentSong);
        editableSong.setLyrics("");
        currentSong = editableSong;
        refreshRightPanelContent(editableSong);

        if (songDao.updateLyrics(editableSong.getId(), "")) {
            setStatus("Đã xóa lời bài hát");
            refreshSongViews();
        } else {
            showError("Xóa lời trong database thất bại!");
        }
    }

    @FXML
    private void onSearch() {
        String keyword = searchField.getText().trim();
        if (keyword.isBlank()) {
            onBackToPlayer();
            return;
        }

        List<Song> results = songDao.searchSongs(keyword, getSelectedSort(), getSelectedFilter());
        searchResultList.setAll(results);
        searchResultTitle.setText("Kết quả local: \"" + keyword + "\"");
        searchResultCount.setText("Tìm thấy " + results.size() + " bài hát trong thư viện");

        setVisible(playerView, false);
        setVisible(searchView, true);
        setStatus("Tìm thấy " + results.size() + " bài hát local");
    }

    @FXML
    private void onSearchInternet() {
        String keyword = internetSearchField == null ? "" : internetSearchField.getText().trim();
        if (keyword.isBlank()) {
            showError("Vui lòng nhập từ khóa để tìm Internet!");
            return;
        }

        setStatus("Đang tìm trên YouTube...");
        ioExecutor.submit(() -> {
            try {
                List<Song> results = ytDlpSearchService.searchYouTube(keyword, 12);
                results.forEach(this::normalizeSongTextFields);
                Platform.runLater(() -> {
                    searchResultList.setAll(results);
                    searchResultTitle.setText("Kết quả Internet: \"" + keyword + "\"");
                    searchResultCount.setText("Tìm thấy " + results.size() + " kết quả YouTube");
                    setVisible(playerView, false);
                    setVisible(searchView, true);
                    setStatus(results.isEmpty() ? "Không có kết quả Internet" : "Đã tải kết quả Internet");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Không tìm được trên Internet: " + e.getMessage()));
            }
        });
    }

    @FXML
    private void onBackToPlayer() {
        setVisible(searchView, false);
        setVisible(playerView, true);
    }

    @FXML
    private void onAddSearchResultToPlaylist() {
        List<Song> selectedSongs = new ArrayList<>(searchResultView.getSelectionModel().getSelectedItems());
        Playlist playlist = searchPlaylistCombo.getSelectionModel().getSelectedItem();

        if (selectedSongs.isEmpty()) {
            showError("Vui lòng chọn ít nhất 1 bài hát!");
            return;
        }
        if (playlist == null) {
            showError("Vui lòng chọn playlist!");
            return;
        }

        int added = 0;
        for (Song song : selectedSongs) {
            Song persistentSong = ensureSongInLibrary(song);
            if (!playlistDao.isSongInPlaylist(playlist.getId(), persistentSong.getId())
                    && playlistDao.addSongToPlaylist(playlist.getId(), persistentSong.getId())) {
                added++;
            }
        }

        loadPlaylists();
        if (added > 0) setStatus("Đã thêm " + added + " bài vào \"" + playlist.getName() + "\"");
        else showError("Không có bài nào được thêm mới!");
    }

    private void loadSongs() {
        songList.setAll(songDao.getAllSongs(getSelectedSort(), getSelectedFilter()));
        normalizeSongCollection(songList, true);
        setStatus("Đã tải " + songList.size() + " bài hát");
    }

    private void reloadCurrentSongPanel() {
        loadSongs();
        if (searchView.isVisible() && searchField != null && !searchField.getText().trim().isBlank()) onSearch();
    }

    private String getSelectedSort() { return sortComboBox.getValue() == null ? "Mới thêm" : sortComboBox.getValue(); }
    private String getSelectedFilter() { return filterComboBox.getValue() == null ? "Tất cả" : filterComboBox.getValue(); }

    private void refreshSongViews() {
        songListView.refresh();
        playlistSongsView.refresh();
        searchResultView.refresh();
        queueListView.refresh();
        recommendationListView.refresh();
    }

    private void addSongToListIfMissing(Song song) {
        normalizeSongTextFields(song);
        boolean exists = songList.stream().anyMatch(s -> Objects.equals(s.getFilePath(), song.getFilePath()));
        if (!exists) songList.add(0, song);
        refreshSongViews();
    }

    @FXML
    private void onAddSong() {
        String title = prompt("Thêm bài hát", "Tên bài hát:", "Tên:", "");
        if (title == null || title.isBlank()) return;

        String artist = prompt("Thêm bài hát", "Ca sĩ:", "Ca sĩ:", "");
        if (artist == null) return;

        String album = prompt("Thêm bài hát", "Album:", "Album:", "");
        String durStr = prompt("Thêm bài hát", "Thời lượng (giây):", "Giây:", "0");
        int duration = parseIntSafe(durStr);

        Song song = new Song(title.trim(), artist.trim(), album == null ? "" : album.trim(), duration, "");

        ioExecutor.submit(() -> {
            if (songDao.addSong(song)) {
                Platform.runLater(() -> {
                    reloadCurrentSongPanel();
                    setStatus("Đã thêm: " + song.getTitle());
                });
            } else {
                Platform.runLater(() -> showError("Không thêm được bài hát!"));
            }
        });
    }

    @FXML
    private void onEditSong() {
        Song song = resolveSelectedSongForActions();
        if (song == null) {
            showError("Vui lòng chọn bài hát để sửa!");
            return;
        }

        Song editableSong = ensureSongInLibrary(song);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Sửa bài hát");
        dialog.setHeaderText("Chỉnh sửa thông tin bài hát");

        ButtonType saveBtn = new ButtonType("Lưu", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        TextField titleField = new TextField(editableSong.getTitle());
        TextField artistField = new TextField(editableSong.getArtist());
        TextField albumField = new TextField(editableSong.getAlbum());
        TextField durationField = new TextField(String.valueOf(editableSong.getDuration()));
        TextField filePathField = new TextField(editableSong.getFilePath());
        TextField thumbnailField = new TextField(editableSong.getThumbnailUrl());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.add(new Label("Tên bài:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Ca sĩ:"), 0, 1);
        grid.add(artistField, 1, 1);
        grid.add(new Label("Album:"), 0, 2);
        grid.add(albumField, 1, 2);
        grid.add(new Label("Thời lượng (giây):"), 0, 3);
        grid.add(durationField, 1, 3);
        grid.add(new Label("File path / URL:"), 0, 4);
        grid.add(filePathField, 1, 4);
        grid.add(new Label("Thumbnail URL:"), 0, 5);
        grid.add(thumbnailField, 1, 5);
        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveBtn) return;

        editableSong.setTitle(titleField.getText().trim());
        editableSong.setArtist(artistField.getText().trim());
        editableSong.setAlbum(albumField.getText().trim());
        editableSong.setDuration(parseIntSafe(durationField.getText()));
        editableSong.setFilePath(filePathField.getText().trim());
        editableSong.setThumbnailUrl(thumbnailField.getText().trim());

        if (songDao.updateSong(editableSong)) {
            refreshSongViews();
            if (currentSong != null && currentSong.getId() == editableSong.getId()) {
                nowPlayingTitle.setText(editableSong.getTitle());
                nowPlayingArtist.setText(editableSong.getArtist());
                nowPlayingAlbum.setText(editableSong.getAlbum());
                totalTimeLabel.setText(editableSong.getDurationFormatted());
                applyThumbnailIfAny(editableSong);
                updateFavoriteButton();
                loadRecommendations(editableSong);
            }
            setStatus("Đã cập nhật bài hát: " + editableSong.getTitle());
        } else {
            showError("Không sửa được bài hát!");
        }
    }

    @FXML
    private void onImportSong() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn file nhạc");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("File nhạc", "*.mp3", "*.wav", "*.flac", "*.m4a", "*.ogg"),
                new FileChooser.ExtensionFilter("Tất cả file", "*.*")
        );

        File file = chooser.showOpenDialog(sidebar.getScene().getWindow());
        if (file == null) return;

        String baseName = file.getName().contains(".") ? file.getName().substring(0, file.getName().lastIndexOf('.')) : file.getName();

        String title = prompt("Import bài hát", "Tên bài:", "Tên:", baseName);
        if (title == null || title.isBlank()) return;

        String artist = prompt("Import bài hát", "Ca sĩ:", "Ca sĩ:", "");
        if (artist == null) artist = "";

        Song song = new Song(title.trim(), artist.trim(), "", 0, file.getAbsolutePath());
        if (songDao.addSong(song)) {
            reloadCurrentSongPanel();
            setStatus("Đã import: " + song.getTitle());
        } else {
            showError("Không import được!");
        }
    }

    @FXML
    private void onBulkImportFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Chọn thư mục nhạc");
        File folder = chooser.showDialog(sidebar.getScene().getWindow());
        if (folder == null) return;

        setStatus("Đang import thư mục...");
        ioExecutor.submit(() -> {
            int imported = 0;
            int skipped = 0;
            try (var paths = Files.walk(folder.toPath())) {
                List<Path> audioFiles = paths.filter(Files::isRegularFile).filter(this::isSupportedAudioFile).sorted().toList();
                for (Path path : audioFiles) {
                    Song song = buildSongFromFile(path.toFile());
                    if (songDao.addSong(song)) imported++; else skipped++;
                }
            } catch (IOException e) {
                Platform.runLater(() -> showError("Không đọc được thư mục: " + e.getMessage()));
                return;
            }

            int finalImported = imported;
            int finalSkipped = skipped;
            Platform.runLater(() -> {
                reloadCurrentSongPanel();
                setStatus("Bulk import xong: thêm " + finalImported + ", bỏ qua " + finalSkipped);
            });
        });
    }

    private boolean isSupportedAudioFile(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return AUDIO_EXTENSIONS.contains(ext);
    }

    private Song buildSongFromFile(File file) {
        String baseName = file.getName().contains(".") ? file.getName().substring(0, file.getName().lastIndexOf('.')) : file.getName();
        String title = baseName;
        String artist = "Unknown Artist";

        if (baseName.contains(" - ")) {
            String[] parts = baseName.split(" - ", 2);
            artist = parts[0].trim();
            title = parts[1].trim();
        }

        return new Song(title, artist, "", 0, file.getAbsolutePath());
    }

    @FXML
    private void onRefreshMetadata() {
        Song selected = resolveSelectedSongForActions();
        if (selected == null) {
            showError("Vui lòng chọn bài hát để refresh metadata!");
            return;
        }
        Song persistent = ensureSongInLibrary(selected);
        refreshMetadataForSong(persistent, true);
    }

    private List<Song> getSelectedSongsForBulkActions() {
        LinkedHashMap<String, Song> selected = new LinkedHashMap<>();
        collectSongs(selected, songListView.getSelectionModel().getSelectedItems());
        collectSongs(selected, playlistSongsView.getSelectionModel().getSelectedItems());
        collectSongs(selected, searchResultView.getSelectionModel().getSelectedItems());
        collectSongs(selected, recommendationListView.getSelectionModel().getSelectedItems());

        if (selected.isEmpty()) {
            Song fallback = resolveSelectedSongForActions();
            if (fallback != null) selected.put(songKey(fallback), fallback);
        }
        return new ArrayList<>(selected.values());
    }

    private List<Song> getSelectedQueueSongs() {
        LinkedHashMap<String, Song> selected = new LinkedHashMap<>();
        collectSongs(selected, queueListView.getSelectionModel().getSelectedItems());
        return new ArrayList<>(selected.values());
    }

    private List<Song> getSelectedPlaylistContextSongs() {
        LinkedHashMap<String, Song> selected = new LinkedHashMap<>();
        collectSongs(selected, playlistSongsView.getSelectionModel().getSelectedItems());
        return new ArrayList<>(selected.values());
    }

    private void collectSongs(Map<String, Song> target, List<Song> songs) {
        if (songs == null) return;
        for (Song song : songs) if (song != null) target.put(songKey(song), song);
    }

    private String songKey(Song song) {
        if (song == null) return UUID.randomUUID().toString();
        if (song.getId() > 0) return "id:" + song.getId();
        return "path:" + song.getFilePath() + "|title:" + song.getTitle() + "|artist:" + song.getArtist();
    }

    private Song resolveSelectedSongForActions() {
        Song song = songListView.getSelectionModel().getSelectedItem();
        if (song == null) song = playlistSongsView.getSelectionModel().getSelectedItem();
        if (song == null) song = searchResultView.getSelectionModel().getSelectedItem();
        if (song == null) song = queueListView.getSelectionModel().getSelectedItem();
        if (song == null) song = recommendationListView.getSelectionModel().getSelectedItem();
        if (song == null) song = currentSong;
        return song;
    }

    private Song ensureSongInLibrary(Song song) {
        if (song == null) return null;
        normalizeSongTextFields(song);
        if (song.getId() > 0) {
            repairSongIfEscaped(song);
            return song;
        }

        Song existing = songDao.getSongByFilePath(song.getFilePath());
        if (existing != null) {
            copySongUiFields(song, existing);
            repairSongIfEscaped(existing);
            return existing;
        }

        Song copy = new Song(song.getTitle(), song.getArtist(), song.getAlbum(), song.getDuration(), song.getFilePath());
        copy.setLyrics(song.getLyrics());
        copy.setDescription(song.getDescription());
        copy.setThumbnailUrl(song.getThumbnailUrl());
        copy.setFavorite(song.isFavorite());
        copy.setPlayCount(song.getPlayCount());
        copy.setLastPlayedAt(song.getLastPlayedAt());

        songDao.addSong(copy);
        Song reloaded = songDao.getSongByFilePath(copy.getFilePath());
        Song result = reloaded != null ? reloaded : copy;
        copySongUiFields(song, result);
        Platform.runLater(this::loadSongs);
        return result;
    }

    private void copySongUiFields(Song source, Song target) {
        if (source == null || target == null) return;
        if (target.getThumbnailUrl().isBlank() && !source.getThumbnailUrl().isBlank()) target.setThumbnailUrl(source.getThumbnailUrl());
        if (target.getLyrics().isBlank() && !source.getLyrics().isBlank()) target.setLyrics(source.getLyrics());
        if (target.getDescription().isBlank() && !source.getDescription().isBlank()) target.setDescription(source.getDescription());
    }

    private void refreshMetadataForSong(Song song, boolean showStatus) {
        ioExecutor.submit(() -> {
            boolean updated = false;
            String filePath = song.getFilePath();

            if (isYouTubeUrl(filePath)) {
                YouTubeMetadata metadata = fetchYouTubeMetadata(filePath);
                if (metadata != null) {
                    if (metadata.title != null && !metadata.title.isBlank()) song.setTitle(decodeUnicodeEscapes(metadata.title));
                    if (metadata.artist != null && !metadata.artist.isBlank()) song.setArtist(decodeUnicodeEscapes(metadata.artist));
                    if (metadata.duration > 0) song.setDuration(metadata.duration);
                    song.setDescription(metadata.description);

                    if (metadata.thumbnailUrl != null && !metadata.thumbnailUrl.isBlank()) song.setThumbnailUrl(metadata.thumbnailUrl);

                    String smarterLyrics = extractLyricsSmartly(metadata.description);
                    if (!smarterLyrics.isBlank()) song.setLyrics(smarterLyrics);

                    if (song.getId() > 0) updated = songDao.updateSong(song);
                }
            } else if (!song.getTitle().isBlank() && !song.getArtist().isBlank()) {
                updated = fetchAlbumArtForSong(song, true, playbackToken);
            }

            boolean finalUpdated = updated;
            Platform.runLater(() -> {
                refreshSongViews();
                if (currentSong != null && Objects.equals(currentSong.getFilePath(), song.getFilePath())) {
                    currentSong = song;
                    nowPlayingTitle.setText(song.getTitle());
                    nowPlayingArtist.setText(song.getArtist());
                    nowPlayingAlbum.setText(song.getAlbum());
                    totalTimeLabel.setText(song.getDuration() > 0 ? song.getDurationFormatted() : "0:00");
                    updateLyricsPanel(song);
                    applyBestArtwork(song, playbackToken);
                    loadRecommendations(song);
                }
                if (showStatus) setStatus(finalUpdated ? "Đã refresh metadata: " + song.getTitle() : "Không lấy được metadata mới");
            });
        });
    }

    private String extractLyricsSmartly(String description) {
        if (description == null || description.isBlank()) return "";

        String normalized = description.replace("\r\n", "\n").replace('\r', '\n').trim();

        int lyricsMarker = normalized.toLowerCase(Locale.ROOT).indexOf("lyrics:");
        if (lyricsMarker >= 0) normalized = normalized.substring(lyricsMarker + 7).trim();

        List<String> lines = new ArrayList<>();
        for (String rawLine : normalized.split("\n")) {
            String line = rawLine.trim();
            if (line.isBlank()) continue;

            String lower = line.toLowerCase(Locale.ROOT);
            boolean noise = DESCRIPTION_NOISE_PREFIXES.stream().anyMatch(lower::startsWith);
            if (noise) continue;
            if (line.startsWith("http://") || line.startsWith("https://")) continue;
            if (line.length() > 180) continue;
            lines.add(line);
        }

        String joined = String.join("\n", lines).trim();
        if (joined.length() > 4000) joined = joined.substring(0, 4000);
        return joined;
    }

    @FXML
    private void onDeleteSong() {
        List<Song> selectedSongs = getSelectedSongsForBulkActions();
        if (selectedSongs.isEmpty()) {
            showError("Vui lòng chọn bài hát!");
            return;
        }

        if (!confirm("Xóa " + selectedSongs.size() + " bài đã chọn?")) return;

        int deleted = 0;
        boolean removedCurrentSong = false;
        for (Song song : selectedSongs) {
            Song target = ensureSongInLibrary(song);
            if (target == null || target.getId() <= 0) continue;

            if (songDao.deleteSong(target.getId())) {
                deleted++;
                if (currentSong != null && Objects.equals(currentSong.getFilePath(), target.getFilePath())) removedCurrentSong = true;
                songList.removeIf(s -> Objects.equals(s.getFilePath(), target.getFilePath()));
                playlistSongList.removeIf(s -> Objects.equals(s.getFilePath(), target.getFilePath()));
                searchResultList.removeIf(s -> Objects.equals(s.getFilePath(), target.getFilePath()));
                queueList.removeIf(s -> Objects.equals(s.getFilePath(), target.getFilePath()));
                recommendationList.removeIf(s -> Objects.equals(s.getFilePath(), target.getFilePath()));
            }
        }

        if (removedCurrentSong) {
            stopMedia();
            stopPlayback();
            currentSong = null;
            progressSeconds = 0;
            nowPlayingTitle.setText("Chưa chọn bài");
            nowPlayingArtist.setText("—");
            nowPlayingAlbum.setText("");
            currentTimeLabel.setText("0:00");
            totalTimeLabel.setText("0:00");
            setAlbumArtDefault();
            updateFavoriteButton();
            recommendationList.clear();
            recommendationTitleLabel.setText("Đề xuất tiếp theo");
        }

        refreshSongViews();
        updateQueueInfo();
        if (deleted > 0) setStatus("Đã xóa " + deleted + " bài hát");
        else showError("Không xóa được bài nào!");
    }

    private void loadPlaylists() {
        playlistList.setAll(playlistDao.getAllPlaylists());
        playlistComboBox.setItems(playlistList);
        searchPlaylistCombo.setItems(playlistList);
    }

    private void loadPlaylistSongs(Playlist playlist) {
        playlistSongList.setAll(playlistDao.getSongsInPlaylist(playlist.getId()));
        setStatus("Playlist \"" + playlist.getName() + "\": " + playlistSongList.size() + " bài");
    }

    @FXML
    private void onAddPlaylist() {
        String name = prompt("Tạo playlist", "Tên playlist:", "Tên:", "");
        if (name == null || name.isBlank()) return;

        Playlist playlist = new Playlist(name.trim());
        if (playlistDao.addPlaylist(playlist)) {
            loadPlaylists();
            setStatus("Đã tạo: " + playlist.getName());
        } else {
            showError("Không tạo được playlist!");
        }
    }

    @FXML
    private void onRenamePlaylist() {
        Playlist playlist = playlistListView.getSelectionModel().getSelectedItem();
        if (playlist == null) {
            showError("Vui lòng chọn playlist để đổi tên!");
            return;
        }

        String newName = prompt("Đổi tên playlist", "Nhập tên mới:", "Tên mới:", playlist.getName());
        if (newName == null || newName.isBlank()) return;

        if (playlistDao.renamePlaylist(playlist.getId(), newName.trim())) {
            playlist.setName(newName.trim());
            loadPlaylists();
            playlistListView.refresh();
            setStatus("Đã đổi tên playlist");
        } else {
            showError("Không đổi tên được playlist!");
        }
    }

    @FXML
    private void onDeletePlaylist() {
        Playlist playlist = playlistListView.getSelectionModel().getSelectedItem();
        if (playlist == null) {
            showError("Vui lòng chọn playlist!");
            return;
        }

        if (!confirm("Xóa \"" + playlist.getName() + "\"?")) return;

        if (playlistDao.deletePlaylist(playlist.getId())) {
            playlistSongList.clear();
            loadPlaylists();
            setStatus("Đã xóa: " + playlist.getName());
        } else {
            showError("Không xóa được!");
        }
    }

    @FXML
    private void onAddSongToPlaylist() {
        List<Song> selectedSongs = getSelectedSongsForBulkActions();
        final Playlist playlist = playlistComboBox.getSelectionModel().getSelectedItem();

        if (selectedSongs.isEmpty()) {
            showError("Vui lòng chọn ít nhất 1 bài hát!");
            return;
        }
        if (playlist == null) {
            showError("Vui lòng chọn playlist!");
            return;
        }

        int added = 0;
        for (Song song : selectedSongs) {
            Song persistentSong = ensureSongInLibrary(song);
            if (persistentSong == null || persistentSong.getId() <= 0) continue;
            if (!playlistDao.isSongInPlaylist(playlist.getId(), persistentSong.getId())
                    && playlistDao.addSongToPlaylist(playlist.getId(), persistentSong.getId())) {
                added++;
            }
        }

        loadPlaylists();
        Playlist refreshedPlaylist = findPlaylistById(playlist.getId());
        if (refreshedPlaylist != null) {
            playlistComboBox.getSelectionModel().select(refreshedPlaylist);
            if (playlistListView.getSelectionModel().getSelectedItem() != null &&
                    playlistListView.getSelectionModel().getSelectedItem().getId() == refreshedPlaylist.getId()) {
                loadPlaylistSongs(refreshedPlaylist);
                playlistListView.getSelectionModel().select(refreshedPlaylist);
            }
        }

        if (added > 0) setStatus("Đã thêm " + added + " bài vào \"" + playlist.getName() + "\"");
        else showError("Không có bài nào được thêm mới!");
    }

    @FXML
    private void onRemoveSongFromPlaylist() {
        Playlist selectedFromList = playlistListView.getSelectionModel().getSelectedItem();
        Playlist selectedFromCombo = playlistComboBox.getSelectionModel().getSelectedItem();
        final Playlist playlist = (selectedFromList != null) ? selectedFromList : selectedFromCombo;
        List<Song> selectedSongs = getSelectedPlaylistContextSongs();

        if (playlist == null) {
            showError("Vui lòng chọn playlist!");
            return;
        }
        if (selectedSongs.isEmpty()) {
            showError("Vui lòng chọn ít nhất 1 bài hát trong playlist!");
            return;
        }

        int removed = 0;
        for (Song song : selectedSongs) if (playlistDao.removeSongFromPlaylist(playlist.getId(), song.getId())) removed++;

        loadPlaylistSongs(playlist);
        loadPlaylists();
        Playlist refreshed = findPlaylistById(playlist.getId());
        if (refreshed != null) {
            playlistListView.getSelectionModel().select(refreshed);
            playlistComboBox.getSelectionModel().select(refreshed);
        }

        if (removed > 0) setStatus("Đã xóa " + removed + " bài khỏi \"" + playlist.getName() + "\"");
        else showError("Không xóa được bài nào khỏi playlist!");
    }

    @FXML private void onMovePlaylistSongUp() { movePlaylistSong(true); }
    @FXML private void onMovePlaylistSongDown() { movePlaylistSong(false); }

    private void movePlaylistSong(boolean up) {
        Playlist playlist = playlistListView.getSelectionModel().getSelectedItem();
        Song song = playlistSongsView.getSelectionModel().getSelectedItem();
        if (playlist == null || song == null) {
            showError("Hãy chọn playlist và bài hát cần đổi vị trí!");
            return;
        }

        boolean ok = up ? playlistDao.moveSongUp(playlist.getId(), song.getId()) : playlistDao.moveSongDown(playlist.getId(), song.getId());

        if (ok) {
            loadPlaylistSongs(playlist);
            Song reselected = playlistSongList.stream().filter(s -> s.getId() == song.getId()).findFirst().orElse(null);
            if (reselected != null) {
                playlistSongsView.getSelectionModel().select(reselected);
                playlistSongsView.scrollTo(reselected);
            }
            setStatus(up ? "Đã đẩy bài hát lên trên" : "Đã đẩy bài hát xuống dưới");
        } else {
            setStatus("Không thể đổi vị trí thêm nữa");
        }
    }

    @FXML
    private void onAddToQueue() {
        List<Song> selectedSongs = getSelectedSongsForBulkActions();
        if (selectedSongs.isEmpty()) {
            showError("Vui lòng chọn bài hát để thêm vào queue!");
            return;
        }

        int added = 0;
        for (Song song : selectedSongs) {
            Song persistent = ensureSongInLibrary(song);
            if (persistent != null) {
                enqueueSong(persistent);
                added++;
            }
        }
        setStatus("Đã thêm " + added + " bài vào queue");
    }

    @FXML
    private void onRemoveFromQueue() {
        List<Song> selectedSongs = getSelectedQueueSongs();
        if (selectedSongs.isEmpty()) {
            showError("Vui lòng chọn bài trong queue!");
            return;
        }
        queueList.removeAll(selectedSongs);
        updateQueueInfo();
        setStatus("Đã xóa " + selectedSongs.size() + " bài khỏi queue");
    }

    @FXML
    private void onClearQueue() {
        queueList.clear();
        updateQueueInfo();
        setStatus("Đã xóa toàn bộ queue");
    }

    private void enqueueSong(Song song) {
        queueList.add(song);
        updateQueueInfo();
    }

    private void updateQueueInfo() {
        queueCountLabel.setText("Queue: " + queueList.size() + " bài");
        scheduleSaveAppState();
    }

    @FXML
    private void onShowLibraryStats() {
        LibraryStats stats = songDao.getLibraryStats();
        List<ArtistPlayStat> topArtists = songDao.getTopArtists(6);
        List<Song> topSongs = songDao.getTopPlayedSongs(8);

        VBox root = new VBox(18);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("main-area");

        Label title = new Label("Khám phá thư viện");
        title.getStyleClass().add("search-result-title");

        HBox cards = new HBox(14,
                createStatsCard("🎵", String.valueOf(stats.getTotalSongs()), "Bài hát"),
                createStatsCard("📁", String.valueOf(playlistList.size()), "Playlist"),
                createStatsCard("⏱", stats.getTotalDurationFormatted(), "Tổng thời gian"),
                createStatsCard("❤", String.valueOf(stats.getFavoriteSongs()), "Yêu thích"),
                createStatsCard("▶", String.valueOf(songDao.getTotalPlayCount()), "Lượt nghe")
        );

        VBox left = new VBox(10);
        left.getChildren().add(new Label("Top artist"));
        left.getChildren().get(0).getStyleClass().add("section-label");
        VBox artistBox = new VBox(8);
        artistBox.getStyleClass().add("info-card");
        for (int i = 0; i < topArtists.size(); i++) {
            Label l = new Label((i + 1) + ". " + topArtists.get(i));
            l.getStyleClass().add("song-cell-title");
            artistBox.getChildren().add(l);
        }
        left.getChildren().add(artistBox);
        VBox.setVgrow(artistBox, Priority.ALWAYS);

        VBox right = new VBox(10);
        right.getChildren().add(new Label("Nghe nhiều nhất"));
        right.getChildren().get(0).getStyleClass().add("section-label");
        VBox songBox = new VBox(8);
        songBox.getStyleClass().add("info-card");
        for (int i = 0; i < topSongs.size(); i++) {
            Song s = topSongs.get(i);
            VBox row = new VBox(2);
            Label t = new Label((i + 1) + ". " + s.getTitle());
            t.getStyleClass().add("song-cell-title");
            Label m = new Label(s.getArtist() + "  |  ▶ " + s.getPlayCount());
            m.getStyleClass().add("song-cell-meta");
            row.getChildren().addAll(t, m);
            songBox.getChildren().add(row);
        }
        right.getChildren().add(songBox);
        VBox.setVgrow(songBox, Priority.ALWAYS);

        HBox body = new HBox(16, left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        root.getChildren().addAll(title, cards, body);

        Scene scene = new Scene(root, 980, 620);
        List<String> sheets = new ArrayList<>();
        sheets.add(Objects.requireNonNull(getClass().getResource(darkMode ? CSS_DARK : CSS_LIGHT)).toExternalForm());
        scene.getStylesheets().setAll(sheets);

        Stage stage = new Stage();
        stage.initOwner(sidebar.getScene().getWindow());
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Thống kê thư viện");
        stage.setScene(scene);
        stage.showAndWait();
    }

    private VBox createStatsCard(String icon, String value, String label) {
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 22px;");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("search-result-title");
        valueLabel.setStyle("-fx-font-size: 28px;");
        Label subLabel = new Label(label);
        subLabel.getStyleClass().add("song-cell-meta");
        VBox card = new VBox(6, iconLabel, valueLabel, subLabel);
        card.getStyleClass().add("info-card");
        card.setPrefWidth(170);
        card.setAlignment(Pos.CENTER_LEFT);
        return card;
    }

    @FXML
    private void onToggleMiniPlayer() {
        if (miniPlayerStage != null && miniPlayerStage.isShowing()) {
            miniPlayerStage.hide();
            miniPlayerBtn.setText("▣ Mini");
            return;
        }
        showMiniPlayer();
    }

    private void showMiniPlayer() {
        if (miniPlayerStage == null) {
            miniTitleLabel = new Label("Chưa chọn bài");
            miniArtistLabel = new Label("—");
            miniCurrentTimeLabel = new Label("0:00");
            miniTotalTimeLabel = new Label("0:00");
            miniProgressSlider = new Slider(0, 100, 0);
            miniProgressSlider.setDisable(true);

            Button miniPrevBtn = new Button("⏮");
            miniPlayPauseInnerBtn = new Button("▶");
            Button miniNextBtn = new Button("⏭");

            miniPrevBtn.setOnAction(e -> onPrev());
            miniPlayPauseInnerBtn.setOnAction(e -> onPlayPause());
            miniNextBtn.setOnAction(e -> onNext());

            miniPrevBtn.getStyleClass().add("btn-prev-next");
            miniPlayPauseInnerBtn.getStyleClass().add("btn-play");
            miniNextBtn.getStyleClass().add("btn-prev-next");
            miniTitleLabel.getStyleClass().add("song-cell-title");
            miniArtistLabel.getStyleClass().add("song-cell-meta");
            miniCurrentTimeLabel.getStyleClass().add("time-label");
            miniTotalTimeLabel.getStyleClass().add("time-label");
            miniProgressSlider.getStyleClass().add("slider");

            HBox controls = new HBox(12, miniPrevBtn, miniPlayPauseInnerBtn, miniNextBtn);
            controls.setAlignment(Pos.CENTER);

            HBox times = new HBox(8, miniCurrentTimeLabel, new Pane(), miniTotalTimeLabel);
            HBox.setHgrow(times.getChildren().get(1), Priority.ALWAYS);

            VBox root = new VBox(12, miniTitleLabel, miniArtistLabel, times, miniProgressSlider, controls);
            root.setPadding(new Insets(16));
            root.setPrefWidth(360);
            root.getStyleClass().addAll("main-area", "mini-player-root");

            miniPlayerStage = new Stage();
            miniPlayerStage.initModality(Modality.NONE);
            miniPlayerStage.setTitle("MusicApp Mini Player");
            miniPlayerStage.setScene(new Scene(root));
            miniPlayerStage.setAlwaysOnTop(true);
            miniPlayerStage.setOnHidden(e -> {
                if (miniPlayerBtn != null) miniPlayerBtn.setText("▣ Mini");
            });
        }

        refreshMiniPlayerUI();
        miniPlayerStage.show();
        miniPlayerStage.toFront();
        miniPlayerBtn.setText("✕ Mini");
    }

    private void refreshMiniPlayerUI() {
        if (miniTitleLabel == null) return;

        if (currentSong == null) {
            miniTitleLabel.setText("Chưa chọn bài");
            miniArtistLabel.setText("—");
            miniCurrentTimeLabel.setText("0:00");
            miniTotalTimeLabel.setText("0:00");
            miniProgressSlider.setValue(0);
            miniPlayPauseInnerBtn.setText("▶");
            return;
        }

        miniTitleLabel.setText(currentSong.getTitle());
        miniArtistLabel.setText(currentSong.getArtist());
        miniCurrentTimeLabel.setText(currentTimeLabel.getText());
        miniTotalTimeLabel.setText(totalTimeLabel.getText());
        miniPlayPauseInnerBtn.setText(isPlaying ? "⏸" : "▶");

        if (currentSong.getDuration() > 0) miniProgressSlider.setValue((progressSeconds / currentSong.getDuration()) * 100.0);
        else miniProgressSlider.setValue(progressSlider.getValue());
    }

    private Playlist findPlaylistById(int playlistId) {
        return playlistList.stream().filter(p -> p.getId() == playlistId).findFirst().orElse(null);
    }

    private void setVisible(VBox node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void setStatus(String msg) {
        if (Platform.isFxApplicationThread()) statusLabel.setText(msg);
        else Platform.runLater(() -> statusLabel.setText(msg));
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
    }

    private boolean confirm(String msg) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private String prompt(String title, String header, String content, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);
        return dialog.showAndWait().orElse(null);
    }

    private void handlePlaybackError(String errorMessage) {
        setStatus("Lỗi phát nhạc: " + (errorMessage == null ? "Không xác định" : errorMessage));
        if (currentSong != null) startProgressTimer();
    }

    private boolean isHttpUrl(String path) {
        return path != null && (path.startsWith("http://") || path.startsWith("https://"));
    }

    private boolean isYouTubeUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("youtube.com") || lower.contains("youtu.be");
    }

    private YtDlpCommand findYtDlp() {
        String userHome = System.getProperty("user.home");
        List<List<String>> candidates = new ArrayList<>();

        candidates.add(List.of("yt-dlp"));
        candidates.add(List.of("yt-dlp.exe"));
        candidates.add(List.of("python", "-m", "yt_dlp"));
        candidates.add(List.of("py", "-m", "yt_dlp"));

        Path venvYtDlp = Paths.get(System.getProperty("user.dir"), "venv", "Scripts", "yt-dlp.exe");
        if (Files.exists(venvYtDlp)) candidates.add(List.of(venvYtDlp.toString()));

        Path dotVenvYtDlp = Paths.get(System.getProperty("user.dir"), ".venv", "Scripts", "yt-dlp.exe");
        if (Files.exists(dotVenvYtDlp)) candidates.add(List.of(dotVenvYtDlp.toString()));

        candidates.add(List.of(userHome + "\\AppData\\Local\\Programs\\Python\\Python311\\Scripts\\yt-dlp.exe"));
        candidates.add(List.of(userHome + "\\AppData\\Local\\Programs\\Python\\Python312\\Scripts\\yt-dlp.exe"));
        candidates.add(List.of(userHome + "/.local/bin/yt-dlp"));
        candidates.add(List.of("/usr/local/bin/yt-dlp"));
        candidates.add(List.of("/usr/bin/yt-dlp"));

        for (List<String> candidate : candidates) {
            try {
                List<String> versionCmd = new ArrayList<>(candidate);
                versionCmd.add("--version");
                Process process = new ProcessBuilder(versionCmd).redirectErrorStream(true).start();
                if (process.waitFor() == 0) return new YtDlpCommand(candidate);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String extractDirectAudioUrl(YtDlpCommand ytDlp, String youtubeUrl) {
        List<String> output = runCommand(ytDlp.withArgs(
                "-g", "-f", "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/best",
                "--no-playlist", "--no-warnings", "--encoding", "utf-8", youtubeUrl
        ));

        if (output == null) return null;
        for (String line : output) {
            String trimmed = line.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;
        }
        return null;
    }

    private File downloadYoutubeAudio(YtDlpCommand ytDlp, String youtubeUrl) {
        try {
            Path cacheDir = getCacheDir();
            String cacheKey = Optional.ofNullable(extractYouTubeVideoIdFromUrl(youtubeUrl)).orElse("cache_" + Math.abs(youtubeUrl.hashCode()));

            File existing = resolveCachedFile(youtubeUrl);
            if (existing != null && existing.exists()) return existing;

            String outputTemplate = cacheDir.resolve(cacheKey + ".%(ext)s").toString();
            List<String> output = runCommand(ytDlp.withArgs(
                    "-f", "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/best",
                    "-o", outputTemplate, "--no-playlist", "--newline", youtubeUrl
            ));
            if (output == null) return null;
            return resolveCachedFile(youtubeUrl);
        } catch (Exception e) {
            System.err.println("Lỗi tải YouTube audio: " + e.getMessage());
            return null;
        }
    }

    private File resolveCachedFile(String youtubeUrl) {
        try {
            String videoId = extractYouTubeVideoIdFromUrl(youtubeUrl);
            if (videoId == null) return null;

            File[] files = getCacheDir().toFile().listFiles();
            if (files == null) return null;

            for (File file : files) {
                String name = file.getName();
                if (name.startsWith(videoId) && !name.endsWith(".part")) return file;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Path getCacheDir() throws IOException {
        Path cacheDir = Paths.get(System.getProperty("user.dir"), "cache");
        Files.createDirectories(cacheDir);
        return cacheDir;
    }

    private List<String> runCommand(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) lines.add(line);
            }

            int exit = process.waitFor();
            return exit == 0 ? lines : null;
        } catch (Exception e) {
            System.err.println("Lỗi chạy command: " + e.getMessage());
            return null;
        }
    }

    private String extractYouTubeVideoIdFromUrl(String url) {
        if (url == null) return null;
        Matcher matcher = YT_ID_PATTERN.matcher(url);
        if (matcher.find()) return matcher.group(1);
        return null;
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIdx = json.indexOf(searchKey);
        if (startIdx == -1) return null;

        startIdx += searchKey.length();
        while (startIdx < json.length() && Character.isWhitespace(json.charAt(startIdx))) startIdx++;
        if (startIdx >= json.length()) return null;

        if (json.charAt(startIdx) == '"') {
            StringBuilder result = new StringBuilder();
            boolean escaped = false;
            for (int i = startIdx + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (escaped) {
                    switch (c) {
                        case 'n' -> result.append('\n');
                        case 't' -> result.append('\t');
                        case 'r' -> result.append('\r');
                        case '"', '\\', '/' -> result.append(c);
                        case 'u' -> {
                            if (i + 4 < json.length()) {
                                try {
                                    String hex = json.substring(i + 1, i + 5);
                                    result.append((char) Integer.parseInt(hex, 16));
                                    i += 4;
                                } catch (Exception ex) {
                                    result.append(c);
                                }
                            }
                        }
                        default -> result.append(c);
                    }
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    return result.toString();
                } else {
                    result.append(c);
                }
            }
            return result.toString();
        }

        int endIdx = json.indexOf(',', startIdx);
        if (endIdx == -1) endIdx = json.indexOf('}', startIdx);
        if (endIdx == -1) return null;
        return json.substring(startIdx, endIdx).trim();
    }

    private String buildTempTitle(String url, boolean isYouTube) {
        if (isYouTube) {
            String videoId = extractYouTubeVideoIdFromUrl(url);
            return videoId != null ? "YouTube: " + videoId : "YouTube stream";
        }

        String lastPart = url.substring(url.lastIndexOf('/') + 1);
        try {
            return URLDecoder.decode(lastPart, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return lastPart;
        }
    }

    private String formatTime(int secs) {
        if (secs < 0) secs = 0;
        return secs / 60 + ":" + String.format("%02d", secs % 60);
    }

    private Image cropToSquare(Image source) {
        if (source == null || source.isError()) return source;

        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        if (width <= 0 || height <= 0) return source;

        int size = Math.min(width, height);
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        PixelReader reader = source.getPixelReader();
        if (reader == null) return source;
        return new WritableImage(reader, x, y, size, size);
    }

    private void applyThumbnailIfAny(Song song) {
        String thumbnailUrl = song.getThumbnailUrl();
        if (thumbnailUrl == null || thumbnailUrl.isBlank()) return;

        try {
            Image image = new Image(thumbnailUrl, true);
            image.progressProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() >= 1.0 && !image.isError()) {
                    Platform.runLater(() -> {
                        if (currentSong != null && Objects.equals(currentSong.getFilePath(), song.getFilePath())) {
                            Image cropped = cropToSquare(image);
                            albumArtCircle.setFill(new ImagePattern(cropped));
                            albumArtLabel.setText("");
                            albumArtLabel.setVisible(false);
                            if (squareAlbumArt != null) {
                                squareAlbumArt.setImage(cropped);
                            }
                            if (squareArtLabel != null) {
                                squareArtLabel.setText("");
                                squareArtLabel.setVisible(false);
                                squareArtLabel.setManaged(false);
                            }
                        }
                    });
                }
            });
        } catch (Exception ignored) {}
    }

    private void restoreAppState() {
        restoringState = true;
        try {
            AppState state = AppStateManager.load();

            darkMode = state.isDarkMode();
            shuffleOn = state.isShuffleOn();
            repeatOn = state.isRepeatOn();
            lyricsVisible = state.isLyricsVisible();
            sidebarVisible = state.isSidebarVisible();
            focusMode = false;
            immersiveMode = state.isImmersiveMode();

            volumeSlider.setValue(state.getVolume());

            applyTheme();
            applyGhostToggle(shuffleBtn, shuffleOn);
            applyGhostToggle(repeatBtn, repeatOn);

            for (Integer songId : state.getQueueSongIds()) {
                Song queued = songDao.getSongById(songId);
                if (queued != null) {
                    repairSongIfEscaped(queued);
                    queueList.add(queued);
                }
            }
            queueListView.refresh();
            queueCountLabel.setText("Queue: " + queueList.size() + " bài");

            if (state.getSelectedPlaylistId() > 0) {
                Playlist playlist = findPlaylistById(state.getSelectedPlaylistId());
                if (playlist != null) {
                    playlistListView.getSelectionModel().select(playlist);
                    playlistComboBox.getSelectionModel().select(playlist);
                    loadPlaylistSongs(playlist);
                }
            }

            try {
                currentPlaybackSource = PlaybackSource.valueOf(state.getPlaybackSource());
            } catch (Exception ignored) {
                currentPlaybackSource = PlaybackSource.ALL;
            }

            if (state.getCurrentSongId() > 0) {
                Song savedSong = songDao.getSongById(state.getCurrentSongId());
                if (savedSong != null) {
                    repairSongIfEscaped(savedSong);
                    selectSongEverywhere(savedSong);
                    if (state.isWasPlaying()) {
                        playSong(savedSong, state.getProgressSeconds(), true);
                    } else {
                        currentSong = savedSong;
                        progressSeconds = Math.max(0, state.getProgressSeconds());
                        nowPlayingTitle.setText(savedSong.getTitle());
                        nowPlayingArtist.setText(savedSong.getArtist());
                        nowPlayingAlbum.setText(savedSong.getAlbum());
                        totalTimeLabel.setText(savedSong.getDuration() > 0 ? savedSong.getDurationFormatted() : "0:00");
                        currentTimeLabel.setText(formatTime((int) progressSeconds));
                        updateLyricsPanel(savedSong);
                        applyBestArtwork(savedSong, playbackToken);
                        updateFavoriteButton();
                        loadRecommendations(savedSong);
                        if (savedSong.getDuration() > 0) progressSlider.setValue((progressSeconds / savedSong.getDuration()) * 100.0);
                        preparePausedState();
                        refreshMiniPlayerUI();
                        setStatus("Đã khôi phục trạng thái app");
                    }
                }
            }
        } finally {
            restoringState = false;
        }
    }

    private void selectSongEverywhere(Song song) {
        Song fromAll = songList.stream().filter(s -> s.getId() == song.getId()).findFirst().orElse(null);
        if (fromAll != null) songListView.getSelectionModel().select(fromAll);

        Song fromPlaylist = playlistSongList.stream().filter(s -> s.getId() == song.getId()).findFirst().orElse(null);
        if (fromPlaylist != null) playlistSongsView.getSelectionModel().select(fromPlaylist);

        Song fromSearch = searchResultList.stream().filter(s -> Objects.equals(s.getFilePath(), song.getFilePath())).findFirst().orElse(null);
        if (fromSearch != null) searchResultView.getSelectionModel().select(fromSearch);
    }

    private void scheduleSaveAppState() {
        if (restoringState) return;
        Platform.runLater(saveStateDebounce::playFromStart);
    }

    private void saveAppStateNow() {
        if (restoringState) return;

        AppState state = new AppState();
        state.setDarkMode(darkMode);
        state.setShuffleOn(shuffleOn);
        state.setRepeatOn(repeatOn);
        state.setLyricsVisible(lyricsVisible);
        state.setSidebarVisible(sidebarVisible);
        state.setWasPlaying(isPlaying);
        state.setVolume(volumeSlider.getValue());
        state.setCurrentSongId(currentSong != null ? currentSong.getId() : -1);
        state.setFocusMode(focusMode);
        state.setImmersiveMode(immersiveMode);

        Playlist selectedPlaylist = playlistListView.getSelectionModel().getSelectedItem();
        if (selectedPlaylist == null) selectedPlaylist = playlistComboBox.getSelectionModel().getSelectedItem();
        state.setSelectedPlaylistId(selectedPlaylist != null ? selectedPlaylist.getId() : -1);
        state.setProgressSeconds(progressSeconds);
        state.setPlaybackSource(currentPlaybackSource.name());

        for (Song song : queueList) state.getQueueSongIds().add(song.getId());

        AppStateManager.save(state);
    }

    private YouTubeMetadata fetchYouTubeMetadata(String youtubeUrl) {
        YtDlpCommand ytDlp = findYtDlp();
        if (ytDlp == null) return null;

        List<String> output = runCommand(ytDlp.withArgs("--dump-json", "--no-playlist", "--encoding", "utf-8", youtubeUrl));

        if (output == null || output.isEmpty()) return null;

        String json = String.join("\n", output).trim();
        if (json.isBlank()) return null;

        YouTubeMetadata metadata = new YouTubeMetadata();
        metadata.title = Optional.ofNullable(extractJsonValue(json, "title")).orElse("YouTube");
        metadata.artist = Optional.ofNullable(extractJsonValue(json, "uploader")).orElse("YouTube");
        metadata.description = Optional.ofNullable(extractJsonValue(json, "description")).orElse("");
        metadata.thumbnailUrl = Optional.ofNullable(extractJsonValue(json, "thumbnail")).orElse("");
        String durationStr = extractJsonValue(json, "duration");
        metadata.duration = parseIntSafe(durationStr);
        return metadata;
    }

    private int findSongIndex(List<Song> list, int songId) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).getId() == songId) return i;
        return -1;
    }

    private void selectSongInCurrentContext(Song song) {
        if (song == null) return;

        switch (currentPlaybackSource) {
            case PLAYLIST -> {
                playlistSongsView.getSelectionModel().select(song);
                playlistSongsView.scrollTo(song);
            }
            case SEARCH -> {
                searchResultView.getSelectionModel().select(song);
                searchResultView.scrollTo(song);
            }
            default -> {
                songListView.getSelectionModel().select(song);
                songListView.scrollTo(song);
            }
        }
    }

    private void normalizeSongCollection(List<Song> songs, boolean persistIfChanged) {
        if (songs == null) return;
        for (Song song : songs) {
            if (song != null) {
                boolean changed = normalizeSongTextFields(song);
                if (changed && persistIfChanged && song.getId() > 0) songDao.updateSong(song);
            }
        }
    }

    private boolean normalizeSongTextFields(Song song) {
        if (song == null) return false;
        boolean changed = false;

        String decodedTitle = decodeUnicodeEscapes(song.getTitle());
        if (!decodedTitle.equals(song.getTitle())) {
            song.setTitle(decodedTitle);
            changed = true;
        }

        String decodedArtist = decodeUnicodeEscapes(song.getArtist());
        if (!decodedArtist.equals(song.getArtist())) {
            song.setArtist(decodedArtist);
            changed = true;
        }

        String decodedAlbum = decodeUnicodeEscapes(song.getAlbum());
        if (!decodedAlbum.equals(song.getAlbum())) {
            song.setAlbum(decodedAlbum);
            changed = true;
        }

        return changed;
    }

    private void repairSongIfEscaped(Song song) {
        if (song == null) return;
        boolean changed = normalizeSongTextFields(song);
        if (changed && song.getId() > 0) songDao.updateSong(song);
    }

    private String decodeUnicodeEscapes(String value) {
        if (value == null || value.isBlank() || !value.contains("\\u")) return value == null ? "" : value;

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 5 < value.length() && value.charAt(i + 1) == 'u') {
                String hex = value.substring(i + 2, i + 6);
                try {
                    out.append((char) Integer.parseInt(hex, 16));
                    i += 5;
                    continue;
                } catch (Exception ignored) {}
            }
            out.append(c);
        }
        return out.toString();
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value == null ? "0" : value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static class YtDlpCommand {
        private final List<String> baseCommand;

        YtDlpCommand(List<String> baseCommand) {
            this.baseCommand = baseCommand;
        }

        List<String> withArgs(String... args) {
            List<String> cmd = new ArrayList<>(baseCommand);
            cmd.addAll(Arrays.asList(args));
            return cmd;
        }
    }

    private static class YouTubeMetadata {
        String title;
        String artist;
        int duration;
        String description;
        String thumbnailUrl;
    }
}
