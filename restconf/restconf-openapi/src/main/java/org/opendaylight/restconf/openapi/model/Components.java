/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.Map;

@JsonInclude(Include.NON_NULL)
public class Components {
    private Map<String, Schema> schemas;
    private SecuritySchemes securitySchemes;

    public Components(Map<String, Schema> schemas, SecuritySchemes securitySchemes) {
        this.schemas = schemas;
        this.securitySchemes = securitySchemes;
    }

    public Map<String, Schema> getSchemas() {
        return schemas;
    }

    public void setSchemas(Map<String, Schema> schemas) {
        this.schemas = schemas;
    }

    public SecuritySchemes getSecuritySchemes() {
        return securitySchemes;
    }

    public void setSecuritySchemes(SecuritySchemes securitySchemes) {
        this.securitySchemes = securitySchemes;
    }
}