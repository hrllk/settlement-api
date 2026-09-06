package com.liveclass.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 기대값이 전부 2025년 데이터에 고정돼 있다. 어떤 코드가 현재 시각을 읽으면
 * 실행 시점에 따라 결과가 달라진다. <b>제출 후 평가자가 돌릴 때 처음 깨지는</b>
 * 종류의 문제라 자동으로 막는다.
 *
 * <p>프로덕션 코드도 검사한다. 정산 조회에 "이번 달" 기본값 같은 편의 기능을
 * 넣고 싶은 유혹이 있는데, 넣는 순간 응답이 실행 시점에 따라 달라진다. 이
 * 과제에서 시각은 전부 요청이 준다.
 */
class NoCurrentTimeUsageTest {

    private static final List<String> FORBIDDEN = List.of(
            "Instant.now(", "LocalDate.now(", "LocalDateTime.now(",
            "ZonedDateTime.now(", "OffsetDateTime.now(",
            "System.currentTimeMillis(", "new Date(");

    /**
     * 이 파일에는 금지 문자열이 리터럴로 들어 있다. 빼지 않으면 자기를 잡아
     * <b>항상 실패한다.</b> 문자열을 쪼개 숨기는 방법도 있지만 다음 사람이 왜
     * 그렇게 썼는지 모른다.
     */
    private static final String SELF = "NoCurrentTimeUsageTest.java";

    @Test
    @DisplayName("소스 어디에도 현재 시각을 읽는 호출이 없다")
    void noCurrentTimeCalls() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path root : List.of(Path.of("src/main/java"), Path.of("src/test/java"))) {
            hits.addAll(scan(root));
        }

        assertThat(hits)
                .as("현재 시각을 읽으면 기대값이 실행 시점에 따라 달라진다")
                .isEmpty();
    }

    private static List<String> scan(Path root) throws IOException {
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals(SELF))
                    .toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    for (String banned : FORBIDDEN) {
                        if (line.contains(banned)) {
                            hits.add(file + ":" + (i + 1) + " -> " + banned);
                        }
                    }
                }
            }
        }
        return hits;
    }

    /** 스캐너가 실제로 파일을 읽고 있는지 본다. 0개를 훑고 통과하면 무의미하다. */
    @Test
    @DisplayName("스캐너가 소스 파일을 실제로 훑는다")
    void scannerReadsSources() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            long javaFiles = files.filter(p -> p.toString().endsWith(".java")).count();
            assertThat(javaFiles)
                    .as("작업 디렉터리가 어긋나면 아무것도 안 훑고 통과한다")
                    .isGreaterThan(20);
        }
    }
}
