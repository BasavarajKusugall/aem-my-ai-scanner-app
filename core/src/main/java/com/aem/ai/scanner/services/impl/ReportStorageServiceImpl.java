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
            // Resolve base path (prefer method param, else OSGi config, else default)
            String folderPath = overrideBasePath != null ? overrideBasePath : this.basePath;
            if (folderPath == null || folderPath.isEmpty()) {
                folderPath = "/var/mytrades";
            }

            // Create folder structure by date: /base/yyyy/MM/dd
            folderPath += "/" + LocalDate.now().getYear() + "/"
                    + String.format("%02d", LocalDate.now().getMonthValue()) + "/"
                    + String.format("%02d", LocalDate.now().getDayOfMonth());

            Resource folderRes = ResourceUtil.getOrCreateResource(resolver, folderPath,
                    Collections.singletonMap("jcr:primaryType", "sling:Folder"),
                    null, true);

            // File resource
            String filePath = folderPath + "/" + fileName;
            Resource fileRes = ResourceUtil.getOrCreateResource(resolver, filePath,
                    Collections.singletonMap("jcr:primaryType", "nt:file"),
                    null, true);

            // jcr:content node under file
            String contentPath = filePath + "/jcr:content";
            Resource contentRes = ResourceUtil.getOrCreateResource(resolver, contentPath,
                    Collections.singletonMap("jcr:primaryType", "nt:resource"),
                    null, true);

            // Set binary properties
            ModifiableValueMap props = contentRes.adaptTo(ModifiableValueMap.class);
            if (props != null) {
                Session session = resolver.adaptTo(Session.class);
                if (session != null) {
                    Binary binary = session.getValueFactory().createBinary(
                            new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))
                    );
                    props.put("jcr:data", binary);
                } else {
                    LOG.warn("Could not adapt resolver to Session, storing plain bytes for {}", filePath);
                    props.put("jcr:data", content.getBytes(StandardCharsets.UTF_8));
                }

                props.put("jcr:mimeType", mimeType);
                props.put("jcr:lastModified", Calendar.getInstance());
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
            description = "Configures where reports are stored in AEM"
    )
    public @interface ReportStorageConfig {

        @AttributeDefinition(
                name = "Base Path",
                description = "Base repository path where reports will be stored (e.g. /var/mytrades)"
        )
        String basePath() default "/var/mytrades";
    }
}
