package com.ecole.saas.schoolimport.api;

import com.ecole.saas.schoolimport.ImportType;
import com.ecole.saas.schoolimport.domain.ImportColumnCatalog;
import java.util.List;

public record ImportTemplateResponse(
        ImportType importType,
        String titleFr,
        String descriptionFr,
        List<ColumnDescriptorResponse> columns) {

    public record ColumnDescriptorResponse(
            String key,
            String labelFr,
            boolean required,
            String exampleValue,
            List<String> aliases) {
    }

    public static ImportTemplateResponse from(ImportColumnCatalog.TemplateDescriptor descriptor) {
        return new ImportTemplateResponse(
                descriptor.importType(),
                descriptor.titleFr(),
                descriptor.descriptionFr(),
                descriptor.columns().stream()
                        .map(c -> new ColumnDescriptorResponse(
                                c.key(), c.labelFr(), c.required(), c.exampleValue(), c.aliases()))
                        .toList());
    }
}
