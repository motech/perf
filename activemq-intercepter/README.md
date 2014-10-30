activemq-interceptor
===================

To use you need to add the following to your activemq.xml in the broker section:

        <plugins>
            <bean id="subjectStatsPlugin"
                  class="org.motechproject.activemq.interceptor.SubjectStatsPlugin"
                  xmlns="http://www.springframework.org/schema/beans">
            </bean> 
        </plugins>

You also need to place the compiled jar in a directory on the activemq classpath.  For my local build installed via homebrew on mac that is /usr/local/Cellar/activemq/5.10.0/libexec/lib/optional.  You also need to place a copy of motech-platform-event.jar in that directoy.  Note if the motech jar has a log4j.xml file it will prevent activemq from logging.  You may want to remove it.
