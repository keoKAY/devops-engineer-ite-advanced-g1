// Trying to intergrate with the owasp zap services 
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
                docker rm -f spring-cont || true 
                docker run -dp 8081:8080  --name spring-cont ${DH_IMAGE}:${TAG}
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
        stage('OWASP ZAP Scan') {
            environment {
                ZAP_IMAGE = 'ghcr.io/zaproxy/zaproxy:stable'
                ZAP_TARGET_URL = 'http://host.docker.internal:8081'
            }

            steps {
                script {

                    // 1. Prepare reports directory
                    sh '''
                        mkdir -p "$WORKSPACE/zap-reports"
                        chmod 777 "$WORKSPACE/zap-reports"
                    '''

                    // 2. Generate Automation Framework YAML
                    writeFile(
                        file: 'zap-reports/zap.yaml',
                        text: """
env:
  contexts:
    - name: target
      urls:
        - "${env.ZAP_TARGET_URL}"
  parameters:
    failOnError: true
    failOnWarning: false
    progressToStdout: true

jobs:
  - type: spider
    parameters:
      context: target
      url: "${env.ZAP_TARGET_URL}"
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
      reportFile: zap-report
      reportTitle: "OWASP ZAP SARIF Report"
"""
                    )

                    // 3. Execute ZAP
                    int zapStatus = sh(
                        returnStatus: true,
                        script: '''
                            docker pull "$ZAP_IMAGE"
                            docker run --rm \
                                --add-host=host.docker.internal:host-gateway \
                                -v "$WORKSPACE/zap-reports:/zap/wrk/:rw" \
                                "$ZAP_IMAGE" \
                                zap.sh -cmd \
                                -autorun /zap/wrk/zap.yaml
                        '''
                    )

                    echo "ZAP exit code: ${zapStatus}"

                    // 4. Check generated reports
                    sh '''
                        echo "========== ZAP REPORTS =========="

                        ls -lah zap-reports/

                        echo "================================="
                    '''

                    // 5. Fail pipeline when ZAP execution fails
                    // ZAP autorun exit codes: 0 = OK, 1 = job errors, 2 = job warnings
                    if (zapStatus == 2) {
                        unstable("OWASP ZAP finished with warnings (exit code 2) - check the ZAP log above")
                    } else if (zapStatus != 0) {
                        error("OWASP ZAP failed with exit code: ${zapStatus}")
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


