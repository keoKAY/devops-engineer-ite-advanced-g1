pipeline {
    agent any
    environment{
        DH_IMAGE="reactjs-special"
        DH_USER="lyvanna544"
        TAG="1.0.${env.BUILD_NUMBER}"
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
        stage("Choose Environment"){
            steps{
            script{
                def userInput=input message: 'Choose Server', parameters: [choice(choices: ['prod-server', 'stage-server'], name: 'CHOOSE YOUR ENVIRONMENT:')] 
               
                node(userInput){
                    stage("Deploy Container"){
                     sh """
                     echo "Chosen environment is : ${userInput}"
                    docker rm -f react-cont || true  
                    docker run -dp 3005:80 --name react-cont  ${DH_USER}/${FULL_IMAGE}  
                     """       
                       
                    }
                }
               
                }
            }
        }
    }
}
