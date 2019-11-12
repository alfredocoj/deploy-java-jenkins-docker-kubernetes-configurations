pipelineJob("${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO}-pull-request") {
    definition {
        cps {
            script("""
node {
    stage("SCM") {
        checkout changelog: true, poll: true, scm: [
                \$class: 'GitSCM',
                 branches: [[name: "origin/\${env.gitlabSourceBranch}"]],
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
              sh "/bin/bash \${SONAR_TEMPLATE} \${pom.artifactId}_\${env.gitlabSourceBranch} \${pom.version} sonar-project.properties"
          }
          def scannerHome = tool 'SonarQubeScanner';
          withSonarQubeEnv() {
              sh "\${scannerHome}/bin/sonar-scanner"
          }
          stage("Quality Gate"){
              timeout(time: 10, unit: 'MINUTES') {
                  def qg = waitForQualityGate()
                  if (qg.status != 'OK') {
                      error "Pipeline aborted due to quality gate failure: \${qg.status}"
                  }
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
        stringParam('sourceBranch', '', 'branch to build')
        stringParam('targetBranch', '', 'branch to build')
    }
    environmentVariables {
        keepBuildVariables(true)
        keepSystemVariables(true)
        overrideBuildParameters()
        groovy('''
            import hudson.model.*
            def env = Thread.currentThread()?.executable.parent.builds[0].properties.get('envVars')
            def map = [:]
            
            if (env['gitlabSourceBranch'] != null) { 
              map['sourceBranch'] = env['gitlabSourceBranch'] 
            }
            if (env['gitlabTargetBranch'] != null) { 
              map['targetBranch'] = env['gitlabTargetBranch'] 
            }
            
            return map
        ''')
    }
    triggers {
        gitlabPush {
            buildOnMergeRequestEvents(true)
            buildOnPushEvents(false)
            enableCiSkip(false)
            commentTrigger("Jenkins, roda uma build pra gente, por favor.")
        }
    }
}