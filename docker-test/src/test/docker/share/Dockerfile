FROM ${alfresco.share.docker.image}:${alfresco.share.docker.version}

COPY support-tools-share-*.amp ${alfresco.share.docker.amp.path}/
COPY de.acosix.alfresco.utility.share-*.amp ${alfresco.share.docker.amp.path}/
COPY aldica-share-ignite-*.amp ${alfresco.share.docker.amp.path}/

RUN java -jar ${alfresco.share.docker.mmt.path}/alfresco-mmt*.jar install \
              ${alfresco.share.docker.amp.path}/support-tools-share-*.amp \
              ${alfresco.share.docker.tomcat.path}/webapps/share -nobackup

RUN java -jar ${alfresco.share.docker.mmt.path}/alfresco-mmt*.jar install \
              ${alfresco.share.docker.amp.path}/de.acosix.alfresco.utility.share-*.amp \
              ${alfresco.share.docker.tomcat.path}/webapps/share -nobackup

RUN java -jar ${alfresco.share.docker.mmt.path}/alfresco-mmt*.jar install \
              ${alfresco.share.docker.amp.path}/aldica-share-ignite-*.amp \
              ${alfresco.share.docker.tomcat.path}/webapps/share -nobackup

RUN sed -i 's/<secure>true<\/secure>/<secure>false<\/secure>/' ${alfresco.share.docker.tomcat.path}/conf/web.xml

COPY share.xml ${alfresco.share.docker.tomcat.path}/conf/Catalina/localhost/share.xml
COPY share-config-custom.xml ${alfresco.share.docker.tomcat.path}/shared/classes/alfresco/web-extension/share-config-custom.xml
COPY dev-log4j.properties dev-log4j2.properties ${alfresco.share.docker.tomcat.path}/shared/classes/alfresco/web-extension/

# Enable use of Web Session replication by modifying web.xml
RUN awk 'done != 1 && /<filter-mapping/ { \
    print "<filter><filter-name>${project.longNameNoWs}WebSessionCacheFilter</filter-name><filter-class>org.springframework.extensions.webscripts.servlet.BeanProxyFilter</filter-class><init-param><param-name>beanName</param-name><param-value>${moduleId}-webSessionCacheFilter</param-value></init-param></filter>"; \
    print "<filter-mapping><filter-name>${project.longNameNoWs}WebSessionCacheFilter</filter-name><url-pattern>/*</url-pattern></filter-mapping>"; \
    done = 1 \
    } 1' ${alfresco.share.docker.tomcat.path}/webapps/share/WEB-INF/web.xml > ${alfresco.share.docker.tomcat.path}/webapps/share/WEB-INF/web.xml.awked \
    && rm ${alfresco.share.docker.tomcat.path}/webapps/share/WEB-INF/web.xml \
    && mv ${alfresco.share.docker.tomcat.path}/webapps/share/WEB-INF/web.xml.awked ${alfresco.share.docker.tomcat.path}/webapps/share/WEB-INF/web.xml