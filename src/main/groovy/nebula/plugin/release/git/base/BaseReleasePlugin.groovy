/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.release.git.base

import groovy.transform.CompileDynamic
import org.ajoberstar.grgit.Grgit
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Plugin providing the base structure of gradle-git's flavor of release
 * behavior. The plugin can be applied using the {@code org.ajoberstar.release-base} id.
 *
 * <p>
 * The plugin adds the {@link ReleasePluginExtension} and a {@code release} task.
 * </p>
 *
 * @see nebula.plugin.release.git.opinion.Strategies
 * @see nebula.plugin.release.git.opinion.OpinionReleasePlugin
 * @see <a href="https://github.com/ajoberstar/gradle-git/wiki/org.ajoberstar.release-base">Wiki Doc</a>
 */
@CompileDynamic
class BaseReleasePlugin implements Plugin<Project> {
    private static final Logger logger = LoggerFactory.getLogger(BaseReleasePlugin)
    private static final String PREPARE_TASK_NAME = 'prepare'
    private static final String RELEASE_TASK_NAME = 'release'

    void apply(Project project) {
        def extension = project.extensions.create('release', ReleasePluginExtension, project)
        addPrepareTask(project, extension)
        addReleaseTask(project, extension)
        project.plugins.withId('org.ajoberstar.grgit') {
            extension.grgit = project.grgit
        }
    }

    private void addPrepareTask(Project project, ReleasePluginExtension extension) {
        project.tasks.create(PREPARE_TASK_NAME) {
            description = 'Verifies that the project could be released.'
            doLast {
                ext.grgit = extension.grgit
                prepare(extension)
            }
        }

        project.tasks.all { task ->
            if (name != PREPARE_TASK_NAME) {
                task.shouldRunAfter PREPARE_TASK_NAME
            }
        }
    }


    private void addReleaseTask(Project project, ReleasePluginExtension extension) {
        project.tasks.create(RELEASE_TASK_NAME) {
            description = 'Releases this project.'
            dependsOn PREPARE_TASK_NAME
            doLast {
                release(project, ext, extension)
            }
        }
    }

    protected static void prepare(ReleasePluginExtension extension) {
        logger.info('Fetching changes from remote: {}', extension.remote)
        Grgit grgit = extension.grgit
        grgit.fetch(remote: extension.remote)

        // if branch is tracking another, make sure it's not behind
        def currentBranch = grgit.branch.current()
        if (currentBranch.trackingBranch && grgit.branch.status(name: currentBranch.fullName).behindCount > 0) {
            throw new GradleException('Current branch is behind the tracked branch. Cannot release.')
        }
    }

    protected static void release(Project project, ext, ReleasePluginExtension extension) {
        // force version inference if it hasn't happened already
        project.version.toString()

        Grgit grgit = extension.grgit
        ext.grgit = grgit
        ext.toPush = []

        // if not on detached HEAD, push branch
        if (grgit.branch.current.fullName != 'HEAD') {
            ext.toPush << grgit.branch.current.fullName
        }

        if(project.version instanceof String) {
            throw new GradleException("version should not be set in build file when using nebula-release plugin. Instead use `-Prelease.version` parameter")
        }

        ext.tagName = extension.tagStrategy.maybeCreateTag(grgit, project.version.inferredVersion)
        if (ext.tagName) {
            ext.toPush << ext.tagName
        }

        if (ext.toPush) {
            logger.warn('Pushing changes in {} to {}', ext.toPush, extension.remote)
            grgit.push(remote: extension.remote, refsOrSpecs: ext.toPush)
        } else {
            logger.warn('Nothing to push.')
        }
    }
}
