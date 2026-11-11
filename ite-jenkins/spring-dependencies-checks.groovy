// NOT YET WORKING 
// DEPENDENCIES NEED TO BE INSTALLED ON THE SPRING SOURCE CODE 

pipeline {
    agent any

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
             git 'https://gitlab.com/devops-trainings3/special-trainning/sample-projects/simple-fileupload-gradle.git'
           
            }
        }




stage('Resolve Dependencies') {
    steps {
        sh '''
            set -eu

            if [ ! -x ./gradlew ]; then
                chmod +x ./gradlew
            fi

            if [ ! -f build.gradle ] && [ ! -f build.gradle.kts ]; then
                echo "No root Gradle build file found."
                exit 1
            fi

            cat > /tmp/odc-init.gradle <<'EOF'
gradle.projectsEvaluated {
    def root = gradle.rootProject

    root.tasks.register('exportDependencyCheckArtifacts') {
        doLast {
            def outputDir = new File(
                root.buildDir,
                'dependency-check-artifacts'
            )

            outputDir.mkdirs()

            root.allprojects.each { project ->

                ['runtimeClasspath', 'testRuntimeClasspath'].each { name ->

                    def configuration =
                        project.configurations.findByName(name)

                    if (configuration != null &&
                        configuration.canBeResolved) {

                        configuration.resolvedConfiguration
                            .resolvedArtifacts.each { artifact ->

                                project.copy {
                                    from artifact.file
                                    into outputDir
                                }
                            }
                    }
                }
            }

            println "Dependency artifacts exported to: ${outputDir}"
        }
    }
}
EOF

            ./gradlew --no-daemon \
                -I /tmp/odc-init.gradle \
                exportDependencyCheckArtifacts
        '''
    }
}

stage('OWASP Dependency Check') {
    steps {
         sh '''
            mkdir -p dependency-check-report
  
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
       
       
stage('Trivy Filesystem Scan') {
    environment {
        TRIVY_IMAGE = 'aquasec/trivy:0.76.2'
    }

    steps {
        script {
            sh '''
                set -eu

                mkdir -p trivy-reports

                CACHE_DIR="${JENKINS_HOME}/trivy-cache"
                mkdir -p "$CACHE_DIR"

                docker pull "$TRIVY_IMAGE"

                docker run --rm \
                    --user "$(id -u):$(id -g)" \
                    -v "$WORKSPACE:/workspace" \
                    -v "$CACHE_DIR:/tmp/trivy-cache" \
                    -w /workspace \
                    "$TRIVY_IMAGE" \
                    --cache-dir /tmp/trivy-cache \
                    fs \
                    --scanners vuln,misconfig,secret \
                    --include-dev-deps \
                    --severity HIGH,CRITICAL \
                    --skip-dirs .git \
                    --skip-dirs .gradle \
                    --format json \
                    --output trivy-reports/trivy-fs-report.json \
                    .
            '''
        }
    }
}

stage('Publish Trivy Filesystem Report') {
    steps {
        recordIssues(
            tools: [
                trivy(
                    pattern: 'trivy-reports/trivy-fs-report.json'
                )
            ],
            enabledForFailure: true
        )
    }
}

stage('Trivy Security Gate') {
    steps {
        sh '''
            docker run --rm \
                --user "$(id -u):$(id -g)" \
                -v "$WORKSPACE:/workspace" \
                -v "${JENKINS_HOME}/trivy-cache:/tmp/trivy-cache" \
                -w /workspace \
                aquasec/trivy:0.76.2 \
                --cache-dir /tmp/trivy-cache \
                fs \
                --scanners vuln,misconfig,secret \
                --include-dev-deps \
                --severity HIGH,CRITICAL \
                --skip-dirs .git \
                --skip-dirs .gradle \
                --exit-code 1 \
                .
        '''
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
