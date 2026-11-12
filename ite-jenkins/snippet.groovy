
pipeline {
    agent any

    stages {

        // Your previous build and security stages go here

        stage('Deploy') {
            steps {
                sh """
                    docker rm -f spring-cont || true

                    docker run -d \
                        -p 8081:8080 \
                        --name spring-cont \
                        ${DH_IMAGE}:${TAG}
                """

                // Wait until Spring Boot is accessible
                sh '''
                    echo "Waiting for Spring Boot..."

                    for i in $(seq 1 60); do
                        if curl -sS -o /dev/null \
                            --max-time 2 \
                            http://127.0.0.1:8081; then

                            echo "Application is accessible!"
                            exit 0
                        fi

                        sleep 2
                    done

                    echo "Application failed to become accessible."
                    docker logs spring-cont
                    exit 1
                '''
            }
        }

        // OWASP ZAP DAST
        stage('OWASP ZAP - DAST') {

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

                    // 3. Execute ZAP
                    int zapStatus = sh(
                        returnStatus: true,
                        script: '''
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
                    if (zapStatus != 0) {
                        error("OWASP ZAP failed with exit code: ${zapStatus}")
                    }
                }
            }
        }
    }

    // Global pipeline post actions
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
                if (fileExists('zap-reports/zap-report.sarif')) {
                    recordIssues(
                        enabledForFailure: true,
                        tools: [
                            sarif(
                                pattern: 'zap-reports/zap-report.sarif',
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
