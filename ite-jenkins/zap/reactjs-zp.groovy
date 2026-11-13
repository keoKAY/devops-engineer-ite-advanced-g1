// Trying to intergrate with the owasp zap services
// NOTE: NOT YET WORKING CORRECTLY  
pipeline {
    agent any

    environment{
        DH_IMAGE="reactjs-zap-special"
        TAG="v1.0.${env.BUILD_NUMBER}"
        FULL_IMAGE="${DH_IMAGE}:${TAG}"
        APP_CONTAINER="reactjs-cont"
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
                docker rm -f ${APP_CONTAINER} || true

                # Deploy application on shared network
                docker run -d \
                    --name ${APP_CONTAINER} \
                    --network ci-security \
                    -p 127.0.0.1:3001:80 \
                    ${DH_IMAGE}:${TAG} 
                """

                // Wait for the ReactJS app (nginx) to be accessible 
                sh '''
                echo "Waiting for the ReactJS app.... 🔥"
                for i in $(seq 1 60 ); do 
                    if curl -sS -o /dev/null \
                        --max-time 2 \
                        http://127.0.0.1:3001; then 

                        echo "Application is accessible! " 
                        exit 0 
                    fi 
                    sleep 2 
                done 

                echo "Application failed be become accessible ❌" 
                docker logs "$APP_CONTAINER" 
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
        // Inside the docker network we talk to the container port (80), not the host port (3001)
        // Port 80 is the HTTP default, so it is omitted (ZAP normalizes it away anyway)
        ZAP_TARGET_URL = "http://${APP_CONTAINER}"
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
  # ZAP AF requires at least one context: it defines what is "in scope"
  contexts:
    - name: reactjs-app
      urls:
        - "${env.ZAP_TARGET_URL}"
      includePaths:
        - "${env.ZAP_TARGET_URL}.*"

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

  # React is a SPA: routes are rendered by JS, so the classic spider finds little.
  # The AJAX spider drives a headless browser to discover client-side routes.
  - type: spiderAjax
    parameters:
      url: "\${target}"
      browserId: firefox-headless
      maxDuration: 3

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
      reportFile: zap-report.sarif.json
      reportTitle: "OWASP ZAP SARIF Report"
"""
            )

            // 3. Verify application connectivity from ZAP network
            sh '''
                echo "Testing application connectivity..."


                docker run --rm \
                    --network "$ZAP_NETWORK" \
                    "$ZAP_IMAGE" \
                    curl -sS --max-time 10 \
                    "$ZAP_TARGET_URL" \
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


