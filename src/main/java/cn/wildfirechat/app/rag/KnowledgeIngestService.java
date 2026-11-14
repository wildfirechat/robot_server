package cn.wildfirechat.app.rag;

import cn.wildfirechat.app.RobotConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 知识库入库服务：解析 → 分块 → Embedding → 写入 kb_chunk。
 * 文件/文本入库异步执行，管理后台显示处理状态。
 */
@Service
public class KnowledgeIngestService {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeIngestService.class);
    private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_TEXT_CHARS = 2_000_000;
    private static final int EMBEDDING_BATCH_SIZE = 32;

    private final KnowledgeRepository repository;
    private final DocumentParser documentParser;
    private final DocumentChunker documentChunker;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeVectorIndex vectorIndex;
    private final RobotConfig robotConfig;
    private final Executor executor;

    public KnowledgeIngestService(KnowledgeRepository repository, DocumentParser documentParser,
                                  DocumentChunker documentChunker, EmbeddingClient embeddingClient,
                                  KnowledgeVectorIndex vectorIndex, RobotConfig robotConfig,
                                  @Qualifier("ragIngestExecutor") Executor executor) {
        this.repository = repository;
        this.documentParser = documentParser;
        this.documentChunker = documentChunker;
        this.embeddingClient = embeddingClient;
        this.vectorIndex = vectorIndex;
        this.robotConfig = robotConfig;
        this.executor = executor;
    }

    @PostConstruct
    public void init() {
        try {
            vectorIndex.reload();
        } catch (Exception e) {
            LOG.warn("启动时加载知识库索引失败: {}", e.getMessage());
        }
    }

    public long ingestText(String title, String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("知识内容不能为空");
        }
        String name = titleOr(title, "未命名文档");
        RobotConfig.Rag rag = robotConfig.getRag();
        long id = repository.insertDocument(name, "text", null,
                KnowledgeRepository.STATUS_PROCESSING, rag.getEmbeddingModel(), System.currentTimeMillis());
        submit(id, name, "text", null, content, null);
        return id;
    }

    public long ingestFile(String title, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("文件不能超过 10MB");
        }
        String filename = file.getOriginalFilename();
        String name = titleOr(title, StringUtils.hasText(filename) ? filename : "未命名文档");
        RobotConfig.Rag rag = robotConfig.getRag();
        long id = repository.insertDocument(name, sourceType(filename), filename,
                KnowledgeRepository.STATUS_PROCESSING, rag.getEmbeddingModel(), System.currentTimeMillis());
        try {
            submit(id, name, "file", filename, null, file.getBytes());
        } catch (Exception e) {
            repository.updateDocument(id, KnowledgeRepository.STATUS_FAILED, "读取上传文件失败: " + e.getMessage(),
                    0, System.currentTimeMillis());
            throw new IllegalStateException("读取上传文件失败", e);
        }
        return id;
    }

    private void submit(long documentId, String title, String sourceType, String sourceUri,
                        String text, byte[] bytes) {
        try {
            executor.execute(() -> process(documentId, title, sourceType, sourceUri, text, bytes));
        } catch (RuntimeException e) {
            repository.updateDocument(documentId, KnowledgeRepository.STATUS_FAILED,
                    "知识库处理队列已满，请稍后重试", 0, System.currentTimeMillis());
            throw new IllegalStateException("知识库处理队列已满，请稍后重试", e);
        }
    }

    private void process(long documentId, String title, String sourceType, String sourceUri,
                         String text, byte[] bytes) {
        try {
            String content;
            String actualType = sourceType;
            if (bytes != null) {
                DocumentParser.ParsedDocument parsed = documentParser.parse(sourceUri, bytes);
                content = parsed.getText();
                actualType = parsed.getSourceType();
            } else {
                content = text;
            }
            if (content == null || content.trim().isEmpty()) {
                throw new IllegalArgumentException("文档中没有可解析的文本");
            }
            content = content.trim();
            if (content.length() > MAX_TEXT_CHARS) {
                throw new IllegalArgumentException("文档内容超过 " + MAX_TEXT_CHARS + " 字符，请拆分后上传");
            }

            RobotConfig.Rag rag = robotConfig.getRag();
            List<DocumentChunker.Chunk> chunks = documentChunker.chunk(
                    content, rag.getChunkSize(), rag.getChunkOverlap());
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("文档分块结果为空");
            }

            List<KnowledgeChunk> entities = new ArrayList<>(chunks.size());
            long now = System.currentTimeMillis();
            for (int start = 0; start < chunks.size(); start += EMBEDDING_BATCH_SIZE) {
                int end = Math.min(chunks.size(), start + EMBEDDING_BATCH_SIZE);
                List<String> inputs = new ArrayList<>(end - start);
                for (int i = start; i < end; i++) {
                    inputs.add(title + "\n" + chunks.get(i).getText());
                }
                List<float[]> vectors = embeddingClient.embedAll(inputs);
                if (vectors == null || vectors.size() != inputs.size()) {
                    throw new IllegalStateException("Embedding 返回数量不匹配");
                }
                for (int i = start; i < end; i++) {
                    KnowledgeChunk chunk = new KnowledgeChunk();
                    chunk.setDocumentId(documentId);
                    chunk.setChunkIndex(chunks.get(i).getIndex());
                    chunk.setTitle(title);
                    chunk.setContent(chunks.get(i).getText());
                    chunk.setEmbedding(vectors.get(i - start));
                    chunk.setCreatedTime(now);
                    entities.add(chunk);
                }
            }
            repository.insertChunks(entities);
            repository.updateDocument(documentId, KnowledgeRepository.STATUS_READY, null,
                    entities.size(), System.currentTimeMillis());
            vectorIndex.reload();
            LOG.info("知识库文档处理完成, id:{}, title:{}, type:{}, chunks:{}",
                    documentId, title, actualType, entities.size());
        } catch (Exception e) {
            LOG.error("知识库文档处理失败, id:{}, title:{}", documentId, title, e);
            try {
                repository.deleteChunksByDocument(documentId);
                repository.updateDocument(documentId, KnowledgeRepository.STATUS_FAILED,
                        truncate(e.getMessage(), 500), 0, System.currentTimeMillis());
                vectorIndex.reload();
            } catch (Exception cleanupError) {
                LOG.warn("清理失败文档出错: {}", cleanupError.getMessage());
            }
        }
    }

    @Transactional
    public void deleteDocument(long documentId) {
        repository.deleteDocument(documentId);
        vectorIndex.reload();
    }

    public void reloadIndex() {
        vectorIndex.reload();
    }

    private static String sourceType(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "file";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1
                ? filename.substring(dot + 1).toLowerCase() : "file";
    }

    private static String titleOr(String title, String fallback) {
        String value = StringUtils.hasText(title) ? title.trim() : fallback;
        if (value == null || value.trim().isEmpty()) {
            value = "未命名文档";
        }
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
