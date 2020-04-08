pipeline {
   agent any

   environment {
      REGISTRY = '10.54.0.214:5001'
      IMAGE_ON_REGISTRY = '$REGISTRY/ithappens/tomcat-homolog-ithappens'
      URL_BASE = "http://167.99.126.144:8080/mateus/release"
      FILE_LAST_VERSION = 'lastversion.txt'
      NAME_IMAGE_DOCKER = 'tomcat-service-homolog'
      LAST_VERSION = sh(script: 'echo $((${BUILD_NUMBER}%5))', returnStdout: true)
      FILE_NAME_LASTVERSION=sh(script: 'wget http://167.99.126.144:8080/mateus/release/maxipos_homol_lastupdate.txt -O  maxipos_homol_lastupdate >/dev/null  && cat  maxipos_homol_lastupdate  ', returnStdout: true)
      TEMPLATES_DIR_ANSIBLE = "ansible"
   }


   stages {

      stage('Clone repository') {
     
         steps {
            git branch: 'master',
                  credentialsId: '105d78b2-3f22-467e-82ac-e52151f8a48f',
                  url: 'git@git.mateus:infra/pdv-apis-configurations.git'

            sh "ls -lat"
         }

      }

      stage('Download .wars') {

         steps {

         
            sh "rm -rf *.war && \
            rm -rf *.zip && \
            rm -rf *.jar && \
            rm  -rf maxipos_homol_lastupdate.txt"

            sh "wget $URL_BASE/$FILE_NAME_LASTVERSION -O maxipos.zip"
            sh "ls -la"
            sh "unzip maxipos.zip"

         }

      }

      stage('Build and Push tomcat-service') {
      
         steps {

            sh "docker pull 10.54.0.214:5001/ithappens/tomcat-ithappens-base:latest"

            sh "docker build . -f DockerfileProd-service -t tomcat-service-homolog"

            sh "docker tag `docker images -q tomcat-service-homolog` $REGISTRY/ithappens/tomcat-service-homolog-ithappens:1.2.4.$LAST_VERSION"

            sh "docker tag `docker images -q tomcat-service-homolog` $REGISTRY/ithappens/tomcat-service-homolog-ithappens:latest"

            sh "echo 'docker tag `docker images -q tomcat-service-homolog` $REGISTRY/ithappens/tomcat-service-homolog-ithappens:1.2.4.$LAST_VERSION'"

            sh "docker push $REGISTRY/ithappens/tomcat-service-homolog-ithappens:1.2.4.$LAST_VERSION"

            sh 'docker push $REGISTRY/ithappens/tomcat-service-homolog-ithappens:latest'

         }
      }

      stage('Build and Push tomcat-share') {
          
         steps {

            sh "docker build . -f DockerfileProd-share -t tomcat-share-homolog"

            sh "docker tag `docker images -q tomcat-share-homolog` $REGISTRY/ithappens/tomcat-share-homolog-ithappens:1.2.4.$LAST_VERSION"

            sh "docker tag `docker images -q tomcat-share-homolog` $REGISTRY/ithappens/tomcat-share-homolog-ithappens:latest"

            sh "echo 'docker tag `docker images -q tomcat-share-homolog` $REGISTRY/ithappens/tomcat-share-homolog-ithappens:1.2.4.$LAST_VERSION'"

            sh "docker push $REGISTRY/ithappens/tomcat-share-homolog-ithappens:1.2.4.$LAST_VERSION"

            sh "docker push $REGISTRY/ithappens/tomcat-share-homolog-ithappens:latest"

         }
      }

      stage('Build and Push tomcat-task') {
     
         steps {

            sh "docker build . -f DockerfileProd-task -t tomcat-task-homolog"

            sh "docker tag `docker images -q tomcat-task-homolog` $REGISTRY/ithappens/tomcat-task-homolog-ithappens:1.2.4.$LAST_VERSION"

            sh "docker tag `docker images -q tomcat-task-homolog` $REGISTRY/ithappens/tomcat-task-homolog-ithappens:latest"

            sh "echo 'docker tag `docker images -q tomcat-task-homolog` $REGISTRY/ithappens/tomcat-task-homolog-ithappens:1.2.4.$LAST_VERSION'"

            sh "docker push $REGISTRY/ithappens/tomcat-task-homolog-ithappens:1.2.4.$LAST_VERSION"

            sh "docker push $REGISTRY/ithappens/tomcat-task-homolog-ithappens:latest"

         }
      }

      stage('Deploy production') {
         environment {
            ANSIBLE_PLAYBOOK = "${TEMPLATES_DIR_ANSIBLE}/ansible-tomcat-homolog.yaml"
            //GROOVYFILE = "conf/${ARTIFACT}.groovy"
         }
         steps {
            //load "${GROOVYFILE}"
            //sh 'printenv'
            sh 'cat $ANSIBLE_PLAYBOOK | envsubst > ansible.yml'
            sh 'ansible-playbook -i $TEMPLATES_DIR_ANSIBLE/hosts ansible.yml'
         }
      }
   }
