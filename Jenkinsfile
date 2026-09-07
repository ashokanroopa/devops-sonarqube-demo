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
    }

    stages {

        stage('Fetch Branch') {
            steps {
                echo "Fetching latest branch information from GitHub..."

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

        stage('Checkout') {
            steps {
                echo "Checking out branch: ${params.BRANCH_NAME}"

                sh '''
                    git checkout -B ${BRANCH_NAME} origin/${BRANCH_NAME}
                '''

                sh '''
                    echo "========================================"
                    echo "Checked out branch:"
                    git branch --show-current

                    echo ""
                    echo "Commit:"
                    git rev-parse HEAD

                    echo "========================================"
                '''
            }
        }

        stage('SonarQube Analysis') {
            steps {
                echo "Starting SonarQube Analysis..."

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

        stage('Approval') {
            steps {
                script {
                    timeout(time: 10, unit: 'MINUTES') {
                        input(
                            message: "SonarQube analysis completed. Do you approve the build?",
                            ok: "Approve Build"
                        )
                    }
                }
            }
        }

        stage('Build') {
            steps {
                echo "Approval received."
                echo "Starting application build..."

                sh '''
                    mvn clean package -DskipTests
                '''

                echo "Build completed successfully."
            }
        }

        stage('Quality Gate') {
            steps {
                echo "Waiting for SonarQube Quality Gate..."

                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }

                echo "========================================"
                echo "CODE QUALITY GATE PASSED"
                echo "========================================"
            }
        }
    }

    post {

        success {
            echo "========================================"
            echo "PIPELINE SUCCESS"
            echo "Branch: ${params.BRANCH_NAME}"
            echo "Build: SUCCESS"
            echo "Code Quality Gate: PASSED"
            echo "========================================"
        }

        failure {
            echo "========================================"
            echo "PIPELINE FAILED"
            echo "Branch: ${params.BRANCH_NAME}"
            echo "========================================"
        }

        aborted {
            echo "========================================"
            echo "PIPELINE ABORTED"
            echo "Approval was not received."
            echo "========================================"
        }
    }
}
