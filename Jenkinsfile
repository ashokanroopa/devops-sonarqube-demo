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

        /*
         * =====================================================
         * STAGE 1: FETCH BRANCH
         * =====================================================
         */

        stage('Fetch Branch') {

            steps {

                echo "Fetching latest branch information from GitHub..."

                // Clean the Jenkins workspace
                deleteDir()

                // Clone the repository initially
                git branch: 'main',
                    url: "${GIT_URL}"

                // Fetch all remote branches
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


        /*
         * =====================================================
         * STAGE 2: CHECKOUT
         * =====================================================
         */

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


        /*
         * =====================================================
         * STAGE 3: SONARQUBE ANALYSIS
         * =====================================================
         */

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


        /*
         * =====================================================
         * STAGE 4: MANUAL APPROVAL
         * =====================================================
         */

        stage('Approval') {

            steps {

                script {

                    def approval = input(

                        message: 'SonarQube analysis completed. Do you approve the build?',

                        ok: 'Submit',

                        parameters: [
                            choice(
                                name: 'APPROVAL',
                                choices: [
                                    'APPROVE',
                                    'DENY'
                                ],
                                description: 'Select APPROVE to continue the build or DENY to stop the pipeline'
                            )
                        ]
                    )

                    if (approval == 'APPROVE') {

                        echo "========================================"
                        echo "BUILD APPROVED"
                        echo "Continuing to Build stage..."
                        echo "========================================"

                    } else {

                        error(
                            "BUILD DENIED BY APPROVER. PIPELINE STOPPED."
                        )
                    }
                }
            }
        }


        /*
         * =====================================================
         * STAGE 5: BUILD
         * =====================================================
         */

        stage('Build') {

            steps {

                echo "========================================"
                echo "Starting Application Build..."
                echo "========================================"

                sh '''
                    mvn clean package -DskipTests
                '''

                echo "========================================"
                echo "APPLICATION BUILD SUCCESSFUL"
                echo "========================================"
            }
        }


        /*
         * =====================================================
         * STAGE 6: QUALITY GATE
         * =====================================================
         */

        stage('Quality Gate') {

            steps {

                echo "Waiting for SonarQube Quality Gate..."

                timeout(
                    time: 10,
                    unit: 'MINUTES'
                ) {

                    waitForQualityGate(
                        abortPipeline: true
                    )
                }

                echo "========================================"
                echo "CODE QUALITY GATE PASSED"
                echo "========================================"
            }
        }
    }


    /*
     * =========================================================
     * POST ACTIONS
     * =========================================================
     */

    post {

        success {

            echo "========================================"
            echo "PIPELINE SUCCESS"
            echo "========================================"

            echo "Branch: ${params.BRANCH_NAME}"
            echo "Build: SUCCESS"
            echo "Code Quality Gate: PASSED"

            echo "========================================"
        }


        failure {

            echo "========================================"
            echo "PIPELINE FAILED"
            echo "========================================"

            echo "Branch: ${params.BRANCH_NAME}"

            echo "========================================"
        }


        aborted {

            echo "========================================"
            echo "PIPELINE ABORTED"
            echo "========================================"

            echo "Branch: ${params.BRANCH_NAME}"

            echo "========================================"
        }
    }
}
