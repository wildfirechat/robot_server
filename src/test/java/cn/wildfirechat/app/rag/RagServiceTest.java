package cn.wildfirechat.app.rag;

import cn.wildfirechat.app.RobotConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RagServiceTest {
    private JdbcTemplate jdbcTemplate;
    private KnowledgeRepository repository;
    private KnowledgeVectorIndex vectorIndex;
    private RobotConfig robotConfig;
    private RagService ragService;

    @BeforeEach
    public void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:rag_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new KnowledgeRepository(jdbcTemplate);
        vectorIndex = new KnowledgeVectorIndex(repository);
        robotConfig = new RobotConfig();
        robotConfig.getRag().setEnabled(true);
        robotConfig.getRag().setTopK(5);
        robotConfig.getRag().setScoreThreshold(0.1);
        robotConfig.getRag().setMaxContextChars(6000);
        EmbeddingClient embeddingClient = new EmbeddingClient() {
            @Override
            public float[] embedOne(String text) {
                return new float[]{1f, 0f};
            }

            @Override
            public List<float[]> embedAll(List<String> texts) {
                return Collections.singletonList(new float[]{1f, 0f});
            }
        };
        ragService = new RagService(robotConfig, embeddingClient, vectorIndex, repository);
    }

    @Test
    public void retrievesRelevantChunkAndBuildsCitationContext() {
        long documentId = repository.insertDocument("产品手册", "text", null,
                KnowledgeRepository.STATUS_READY, "embedding-model", System.currentTimeMillis());
        insertChunk(documentId, 0, "退款政策为七天无理由退款。", new float[]{1f, 0f});
        insertChunk(documentId, 1, "服务器默认端口是80。", new float[]{0f, 1f});
        vectorIndex.reload();

        RagResult result = ragService.retrieve(0, "user1", "怎么退款");

        assertTrue(result.hasContext());
        assertTrue(result.getContext().contains("[1] 产品手册"));
        assertTrue(result.getContext().contains("退款政策"));
        assertEquals(1, result.getHits().size());
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rag_query_log", Long.class));
    }

    @Test
    public void disabledRagReturnsEmpty() {
        robotConfig.getRag().setEnabled(false);
        assertTrue(!ragService.retrieve(0, "user1", "任意问题").hasContext());
    }

    private void insertChunk(long documentId, int index, String content, float[] embedding) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(index);
        chunk.setTitle("产品手册");
        chunk.setContent(content);
        chunk.setEmbedding(embedding);
        chunk.setCreatedTime(System.currentTimeMillis());
        repository.insertChunks(Collections.singletonList(chunk));
    }
}
