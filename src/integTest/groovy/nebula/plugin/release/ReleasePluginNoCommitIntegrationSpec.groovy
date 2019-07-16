/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nebula.plugin.release

import nebula.test.IntegrationSpec
import org.ajoberstar.grgit.Grgit
import org.gradle.api.plugins.JavaPlugin

class ReleasePluginNoCommitIntegrationSpec extends IntegrationSpec {
    Grgit repo

    def 'repo with no commits does not throw errors'() {
        given:
        repo = Grgit.init(dir: projectDir)
        buildFile << """\
            ext.dryRun = true
            group = 'test'
            ${applyPlugin(ReleasePlugin)}
            ${applyPlugin(JavaPlugin)}

            task showVersion {
                doLast {
                    logger.lifecycle "Version in task: \${version.toString()}"
                }
            }
            """.stripIndent()

        when:
        def results = runTasks('showVersion')

        then:
        results.standardOutput.contains 'Version in task: 0.1.0-dev.0.uncommitted'
    }

    def 'repo with no commits does not throw errors - replace dev with immutable snapshot'() {
        given:
        repo = Grgit.init(dir: projectDir)
        new File(buildFile.parentFile, "gradle.properties").text = """
nebula.release.features.replaceDevWithImmutableSnapshot=true
"""
        buildFile << """\
            ext.dryRun = true
            group = 'test'
            ${applyPlugin(ReleasePlugin)}
            ${applyPlugin(JavaPlugin)}

            task showVersion {
                doLast {
                    logger.lifecycle "Version in task: \${version.toString()}"
                }
            }
            """.stripIndent()

        when:
        def results = runTasks('showVersion')

        then:
        results.standardOutput.contains 'Version in task: 0.1.0-snapshot.'
        results.standardOutput.contains '.uncommitted'
    }
}
