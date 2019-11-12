node {
   environment {
       PROJECT_FULL_NAME="${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO}"
   }
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
           httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${MODULO}%2F${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO}", validResponseCodes: '404'
       }
   }
   stage('[Build] Criando projeto') {
       tool name: 'M3', type: 'maven'
       def mvnCommand = ""
       script {
           def tipo = "${TIPO_DO_PROJETO}"
           if (tipo == "rest")
               mvnCommand = "archetype:generate -DarchetypeGroupId=br.com.ithappens -DarchetypeArtifactId=archetype -DarchetypeVersion=1.3 -DgroupId=br.com.ithappens.${MODULO}.${TIPO_DO_PROJETO} -DartifactId=${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO} -Dversion=0.1.0"
           else if (tipo == "task")
               mvnCommand = "archetype:generate -DarchetypeGroupId=br.com.ithappens -DarchetypeArtifactId=archetype-task -DarchetypeVersion=1.2 -DgroupId=br.com.ithappens.${MODULO}.${TIPO_DO_PROJETO} -DartifactId=${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO} -Dversion=0.1.0"
           else if (tipo == "maestro")
               mvnCommand = "archetype:generate -DarchetypeGroupId=br.com.ithappens -DarchetypeArtifactId=maestro-archetype -DarchetypeVersion=1.3 -DgroupId=br.com.ithappens.${MODULO}.${TIPO_DO_PROJETO} -DartifactId=${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO} -Dversion=0.1.0"
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
                   def responseNewProject = httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'POST', ignoreSslErrors: true, url: "https://gitlab.mateus/api/v4/projects?name=${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO}&namespace_id=${idGrupo}&only_allow_merge_if_pipeline_succeeds=true", validResponseCodes: '200:299'
                   idProjeto = jsonHelper.extrairIdProjeto(responseNewProject.content)
                   
                   withCredentials([sshUserPrivateKey(credentialsId: 'Chave-SSH-Ivo', keyFileVariable: 'SSH_FILE', passphraseVariable: '', usernameVariable: '')]) {
                       // Faz o push do projeto criado no GitLab
                       sh label: '', script: '''cd "${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO}"
                        git init
                        git config core.sshCommand "ssh -i ${SSH_FILE} -F /dev/null"
                        git config user.email 'ivo.lopes@armateus.com.br'
                        git config user.name 'Ivo Lopes'
                        git add .gitignore pom.xml README.md src/
                        git commit -m 'Primeiro Commit'
                        git remote add origin "git@gitlab.mateus:${MODULO}/${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO}.git"
                        git push -u origin master
                        cd ..'''
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
                       // Cria um hook na branch master
                       httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'POST', ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${idProjeto}/hooks?push_events=true&push_events_branch_filter=master&note_events=true&url=http%3A%2F%2Fivolopes%3A11f5c0a26fea6e7656da2310c3b5187cb4%40192.168.6.95%3A32595%2Fproject%2F${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO}-master", validResponseCodes: '200:299'
                       // Cria um hook na branch staging
                       httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'POST', ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${idProjeto}/hooks?push_events=true&push_events_branch_filter=staging&note_events=true&url=http%3A%2F%2Fivolopes%3A11f5c0a26fea6e7656da2310c3b5187cb4%40192.168.6.95%3A32595%2Fproject%2F${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO}-staging", validResponseCodes: '200:299'
                       // Cria um hook para avaliar pull requests
                       httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'POST', ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${idProjeto}/hooks?push_events=false&merge_requests_events=true&note_events=true&url=http%3A%2F%2Fivolopes%3A11f5c0a26fea6e7656da2310c3b5187cb4%40192.168.6.95%3A32595%2Fproject%2F${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO}-pull-request", validResponseCodes: '200:299'
                    }
                } catch (Exception ex) {
                   if(idProjeto > 0) {
                       // Deu erro... deleto o repositório
                       httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'DELETE', ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${idProjeto}", validResponseCodes: '200:299'
                   }
                   error(ex.message)
               }
           }
       }
   }
   
   stage('Arquivando projeto') {
       zip archive: true, dir: "${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO}", glob: '', zipFile: "${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO}.zip"
   }

   stage('Criando Arquivo de Configuracoes de Deploy') {
      checkout changelog: true, poll: true, scm: [
             $class: 'GitSCM',
             branches: [[name: "origin/master"]],
             doGenerateSubmoduleConfigurations: false,
             submoduleCfg: [],
             userRemoteConfigs: [[credentialsId: 'Chave-SSH-Ivo', url: "git@gitlab.mateus:infa/configs-templates-deployment.git"]]
      ]

      // Branch: Master
      sh 'cat configs-templates-deployment/conf/java/${TIPO_DO_PROJETO}/master/template-master.groovy | envsubst > configs-templates-deployment/conf/java/${TIPO_DO_PROJETO}/master/${MODULO}/${NOME_DO_PROJETO}.groovy'

      // Branch: Homologacao
      sh 'cat configs-templates-deployment/conf/java/${TIPO_DO_PROJETO}/homologacao/template-homologacao.groovy | envsubst > configs-templates-deployment/conf/java/${TIPO_DO_PROJETO}/homologacao/${MODULO}/${NOME_DO_PROJETO}.groovy'

      sh 'cd configs-templates-deployment'

      sh 'git config core.sshCommand "ssh -i ${SSH_FILE} -F /dev/null"'

      sh 'git config user.email \'jenkins@armateus.com.br\''

      sh 'git config user.name \'Jenkins\''

      sh 'git add .'

      sh 'git commit -m "new project: ${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO}"'

      sh 'git push -u origin master'

      sh 'rm -rf configs-templates-deployment'
   }
}