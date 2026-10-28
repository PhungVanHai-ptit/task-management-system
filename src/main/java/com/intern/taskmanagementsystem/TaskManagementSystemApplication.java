package com.intern.taskmanagementsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
@EnableScheduling
public class TaskManagementSystemApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(TaskManagementSystemApplication.class, args);
    }

    /**
     * Tự động nạp các biến từ file .env vào System Properties khi khởi động (nếu có file .env)
     */
    private static void loadDotEnv() {
        File envFile = new File(".env");
        if (!envFile.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                int separatorIndex = line.indexOf('=');
                String key = line.substring(0, separatorIndex).trim();
                String value = line.substring(separatorIndex + 1).trim();

                // Bỏ dấu ngoặc kép hoặc đơn nếu có bọc quanh giá trị
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }

                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
