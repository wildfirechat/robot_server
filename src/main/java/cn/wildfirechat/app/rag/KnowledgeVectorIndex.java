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
 * 向量检索入口：PostgreSQL 模式走 pgvector 数据库检索；H2 测试模式走内存索引。
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
        if (repository.isPostgres()) {
            entries = Collections.emptyList();
            loaded = true;
            LOG.info("PostgreSQL 模式使用数据库向量检索，无需加载内存索引");
            return;
        }
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
        if (repository.isPostgres()) {
            long count = repository.countReadyChunks();
            return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
        }
        ensureLoaded();
        return entries.size();
    }

    public boolean isEmpty() {
        if (repository.isPostgres()) {
            return !repository.hasReadyChunks();
        }
        return size() == 0;
    }

    public List<ScoredChunk> search(float[] query, int topK, double threshold) {
        if (query == null || query.length == 0 || topK <= 0) {
            return Collections.emptyList();
        }
        if (repository.isPostgres()) {
            List<KnowledgeRepository.ScoredVector> scored = repository.searchSimilar(query, topK, threshold);
            List<ScoredChunk> result = new ArrayList<>(scored.size());
            for (KnowledgeRepository.ScoredVector item : scored) {
                result.add(new ScoredChunk(item.getChunkId(), item.getDocumentId(),
                        item.getChunkIndex(), item.getTitle(), item.getScore()));
            }
            return result;
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
        if (repository.isPostgres()) {
            loaded = true;
            return;
        }
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
