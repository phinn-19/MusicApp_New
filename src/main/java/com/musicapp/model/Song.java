package com.musicapp.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Song {
    private int id;
    private String title;
    private String artist;
    private String album;
    private int duration;
    private String filePath;

    private String lyrics;
    private String description;
    private String thumbnailUrl;

    private boolean favorite;
    private int playCount;
    private LocalDateTime lastPlayedAt;

    public Song() {
        this.lyrics = "";
        this.description = "";
        this.thumbnailUrl = "";
    }

    public Song(String title, String artist, String album, int duration, String filePath) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.filePath = filePath;
        this.lyrics = "";
        this.description = "";
        this.thumbnailUrl = "";
        this.favorite = false;
        this.playCount = 0;
        this.lastPlayedAt = null;
    }

    public Song(int id, String title, String artist, String album, int duration, String filePath) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.filePath = filePath;
        this.lyrics = "";
        this.description = "";
        this.thumbnailUrl = "";
        this.favorite = false;
        this.playCount = 0;
        this.lastPlayedAt = null;
    }

    public Song(
            int id,
            String title,
            String artist,
            String album,
            int duration,
            String filePath,
            String lyrics,
            String description,
            String thumbnailUrl,
            boolean favorite,
            int playCount,
            LocalDateTime lastPlayedAt
    ) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.filePath = filePath;
        this.lyrics = lyrics == null ? "" : lyrics;
        this.description = description == null ? "" : description;
        this.thumbnailUrl = thumbnailUrl == null ? "" : thumbnailUrl;
        this.favorite = favorite;
        this.playCount = playCount;
        this.lastPlayedAt = lastPlayedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }    

    public String getTitle() {
        return title == null ? "" : title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public String getArtist() {
        return artist == null ? "" : artist;
    }

    public void setArtist(String artist) {
        this.artist = artist == null ? "" : artist;
    }

    public String getAlbum() {
        return album == null ? "" : album;
    }

    public void setAlbum(String album) {
        this.album = album == null ? "" : album;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = Math.max(duration, 0);
    }

    public String getFilePath() {
        return filePath == null ? "" : filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath == null ? "" : filePath;
    }

    public String getLyrics() {
        return lyrics == null ? "" : lyrics;
    }

    public void setLyrics(String lyrics) {
        this.lyrics = lyrics == null ? "" : lyrics;
    }

    public String getDescription() {
        return description == null ? "" : description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl == null ? "" : thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl == null ? "" : thumbnailUrl;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public int getPlayCount() {
        return playCount;
    }

    public void setPlayCount(int playCount) {
        this.playCount = Math.max(playCount, 0);
    }

    public LocalDateTime getLastPlayedAt() {
        return lastPlayedAt;
    }

    public void setLastPlayedAt(LocalDateTime lastPlayedAt) {
        this.lastPlayedAt = lastPlayedAt;
    }

    public String getDurationFormatted() {
        int mins = duration / 60;
        int secs = duration % 60;
        return mins + ":" + String.format("%02d", secs);
    }

    public String getLastPlayedFormatted() {
        if (lastPlayedAt == null) return "Chưa phát";
        return lastPlayedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    @Override
    public String toString() {
        return title + " - " + artist;
    }
}