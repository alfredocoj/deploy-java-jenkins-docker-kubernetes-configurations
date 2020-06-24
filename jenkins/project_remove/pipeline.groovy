node {
    logstash {
        
        def projectFullName = "${MODULO}-${TIPO_DO_PROJETO}-${NOME_DO_PROJETO}"
        
        stage('Limpando Workspace') {
           cleanWs()
        }
        
        stage('[Validação] Existência do projeto') {
           

            withCredentials([string(credentialsId: 'GitlabAccessToken', variable: 'GITLAB_TOKEN')]) {
                    
                httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], ignoreSslErrors: true, httpMode: 'GET', url: "https://gitlab.mateus/api/v4/projects/${MODULO}%2F${projectFullName}", validResponseCodes: '200:299'

            }
        }

        stage("[Escolha] Escolher se o projeto será excluído "){

            script{
                
                def DELETE_CHOICE = input message: "Você deseja mesmo deletar o projeto ${projectFullName}", ok: 'Next',
                    parameters: [
                        choice(name: 'DELETE_CHOICE', choices: ['sim', 'nao'].join('\n'), description: 'Por favor, selecione [sim] para apagar e [não para cancelar] - (1): sim e (2): nao')]
             
                if (DELETE_CHOICE == 'nao'){

                    error("O projeto nao será excluido")
                }

                else{
                    println("Vamos excluir o projeto ${projectFullName}")
                }
             
             }

        }
        
        stage ('[Templates Deployment] Deletando arquivo de Configuração de Deploy'){

            sh 'mkdir configs-templates-deployment'

            dir('configs-templates-deployment') {
                
                checkout changelog: true, poll: true, scm: [
                        $class: 'GitSCM',
                        branches: [[name: "origin/master"]],
                        doGenerateSubmoduleConfigurations: false,
                        submoduleCfg: [],
                        userRemoteConfigs: [[credentialsId: 'JenkinsUserInGitLab', url: "git@gitlab.mateus:infra/configs-templates-deployment.git"]]
                ]

                sshagent(['JenkinsUserInGitLab']) {
                                    sh label: '', script: """
                                        git rm  "conf/java/${TIPO_DO_PROJETO}/master/${MODULO}/${projectFullName}.groovy"
                                        git rm  "conf/java/${TIPO_DO_PROJETO}/homologacao/${MODULO}/${projectFullName}.groovy"
                                        git config user.email "jenkins@armateus.com.br"
                                        git config user.name "Jenkins"
                                        git diff --quiet && git diff --staged --quiet || git commit --allow-empty -m "Deletando projeto: ${projectFullName}"
                                        git push -u origin HEAD:master
                                        """   
                }
            }
        }
        
        stage('[GitLab] Deletando seu projeto'){
            
            configFileProvider([configFile(fileId: 'gitea-client', variable: 'GITEA_CLIENT_GROOVY')]) {
                def jsonHelper = load("${GITEA_CLIENT_GROOVY}")

                withCredentials([string(credentialsId: 'JENKINS_ADMIN_GITLAB', variable: 'GITLAB_TOKEN')]) {

                def responseGrupo = httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], ignoreSslErrors: true, url: "https://gitlab.mateus/api/v4/groups?search=${MODULO}", validResponseCodes: '200'
                def idGrupo = jsonHelper.extrairIdGrupo(responseGrupo.content)
                    
                    script {
                        if (idGrupo == 0) {
                                   error("Grupo ${MODULO} não encontrado!!")
                        }
                    }
            
                def responseProjeto = httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], ignoreSslErrors: true, url: "https://gitlab.mateus/api/v4/projects/${MODULO}%2F${projectFullName}", validResponseCodes: '200:299'
                def idProjeto = jsonHelper.extrairIdProjeto(responseProjeto.content)

                   // println("ID DO GRUPO ${MODULO} é ${idGrupo}")
                   // println("ID DO PROJETO ${projectFullName} é ${idProjeto}")
                
                    script{
                        
                        if(idProjeto > 0){

                            httpRequest acceptType: 'APPLICATION_JSON', consoleLogResponseBody: true, customHeaders: [[maskValue: false, name: 'Authorization', value: "Bearer ${GITLAB_TOKEN}"]], httpMode: 'DELETE', ignoreSslErrors: true, responseHandle: 'NONE', url: "https://gitlab.mateus/api/v4/projects/${idProjeto}", validResponseCodes: '200:299'
                    //    println ("Projeto ${projectFullName} foi deletado")
                        } else {

                            error("Projeto não foi deletado")
                        }
                    }
                    
                }
            }
        }

        stage('[Jenkins] Deletando seu projeto'){
            
            def jobnameMaster = Jenkins.instance.getItem("${projectFullName}-master")
            def jobnameStaging = Jenkins.instance.getItem("${projectFullName}-staging")
            def jobnamePR = Jenkins.instance.getItem("${projectFullName}-pull-request")
            
            // deletando job master
            println("${jobnameMaster}")
            jobnameMaster.delete()
            
            // deletando job stating
            println("${jobnameStaging}")
            jobnameStaging.delete()
            
            // deletando job pull-request
            println("${jobnamePR}")
            jobnamePR.delete()


        } 
    }
}
