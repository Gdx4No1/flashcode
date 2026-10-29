package com.gejia.flashcode.flashcodeportalservice.flash.utils;

import com.gejia.flashcode.flashcodeportalservice.flash.enums.AppType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelOutPutParserTest {
    @Test
    void rejectsTruncatedFileFence() {
        assertThrows(IllegalArgumentException.class, () -> ModelOutPutParser.parse(
                "APP_TYPE=VUE3\nFILE: src/App.vue\n```vue\n<style scoped>\n.title {"));
    }


    @Test
    void keepsFileMarkerInsideGeneratedCode() {
        var result = ModelOutPutParser.parse("APP_TYPE=HTML\nFILE: index.html\n```html\n<pre>\nFILE: example.txt\n</pre>\n```\n");
        assertEquals(1, result.getFiles().size());
        assertTrue(result.getFiles().get("index.html").contains("FILE: example.txt"));
    }

    @Test
    void validatesCompleteVueSpringApplication() {
        ModelOutPutParser.ParseResult result = new ModelOutPutParser.ParseResult();
        result.setAppType(AppType.VUE_SPRING);
        result.setFiles(files(
                "frontend/package.json",
                "frontend/index.html",
                "frontend/src/main.js",
                "frontend/src/App.vue",
                "backend/pom.xml",
                "backend/src/main/java/com/example/DemoApplication.java"));

        assertDoesNotThrow(() -> ModelOutPutParser.validate(result));
    }

    @Test
    void rejectsVueApplicationWithoutEntryFile() {
        ModelOutPutParser.ParseResult result = new ModelOutPutParser.ParseResult();
        result.setAppType(AppType.VUE);
        result.setFiles(files("package.json", "index.html", "src/App.vue"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ModelOutPutParser.validate(result));

        assertEquals("生成应用缺少必要文件: src/main.js", exception.getMessage());
    }

    @Test
    void parsesAndValidatesSingleHtmlApplication() {
        String output = """
                APP_TYPE=HTML
                FILE: index.html
                ```html
                <!doctype html><html><body>Hello</body></html>
                ```
                """;

        ModelOutPutParser.ParseResult result = ModelOutPutParser.parse(output);

        assertEquals(AppType.HTML, result.getAppType());
        assertDoesNotThrow(() -> ModelOutPutParser.validate(result));
    }

    @Test
    void ignoresModelThinkingSectionBeforeGeneratedFiles() {
        String output = """
                <think>
                FILE: ignored.txt
                ```text
                reasoning
                ```
                </think>
                APP_TYPE=HTML
                FILE: index.html
                ```html
                <!doctype html><html><body>Hello</body></html>
                ```
                """;

        ModelOutPutParser.ParseResult result = ModelOutPutParser.parse(output);

        assertEquals(AppType.HTML, result.getAppType());
        assertEquals(1, result.getFiles().size());
        assertTrue(result.getFiles().containsKey("index.html"));
    }

    private Map<String, String> files(String... paths) {
        Map<String, String> files = new LinkedHashMap<>();
        for (String path : paths) {
            files.put(path, "content");
        }
        return files;
    }
}
