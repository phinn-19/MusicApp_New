package com.musicapp.util;

import java.util.ArrayList;
import java.util.List;

public class AppState {
    private boolean darkMode;
    private boolean shuffleOn;
    private boolean repeatOn;
    private boolean lyricsVisible = true;
    private boolean sidebarVisible = true;
    private boolean wasPlaying = false;
    private double volume = 70.0;
    private int currentSongId = -1;
    private int selectedPlaylistId = -1;
    private double progressSeconds = 0;
    private String playbackSource = "ALL";
    private boolean focusMode = false;
    private boolean immersiveMode = false;
    private final List<Integer> queueSongIds = new ArrayList<>();

    public boolean isDarkMode() { return darkMode; }
    public void setDarkMode(boolean darkMode) { this.darkMode = darkMode; }
    public boolean isShuffleOn() { return shuffleOn; }
    public void setShuffleOn(boolean shuffleOn) { this.shuffleOn = shuffleOn; }
    public boolean isRepeatOn() { return repeatOn; }
    public void setRepeatOn(boolean repeatOn) { this.repeatOn = repeatOn; }
    public boolean isLyricsVisible() { return lyricsVisible; }
    public void setLyricsVisible(boolean lyricsVisible) { this.lyricsVisible = lyricsVisible; }
    public boolean isSidebarVisible() { return sidebarVisible; }
    public void setSidebarVisible(boolean sidebarVisible) { this.sidebarVisible = sidebarVisible; }
    public boolean isWasPlaying() { return wasPlaying; }
    public void setWasPlaying(boolean wasPlaying) { this.wasPlaying = wasPlaying; }
    public double getVolume() { return volume; }
    public void setVolume(double volume) { this.volume = volume; }
    public int getCurrentSongId() { return currentSongId; }
    public void setCurrentSongId(int currentSongId) { this.currentSongId = currentSongId; }
    public int getSelectedPlaylistId() { return selectedPlaylistId; }
    public void setSelectedPlaylistId(int selectedPlaylistId) { this.selectedPlaylistId = selectedPlaylistId; }
    public double getProgressSeconds() { return progressSeconds; }
    public void setProgressSeconds(double progressSeconds) { this.progressSeconds = progressSeconds; }
    public String getPlaybackSource() { return playbackSource; }
    public void setPlaybackSource(String playbackSource) { this.playbackSource = playbackSource; }
    public boolean isFocusMode() { return focusMode; }
    public void setFocusMode(boolean focusMode) { this.focusMode = focusMode; }
    public boolean isImmersiveMode() { return immersiveMode; }
    public void setImmersiveMode(boolean immersiveMode) { this.immersiveMode = immersiveMode; }
    public List<Integer> getQueueSongIds() { return queueSongIds; }
}
