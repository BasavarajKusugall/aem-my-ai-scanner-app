/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aem.ai.scanner.servlet;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hello World Servlet registered by path
 * 
 * This servlet registers itself to a specific path, namely <tt>/hello-world-servlet</tt>.
 * Note that this is discouraged and binding servlet resources is the preferred
 * way.
 * 
 * @see <a href="https://sling.apache.org/documentation/the-sling-engine/servlets.html">Servlets and Scripts</a>
 */
@Component(
    service = Servlet.class, immediate = true,
    property = {
        Constants.SERVICE_DESCRIPTION + "=Hello World Path Servlet",
        Constants.SERVICE_VENDOR + "=The Apache Software Foundation",
        "sling.servlet.paths=/hello-world-servlet"
    }
)
@SuppressWarnings("serial")
public class ByPathServlet extends SlingSafeMethodsServlet {
    
    private final Logger log = LoggerFactory.getLogger(ByPathServlet.class);

    @Override
    protected void doGet(SlingHttpServletRequest request,
            SlingHttpServletResponse response) throws ServletException,
            IOException {
        
        Writer w = response.getWriter();
        w.write("<!DOCTYPE html PUBLIC \"-//IETF//DTD HTML 2.0//EN\">");
        w.write("<html>");
        w.write("<head>");
        w.write("<title>Hello World Servlet</title>");
        w.write("</head>");
        w.write("<body>");
        w.write("<h1>Hello World!</h1>");
        w.write("</body>");
        w.write("</html>");
        
        log.info("Hello World Servlet");
        
    }

}

