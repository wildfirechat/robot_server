package cn.wildfirechat.app.call;

// Log4j imports

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// json-simple imports
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

class RtspMetadataProber {
    private static final Logger logger = LogManager.getLogger(RtspMetadataProber.class);
    private static final String FFPROBE_PATH = "ffprobe"; // 或者提供 ffprobe 的绝对路径

    public static VideoInfo probeStream(String rtspUrl, int timeoutSeconds) {
        String command = String.format(
            "%s -v quiet -print_format json -show_streams -select_streams v:0 %s",
            FFPROBE_PATH, rtspUrl
        );

        logger.debug("Executing ffprobe command: {}", command);
        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
            process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            boolean exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                logger.error("ffprobe command timed out for URL: {}", rtspUrl);
                return null;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                StringBuilder errorOutput = new StringBuilder();
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                }
                logger.error("ffprobe command failed with exit code {} for URL: {}. Error: {}", exitCode, rtspUrl, errorOutput.toString());
                return null;
            }

            String jsonOutput = output.toString();
            logger.debug("ffprobe JSON output: {}", jsonOutput);
            if (jsonOutput.trim().isEmpty()) {
                logger.error("ffprobe returned empty output for URL: {}", rtspUrl);
                return null;
            }

            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(jsonOutput);
            JSONArray streams = (JSONArray) json.get("streams");

            if (streams == null || streams.isEmpty()) {
                logger.error("No video streams found in ffprobe output for URL: {}", rtspUrl);
                return null;
            }

            JSONObject videoStream = (JSONObject) streams.get(0); // 我们用了 -select_streams v:0

            Long widthLong = (Long) videoStream.get("width");
            Long heightLong = (Long) videoStream.get("height");
            int width = (widthLong != null) ? widthLong.intValue() : -1;
            int height = (heightLong != null) ? heightLong.intValue() : -1;

            String avgFrameRateStr = (String) videoStream.get("avg_frame_rate");
            if (avgFrameRateStr == null) avgFrameRateStr = "0/0";


            if (width <= 0 || height <= 0) {
                logger.error("Failed to parse width/height from ffprobe for URL: {}", rtspUrl);
                return null;
            }

            int fps = 0;
            if (avgFrameRateStr.contains("/")) {
                String[] parts = avgFrameRateStr.split("/");
                if (parts.length == 2) {
                    try {
                        double num = Double.parseDouble(parts[0]);
                        double den = Double.parseDouble(parts[1]);
                        if (den != 0) {
                            fps = (int) Math.round(num / den);
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("Could not parse avg_frame_rate: {}", avgFrameRateStr, e);
                    }
                }
            }

            if (fps <= 0) {
                String rFrameRateStr = (String) videoStream.get("r_frame_rate");
                if (rFrameRateStr == null) rFrameRateStr = "0/0";

                if (rFrameRateStr.contains("/")) {
                    String[] parts = rFrameRateStr.split("/");
                    if (parts.length == 2) {
                        try {
                            double num = Double.parseDouble(parts[0]);
                            double den = Double.parseDouble(parts[1]);
                            if (den != 0) {
                                fps = (int) Math.round(num / den);
                            }
                        } catch (NumberFormatException e) {
                            logger.warn("Could not parse r_frame_rate: {}", rFrameRateStr, e);
                        }
                    }
                }
            }

            if (fps <= 0) {
                logger.warn("Could not determine FPS from ffprobe for URL: {}. Consider defaulting or erroring out.", rtspUrl);
                return null;
            }

            return new VideoInfo(width, height, fps);

        } catch (ParseException e) {
            logger.error("Failed to parse ffprobe JSON output for URL: {}", rtspUrl, e);
            if (process != null) process.destroyForcibly();
            return null;
        } catch (Exception e) {
            logger.error("Exception while probing RTSP stream: {}", rtspUrl, e);
            if (process != null) {
                process.destroyForcibly();
            }
            return null;
        }
    }

    public static AudioInfo probeAudioInfo(String rtspUrl, int timeoutSeconds) {
        String command = String.format(
            "%s -v quiet -print_format json -show_streams -select_streams a:0 %s",
            FFPROBE_PATH, rtspUrl
        );

        logger.debug("Executing ffprobe audio command: {}", command);
        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
            process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            boolean exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                logger.error("ffprobe audio command timed out for URL: {}", rtspUrl);
                return null;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                StringBuilder errorOutput = new StringBuilder();
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\\n");
                    }
                }
                logger.error("ffprobe audio command failed with exit code {} for URL: {}. Error: {}", exitCode, rtspUrl, errorOutput.toString());
                return null;
            }

            String jsonOutput = output.toString();
            logger.debug("ffprobe audio JSON output: {}", jsonOutput);
            if (jsonOutput.trim().isEmpty()) {
                logger.error("ffprobe audio returned empty output for URL: {}", rtspUrl);
                return null;
            }

            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(jsonOutput);
            JSONArray streams = (JSONArray) json.get("streams");

            if (streams == null || streams.isEmpty()) {
                logger.error("No audio streams found in ffprobe output for URL: {}", rtspUrl);
                return null;
            }

            JSONObject audioStream = (JSONObject) streams.get(0); // We used -select_streams a:0

            String sampleRateStr = (String) audioStream.get("sample_rate");
            Long channelsLong = (Long) audioStream.get("channels");
            // Also try "channel_layout" and parse it if "channels" is not available or invalid
            if (channelsLong == null) {
                String channelLayout = (String) audioStream.get("channel_layout");
                if (channelLayout != null) {
                    // Basic parsing for common layouts like "stereo" (2), "mono" (1)
                    // FFmpeg channel layout names: "mono", "stereo", "2.1", "3.0", "3.0(back)", "3.1", "4.0", "4.1", "5.0", "5.0(side)", "5.1", "5.1(side)", "6.0", "6.0(front)", "6.1", "6.1(front)", "6.1(back)", "7.0", "7.0(front)", "7.1", "7.1(wide)", "7.1(wide-side)", "octagonal", "hexadecagonal", "downmix"
                    // For simplicity, we'll only handle mono and stereo here. More complex parsing might be needed.
                    if ("stereo".equalsIgnoreCase(channelLayout)) {
                        channelsLong = 2L;
                    } else if ("mono".equalsIgnoreCase(channelLayout)) {
                        channelsLong = 1L;
                    } else {
                        // Try to get from "channels" if "channel_layout" is not mono/stereo
                        Object channelsObj = audioStream.get("channels");
                        if (channelsObj instanceof Long) {
                            channelsLong = (Long) channelsObj;
                        } else if (channelsObj instanceof String) {
                            try {
                                channelsLong = Long.parseLong((String) channelsObj);
                            } catch (NumberFormatException e) {
                                logger.warn("Could not parse channels string: {} for URL: {}", channelsObj, rtspUrl);
                            }
                        }
                    }
                }
            }


            if (sampleRateStr == null || channelsLong == null) {
                logger.error("Failed to parse sample_rate or channels from ffprobe audio for URL: {}. SampleRateStr: {}, ChannelsLong: {}", rtspUrl, sampleRateStr, channelsLong);
                return null;
            }

            int sampleRate = 0;
            try {
                sampleRate = Integer.parseInt(sampleRateStr);
            } catch (NumberFormatException e) {
                logger.error("Failed to parse sample_rate string '{}' to integer for URL: {}", sampleRateStr, rtspUrl, e);
                return null;
            }

            int channels = channelsLong.intValue();

            if (sampleRate <= 0 || channels <= 0) {
                logger.error("Invalid sample_rate ({}) or channels ({}) from ffprobe audio for URL: {}", sampleRate, channels, rtspUrl);
                return null;
            }

            return new AudioInfo(sampleRate, channels);

        } catch (ParseException e) {
            logger.error("Failed to parse ffprobe audio JSON output for URL: {}", rtspUrl, e);
            if (process != null) process.destroyForcibly();
            return null;
        } catch (NumberFormatException e) {
            logger.error("Failed to parse numeric value from ffprobe audio for URL: {}", rtspUrl, e);
            if (process != null) process.destroyForcibly();
            return null;
        } catch (Exception e) {
            logger.error("Exception while probing RTSP audio stream: {}", rtspUrl, e);
            if (process != null) {
                process.destroyForcibly();
            }
            return null;
        }
    }

    public static class AudioInfo {
        public final int sampleRate;
        public final int channels;

        public AudioInfo(int sampleRate, int channels) {
            this.sampleRate = sampleRate;
            this.channels = channels;
        }

        @Override
        public String toString() {
            return "AudioInfo{" +
                "sampleRate=" + sampleRate +
                ", channels=" + channels +
                '}';
        }
    }

    public static class VideoInfo {
        public final int width;
        public final int height;
        public final int frameRate;

        public VideoInfo(int width, int height, int frameRate) {
            this.width = width;
            this.height = height;
            this.frameRate = frameRate;
        }

        @Override
        public String toString() {
            return "VideoInfo{" +
                "width=" + width +
                ", height=" + height +
                ", frameRate=" + frameRate +
                '}';
        }
    }
}