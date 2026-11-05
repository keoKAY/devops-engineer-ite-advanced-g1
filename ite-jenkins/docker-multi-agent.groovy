
// Plugin: Docker Pipeline 
pipeline { 


    agent none 

    stages {

    stage('Stage 1'){
        agent {
            docker {
                image 'node:alpine' // base image for cont.
                args '-u root' // run as root 
            }
        }
            steps{
                sh '''
                node --version 
                npm --version 
                '''
            }
        }

    
    

    stage("Stage2"){
        agent{
            docker {
                image 'nginx:alpine'
                args '-u root '
            }
        }
        steps{
            sh "nginx -v"
        }
    }
    }
}
