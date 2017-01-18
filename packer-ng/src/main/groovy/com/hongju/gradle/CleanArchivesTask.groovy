package com.hongju.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class CleanArchivesTask extends DefaultTask {

    @Input
    File target

    CleanArchivesTask() {
        setDescription('clean all apk archives in output dir')
    }

    @TaskAction
    void showMessage() {
        logger.info("${name}: ${description}")
    }

    @TaskAction
    void deleteAll() {
        logger.info("${name}: delete all files in ${target.absolutePath}")
        target.deleteDir()
    }

}
