pipelineJob("${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO}-${BRANCH}") {
    definition {
        cps {
            script("""
node {
	logstash {
      stage("SCM") {
          checkout changelog: true, poll: true, scm: [
                  \$class: 'GitSCM',
                   branches: [[name: "origin/${BRANCH}"]],
                   doGenerateSubmoduleConfigurations: false,
                   submoduleCfg: [],
                   userRemoteConfigs: [[credentialsId: 'JenkinsUserInGitLab', url: "git@gitlab.mateus:${MODULO}/${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO}.git"]]
          ]
      }
      gitlabCommitStatus(connection:gitLabConnection('gitlab.mateus')) {
          stage('Build') {
              tool name: 'M3', type: 'maven'
              configFileProvider([configFile(fileId: '07710e30-2822-4669-9f45-30349edeb108', variable: 'SETTINGS_XML')]) {
                  sh "mvn -B -DskipTests clean package -s \${SETTINGS_XML}"
              }
          }
          stage('Test') {
              tool name: 'M3', type: 'maven'
              configFileProvider([configFile(fileId: '07710e30-2822-4669-9f45-30349edeb108', variable: 'SETTINGS_XML')]) {
                  sh "mvn test jacoco:report -s \${SETTINGS_XML}"
                  junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
              }
          }
          stage('SonarQube') {
              def pom = readMavenPom file: ''
              configFileProvider([configFile(fileId: 'SonarQubeFile', variable: 'SONAR_TEMPLATE')]) {
                  sh "chmod +x \${SONAR_TEMPLATE}"
                  sh "/bin/bash \${SONAR_TEMPLATE} \${pom.artifactId}_${BRANCH} \${pom.version} sonar-project.properties"
              }
              def scannerHome = tool 'SonarQubeScanner';
              withSonarQubeEnv() {
                  sh "\${scannerHome}/bin/sonar-scanner"
              }
              stage("Quality Gate"){
                  timeout(time: 10, unit: 'MINUTES') { // Just in case something goes wrong, pipeline will be killed after a timeout
                      def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
                      if (qg.status != 'OK') {
                          error "Pipeline aborted due to quality gate failure: \${qg.status}"
                      }
                  }
              }
          }
          stage('Docker Build') {
              def pom = readMavenPom file: ''
              def dockerRegistry = '10.54.0.214:5001'
              configFileProvider([configFile(fileId: 'DockerImageGenerator', variable: 'DOCKER_IG')]) {
                  configFileProvider([configFile(fileId: 'DockerFile', variable: 'DOCKERFILE')]) {
                      sh "chmod +x \${DOCKER_IG}"
                      sh "/bin/bash \${DOCKER_IG} \${dockerRegistry} ithappens/\${pom.artifactId} \${pom.version}-${BRANCH}"
					  sh "/bin/bash \${DOCKER_IG} \${dockerRegistry} ithappens/\${pom.artifactId} \${pom.version}-${BRANCH}"
                  }
              }
          }
          stage('Deploy Kubernetes'){
			      def pom = readMavenPom file: ''
               def dockerRegistry = '10.54.0.214:5001'
               def dataflowDockerURI = "docker:\${dockerRegistry}/ithappens/\${pom.artifactId}:\${pom.version}-${BRANCH}"
               def dockerTool = tool name: 'docker', type: 'org.jenkinsci.plugins.docker.commons.tools.DockerTool'
               try{
                  withEnv(["DOCKER=\${dockerTool}/bin"]) {
                      sh "\${DOCKER}/docker run -d --rm \${dockerRegistry}/ithappens/\${pom.artifactId}:\${pom.version}-${BRANCH} /deployments/run-java.sh --ittask.app.dockeruri=\${dataflowDockerURI} --ittask.dataflow.server.uri=http://dataflow.mateus --ittask.autoregister=true --thin.root=/deployments/m2"
                  }
               }catch(Exception ex){
                  slackSend color: '#A64941', message: "Erro ao deployer projeto \${nameDeployment} [\${pom.version}]: \${ex.message}"
                  error(ex.message)
               }
          }
      }
	}
}
            """)
            sandbox()
        }
    }
    parameters {
        stringParam('branch', "${BRANCH}", 'Branch para realizar o build')
    }
    environmentVariables {
        keepBuildVariables(true)
        keepSystemVariables(true)
        overrideBuildParameters()
    }
    triggers {
        gitlabPush {
            buildOnMergeRequestEvents(false)
            buildOnPushEvents(true)
            enableCiSkip(false)
            includeBranches("${BRANCH}")
            commentTrigger("Jenkins, roda uma build pra gente, por favor.")
        }
    }
}