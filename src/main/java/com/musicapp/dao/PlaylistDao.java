package com.musicapp.dao;

import com.musicapp.database.DatabaseConnection;
import com.musicapp.model.Playlist;
import com.musicapp.model.Song;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PlaylistDao {

    public List<Playlist> getAllPlaylists() {
        List<Playlist> playlists = new ArrayList<>();
        String sql = "SELECT p.id, p.name, COUNT(ps.song_id) AS song_count " +
                "FROM playlists p " +
                "LEFT JOIN playlist_songs ps ON p.id = ps.playlist_id " +
                "GROUP BY p.id, p.name " +
                "ORDER BY p.id DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                List<Song> songs = new ArrayList<>();
                int songCount = rs.getInt("song_count");

                for (int i = 0; i < songCount; i++) {
                    songs.add(new Song());
                }

                playlists.add(new Playlist(
                        rs.getInt("id"),
                        rs.getString("name"),
                        songs
                ));
            }
        } catch (SQLException e) {
            System.out.println("Lỗi lấy danh sách playlist: " + e.getMessage());
        }
        return playlists;
    }

    public boolean addPlaylist(Playlist playlist) {
        String sql = "INSERT INTO playlists (name) VALUES (?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, playlist.getName());
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    playlist.setId(rs.getInt(1));
                }
            }
            return true;

        } catch (SQLException e) {
            System.out.println("Lỗi tạo playlist: " + e.getMessage());
            return false;
        }
    }

    public boolean renamePlaylist(int playlistId, String newName) {
        String sql = "UPDATE playlists SET name = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newName == null ? "" : newName.trim());
            stmt.setInt(2, playlistId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Lỗi đổi tên playlist: " + e.getMessage());
            return false;
        }
    }

    public boolean deletePlaylist(int id) {
        String sql = "DELETE FROM playlists WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Lỗi xóa playlist: " + e.getMessage());
            return false;
        }
    }

    public List<Song> getSongsInPlaylist(int playlistId) {
        List<Song> songs = new ArrayList<>();
        String sql = "SELECT s.* FROM songs s " +
                "JOIN playlist_songs ps ON s.id = ps.song_id " +
                "WHERE ps.playlist_id = ? " +
                "ORDER BY ps.position, ps.id";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, playlistId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp lastPlayedTs = getSafeTimestamp(rs, "last_played_at");
                    LocalDateTime lastPlayedAt = lastPlayedTs == null ? null : lastPlayedTs.toLocalDateTime();

                    songs.add(new Song(
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
                    ));
                }
            }
        } catch (SQLException e) {
            System.out.println("Lỗi lấy bài hát trong playlist: " + e.getMessage());
        }
        return songs;
    }

    public boolean isSongInPlaylist(int playlistId, int songId) {
        String sql = "SELECT 1 FROM playlist_songs WHERE playlist_id = ? AND song_id = ? LIMIT 1";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, playlistId);
            stmt.setInt(2, songId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.out.println("Lỗi kiểm tra bài trong playlist: " + e.getMessage());
            return false;
        }
    }

    public boolean addSongToPlaylist(int playlistId, int songId) {
        if (isSongInPlaylist(playlistId, songId)) {
            return false;
        }

        String sqlPos = "SELECT COALESCE(MAX(position), 0) + 1 FROM playlist_songs WHERE playlist_id = ?";
        String sqlInsert = "INSERT INTO playlist_songs (playlist_id, song_id, position) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmtPos = conn.prepareStatement(sqlPos)) {

            stmtPos.setInt(1, playlistId);

            int position = 1;
            try (ResultSet rs = stmtPos.executeQuery()) {
                if (rs.next()) {
                    position = rs.getInt(1);
                }
            }

            try (PreparedStatement stmtInsert = conn.prepareStatement(sqlInsert)) {
                stmtInsert.setInt(1, playlistId);
                stmtInsert.setInt(2, songId);
                stmtInsert.setInt(3, position);
                return stmtInsert.executeUpdate() > 0;
            }

        } catch (SQLException e) {
            System.out.println("Lỗi thêm bài vào playlist: " + e.getMessage());
            return false;
        }
    }

    public boolean removeSongFromPlaylist(int playlistId, int songId) {
        String sql = "DELETE FROM playlist_songs WHERE playlist_id = ? AND song_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, playlistId);
            stmt.setInt(2, songId);
            boolean deleted = stmt.executeUpdate() > 0;
            if (deleted) {
                normalizePositions(conn, playlistId);
            }
            return deleted;
        } catch (SQLException e) {
            System.out.println("Lỗi xóa bài khỏi playlist: " + e.getMessage());
            return false;
        }
    }

    public boolean moveSongUp(int playlistId, int songId) {
        return moveSong(playlistId, songId, true);
    }

    public boolean moveSongDown(int playlistId, int songId) {
        return moveSong(playlistId, songId, false);
    }

    private boolean moveSong(int playlistId, int songId, boolean moveUp) {
        String sql = "SELECT id, song_id, position FROM playlist_songs WHERE playlist_id = ? ORDER BY position, id";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql,
                     ResultSet.TYPE_SCROLL_INSENSITIVE,
                     ResultSet.CONCUR_READ_ONLY)) {

            stmt.setInt(1, playlistId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Row> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new Row(rs.getInt("id"), rs.getInt("song_id"), rs.getInt("position")));
                }

                int currentIndex = -1;
                for (int i = 0; i < rows.size(); i++) {
                    if (rows.get(i).songId == songId) {
                        currentIndex = i;
                        break;
                    }
                }

                if (currentIndex == -1) return false;
                int swapIndex = moveUp ? currentIndex - 1 : currentIndex + 1;
                if (swapIndex < 0 || swapIndex >= rows.size()) return false;

                Row current = rows.get(currentIndex);
                Row target = rows.get(swapIndex);
                swapPositions(conn, current, target);
                normalizePositions(conn, playlistId);
                return true;
            }
        } catch (SQLException e) {
            System.out.println("Lỗi đổi vị trí bài hát trong playlist: " + e.getMessage());
            return false;
        }
    }

    private void swapPositions(Connection conn, Row a, Row b) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE playlist_songs SET position = ? WHERE id = ?")) {
            stmt.setInt(1, b.position);
            stmt.setInt(2, a.id);
            stmt.executeUpdate();

            stmt.setInt(1, a.position);
            stmt.setInt(2, b.id);
            stmt.executeUpdate();
        }
    }

    private void normalizePositions(Connection conn, int playlistId) throws SQLException {
        List<Integer> rowIds = new ArrayList<>();
        try (PreparedStatement select = conn.prepareStatement(
                "SELECT id FROM playlist_songs WHERE playlist_id = ? ORDER BY position, id")) {
            select.setInt(1, playlistId);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    rowIds.add(rs.getInt("id"));
                }
            }
        }

        try (PreparedStatement update = conn.prepareStatement("UPDATE playlist_songs SET position = ? WHERE id = ?")) {
            for (int i = 0; i < rowIds.size(); i++) {
                update.setInt(1, i + 1);
                update.setInt(2, rowIds.get(i));
                update.addBatch();
            }
            update.executeBatch();
        }
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

    private static class Row {
        private final int id;
        private final int songId;
        private final int position;

        private Row(int id, int songId, int position) {
            this.id = id;
            this.songId = songId;
            this.position = position;
        }
    }
}
