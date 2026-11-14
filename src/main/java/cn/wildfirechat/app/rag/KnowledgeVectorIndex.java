package cn.wildfirechat.app.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 单机内存向量索引。文档处理完成后 reload，多实例部署时每个实例各自加载。
 */
@Component
public class KnowledgeVectorIndex {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeVectorIndex.class);

    private final KnowledgeRepository repository;
    private volatile List<VectorEntry> entries = Collections.emptyList();
    private volatile boolean loaded;

    public KnowledgeVectorIndex(KnowledgeRepository repository) {
        this.repository = repository;
    }

    public synchronized void reload() {
        try {
            List<VectorEntry> loadedEntries = new ArrayList<>();
            for (VectorEntry entry : repository.findAllVectors()) {
                if (entry.getVector() == null || entry.getVector().length == 0) {
                    continue;
                }
                loadedEntries.add(new VectorEntry(entry.getChunkId(), entry.getDocumentId(),
                        entry.getChunkIndex(), entry.getTitle(), VectorMath.normalizeCopy(entry.getVector())));
            }
            entries = loadedEntries;
            loaded = true;
            LOG.info("知识库向量索引已加载，共 {} 个分块", entries.size());
        } catch (Exception e) {
            LOG.error("加载知识库向量索引失败", e);
            loaded = true;
        }
    }

    public int size() {
        ensureLoaded();
        return entries.size();
    }

    public List<ScoredChunk> search(float[] query, int topK, double threshold) {
        if (query == null || query.length == 0 || topK <= 0) {
            return Collections.emptyList();
        }
        ensureLoaded();
        float[] normalizedQuery = VectorMath.normalizeCopy(query);
        PriorityQueue<ScoredChunk> heap = new PriorityQueue<>(Comparator.comparingDouble(ScoredChunk::getScore));
        for (VectorEntry entry : entries) {
            float[] vector = entry.getVector();
            if (vector.length != normalizedQuery.length) {
                continue;
            }
            double score = VectorMath.dot(normalizedQuery, vector);
            if (score < threshold) {
                continue;
            }
            heap.offer(new ScoredChunk(entry.getChunkId(), entry.getDocumentId(),
                    entry.getChunkIndex(), entry.getTitle(), score));
            if (heap.size() > topK) {
                heap.poll();
            }
        }
        List<ScoredChunk> result = new ArrayList<>(heap);
        result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return result;
    }

    private void ensureLoaded() {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    reload();
                }
            }
        }
    }

    public static class ScoredChunk {
        private final long chunkId;
        private final long documentId;
        private final int chunkIndex;
        private final String title;
        private final double score;

        public ScoredChunk(long chunkId, long documentId, int chunkIndex, String title, double score) {
            this.chunkId = chunkId;
            this.documentId = documentId;
            this.chunkIndex = chunkIndex;
            this.title = title;
            this.score = score;
        }

        public long getChunkId() {
            return chunkId;
        }

        public long getDocumentId() {
            return documentId;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public String getTitle() {
            return title;
        }

        public double getScore() {
            return score;
        }
    }
}
