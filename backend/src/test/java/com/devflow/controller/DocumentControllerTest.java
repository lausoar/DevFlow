package com.devflow.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.devflow.dto.request.CreateDocumentRequest;
import com.devflow.dto.request.UpdateDocumentRequest;
import com.devflow.entity.Document;
import com.devflow.entity.DocumentVersion;
import com.devflow.mapper.DocumentCommentMapper;
import com.devflow.mapper.DocumentMapper;
import com.devflow.mapper.DocumentVersionMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.AiKnowledgeSyncService;
import com.devflow.service.DocumentIndexOutboxService;
import com.devflow.service.NotificationRecipients;
import com.devflow.service.NotificationService;
import com.devflow.service.PermissionService;
import com.devflow.service.ProjectAccessService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock private DocumentMapper documentMapper;
    @Mock private DocumentIndexOutboxService documentIndexOutboxService;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private DocumentVersionMapper documentVersionMapper;
    @Mock private DocumentCommentMapper documentCommentMapper;
    @Mock private UserMapper userMapper;
    @Mock private PermissionService permissionService;
    @Mock private NotificationService notificationService;
    @Mock private NotificationRecipients notificationRecipients;
    @Mock private AccessGuard guard;
    @Mock private Authentication authentication;

    @InjectMocks private DocumentController controller;

    @BeforeAll
    static void initTableInfo() {
        Configuration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Document.class);
    }

    @Test
    void createPersistsDocumentBeforeTransactionalEnqueue() {
        mockUser();
        when(guard.enterpriseIdOf("u1")).thenReturn("e1");
        doAnswer(invocation -> {
            invocation.getArgument(0, Document.class).setId("d1");
            return 1;
        }).when(documentMapper).insert(any(Document.class));
        Document persisted = document("body");
        when(documentMapper.selectById("d1")).thenReturn(persisted);
        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setTitle("Design");
        request.setContent("body");
        request.setProjectId("p1");

        var response = controller.create(request, authentication);

        assertThat(response.getData()).isSameAs(persisted);
        InOrder order = inOrder(documentMapper, documentIndexOutboxService);
        order.verify(documentMapper).insert(any(Document.class));
        order.verify(documentMapper).selectById("d1");
        order.verify(documentIndexOutboxService).enqueue(persisted);
    }

    @Test
    void updateEnqueuesTheMergedPersistedDocument() {
        mockUser();
        Document old = document("old");
        Document merged = document("new");
        merged.setVersion(2);
        when(documentMapper.selectById("d1")).thenReturn(old, merged);
        when(projectAccessService.canView("u1", "p1")).thenReturn(true);
        when(permissionService.hasProjectPermission("u1", "p1", "document:manage")).thenReturn(true);
        when(documentMapper.update(any(Document.class), any())).thenReturn(1);
        UpdateDocumentRequest request = new UpdateDocumentRequest();
        request.setContent("new");
        request.setExpectedVersion(1);

        var response = controller.update("d1", request, authentication);

        assertThat(response.getData()).isSameAs(merged);
        verify(documentIndexOutboxService).enqueue(merged);
    }

    @Test
    void restoreEnqueuesTheRestoredPersistedDocument() {
        mockUser();
        Document old = document("current");
        Document restored = document("historic");
        restored.setVersion(2);
        DocumentVersion version = new DocumentVersion();
        version.setDocumentId("d1");
        version.setTitle("Design");
        version.setContent("historic");
        when(documentMapper.selectById("d1")).thenReturn(old, restored);
        when(documentVersionMapper.selectById("v1")).thenReturn(version);
        when(projectAccessService.canView("u1", "p1")).thenReturn(true);
        when(permissionService.hasProjectPermission("u1", "p1", "document:manage")).thenReturn(true);
        when(documentMapper.updateById(any(Document.class))).thenReturn(1);

        var response = controller.restoreVersion("d1", "v1", authentication);

        assertThat(response.getData()).isSameAs(restored);
        verify(documentIndexOutboxService).enqueue(restored);
    }

    @Test
    void deletePersistsSoftDeleteBeforeScopedOutboxEnqueue() {
        mockUser();
        Document document = document("body");
        when(documentMapper.selectById("d1")).thenReturn(document);
        when(projectAccessService.canView("u1", "p1")).thenReturn(true);
        when(permissionService.hasProjectPermission("u1", "p1", "document:delete")).thenReturn(true);
        when(documentMapper.deleteById("d1")).thenReturn(1);

        controller.delete("d1", authentication);

        InOrder order = inOrder(documentMapper, documentIndexOutboxService);
        order.verify(documentMapper).deleteById("d1");
        order.verify(documentIndexOutboxService).enqueueDeletion(document);
    }

    @Test
    void allWritePathsAreTransactionalAndHaveNoDirectAiClient() throws Exception {
        assertThat(DocumentController.class.getMethod("create", CreateDocumentRequest.class, Authentication.class)
                .getAnnotation(Transactional.class)).isNotNull();
        assertThat(DocumentController.class.getMethod("update", String.class, UpdateDocumentRequest.class,
                Authentication.class).getAnnotation(Transactional.class)).isNotNull();
        assertThat(DocumentController.class.getMethod("restoreVersion", String.class, String.class,
                Authentication.class).getAnnotation(Transactional.class)).isNotNull();
        assertThat(DocumentController.class.getMethod("delete", String.class, Authentication.class)
                .getAnnotation(Transactional.class)).isNotNull();
        assertThat(Arrays.stream(DocumentController.class.getDeclaredFields()).map(Field::getType))
                .doesNotContain(AiKnowledgeSyncService.class);
    }

    private void mockUser() {
        when(guard.userId(authentication)).thenReturn("u1");
    }

    private static Document document(String content) {
        Document document = new Document();
        document.setId("d1");
        document.setTitle("Design");
        document.setContent(content);
        document.setProjectId("p1");
        document.setEnterpriseId("e1");
        document.setType("DOC");
        document.setVersion(1);
        document.setDeleted(0);
        document.setUpdatedAt(LocalDateTime.of(2026, 11, 20, 10, 0));
        return document;
    }
}
