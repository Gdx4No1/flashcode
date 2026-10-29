package com.gejia.flashcode.flashcodeportalservice.flash.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
@Slf4j
public class VectorDataInit implements ApplicationRunner {

    @Autowired
    private MilvusVectorStore vectorStore;

    @Override
    public void run(ApplicationArguments args) {

        List<Document> documents = List.of(
                new Document("1", "Flashcode 是葛佳维护的 AI 应用生成项目，基于课程框架学习与完善。", Map.of(
                        "type", "course",
                        "name", "Flashcode 项目介绍",
                        "company", "flashcode"
                )),
                new Document("2","Flashcode 的实践方向包括需求整理、代码生成、构建预览和应用管理。", Map.of(
                        "type", "teacher",
                        "name", "Flashcode 功能概览",
                        "company", "flashcode"
                )),
                new Document("3","使用 Flashcode 时，可先描述应用需求，再逐步确认需要的页面和功能。", Map.of(
                        "type", "teacher_detail",
                        "name", "需求整理说明",
                        "company", "flashcode"
                )),
                new Document("4","Flashcode 的模型生成、构建和预览功能需要对应的个人模型服务与运行环境。",Map.of(
                        "type", "teacher_detail",
                        "name", "运行环境说明",
                        "company", "flashcode"
                )),
                new Document("5","Flashcode 的维护者为葛佳。项目保留原框架来源与使用条件，功能完成情况以实际验收为准。",Map.of(
                        "type", "teacher_detail",
                        "name", "项目维护说明",
                        "company", "flashcode"
                ))
        );

        vectorStore.add(documents);
        log.info("Vector data initialized");
    }

}
