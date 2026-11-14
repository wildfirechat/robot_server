package cn.wildfirechat.app.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档分块器：按字符窗口切分，尽量在换行/句号处断开，并保留重叠。
 */
@Component
public class DocumentChunker {
    private static final int MAX_CHUNKS = 3000;
    private static final String BOUNDARY_CHARS = "\n。！？；.!?;";

    public List<Chunk> chunk(String content, int chunkSize, int overlap) {
        List<Chunk> chunks = new ArrayList<>();
        if (content == null) {
            return chunks;
        }
        String text = content.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (text.isEmpty()) {
            return chunks;
        }
        int size = Math.max(200, chunkSize);
        int overlapChars = Math.max(0, Math.min(overlap, size / 2));
        int position = 0;
        int index = 0;
        while (position < text.length()) {
            int end = Math.min(text.length(), position + size);
            if (end < text.length()) {
                int boundary = findBoundary(text, position + size / 2, end);
                if (boundary > position) {
                    end = boundary;
                }
            }
            String piece = text.substring(position, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(new Chunk(index++, piece));
                if (chunks.size() >= MAX_CHUNKS) {
                    throw new IllegalArgumentException("文档分块超过 " + MAX_CHUNKS + " 个，请拆分文档后再上传");
                }
            }
            if (end >= text.length()) {
                break;
            }
            position = Math.max(position + 1, end - overlapChars);
        }
        return chunks;
    }

    private static int findBoundary(String text, int min, int max) {
        int best = -1;
        for (int i = max - 1; i >= min; i--) {
            if (BOUNDARY_CHARS.indexOf(text.charAt(i)) >= 0) {
                best = i + 1;
                break;
            }
        }
        return best;
    }

    public static class Chunk {
        private final int index;
        private final String text;

        public Chunk(int index, String text) {
            this.index = index;
            this.text = text;
        }

        public int getIndex() {
            return index;
        }

        public String getText() {
            return text;
        }
    }
}
