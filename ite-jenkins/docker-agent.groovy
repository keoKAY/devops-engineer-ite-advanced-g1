
// Plugin: Docker Pipeline 
pipeline { 
    agent {
        docker {
            image 'node:alpine' // base image for cont.
            args '-u root' // run as root 
        }
    }

    stages {
        stage('Git Checkout') {
            steps {
                git 'https://github.com/keoKAY/reactjs-devop8-template.git'
            }
        }
        stage('Run Test'){
            steps{
                sh '''
                node --version 
                npm --version 

                npm install --force 
                npm run test 
                '''
            }
        }

        stage("Build"){
            steps{
                sh 'npm run build'
            }
        }
    }
}
