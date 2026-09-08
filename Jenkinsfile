pipeline {

    agent any

    options {
        skipDefaultCheckout(true)
    }

    parameters {
        gitParameter(
            name: 'BRANCH_NAME',
            type: 'PT_BRANCH',
            branchFilter: 'origin/(.*)',
            defaultValue: 'main',
            selectedValue: 'DEFAULT',
            sortMode: 'DESCENDING_SMART',
            description: 'Select the GitHub branch to build'
        )
    }

    environment {
        GIT_URL = 'https://github.com/ashokanroopa/devops-sonarqube-demo.git'

        SONARQUBE_SERVER = 'SonarQube-Server'

        NEXUS_URL = 'http://172.31.5.36:8081'

        NEXUS_CREDENTIALS = 'nexus-credentials'
    }

    stages {

        // =====================================================
        // 1. Fetch Branch
        // =====================================================

        stage('Fetch Branch') {

            steps {

                echo "========================================"
                echo "Fetching Branch Information"
                echo "========================================"

                deleteDir()

                git branch: 'main',
                    url: "${GIT_URL}"

                sh '''
                    echo "Fetching all branches..."
                    git fetch --all --prune

                    echo ""
                    echo "Available branches:"
                    git branch -r
                '''

                echo "Selected Branch: ${params.BRANCH_NAME}"
            }
        }


        // =====================================================
        // 2. Checkout
        // =====================================================

        stage('Checkout') {

            steps {

                echo "========================================"
                echo "Checking Out Selected Branch"
                echo "========================================"

                sh '''
                    git checkout -B ${BRANCH_NAME} origin/${BRANCH_NAME}
                '''

                sh '''
                    echo "========================================"
                    echo "Checked Out Branch:"
                    git branch --show-current

                    echo ""
                    echo "Commit:"
                    git rev-parse HEAD

                    echo "========================================"
                '''
            }
        }


        // =====================================================
        // 3. SonarQube Analysis
        // =====================================================

        stage('SonarQube Analysis') {

            steps {

                echo "========================================"
                echo "Starting SonarQube Analysis"
                echo "========================================"

                withSonarQubeEnv("${SONARQUBE_SERVER}") {

                    sh '''
                        mvn clean verify sonar:sonar \
                        -Dsonar.projectKey=devops-sonarqube-demo \
                        -Dsonar.projectName=devops-sonarqube-demo
                    '''
                }

                echo "SonarQube analysis completed."
            }
        }


        // =====================================================
        // 4. Quality Gate
        // =====================================================

        stage('Quality Gate') {

            steps {

                echo "========================================"
                echo "Waiting for SonarQube Quality Gate"
                echo "========================================"

                timeout(time: 10, unit: 'MINUTES') {

                    waitForQualityGate abortPipeline: true
                }

                echo "========================================"
                echo "CODE QUALITY GATE PASSED"
                echo "========================================"
            }
        }


        // =====================================================
        // 5. Manual Approval
        // =====================================================

        stage('Approval') {

            steps {

                script {

                    def approval = input(
                        message: 'Do you approve the build?',
                        parameters: [
                            choice(
                                name: 'APPROVAL',
                                choices: 'APPROVE\nDENY',
                                description: 'Select APPROVE to continue or DENY to stop the pipeline'
                            )
                        ]
                    )

                    echo "Approval decision: ${approval}"

                    if (approval == 'APPROVE') {

                        echo "========================================"
                        echo "BUILD APPROVED"
                        echo "Proceeding to Build stage"
                        echo "========================================"

                    } else {

                        error("Build denied by approver. Pipeline stopped.")
                    }
                }
            }
        }


        // =====================================================
        // 6. Maven Build
        // =====================================================

        stage('Build') {

            steps {

                echo "========================================"
                echo "Starting Application Build"
                echo "========================================"

                sh '''
                    mvn clean package -DskipTests
                '''

                echo "========================================"
                echo "APPLICATION BUILD SUCCESSFUL"
                echo "========================================"
            }
        }


        // =====================================================
        // 7. Nexus Upload
        // =====================================================

        stage('Push Artifact to Nexus') {

            steps {

                echo "========================================"
                echo "Uploading Artifact to Nexus"
                echo "========================================"

                withCredentials([
                    usernamePassword(
                        credentialsId: "${NEXUS_CREDENTIALS}",
                        usernameVariable: 'NEXUS_USERNAME',
                        passwordVariable: 'NEXUS_PASSWORD'
                    )
                ]) {

                    sh '''
                        echo "Uploading Maven artifact..."

                        mvn deploy -DskipTests
                    '''
                }

                echo "========================================"
                echo "ARTIFACT UPLOADED TO NEXUS"
                echo "========================================"
            }
        }
    }


    // =========================================================
    // POST ACTIONS
    // =========================================================

    post {

        success {

            echo """
            ========================================
                    PIPELINE SUCCESS
            ========================================

            Branch:
            ${params.BRANCH_NAME}

            SonarQube:
            Quality Gate PASSED

            Approval:
            APPROVED

            Build:
            SUCCESS

            Nexus:
            ARTIFACT UPLOADED

            ========================================
            """
        }


        failure {

            echo """
            ========================================
                    PIPELINE FAILED
            ========================================

            Branch:
            ${params.BRANCH_NAME}

            Please check the Jenkins Console Output.

            ========================================
            """
        }


        aborted {

            echo """
            ========================================
                    PIPELINE ABORTED
            ========================================

            Branch:
            ${params.BRANCH_NAME}

            Pipeline was aborted by user or timeout.

            ========================================
            """
        }
    }
}
