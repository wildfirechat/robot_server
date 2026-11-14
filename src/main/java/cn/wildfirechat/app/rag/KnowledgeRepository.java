package cn.wildfirechat.app.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 知识库文档、分块、检索日志的存储。
 */
@Repository
public class KnowledgeRepository {
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeRepository.class);

    private static final RowMapper<KnowledgeDocument> DOCUMENT_MAPPER = (rs, rowNum) -> {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(rs.getLong("id"));
        document.setTitle(rs.getString("title"));
        document.setSourceType(rs.getString("source_type"));
        document.setSourceUri(rs.getString("source_uri"));
        document.setStatus(rs.getString("status"));
        document.setEmbeddingModel(rs.getString("embedding_model"));
        document.setErrorMessage(rs.getString("error_message"));
        document.setChunkCount(rs.getInt("chunk_count"));
        document.setCreatedTime(rs.getTimestamp("created_time").getTime());
        document.setUpdatedTime(rs.getTimestamp("updated_time").getTime());
        return document;
    };

    private static final RowMapper<KnowledgeChunk> CHUNK_MAPPER = (rs, rowNum) -> {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(rs.getLong("id"));
        chunk.setDocumentId(rs.getLong("document_id"));
        chunk.setChunkIndex(rs.getInt("chunk_index"));
        chunk.setTitle(rs.getString("title"));
        chunk.setContent(rs.getString("content"));
        chunk.setEmbedding(VectorMath.fromBytes(rs.getBytes("embedding")));
        return chunk;
    };

    private final JdbcTemplate jdbcTemplate;
    private final boolean mysql;

    public KnowledgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.mysql = detectMysql(jdbcTemplate);
        initSchema();
    }

    private static boolean detectMysql(JdbcTemplate jdbcTemplate) {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase().contains("mysql");
        } catch (Exception e) {
            LOG.warn("无法识别数据库类型，将按 H2 处理", e);
            return false;
        }
    }

    private void initSchema() {
        String contentColumn = mysql ? "LONGTEXT" : "CLOB";
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS kb_document ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "title VARCHAR(255) NOT NULL,"
                + "source_type VARCHAR(32) NOT NULL,"
                + "source_uri VARCHAR(1000),"
                + "status VARCHAR(16) NOT NULL,"
                + "embedding_model VARCHAR(128),"
                + "error_message VARCHAR(512),"
                + "chunk_count INT NOT NULL,"
                + "created_time TIMESTAMP NOT NULL,"
                + "updated_time TIMESTAMP NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS kb_chunk ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "document_id BIGINT NOT NULL,"
                + "chunk_index INT NOT NULL,"
                + "title VARCHAR(255),"
                + "content " + contentColumn + " NOT NULL,"
                + "embedding BLOB,"
                + "embedding_dim INT NOT NULL,"
                + "created_time TIMESTAMP NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS rag_query_log ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "conv_type INT NOT NULL,"
                + "conv_target VARCHAR(128),"
                + "question VARCHAR(2000),"
                + "hit_count INT NOT NULL,"
                + "top_score DOUBLE,"
                + "chunk_ids VARCHAR(2000),"
                + "latency_ms BIGINT NOT NULL,"
                + "created_time TIMESTAMP NOT NULL)");
        createIndex("kb_chunk", "idx_kb_chunk_doc", "document_id");
        createIndex("kb_document", "idx_kb_document_status", "status");
        createIndex("rag_query_log", "idx_rag_query_time", "created_time");
    }

    private void createIndex(String table, String name, String columns) {
        String sql = mysql
                ? "CREATE INDEX " + name + " ON " + table + " (" + columns + ")"
                : "CREATE INDEX IF NOT EXISTS " + name + " ON " + table + " (" + columns + ")";
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            LOG.debug("知识库索引已存在: {}", name);
        }
    }

    public long insertDocument(String title, String sourceType, String sourceUri,
                               String status, String embeddingModel, long now) {
        String sql = "INSERT INTO kb_document (title, source_type, source_uri, status, embedding_model, "
                + "error_message, chunk_count, created_time, updated_time) VALUES (?, ?, ?, ?, ?, NULL, 0, ?, ?)";
        Timestamp timestamp = new Timestamp(now);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, truncate(title, 255));
            statement.setString(2, truncate(sourceType, 32));
            statement.setString(3, truncate(sourceUri, 1000));
            statement.setString(4, status);
            statement.setString(5, truncate(embeddingModel, 128));
            statement.setTimestamp(6, timestamp);
            statement.setTimestamp(7, timestamp);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? -1 : key.longValue();
    }

    public void updateDocument(long id, String status, String errorMessage, int chunkCount, long now) {
        jdbcTemplate.update("UPDATE kb_document SET status = ?, error_message = ?, chunk_count = ?, updated_time = ? "
                        + "WHERE id = ?",
                status, truncate(errorMessage, 512), chunkCount, new Timestamp(now), id);
    }

    public List<KnowledgeDocument> listDocuments() {
        return jdbcTemplate.query("SELECT * FROM kb_document ORDER BY updated_time DESC", DOCUMENT_MAPPER);
    }

    public Optional<KnowledgeDocument> findDocument(long id) {
        List<KnowledgeDocument> documents = jdbcTemplate.query(
                "SELECT * FROM kb_document WHERE id = ?", DOCUMENT_MAPPER, id);
        return documents.isEmpty() ? Optional.empty() : Optional.of(documents.get(0));
    }

    public void deleteDocument(long id) {
        jdbcTemplate.update("DELETE FROM kb_chunk WHERE document_id = ?", id);
        jdbcTemplate.update("DELETE FROM kb_document WHERE id = ?", id);
    }

    public void insertChunks(List<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("INSERT INTO kb_chunk (document_id, chunk_index, title, content, "
                        + "embedding, embedding_dim, created_time) VALUES (?, ?, ?, ?, ?, ?, ?)",
                new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int i) throws java.sql.SQLException {
                        KnowledgeChunk chunk = chunks.get(i);
                        statement.setLong(1, chunk.getDocumentId());
                        statement.setInt(2, chunk.getChunkIndex());
                        statement.setString(3, truncate(chunk.getTitle(), 255));
                        statement.setString(4, chunk.getContent());
                        statement.setBytes(5, VectorMath.toBytes(chunk.getEmbedding()));
                        statement.setInt(6, chunk.getEmbedding().length);
                        statement.setTimestamp(7, new Timestamp(chunk.getCreatedTime()));
                    }

                    @Override
                    public int getBatchSize() {
                        return chunks.size();
                    }
                });
    }

    public List<KnowledgeChunk> findChunksByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT id, document_id, chunk_index, title, content, embedding FROM kb_chunk WHERE id IN (");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
        sql.append(')');
        return jdbcTemplate.query(sql.toString(), CHUNK_MAPPER, ids.toArray());
    }

    public List<VectorEntry> findAllVectors() {
        return jdbcTemplate.query("SELECT c.id, c.document_id, c.chunk_index, c.title, c.embedding "
                        + "FROM kb_chunk c JOIN kb_document d ON d.id = c.document_id "
                        + "WHERE d.status = ?",
                (rs, rowNum) -> new VectorEntry(
                        rs.getLong("id"),
                        rs.getLong("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("title"),
                        VectorMath.fromBytes(rs.getBytes("embedding"))),
                STATUS_READY);
    }

    public void deleteChunksByDocument(long documentId) {
        jdbcTemplate.update("DELETE FROM kb_chunk WHERE document_id = ?", documentId);
    }

    public void insertQueryLog(int convType, String convTarget, String question, int hitCount,
                               double topScore, String chunkIds, long latencyMs) {
        try {
            jdbcTemplate.update("INSERT INTO rag_query_log (conv_type, conv_target, question, hit_count, "
                            + "top_score, chunk_ids, latency_ms, created_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    convType, truncate(convTarget, 128), truncate(question, 2000), hitCount,
                    topScore, truncate(chunkIds, 2000), latencyMs, new Timestamp(System.currentTimeMillis()));
        } catch (Exception e) {
            LOG.warn("记录知识库检索日志失败: {}", e.getMessage());
        }
    }

    public long countChunks() {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kb_chunk", Long.class);
        return value == null ? 0 : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
