node {


        stage('Clone repository') {
            /* Let's make sure we have the repository cloned to our workspace */

            //checkout scm
            
                git branch: 'master',
                        //credentialsId: 'my_cred_id',
                        url: 'git@git.mateus:grupomateus/rh_services.git'

                sh "ls -lat"
            
        }


   stage('Build') {
        sh 'cd importacaoRH && docker image build -t rh-importacao-pg --force-rm . -f Dockerfile'
    }

    stage('Push') {
        echo 'Push docker images'
        sh 'docker tag rh-importacao-pg 10.54.0.214:5001/ithappens/rh-importacao-pg:latest'
        sh 'docker push 10.54.0.214:5001/ithappens/rh-importacao-pg:latest'
    }

    stage('Deploy') {

        sh 'ssh deploy@192.168.6.40 /home/deploy/deploys/rh/start_rh-importacao.sh'
        sh 'echo "deploy finalizado!!"'

    }
}