package com.hongju.gradle

import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.Task
import org.gradle.api.tasks.Exec

// Android Multi Packer Plugin Source
class PackerNgPlugin implements Plugin<Project> {
    static final String TAG = "PackerNg"
    static final String PLUGIN_NAME = "packer"
    static final String P_MARKET = "market"
    static final String P_LABEL = "label"

    Project project
    PackerNgExtension modifierExtension
    List<String> markets;
    List<String> labelNames;
    String labelName;

    @Override
    void apply(Project project) {
        this.project = project
        if (!project.plugins.hasPlugin("com.android.application")) {
            throw new ProjectConfigurationException("the android plugin must be applied", null)
        }

        if (project.hasProperty(P_LABEL)) {
            println(":${project.name} ----${project.property(P_LABEL).toString()}")
            labelName = project.property(P_LABEL).toString()
        }

        applyExtension()
        parseMarkets()
        initNameTask()
        applyPluginTasks()
    }

    void applyExtension() {
        // setup plugin and extension
        project.configurations.create(PLUGIN_NAME).extendsFrom(project.configurations.compile)
        this.modifierExtension = project.extensions.create(PLUGIN_NAME, PackerNgExtension, project)
    }

    void applyPluginTasks() {
        project.afterEvaluate {
            checkCleanTask()
            debug(":${project.name} flavors: ${project.android.productFlavors.collect { it.name }}")
            //applySigningConfigs()
            project.android.applicationVariants.all { BaseVariant variant ->

                if (labelName != null) {
                    def variantOutput = variant.outputs.first()
                    def variantName = variant.name.capitalize()

                    def manifestPath = variantOutput.processManifest.manifestOutputFile
                    ManifestTask manifestTask = project.tasks.create("myProcess${variantName}Manifest", ManifestTask)
                    manifestTask.manifestPath = manifestPath
                    manifestTask.appName = labelName
                    manifestTask.mustRunAfter variantOutput.processManifest
                    variantOutput.processResources.dependsOn manifestTask
                }

                checkPackerNgTask(variant)
            }
        }
    }

/**
 *  parse markets file
 * @param project Project
 * @return found markets file
 */
    boolean parseMarkets() {
        markets = new ArrayList<String>();

//        if (!project.hasProperty(P_MARKET)) {
//            debug("parseMarkets() market property not found, ignore")
//            return false
//        }

        // check markets file exists
//        def marketsFilePath = project.property(P_MARKET).toString()
//        if (!marketsFilePath) {
//            println(":${project.name} markets property not found, using default")
//            // if not set, use default ./markets.txt
//            marketsFilePath = "markets.txt"
//        }


        def marketsFilePath = "${project.rootDir}\\market.txt"

        File file = project.rootProject.file(marketsFilePath)
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            throw new IllegalArgumentException("Invalid market file: ${file.absolutePath}")
        }
        println(":${project.name} market: ${file.absolutePath}")

        if (project.hasProperty(P_LABEL)) {
            println(":${project.name} AndroidMenifest label: ${project.property(P_LABEL).toString()}")
            markets = readMarkets(file, project.property(P_LABEL).toString())
        } else {
            markets = readMarkets(file, null)
        }
        debug(":${project.name} found markets:$markets")
        return true
    }

    List<String> readMarkets(File file, String label) {
        // add all markets
        List<String> allMarkets = []
        file.eachLine { line, number ->
            String[] parts = line.split('#')
            if (parts && parts[1]) {
                def labelName = parts[1].trim()
                def market = parts[0].trim()
                if (market) {
                    if (label == null) {
                        allMarkets.add(market)
                    } else if (labelName == label) {
                        allMarkets.add(market)
                    }
                }
            } else {
                debug(":${project.name} skip invalid market line ${number}:'${line}'")
            }
        }
        return allMarkets
    }

/**
 *  add archiveApk tasks
 * @param variant current Variant
 */
    void checkPackerNgTask(BaseVariant variant) {
        debug("checkPackerNgTask() for ${variant.name}")
        def File inputFile = variant.outputs[0].outputFile
        def File tempDir = modifierExtension.tempOutput
        def File outputDir = modifierExtension.archiveOutput
        debug("checkPackerNgTask() input: ${inputFile}")
        debug("checkPackerNgTask() temp: ${tempDir}")
        debug("checkPackerNgTask() output: ${outputDir}")
        def archiveTask = project.task("apk${variant.name.capitalize()}",
                type: ArchiveAllApkTask) {
            theVariant = variant
            theExtension = modifierExtension
            theMarkets = markets
            dependsOn variant.assemble
        }

        debug("checkPackerNgTask() new variant task:${archiveTask.name}")

        def buildTypeName = variant.buildType.name
        if (variant.name != buildTypeName) {
            def taskName = "apk${buildTypeName.capitalize()}"
            def task = project.tasks.findByName(taskName)
            if (task == null) {
                debug("checkPackerNgTask() new build type task:${taskName}")
                task = project.task(taskName, dependsOn: archiveTask)
            }
        }
    }

    /**
     *  add cleanArchives task if not added
     * @return task
     */
    void checkCleanTask() {
        def output = modifierExtension.archiveOutput
        debug("checkCleanTask() create clean archived apks task, path:${output}")
        def task = project.task("cleanApks",
                type: CleanArchivesTask) {
            target = output
        }

        project.getTasksByName("clean", true)?.each {
            it.dependsOn task
        }
    }

    void initNameTask() {
        labelNames = new ArrayList<>()

        def marketsFilePath = "${project.rootDir}\\market.txt"
        File file = project.rootProject.file(marketsFilePath)
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            throw new IllegalArgumentException("Invalid market file: ${file.absolutePath}")
        }
        labelNames = readNames(file);

        List<Task> taskList = new ArrayList<>();

        for (String s : labelNames) {
            taskList.add(project.task("apk${s}",
                    type: Exec, group: "packer") {
                workingDir "${project.rootDir}"
                commandLine 'cmd', "/c", "gradlew -Plabel=${s}  apkRelease"
            })
        }

        def tt = project.task("apkPacker", group: "packer") {
        }
        tt.dependsOn(taskList.toArray())

    }

    List<String> readNames(File file) {
        // add all markets
        List<String> allNames = []
        file.eachLine { line, number ->
            String[] parts = line.split('#')
            if (parts && parts[1]) {
                def labelName = parts[1].trim()
                if (labelName && !allNames.contains(labelName)) {
                    allNames.add(labelName)
                }
            } else {
                debug(":${project.name} skip invalid label name line ${number}:'${line}'")
            }
        }
        return allNames
    }
/**
 *  print debug messages
 * @param msg msg
 * @param vars vars
 */
    void debug(String msg) {
        project.logger.info(msg)
    }

}
