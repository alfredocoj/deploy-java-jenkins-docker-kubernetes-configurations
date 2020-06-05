pipeline {
   agent any

   environment {
      REGISTRY = '192.168.6.144:5001'
      IMAGE_ON_REGISTRY = '$REGISTRY/ithappens/tomcat-homolog-ithappens'
      URL_BASE = "http://167.99.126.144:8080/mateus/release"
      FILE_LAST_VERSION = 'lastversion.txt'
      NAME_IMAGE_DOCKER = 'tomcat-service-homolog'
      LAST_VERSION = sh(script: 'echo $((${BUILD_NUMBER}%5))', returnStdout: true)
      FILE_NAME_LASTVERSION=sh(script: 'wget http://167.99.126.144:8080/mateus/release/maxipos_homol_lastupdate.txt -O  maxipos_homol_lastupdate.txt >/dev/null  && cat  maxipos_homol_lastupdate.txt  ', returnStdout: true)
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

      stage ('Docker pull tomcat base'){
          steps {
              sh "docker pull 192.168.6.144:5001/ithappens/tomcat-ithappens-base:latest"
          }
      }

      stage('Build and Push tomcat-service') {
               /* This builds the actual image; synonymous to
          * docker build on the command line */
         when {
            // Only say hello if a "greeting" is requested
            expression { params.tomcat_service_3_sobe_venda || tomcat_service_2_grava_nfce || tomcat_service_1_autoriza_nfce || tomcat_pdv_status }
         }

         steps {

            sh 'printenv' 

            sh "docker build . -f DockerfileProd-service -t tomcat-service-homolog --no-cache"

            sh "docker tag `docker images -q tomcat-service-homolog` $REGISTRY/ithappens/tomcat-service-homolog-ithappens:1.2.4.$LAST_VERSION"

            sh "docker tag `docker images -q tomcat-service-homolog` $REGISTRY/ithappens/tomcat-service-homolog-ithappens:latest"

            sh "echo 'docker tag `docker images -q tomcat-service-homolog` $REGISTRY/ithappens/tomcat-service-homolog-ithappens:1.2.4.$LAST_VERSION'"

            sh "docker push $REGISTRY/ithappens/tomcat-service-homolog-ithappens:1.2.4.$LAST_VERSION"

            sh 'docker push $REGISTRY/ithappens/tomcat-service-homolog-ithappens:latest'

         }
      }

      stage('Build and Push tomcat-share') {
                /* This builds the actual image; synonymous to
          * docker build on the command line */
         when {
            // Only say hello if a "greeting" is requested
            expression { params.tomcat_share }
         } 
         steps {

            sh "docker build . -f DockerfileProd-share -t tomcat-share-homolog --no-cache"

            sh "docker tag `docker images -q tomcat-share-homolog` $REGISTRY/ithappens/tomcat-share-homolog-ithappens:1.2.4.$LAST_VERSION"

            sh "docker tag `docker images -q tomcat-share-homolog` $REGISTRY/ithappens/tomcat-share-homolog-ithappens:latest"

            sh "echo 'docker tag `docker images -q tomcat-share-homolog` $REGISTRY/ithappens/tomcat-share-homolog-ithappens:1.2.4.$LAST_VERSION'"

            sh "docker push $REGISTRY/ithappens/tomcat-share-homolog-ithappens:1.2.4.$LAST_VERSION"

            sh "docker push $REGISTRY/ithappens/tomcat-share-homolog-ithappens:latest"

         }
      }

      stage('Build and Push tomcat-task') {
                   /* This builds the actual image; synonymous to
          * docker build on the command line */
         when {
            // Only say hello if a "greeting" is requested
            expression { params.tomcat_task }
         }
     
         steps {

            sh "docker build . -f DockerfileProd-task -t tomcat-task-homolog --no-cache"

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

}