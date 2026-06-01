FROM ${alfresco.repo.docker.image}:${alfresco.repo.docker.version}

${alfresco.repo.docker.build.preRun}

RUN \
  yum update --security -y && \
  yum install -y langpacks-en java-${alfresco.repo.docker.java.version}-openjdk-devel && \
  yum clean all && rm -rf /var/cache/yum

#COPY glowroot.jar glowroot.properties /usr/local/tomcat/glowroot/
#COPY lib/*.jar /usr/local/tomcat/glowroot/lib/
COPY admin.json config.json /usr/local/tomcat/glowroot/conf/
  
COPY support-tools-repo-*.amp ${alfresco.repo.docker.amp.path}/
COPY de.acosix.alfresco.utility.repo-*.amp ${alfresco.repo.docker.amp.path}/
COPY aldica-repo-ignite-*.amp ${alfresco.repo.docker.amp.path}/

RUN java -jar ${alfresco.repo.docker.mmt.path}/alfresco-mmt*.jar install \
              ${alfresco.repo.docker.amp.path}/support-tools-repo-*.amp \
              ${alfresco.repo.docker.tomcat.path}/webapps/alfresco -nobackup

RUN java -jar ${alfresco.repo.docker.mmt.path}/alfresco-mmt*.jar install \
              ${alfresco.repo.docker.amp.path}/de.acosix.alfresco.utility.repo-*.amp \
              ${alfresco.repo.docker.tomcat.path}/webapps/alfresco -nobackup

# include first digit of version to differentiate from mem-bm AMP
RUN java -jar ${alfresco.repo.docker.mmt.path}/alfresco-mmt*.jar install \
              ${alfresco.repo.docker.amp.path}/aldica-repo-ignite-1*.amp \
              ${alfresco.repo.docker.tomcat.path}/webapps/alfresco -nobackup

RUN java -jar ${alfresco.repo.docker.mmt.path}/alfresco-mmt*.jar install \
              ${alfresco.repo.docker.amp.path}/aldica-repo-ignite-mem-bm-*.amp \
              ${alfresco.repo.docker.tomcat.path}/webapps/alfresco -nobackup

COPY alfresco.xml ${alfresco.repo.docker.tomcat.path}/conf/Catalina/localhost/
COPY alfresco-global.addition.properties ${alfresco.repo.docker.tomcat.path}/shared/classes/alfresco/
COPY dev-log4j.properties dev-log4j2.properties ${alfresco.repo.docker.tomcat.path}/shared/classes/alfresco/extension/

# merge additions to alfresco-global.properties
RUN echo "" >> ${alfresco.repo.docker.tomcat.path}/shared/classes/alfresco-global.properties \
    && cat ${alfresco.repo.docker.tomcat.path}/shared/classes/alfresco/alfresco-global.addition.properties >> ${alfresco.repo.docker.tomcat.path}/shared/classes/alfresco-global.properties \
    && sed -i 's/<secure>true<\/secure>/<secure>false<\/secure>/' ${alfresco.repo.docker.tomcat.path}/conf/web.xml

# Enable use of Web Session replication by modifying web.xml
RUN awk 'done != 1 && /<filter-mapping/ { \
    print "<filter><filter-name>${project.longNameNoWs}WebSessionCacheFilter</filter-name><filter-class>org.springframework.extensions.webscripts.servlet.BeanProxyFilter</filter-class><init-param><param-name>beanName</param-name><param-value>${baseModuleId}-webSessionCacheFilter</param-value></init-param></filter>"; \
    print "<filter-mapping><filter-name>${project.longNameNoWs}WebSessionCacheFilter</filter-name><url-pattern>/*</url-pattern></filter-mapping>"; \
    done = 1 \
    } 1' ${alfresco.repo.docker.tomcat.path}/webapps/alfresco/WEB-INF/web.xml > ${alfresco.repo.docker.tomcat.path}/webapps/alfresco/WEB-INF/web.xml.awked \
    && rm ${alfresco.repo.docker.tomcat.path}/webapps/alfresco/WEB-INF/web.xml \
    && mv ${alfresco.repo.docker.tomcat.path}/webapps/alfresco/WEB-INF/web.xml.awked ${alfresco.repo.docker.tomcat.path}/webapps/alfresco/WEB-INF/web.xml

RUN chown -R ${alfresco.repo.docker.chown.mask} \
             ${alfresco.repo.docker.tomcat.path}/webapps/alfresco \
             ${alfresco.repo.docker.tomcat.path}/shared/classes/*

RUN chown -R ${alfresco.repo.docker.chown.mask} \
             ${alfresco.repo.docker.tomcat.path}/glowroot
             
${alfresco.repo.docker.build.postRun}