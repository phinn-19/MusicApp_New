package com.musicapp.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

public final class AppStateManager {
    private static final Path STATE_FILE = Paths.get(System.getProperty("user.home"), ".musicapp", "app-state.properties");

    private AppStateManager() {}

    public static AppState load() {
        AppState state = new AppState();
        if (!Files.exists(STATE_FILE)) return state;

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(STATE_FILE)) {
            props.load(in);
        } catch (IOException ignored) {
            return state;
        }

        state.setDarkMode(parseBoolean(props.getProperty("darkMode"), false));
        state.setShuffleOn(parseBoolean(props.getProperty("shuffleOn"), false));
        state.setRepeatOn(parseBoolean(props.getProperty("repeatOn"), false));
        state.setLyricsVisible(parseBoolean(props.getProperty("lyricsVisible"), true));
        state.setSidebarVisible(parseBoolean(props.getProperty("sidebarVisible"), true));
        state.setWasPlaying(parseBoolean(props.getProperty("wasPlaying"), false));
        state.setVolume(parseDouble(props.getProperty("volume"), 70.0));
        state.setCurrentSongId(parseInt(props.getProperty("currentSongId"), -1));
        state.setSelectedPlaylistId(parseInt(props.getProperty("selectedPlaylistId"), -1));
        state.setProgressSeconds(parseDouble(props.getProperty("progressSeconds"), 0));
        state.setPlaybackSource(props.getProperty("playbackSource", "ALL"));
        state.setFocusMode(parseBoolean(props.getProperty("focusMode"), false));
        state.setImmersiveMode(parseBoolean(props.getProperty("immersiveMode"), false));

        String queue = props.getProperty("queueSongIds", "").trim();
        if (!queue.isBlank()) {
            Arrays.stream(queue.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .mapToInt(s -> parseInt(s, -1))
                    .filter(id -> id > 0)
                    .forEach(id -> state.getQueueSongIds().add(id));
        }
        return state;
    }

    public static void save(AppState state) {
        try {
            Files.createDirectories(STATE_FILE.getParent());

            Properties props = new Properties();
            props.setProperty("darkMode", String.valueOf(state.isDarkMode()));
            props.setProperty("shuffleOn", String.valueOf(state.isShuffleOn()));
            props.setProperty("repeatOn", String.valueOf(state.isRepeatOn()));
            props.setProperty("lyricsVisible", String.valueOf(state.isLyricsVisible()));
            props.setProperty("sidebarVisible", String.valueOf(state.isSidebarVisible()));
            props.setProperty("wasPlaying", String.valueOf(state.isWasPlaying()));
            props.setProperty("volume", String.valueOf(state.getVolume()));
            props.setProperty("currentSongId", String.valueOf(state.getCurrentSongId()));
            props.setProperty("selectedPlaylistId", String.valueOf(state.getSelectedPlaylistId()));
            props.setProperty("progressSeconds", String.valueOf(state.getProgressSeconds()));
            props.setProperty("playbackSource", state.getPlaybackSource() == null ? "ALL" : state.getPlaybackSource());
            props.setProperty("focusMode", String.valueOf(state.isFocusMode()));
            props.setProperty("immersiveMode", String.valueOf(state.isImmersiveMode()));
            props.setProperty("queueSongIds", state.getQueueSongIds().stream().map(String::valueOf).collect(Collectors.joining(",")));

            try (OutputStream out = Files.newOutputStream(STATE_FILE)) {
                props.store(out, "MusicApp state");
            }
        } catch (IOException ignored) {}
    }

    private static boolean parseBoolean(String value, boolean fallback) { return value == null ? fallback : Boolean.parseBoolean(value); }
    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value == null ? "" : value.trim()); } catch (Exception e) { return fallback; }
    }
    private static double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value == null ? "" : value.trim()); } catch (Exception e) { return fallback; }
    }
}
