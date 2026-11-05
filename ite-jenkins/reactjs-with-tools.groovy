pipeline {
    agent any
    tools{
        nodejs 'nodejs-v22'
    }
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
        // call another job 
        stage("Call Other Job"){
            steps{
                // project with no params
                build 'first-demo'
                // build project with params 
                build job: 'reactjs-with-tools', parameters: [booleanParam(name: 'RUN_TEST', value: false)], waitForStart: true
         
            }
        }
        stage("Test Tools"){
            steps{
                sh """
                node --version 
                npm --version 
                """
            }
        }
        stage('Git Checkout') {
            steps {
                git 'https://github.com/keoKAY/reactjs-devop8-template.git'
            }
        }
        stage("Run Test "){
            when {
                expression{
                    params.RUN_TEST==true 
                }
            }
            steps{
                sh """
                    npm install 
                    npm run test 
                """
            }
        }
        stage('Build Image') {
            steps {
                sh """
                docker build -t ${DH_IMAGE}:${TAG} -f prod.Dockerfile . 
                """
            }
        
        }


        stage("Push Image"){
            steps{
                script{
                    def userInput = input message: 'Choose your environment', parameters: [choice(choices: ['Production', 'Stagging'], name: 'ENVIROMENT')]

                    // def userInput = input(
                    //     message: 'Choose your environemt: ', 
                    //     parameters: [
                    //         choice(name: 'ENVIRONMENT', choices: ['Staging', 'Production'], description: 'Target Environment')
                    //     ]
                    // )
                    sh """
                    echo "User choise is : ${userInput}"

                    """
                    if(userInput == "Production"){
                        sh """
                            echo "Pushing this to the production environment"
                        """
                    }else {
                        echo """
                        echo "Pushing image to Stagging enviroment"
                        """
                    }

                }
            }
        }
            }


    
    
    post{
        always{
            echo "This always run!! "
            cleanWs() // clean workspace 
        }
        success{
            // send message to group ! 
            echo "This means your pipeline is success "

        }
        failure{
            // send message to group ! 
            echo "This means your pipeline is failed "
        }
    }

}
