package com.devflow.service;

import com.devflow.entity.Document;
import com.devflow.entity.DocumentIndexOutbox;

public interface DocumentIndexOutboxService {

    String UPSERT = "UPSERT";
    String DELETE = "DELETE";
    String PENDING = "PENDING";
    String RETRY = "RETRY";
    String DONE = "DONE";
    String FAILED = "FAILED";

    /** Enqueue in the caller's transaction; blank content is a DELETE. */
    DocumentIndexOutbox enqueue(Document document);

    /** Enqueue a deletion using the persisted, logically deleted document scope. */
    DocumentIndexOutbox enqueueDeletion(Document document);

    /** Execute one command synchronously and persist its resulting state. */
    void process(DocumentIndexOutbox outbox);

    /** Reconcile every current and logically deleted document without duplicates. */
    void reconcile();
}
