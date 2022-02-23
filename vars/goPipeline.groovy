import com.privatesquare.pipeline.go.Go
import com.privatesquare.pipeline.utils.Git

def call() {
    node() {
        def go = new Go(this)
        def git = new Git(this)
        String nexusRepositoryId
        String scmCredentialsId, nexusCredentialsId
        String artifactId, groupId, version, scmUrl, scmType, zipFile
        def jenkinsProperties
        def goTool
        def gometalinter

        final String propsFileName = 'jenkins.yml'

        step([$class: 'WsCleanup'])

        stage('checkout'){
            checkout scm
            scmType = getScmType(scm)
            scmUrl = scm.getUserRemoteConfigs()[0].getUrl()
            println "[INFO] SCM type : ${scmType}"
            println "[INFO] SCM Url : ${scmUrl}"
        }

        stage('prepare') {

            timeout(time: 10, unit: 'SECONDS') {
                jenkinsProperties = readYaml file: propsFileName
            }

            scmCredentialsId = "bitbucket"
            nexusCredentialsId = "builder"
            nexusRepositoryId = "Go-releases"

            // generate version
            assert jenkinsProperties.version : 'Please add paramter "version" to your jenkins.yml file'
            String currentVersion = String.valueOf(jenkinsProperties.version)
            artifactId = jenkinsProperties.artifactId
            groupId = jenkinsProperties.groupId
            version = git.createNextTagVersion(currentVersion)
        }

        stage('build') {
            goTool = tool name: 'go-1.9.2', type: 'go'
            String goPath = env.WORKSPACE

            println "[INFO] GOPATH : $goPath"

            String outputFolder = "${env.WORKSPACE}/bin"
            sh "mkdir -p ${outputFolder}"

            def outputs = [[
                                   OS: 'darwin',
                                   architecture: 'amd64',
                                   postfix: '-darwin'
                           ], [
                                   OS: 'darwin',
                                   architecture: '386',
                                   postfix: '-darwin-x86'
                           ], [
                                   OS: 'windows',
                                   architecture: 'amd64',
                                   postfix: '.exe'
                           ], [
                                   OS: 'windows',
                                   architecture: '386',
                                   postfix: '-32.exe'
                           ] , [
                                   OS: 'linux',
                                   architecture: 'amd64',
                                   postfix: '-linux'
                           ], [
                                   OS: 'linux',
                                   architecture: '386',
                                   postfix: '-linux-x86'
                           ]]


            for(output in outputs) {
                String file = "${outputFolder}/${jenkinsProperties.artifactId}${output.postfix}"
                go.build(goTool, goPath, output.OS, output.architecture, file, jenkinsProperties.groupId, jenkinsProperties.artifactId)
            }
            zipFile = "${jenkinsProperties.artifactId}.zip"
            if(fileExists(zipFile)) {
                sh "rm $zipFile"
            }

            zip dir: outputFolder, zipFile: zipFile
        }

        stage('gometalinter') {
            gometalinter = tool name: 'gometalinter', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'
            go.gometalinter(goTool, gometalinter)
        }

        stage('tag') {
            git.createNextTag(String.valueOf(jenkinsProperties.version), scmCredentialsId)
        }

        stage('sonarqube') {
            def sonarScanner = tool name: 'sonar-scanner-3.0.3', type: 'hudson.plugins.sonar.SonarRunnerInstallation'
            withEnv(["PATH+SONAR=${sonarScanner}/bin"]) {
                withSonarQubeEnv {
                    sh "sonar-scanner \
                        -Dsonar.sources=./ \
                        -Dsonar.golint.reportPath=report.xml \
                        -Dsonar.projectName=${artifactId} \
                        -Dsonar.projectKey=${groupId}.${artifactId} \
                        -Dsonar.projectVersion=${version} \
                        -Dsonar.links.scm=${scmUrl} \
                        -Dsonar.links.ci=${BUILD_URL}"
                }
            }
        }

        stage('publish') {
            String packaging = "zip"

            uploadToNexus(
                    nexusRepositoryId,
                    groupId,
                    artifactId,
                    version,
                    packaging,
                    zipFile)
        }

        step([$class: 'WsCleanup'])
    }
}