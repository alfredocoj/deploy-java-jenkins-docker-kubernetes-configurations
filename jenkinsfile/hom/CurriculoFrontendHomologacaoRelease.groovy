node {

      stage('Clone repository') {
            /* Let's make sure we have the repository cloned to our workspace */

            //checkout scm
            
                git branch: 'dockerizacao',
                        //credentialsId: 'my_cred_id',
                        url: 'git@git.mateus:grupomateus/rh_curriculo.git'

                sh "ls -lat"
            
        }
    stage ('Conf'){
        
        sh "sed -i 's/10.121.2.238/192.168.6.40/g' curriculo-front/src/api/config/config.json"
        sh 'echo substituindo ips'

        
    }    
   stage('Build') {
   //     checkout scm
     //   sh 'cp /home/servidorbuild/configs/rh/itpeople/backend/production.py backend/rh/settings/'
        sh 'cd curriculo-front && docker image build -t rh-curriculo-frontend-pg --force-rm . -f Dockerfile'
    }

    stage('Push') {
        echo 'Push docker images'
        sh 'docker tag rh-curriculo-frontend-pg 10.54.0.214:5001/ithappens/rh-curriculo-frontend-pg:latest'
        sh 'docker push 10.54.0.214:5001/ithappens/rh-curriculo-frontend-pg:latest'
    }

    stage('Deploy') {

        sh 'ssh deploy@192.168.6.40 /home/deploy/deploys/rh/start_rh-curriculo-frontend.sh'
        sh 'echo "deploy finalizado!!"'

    }
