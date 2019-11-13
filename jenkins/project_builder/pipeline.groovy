node {
   def projectFullName = "${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO}"
   stage('Limpando Workspace') {
       cleanWs()
   }
   stage('[Validação] Nome do projeto') {
       script {
           def projectName = "${NOME_DO_PROJETO}"
           def pattern = ~/^[a-z-]+$/
           def matcher = (projectName =~ pattern)
           if (!matcher.find()) {
               error("Nome ${NOME_DO_PROJETO} é inválido para projetos (preste atenção na descrição do parâmetro!!)")
           }
       }
   }
   stage('[Validação] Disponibilidade do Nome') {
       withCredentials([string(credentialsId: 'GitlabAccessToken', variable: 'GITLAB_TOKEN')]) {
           httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${MODULO}%2F${projectFullName}", validResponseCodes: '404'
       }
   }
   stage('[Build] Criando projeto') {
       tool name: 'M3', type: 'maven'
       def mvnCommand = ""
       script {
           def tipo = "${TIPO_DO_PROJETO}"
           if (tipo == "rest")
               mvnCommand = "archetype:generate -DarchetypeGroupId=br.com.ithappens -DarchetypeArtifactId=archetype -DarchetypeVersion=1.3 -DgroupId=br.com.ithappens.${MODULO}.${TIPO_DO_PROJETO} -DartifactId=${projectFullName} -Dversion=0.1.0"
           else if (tipo == "task")
               mvnCommand = "archetype:generate -DarchetypeGroupId=br.com.ithappens -DarchetypeArtifactId=archetype-task -DarchetypeVersion=1.2 -DgroupId=br.com.ithappens.${MODULO}.${TIPO_DO_PROJETO} -DartifactId=${projectFullName} -Dversion=0.1.0"
           else if (tipo == "maestro")
               mvnCommand = "archetype:generate -DarchetypeGroupId=br.com.ithappens -DarchetypeArtifactId=maestro-archetype -DarchetypeVersion=1.3 -DgroupId=br.com.ithappens.${MODULO}.${TIPO_DO_PROJETO} -DartifactId=${projectFullName} -Dversion=0.1.0"
       }
       configFileProvider([configFile(fileId: '07710e30-2822-4669-9f45-30349edeb108', variable: 'SETTINGS_XML')]) {
        sh "mvn -B ${mvnCommand} -s ${SETTINGS_XML}"
       }
   }
   stage('[VCS] Criando projeto') {
       configFileProvider([configFile(fileId: 'gitea-client', variable: 'GITEA_CLIENT_GROOVY')]) {
           def jsonHelper = load("${GITEA_CLIENT_GROOVY}")
           withCredentials([string(credentialsId: 'GitlabAccessToken', variable: 'GITLAB_TOKEN')]) {
               def idProjeto = 0
               try {
                   // Recupera o ID do Grupo
                   def response = httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], ignoreSslErrors: true, url: "https://gitlab.mateus/api/v4/groups?search=${MODULO}", validResponseCodes: '200'
                   def idGrupo = jsonHelper.extrairIdGrupo(response.content)
                   script {
                       if (idGrupo == 0) {
                           error("Grupo ${MODULO} não encontrado!!")
                       }
                   }
                   // Cria um projeto e pega o ID dele
                   def responseNewProject = httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'POST', ignoreSslErrors: true, url: "https://gitlab.mateus/api/v4/projects?name=${projectFullName}&namespace_id=${idGrupo}&only_allow_merge_if_pipeline_succeeds=true", validResponseCodes: '200:299'
                   idProjeto = jsonHelper.extrairIdProjeto(responseNewProject.content)
                   
                   withCredentials([sshUserPrivateKey(credentialsId: 'JenkinsUserInGitLab', keyFileVariable: 'SSH_FILE', passphraseVariable: '', usernameVariable: '')]) {
                       // Faz o push do projeto criado no GitLab
                       sh label: '', script: '''cd "${projectFullName}"
                        git init
                        git config core.sshCommand "ssh -i ${SSH_FILE} -F /dev/null"
                        git config user.email 'jenkins@armateus.com.br'
                        git config user.name 'Jenkins'
                        git add .gitignore pom.xml README.md src/
                        git commit -m 'Primeiro Commit'
                        git remote add origin "git@gitlab.mateus:${MODULO}/${projectFullName}.git"
                        git push -u origin master
                        cd ..'''
                    }

                    stage('Criando Arquivo de Configuracoes de Deploy') {
                        sh 'mkdir configs-templates-deployment'

                        dir('configs-templates-deployment') {
                            checkout changelog: true, poll: true, scm: [
                                    $class: 'GitSCM',
                                    branches: [[name: "origin/master"]],
                                    doGenerateSubmoduleConfigurations: false,
                                    submoduleCfg: [],
                                    userRemoteConfigs: [[credentialsId: 'JenkinsUserInGitLab', url: "git@gitlab.mateus:infa/configs-templates-deployment.git"]]
                            ]

                            sh label: '', script: """
                            cat "configs-templates-deployment/conf/java/${TIPO_DO_PROJETO}/master/template-master.groovy" | envsubst > "configs-templates-deployment/conf/java/${TIPO_DO_PROJETO}/master/${MODULO}/${projectFullName}.groovy"
                            cat "configs-templates-deployment/conf/java/${TIPO_DO_PROJETO}/homologacao/template-homologacao.groovy" | envsubst > "configs-templates-deployment/conf/java/${TIPO_DO_PROJETO}/homologacao/${MODULO}/${projectFullName}.groovy"
                            cd configs-templates-deployment
                            git config core.sshCommand "ssh -i ${SSH_FILE} -F /dev/null"
                            git config user.email "jenkins@armateus.com.br"
                            git config user.name "Jenkins"
                            git add .
                            git commit -m "new project: ${projectFullName}"
                            git push -u origin master
                            """
                        }
                    }
                    
                    stage('[Jenkins] Criando jobs') {
                        configFileProvider([configFile(fileId: '29a4632a-5508-411f-8e7c-a5d09d728ed4', targetLocation: 'normal_build.groovy'),
                        					configFile(fileId: 'ca280e60-2520-4cdd-8b59-d482deb7ea80', targetLocation: 'pr_build.groovy')]) {
                            jobDsl targets: 'normal_build.groovy',
                                additionalParameters: [MODULO: "${MODULO}", TIPO_DO_PROJETO: "${TIPO_DO_PROJETO}", NOME_DO_PROJETO: "${NOME_DO_PROJETO}", BRANCH: "master"]
                            jobDsl targets: 'normal_build.groovy',
                                additionalParameters: [MODULO: "${MODULO}", TIPO_DO_PROJETO: "${TIPO_DO_PROJETO}", NOME_DO_PROJETO: "${NOME_DO_PROJETO}", BRANCH: "staging"]          
                            jobDsl targets: 'pr_build.groovy',
                                additionalParameters: [MODULO: "${MODULO}", TIPO_DO_PROJETO: "${TIPO_DO_PROJETO}", NOME_DO_PROJETO: "${NOME_DO_PROJETO}"]
                        }
                    }
                    
                    stage('[VCS] Criando hooks') {
                       // Cria um branch chamado staging (homologacao), que referencia o master
                       httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'POST', ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${idProjeto}/repository/branches?branch=staging&ref=master", validResponseCodes: '200:299'
                       // Bloqueia o branch staging
                       httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'POST', ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${idProjeto}/protected_branches?name=staging", validResponseCodes: '200:299'

                       // Adicionar Badges
                       httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'POST', requestBody: "link_url=http://192.168.6.95:32595/view/contabil/job/${projectFullName}-master/&image_url=http://192.168.6.95:32595/buildStatus/icon?job=${projectFullName}-master&position=0", ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${idProjeto}/badges", validResponseCodes: '200:299'
                       httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'POST', requestBody: "link_url=https://img.shields.io&image_url=https://img.shields.io/badge/maven-0.1.0-blue&position=0", ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${idProjeto}/badges", validResponseCodes: '200:299'
                       httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'POST', requestBody: "link_url=http://192.168.6.182:9000/dashboard/index/${projectFullName}_master&image_url=http://192.168.6.182:9000/api/badges/gate?key=${projectFullName}_master&position=0", ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${idProjeto}/badges", validResponseCodes: '200:299'
                       httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'POST', requestBody: "link_url=http://192.168.6.182:9000/dashboard/index/${projectFullName}_master&image_url=http://192.168.6.182:9000/api/badges/measure?key=${projectFullName}_master%26metric=coverage&position=0", ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${idProjeto}/badges", validResponseCodes: '200:299'

                       // Cria um hook na branch master
                       httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'POST', ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${idProjeto}/hooks?push_events=true&push_events_branch_filter=master&note_events=true&url=http%3A%2F%2Fgitlab%3A11f9af836ab26abffd89bb0812ab5a860c%40192.168.6.95%3A32595%2Fproject%2F${projectFullName}-master", validResponseCodes: '200:299'
                       // Cria um hook na branch staging
                       httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'POST', ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${idProjeto}/hooks?push_events=true&push_events_branch_filter=staging&note_events=true&url=http%3A%2F%2Fgitlab%3A11f9af836ab26abffd89bb0812ab5a860c%40192.168.6.95%3A32595%2Fproject%2F${projectFullName}-staging", validResponseCodes: '200:299'
                       // Cria um hook para avaliar pull requests
                       httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'POST', ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${idProjeto}/hooks?push_events=false&merge_requests_events=true&note_events=true&url=http%3A%2F%2Fgitlab%3A11f9af836ab26abffd89bb0812ab5a860c%40192.168.6.95%3A32595%2Fproject%2F${projectFullName}-pull-request", validResponseCodes: '200:299'
                    }
                } catch (Exception ex) {
                   if(idProjeto > 0) {
                       // Deu erro... deleto o repositório
                       httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'DELETE', ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${idProjeto}", validResponseCodes: '200:299'
                   }
                   slackSend color: '#A64941', message: "Erro ao construir projeto ${projectFullName}: ${ex.message}"
                   error(ex.message)
               }
           }
       }
   }
   
   stage('Arquivando projeto') {
       zip archive: true, dir: "${projectFullName}", glob: '', zipFile: "${projectFullName}.zip"
       slackSend color: '#69BA34', message: "Projeto ${projectFullName} construído com sucesso!"
   }
}