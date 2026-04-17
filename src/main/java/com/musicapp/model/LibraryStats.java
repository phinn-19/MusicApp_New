
package com.musicapp.model;

public class LibraryStats {
    private int totalSongs;
    private int totalDurationSeconds;
    private int favoriteSongs;
    private int localSongs;
    private int onlineSongs;

    public LibraryStats() {
    }

    public LibraryStats(int totalSongs, int totalDurationSeconds, int favoriteSongs, int localSongs, int onlineSongs) {
        this.totalSongs = totalSongs;
        this.totalDurationSeconds = totalDurationSeconds;
        this.favoriteSongs = favoriteSongs;
        this.localSongs = localSongs;
        this.onlineSongs = onlineSongs;
    }

    public int getTotalSongs() {
        return totalSongs;
    }

    public int getTotalDurationSeconds() {
        return totalDurationSeconds;
    }

    public int getFavoriteSongs() {
        return favoriteSongs;
    }

    public int getLocalSongs() {
        return localSongs;
    }

    public int getOnlineSongs() {
        return onlineSongs;
    }

    public String getTotalDurationFormatted() {
        int hours = totalDurationSeconds / 3600;
        int minutes = (totalDurationSeconds % 3600) / 60;
        int seconds = totalDurationSeconds % 60;
        if (hours > 0) {
            return hours + " giờ " + minutes + " phút";
        }
        if (minutes > 0) {
            return minutes + " phút " + seconds + " giây";
        }
        return seconds + " giây";
    }
}
