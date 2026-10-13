
package com.educompass.backend.service;

import com.educompass.backend.entity.DiscussionThread;
import com.educompass.backend.entity.ThreadReply;
import com.educompass.backend.entity.User;
import com.educompass.backend.exception.ResourceNotFoundException;
import com.educompass.backend.repository.DiscussionThreadRepository;
import com.educompass.backend.repository.ThreadReplyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class DiscussionThreadService {

    private final DiscussionThreadRepository threadRepository;
    private final ThreadReplyRepository replyRepository;

    public DiscussionThreadService(
            DiscussionThreadRepository threadRepository,
            ThreadReplyRepository replyRepository) {
        this.threadRepository = threadRepository;
        this.replyRepository = replyRepository;
    }

    @Transactional(readOnly = true)
    public List<DiscussionThread> getAllThreads(String category) {
        if (category != null && !category.trim().isEmpty()) {
            return threadRepository
                    .findByCategoryIgnoreCaseOrderByCreatedAtDesc(category.trim());
        }
        return threadRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public DiscussionThread getThreadById(Long id) {
        return threadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Discussion thread not found with ID: " + id));
    }

    public DiscussionThread createThread(DiscussionThread thread, User currentUser) {
        requireUser(currentUser);

        thread.setId(null);
        thread.setAuthor(currentUser.getName());
        thread.setCreator(currentUser);
        thread.setReplyCount(0);
        thread.setReplies(new java.util.ArrayList<>());

        return threadRepository.save(thread);
    }

    public DiscussionThread updateThread(
            Long id, DiscussionThread updated, User currentUser) {
        DiscussionThread existing = getThreadById(id);
        requireOwner(existing, currentUser);

        existing.setTitle(updated.getTitle());
        existing.setContent(updated.getContent());
        existing.setCategory(updated.getCategory());

        return threadRepository.save(existing);
    }

    public void deleteThread(Long id, User currentUser) {
        DiscussionThread existing = getThreadById(id);
        requireOwner(existing, currentUser);
        threadRepository.delete(existing);
    }

    public DiscussionThread addReply(
            Long threadId, String content, User currentUser) {
        requireUser(currentUser);

        DiscussionThread thread = getThreadById(threadId);
        ThreadReply reply = new ThreadReply(currentUser.getName(), content);
        reply.setCreator(currentUser);
        thread.addReply(reply);

        return threadRepository.save(thread);
    }

    public ThreadReply updateReply(
            Long threadId, Long replyId, String content, User currentUser) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Reply content cannot be empty.");
        }

        DiscussionThread thread = getThreadById(threadId);
        ThreadReply reply = replyRepository.findById(replyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reply not found with ID: " + replyId));

        if (reply.getThread() == null || !Objects.equals(reply.getThread().getId(), thread.getId())) {
            throw new IllegalArgumentException(
                    "Reply with ID " + replyId + " does not belong to thread with ID " + threadId);
        }

        requireReplyOwner(reply, currentUser);

        reply.setContent(content.trim());
        return replyRepository.save(reply);
    }

    public void deleteReply(
            Long threadId, Long replyId, User currentUser) {
        DiscussionThread thread = getThreadById(threadId);
        ThreadReply reply = replyRepository.findById(replyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reply not found with ID: " + replyId));

        if (reply.getThread() == null || !Objects.equals(reply.getThread().getId(), thread.getId())) {
            throw new IllegalArgumentException(
                    "Reply with ID " + replyId + " does not belong to thread with ID " + threadId);
        }

        requireReplyOwner(reply, currentUser);

        thread.getReplies().removeIf(r -> Objects.equals(r.getId(), replyId));
        thread.setReplyCount(thread.getReplies().size());
        replyRepository.delete(reply);
        threadRepository.save(thread);
    }

    private void requireUser(User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AccessDeniedException("Please sign in to perform this action.");
        }
    }

    private void requireOwner(DiscussionThread thread, User currentUser) {
        requireUser(currentUser);

        if (thread.getCreator() == null
                || !Objects.equals(thread.getCreator().getId(), currentUser.getId())) {
            throw new AccessDeniedException(
                    "You can only edit or delete questions that you created.");
        }
    }

    private void requireReplyOwner(ThreadReply reply, User currentUser) {
        requireUser(currentUser);

        if (reply.getCreator() == null
                || !Objects.equals(reply.getCreator().getId(), currentUser.getId())) {
            throw new AccessDeniedException(
                    "You can only edit or delete replies that you created.");
        }
    }
}
