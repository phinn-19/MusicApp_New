
package com.musicapp.model;

public class ArtistPlayStat {
    private final String artistName;
    private final int songCount;
    private final int totalPlays;

    public ArtistPlayStat(String artistName, int songCount, int totalPlays) {
        this.artistName = artistName;
        this.songCount = songCount;
        this.totalPlays = totalPlays;
    }

    public String getArtistName() {
        return artistName;
    }

    public int getSongCount() {
        return songCount;
    }

    public int getTotalPlays() {
        return totalPlays;
    }

    @Override
    public String toString() {
        return artistName + " | " + songCount + " bài | " + totalPlays + " lượt phát";
    }
}
