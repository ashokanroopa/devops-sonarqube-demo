pipeline {

    agent any

    parameters {
        gitParameter(
            name: 'BRANCH_NAME',
            type: 'PT_BRANCH',
            description: 'Select the GitHub branch to build',
            branchFilter: 'origin/(.*)',
            defaultValue: 'main',
            selectedValue: 'DEFAULT',
            sortMode: 'ASCENDING',
            useRepository: 'https://github.com/ashokanroopa/devops-sonarqube-demo.git'
        )
    }

    environment {
        GIT_REPO = 'https://github.com/ashokanroopa/devops-sonarqube-demo.git'
        SONARQUBE_SERVER = 'SonarQube'
    }

    stages {

        stage('Fetch Branch') {
            steps {
                echo "Fetching latest branch information from GitHub..."

                sh '''
                    rm -rf .git
                    git init
                    git remote add origin ${GIT_REPO}
                    git fetch --all --prune
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

                echo "Checkout completed successfully"
                echo "Selected Branch: ${params.BRANCH_NAME}"
            }
        }

        stage('SonarQube Analysis') {
            steps {

                echo "Starting SonarQube analysis..."

                withSonarQubeEnv("${SONARQUBE_SERVER}") {

                    sh '''
                        mvn clean verify sonar:sonar \
                        -Dsonar.projectKey=devops-demo \
                        -Dsonar.projectName=DevOps-Demo \
                        -Dsonar.host.url=$SONAR_HOST_URL
                    '''
                }
            }
        }

        stage('Quality Gate') {
            steps {

                echo "Waiting for SonarQube Quality Gate..."

                timeout(time: 5, unit: 'MINUTES') {

                    waitForQualityGate abortPipeline: true
                }

                echo "SonarQube Quality Gate PASSED"
            }
        }
    }

    post {

        success {
            echo "========================================"
            echo "PIPELINE SUCCESS"
            echo "Branch: ${params.BRANCH_NAME}"
            echo "Code Quality Gate: PASSED"
            echo "========================================"
        }

        failure {
            echo "========================================"
            echo "PIPELINE FAILED"
            echo "Branch: ${params.BRANCH_NAME}"
            echo "Code Quality Gate: FAILED"
            echo "========================================"
        }
    }
}
