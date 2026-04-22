package ai.mcp.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

@Component
public class PromptVersionLogger {

    private final Logger logger = LoggerFactory.getLogger(PromptVersionLogger.class);
    private static final String LOG_FILE = "prompt_versions.log";

    public void hashAndLog(String agentName, String systemPrompt) {
        String hash = Integer.toHexString(systemPrompt.strip().hashCode());
        String lastHash = readLastHash(agentName);

        int version = resolveVersion(agentName);
        if (!hash.equals(lastHash)) {
            version++;
        }

        String entry = String.format("%s agent=%s hash=%s version=%d%n",
                LocalDateTime.now(), agentName, hash, version);

        try {
            Files.writeString(Path.of(LOG_FILE), entry,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            logger.info("[PROMPT-VERSION] agent={} hash={} version={}", agentName, hash, version);
        } catch (IOException e) {
            logger.warn("Failed to write prompt version log: {}", e.getMessage());
        }
    }

    private String readLastHash(String agentName) {
        try {
            Path path = Path.of(LOG_FILE);
            if (!Files.exists(path)) return null;
            return Files.readAllLines(path).stream()
                    .filter(line -> line.contains("agent=" + agentName))
                    .reduce((first, second) -> second) // last line wins
                    .map(line -> {
                        String[] parts = line.split(" ");
                        for (String part : parts) {
                            if (part.startsWith("hash=")) return part.substring(5);
                        }
                        return null;
                    })
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private int resolveVersion(String agentName) {
        try {
            Path path = Path.of(LOG_FILE);
            if (!Files.exists(path)) return 0;
            return Files.readAllLines(path).stream()
                    .filter(line -> line.contains("agent=" + agentName))
                    .reduce((first, second) -> second)
                    .map(line -> {
                        String[] parts = line.split(" ");
                        for (String part : parts) {
                            if (part.startsWith("version=")) {
                                return Integer.parseInt(part.substring(8));
                            }
                        }
                        return 0;
                    })
                    .orElse(0);
        } catch (IOException e) {
            return 0;
        }
    }
}