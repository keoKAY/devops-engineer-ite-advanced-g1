pipeline {
    agent any
    environment{
        DH_IMAGE="reactjs-special"
        TAG="v1.0.2026"
        FULL_IMAGE="${DH_IMAGE}:${TAG}"
    }
    stages {
        stage('Clone Code') {
            steps {
                git 'https://github.com/keoKAY/reactjs-devop8-template.git'
            }
        }
        stage('Build Image') {
            steps {
                sh """
                docker build -t ${DH_IMAGE}:${TAG} -f prod.Dockerfile . 
                """
            }
        }
        stage("Push"){
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
        stage('Deploy') {
            steps {
               sh """
                docker rm -f react-cont || true  
                docker run -dp 3005:80 --name react-cont ${FULL_IMAGE} 
               """
            }
        }
    }
}
