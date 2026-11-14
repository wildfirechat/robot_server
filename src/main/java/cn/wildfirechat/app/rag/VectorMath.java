package cn.wildfirechat.app.rag;

import java.nio.ByteBuffer;

/**
 * 向量序列化与相似度计算工具。
 */
public final class VectorMath {
    private VectorMath() {
    }

    public static byte[] toBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    /**
     * pgvector 文本格式：[0.1,0.2] 或 0.1,0.2。
     */
    public static float[] fromString(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        String value = text.trim();
        if (value.startsWith("[")) {
            value = value.substring(1);
        }
        if (value.endsWith("]")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isBlank()) {
            return new float[0];
        }
        String[] parts = value.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }

    public static String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 12 + 2);
        builder.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(vector[i]));
        }
        builder.append(']');
        return builder.toString();
    }

    public static float[] fromBytes(byte[] bytes) {
        if (bytes == null) {
            return new float[0];
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] vector = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }

    /**
     * 返回归一化后的新向量；零向量返回原数组。
     */
    public static float[] normalizeCopy(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) {
            return vector;
        }
        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = (float) (vector[i] / norm);
        }
        return result;
    }

    public static double dot(float[] a, float[] b) {
        if (a.length != b.length) {
            return -1;
        }
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}
