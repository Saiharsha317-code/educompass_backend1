package com.educompass.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class ThreadReplyRequest {

    private String author;

    @NotBlank(message = "Content is required")
    private String content;

    public ThreadReplyRequest() {}

    public ThreadReplyRequest(String author, String content) {
        this.author = author;
        this.content = content;
    }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
