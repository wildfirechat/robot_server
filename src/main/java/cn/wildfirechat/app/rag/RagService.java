package cn.wildfirechat.app.rag;

import cn.wildfirechat.app.RobotConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 知识库检索服务：问题向量化 → 内存索引 TopK → 取回正文 → 拼参考资料。
 */
@Service
public class RagService {
    private static final Logger LOG = LoggerFactory.getLogger(RagService.class);
    private static final int MAX_SNIPPET_CHARS = 1600;

    private final RobotConfig robotConfig;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeVectorIndex vectorIndex;
    private final KnowledgeRepository repository;

    public RagService(RobotConfig robotConfig, EmbeddingClient embeddingClient,
                      KnowledgeVectorIndex vectorIndex, KnowledgeRepository repository) {
        this.robotConfig = robotConfig;
        this.embeddingClient = embeddingClient;
        this.vectorIndex = vectorIndex;
        this.repository = repository;
    }

    public RagResult retrieve(int convType, String convTarget, String question) {
        RobotConfig.Rag rag = robotConfig.getRag();
        if (!rag.isEnabled() || question == null || question.trim().isEmpty()) {
            return RagResult.empty();
        }

        if (vectorIndex.isEmpty()) {
            return RagResult.empty();
        }

        long startedAt = System.currentTimeMillis();
        try {
            float[] queryVector = embeddingClient.embedOne(question);
            List<KnowledgeVectorIndex.ScoredChunk> scored = vectorIndex.search(
                    queryVector, rag.getTopK(), rag.getScoreThreshold());
            if (scored.isEmpty()) {
                logQuery(convType, convTarget, question, 0, 0, "", System.currentTimeMillis() - startedAt);
                return RagResult.empty();
            }

            List<Long> ids = new ArrayList<>(scored.size());
            for (KnowledgeVectorIndex.ScoredChunk chunk : scored) {
                ids.add(chunk.getChunkId());
            }
            Map<Long, KnowledgeChunk> chunkMap = new HashMap<>();
            for (KnowledgeChunk chunk : repository.findChunksByIds(ids)) {
                chunkMap.put(chunk.getId(), chunk);
            }

            List<RagResult.RagHit> hits = new ArrayList<>();
            StringBuilder context = new StringBuilder("【参考资料】\n");
            int remaining = Math.max(500, rag.getMaxContextChars());
            int index = 1;
            for (KnowledgeVectorIndex.ScoredChunk scoredChunk : scored) {
                KnowledgeChunk chunk = chunkMap.get(scoredChunk.getChunkId());
                if (chunk == null || chunk.getContent() == null || chunk.getContent().trim().isEmpty()) {
                    continue;
                }
                String title = chunk.getTitle() == null ? "未知文档" : chunk.getTitle();
                String content = chunk.getContent().trim();
                int allowed = Math.min(MAX_SNIPPET_CHARS, remaining - 100);
                if (allowed <= 100) {
                    break;
                }
                if (content.length() > allowed) {
                    content = content.substring(0, allowed) + "…";
                }
                String sourceUrl = chunk.getSourceUrl();
                String block = "[" + index + "] " + title
                        + (sourceUrl == null || sourceUrl.isEmpty() ? "" : "\n来源: " + sourceUrl)
                        + "\n" + content + "\n\n";
                if (block.length() > remaining) {
                    break;
                }
                context.append(block);
                remaining -= block.length();
                hits.add(new RagResult.RagHit(chunk.getId(), chunk.getDocumentId(), title,
                        chunk.getSourceUrl(), chunk.getContent(), scoredChunk.getScore()));
                index++;
            }

            if (hits.isEmpty()) {
                logQuery(convType, convTarget, question, 0, 0, "", System.currentTimeMillis() - startedAt);
                return RagResult.empty();
            }
            StringJoiner idsJoiner = new StringJoiner(",");
            for (RagResult.RagHit hit : hits) {
                idsJoiner.add(String.valueOf(hit.getChunkId()));
            }
            logQuery(convType, convTarget, question, hits.size(), hits.get(0).getScore(),
                    idsJoiner.toString(), System.currentTimeMillis() - startedAt);
            return new RagResult(context.toString(), hits);
        } catch (Exception e) {
            LOG.warn("知识库检索失败，继续普通对话: {}", e.getMessage());
            logQuery(convType, convTarget, question, 0, 0, "", System.currentTimeMillis() - startedAt);
            return RagResult.empty();
        }
    }

    private void logQuery(int convType, String convTarget, String question, int hitCount,
                          double topScore, String chunkIds, long latencyMs) {
        repository.insertQueryLog(convType, convTarget, question, hitCount, topScore, chunkIds, latencyMs);
    }
}
