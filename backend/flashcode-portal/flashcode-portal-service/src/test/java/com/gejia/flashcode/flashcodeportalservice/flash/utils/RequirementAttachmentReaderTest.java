package com.gejia.flashcode.flashcodeportalservice.flash.utils;

import com.gejia.flashcode.flashcodecommondomain.exception.ServiceException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class RequirementAttachmentReaderTest {
    private MockMultipartFile file(String name, byte[] bytes) { return new MockMultipartFile("sketch",name,"application/octet-stream",bytes); }
    private byte[] zip(String xml) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) { zip.putNextEntry(new ZipEntry("word/document.xml"));zip.write(xml.getBytes(StandardCharsets.UTF_8));zip.closeEntry(); }
        return out.toByteArray();
    }
    private void fixture(String name, byte[] bytes) throws IOException {
        String root=System.getProperty("flashcode.fixture.root");
        if(root!=null) { Files.createDirectories(Path.of(root));Files.write(Path.of(root,name),bytes); }
    }
    @Test void textOnlyIsUnchanged() { assertEquals("原始需求",RequirementAttachmentReader.combine(" 原始需求 ",null)); }
    @Test void readsUtf8BomAndAttachmentWithoutInput() {
        String text=RequirementAttachmentReader.combine(null,file("测试.TXT","\ufeff附件蓝鲸计时器".getBytes(StandardCharsets.UTF_8)));
        assertTrue(text.contains("附件蓝鲸计时器"));assertFalse(text.contains("\ufeff"));
    }
    @Test void readsLegacyChineseAndUtf16() {
        for(Charset charset:new Charset[]{Charset.forName("GB18030"),StandardCharsets.UTF_16})
            assertTrue(RequirementAttachmentReader.combine("需求",file("需求.txt","中文附件内容".getBytes(charset))).contains("中文附件内容"));
    }
    @Test void readsDocxRunsParagraphsAndTables() throws Exception {
        byte[] bytes=zip("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>附件专属</w:t></w:r><w:r><w:t>蓝鲸番茄钟</w:t></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>25分钟</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>");
        String text=RequirementAttachmentReader.combine(null,file("requirements.docx",bytes));assertTrue(text.contains("附件专属蓝鲸番茄钟\n25分钟"));fixture("requirements.docx",bytes);
    }
    @Test void readsRealPdfAndRejectsBlankPdf() throws Exception {
        byte[] bytes;
        try(var document=new PDDocument(); var out=new ByteArrayOutputStream()) {
            var page=new PDPage();document.addPage(page);
            try(var stream=new PDPageContentStream(document,page)) {stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(30,700);stream.showText("OceanTimer: a blue 25 minute countdown. Application name: OceanTimer.");stream.endText();}
            document.save(out);bytes=out.toByteArray();
        }
        assertTrue(RequirementAttachmentReader.combine(null,file("requirements.pdf",bytes)).contains("OceanTimer"));fixture("requirements.pdf",bytes);
        try(var document=new PDDocument();var out=new ByteArrayOutputStream()) {document.addPage(new PDPage());document.save(out);assertThrows(ServiceException.class,()->RequirementAttachmentReader.combine("有文字",file("blank.pdf",out.toByteArray())));}
    }
    @Test void rejectsEmptyOversizedUnsupportedAndBinary() {
        for(var file:new MockMultipartFile[]{file("a.txt",new byte[0]),file("a.txt",new byte[RequirementAttachmentReader.MAX_BYTES+1]),file("a.png",new byte[]{1}),file("a.doc",new byte[]{1}),file("fake.txt",new byte[]{0,1,2}),file("a.txt"," ".getBytes())}) {
            var ex=assertThrows(ServiceException.class,()->RequirementAttachmentReader.combine("需求",file));assertEquals(400000,ex.getCode());
        }
    }
    @Test void rejectsCorruptAndExternalXmlWithoutReadingLocalFile() throws Exception {
        assertThrows(ServiceException.class,()->RequirementAttachmentReader.combine(null,file("a.pdf","not pdf".getBytes())));
        assertThrows(ServiceException.class,()->RequirementAttachmentReader.combine(null,file("a.docx","not zip".getBytes())));
        var malicious=zip("<!DOCTYPE document [<!ENTITY ex SYSTEM 'file:///D:/not-to-read'>]><document>&ex;</document>");
        assertThrows(ServiceException.class,()->RequirementAttachmentReader.combine(null,file("a.docx",malicious)));
    }
    @Test void rejectsZipExpansionAndCombinedTextLimit() throws Exception {
        var bomb=zip(" ".repeat(8*1024*1024+1));assertThrows(ServiceException.class,()->RequirementAttachmentReader.combine(null,file("a.docx",bomb)));
        assertThrows(ServiceException.class,()->RequirementAttachmentReader.combine("x".repeat(50000),file("a.md","more".getBytes())));
        assertThrows(ServiceException.class,()->RequirementAttachmentReader.combine(null,null));
    }
}
