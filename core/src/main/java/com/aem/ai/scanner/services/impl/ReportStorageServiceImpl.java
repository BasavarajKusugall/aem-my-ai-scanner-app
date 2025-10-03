package com.aem.ai.scanner.services.impl;

import com.aem.ai.scanner.services.ReportStorageService;
import com.aem.ai.scanner.services.ResolverService;
import org.apache.sling.api.resource.*;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Binary;
import javax.jcr.Session;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Component(
        service = ReportStorageService.class,
        configurationPid = "com.aem.ai.scanner.config.ReportStorageConfig"
)
public class ReportStorageServiceImpl implements ReportStorageService {

    private static final Logger LOG = LoggerFactory.getLogger(ReportStorageServiceImpl.class);

    @Reference
    private ResolverService resolverService;

    private volatile String basePath;

    @Activate
    @Modified
    protected void activate(ReportStorageConfig config) {
        this.basePath = config.basePath();
    }

    @Override
    public void storeReport(String overrideBasePath,
                            String fileName,
                            String content,
                            String mimeType) throws Exception {

        try (ResourceResolver resolver = resolverService.getServiceResolver()) {
            String folderPath = overrideBasePath != null ? overrideBasePath : this.basePath;
            if (folderPath == null || folderPath.isEmpty()) folderPath = "/var/mytrades";

            // Create dated folder: /base/yyyy/MM/dd
            folderPath += "/" + LocalDate.now().getYear() + "/"
                    + String.format("%02d", LocalDate.now().getMonthValue()) + "/"
                    + String.format("%02d", LocalDate.now().getDayOfMonth());

            Resource folderRes = ResourceUtil.getOrCreateResource(resolver, folderPath,
                    Collections.singletonMap("jcr:primaryType", "sling:Folder"), null, true);

            // Ensure nt:file node exists
            String filePath = folderPath + "/" + fileName;
            Resource fileRes = resolver.getResource(filePath);
            if (fileRes == null) {
                Map<String, Object> fileProps = new HashMap<>();
                fileProps.put("jcr:primaryType", "nt:file");
                fileRes = resolver.create(folderRes, fileName, fileProps);
            }

            // Ensure jcr:content exists
            Resource contentRes = resolver.getResource(filePath + "/jcr:content");
            if (contentRes == null) {
                Map<String, Object> contentProps = new HashMap<>();
                contentProps.put("jcr:primaryType", "nt:resource");
                contentRes = resolver.create(fileRes, "jcr:content", contentProps);
            }

            // Use Node API for binary property
            Session session = resolver.adaptTo(Session.class);
            javax.jcr.Node contentNode = contentRes.adaptTo(javax.jcr.Node.class);

            if (session != null && contentNode != null) {
                try (ByteArrayInputStream bais =
                             new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
                    Binary binary = session.getValueFactory().createBinary(bais);
                    contentNode.setProperty("jcr:data", binary);
                    contentNode.setProperty("jcr:lastModified", Calendar.getInstance());
                    contentNode.setProperty("jcr:mimeType", mimeType != null ? mimeType : "text/plain");
                    contentNode.setProperty("jcr:encoding", "UTF-8");
                }
            } else {
                // fallback (string store)
                ModifiableValueMap props = contentRes.adaptTo(ModifiableValueMap.class);
                if (props != null) {
                    props.put("jcr:data", content);
                    props.put("jcr:lastModified", Calendar.getInstance());
                    props.put("jcr:mimeType", mimeType != null ? mimeType : "text/plain");
                    props.put("jcr:encoding", "UTF-8");
                    LOG.warn("Stored report as plain string (fallback) for {}", filePath);
                }
            }

            resolver.commit();
            LOG.info("Report stored successfully at {}", filePath);

        } catch (Exception e) {
            LOG.error("Error storing report: {}", fileName, e);
            throw e;
        }
    }


    @ObjectClassDefinition(
            name = "Report Storage Configuration",
            description = "Configures where reports will be stored in AEM"
    )
    public @interface ReportStorageConfig {
        @AttributeDefinition(
                name = "Base Path",
                description = "Base repository path where reports will be stored (e.g. /var/mytrades)"
        )
        String basePath() default "/var/mytrades";
    }
}
