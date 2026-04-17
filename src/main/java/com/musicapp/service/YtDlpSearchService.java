package com.musicapp.service;

import com.musicapp.model.Song;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YtDlpSearchService {

    private static final Pattern STRING_FIELD_PATTERN = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private static final Pattern NUMBER_FIELD_PATTERN = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*([0-9]+)");
    private static final Pattern UNICODE_ESCAPE_PATTERN = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    public List<Song> searchYouTube(String keyword, int limit) {
        List<Song> results = new ArrayList<>();
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isBlank()) {
            return results;
        }

        YtDlpCommand ytDlp = findYtDlp();
        if (ytDlp == null) {
            throw new IllegalStateException("Không tìm thấy yt-dlp. Hãy cài yt-dlp trước.");
        }

        int safeLimit = Math.max(1, Math.min(limit, 20));
        List<String> output = runCommand(ytDlp.withArgs(
                "--dump-json",
                "--flat-playlist",
                "--no-warnings",
                "--playlist-end", String.valueOf(safeLimit * 2),
                "--encoding", "utf-8",
                "ytsearch" + safeLimit + ":" + trimmed
        ));

        if (output == null || output.isEmpty()) {
            return results;
        }

        for (String line : output) {
            String json = line == null ? "" : line.trim();
            if (json.isBlank() || !json.startsWith("{")) {
                continue;
            }

            String id = extractString(json, "id");
            String title = coalesce(extractString(json, "title"), "Không rõ tiêu đề");
            String uploader = coalesce(extractString(json, "uploader"), coalesce(extractString(json, "channel"), "YouTube"));
            int duration = extractInt(json, "duration");
            String url = coalesce(extractString(json, "webpage_url"), "https://www.youtube.com/watch?v=" + id);

            // Lọc bớt playlist/channel/entry lạ: video YouTube thường có id dài 11 ký tự.
            if (id == null || id.isBlank() || id.length() != 11) {
                continue;
            }
            if (!url.contains("watch?v=") && !url.contains("youtu.be/")) {
                continue;
            }

            Song song = new Song(unescapeJson(title), unescapeJson(uploader), "YouTube", duration, url);
            song.setThumbnailUrl("https://i.ytimg.com/vi/" + id + "/hqdefault.jpg");
            song.setDescription("YTSEARCH_RESULT");
            results.add(song);

            if (results.size() >= safeLimit) {
                break;
            }
        }

        return results;
    }

    private String extractString(String json, String fieldName) {
        Matcher matcher = STRING_FIELD_PATTERN.matcher(json);
        while (matcher.find()) {
            if (fieldName.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return null;
    }

    private int extractInt(String json, String fieldName) {
        Matcher matcher = NUMBER_FIELD_PATTERN.matcher(json);
        while (matcher.find()) {
            if (fieldName.equals(matcher.group(1))) {
                try {
                    return Integer.parseInt(matcher.group(2));
                } catch (Exception ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private String unescapeJson(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String decoded = value
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\\\", "\\");

        Matcher matcher = UNICODE_ESCAPE_PATTERN.matcher(decoded);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            char ch = (char) Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(ch)));
        }
        matcher.appendTail(sb);
        return sb.toString().trim();
    }

    private String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
                if (process.waitFor() == 0) {
                    return new YtDlpCommand(candidate);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private List<String> runCommand(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }

            int exit = process.waitFor();
            return exit == 0 ? lines : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static class YtDlpCommand {
        private final List<String> baseCommand;

        YtDlpCommand(List<String> baseCommand) {
            this.baseCommand = baseCommand;
        }

        List<String> withArgs(String... args) {
            List<String> cmd = new ArrayList<>(baseCommand);
            for (String arg : args) {
                cmd.add(arg);
            }
            return cmd;
        }
    }
}
