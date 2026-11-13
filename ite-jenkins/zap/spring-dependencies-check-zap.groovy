// Trying to intergrate with the owasp zap services
// NOTE: NOT YET WORKING CORRECTLY  
pipeline {
    agent any

    environment{
        DH_IMAGE="springboot-fileupload-special"
        TAG="v1.0.${env.BUILD_NUMBER}"
        FULL_IMAGE="${DH_IMAGE}:${TAG}"
        // for our spring boot service 
        //ZAP_TARGET_URL = 'http://host.docker.internal:8081'
    }

    options{
        // discard old build 
        // timeout 
        // timeout(time: 3, unit: 'MINUTES')
         buildDiscarder(logRotator(numToKeepStr: '3', daysToKeepStr: '7'))
    }
    stages {
        stage('Checkout') {
            steps {
             git 'https://gitlab.com/devops-trainings3/special-trainning/sample-projects/simple-fileupload-gradle.git'
           
            }
        }

            // This Shall be fixed by shared library 
            stage('Resolve Dependencies') {
                when {
                    expression { false }
                }
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
            // Fix: used jenkins shared library instead 
            stage('OWASP Dependency Check') {
                when {
                    expression { false }
                }
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
                    withSonarQubeEnv(
                    credentialsId: 'SONARQUBE-TOKEN', 
                    installationName: 'sonarqube-scanner') {
                
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
       
        // Fix🔥 : With the jenkins shared libraries 
        stage('Trivy Filesystem Scan') {
            when {
                expression { false}
            }
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
            when { 
                expression { false }
            }
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
            when { expression { false }}
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


        stage("Deploy"){
            steps{
                sh """
                 # Create network if it does not exist
                docker network inspect ci-security >/dev/null 2>&1 || \
                    docker network create ci-security

                # Remove previous container
                docker rm -f spring-cont || true

                # Deploy application on shared network
                docker run -d \
                    --name spring-cont \
                    --network ci-security \
                    -p 127.0.0.1:8081:8080 \
                    ${DH_IMAGE}:${TAG} 
                """

                // Wait for spring boot be accessible 
                sh '''
                echo "Waiting for the Spring Boot.... 🔥"
                for i in $(seq 1 60 ); do 
                    if curl -sS -o /dev/null \
                        --max-time 2 \
                        http://127.0.0.1:8081; then 

                        echo "Application is accessible! " 
                        exit 0 
                    fi 
                    sleep 2 
                done 

                echo "Application failed be become accessible ❌" 
                docker logs spring-cont 
                exit 1 
                '''
            }
        }

    // Fix: this will need to move to the shared lib 
    // Usage: Using the owasp zap for application Security testings
      

stage('OWASP ZAP - DAST') {

    environment {
        ZAP_IMAGE = 'ghcr.io/zaproxy/zaproxy:stable'
        ZAP_NETWORK = 'ci-security'
        ZAP_TARGET_URL = 'http://spring-cont:8080'
        //ZAP_TARGET_URL = 'https://istad.co'
    }

    steps {
        script {

            // 1. Prepare report directory
            sh '''
                mkdir -p "$WORKSPACE/zap-reports"
                chmod 777 "$WORKSPACE/zap-reports"
            '''

            // 2. Generate Automation Framework configuration
            writeFile(
                file: 'zap-reports/zap.yaml',
                text: """
env:
  parameters:
    failOnError: true
    failOnWarning: false
    progressToStdout: true

  vars:
    target: "${env.ZAP_TARGET_URL}"

jobs:
  - type: spider
    parameters:
      url: "\${target}"
      maxDuration: 2

  - type: passiveScan-wait
    parameters:
      maxDuration: 5

  - type: report
    parameters:
      template: traditional-html
      reportDir: /zap/wrk
      reportFile: zap-report.html
      reportTitle: "OWASP ZAP Security Report"

  - type: report
    parameters:
      template: sarif-json
      reportDir: /zap/wrk
      reportFile: zap-report.sarif
      reportTitle: "OWASP ZAP SARIF Report"
"""
            )

            // 3. Verify application connectivity from ZAP network
            sh '''
                echo "Testing application connectivity..."


                docker run --rm \
                    --network ci-security \
                    ghcr.io/zaproxy/zaproxy:stable \
                    curl -sS --max-time 10 \
                    http://spring-cont:8080 \
                    -o /dev/null \
                    -w "HTTP Status: %{http_code}\n"
                echo "Application is reachable!"
            '''

            // 4. Execute ZAP Automation Framework
            int zapStatus = sh(
                returnStatus: true,
                script: '''
                    docker run --rm \
                        --network "$ZAP_NETWORK" \
                        -v "$WORKSPACE/zap-reports:/zap/wrk/:rw" \
                        "$ZAP_IMAGE" \
                        zap.sh -cmd \
                        -autorun /zap/wrk/zap.yaml
                '''
            )

            echo "OWASP ZAP exit code: ${zapStatus}"

            // 5. Inspect generated reports
            sh '''
                echo "========== ZAP REPORTS =========="
                ls -lah "$WORKSPACE/zap-reports"
                echo "================================="
            '''

            // 6. Handle ZAP execution status
            if (zapStatus == 1) {
                error("OWASP ZAP Automation Framework encountered an error.")
            }

            if (zapStatus == 2) {
                unstable("OWASP ZAP completed with warnings. Review the scan output.")
            }

            if (zapStatus != 0 && zapStatus != 2) {
                error("Unexpected ZAP exit code: ${zapStatus}")
            }
        }
    }
}


}




post {
        always {

            // 6. Archive reports
            archiveArtifacts(
                artifacts: 'zap-reports/**',
                allowEmptyArchive: true
            )

            // 7. Publish HTML report
            publishHTML(target: [
                reportDir: 'zap-reports',
                reportFiles: 'zap-report.html',
                reportName: 'OWASP ZAP Security Report',
                keepAll: true,
                alwaysLinkToLastBuild: true,
                allowMissing: true
            ])

            // 8. Publish SARIF to Warnings NG
            script {
                if (fileExists('zap-reports/zap-report.sarif.json')) {
                    recordIssues(
                        enabledForFailure: true,
                        tools: [
                            sarif(
                                pattern: 'zap-reports/zap-report.sarif.json',
                                id: 'owasp-zap',
                                name: 'OWASP ZAP'
                            )
                        ]
                    )
                } else {
                    echo 'ZAP SARIF report was not generated.'
                }
            }
        }
    }

}


