pipeline {
    agent any
     tools{
        nodejs 'nodejs-v22'
    }
    environment{
        DH_IMAGE="springboot-fileupload-special"
        TAG="v1.0.${env.BUILD_NUMBER}"
        FULL_IMAGE="${DH_IMAGE}:${TAG}"
    }

    options{
        // discard old build 
        // timeout 
        // timeout(time: 3, unit: 'MINUTES')
         buildDiscarder(logRotator(numToKeepStr: '3', daysToKeepStr: '7'))
    }
    stages {
        stage('Git Checkout') {
            steps {
               // git 'https://gitlab.com/devops-trainings3/special-trainning/sample-projects/simple-fileupload-gradle.git'
        
            git 'https://github.com/keoKAY/reactjs-devop8-template.git'
        
            }
        }

stage('OWASP Dependency Check') {
    steps {
         sh '''
            mkdir -p dependency-check-report
            npm install 
        '''
        dependencyCheck(
            
            nvdCredentialsId: 'ndv-api-key',
            additionalArguments: """
                --project "${env.JOB_NAME}"
                --scan .
                --out dependency-check-report
                --format XML
                --format HTML
                --prettyPrint
            """,
            odcInstallation: 'OWASP-Dependency-Check'
        )

        dependencyCheckPublisher(
            pattern: 'dependency-check-report/dependency-check-report.xml',
            failedTotalCritical: 1,
            failedTotalHigh: 1
        )
    }

  
}
        stage('Sonarqube Scan '){
            when {
                expression { false }
            }
            environment {
                scannerHome= tool 'sonarqube-scanner' 
            }



            steps{
                withSonarQubeEnv(credentialsId: 'SONARQUBE-TOKEN', installationName: 'sonarqube-scanner') {
            
            script {
                def projectKey = 'springboot-product-api'
                def projectName = 'SpringBootProductAPI'
                def projectVersion = '1.0.0'

                sh """
                    ${scannerHome}/bin/sonar-scanner \
                    -Dsonar.projectKey=${projectKey} \
                    -Dsonar.projectName="${projectName}" \
                    -Dsonar.projectVersion=${projectVersion} \
                    -Dsonar.sources=src/main/java \
                    -Dsonar.tests=src/test/java \
                    -Dsonar.java.binaries=build/classes/java/main \
                    -Dsonar.java.test.binaries=build/classes/java/test \
                    -Dsonar.sourceEncoding=UTF-8
                """
            }
            }

            } 
        }
        
        stage("Wait for Quality Gate"){
            when {
                expression { false }
            }
            steps{
                script{
                    def qg = waitForQualityGate()
                    if ( qg.status != 'OK'){
                        sh """
                        echo " No need to build since you QG is failed "
                        """

                        currentBuild.result='FAILURE'
                        error("Quality Gate is Failed !! ")
                        return 
                    }else {
                        // quality gate is passed ! 
                        echo "Quality of code is okay!! "
                        currentBuild.result='SUCCESS'
                    }
                }
            }
        }
        stage('Build Image') {
                    steps {
                        sh """
                        docker build -t ${DH_IMAGE}:${TAG} -f dev.Dockerfile . 
                        """
                    }
                
                }
        stage("Push"){
            when {
                expression { false }
            } 
            options{
                timeout(time: 3, unit: 'MINUTES')
            } 
            steps{
                withCredentials([usernamePassword(credentialsId: 'DH_STAG', passwordVariable: 'PASS', usernameVariable: 'NAME')]) {
                sh """
                    docker tag ${FULL_IMAGE} ${NAME}/${FULL_IMAGE}
                    docker login -u ${NAME} -p ${PASS}
                    docker push ${NAME}/${FULL_IMAGE} 
                    
                    
                """
                }
            }
        }
            }

}
