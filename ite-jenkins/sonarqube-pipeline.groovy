pipeline {
    agent any

    environment{
        DH_IMAGE="reactjs-special"
        TAG="v1.0.${env.BUILD_NUMBER}"
        FULL_IMAGE="${DH_IMAGE}:${TAG}"
    }

    options{
        // discard old build 
        // timeout 
         timeout(time: 3, unit: 'MINUTES')
         buildDiscarder(logRotator(numToKeepStr: '3', daysToKeepStr: '7'))
    }
    stages {
      
       
        stage('Git Checkout') {
            steps {
                git 'https://github.com/keoKAY/reactjs-devop8-template.git'
            }
        }
        stage('Sonarqube Scan '){
            environment {
                scannerHome= tool 'sonarqube-scanner' 
            }

            steps{
                withSonarQubeEnv(credentialsId: 'SONARQUBE-TOKEN', installationName: 'sonarqube-scanner') {
                script{
                    def projectKey = 'reactjs-ite-template' 
                    def projectName = 'ReactjsITEtemplate'
                    def projectVersion = '1.0.0' 
                    
                    sh """
                    ${scannerHome}/bin/sonar-scanner \
                    -Dsonar.projectKey=${projectKey} \
                    -Dsonar.projectName="${projectName}" \
                    -Dsonar.projectVersion=${projectVersion} \
                     """   
                        
                        }
            }

            } 
        }
        


            }


    
    


}
