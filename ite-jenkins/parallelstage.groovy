pipeline{
    agent any
    stages{
        stage("Work 1 "){
            steps{
                sh """ 
                    echo "Work 1 is completed "
                """
            }
        }
        stage("Parallel Stage"){
            parallel{
                stage("Work 2 "){
                    steps{
                    sh """
                    echo "Work 2 is completed ! "
                    """
                    }
                }
                stage("Work 3 "){
                    steps{
                    sh """
                    echo "Work 3 is completed "
                    """
                    }
                }

                stage("Work 4"){
                    steps{
                    sh """
                    echo "Work 4 is completed "
                    """
                    }
                }
            }
        }
    }
}