package com.musicapp.dao;

import com.musicapp.database.DatabaseConnection;
import com.musicapp.model.ArtistPlayStat;
import com.musicapp.model.LibraryStats;
import com.musicapp.model.Song;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SongDao {

    public List<Song> getAllSongs() {
        return getAllSongs("Mới thêm", "Tất cả");
    }

    public List<Song> getAllSongs(String sortBy, String filterBy) {
        List<Song> songs = new ArrayList<>();

        StringBuilder sql = new StringBuilder("SELECT * FROM songs ");
        sql.append(buildFilterClause(filterBy));
        sql.append(" ");
        sql.append(buildOrderClause(sortBy));

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString());
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                songs.add(mapSong(rs));
            }
        } catch (SQLException e) {
            System.out.println("Lỗi lấy danh sách bài hát: " + e.getMessage());
        }

        return songs;
    }

    public Song getSongById(int songId) {
        String sql = "SELECT * FROM songs WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, songId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapSong(rs);
                }
            }
        } catch (SQLException e) {
            System.out.println("Lỗi lấy bài hát theo id: " + e.getMessage());
        }
        return null;
    }

    public Song getSongByFilePath(String filePath) {
        String sql = "SELECT * FROM songs WHERE file_path = ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, safe(filePath));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapSong(rs);
                }
            }
        } catch (SQLException e) {
            System.out.println("Lỗi lấy bài hát theo file_path: " + e.getMessage());
        }
        return null;
    }

    public List<Song> searchSongs(String keyword, String sortBy, String filterBy) {
        List<Song> songs = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
                "SELECT * FROM songs WHERE " +
                        "(title LIKE ? OR artist LIKE ? OR album LIKE ? OR lyrics LIKE ? OR description LIKE ?)"
        );

        String filterClause = buildFilterClause(filterBy);
        if (!filterClause.isBlank()) {
            sql.append(" AND ").append(filterClause.replaceFirst("^WHERE\\s+", ""));
        }

        sql.append(" ").append(buildOrderClause(sortBy));

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            String like = "%" + safe(keyword) + "%";
            stmt.setString(1, like);
            stmt.setString(2, like);
            stmt.setString(3, like);
            stmt.setString(4, like);
            stmt.setString(5, like);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    songs.add(mapSong(rs));
                }
            }
        } catch (SQLException e) {
            System.out.println("Lỗi tìm kiếm bài hát: " + e.getMessage());
        }

        return songs;
    }

    public LibraryStats getLibraryStats() {
        String sql = """
                SELECT
                    COUNT(*) AS total_songs,
                    COALESCE(SUM(duration), 0) AS total_duration,
                    SUM(CASE WHEN is_favorite = 1 THEN 1 ELSE 0 END) AS favorite_count,
                    SUM(CASE WHEN file_path LIKE 'http%' THEN 1 ELSE 0 END) AS online_count,
                    SUM(CASE WHEN file_path NOT LIKE 'http%' AND file_path <> '' THEN 1 ELSE 0 END) AS local_count
                FROM songs
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return new LibraryStats(
                        rs.getInt("total_songs"),
                        rs.getInt("total_duration"),
                        rs.getInt("favorite_count"),
                        rs.getInt("local_count"),
                        rs.getInt("online_count")
                );
            }
        } catch (SQLException e) {
            System.out.println("Lỗi lấy thống kê thư viện: " + e.getMessage());
        }

        return new LibraryStats();
    }

    public List<ArtistPlayStat> getTopArtists(int limit) {
        List<ArtistPlayStat> stats = new ArrayList<>();
        String sql = """
                SELECT
                    COALESCE(NULLIF(TRIM(artist), ''), 'Unknown Artist') AS artist_name,
                    COUNT(*) AS song_count,
                    COALESCE(SUM(play_count), 0) AS total_plays
                FROM songs
                GROUP BY COALESCE(NULLIF(TRIM(artist), ''), 'Unknown Artist')
                ORDER BY total_plays DESC, song_count DESC, artist_name ASC
                LIMIT ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, Math.max(1, limit));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    stats.add(new ArtistPlayStat(
                            rs.getString("artist_name"),
                            rs.getInt("song_count"),
                            rs.getInt("total_plays")
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println("Lỗi lấy top artist: " + e.getMessage());
        }

        return stats;
    }

    public List<Song> getTopPlayedSongs(int limit) {
        List<Song> songs = new ArrayList<>();
        String sql = "SELECT * FROM songs ORDER BY play_count DESC, is_favorite DESC, id DESC LIMIT ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, Math.max(1, limit));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    songs.add(mapSong(rs));
                }
            }
        } catch (SQLException e) {
            System.out.println("Lỗi lấy top bài hát: " + e.getMessage());
        }

        return songs;
    }

    public List<Song> getSimilarSongs(Song baseSong, int limit) {
        List<Song> result = new ArrayList<>();
        if (baseSong == null) {
            return result;
        }

        String sql = "SELECT * FROM songs WHERE id <> ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, baseSong.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                List<RankedSong> ranked = new ArrayList<>();
                while (rs.next()) {
                    Song candidate = mapSong(rs);
                    int score = similarityScore(baseSong, candidate);
                    if (score > 0) {
                        ranked.add(new RankedSong(candidate, score));
                    }
                }

                ranked.sort((a, b) -> {
                    if (b.score != a.score) return Integer.compare(b.score, a.score);
                    if (b.song.getPlayCount() != a.song.getPlayCount()) {
                        return Integer.compare(b.song.getPlayCount(), a.song.getPlayCount());
                    }
                    if (a.song.isFavorite() != b.song.isFavorite()) {
                        return Boolean.compare(b.song.isFavorite(), a.song.isFavorite());
                    }
                    return Integer.compare(b.song.getId(), a.song.getId());
                });

                for (RankedSong rankedSong : ranked) {
                    if (result.size() >= limit) break;
                    result.add(rankedSong.song);
                }
            }
        } catch (SQLException e) {
            System.out.println("Lỗi lấy bài tương tự: " + e.getMessage());
        }

        if (result.isEmpty()) {
            return getTopPlayedSongs(limit);
        }
        return result;
    }

    private int similarityScore(Song base, Song candidate) {
        int score = 0;

        if (!base.getArtist().isBlank() && base.getArtist().equalsIgnoreCase(candidate.getArtist())) {
            score += 60;
        }
        if (!base.getAlbum().isBlank() && base.getAlbum().equalsIgnoreCase(candidate.getAlbum())) {
            score += 35;
        }

        List<String> baseTokens = tokenize(base.getTitle());
        List<String> candidateTokens = tokenize(candidate.getTitle());
        for (String token : baseTokens) {
            if (token.length() >= 3 && candidateTokens.contains(token)) {
                score += 8;
            }
        }

        int durationGap = Math.abs(base.getDuration() - candidate.getDuration());
        if (base.getDuration() > 0 && candidate.getDuration() > 0) {
            if (durationGap <= 10) score += 10;
            else if (durationGap <= 30) score += 6;
            else if (durationGap <= 60) score += 3;
        }

        if (candidate.isFavorite()) {
            score += 4;
        }
        score += Math.min(candidate.getPlayCount(), 20);

        return score;
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+"))
                .filter(s -> !s.isBlank())
                .toList();
    }

    public boolean songExists(Song song) {
        String sql = "SELECT id FROM songs WHERE title = ? AND artist = ? AND file_path = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, safe(song.getTitle()));
            stmt.setString(2, safe(song.getArtist()));
            stmt.setString(3, safe(song.getFilePath()));

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.out.println("Lỗi kiểm tra bài hát tồn tại: " + e.getMessage());
            return false;
        }
    }

    public boolean addSong(Song song) {
        if (songExists(song)) {
            return false;
        }

        String sql = "INSERT INTO songs " +
                "(title, artist, album, duration, file_path, lyrics, description, thumbnail_url, is_favorite, play_count, last_played_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, safe(song.getTitle()));
            stmt.setString(2, safe(song.getArtist()));
            stmt.setString(3, safe(song.getAlbum()));
            stmt.setInt(4, song.getDuration());
            stmt.setString(5, safe(song.getFilePath()));
            stmt.setString(6, safe(song.getLyrics()));
            stmt.setString(7, safe(song.getDescription()));
            stmt.setString(8, safe(song.getThumbnailUrl()));
            stmt.setBoolean(9, song.isFavorite());
            stmt.setInt(10, song.getPlayCount());

            if (song.getLastPlayedAt() != null) {
                stmt.setTimestamp(11, Timestamp.valueOf(song.getLastPlayedAt()));
            } else {
                stmt.setTimestamp(11, null);
            }

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    song.setId(rs.getInt(1));
                }
            }
            return true;
        } catch (SQLException e) {
            System.out.println("Lỗi thêm bài hát: " + e.getMessage());
            return false;
        }
    }

    public boolean updateSong(Song song) {
        String sql = "UPDATE songs SET " +
                "title = ?, artist = ?, album = ?, duration = ?, file_path = ?, lyrics = ?, description = ?, thumbnail_url = ?, " +
                "is_favorite = ?, play_count = ?, last_played_at = ? " +
                "WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, safe(song.getTitle()));
            stmt.setString(2, safe(song.getArtist()));
            stmt.setString(3, safe(song.getAlbum()));
            stmt.setInt(4, song.getDuration());
            stmt.setString(5, safe(song.getFilePath()));
            stmt.setString(6, safe(song.getLyrics()));
            stmt.setString(7, safe(song.getDescription()));
            stmt.setString(8, safe(song.getThumbnailUrl()));
            stmt.setBoolean(9, song.isFavorite());
            stmt.setInt(10, song.getPlayCount());

            if (song.getLastPlayedAt() != null) {
                stmt.setTimestamp(11, Timestamp.valueOf(song.getLastPlayedAt()));
            } else {
                stmt.setTimestamp(11, null);
            }

            stmt.setInt(12, song.getId());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Lỗi sửa bài hát: " + e.getMessage());
            return false;
        }
    }

    public boolean updateLyrics(int songId, String lyrics) {
        String sql = "UPDATE songs SET lyrics = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, lyrics == null ? "" : lyrics);
            stmt.setInt(2, songId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Lỗi cập nhật lyrics: " + e.getMessage());
            return false;
        }
    }

    public boolean updateFavorite(int songId, boolean favorite) {
        String sql = "UPDATE songs SET is_favorite = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setBoolean(1, favorite);
            stmt.setInt(2, songId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Lỗi cập nhật favorite: " + e.getMessage());
            return false;
        }
    }

    public boolean markAsPlayed(int songId) {
        String sql = "UPDATE songs SET play_count = play_count + 1, last_played_at = NOW() WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, songId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Lỗi cập nhật recently played: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteSong(int id) {
        String sql = "DELETE FROM songs WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Lỗi xóa bài hát: " + e.getMessage());
            return false;
        }
    }

    public int getTotalPlayCount() {
        String sql = "SELECT COALESCE(SUM(play_count), 0) FROM songs";

        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }

    public int removeDuplicates() {
        String sql = "DELETE s1 FROM songs s1 " +
                "INNER JOIN songs s2 ON s1.title = s2.title " +
                "AND s1.artist = s2.artist " +
                "AND s1.file_path = s2.file_path " +
                "AND s1.id > s2.id";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            return stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Lỗi xóa bài hát trùng: " + e.getMessage());
            return 0;
        }
    }

    private String buildFilterClause(String filterBy) {
        if (filterBy == null) return "";

        return switch (filterBy) {
            case "Yêu thích" -> "WHERE is_favorite = 1";
            case "Nhạc local" -> "WHERE file_path NOT LIKE 'http%' AND file_path <> ''";
            case "Online/Stream" -> "WHERE file_path LIKE 'http%'";
            default -> "";
        };
    }

    private String buildOrderClause(String sortBy) {
        if (sortBy == null) return "ORDER BY id DESC";

        return switch (sortBy) {
            case "Tên A-Z" -> "ORDER BY title ASC, artist ASC, id DESC";
            case "Ca sĩ A-Z" -> "ORDER BY artist ASC, title ASC, id DESC";
            case "Nghe gần đây" -> "ORDER BY last_played_at DESC, id DESC";
            case "Nghe nhiều nhất" -> "ORDER BY play_count DESC, id DESC";
            default -> "ORDER BY id DESC";
        };
    }

    private Song mapSong(ResultSet rs) throws SQLException {
        Timestamp lastPlayedTs = getSafeTimestamp(rs, "last_played_at");
        LocalDateTime lastPlayedAt = lastPlayedTs == null ? null : lastPlayedTs.toLocalDateTime();

        return new Song(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getString("artist"),
                rs.getString("album"),
                rs.getInt("duration"),
                rs.getString("file_path"),
                getSafeColumn(rs, "lyrics"),
                getSafeColumn(rs, "description"),
                getSafeColumn(rs, "thumbnail_url"),
                getSafeBoolean(rs, "is_favorite"),
                getSafeInt(rs, "play_count"),
                lastPlayedAt
        );
    }

    private String getSafeColumn(ResultSet rs, String columnName) {
        try {
            String value = rs.getString(columnName);
            return value == null ? "" : value;
        } catch (SQLException e) {
            return "";
        }
    }

    private int getSafeInt(ResultSet rs, String columnName) {
        try {
            return rs.getInt(columnName);
        } catch (SQLException e) {
            return 0;
        }
    }

    private boolean getSafeBoolean(ResultSet rs, String columnName) {
        try {
            return rs.getBoolean(columnName);
        } catch (SQLException e) {
            return false;
        }
    }

    private Timestamp getSafeTimestamp(ResultSet rs, String columnName) {
        try {
            return rs.getTimestamp(columnName);
        } catch (SQLException e) {
            return null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class RankedSong {
        private final Song song;
        private final int score;

        private RankedSong(Song song, int score) {
            this.song = song;
            this.score = score;
        }
    }
}
