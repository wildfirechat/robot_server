package cn.wildfirechat.app.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 检索结果。
 */
public class RagResult {
    private static final RagResult EMPTY = new RagResult("", Collections.emptyList());

    private final String context;
    private final List<RagHit> hits;

    public RagResult(String context, List<RagHit> hits) {
        this.context = context == null ? "" : context;
        this.hits = hits == null ? Collections.emptyList() : hits;
    }

    public static RagResult empty() {
        return EMPTY;
    }

    public boolean hasContext() {
        return !context.isEmpty();
    }

    public String getContext() {
        return context;
    }

    public List<RagHit> getHits() {
        return hits;
    }

    public static class RagHit {
        private final long chunkId;
        private final long documentId;
        private final String title;
        private final String content;
        private final double score;

        public RagHit(long chunkId, long documentId, String title, String content, double score) {
            this.chunkId = chunkId;
            this.documentId = documentId;
            this.title = title;
            this.content = content;
            this.score = score;
        }

        public long getChunkId() {
            return chunkId;
        }

        public long getDocumentId() {
            return documentId;
        }

        public String getTitle() {
            return title;
        }

        public String getContent() {
            return content;
        }

        public double getScore() {
            return score;
        }
    }
}
