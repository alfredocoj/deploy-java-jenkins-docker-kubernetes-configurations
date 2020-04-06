node {

      stage('Clone repository') {
          
                git branch: 'dockerizacao',
                        url: 'git@git.mateus:grupomateus/rh_curriculo.git'

                sh "ls -lat"
            
        }

   stage('Build') {
   //     checkout scm
      //  sh 'cp /home/servidorbuild/configs/rh/itpeople/backend/production.py backend/rh/settings/'
        sh 'cd curriculo-back && docker image build -t rh-curriculo-backend --force-rm . -f Dockerfile-prod'
    }

    stage('Push') {
        echo 'Push docker images'
        sh 'docker tag rh-curriculo-backend 10.54.0.214:5001/ithappens/rh-curriculo-backend:latest'
        sh 'docker push 10.54.0.214:5001/ithappens/rh-curriculo-backend:latest'
    }

    stage('Deploy') {

        sh 'ssh deploy@192.168.6.40 /home/deploy/deploys/rh/start-curriculo-backend.sh'
        sh 'echo "deploy finalizado!!"'

    }
