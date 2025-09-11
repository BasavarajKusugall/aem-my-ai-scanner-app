package com.aem.ai.scanner.services.impl;

import com.aem.ai.scanner.services.ReportStorageService;
import com.aem.ai.scanner.services.ResolverService;
import org.apache.sling.api.resource.*;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Component(
        service = ReportStorageService.class,
        immediate = true,
        configurationPid = "com.aem.ai.scanner.config.ReportStorageConfig"
)
public class ReportStorageServiceImpl implements ReportStorageService {


    @Reference
    private ResolverService resolverService;

    private volatile String basePath;

    @Activate
    @Modified
    protected void activate(ReportStorageConfig config) {
        this.basePath = config.basePath();
    }

    @Override
    public void storeReport(String basePath,
                            String fileName,
                            String content,
                            String mimeType) throws Exception {

        try (ResourceResolver resolver = resolverService.getServiceResolver()) {

            // Ensure base folder exists
            String folderPath = basePath != null ? basePath : "/var/mytrades";
            folderPath += "/" + LocalDate.now().getYear() + "/"
                    + String.format("%02d", LocalDate.now().getMonthValue()) + "/"
                    + String.format("%02d", LocalDate.now().getDayOfMonth());

            Resource folderRes = resolver.getResource(folderPath);
            if (folderRes == null) {
                folderRes = ResourceUtil.getOrCreateResource(resolver, folderPath, "sling:Folder", null, false);
            }

            // File resource path
            String filePath = folderPath + "/" + fileName;
            Resource fileRes = resolver.getResource(filePath);
            if (fileRes == null) {
                fileRes = ResourceUtil.getOrCreateResource(resolver, filePath, "nt:file", null, false);
            }

            // jcr:content node
            Resource contentRes = fileRes.getChild("jcr:content");
            if (contentRes == null) {
                contentRes = ResourceUtil.getOrCreateResource(resolver, filePath + "/jcr:content", "nt:resource", null, false);
            }

            // Set properties
            ModifiableValueMap props = contentRes.adaptTo(ModifiableValueMap.class);
            props.put("jcr:data", content.getBytes(StandardCharsets.UTF_8));
            props.put("jcr:mimeType", mimeType);
            props.put("jcr:lastModified", Calendar.getInstance());

            resolver.commit();
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
