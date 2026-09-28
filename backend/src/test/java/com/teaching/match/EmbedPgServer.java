package com.teaching.match;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.nio.file.Files;

/**
 * 仅用于本地/CI 在没有外部 PostgreSQL 时启动一个真实 PG 实例（固定端口）。
 * 生产/教学环境仍应使用独立 PostgreSQL（见 README 与 docker-compose.yml）。
 */
public class EmbedPgServer {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("pg.port", "5439"));
        EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setPort(port)
                .setDataDirectory(Files.createTempDirectory("epg-data"))
                .start();
        System.out.println("EMBEDDED_POSTGRES_READY port=" + port);
        System.out.println(pg.getJdbcUrl("postgres", "postgres"));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                pg.close();
            } catch (Exception ignored) {
            }
        }));
        Thread.currentThread().join();
    }
}
