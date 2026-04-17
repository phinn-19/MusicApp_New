package com.musicapp.model;

import java.util.ArrayList;
import java.util.List;

public class Playlist {
    private int id;
    private String name;
    private List<Song> songs;

    public Playlist(int id, String name, List<Song> songs) {
        this.id = id;
        this.name = name;
        this.songs = songs == null ? new ArrayList<>() : songs;
    }

    public Playlist(String name) {
        this.name = name;
        this.songs = new ArrayList<>();
    }

    public Playlist(String name, List<Song> songs) {
        this.name = name;
        this.songs = songs == null ? new ArrayList<>() : songs;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }    

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs == null ? new ArrayList<>() : songs;
    }

    public void addSong(Song song) {
        songs.add(song);
    }

    public void removeSong(Song song) {
        songs.remove(song);
    }

    public int getSongCount() {
        return songs == null ? 0 : songs.size();
    }

    @Override
    public String toString() {
        return name + " (" + getSongCount() + " bài)";
    }
}