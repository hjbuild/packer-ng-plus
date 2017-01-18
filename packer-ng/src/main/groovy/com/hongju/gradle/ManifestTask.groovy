package com.hongju.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 */
public class ManifestTask extends DefaultTask {

    String manifestPath

    String appName

    @TaskAction
    def manifest() {
        logger("----------modify your manifestFile label name to ${appName}----------")
        def manifestFile = new File(manifestPath)
        def text = manifestFile.text.replace("@string/app_name", appName)
        manifestFile.write(text, "UTF-8")
    }

    public void logger(String s) {
        project.logger.error(s)
    }
}
