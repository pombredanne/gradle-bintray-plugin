package com.jfrog.bintray.gradle

import org.gradle.BuildAdapter
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.invocation.Gradle
import org.gradle.api.publish.Publication
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Upload

class BintrayPlugin implements Plugin<Project> {

    private Project project

    public void apply(Project project) {
        this.project = project;
        def extension = project.extensions.create("bintray", BintrayExtension, project)

        extension.with {
            apiUrl = BintrayUploadTask.API_URL_DEFAULT
        }

        //Create and configure the task
        BintrayUploadTask bintrayUpload = project.task(type: BintrayUploadTask, BintrayUploadTask.NAME)
        //Depend on tasks in sub-projects
        project.subprojects.each {
            Task subTask = it.tasks.findByName(BintrayUploadTask.NAME)
            if (subTask) {
                bintrayUpload.dependsOn(subTask)
            }
        }

        def projectAdapter = [
                bintrayUpload: bintrayUpload,
                projectsEvaluated: { Gradle gradle ->
                    bintrayUpload.with {
                        apiUrl = extension.apiUrl
                        user = extension.user
                        apiKey = extension.key
                        configurations = extension.configurations
                        publications = extension.publications
                        publish = extension.publish
                        dryRun = extension.dryRun
                        userOrg = extension.pkg.userOrg ?: extension.user
                        repoName = extension.pkg.repo
                        packageName = extension.pkg.name
                        packageDesc = extension.pkg.desc
                        packageWebsiteUrl = extension.pkg.websiteUrl
                        packageIssueTrackerUrl = extension.pkg.issueTrackerUrl
                        packageVcsUrl = extension.pkg.vcsUrl
                        packageLicenses = extension.pkg.licenses
                        packageLabels = extension.pkg.labels
                        packagePublicDownloadNumbers = extension.pkg.publicDownloadNumbers
                        versionName = extension.pkg.version.name ?: project.version
                        versionDesc = extension.pkg.version.desc
                        versionVcsTag = extension.pkg.version.vcsTag ?: project.version
                    }
                    if (extension.configurations?.length) {
                        Upload installTask = project.tasks.withType(Upload)?.findByName('install')
                        if (installTask) {
                            bintrayUpload.dependsOn(installTask)
                        } else {
                            project.logger.warn "Configuration(s) specified but the install task does not exist in project {}.",
                                    project.path
                        }
                    }
                    if (extension.publications?.length) {
                        def publicationExt = project.extensions.findByType(PublishingExtension)
                        if (!publicationExt) {
                            project.logger.warn "Publications(s) specified but no publications exist in project {}.",
                                    project.path
                        } else {
                            extension.publications.each {
                                Publication publication = publicationExt?.publications?.findByName(it)
                                if (!publication) {
                                    project.logger.warn 'Publication {} not found in project {}.', it, project.path
                                } else if (publication instanceof MavenPublication) {
                                    def taskName =
                                            "publish${it[0].toUpperCase()}${it.substring(1)}PublicationToMavenLocal"
                                    Task publishToLocalTask = project.tasks.findByName(taskName)
                                    bintrayUpload.dependsOn(publishToLocalTask)
                                    /*bintrayUpload.dependsOn(publication.publishableFiles)*/
                                } else {
                                    project.logger.warn "{} can only use maven publications - skipping {}.",
                                            bintrayUpload.path, publication.name
                                }
                            }
                        }
                    }
                }
        ] as BuildAdapter
        project.gradle.addBuildListener(projectAdapter)
    }
}