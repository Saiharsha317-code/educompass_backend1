package com.educompass.backend.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "discussion_threads")
public class DiscussionThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Thread title is required")
    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User creator;

    @NotBlank(message = "Category is required")
    @Column(nullable = false)
    private String category; // e.g. "Intermediate", "Polytechnic", "Govt Seats", "General"

    @NotBlank(message = "Thread content is required")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "reply_count")
    private int replyCount = 0;

    @OneToMany(mappedBy = "thread", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("createdAt ASC")
    @JsonManagedReference
    private List<ThreadReply> replies = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public DiscussionThread() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addReply(ThreadReply reply) {
        replies.add(reply);
        reply.setThread(this);
        this.replyCount = replies.size();
    }

    public void removeReply(ThreadReply reply) {
        replies.remove(reply);
        reply.setThread(null);
        this.replyCount = replies.size();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public User getCreator() {
        return creator;
    }

    public void setCreator(User creator) {
        this.creator = creator;
    }

    public Long getCreatorId() {
        return creator != null ? creator.getId() : null;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getReplyCount() {
        return replyCount;
    }

    public void setReplyCount(int replyCount) {
        this.replyCount = replyCount;
    }

    public List<ThreadReply> getReplies() {
        return replies;
    }

    public void setReplies(List<ThreadReply> replies) {
        this.replies = replies;
        this.replyCount = (replies != null) ? replies.size() : 0;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
}
