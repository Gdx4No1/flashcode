<template>
    <section class="about-intro">
        <div class="container">
            <div class="intro-left">
                <div class="intro-title">项目介绍</div>
                <p class="intro-text">
                    Flashcode 是一个 AI 应用生成工具，提供需求整理、代码生成、构建预览和应用管理。
                </p>
                <p class="intro-text">
                    通过自然语言描述应用需求，在工作台查看需求文档、生成代码和预览页面，集中管理应用与源码。
                </p>
            </div>
            <div class="intro-right">
                <img :src="highlightImg" alt="项目亮点" />
            </div>
        </div>
    </section>
</template>

<script setup>
const highlightImg = new URL('@/imges/flashcode-overview.svg', import.meta.url).href;
</script>

<style scoped lang="scss">
.about-intro {
    padding: 64px 0 72px;
    background-color: #ffffff;
}

.container {
    max-width: 1200px;
    margin: 0 auto;
    padding: 0 24px;
    display: grid;
    grid-template-columns: 1fr 1fr;
    align-items: center;
    gap: 40px;
}

.intro-left {
    display: flex;
    flex-direction: column;
    gap: 24px;
}

.intro-title {
    position: relative;
    font-size: 24px;
    font-weight: 600;
    color: #1f2937;
}

.intro-title::before {
    content: "“";
    position: absolute;
    left: 3px;
    top: -6px;
    font-size: 82px;
    line-height: 52px;
    color: rgba(0, 0, 0, 0.08);
    pointer-events: none;
}

.intro-text {
    font-size: 16px;
    line-height: 27px;
    color: #333333;
    margin: 0;
}

.intro-right img {
    width: 100%;
    max-width: 612px;
    aspect-ratio: 612 / 374;
    object-fit: cover;
    border-radius: 4px;
    display: block;
    margin-left: auto;
}

@media (max-width: 900px) {
    .container {
        grid-template-columns: 1fr;
    }

    .intro-right img {
        margin: 0 auto;
    }
}
</style>
