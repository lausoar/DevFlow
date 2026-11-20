package com.devflow.service.impl;

import com.devflow.entity.Document;
import com.devflow.entity.DocumentIndexOutbox;
import com.devflow.mapper.DocumentIndexOutboxMapper;
import com.devflow.service.AiKnowledgeSyncService;
import com.devflow.service.DocumentIndexOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIndexOutboxServiceImplTest {

    @Mock private DocumentIndexOutboxMapper outboxMapper;
    @Mock private AiKnowledgeSyncService aiKnowledgeSyncService;

    private DocumentIndexOutboxServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentIndexOutboxServiceImpl(outboxMapper, aiKnowledgeSyncService, 5, 60);
    }

    @Test
    void enqueueMethodsJoinTheAuthoritativeDocumentTransaction() throws Exception {
        var enqueue = DocumentIndexOutboxServiceImpl.class.getMethod("enqueue", Document.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        var enqueueDeletion = DocumentIndexOutboxServiceImpl.class
                .getMethod("enqueueDeletion", Document.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);

        assertThat(enqueue).isNotNull();
        assertThat(enqueueDeletion).isNotNull();
        assertThat(enqueue.propagation())
                .isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRED);
        assertThat(enqueueDeletion.propagation())
                .isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRED);
    }

    @Test
    void duplicateDocumentVersionDoesNotCreateASecondPendingRecord() {
        Document document = document("body");
        document.setUpdatedAt(LocalDateTime.of(2026, 11, 20, 10, 15, 30, 123_456_789));
        when(outboxMapper.insertIfAbsent(any())).thenReturn(1, 0);

        DocumentIndexOutbox first = service.enqueue(document);
        DocumentIndexOutbox duplicate = service.enqueue(document);

        assertThat(first).isNotNull();
        assertThat(duplicate).isNull();
        ArgumentCaptor<DocumentIndexOutbox> captor = ArgumentCaptor.forClass(DocumentIndexOutbox.class);
        verify(outboxMapper, org.mockito.Mockito.times(2)).insertIfAbsent(captor.capture());
        DocumentIndexOutbox inserted = captor.getAllValues().get(0);
        assertThat(inserted.getOperation()).isEqualTo(DocumentIndexOutboxService.UPSERT);
        assertThat(inserted.getStatus()).isEqualTo(DocumentIndexOutboxService.PENDING);
        assertThat(inserted.getAttempts()).isZero();
        assertThat(inserted.getDocumentUpdatedAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 20, 10, 15, 30, 123_000_000));
        assertThat(captor.getAllValues().get(1).getDocumentUpdatedAt())
                .isEqualTo(inserted.getDocumentUpdatedAt());
    }

    @Test
    void confirmedIndexSuccessMarksDoneAndNeverUsesAsyncBypass() {
        DocumentIndexOutbox outbox = outbox(DocumentIndexOutboxService.UPSERT);

        service.process(outbox);

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(aiKnowledgeSyncService).indexDocumentSync(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getId()).isEqualTo("d1");
        assertThat(documentCaptor.getValue().getProjectId()).isEqualTo("p1");
        assertThat(documentCaptor.getValue().getEnterpriseId()).isEqualTo("e1");
        assertThat(documentCaptor.getValue().getContent()).isEqualTo("body");
        assertThat(outbox.getStatus()).isEqualTo(DocumentIndexOutboxService.DONE);
        assertThat(outbox.getAttempts()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isNull();
        assertThat(outbox.getLastError()).isNull();
        verify(outboxMapper).updateState(outbox);
        verify(aiKnowledgeSyncService, never()).indexDocument(any());
        verify(aiKnowledgeSyncService, never()).deleteDocument(any(), any(), any());
    }

    @Test
    void failureSchedulesExponentialRetryWithError() {
        DocumentIndexOutbox outbox = outbox(DocumentIndexOutboxService.UPSERT);
        doThrow(new IllegalStateException("chroma unavailable"))
                .when(aiKnowledgeSyncService).indexDocumentSync(any());
        LocalDateTime before = LocalDateTime.now();

        service.process(outbox);

        assertThat(outbox.getStatus()).isEqualTo(DocumentIndexOutboxService.RETRY);
        assertThat(outbox.getAttempts()).isEqualTo(1);
        assertThat(outbox.getLastError()).contains("chroma unavailable");
        assertThat(outbox.getNextAttemptAt()).isAfter(before.plusSeconds(50));
        LocalDateTime firstRetry = outbox.getNextAttemptAt();

        service.process(outbox);

        assertThat(outbox.getStatus()).isEqualTo(DocumentIndexOutboxService.RETRY);
        assertThat(outbox.getAttempts()).isEqualTo(2);
        assertThat(outbox.getNextAttemptAt()).isAfter(firstRetry.plusSeconds(50));
        verify(outboxMapper, org.mockito.Mockito.times(2)).updateState(outbox);
    }

    @Test
    void failureAtMaximumAttemptsMarksFailed() {
        DocumentIndexOutbox outbox = outbox(DocumentIndexOutboxService.UPSERT);
        outbox.setStatus(DocumentIndexOutboxService.RETRY);
        outbox.setAttempts(4);
        doThrow(new IllegalStateException("still down"))
                .when(aiKnowledgeSyncService).indexDocumentSync(any());

        service.process(outbox);

        assertThat(outbox.getStatus()).isEqualTo(DocumentIndexOutboxService.FAILED);
        assertThat(outbox.getAttempts()).isEqualTo(5);
        assertThat(outbox.getNextAttemptAt()).isNull();
        assertThat(outbox.getLastError()).contains("still down");
        verify(outboxMapper).updateState(outbox);
    }

    @Test
    void retryCanSucceedAfterAiRecovers() {
        DocumentIndexOutbox outbox = outbox(DocumentIndexOutboxService.UPSERT);
        outbox.setStatus(DocumentIndexOutboxService.RETRY);
        outbox.setAttempts(1);
        doThrow(new IllegalStateException("temporary outage")).doNothing()
                .when(aiKnowledgeSyncService).indexDocumentSync(any());

        service.process(outbox);
        assertThat(outbox.getStatus()).isEqualTo(DocumentIndexOutboxService.RETRY);
        assertThat(outbox.getAttempts()).isEqualTo(2);

        service.process(outbox);

        assertThat(outbox.getStatus()).isEqualTo(DocumentIndexOutboxService.DONE);
        assertThat(outbox.getAttempts()).isEqualTo(3);
        assertThat(outbox.getLastError()).isNull();
        assertThat(outbox.getNextAttemptAt()).isNull();
    }

    @Test
    void BlankContentEnqueuesAndExecutesScopedDelete() {
        Document document = document("  \n\t");
        when(outboxMapper.insertIfAbsent(any())).thenReturn(1);

        DocumentIndexOutbox outbox = service.enqueue(document);
        service.process(outbox);

        assertThat(outbox.getOperation()).isEqualTo(DocumentIndexOutboxService.DELETE);
        verify(aiKnowledgeSyncService).deleteDocumentSync("d1", "p1", "e1");
        verify(aiKnowledgeSyncService, never()).indexDocumentSync(any());
        verify(aiKnowledgeSyncService, never()).deleteDocument(any(), any(), any());
        assertThat(outbox.getStatus()).isEqualTo(DocumentIndexOutboxService.DONE);
    }

    @Test
    void LogicalDeleteUsesPersistedScopeAndTimestamp() {
        Document beforeDelete = document("body");
        beforeDelete.setProjectId("old-project");
        beforeDelete.setEnterpriseId("old-enterprise");
        Document persisted = document("body");
        persisted.setDeleted(1);
        persisted.setUpdatedAt(LocalDateTime.of(2026, 11, 20, 12, 0));
        when(outboxMapper.selectDocumentIncludingDeleted("d1")).thenReturn(persisted);
        when(outboxMapper.insertIfAbsent(any())).thenReturn(1);

        DocumentIndexOutbox outbox = service.enqueueDeletion(beforeDelete);
        service.process(outbox);

        assertThat(outbox.getOperation()).isEqualTo(DocumentIndexOutboxService.DELETE);
        assertThat(outbox.getDocumentUpdatedAt()).isEqualTo(persisted.getUpdatedAt());
        verify(aiKnowledgeSyncService).deleteDocumentSync("d1", "p1", "e1");
        verify(aiKnowledgeSyncService, never()).deleteDocument(any(), any(), any());
    }

    @Test
    void reconciliationEnqueuesCurrentAndDeletedVersionsWithoutDuplicatingHistory() {
        Document current = document("current body");
        Document deleted = document("deleted body");
        deleted.setId("d2");
        deleted.setDeleted(1);
        when(outboxMapper.selectAllDocumentsIncludingDeleted()).thenReturn(List.of(current, deleted));
        when(outboxMapper.insertIfAbsent(any())).thenReturn(1, 0);

        service.reconcile();

        ArgumentCaptor<DocumentIndexOutbox> captor = ArgumentCaptor.forClass(DocumentIndexOutbox.class);
        verify(outboxMapper, org.mockito.Mockito.times(2)).insertIfAbsent(captor.capture());
        assertThat(captor.getAllValues().get(0).getOperation()).isEqualTo(DocumentIndexOutboxService.UPSERT);
        assertThat(captor.getAllValues().get(1).getOperation()).isEqualTo(DocumentIndexOutboxService.DELETE);
        verify(outboxMapper).requeueFailed(eq("d2"), eq(DocumentIndexOutboxService.DELETE),
                eq(deleted.getUpdatedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)), any(), any());
        verify(outboxMapper, never()).requeueFailed(eq("d1"), any(), any(), any(), any());
    }

    private static Document document(String content) {
        Document document = new Document();
        document.setId("d1");
        document.setTitle("Design");
        document.setContent(content);
        document.setProjectId("p1");
        document.setEnterpriseId("e1");
        document.setType("WIKI");
        document.setDeleted(0);
        document.setUpdatedAt(LocalDateTime.of(2026, 11, 20, 10, 0));
        return document;
    }

    private static DocumentIndexOutbox outbox(String operation) {
        DocumentIndexOutbox outbox = new DocumentIndexOutbox();
        outbox.setId("o1");
        outbox.setDocumentId("d1");
        outbox.setProjectId("p1");
        outbox.setEnterpriseId("e1");
        outbox.setOperation(operation);
        outbox.setTitle("Design");
        outbox.setContent("body");
        outbox.setDocType("WIKI");
        outbox.setDocumentUpdatedAt(LocalDateTime.of(2026, 11, 20, 10, 0));
        outbox.setStatus(DocumentIndexOutboxService.PENDING);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(LocalDateTime.now());
        return outbox;
    }
}
