package com.hanspoon.backend_api.global.common;

import java.util.Arrays;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PgVectorUtils {

    public static String toVectorLiteral(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(vector[index]));
        }
        return builder.append(']').toString();
    }

    public static float[] parseVectorLiteral(String literal) {
        if (literal == null || literal.isBlank() || "[]".equals(literal.trim())) {
            return new float[0];
        }

        String normalized = literal.trim();
        if (!normalized.startsWith("[") || !normalized.endsWith("]")) {
            throw new IllegalArgumentException("Invalid pgvector literal: " + literal);
        }

        String content = normalized.substring(1, normalized.length() - 1).trim();
        if (content.isEmpty()) {
            return new float[0];
        }

        return Arrays.stream(content.split(","))
                .map(String::trim)
                .mapToDouble(Double::parseDouble)
                .collect(
                        () -> new FloatArrayBuilder(content.length()),
                        FloatArrayBuilder::add,
                        FloatArrayBuilder::addAll)
                .toArray();
    }

    private static final class FloatArrayBuilder {

        private float[] values;
        private int size;

        private FloatArrayBuilder(int initialCapacity) {
            this.values = new float[Math.max(4, initialCapacity / 2)];
        }

        private void add(double value) {
            ensureCapacity(size + 1);
            values[size++] = (float) value;
        }

        private void addAll(FloatArrayBuilder other) {
            ensureCapacity(size + other.size);
            System.arraycopy(other.values, 0, values, size, other.size);
            size += other.size;
        }

        private float[] toArray() {
            return Arrays.copyOf(values, size);
        }

        private void ensureCapacity(int requiredCapacity) {
            if (requiredCapacity <= values.length) {
                return;
            }
            values = Arrays.copyOf(values, Math.max(requiredCapacity, values.length * 2));
        }
    }
}
